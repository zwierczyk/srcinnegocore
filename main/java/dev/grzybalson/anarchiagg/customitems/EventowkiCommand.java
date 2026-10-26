package dev.grzybalson.anarchiagg.customitems;

import dev.grzybalson.utils.ColorUtils;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.AnarchiaGGPaths;
import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.items.BombaKapitanaHak;
import dev.grzybalson.anarchiagg.customitems.items.Excalibur;
import dev.grzybalson.anarchiagg.customitems.items.HydroTrojzab;
import dev.grzybalson.anarchiagg.customitems.items.Kokos;
import dev.grzybalson.anarchiagg.customitems.items.LinaCecco;
import dev.grzybalson.anarchiagg.customitems.items.NowyExcalibur;
import dev.grzybalson.anarchiagg.customitems.items.RetrospekcjaSkywalkera;
import dev.grzybalson.anarchiagg.customitems.items.RozdzkaIluzjonisty;
import dev.grzybalson.anarchiagg.customitems.items.SuperNieskonczonaMarchewka;
import dev.grzybalson.anarchiagg.customitems.items.UsciskVadera;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import dev.grzybalson.utils.ConfigItemStackReader;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class EventowkiCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String GUI_TITLE = "§b§lᴘʀᴢᴇᴅᴍɪᴏᴛʏ ᴇᴠᴇɴᴛᴏᴡᴇ";
    private static final String EDIT_GUI_TITLE = "§6§lᴇᴅʏᴄᴊᴀ ᴇᴠᴇɴᴛᴏ́ᴡᴇᴋ";
    private static final String ADMIN_PERMISSION = "Holly.eventowki.admin";
    private static final int SUPER_CARROT_SLOT = 41;
    private static final int COBWEB_REMOVER_SLOT = 44;
    private static final int NEW_EXCALIBUR_SLOT = 42;
    private static final int ANARCHIC_HEART_SLOT = 43;
    private static final int KOKOS_SLOT = 45;
    private static final int BOMBA_SLOT = 46;
    private static final int LINA_SLOT = 47;
    private static final int VADER_SLOT = 48;
    private static final int SKYWALKER_SLOT = 49;

    private final JavaPlugin plugin;
    private final File eventowkiFile;
    private YamlConfiguration eventowkiConfig;

    public EventowkiCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.eventowkiFile = AnarchiaGGPaths.getFile(plugin, "customitems/guidata.yml");
        reloadFiles();
    }

    public static void ensureDefaultGuiItems(JavaPlugin plugin) {
        File guiFile = AnarchiaGGPaths.getFile(plugin, "customitems/guidata.yml");
        if (!guiFile.exists()) {
            AnarchiaGGPaths.ensureResource(plugin, "customitems/guidata.yml");
        }

        YamlConfiguration guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        boolean changed = false;

        try (java.io.InputStream stream = plugin.getResource("addons/anarchiagg/customitems/guidata.yml")) {
            if (stream != null) {
                YamlConfiguration resourceConfig = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
                ConfigurationSection resItems = resourceConfig.getConfigurationSection("gui.items");
                if (resItems != null) {
                    for (String key : resItems.getKeys(false)) {
                        if (!guiConfig.isSet("gui.items." + key)) {
                            guiConfig.set("gui.items." + key, resItems.get(key));
                            changed = true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (ConfigItemStackReader.getItem(guiConfig, "gui.items." + SUPER_CARROT_SLOT) == null) {
            guiConfig.set("gui.items." + SUPER_CARROT_SLOT, SuperNieskonczonaMarchewka.createDefaultItem());
            changed = true;
        }

        guiConfig.set("gui.items." + ANARCHIC_HEART_SLOT, dev.grzybalson.anarchiagg.customitems.items.AnarchiczneSerce.createDefaultItem(plugin));
        guiConfig.set("gui.items." + COBWEB_REMOVER_SLOT, dev.grzybalson.anarchiagg.customitems.items.UsuwaczPajeczyn.createDefaultItem());
        ItemStack existingKokos = ConfigItemStackReader.getItem(guiConfig, "gui.items." + KOKOS_SLOT);
        if (existingKokos == null) {
            guiConfig.set("gui.items." + KOKOS_SLOT, Kokos.createDefaultItem(plugin));
            changed = true;
        } else if (existingKokos.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            existingKokos.setType(Material.GOLDEN_APPLE);
            guiConfig.set("gui.items." + KOKOS_SLOT, existingKokos);
            changed = true;
        }
        if (ConfigItemStackReader.getItem(guiConfig, "gui.items." + BOMBA_SLOT) == null) {
            guiConfig.set("gui.items." + BOMBA_SLOT, BombaKapitanaHak.createDefaultItem(plugin));
            changed = true;
        }
        if (ConfigItemStackReader.getItem(guiConfig, "gui.items." + LINA_SLOT) == null) {
            guiConfig.set("gui.items." + LINA_SLOT, LinaCecco.createDefaultItem(plugin));
            changed = true;
        }
        if (ConfigItemStackReader.getItem(guiConfig, "gui.items." + VADER_SLOT) == null) {
            guiConfig.set("gui.items." + VADER_SLOT, UsciskVadera.createDefaultItem(plugin));
            changed = true;
        }
        if (ConfigItemStackReader.getItem(guiConfig, "gui.items." + SKYWALKER_SLOT) == null) {
            guiConfig.set("gui.items." + SKYWALKER_SLOT, RetrospekcjaSkywalkera.createDefaultItem(plugin));
            changed = true;
        }

        // Usuń dodatkowy excalibur (slot 18, 42) z GUI anarchiaitems, zachowując zwykły Excalibur i Usuwacz Pajęczyn
        ConfigurationSection itemsSec = guiConfig.getConfigurationSection("gui.items");
        if (itemsSec != null) {
            for (String key : new ArrayList<>(itemsSec.getKeys(false))) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack item = ConfigItemStackReader.getItem(guiConfig, "gui.items." + key);
                    if (shouldRemoveFromGui(plugin, slot, item)) {
                        guiConfig.set("gui.items." + key, null);
                        changed = true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (!changed) {
            return;
        }

        try {
            guiConfig.save(guiFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Nie udało się zapisać domyślnych itemów do customitems/guidata.yml", exception);
        }
    }


    private static boolean hasCobwebRemoverGuiItem(YamlConfiguration guiConfig) {
        ConfigurationSection items = guiConfig.getConfigurationSection("gui.items");
        if (items == null) {
            return false;
        }
        for (String key : items.getKeys(false)) {
            ItemStack item = ConfigItemStackReader.getItem(guiConfig, "gui.items." + key);
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().toLowerCase(Locale.ROOT).contains("usuwacz")) {
                return true;
            }
        }
        return false;
    }

    private static int findFreeGuiSlot(YamlConfiguration guiConfig, int preferredSlot) {
        if (ConfigItemStackReader.getItem(guiConfig, "gui.items." + preferredSlot) == null) {
            return preferredSlot;
        }
        for (int slot = 0; slot < 54; slot++) {
            if (ConfigItemStackReader.getItem(guiConfig, "gui.items." + slot) == null) {
                return slot;
            }
        }
        return 53;
    }

    private static ItemStack createCobwebRemoverGuiItem() {
        return dev.grzybalson.anarchiagg.customitems.items.UsuwaczPajeczyn.createDefaultItem();
    }

    private static ItemStack createNewExcaliburGuiItem(JavaPlugin plugin) {
        return NowyExcalibur.createConfiguredItem(plugin, 0);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.error("Tylko gracz może otworzyć GUI eventówek."));
                return true;
            }
            openGui(player);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "reload" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage(ColorUtils.noPermission(label + " reload", ADMIN_PERMISSION));
                    return true;
                }
                if (plugin instanceof HollyCore core && core.getAnarchiaGGModuleManager() != null) {
                    core.getAnarchiaGGModuleManager().reloadEventowki();
                } else {
                    reloadFiles();
                    EventItemUseGuard.ensureDefaultCooldowns(plugin);
                    EventItemRegistry.syncAllKnownItemsToConfig(plugin);
                }
                sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Przeładowano GUI eventówek i cache przedmiotów."));
                return true;
            }
            case "editgui" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage(ColorUtils.noPermission(label + " editgui", ADMIN_PERMISSION));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.error("Tylko gracz może edytować GUI eventówek."));
                    return true;
                }
                openEditGui(player);
                return true;
            }
            case "resetujcooldown" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage(ColorUtils.noPermission(label + " resetujcooldown", ADMIN_PERMISSION));
                    return true;
                }
                if (args.length < 2) {
                    ColorUtils.sendUsage(sender, "/" + label + " resetujcooldown <nick|all>");
                    return true;
                }

                if (args[1].equalsIgnoreCase("all")) {
                    int reset = 0;
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        resetPlayerCooldowns(online);
                        reset++;
                    }
                    sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Zresetowano cooldowny dla §f" + reset + " §agraczy."));
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie znaleziono gracza §4" + args[1] + "§c."));
                    return true;
                }

                resetPlayerCooldowns(target);
                sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Zresetowano cooldowny gracza §f" + target.getName() + "§a."));
                target.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Twoje cooldowny eventówek zostały zresetowane."));
                return true;
            }
            case "setkills" -> {
                if (!hasAdminPermission(sender)) {
                    sender.sendMessage(ColorUtils.noPermission(label + " setkills", ADMIN_PERMISSION));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.error("Tylko gracz może ustawić zabójstwa Excalibura."));
                    return true;
                }
                if (args.length < 2) {
                    ColorUtils.sendUsage(sender, "/" + label + " setkills <ilość>");
                    return true;
                }

                int kills;
                try {
                    kills = Integer.parseInt(args[1]);
                } catch (NumberFormatException exception) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cIlość zabójstw musi być liczbą całkowitą."));
                    return true;
                }

                if (kills < 0) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cIlość zabójstw nie może być ujemna."));
                    return true;
                }

                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cMusisz trzymać Excalibur w ręce."));
                    return true;
                }

                boolean isNowyExcalibur = EventItemRegistry.matches(plugin, "nowyexcalibur", item);
                boolean isExcalibur = EventItemRegistry.matches(plugin, "excalibur", item);

                if (isNowyExcalibur) {
                    NowyExcalibur.setKills(plugin, item, kills);
                    player.getInventory().setItemInMainHand(item);
                    sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Ustawiono zabójstwa na Excaliburze na &f" + kills + "&#4EFA76."));
                    return true;
                }

                if (isExcalibur) {
                    Excalibur.setKills(item, kills);
                    player.getInventory().setItemInMainHand(item);
                    sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Ustawiono zabójstwa na Excaliburze na &f" + kills + "&#4EFA76."));
                    return true;
                }

                boolean hasExcaliburName = item.hasItemMeta()
                        && item.getItemMeta().hasDisplayName()
                        && org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT).contains("excalibur");

                if (hasExcaliburName) {
                    boolean looksLikeNowy = false;
                    if (item.getItemMeta().hasLore() && item.getItemMeta().getLore() != null) {
                        for (String line : item.getItemMeta().getLore()) {
                            String stripped = org.bukkit.ChatColor.stripColor(line).toLowerCase(Locale.ROOT);
                            if (stripped.contains("sharpness") || stripped.contains("attack speed") || stripped.contains("⭐")) {
                                looksLikeNowy = true;
                                break;
                            }
                        }
                    }
                    if (looksLikeNowy) {
                        NowyExcalibur.setKills(plugin, item, kills);
                    } else {
                        Excalibur.setKills(item, kills);
                    }
                    player.getInventory().setItemInMainHand(item);
                    sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Ustawiono zabójstwa na Excaliburze na &f" + kills + "&#4EFA76."));
                    return true;
                }

                sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cMusisz trzymać Excalibur w ręce."));
                return true;
            }
            default -> {
                ColorUtils.sendUsage(sender,
                    "/" + label + " gui",
                    "/" + label + " editgui",
                    "/" + label + " reload",
                    "/" + label + " resetujcooldown <nick|all>",
                    "/" + label + " setkills <ilość>"
                );
                return true;
            }
        }
    }

    private void openGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);
        loadGuiContent(gui, true);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        player.openInventory(gui);
    }

    private void openEditGui(Player player) {
        Inventory gui = Bukkit.createInventory(player, 54, EDIT_GUI_TITLE);
        loadGuiContent(gui, false);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
        player.openInventory(gui);
        player.sendMessage(ColorUtils.colorizeComponent("&#FFB800⚠ &8Ξ &eZamknij GUI, aby zapisać zmiany."));
    }

    private static boolean shouldRemoveFromGui(JavaPlugin plugin, int slot, ItemStack item) {
        if (slot == NEW_EXCALIBUR_SLOT) {
            return true;
        }
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        if (EventItemRegistry.matches(plugin, "nowyexcalibur", item)) {
            return true;
        }
        return false;
    }

    private void loadGuiContent(Inventory inventory, boolean prepareTrackedItems) {
        if (eventowkiConfig.contains("gui.items") && eventowkiConfig.getConfigurationSection("gui.items") != null) {
            for (String key : eventowkiConfig.getConfigurationSection("gui.items").getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack item = ConfigItemStackReader.getItem(eventowkiConfig, "gui.items." + slot);
                    if (item == null) {
                        continue;
                    }
                    if (shouldRemoveFromGui(plugin, slot, item)) {
                        continue;
                    }

                    ItemStack finalItem = item.clone();
                    if (slot == ANARCHIC_HEART_SLOT) {
                        finalItem = dev.grzybalson.anarchiagg.customitems.items.AnarchiczneSerce.createDefaultItem(plugin);
                    }
                    if (slot == COBWEB_REMOVER_SLOT) {
                        finalItem = dev.grzybalson.anarchiagg.customitems.items.UsuwaczPajeczyn.createDefaultItem(plugin);
                    }
                    if (prepareTrackedItems) {
                        finalItem = EventItemRegistry.prepareGuiItem(plugin, slot, finalItem);
                    }
                    inventory.setItem(slot, finalItem);
                } catch (NumberFormatException exception) {
                    plugin.getLogger().log(Level.WARNING, "Nieprawidłowy slot w customitems/guidata.yml: " + key, exception);
                }
            }
        }

        ItemStack cobwebRemover = dev.grzybalson.anarchiagg.customitems.items.UsuwaczPajeczyn.createDefaultItem(plugin);
        if (prepareTrackedItems) {
            cobwebRemover = EventItemRegistry.prepareGuiItem(plugin, COBWEB_REMOVER_SLOT, cobwebRemover);
        }
        inventory.setItem(COBWEB_REMOVER_SLOT, cobwebRemover);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!EDIT_GUI_TITLE.equals(event.getView().getTitle())) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!hasAdminPermission(player)) {
            return;
        }

        saveEditGui(event.getInventory());
        player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Zapisano GUI eventówek."));
    }

    private void saveEditGui(Inventory inventory) {
        eventowkiConfig.set("gui.items", null);
        eventowkiConfig.createSection("gui.items");

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            eventowkiConfig.set("gui.items." + slot, item.clone());
        }

        saveGuiConfiguration();
        EventItemRegistry.clearCaches();
        EventItemRegistry.syncAllKnownItemsToConfig(plugin);
    }

    private void resetPlayerCooldowns(Player player) {
        for (Material material : Material.values()) {
            if (material.isItem()) {
                player.setCooldown(material, 0);
            }
        }

        HydroTrojzab.resetCooldowns(player.getUniqueId());
        RozdzkaIluzjonisty.resetCooldowns(player.getUniqueId());
        EventItemUseGuard.clear(player);
    }

    private void reloadFiles() {
        AnarchiaGGSettings.reload(plugin);
        NowyExcalibur.ensureDefaults(plugin);
        ensureDefaultGuiItems(plugin);
        EventItemUseGuard.ensureDefaultCooldowns(plugin);
        EventItemRegistry.clearCaches();
        this.eventowkiConfig = YamlConfiguration.loadConfiguration(this.eventowkiFile);
    }

    private void saveGuiConfiguration() {
        try {
            eventowkiConfig.save(eventowkiFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Nie udało się zapisać customitems/guidata.yml", exception);
        }
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission(ADMIN_PERMISSION) || sender.isOp();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("gui");
            if (hasAdminPermission(sender)) {
                suggestions.addAll(Arrays.asList("editgui", "reload", "resetujcooldown", "setkills"));
            }
            return filterSuggestions(suggestions, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("setkills") && hasAdminPermission(sender)) {
            return filterSuggestions(Arrays.asList("0", "10", "50", "100"), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("resetujcooldown") && hasAdminPermission(sender)) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("all");
            for (Player online : Bukkit.getOnlinePlayers()) {
                suggestions.add(online.getName());
            }
            return filterSuggestions(suggestions, args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filterSuggestions(List<String> values, String current) {
        String lowered = current.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered))
                .distinct()
                .sorted()
                .toList();
    }
}
