package dev.grzybalson.pvp.kowal;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.pvp.kowal.KowalUpgradeHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class KowalGUIHandler
implements Listener,
CommandExecutor {
    private final JavaPlugin plugin;
    private final KowalUpgradeHandler upgradeHandler;
    private final Map<UUID, Boolean> playerKowalStoneToggle = new HashMap<UUID, Boolean>();
    private final List<Material> specialItems = Arrays.asList(Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, Material.NETHERITE_BOOTS, Material.NETHERITE_LEGGINGS);

    public KowalGUIHandler(JavaPlugin plugin, KowalUpgradeHandler upgradeHandler) {
        this.plugin = plugin;
        this.upgradeHandler = upgradeHandler;
    }

    public static String colorize(String message) {
        return ColorUtils.colorize(message);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(KowalGUIHandler.colorize("\u00a7cNie mo\u017cesz u\u017cy\u0107 tej komendy z konsoli!"));
            return true;
        }
        Player player = (Player)sender;
        String name = command.getName();
        if (!name.equalsIgnoreCase("kowal") && !label.equalsIgnoreCase("kowal")) {
            return false;
        }
        if (!player.hasPermission("Holly.kowal.use") && !player.hasPermission("holly.kowal.use") && !player.isOp()) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.kowal.use"));
            return true;
        }
        if (args != null && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.kowal.admin") && !sender.isOp()) {
                sender.sendMessage(ColorUtils.noPermission(label, "Holly.kowal.admin"));
                return true;
            }
            this.upgradeHandler.reloadConfig();
            sender.sendMessage(ColorUtils.success("Pomyślnie przeładowano konfigurację kowala (kowal.yml)!"));
            return true;
        }
        this.playerKowalStoneToggle.put(player.getUniqueId(), false);
        this.openKowalGUI(player);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt((World)player.getLocation().getWorld()));
        if (regionManager == null) {
            return;
        }
        ProtectedRegion region = regionManager.getRegion("kowal");
        if (region == null) {
            return;
        }
        BlockVector3 blockVector = BukkitAdapter.asBlockVector((Location)player.getLocation());
        if (region.contains(blockVector)) {
            // Blokuj wszystkie prawe kliknięcia w regionie kowala aby zapobiec swapowaniu zbroi
            event.setCancelled(true);
        }
    }

    public void openKowalGUI(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (!this.upgradeHandler.canUpgrade(heldItem)) {
            // Jeśli gracz trzyma inny przedmiot (nie armor i nie pusty slot) - nie pozwól na otwarcie GUI
            if (heldItem != null && heldItem.getType() != Material.AIR) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cMusisz trzyma\u0107 cz\u0119\u015b\u0107 uzbrojenia!");
                player.closeInventory();
                return;
            }
            // Gracz ma pusty slot - spróbuj znaleźć armor ze slotów
            ItemStack armorItem = this.findAndRemoveArmorFromSlots(player, null);
            if (armorItem != null) {
                player.getInventory().setItemInMainHand(armorItem);
                player.updateInventory();
                heldItem = armorItem;
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cMusisz trzyma\u0107 cz\u0119\u015b\u0107 uzbrojenia!");
                player.closeInventory();
                return;
            }
        }
        Inventory gui = Bukkit.createInventory(null, 27, this.upgradeHandler.getGuiTitleMain());
        int currentLevel = this.upgradeHandler.getCurrentUpgradeLevel(heldItem);
        int nextLevel = this.upgradeHandler.getNextUpgradeLevel(currentLevel);
        KowalUpgradeHandler.UpgradeInfo upgradeInfo = this.upgradeHandler.getUpgradeInfo(nextLevel);
        String cancelPath = "gui-messages.cancel";
        gui.setItem(this.upgradeHandler.getGuiSlot(cancelPath + ".slot", 11), this.createGuiItem(this.upgradeHandler.getGuiMaterial(cancelPath + ".material", Material.RED_DYE), this.upgradeHandler.getGuiName(cancelPath + ".name", "\u00a7cAnuluj"), this.upgradeHandler.getGuiLore(cancelPath + ".lore", Arrays.asList("\u00a7cNie chc\u0119 ryzykowa\u0107. Wol\u0119 zosta\u0107", "\u00a7cprzy aktualnym poziomie ulepszenia."))));
        gui.setItem(13, heldItem.clone());
        String upgradePath = "gui-messages.upgrade-button";
        if (nextLevel != -1 && upgradeInfo != null) {
            Map<String, Boolean> status = this.upgradeHandler.checkRequirementsStatus(player, nextLevel);
            boolean allRequirementsMet = !status.containsValue(false);
            ArrayList<String> infoLore = new ArrayList<String>();
            String upgradeLine = this.upgradeHandler.getGuiName(upgradePath + ".lore-prefix-upgrade", "\u00a77Ulepszenie: \u00a7a+<CURRENT_LEVEL> \u00a78\u00ab\u00a72\u279c \u00a7a+<NEXT_LEVEL>").replace("<CURRENT_LEVEL>", String.valueOf(currentLevel)).replace("<NEXT_LEVEL>", String.valueOf(nextLevel));
            infoLore.add(upgradeLine);
            infoLore.add(KowalGUIHandler.colorize(""));
            infoLore.add(this.upgradeHandler.getGuiName(upgradePath + ".lore-prefix-requirements", "\u00a77Wymagane ulepszacze:"));
            for (String mat : upgradeInfo.getMaterials()) {
                String[] parts = mat.split(":");
                Material material = Material.matchMaterial(parts[0]);
                String materialName = material != null ? material.name().replace('_', ' ') : parts[0];
                String color = status.getOrDefault(mat, false) != false ? "\u00a7a" : "\u00a7c";
                infoLore.add(KowalGUIHandler.colorize(" \u00a78\u00bb " + color + materialName.toUpperCase().replace(' ', '_').replace("_INGOT", "").replace("POTATO", "ZIEMNIAK") + " x" + parts[1]));
            }
            String moneyKey = "money";
            String moneyColor = status.getOrDefault(moneyKey, false) != false ? "\u00a7a" : "\u00a7c";
            infoLore.add(KowalGUIHandler.colorize(" \u00a78\u00bb " + moneyColor + upgradeInfo.getCost() + "$"));
            infoLore.add(KowalGUIHandler.colorize(""));
            if (!allRequirementsMet) {
                infoLore.add(this.upgradeHandler.getGuiName(upgradePath + ".lore-suffix-missing", "\u00a7cUzbi\u00e9raj wymagane przedmioty!"));
            } else {
                infoLore.add(this.upgradeHandler.getGuiName(upgradePath + ".lore-suffix-ready", "\u00a7eKliknij, aby ulepszy\u0107!"));
            }
            gui.setItem(this.upgradeHandler.getGuiSlot(upgradePath + ".slot", 15), this.createGuiItem(this.upgradeHandler.getGuiMaterial(upgradePath + ".material", Material.LIME_DYE), this.upgradeHandler.getGuiName(upgradePath + ".name", "\u00a7aZwi\u0119ksz poziom"), infoLore));
        } else {
            gui.setItem(this.upgradeHandler.getGuiSlot(upgradePath + ".slot", 15), this.createGuiItem(this.upgradeHandler.getGuiMaterial(upgradePath + ".material-max-level", Material.BARRIER), this.upgradeHandler.getGuiName(upgradePath + ".name-max-level", "\u00a74Maksymalny poziom!"), this.upgradeHandler.getGuiLore(upgradePath + ".lore-max-level", Arrays.asList("\u00a7cNie mo\u017cna ulepszy\u0107 tego przedmiotu dalej."))));
        }
        boolean hasStone = this.upgradeHandler.hasKowalStone(player);
        boolean isToggleOn = this.playerKowalStoneToggle.getOrDefault(player.getUniqueId(), false) != false && hasStone;
        String stonePath = "gui-messages.toggle-stone";
        String currentStoneStatePath = isToggleOn ? stonePath + ".stone-active" : stonePath + ".metal-active";
        String stoneName = this.upgradeHandler.getGuiName(currentStoneStatePath + ".name", isToggleOn ? "\u00a75Ulepszanie kamieniem kowalskim" : "\u00a79Ulepszasz metalem!");
        ArrayList<String> stoneLore = new ArrayList<String>(this.upgradeHandler.getGuiLore(currentStoneStatePath + ".lore", Arrays.asList("")));
        Material stoneMat = this.upgradeHandler.getGuiMaterial(currentStoneStatePath + ".material", isToggleOn ? Material.REDSTONE_TORCH : Material.LEVER);
        if (!isToggleOn && currentLevel == 0) {
            if (stoneLore.size() > 0) {
                stoneLore.remove(stoneLore.size() - 1);
            }
            stoneLore.add(this.upgradeHandler.getGuiName(stonePath + ".stone-level-limit", "\u00a7cKamie\u0144 Kowalski jest dost\u0119pny do u\u017cycia tylko od poziomu ulepszenia 1"));
        }
        gui.setItem(this.upgradeHandler.getGuiSlot(stonePath + ".slot", 22), this.createGuiItem(stoneMat, stoneName, stoneLore));
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private ItemStack findAndRemoveArmorFromSlots(Player player, ItemStack itemToSwap) {
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        ItemStack[] slotItems = {armorContents[3], armorContents[2], armorContents[1], armorContents[0]};
        int[] slotIndices = {39, 38, 37, 36};
        for (int i = 0; i < slotItems.length; i++) {
            ItemStack armor = slotItems[i];
            if (armor != null && this.specialItems.contains(armor.getType())) {
                ItemStack armorCopy = armor.clone();
                if (itemToSwap != null && itemToSwap.getType() != Material.AIR) {
                    player.getInventory().setItem(slotIndices[i], itemToSwap.clone());
                } else {
                    player.getInventory().setItem(slotIndices[i], null);
                }
                return armorCopy;
            }
        }
        return null;
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openConfirmationGUI(Player player) {
        boolean useKowalStone;
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        int currentLevel = this.upgradeHandler.getCurrentUpgradeLevel(heldItem);
        int nextLevel = this.upgradeHandler.getNextUpgradeLevel(currentLevel);
        KowalUpgradeHandler.UpgradeInfo info = this.upgradeHandler.getUpgradeInfo(nextLevel);
        if (info == null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cWyst\u0105pi\u0142 b\u0142\u0105d!");
            player.closeInventory();
            return;
        }
        Inventory gui = Bukkit.createInventory(null, 9, this.upgradeHandler.getGuiTitleConfirm());
        String confirmNoPath = "gui-messages.confirm-no";
        gui.setItem(this.upgradeHandler.getGuiSlot(confirmNoPath + ".slot", 0), this.createGuiItem(this.upgradeHandler.getGuiMaterial(confirmNoPath + ".material", Material.RED_DYE), this.upgradeHandler.getGuiName(confirmNoPath + ".name", "\u00a7cNIE"), this.upgradeHandler.getGuiLore(confirmNoPath + ".lore", Arrays.asList("\u00a7cNie chc\u0119 ryzykowa\u0107. Wol\u0119 zosta\u0107", "\u00a7cprzy aktualnym poziomie ulepszenia."))));
        ArrayList<String> confirmLore = new ArrayList<String>();
        String confirmYesPath = "gui-messages.confirm-yes";
        String chanceLine = this.upgradeHandler.getGuiName(confirmYesPath + ".lore-chance-line", "\u00a7aSzansa na ulepszenie: \u00a7e<CHANCE>%%").replace("<CHANCE>", String.format(Locale.US, "%.0f", info.getChance()));
        confirmLore.add(chanceLine);
        confirmLore.add(KowalGUIHandler.colorize(""));
        boolean bl = useKowalStone = this.playerKowalStoneToggle.getOrDefault(player.getUniqueId(), false) != false && this.upgradeHandler.hasKowalStone(player);
        if (useKowalStone) {
            confirmLore.addAll(this.upgradeHandler.getGuiLore(confirmYesPath + ".lore-safe", Arrays.asList("\u00a77Te ulepszenie jest \u00a7ezabezpieczone", "\u00a77przed obni\u017ceniem poziomu przedmiotu.")));
        } else {
            confirmLore.addAll(this.upgradeHandler.getGuiLore(confirmYesPath + ".lore-risk", Arrays.asList("\u00a7cJestem \u015bwiadomy, \u017ce przy nieudanej", "\u00a7cpr\u00f3bie ulepszenia poziom przedmiotu zostanie", "\u00a7cobni\u017cone o jeden.")));
        }
        gui.setItem(this.upgradeHandler.getGuiSlot(confirmYesPath + ".slot", 4), this.createGuiItem(this.upgradeHandler.getGuiMaterial(confirmYesPath + ".material", Material.LIME_DYE), this.upgradeHandler.getGuiName(confirmYesPath + ".name", "\u00a7aTAK"), confirmLore));
        player.openInventory(gui);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.playerKowalStoneToggle.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity humanEntity = event.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player player = (Player)humanEntity;
        if (event.getView().getTitle().equals(this.upgradeHandler.getGuiTitleMain())) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            if (!this.upgradeHandler.canUpgrade(heldItem)) {
                ItemStack armorItem = this.findAndRemoveArmorFromSlots(player, heldItem);
                if (armorItem != null) {
                    player.getInventory().setItemInMainHand(armorItem);
                    player.updateInventory();
                    heldItem = armorItem;
                }
            }
            int currentLevel = this.upgradeHandler.getCurrentUpgradeLevel(heldItem);
            int cancelSlot = this.upgradeHandler.getGuiSlot("gui-messages.cancel.slot", 11);
            int upgradeSlot = this.upgradeHandler.getGuiSlot("gui-messages.upgrade-button.slot", 15);
            int toggleSlot = this.upgradeHandler.getGuiSlot("gui-messages.toggle-stone.slot", 22);
            if (event.getSlot() == cancelSlot) {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } else if (event.getSlot() == upgradeSlot) {
                int nextLevel = this.upgradeHandler.getNextUpgradeLevel(currentLevel);
                if (nextLevel == -1) {
                    return;
                }
                if (!this.upgradeHandler.checkMaterials(player, nextLevel)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cNie posiadasz wymaganych materia\u0142\u00f3w!");
                    player.closeInventory();
                    return;
                }
                this.openConfirmationGUI(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } else if (event.getSlot() == toggleSlot) {
                boolean hasStone = this.upgradeHandler.hasKowalStone(player);
                if (hasStone && currentLevel > 0) {
                    boolean currentState = this.playerKowalStoneToggle.getOrDefault(player.getUniqueId(), false);
                    this.playerKowalStoneToggle.put(player.getUniqueId(), !currentState);
                    this.openKowalGUI(player);
                } else if (!hasStone) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cNie posiadasz Kamienia kowalskiego!");
                    player.closeInventory();
                } else if (currentLevel == 0) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cPrzedmiot ma zbyt niski poziom!");
                    player.closeInventory();
                }
            }
        } else if (event.getView().getTitle().equals(this.upgradeHandler.getGuiTitleConfirm())) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }
            int confirmNoSlot = this.upgradeHandler.getGuiSlot("gui-messages.confirm-no.slot", 0);
            int confirmYesSlot = this.upgradeHandler.getGuiSlot("gui-messages.confirm-yes.slot", 4);
            if (event.getSlot() == confirmNoSlot) {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            } else if (event.getSlot() == confirmYesSlot) {
                boolean useKowalStone;
                player.closeInventory();
                ItemStack itemToUpgrade = player.getInventory().getItemInMainHand();
                if (!this.upgradeHandler.canUpgrade(itemToUpgrade)) {
                    ItemStack armorItem = this.findAndRemoveArmorFromSlots(player, itemToUpgrade);
                    if (armorItem != null) {
                        player.getInventory().setItemInMainHand(armorItem);
                        player.updateInventory();
                        itemToUpgrade = armorItem;
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cNie masz przedmiotu do ulepszenia!");
                        return;
                    }
                }
                int currentLevel = this.upgradeHandler.getCurrentUpgradeLevel(itemToUpgrade);
                int nextLevel = this.upgradeHandler.getNextUpgradeLevel(currentLevel);
                KowalUpgradeHandler.UpgradeInfo info = this.upgradeHandler.getUpgradeInfo(nextLevel);
                if (nextLevel == -1 || info == null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cTen przedmiot ma ju\u017c maksymalny poziom!");
                    player.closeInventory();
                    return;
                }
                boolean bl = useKowalStone = this.playerKowalStoneToggle.getOrDefault(player.getUniqueId(), false) != false && this.upgradeHandler.hasKowalStone(player);
                if (!this.upgradeHandler.checkMaterials(player, nextLevel)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cNie posiadasz wymaganych materia\u0142\u00f3w!");
                    player.closeInventory();
                    return;
                }
                if (!this.upgradeHandler.consumeMaterials(player, nextLevel)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cWyst\u0105pi\u0142 b\u0142\u0105d!");
                    player.closeInventory();
                    return;
                }
                if (useKowalStone && !this.upgradeHandler.consumeKowalStone(player)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cNie posiadasz kamienia kowalskiego!");
                    player.closeInventory();
                    return;
                }
                boolean success = this.upgradeHandler.attemptUpgrade(player, itemToUpgrade, nextLevel, useKowalStone);
                if (success) {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                    dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7aUlepszono!");
                    player.closeInventory();
                } else if (useKowalStone) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                    dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cUlepszenie nie powiod\u0142o si\u0119.");
                    player.closeInventory();
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                    dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cUlepszenie nie powiod\u0142o si\u0119.");
                    player.closeInventory();
                }
                player.getInventory().setItemInMainHand(itemToUpgrade);
                player.updateInventory();
            }
        }
    }
}
