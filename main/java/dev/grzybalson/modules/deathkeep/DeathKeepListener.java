package dev.grzybalson.modules.deathkeep;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.PlayerInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Port logiki Hypestar ClanController.onDeath:
 * - ~35% "weapon/armor" wraca do ofiary
 * - reszta idzie do killera (nie drop na ziemie)
 * - backup zapisuje TYLKO stracone itemy (bez tych zwroconych)
 */
public final class DeathKeepListener implements Listener {

    public static final String KEEP50_PERMISSION = "Holly.death.keep50";

    private final HollyCore plugin;
    private final Map<UUID, LostSnapshot> lastLost = new ConcurrentHashMap<>();

    public DeathKeepListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    public LostSnapshot consumeLostSnapshot(UUID playerId) {
        return lastLost.remove(playerId);
    }

    public LostSnapshot peekLostSnapshot(UUID playerId) {
        return lastLost.get(playerId);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!isEnabled()) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (plugin.getCreatorModeManager() != null && plugin.getCreatorModeManager().shouldPreventDrops(victim, killer)) {
            return;
        }

        // Inny handler (dungeon, totem ulaskawienia, gamerule keepInventory) juz przejal itemy —
        // nie ma czego dzielic, a czyszczenie EQ w takim wypadku kasowalo caly ekwipunek.
        if (event.getKeepInventory() || event.getDrops().isEmpty()) {
            return;
        }

        // Storage + offhand osobno od zbroi. getContents() ma armor na 36-39,
        // a backup trzyma armor w osobnym polu — pakowanie obu dawało x2 przy /odbierzbackup.
        ItemStack[] lostStorage = cloneSlots(victim.getInventory().getStorageContents(), PlayerInventories.STORAGE_SIZE);
        ItemStack[] lostExtra = cloneSlots(victim.getInventory().getExtraContents(), 1);
        ItemStack[] fullArmorSnapshot = cloneArmor(victim.getInventory().getArmorContents());

