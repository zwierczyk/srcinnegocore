package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class WataCukrowa implements Listener {
    private final String wataCukrowaName;
    private final Material wataCukrowaMaterial;
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int cooldownSeconds;
    private final Random random = new Random();

    public WataCukrowa(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);
        List<String> regionList = eventowkiConfig.getStringList("blocked-regions");
        if (regionList.isEmpty() && !eventowkiConfig.isSet("blocked-regions")) {
            eventowkiConfig.set("blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }

        String wataCukrowaItem = eventowkiConfig.getString("eventowki.watacukrowa.item", "PINK_DYE");
        wataCukrowaName = "§b§lWata cukrowa";
        wataCukrowaMaterial = Material.valueOf(wataCukrowaItem.toUpperCase());
        cooldownSeconds = eventowkiConfig.getInt("eventowki.watacukrowa.cooldown", 5);
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;

        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (itemInHand.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "watacukrowa", itemInHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;

        event.setCancelled(true);

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "watacukrowa", itemInHand, cooldownSeconds)) {
            return;
        }

        if (isLocationBlocked(player.getLocation())) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        List<ItemStack> damagedArmor = new ArrayList<>();
        List<String> armorNames = new ArrayList<>();

        ItemStack helmet = inventory.getHelmet();
        ItemStack chestplate = inventory.getChestplate();
        ItemStack leggings = inventory.getLeggings();
        ItemStack boots = inventory.getBoots();

        if (isDamaged(helmet)) {
            damagedArmor.add(helmet);
            armorNames.add("§7swój §bHełm§7!");
        }
        if (isDamaged(chestplate)) {
            damagedArmor.add(chestplate);
            armorNames.add("§7swój §bNapierśnik§7!");
        }
        if (isDamaged(leggings)) {
            damagedArmor.add(leggings);
            armorNames.add("§7swoje §bSpodnie§7!");
        }
        if (isDamaged(boots)) {
            damagedArmor.add(boots);
            armorNames.add("§7swoje §bButy§7!");
        }

        if (damagedArmor.isEmpty()) {
            return;
        }

        int randomIndex = random.nextInt(damagedArmor.size());
        ItemStack itemToRepair = damagedArmor.get(randomIndex);
        String repairedName = armorNames.get(randomIndex);

        itemToRepair.setDurability((short) 0);

        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            inventory.setItemInMainHand(null);
        }

        EventItemUseGuard.applyItemCooldown(plugin, player, "watacukrowa", itemInHand, cooldownSeconds);

        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§7Naprawiłeś " + repairedName, 0, 50, 10);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 1f);
    }

    private boolean isDamaged(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getDurability() > 0;
    }
}
