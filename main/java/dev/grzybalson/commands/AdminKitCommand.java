package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.KitConfig;
import dev.grzybalson.gui.KitEditGUI;
import dev.grzybalson.gui.KitGUI;
import dev.grzybalson.modules.kit.Kit;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.HeadUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class AdminKitCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public AdminKitCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTa komenda jest tylko dla graczy!"));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.akit")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.akit"));
            return true;
        }

        if (args.length < 1) {
            sendHelp(player);
            return true;
        }

        KitConfig config = plugin.getConfigManager().getKitConfig();
        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "reload" -> handleReload(player, config);
            case "create" -> handleCreate(player, args, config);
            case "delete" -> handleDelete(player, args, config);
            case "edit" -> handleEdit(player, args, config);
            case "setitems" -> handleSetItems(player, args, config);
            case "setprefix" -> handleSetPrefix(player, args, config);
            case "seticon", "icon" -> handleSetIcon(player, args, config);
            case "addlore", "lore-add" -> handleAddLore(player, args, config);
            case "removelore", "lore-remove" -> handleRemoveLore(player, args, config);
            case "list" -> handleList(player, config);
            case "gui" -> {
                new KitGUI(plugin, player).open();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
            }
            case "resetcooldown" -> handleResetCooldown(player, args, config);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleReload(Player player, KitConfig config) {
        if (plugin.getConfigManager() != null && plugin.getConfigManager().getKitConfig() != null) {
            plugin.getConfigManager().getKitConfig().load();
        }
        if (plugin.getModuleConfigManager() != null) {
            if (plugin.getModuleConfigManager().getKitsConfig() != null) {
                plugin.getModuleConfigManager().getKitsConfig().load(true);
            }
            if (plugin.getModuleConfigManager().getGuiTemplateConfig() != null) {
                plugin.getModuleConfigManager().getGuiTemplateConfig().load(true);
            }
        }
        if (plugin.getConfigManager() != null && plugin.getConfigManager().getGuiTemplateConfig() != null) {
            plugin.getConfigManager().getGuiTemplateConfig().load(true);
        }
        if (plugin.getKitManager() != null) {
            plugin.getKitManager().loadKits();
        }
        player.sendMessage(ColorUtils.colorizeComponent(config.getKitReloadedMessage()));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    private void handleCreate(Player player, String[] args, KitConfig config) {
        if (args.length < 7) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getHelpCreate()));
            return;
        }

        String kitName = args[1];
        if (plugin.getKitManager().getKit(kitName) != null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getAlreadyExists().replace("{NAME}", kitName)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }

        String displayName = args[2].replace("_", " ");
        String iconRaw = args[3];
        boolean texture = iconRaw.length() > 30;
        Material iconMaterial = Material.CHEST;
        String headValue = "";

        if (!texture) {
            try {
                iconMaterial = Material.valueOf(iconRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                player.sendMessage(ColorUtils.colorizeComponent(config.getNotFound().replace("{NAME}", iconRaw + " (materiał)")));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
                return;
            }
        } else {
            iconMaterial = Material.PLAYER_HEAD;
            headValue = iconRaw;
        }

        // Optional headValue between icon and permission (HollyCore style).
        int offset = 0;
        if (!texture && iconMaterial == Material.PLAYER_HEAD && args.length >= 8) {
            headValue = args[4];
            offset = 1;
        }
        if (args.length < 7 + offset) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getHelpCreate()));
            return;
        }

        String permission = args[4 + offset];
        int cooldown = Kit.parseCooldownSeconds(args[5 + offset]);
        int slot;
        try {
            slot = Integer.parseInt(args[6 + offset]);
        } catch (NumberFormatException exception) {
            player.sendMessage(ColorUtils.colorizeComponent(" &#FF5C5C&l✖ &#C1C1C1Slot musi być liczbą!"));
            return;
        }

        ItemStack icon = new ItemStack(iconMaterial);
        if (iconMaterial == Material.PLAYER_HEAD && !headValue.isBlank()) {
            icon = HeadUtils.setHeadTextureLikeShop(icon, headValue);
        }

        Kit kit = new Kit(kitName);
        kit.setDisplayName(displayName);
        kit.setPermission(permission);
        kit.setCooldown(cooldown);
        kit.setSlot(slot);
        kit.setIcon(icon);
        kit.setPrefix(displayName);

        plugin.getKitManager().saveKit(kit);
        player.sendMessage(ColorUtils.colorizeComponent(config.getCreated().replace("{NAME}", kitName)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    private void handleDelete(Player player, String[] args, KitConfig config) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getHelpDelete()));
            return;
        }
        String kitName = args[1];
        if (plugin.getKitManager().getKit(kitName) == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getNotFound().replace("{NAME}", kitName)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }
        plugin.getKitManager().deleteKit(kitName);
        player.sendMessage(ColorUtils.colorizeComponent(config.getDeleted().replace("{NAME}", kitName)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    private void handleEdit(Player player, String[] args, KitConfig config) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getHelpEdit()));
            return;
        }
        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getNotFound().replace("{NAME}", args[1])));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }
        new KitEditGUI(plugin, player, kit).open();
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    private void handleSetItems(Player player, String[] args, KitConfig config) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getHelpSetitems()));
            return;
        }
        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getNotFound().replace("{NAME}", args[1])));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }

        ItemStack[] contents = player.getInventory().getContents();
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        plugin.getKitManager().setKitItemsFromInventory(args[1], contents);
        player.sendMessage(ColorUtils.colorizeComponent(config.getItemsSaved()
            .replace("{COUNT}", String.valueOf(count))
            .replace("{NAME}", kit.getName())));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    private void handleSetPrefix(Player player, String[] args, KitConfig config) {
        if (args.length < 3) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getHelpSetprefix()));
            return;
        }
        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getNotFound().replace("{NAME}", args[1])));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).replace("_", " ");
        kit.setPrefix(text);
        plugin.getKitManager().saveKit(kit);
        player.sendMessage(ColorUtils.colorizeComponent(config.getPrefixSet()
            .replace("{NAME}", kit.getName())
            .replace("{PREFIX}", kit.getPrefix())
            .replace("{KIT}", kit.getPrefix())));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    private void handleSetIcon(Player player, String[] args, KitConfig config) {
        if (args.length < 3) {
            player.sendMessage(ColorUtils.colorizeComponent(
                "  &#89E2FF/akit seticon &8<&#C1C1C1nazwa&8> <&#C1C1C1MATERIAL|head-value&8>"
            ));
            return;
        }
        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getNotFound().replace("{NAME}", args[1])));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }
        String iconRaw = args[2];
        if (!plugin.getKitManager().setKitIcon(kit.getName(), iconRaw)) {
            player.sendMessage(ColorUtils.colorizeComponent(
                " &#FF5C5C&l✖ &#C1C1C1Nieprawidłowa ikona. Podaj MATERIAL (np. DIAMOND_SWORD) albo base64 head-value."
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }
        player.sendMessage(ColorUtils.colorizeComponent(
            " &#60FF8B&l✔ &#C1C1C1Ustawiono ikonę GUI dla kita &#FFFFFF&l" + kit.getName()
        ));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    private void handleAddLore(Player player, String[] args, KitConfig config) {
        if (args.length < 3) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getHelpAddlore()));
            return;
        }
        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getNotFound().replace("{NAME}", args[1])));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).replace("_", " ");
        kit.getLore().add(text);
        plugin.getKitManager().saveKit(kit);
        player.sendMessage(ColorUtils.colorizeComponent(config.getLoreAdded().replace("{NAME}", kit.getName())));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    private void handleRemoveLore(Player player, String[] args, KitConfig config) {
        if (args.length < 3) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getHelpRemovelore()));
            return;
        }
        Kit kit = plugin.getKitManager().getKit(args[1]);
        if (kit == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getNotFound().replace("{NAME}", args[1])));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }
        int line;
        try {
            line = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getNotFound().replace("{NAME}", "linia " + args[2])));
            return;
        }
        int index = line - 1;
        if (index < 0 || index >= kit.getLore().size()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getNotFound().replace("{NAME}", "linia " + line)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
            return;
        }
        kit.getLore().remove(index);
        plugin.getKitManager().saveKit(kit);
        player.sendMessage(ColorUtils.colorizeComponent(config.getLoreRemoved()
            .replace("{LINE}", String.valueOf(line))
            .replace("{NAME}", kit.getName())));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    private void handleList(Player player, KitConfig config) {
        player.sendMessage(ColorUtils.colorizeComponent(config.getHeader()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getTitle()));
        if (plugin.getKitManager().getAllKits().isEmpty()) {
            player.sendMessage(ColorUtils.colorizeComponent("  &#AAAAAA  ʙʀᴀᴋ ᴋɪᴛᴏᴡ"));
        } else {
            for (Kit kit : plugin.getKitManager().getAllKits()) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "  &8- &f" + kit.getName()
                        + " &8— &#AAAAAA" + kit.getDisplayName()
                        + " &8| &#AAAAAA" + Kit.formatTime(kit.getCooldown())
                        + " &8| &#AAAAAAslot " + kit.getSlot()
                        + " &8| &#AAAAAA" + kit.getItems().size() + " items"
                ));
            }
        }
        player.sendMessage(ColorUtils.colorizeComponent(config.getHeader()));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
    }

    private void handleResetCooldown(Player player, String[] args, KitConfig config) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getHelpResetcooldown()));
            return;
        }

        String targetName = args[1];
        String kitName = args.length >= 3 ? args[2] : "*";
        boolean allPlayers = targetName.equals("*") || targetName.equalsIgnoreCase("all");
        boolean allKits = kitName.equals("*") || kitName.equalsIgnoreCase("all");

        if (allPlayers) {
            int players = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (allKits) {
                    plugin.getKitManager().resetAllCooldowns(online.getUniqueId());
                } else {
                    if (plugin.getKitManager().getKit(kitName) == null) {
                        player.sendMessage(ColorUtils.colorizeComponent(config.getNotFound().replace("{NAME}", kitName)));
                        return;
                    }
                    plugin.getKitManager().resetCooldown(online.getUniqueId(), kitName);
                }
                players++;
            }
            player.sendMessage(ColorUtils.colorizeComponent(
                " &#60FF8B&l✔ &#C1C1C1Zresetowano cooldowny "
                    + (allKits ? "wszystkich kitów" : "kita &#FFFFFF" + kitName + "&#C1C1C1")
                    + " dla &#FFFFFF" + players + " &#C1C1C1graczy."
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ColorUtils.colorizeComponent(" &#FF5C5C&l✖ &#C1C1C1Gracz nie jest online!"));
            return;
        }

        if (allKits) {
            plugin.getKitManager().resetAllCooldowns(target.getUniqueId());
            player.sendMessage(ColorUtils.colorizeComponent(
                " &#60FF8B&l✔ &#C1C1C1Zresetowano cooldowny wszystkich kitów gracza &#FFFFFF" + target.getName()
            ));
        } else {
            if (plugin.getKitManager().getKit(kitName) == null) {
                player.sendMessage(ColorUtils.colorizeComponent(config.getNotFound().replace("{NAME}", kitName)));
                return;
            }
            plugin.getKitManager().resetCooldown(target.getUniqueId(), kitName);
            player.sendMessage(ColorUtils.colorizeComponent(config.getKitCooldownResetMessage()
                .replace("{NAME}", kitName)
                .replace("{kit}", kitName)
                .replace("{PLAYER}", target.getName())
                .replace("{player}", target.getName())));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    private void sendHelp(Player player) {
        KitConfig config = plugin.getConfigManager().getKitConfig();
        player.sendMessage(ColorUtils.colorizeComponent(config.getHeader()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getTitle()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHelpCreate()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHelpDelete()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHelpSetitems()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHelpAddlore()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHelpRemovelore()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHelpSetprefix()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHelpSeticon()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHelpEdit()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHelpList()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHelpGui()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHelpReload()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHelpResetcooldown()));
        player.sendMessage(ColorUtils.colorizeComponent(config.getHeader()));
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (!sender.hasPermission("Holly.cmd.akit")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(args[0], List.of(
                "reload", "create", "delete", "edit", "setitems", "setprefix", "seticon", "icon",
                "addlore", "removelore", "list", "gui", "resetcooldown"
            ));
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "delete", "edit", "setitems", "setprefix", "seticon", "icon", "addlore", "removelore" -> kitNames(args[1]);
                case "resetcooldown" -> {
                    List<String> names = new ArrayList<>();
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                            names.add(online.getName());
                        }
                    }
                    if ("*".startsWith(args[1]) || "all".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        names.add("*");
                    }
                    yield names;
                }
                case "create" -> List.of("<nazwa>");
                default -> List.of();
            };
        }

        if (sub.equals("create")) {
            return switch (args.length) {
                case 3 -> List.of("<displayName>");
                case 4 -> materials(args[3]);
                case 5 -> List.of("Holly.kit.<nazwa>");
                case 6 -> List.of("1h", "30m", "1h30m", "2d", "12h", "86400");
                case 7 -> List.of("10", "11", "12", "13", "14", "15", "16");
                default -> List.of();
            };
        }

        if (sub.equals("setprefix") && args.length == 3) {
            return List.of("<tekst>");
        }
        if ((sub.equals("addlore") || sub.equals("lore-add")) && args.length == 3) {
            return List.of("<tekst>");
        }
        if ((sub.equals("removelore") || sub.equals("lore-remove")) && args.length == 3) {
            return List.of("1", "2", "3");
        }
        if (sub.equals("resetcooldown") && args.length == 3) {
            List<String> names = kitNames(args[2]);
            if ("*".startsWith(args[2]) || "all".startsWith(args[2].toLowerCase(Locale.ROOT))) {
                names.add("*");
            }
            return names;
        }
        return List.of();
    }

    private List<String> kitNames(String input) {
        List<String> names = new ArrayList<>();
        String needle = input.toLowerCase(Locale.ROOT);
        for (Kit kit : plugin.getKitManager().getAllKits()) {
            if (kit.getName().toLowerCase(Locale.ROOT).startsWith(needle)) {
                names.add(kit.getName());
            }
        }
        return names;
    }

    private List<String> materials(String input) {
        String needle = input.toUpperCase(Locale.ROOT);
        return Stream.of(Material.values())
            .filter(Material::isItem)
            .map(Material::name)
            .filter(name -> name.startsWith(needle))
            .limit(20)
            .toList();
    }

    private List<String> filter(String input, List<String> values) {
        String needle = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(needle)) {
                out.add(value);
            }
        }
        return out;
    }
}
