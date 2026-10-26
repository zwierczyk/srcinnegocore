package dev.grzybalson.anarchiagg.szafa;

import dev.grzybalson.utils.ColorUtils;

import dev.grzybalson.anarchiagg.szafa.gui.SzafaGUIHandler;
import dev.grzybalson.anarchiagg.szafa.gui.SzafaGUIListener;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyHandler;
import dev.grzybalson.anarchiagg.szafa.papugi.PapugiHandler;
import dev.grzybalson.anarchiagg.szafa.pety.PetyHandler;
import dev.grzybalson.utils.NBTUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class ItemCommand implements CommandExecutor, TabCompleter, Listener {
    private static final int GUI_SIZE = 54;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int[] CONTENT_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final JavaPlugin plugin;
    private final KostiumyHandler kostiumyHandler;
    private final PetyHandler petyHandler;
    private final PapugiHandler papugiHandler;
    private final SzafaGUIHandler guiHandler;
    private final boolean costumesOnly;

    public ItemCommand(JavaPlugin plugin, KostiumyHandler kostiumyHandler, PetyHandler petyHandler, PapugiHandler papugiHandler, SzafaGUIHandler guiHandler) {
        this.plugin = plugin;
        this.kostiumyHandler = kostiumyHandler;
        this.petyHandler = petyHandler;
        this.papugiHandler = papugiHandler;
        this.guiHandler = guiHandler;
        this.costumesOnly = petyHandler == null;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean rejectDisabledFeature(CommandSender sender, String featureName) {
        if (!this.costumesOnly) {
            return false;
        }
        sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cModuł §4" + featureName + " §cjest wyłączony w tej wersji szafki."));
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("Holly.szafka.admin") && !sender.hasPermission("Holly.szafa.admin")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.szafka.admin"));
            return true;
        }
        if (args.length == 0) {
            this.sendUsageMessage(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("przeladuj")) {
            try {
                if (!this.costumesOnly) {
                    SzafkaConfig.ensureDefaults(this.plugin);
                }
                this.kostiumyHandler.reload();
                if (!this.costumesOnly) {
                    this.petyHandler.reload();
                    this.papugiHandler.reload();
                }
                this.guiHandler.reload();
                sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Pomyślnie przeładowano konfigurację szafki!"));
            } catch (Exception exception) {
                sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cBłąd podczas przeładowywania konfiguracji: " + exception.getMessage()));
                exception.printStackTrace();
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("savekostium")) {
            if (args.length == 2 && sender instanceof Player player) {
                String itemName = args[1];
                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                if (itemInHand.getType().isAir()) {
                    player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cMusisz trzymać przedmiot w ręce, aby go zapisać!"));
                    return true;
                }
                this.kostiumyHandler.saveItem(itemName, itemInHand);
                player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Zapisano przedmiot §2" + itemName));
                return true;
            }
            ColorUtils.sendUsage(sender, "/aszafka savekostium <nazwa>");
            return true;
        }
        if (args[0].equalsIgnoreCase("savepet")) {
            if (rejectDisabledFeature(sender, "petów")) {
                return true;
            }
            if (args.length == 2 && sender instanceof Player player) {
                String itemName = args[1];
                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                if (itemInHand.getType().isAir()) {
                    player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cMusisz trzymać przedmiot w ręce, aby go zapisać!"));
                    return true;
                }
                this.petyHandler.saveItem(itemName, itemInHand);
                player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Zapisano przedmiot peta §2" + itemName));
                return true;
            }
            ColorUtils.sendUsage(sender, "/aszafka savepet <nazwa>");
            return true;
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length < 2) {
                this.sendUsageMessage(sender);
                return true;
            }
            String giveType = args[1].toLowerCase(Locale.ROOT);
            boolean petFeature = giveType.equals("pet") || giveType.equals("zwoj-pet") || giveType.equals("papuga");
            if (petFeature && rejectDisabledFeature(sender, giveType.equals("papuga") ? "papug" : "petów")) {
                return true;
            }
            if ((giveType.equals("kostium") || giveType.equals("pet")) && args.length == 5 && args[2].equalsIgnoreCase("gui")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTę wersję komendy możesz wykonać tylko jako gracz."));
                    return true;
                }
                int amount;
                int days;
                try {
                    amount = Integer.parseInt(args[3]);
                    days = Integer.parseInt(args[4]);
                } catch (NumberFormatException exception) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cIlość i dni muszą być liczbami całkowitymi."));
                    return true;
                }
                if (amount < 1 || amount > 64) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cIlość musi być w zakresie od §41 §cdo §464§c."));
                    return true;
                }
                if (days < 1) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cLiczba dni musi być większa od zera."));
                    return true;
                }
                this.openGiveSelectionGui(player, giveType, amount, days, 0);
                return true;
            }
            if (giveType.equals("kostium")) {
                if (args.length != 5) {
                    ColorUtils.sendUsage(sender, "/aszafka give kostium <nazwa> <nick> <czas>");
                    return true;
                }
                String kostiumKey = args[2];
                String playerName = args[3];
                String duration = args[4];
                KostiumyHandler.KostiumData data = this.kostiumyHandler.getKostiumData(kostiumKey);
                Player target = Bukkit.getPlayer(playerName);
                if (data == null) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cKostium o nazwie §4" + kostiumKey + " §cnie istnieje!"));
                    return true;
                }
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cGracz §4" + playerName + "§c nie jest online!"));
                    return true;
                }
                ItemStack kostiumItem = this.createTimeKostiumItem(kostiumKey, data, duration);
                this.giveItemToPlayer(target, kostiumItem, "&#FF0000✘ &8Ξ &cTwój ekwipunek jest pełny, kostium został upuszczony na ziemię!");
                sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Nadano kostium §2" + ColorUtils.colorize(data.displayName().replace("%czas%", duration)) + "§a graczowi §2" + playerName));
                return true;
            }
            if (giveType.equals("zwoj-kostium")) {
                if (args.length != 4) {
                    ColorUtils.sendUsage(sender, "/aszafka give zwoj-kostium <czas> <nick>");
                    return true;
                }
                String duration = args[2];
                String playerName = args[3];
                Player target = Bukkit.getPlayer(playerName);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cGracz §4" + playerName + " §cnie jest online!"));
                    return true;
                }
                ItemStack zwojItem = this.createZwojKostiumItem(duration);
                this.giveItemToPlayer(target, zwojItem, "&#FF0000✘ &8Ξ &cTwój ekwipunek jest pełny, zwój został upuszczony na ziemię!");
                sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Nadano Zwój Przedłużenia Kostiumu na czas §2" + duration + " §agraczowi §2" + playerName));
                return true;
            }
            if (giveType.equals("pet")) {
                if (args.length != 5) {
                    ColorUtils.sendUsage(sender, "/aszafka give pet <nazwa> <nick> <czas>");
                    return true;
                }
                String petKey = args[2];
                String playerName = args[3];
                String duration = args[4];
                PetyHandler.PetData data = this.petyHandler.getPety().get(petKey);
                Player target = Bukkit.getPlayer(playerName);
                if (data == null) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cPet o nazwie §4" + petKey + " §cnie istnieje!"));
                    return true;
                }
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cGracz §4" + playerName + "§c nie jest online."));
                    return true;
                }
                ItemStack petItem = this.createTimePetItem(petKey, data, duration);
                this.giveItemToPlayer(target, petItem, "&#FF0000✘ &8Ξ &cTwój ekwipunek jest pełny, pet został upuszczony na ziemię!");
                sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Nadano peta §2" + ColorUtils.colorize(data.displayName().replace("%czas%", duration)) + "§a graczowi " + playerName));
                return true;
            }
            if (giveType.equals("zwoj-pet")) {
                if (args.length != 4) {
                    ColorUtils.sendUsage(sender, "/aszafka give zwoj-pet <czas> <nick>");
                    return true;
                }
                String duration = args[2];
                String playerName = args[3];
                Player target = Bukkit.getPlayer(playerName);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ColorUtils.error("Gracz '" + playerName + "' nie jest online."));
                    return true;
                }
                ItemStack zwojItem = this.createZwojPetItem(duration);
                this.giveItemToPlayer(target, zwojItem, "&#FF0000✘ &8Ξ &cTwój ekwipunek jest pełny, zwój został upuszczony na ziemię!");
                sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Nadano Zwój Przedłużenia Peta na czas §2" + duration + " §agraczowi §2" + playerName));
                return true;
            }
            if (giveType.equals("papuga")) {
                if (args.length != 5) {
                    ColorUtils.sendUsage(sender, "/aszafka give papuga <nazwa> <nick> <czas>");
                    return true;
                }
                String papugaKey = args[2];
                String playerName = args[3];
                String duration = args[4];
                PapugiHandler.PapugaData data = this.papugiHandler.getPapuga(papugaKey);
                Player target = Bukkit.getPlayer(playerName);
                if (data == null) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cPapuga o nazwie §4" + papugaKey + " §cnie istnieje!"));
                    return true;
                }
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cGracz §4" + playerName + " §cnie jest online!"));
                    return true;
                }
                ItemStack papugaItem = this.createTimePapugaItem(papugaKey, duration);
                this.giveItemToPlayer(target, papugaItem, "&#FF0000✘ &8Ξ &cTwój ekwipunek jest pełny, papuga została upuszczona na ziemię!");
                String resolvedPapugaName = this.papugiHandler.resolveActivationDisplayName(papugaItem, papugaKey, ChatColor.translateAlternateColorCodes('&', data.displayName().replace("%czas%", duration)));
                sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Nadano papugę §2" + resolvedPapugaName + "§a graczowi §2" + playerName));
                return true;
            }
        }
        this.sendUsageMessage(sender);
        return true;
    }

    private void giveItemToPlayer(Player player, ItemStack item, String fullInventoryMessage) {
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), item);
            player.sendMessage(ColorUtils.colorizeComponent(fullInventoryMessage));
        } else {
            player.getInventory().addItem(item);
        }
    }

    private void giveMultipleItemsToPlayer(Player player, ItemStack baseItem, int amount, String fullInventoryMessage) {
        boolean droppedAny = false;
        for (int i = 0; i < amount; i++) {
            ItemStack item = baseItem.clone();
            item.setAmount(1);
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItem(player.getLocation(), item);
                droppedAny = true;
            } else {
                player.getInventory().addItem(item);
            }
        }
        if (droppedAny) {
            player.sendMessage(ColorUtils.colorizeComponent(fullInventoryMessage));
        }
    }

    private void sendUsageMessage(CommandSender sender) {
        if (this.costumesOnly) {
            ColorUtils.sendUsage(sender,
                "/aszafka reload",
                "/aszafka savekostium <nazwa>",
                "/aszafka give kostium gui <ilość> <dni>",
                "/aszafka give kostium <nazwa> <nick> <czas>",
                "/aszafka give zwoj-kostium <czas> <nick>"
            );
            return;
        }
        ColorUtils.sendUsage(sender,
            "/aszafka reload",
            "/aszafka savekostium <nazwa>",
            "/aszafka savepet <nazwa>",
            "/aszafka give kostium gui <ilość> <dni>",
            "/aszafka give pet gui <ilość> <dni>",
            "/aszafka give kostium <nazwa> <nick> <czas>",
            "/aszafka give zwoj-kostium <czas> <nick>",
            "/aszafka give pet <nazwa> <nick> <czas>",
            "/aszafka give zwoj-pet <czas> <nick>",
            "/aszafka give papuga <nazwa> <nick> <czas>"
        );
    }

    private ItemStack createTimeKostiumItem(String kostiumKey, KostiumyHandler.KostiumData data, String duration) {
        ItemStack item = data.displayItem() != null ? data.displayItem().clone() : new ItemStack(data.type());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String displayName = ColorUtils.colorize(data.displayName().replace("%czas%", duration));
        meta.setDisplayName(displayName);
        ArrayList<String> lore = new ArrayList<>();
        for (String line : data.lore()) {
            String formattedLine = line.replace("%uzycie%", "Brak").replace("%czas%", duration);
            lore.add(ColorUtils.colorize(formattedLine));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        String effectiveColor = KostiumyHandler.getEffectiveColor(kostiumKey, data.displayName(), data.color());
        if (item.getType().name().startsWith("LEATHER_") && effectiveColor != null) {
            this.guiHandler.applyColorToLeatherArmor(item, effectiveColor);
        }
        NBTUtils.setString(item, SzafaGUIListener.KOSTIUM_DURATION_TAG, duration);
        if (kostiumKey != null && !kostiumKey.isBlank()) {
            NBTUtils.setString(item, SzafaGUIListener.KOSTIUM_KEY_TAG, kostiumKey);
        }
        return item;
    }

    private ItemStack createZwojKostiumItem(String duration) {
        KostiumyHandler.KostiumData zwojData = this.kostiumyHandler.getKostiumy().get("zwoj-kostium");
        ItemStack item = new ItemStack(Material.FLOWER_BANNER_PATTERN);
        ItemMeta meta = item.getItemMeta();
        if (meta == null || zwojData == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', zwojData.displayName()));
        ArrayList<String> lore = new ArrayList<>();
        for (String line : zwojData.lore()) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line.replace("%czas%", duration)));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTimePetItem(String petKey, PetyHandler.PetData data, String duration) {
        ItemStack item = this.petyHandler.getPetItem(data.itemKey());
        item = item == null ? new ItemStack(Material.STONE) : item.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String displayName = ColorUtils.colorize(data.displayName().replace("%czas%", duration));
        meta.setDisplayName(displayName);
        ArrayList<String> lore = new ArrayList<>();
        for (String line : data.lore()) {
            String formattedLine = line.replace("%uzycie%", "Brak").replace("%czas%", duration);
            lore.add(ColorUtils.colorize(formattedLine));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        if (item.getType().name().startsWith("LEATHER_") && data.color() != null) {
            this.guiHandler.applyColorToLeatherArmor(item, data.color());
        }
        NBTUtils.setString(item, SzafaGUIListener.PET_DURATION_TAG, duration);
        if (petKey != null && !petKey.isBlank()) {
            NBTUtils.setString(item, SzafaGUIListener.PET_KEY_TAG, petKey);
        }
        return item;
    }

    private ItemStack createTimePapugaItem(String papugaKey, String duration) {
        return this.papugiHandler.createItemForGive(papugaKey, duration);
    }

    private ItemStack createZwojPetItem(String duration) {
        PetyHandler.PetData zwojData = this.petyHandler.getPety().get("zwoj-pet");
        ItemStack item = new ItemStack(Material.FLOWER_BANNER_PATTERN);
        ItemMeta meta = item.getItemMeta();
        if (meta == null || zwojData == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', zwojData.displayName()));
        ArrayList<String> lore = new ArrayList<>();
        for (String line : zwojData.lore()) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line.replace("%czas%", duration)));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }


    private void openGiveSelectionGui(Player player, String type, int amount, int days, int page) {
        AdminGiveHolder holder = new AdminGiveHolder(type, amount, days, page);
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, this.getAdminGuiTitle(type, page));
        holder.setInventory(inventory);

        for (int slot = 0; slot < GUI_SIZE; slot++) {
            inventory.setItem(slot, this.createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        List<String> keys = this.getAvailableKeys(type);
        int startIndex = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex >= keys.size()) {
                break;
            }
            String key = keys.get(dataIndex);
            ItemStack displayItem = this.createDisplayItemForKey(type, key, amount, days);
            if (displayItem != null) {
                inventory.setItem(CONTENT_SLOTS[i], displayItem);
                holder.keyBySlot.put(CONTENT_SLOTS[i], key);
            }
        }

        if (page > 0) {
            inventory.setItem(PREV_PAGE_SLOT, this.createGuiItem(Material.ARROW, "§aPoprzednia strona", List.of("§7Kliknij, aby przejść wstecz.")));
        } else {
            inventory.setItem(PREV_PAGE_SLOT, this.createGuiItem(Material.BARRIER, "§8Brak poprzedniej strony"));
        }

        int maxPage = Math.max(0, (keys.size() - 1) / CONTENT_SLOTS.length);
        if (page < maxPage) {
            inventory.setItem(NEXT_PAGE_SLOT, this.createGuiItem(Material.ARROW, "§aNastępna strona", List.of("§7Kliknij, aby przejść dalej.")));
        } else {
            inventory.setItem(NEXT_PAGE_SLOT, this.createGuiItem(Material.BARRIER, "§8Brak następnej strony"));
        }

        inventory.setItem(CLOSE_SLOT, this.createGuiItem(Material.BARRIER, "§cZamknij", List.of("§7Typ: §f" + type, "§7Ilość: §f" + amount, "§7Czas: §f" + days + " dni")));
        player.openInventory(inventory);
    }

    private String getAdminGuiTitle(String type, int page) {
        String prettyType = type.equalsIgnoreCase("kostium") ? "Kostiumy" : "Pety";
        return "§7Wybierz " + prettyType + " §7(§f" + (page + 1) + "§7)";
    }

    private List<String> getAvailableKeys(String type) {
        if (type.equalsIgnoreCase("kostium")) {
            return this.kostiumyHandler.getKostiumy().keySet().stream()
                    .filter(key -> !key.equalsIgnoreCase("zwoj-kostium") && !(key.equalsIgnoreCase("grincz") && this.kostiumyHandler.getKostiumy().containsKey("grinch")))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (type.equalsIgnoreCase("pet") && this.petyHandler != null) {
            return this.petyHandler.getPety().keySet().stream()
                    .filter(key -> !key.equalsIgnoreCase("zwoj-pet"))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return Collections.emptyList();
    }

    private ItemStack createDisplayItemForKey(String type, String key, int amount, int days) {
        String duration = days + "d";
        if (type.equalsIgnoreCase("kostium")) {
            KostiumyHandler.KostiumData data = this.kostiumyHandler.getKostiumy().get(key);
            if (data == null) {
                return null;
            }
            ItemStack item = this.createTimeKostiumItem(key, data, duration);
            item.setAmount(1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(" ");
                lore.add("§8• §7ID: §f" + key);
                lore.add("§8• §7Po kliknięciu dostaniesz: §f" + amount + "x");
                lore.add("§8• §7Czas: §f" + days + " dni");
                lore.add(" ");
                lore.add("§aKliknij, aby odebrać.");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }
        if (type.equalsIgnoreCase("pet") && this.petyHandler != null) {
            PetyHandler.PetData data = this.petyHandler.getPety().get(key);
            if (data == null) {
                return null;
            }
            ItemStack item = this.createTimePetItem(key, data, duration);
            item.setAmount(1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(" ");
                lore.add("§8• §7ID: §f" + key);
                lore.add("§8• §7Po kliknięciu dostaniesz: §f" + amount + "x");
                lore.add("§8• §7Czas: §f" + days + " dni");
                lore.add(" ");
                lore.add("§aKliknij, aby odebrać.");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }
        return null;
    }

    private ItemStack createGuiItem(Material material, String name) {
        return this.createGuiItem(material, name, Collections.emptyList());
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onAdminGiveInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof AdminGiveHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }
        if (slot == PREV_PAGE_SLOT && holder.page > 0) {
            this.openGiveSelectionGui(player, holder.type, holder.amount, holder.days, holder.page - 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }
        if (slot == NEXT_PAGE_SLOT) {
            int maxPage = Math.max(0, (this.getAvailableKeys(holder.type).size() - 1) / CONTENT_SLOTS.length);
            if (holder.page < maxPage) {
                this.openGiveSelectionGui(player, holder.type, holder.amount, holder.days, holder.page + 1);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            return;
        }

        String selectedKey = holder.getKeyBySlot(slot);
        if (selectedKey == null) {
            return;
        }

        String duration = holder.days + "d";
        ItemStack rewardItem;
        String successName;
        if (holder.type.equalsIgnoreCase("kostium")) {
            KostiumyHandler.KostiumData data = this.kostiumyHandler.getKostiumy().get(selectedKey);
            if (data == null) {
                player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie znaleziono wybranego kostiumu."));
                return;
            }
            rewardItem = this.createTimeKostiumItem(selectedKey, data, duration);
            successName = ColorUtils.colorize(data.displayName().replace("%czas%", duration));
        } else if (holder.type.equalsIgnoreCase("pet")) {
            PetyHandler.PetData data = this.petyHandler.getPety().get(selectedKey);
            if (data == null) {
                player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie znaleziono wybranego peta."));
                return;
            }
            rewardItem = this.createTimePetItem(selectedKey, data, duration);
            successName = ColorUtils.colorize(data.displayName().replace("%czas%", duration));
        } else {
            return;
        }

        this.giveMultipleItemsToPlayer(player, rewardItem, holder.amount, "&#FF0000✘ &8Ξ &cTwój ekwipunek jest pełny, część przedmiotów została upuszczona na ziemię!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendMessage(ColorUtils.success("Otrzymano &2" + holder.amount + "x &a" + successName + " &7na &f" + holder.days + " dni&7."));
        // GUI celowo pozostaje otwarte, aby administrator mógł nadać kolejne elementy.
    }

    private static final class AdminGiveHolder implements InventoryHolder {
        private final String type;
        private final int amount;
        private final int days;
        private final int page;
        private final Map<Integer, String> keyBySlot = new java.util.HashMap<>();
        private Inventory inventory;

        private AdminGiveHolder(String type, int amount, int days, int page) {
            this.type = type;
            this.amount = amount;
            this.days = days;
            this.page = page;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }

        private String getKeyBySlot(int slot) {
            return this.keyBySlot.get(slot);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("Holly.szafka.admin") && !sender.hasPermission("Holly.szafa.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            Stream<String> roots = this.costumesOnly
                ? Stream.of("reload", "savekostium", "give")
                : Stream.of("reload", "savekostium", "savepet", "give");
            return roots.filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give")) {
                Stream<String> types = this.costumesOnly
                    ? Stream.of("kostium", "zwoj-kostium")
                    : Stream.of("kostium", "zwoj-kostium", "pet", "zwoj-pet", "papuga");
                return types.filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
            }
            if (args[0].equalsIgnoreCase("savekostium")) {
                return this.kostiumyHandler.getKostiumy().keySet().stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
            }
            if (args[0].equalsIgnoreCase("savepet") && !this.costumesOnly) {
                return this.petyHandler.getPety().keySet().stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            if (args[1].equalsIgnoreCase("kostium")) {
                return Stream.concat(Stream.of("gui"), this.kostiumyHandler.getKostiumy().keySet().stream().filter(s -> !s.equalsIgnoreCase("zwoj-kostium"))).filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).toList();
            }
            if (args[1].equalsIgnoreCase("zwoj-kostium") || args[1].equalsIgnoreCase("zwoj-pet")) {
                return Stream.of("1d", "7d", "30m", "1h").filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).toList();
            }
            if (args[1].equalsIgnoreCase("pet") && !this.costumesOnly) {
                return Stream.concat(Stream.of("gui"), this.petyHandler.getPety().keySet().stream().filter(s -> !s.equalsIgnoreCase("zwoj-pet"))).filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).toList();
            }
            if (args[1].equalsIgnoreCase("papuga") && !this.costumesOnly) {
                return this.papugiHandler.getPapugi().keySet().stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).toList();
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give") && (args[1].equalsIgnoreCase("kostium") || args[1].equalsIgnoreCase("pet")) && args[2].equalsIgnoreCase("gui")) {
            return Stream.of("1", "2", "4", "8", "16", "32", "64").filter(s -> s.startsWith(args[3])).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give") && (args[1].equalsIgnoreCase("kostium") || args[1].equalsIgnoreCase("pet") || args[1].equalsIgnoreCase("papuga") || args[1].equalsIgnoreCase("zwoj-kostium") || args[1].equalsIgnoreCase("zwoj-pet"))) {
            return Bukkit.getOnlinePlayers().stream().map(OfflinePlayer::getName).filter(s -> s != null && s.toLowerCase().startsWith(args[3].toLowerCase())).toList();
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("give") && (args[1].equalsIgnoreCase("kostium") || args[1].equalsIgnoreCase("pet")) && args[2].equalsIgnoreCase("gui")) {
            return Stream.of("1", "3", "7", "14", "30").filter(s -> s.startsWith(args[4])).toList();
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("give") && (args[1].equalsIgnoreCase("kostium") || args[1].equalsIgnoreCase("pet") || args[1].equalsIgnoreCase("papuga"))) {
            return Stream.of("1d", "7d", "30m", "1h").filter(s -> s.toLowerCase().startsWith(args[4].toLowerCase())).toList();
        }
        return Collections.emptyList();
    }
}