        List<ItemStack> drops = new ArrayList<>();
        List<ItemStack> kept = new ArrayList<>();
        List<ItemStack> weapons = new ArrayList<>();

        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            if (isKeepableGear(drop.getType()) && drop.getEnchantmentLevel(Enchantment.VANISHING_CURSE) <= 0) {
                weapons.add(drop.clone());
            } else {
                drops.add(drop.clone());
            }
        }

        if (!weapons.isEmpty()) {
            Collections.shuffle(weapons, ThreadLocalRandom.current());
            double keepRatio = victim.hasPermission(KEEP50_PERMISSION) ? getKeepBoostRatio() : getKeepRatio();
            int itemsLeft = (int) (weapons.size() * keepRatio);
            int swordInsert = 0;
            for (ItemStack weapon : weapons) {
                if (itemsLeft <= 0) {
                    if (isPriorityLost(weapon.getType())) {
                        drops.add(0, weapon);
                        swordInsert++;
                    } else {
                        drops.add(Math.min(swordInsert, drops.size()), weapon);
                    }
                    continue;
                }
                itemsLeft--;
                kept.add(weapon);
            }
            // Dobrobyt: dodatkowa szansa na odzyskanie miecza, który nie wpadł w bazowy keep.
            tryKeepSwordWithDobrobyt(victim, drops, kept);
        }

        event.getDrops().clear();
        event.setKeepInventory(true);
        event.setKeepLevel(event.getKeepLevel());

        clearInventory(victim);

        if (plugin.getInventorySyncManager() != null) {
            // Autosave pustego EQ przed restore keep — OK; quit i tak force-save'uje.
            plugin.getInventorySyncManager().suppressSaveBriefly(victim.getUniqueId(), 10_000L);
            // Natychmiast nadpisz Mongo stanem po śmierci (puste / death), żeby rejoin
            // nie wczytał EQ sprzed kill'a gdy ofiara DC'nie przed końcem suppress.
            plugin.getInventorySyncManager().forceSavePlayer(victim);
        }

        // Backup = pelne EQ minus to co wraca do ofiary (jak playerInventory.remove w Hypestar)
        for (ItemStack keptItem : kept) {
            if (removeFirstSimilar(lostStorage, keptItem)) {
                continue;
            }
            removeFirstSimilar(lostExtra, keptItem);
        }
        ItemStack[] lostArmor = filterLostArmor(fullArmorSnapshot, kept);

        lastLost.put(victim.getUniqueId(), new LostSnapshot(
            PlayerInventories.combineStorageAndExtra(lostStorage, lostExtra),
            lostArmor,
            System.currentTimeMillis()
        ));

        if (killer != null && !killer.equals(victim) && !drops.isEmpty()) {
            if (plugin.getMasakraCustomItemsManager() != null) {
                plugin.getMasakraCustomItemsManager().collectMisakoDrops(killer, victim, drops);
            }
            LocationSafeGive(killer, drops);
            if (plugin.getInventorySyncManager() != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (killer.isOnline()) {
                        plugin.getInventorySyncManager().forceSavePlayer(killer);
                    }
                }, 2L);
            }
        } else if (!drops.isEmpty()) {
            for (ItemStack drop : drops) {
                victim.getWorld().dropItemNaturally(victim.getLocation(), drop);
            }
        }

        if (!kept.isEmpty()) {
            List<ItemStack> keptCopy = new ArrayList<>(kept);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!victim.isOnline()) {
                    return;
                }
                for (ItemStack item : keptCopy) {
                    Map<Integer, ItemStack> leftover = victim.getInventory().addItem(item);
                    for (ItemStack left : leftover.values()) {
                        victim.getWorld().dropItemNaturally(victim.getLocation(), left);
                    }
                }
                victim.sendMessage(ColorUtils.colorizeComponent(
                    "&#00E0FFℹ &8Ξ &fZachowałeś &e" + keptCopy.size() + " &fprzedmiotów po śmierci."
                ));
                if (plugin.getInventorySyncManager() != null) {
                    plugin.getInventorySyncManager().forceSavePlayer(victim);
                }
            }, 5L);
        }
    }

    private void tryKeepSwordWithDobrobyt(Player victim, List<ItemStack> drops, List<ItemStack> kept) {
        if (victim == null || plugin.getSigilManager() == null || drops.isEmpty()) {
            return;
        }
        double chance = plugin.getSigilManager().getActivePower(victim.getUniqueId(),
            dev.grzybalson.modules.sigil.SigilType.DOBROBYT);
        if (chance <= 0.0D) {
            return;
        }
        // Już zachował jakiś miecz — nie dubluj.
        for (ItemStack item : kept) {
            if (item != null && isSword(item.getType())) {
                return;
            }
        }
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            if (drop == null || !isSword(drop.getType())) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
                continue;
            }
            kept.add(drop);
            drops.remove(i);
            break;
        }
    }

    private static boolean isSword(Material material) {
        return material == Material.IRON_SWORD
            || material == Material.DIAMOND_SWORD
            || material == Material.NETHERITE_SWORD;
    }

    private boolean isEnabled() {
        var config = plugin.getConfigManager() != null ? plugin.getConfigManager().getBackupConfig() : null;
        if (config == null) {
            return true;
        }
        try {
            return config.getDeathKeepEnabled();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private double getKeepRatio() {
        var config = plugin.getConfigManager() != null ? plugin.getConfigManager().getBackupConfig() : null;
        if (config == null) {
            return 0.35D;
        }
        try {
            return config.getDeathKeepPercent();
        } catch (Throwable ignored) {
            return 0.35D;
        }
    }

    private double getKeepBoostRatio() {
        var config = plugin.getConfigManager() != null ? plugin.getConfigManager().getBackupConfig() : null;
        if (config == null) {
            return 0.50D;
        }
        try {
            return config.getDeathKeepBoostPercent();
        } catch (Throwable ignored) {
            return 0.50D;
        }
    }

    private static boolean isKeepableGear(Material material) {
        return getWeaponType(material) != null;
    }

    private static boolean isPriorityLost(Material material) {
        return material == Material.DIAMOND_SWORD
            || material == Material.NETHERITE_SWORD
            || material == Material.DIAMOND_SHOVEL
            || material == Material.NETHERITE_SHOVEL;
    }

    private static WeaponType getWeaponType(Material material) {
        if (material == Material.IRON_SWORD || material == Material.DIAMOND_SWORD || material == Material.NETHERITE_SWORD) {
            return WeaponType.SWORD;
        }
        if (material == Material.DIAMOND_SHOVEL || material == Material.NETHERITE_SHOVEL) {
            return WeaponType.PICKAXE;
        }
        if (material == Material.ELYTRA) {
            return WeaponType.ELYTRA;
        }
        if (material == Material.DIAMOND_HELMET || material == Material.NETHERITE_HELMET || material == Material.LEATHER_HELMET) {
            return WeaponType.HELMET;
        }
        if (material == Material.DIAMOND_CHESTPLATE || material == Material.NETHERITE_CHESTPLATE || material == Material.LEATHER_CHESTPLATE) {
            return WeaponType.CHESTPLATE;
        }
        if (material == Material.DIAMOND_LEGGINGS || material == Material.NETHERITE_LEGGINGS || material == Material.LEATHER_LEGGINGS) {
            return WeaponType.LEGGINGS;
        }
        if (material == Material.DIAMOND_BOOTS || material == Material.NETHERITE_BOOTS
            || material == Material.LEATHER_BOOTS || material == Material.PLAYER_HEAD) {
            return WeaponType.BOOTS;
        }
        return null;
    }

    private enum WeaponType {
        SWORD, PICKAXE, ELYTRA, HELMET, CHESTPLATE, LEGGINGS, BOOTS
    }

    private static void clearInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setHelmet(null);
        inventory.setChestplate(null);
        inventory.setLeggings(null);
        inventory.setBoots(null);
        inventory.setItemInOffHand(null);
        player.updateInventory();
    }

    private static void LocationSafeGive(Player killer, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            Map<Integer, ItemStack> leftover = killer.getInventory().addItem(drop);
            for (ItemStack left : leftover.values()) {
                killer.getWorld().dropItemNaturally(killer.getLocation(), left);
            }
        }
    }

    private static ItemStack[] cloneSlots(ItemStack[] contents, int size) {
        ItemStack[] copy = new ItemStack[size];
        if (contents == null) {
            return copy;
        }
        int n = Math.min(size, contents.length);
        for (int i = 0; i < n; i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) {
                copy[i] = contents[i].clone();
            }
        }
        return copy;
    }

    private static ItemStack[] cloneArmor(ItemStack[] armor) {
        if (armor == null) {
            return new ItemStack[4];
        }
        ItemStack[] copy = new ItemStack[armor.length];
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && !armor[i].getType().isAir()) {
                copy[i] = armor[i].clone();
            }
        }
        return copy;
    }

    private static boolean removeFirstSimilar(ItemStack[] items, ItemStack target) {
        if (items == null || target == null) {
            return false;
        }
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item != null && item.isSimilar(target)) {
                items[i] = null;
                return true;
            }
        }
        return false;
    }

    private static ItemStack[] filterLostArmor(ItemStack[] fullArmor, List<ItemStack> kept) {
        ItemStack[] lost = new ItemStack[fullArmor.length];
        for (int i = 0; i < fullArmor.length; i++) {
            ItemStack piece = fullArmor[i];
            if (piece == null || piece.getType().isAir()) {
                continue;
            }
            boolean wasKept = false;
            for (ItemStack k : kept) {
                if (k != null && k.isSimilar(piece)) {
                    wasKept = true;
                    break;
                }
            }
            if (!wasKept) {
                lost[i] = piece.clone();
            }
        }
        return lost;
    }

    public record LostSnapshot(ItemStack[] inventory, ItemStack[] armor, long createdAtMillis) {
        public boolean isFresh() {
            return System.currentTimeMillis() - createdAtMillis < 10_000L;
        }
    }
}
