package dev.grzybalson.modules.itemshop;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.managers.PickupManager;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ItemShopCommand implements CommandExecutor, TabCompleter {

    private static final String CONFIG_PATH = "addons/itemshop/itemshop.yml";

    private final HollyCore plugin;
    private YamlConfiguration config;
    private final Map<String, String> packageAliases = new LinkedHashMap<>();
    private final Map<String, String> bundleAliases = new LinkedHashMap<>();

    public ItemShopCommand(HollyCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, CONFIG_PATH);
        File file = new File(plugin.getDataFolder(), CONFIG_PATH);
        this.config = YamlConfiguration.loadConfiguration(file);
        ensureConfigDefaults(file);
        rebuildPackageAliases();
        rebuildBundleAliases();
    }

    /** Dopisuje brakujace klucze — nie nadpisuje Twojej konfiguracji. */
    private void ensureConfigDefaults(File file) {
        boolean changed = false;
        changed |= setDefault("packages.ogbox.pickup-package", "KEY_HYPEBOX");
        changed |= setDefault("packages.ogbox.broadcast", "&fx{AMOUNT} &6&lOGBOX");
        changed |= setDefault("packages.skarbowa.pickup-package", "KEY_LEGENDARNA");
        changed |= setDefault("packages.skarbowa.broadcast", "&fx{AMOUNT} &6KLUCZ SKARBOWY");
        changed |= setDefault("packages.shard.pickup-package", "SHARD");
        changed |= setDefault("packages.shard.broadcast", "&fx{AMOUNT} {ITEM_NAME}");
        changed |= setDefault("packages.shard.use-item-name", true);
        changed |= setDefault("packages.monetyklanowe.pickup-package", "CLAN_COIN");
        changed |= setDefault("packages.monetyklanowe.broadcast", "&fx{AMOUNT} {ITEM_NAME}");
        changed |= setDefault("packages.monetyklanowe.use-item-name", true);
        if (!config.isList("packages.monetyklanowe.aliases")) {
            config.set("packages.monetyklanowe.aliases", List.of("monety", "klanowe", "clancoins"));
            changed = true;
        }
        changed |= setDefault("ranks.ninja.group", "ninja");
        changed |= setDefault("ranks.ninja.duration", "30d");
        changed |= setDefault("ranks.ninja.broadcast", "&a&lNINJA");
        changed |= setDefault("ranks.boss.group", "boss");
        changed |= setDefault("ranks.boss.duration", "30d");
        changed |= setDefault("ranks.boss.broadcast", "&c&lBOSS");
        changed |= setDefault("ranks.goat.group", "goat");
        changed |= setDefault("ranks.goat.duration", "30d");
        changed |= setDefault("ranks.goat.broadcast", "&f&lGOAT");

        changed |= ensureBundleDefaults();
        if (changed) {
            try {
                config.save(file);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean ensureBundleDefaults() {
        boolean changed = false;
        if (!config.contains("bundles.pakietlosowan")) {
            config.set("bundles.pakietlosowan.aliases", List.of("pakiet-losowan"));
            config.set("bundles.pakietlosowan.broadcast", "&bx20 &fLosowań zmianki");
            config.set("bundles.pakietlosowan.rewards", List.of(
                Map.of("type", "zmianki", "amount", 20)
            ));
            changed = true;
        } else {
            // /itemshop losowan <nick> <ilosc> jest osobna komenda — nie alias bundla.
            List<String> aliases = config.getStringList("bundles.pakietlosowan.aliases");
            if (aliases != null && aliases.stream().anyMatch(a -> a != null && a.equalsIgnoreCase("losowan"))) {
                List<String> cleaned = new ArrayList<>();
                for (String alias : aliases) {
                    if (alias != null && !alias.equalsIgnoreCase("losowan")) {
                        cleaned.add(alias);
                    }
                }
                config.set("bundles.pakietlosowan.aliases", cleaned);
                changed = true;
            }
        }
        if (!config.contains("bundles.pakietninja")) {
            config.set("bundles.pakietninja.aliases", List.of("ninja-pakiet", "pakiet-ninja"));
            config.set("bundles.pakietninja.broadcast", "&a&lPAKIET NINJA");
            config.set("bundles.pakietninja.rewards", List.of(
                Map.of("type", "rank", "rank", "ninja"),
                Map.of("type", "tickets", "amount", 10),
                Map.of("type", "pickup", "package", "SHARD", "amount", 250)
            ));
            changed = true;
        }
        if (!config.contains("bundles.pakietboss")) {
            config.set("bundles.pakietboss.aliases", List.of("boss-pakiet", "pakiet-boss"));
            config.set("bundles.pakietboss.broadcast", "&c&lPAKIET BOSS");
            config.set("bundles.pakietboss.rewards", List.of(
                Map.of("type", "rank", "rank", "boss"),
                Map.of("type", "tickets", "amount", 25),
                Map.of("type", "pickup", "package", "SHARD", "amount", 500),
                Map.of("type", "zmianki", "amount", 7)
            ));
            changed = true;
        }
        if (!config.contains("bundles.pakietgoat")) {
            config.set("bundles.pakietgoat.aliases", List.of("goat-pakiet", "pakiet-goat"));
            config.set("bundles.pakietgoat.broadcast", "&f&lPAKIET GOAT");
            config.set("bundles.pakietgoat.rewards", List.of(
                Map.of("type", "rank", "rank", "goat"),
                Map.of("type", "tickets", "amount", 50),
                Map.of("type", "pickup", "package", "SHARD", "amount", 1000),
                Map.of("type", "zmianki", "amount", 15)
            ));
            changed = true;
        }
        return changed;
    }

    private boolean setDefault(String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canGrant(sender)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CBrak uprawnien!"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String service = args[0].toLowerCase(Locale.ROOT);
        if (service.equals("reload")) {
            reload();
            sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8• &#4EFA76Przeladowano itemshop.yml"));
            return true;
        }

        if (isRankService(service)) {
            if (args.length < 2) {
                ColorUtils.sendUsage(sender, "/" + label + " " + service + " <gracz>");
                return true;
            }
            return grantRank(sender, service, args[1]);
        }

        if (isLosowanService(service)) {
            if (args.length < 3) {
                ColorUtils.sendUsage(sender, "/" + label + " losowan <gracz> <ilosc>");
                return true;
            }
            return grantLosowan(sender, args[1], args[2]);
        }

        if (isDungeonTicketService(service)) {
            if (args.length < 3) {
                ColorUtils.sendUsage(sender, "/" + label + " dungeon <gracz> <ilosc>");
                return true;
            }
            return grantDungeonTickets(sender, args[1], args[2]);
        }

        String bundleId = resolveBundleId(service);
        if (bundleId != null) {
            if (args.length < 2) {
                ColorUtils.sendUsage(sender, "/" + label + " " + service + " <gracz>");
                return true;
            }
            return grantBundle(sender, bundleId, args[1]);
        }

        String packageId = resolvePackageId(service);
        if (packageId != null) {
            if (args.length < 3) {
                ColorUtils.sendUsage(sender, "/" + label + " " + service + " <ilosc> <gracz>");
                return true;
            }
            return grantPackage(sender, packageId, args[1], args[2]);
        }

        sendUsage(sender, label);
        return true;
    }

    private boolean isLosowanService(String service) {
        if (service == null) {
            return false;
        }
        return switch (service.toLowerCase(Locale.ROOT)) {
            case "losowan", "losowania", "zmianki", "zmianka" -> true;
            default -> false;
        };
    }

    private boolean isDungeonTicketService(String service) {
        if (service == null) {
            return false;
        }
        return switch (service.toLowerCase(Locale.ROOT)) {
            case "dungeon", "wejscie", "wejsciowka", "wejsciówki", "wejsciowki", "ticket", "tickets" -> true;
            default -> false;
        };
    }

    private boolean grantLosowan(CommandSender sender, String playerName, String amountRaw) {
        Integer amount = parsePositiveAmount(sender, amountRaw);
        if (amount == null) {
            return true;
        }
        OfflinePlayer target = resolvePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CNie znaleziono gracza &f" + playerName));
            return true;
        }
        if (plugin.getOgHubZmiankiManager() == null && plugin.getPickupManager() == null) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CZmianki / pickup nie sa dostepne."));
            return true;
        }
        giveZmiankiReward(target, amount);
        String displayName = target.getName() != null ? target.getName() : playerName;
        broadcast(displayName, "&fx" + amount + " &bLosowań zmianki");
        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8• &#4EFA76Nadano &f" + amount + "x zmianka &adla &f" + displayName
        ));
        return true;
    }

    private boolean grantDungeonTickets(CommandSender sender, String playerName, String amountRaw) {
        Integer amount = parsePositiveAmount(sender, amountRaw);
        if (amount == null) {
            return true;
        }
        OfflinePlayer target = resolvePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CNie znaleziono gracza &f" + playerName));
            return true;
        }
        if (plugin.getEventManager() == null) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CEvent/dungeon jest wylaczony."));
            return true;
        }
        giveTicketsReward(target, amount);
        String displayName = target.getName() != null ? target.getName() : playerName;
        broadcast(displayName, "&fx" + amount + " &aWejściówka dungeon");
        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8• &#4EFA76Nadano &f" + amount + "x wejściówka &adla &f" + displayName
        ));
        return true;
    }

    private Integer parsePositiveAmount(CommandSender sender, String amountRaw) {
        int amount;
        try {
            amount = Integer.parseInt(amountRaw);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CNieprawidlowa ilosc: &f" + amountRaw));
            return null;
        }
        if (amount < 1) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CIlosc musi byc wieksza od 0."));
            return null;
        }
        return amount;
    }

    private boolean grantBundle(CommandSender sender, String bundleId, String playerName) {
        OfflinePlayer target = resolvePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CNie znaleziono gracza &f" + playerName));
            return true;
        }

        List<Map<?, ?>> rewards = config.getMapList("bundles." + bundleId + ".rewards");
        if (rewards == null || rewards.isEmpty()) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                "&#FF5C5C✘ &8• &#FF5C5CBrak rewards dla bundla &f" + bundleId + " &cw itemshop.yml."
            ));
            return true;
        }

        String displayName = target.getName() != null ? target.getName() : playerName;
        for (Map<?, ?> reward : rewards) {
            if (reward == null) {
                continue;
            }
            String type = String.valueOf(reward.get("type")).toLowerCase(Locale.ROOT);
            switch (type) {
                case "rank" -> {
                    Object rankObj = reward.get("rank");
                    if (rankObj != null) {
                        grantRankSilent(String.valueOf(rankObj).toLowerCase(Locale.ROOT), target);
                    }
                }
                case "pickup", "shard" -> {
                    String pkg = reward.get("package") != null
                        ? String.valueOf(reward.get("package"))
                        : "SHARD";
                    int amount = parseRewardAmount(reward.get("amount"), 1);
                    givePickup(target.getUniqueId(), pkg, amount);
                }
                case "tickets", "wejscie", "wejsciowka" -> {
                    int amount = parseRewardAmount(reward.get("amount"), 1);
                    giveTicketsReward(target, amount);
                }
                case "zmianki", "zmianka", "losowania" -> {
                    int amount = parseRewardAmount(reward.get("amount"), 1);
                    giveZmiankiReward(target, amount);
                }
                default -> plugin.getLogger().warning("[itemshop] Nieznany reward type: " + type);
            }
        }

        String serviceLabel = config.getString(
            "bundles." + bundleId + ".broadcast",
            "&f" + bundleId.toUpperCase(Locale.ROOT)
        );
        broadcast(displayName, serviceLabel);
        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8• &#4EFA76Nadano bundel &f" + bundleId + " &adla &f" + displayName
        ));
        return true;
    }

    private int parseRewardAmount(Object raw, int fallback) {
        if (raw instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (raw != null) {
            try {
                return Math.max(1, Integer.parseInt(String.valueOf(raw)));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private void grantRankSilent(String rank, OfflinePlayer target) {
        if (!isRankService(rank) || target.getName() == null) {
            return;
        }
        String duration = config.getString("ranks." + rank + ".duration", "30d");
        String group = config.getString("ranks." + rank + ".group", rank);
        String server = config.getString("lp-server", "");
        StringBuilder lp = new StringBuilder("lp user ")
            .append(target.getName())
            .append(" parent addtemp ")
            .append(group)
            .append(" ")
            .append(duration);
        if (server != null && !server.isBlank()) {
            lp.append(" server=").append(server.trim());
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), lp.toString());
    }

    private void givePickup(UUID uuid, String pickupPackage, int amount) {
        PickupManager pickupManager = plugin.getPickupManager();
        if (pickupManager == null || amount < 1) {
            return;
        }
        ensurePackage(pickupManager, pickupPackage);
        if (pickupManager.packageExists(pickupPackage)) {
            pickupManager.givePackage(uuid, pickupPackage, amount);
        }
    }

    private void giveTicketsReward(OfflinePlayer target, int amount) {
        if (amount < 1 || plugin.getEventManager() == null) {
            return;
        }
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            plugin.getEventManager().giveTicketsSilent(online, amount);
            return;
        }
        PickupManager pickupManager = plugin.getPickupManager();
        if (pickupManager == null) {
            return;
        }
        ItemStack ticket = plugin.getEventManager().createTicket(1);
        ensureDynamicPackage(pickupManager, "DUNGEON_TICKET", ticket);
        pickupManager.givePackage(target.getUniqueId(), "DUNGEON_TICKET", amount);
    }

    private void giveZmiankiReward(OfflinePlayer target, int amount) {
        if (amount < 1) {
            return;
        }
        ItemStack zmianka = null;
        if (plugin.getOgHubZmiankiManager() != null) {
            zmianka = plugin.getOgHubZmiankiManager().getZmiankiManager().buildZmiankaItem(1);
        }
        Player online = target.getPlayer();
        if (online != null && online.isOnline() && zmianka != null) {
            int left = amount;
            while (left > 0) {
                int stack = Math.min(64, left);
                ItemStack give = zmianka.clone();
                give.setAmount(stack);
                Map<Integer, ItemStack> leftover = online.getInventory().addItem(give);
                for (ItemStack drop : leftover.values()) {
                    online.getWorld().dropItemNaturally(online.getLocation(), drop);
                }
                left -= stack;
            }
            return;
        }
        PickupManager pickupManager = plugin.getPickupManager();
        if (pickupManager == null || zmianka == null) {
            return;
        }
        ensureDynamicPackage(pickupManager, "ZMIANKA", zmianka);
        pickupManager.givePackage(target.getUniqueId(), "ZMIANKA", amount);
    }

    private void ensureDynamicPackage(PickupManager pickupManager, String name, ItemStack item) {
        if (pickupManager.packageExists(name) || item == null) {
            return;
        }
        pickupManager.createPackage(name, item);
    }

    private boolean grantRank(CommandSender sender, String rank, String playerName) {
        if (!isRankService(rank)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                "&#FF5C5C✘ &8• &#FF5C5CNieznana ranga &f" + rank + " &c— dodaj ja w &fitemshop.yml &c(sekcja &franks&c)."
            ));
            return true;
        }

        OfflinePlayer target = resolvePlayer(playerName);
        if (target == null || target.getName() == null) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CNie znaleziono gracza &f" + playerName));
            return true;
        }

        String duration = config.getString("ranks." + rank + ".duration", "30d");
        String group = config.getString("ranks." + rank + ".group", rank);
        String server = config.getString("lp-server", "");

        StringBuilder lp = new StringBuilder("lp user ")
            .append(target.getName())
            .append(" parent addtemp ")
            .append(group)
            .append(" ")
            .append(duration);
        if (server != null && !server.isBlank()) {
            lp.append(" server=").append(server.trim());
        }

        ConsoleCommandSender console = Bukkit.getConsoleSender();
        Bukkit.dispatchCommand(console, lp.toString());

        String serviceLabel = config.getString(
            "ranks." + rank + ".broadcast",
            "&f" + rank.toUpperCase(Locale.ROOT)
        );

        broadcast(target.getName(), serviceLabel);
        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8• &#4EFA76Nadano &f" + rank + " &adla &f" + target.getName()
        ));
        return true;
    }

    private boolean grantPackage(CommandSender sender, String packageId, String amountRaw, String playerName) {
        int amount;
        try {
            amount = Integer.parseInt(amountRaw);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CNieprawidlowa ilosc: &f" + amountRaw));
            return true;
        }
        if (amount < 1) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CIlosc musi byc wieksza od 0."));
            return true;
        }

        OfflinePlayer target = resolvePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CNie znaleziono gracza &f" + playerName));
            return true;
        }

        PickupManager pickupManager = plugin.getPickupManager();
        if (pickupManager == null) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CPickupManager jest wylaczony (addon commands)."));
            return true;
        }

        String pickupPackage = config.getString("packages." + packageId + ".pickup-package");
        if (pickupPackage == null || pickupPackage.isBlank()) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                "&#FF5C5C✘ &8• &#FF5C5CBrak &fpickup-package &cdla &f" + packageId + " &cw itemshop.yml."
            ));
            return true;
        }

        ensurePackage(pickupManager, pickupPackage);
        if (!pickupManager.packageExists(pickupPackage)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                "&#FF5C5C✘ &8• &#FF5C5CBrak pakietu &f" + pickupPackage + " &cw /odbierz. Zaladuj ItemStorage."
            ));
            return true;
        }

        UUID uuid = target.getUniqueId();
        String displayName = target.getName() != null ? target.getName() : playerName;
        pickupManager.givePackage(uuid, pickupPackage, amount);

        String serviceLabel = resolvePackageBroadcast(packageId, amount);
        broadcast(displayName, serviceLabel);
        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8• &#4EFA76Nadano &f" + pickupPackage + " x" + amount + " &adla &f" + displayName
        ));
        return true;
    }

    private String resolvePackageBroadcast(String packageId, int amount) {
        String template = config.getString("packages." + packageId + ".broadcast", "&fx{AMOUNT} &f" + packageId);
        String label = template;
        if (config.getBoolean("packages." + packageId + ".use-item-name", false)) {
            String itemKey = config.getString("packages." + packageId + ".pickup-package", packageId);
            label = template.replace("{ITEM_NAME}", resolveItemLabel(itemKey, packageId));
        }
        return label.replace("{AMOUNT}", String.valueOf(amount));
    }

    private String resolveItemLabel(String key, String fallback) {
        if (plugin.getItemStorageManager() == null) {
            return fallback;
        }
        ItemStack item = plugin.getItemStorageManager().getItem(key);
        if (item == null) {
            return fallback;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return fallback;
    }

    private void ensurePackage(PickupManager pickupManager, String packageName) {
        if (pickupManager.packageExists(packageName)) {
            return;
        }
        if (plugin.getItemStorageManager() == null) {
            return;
        }
        var item = plugin.getItemStorageManager().getItem(packageName);
        if (item != null) {
            pickupManager.createPackage(packageName, item);
        }
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolvePlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline;
        }
        return offline;
    }

    private void broadcast(String playerName, String service) {
        List<String> lines = config.getStringList("broadcast");
        if (lines == null || lines.isEmpty()) {
            lines = defaultBroadcast();
        }

        for (String line : lines) {
            String parsed = line
                .replace("{PLAYER}", playerName)
                .replace("{SERVICE}", service);
            Component component = ColorUtils.colorizeComponent(parsed);
            Bukkit.broadcast(component);
        }
    }

    private List<String> defaultBroadcast() {
        return List.of(
            "&f",
            "         &8⌞&#F2F454★&8⌝ &#FFBA00&lɴ&#FCC611&lᴏ&#FAD122&lᴡ&#F7DD32&lʏ &#F2F454&lᴢ&#F5E63F&lᴀ&#F9D72A&lᴋ&#FCC915&lᴜ&#FFBA00&lᴘ  &8⌞&#F2F454★&8⌝",
            "    &fGracz &#F2F454{PLAYER} &fzakupił &#F2F454{SERVICE}",
            "  &#FF005D❤ &#83FF86Dziękujemy za wsparcie! &#FF005D❤",
            "     &fSklep: &#F883FFhttps://oghub.pl/",
            "       &8⌞&#F2F454★&8⌝ &#FFBA00&lɴ&#FCC611&lᴏ&#FAD122&lᴡ&#F7DD32&lʏ &#F2F454&lᴢ&#F5E63F&lᴀ&#F9D72A&lᴋ&#FCC915&lᴜ&#FFBA00&lᴘ  &8⌞&#F2F454★&8⌝",
            "&f"
        );
    }

    private void sendUsage(CommandSender sender, String label) {
        List<String> ranks = getRankServices();
        List<String> packages = getPackageCommandNames();
        List<String> bundles = getBundleCommandNames();
        String rankPart = ranks.isEmpty() ? "<ranga>" : String.join("|", ranks);
        String packagePart = packages.isEmpty() ? "<pakiet>" : String.join("|", packages);
        String bundlePart = bundles.isEmpty() ? "<bundel>" : String.join("|", bundles);
        ColorUtils.sendUsage(sender,
            "/" + label + " <" + rankPart + "|" + bundlePart + "> <gracz>",
            "/" + label + " <" + packagePart + "> <ilosc> <gracz>",
            "/" + label + " losowan <gracz> <ilosc>",
            "/" + label + " dungeon <gracz> <ilosc>"
        );
    }

    private boolean canGrant(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true;
        }
        return sender.hasPermission("Holly.cmd.itemshop");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!canGrant(sender)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.addAll(getRankServices());
            options.addAll(getBundleCommandNames());
            options.addAll(getPackageCommandNames());
            options.add("losowan");
            options.add("dungeon");
            options.add("reload");
            return filter(options, args[0]);
        }

        if (args.length == 2) {
            String service = args[0].toLowerCase(Locale.ROOT);
            if (isRankService(service) || resolveBundleId(service) != null
                || isLosowanService(service) || isDungeonTicketService(service)) {
                return filter(onlineNames(), args[1]);
            }
            if (resolvePackageId(service) != null) {
                return filter(List.of("1", "2", "5", "10", "16", "32", "64"), args[1]);
            }
        }

        if (args.length == 3) {
            String service = args[0].toLowerCase(Locale.ROOT);
            if (isLosowanService(service) || isDungeonTicketService(service)) {
                return filter(List.of("1", "2", "5", "10", "16", "20", "32", "64"), args[2]);
            }
            if (resolvePackageId(service) != null) {
                return filter(onlineNames(), args[2]);
            }
        }

        return List.of();
    }

    private boolean isRankService(String service) {
        if (service == null || service.isBlank()) {
            return false;
        }
        ConfigurationSection ranks = config.getConfigurationSection("ranks");
        return ranks != null && ranks.contains(service.toLowerCase(Locale.ROOT));
    }

    private List<String> getRankServices() {
        ConfigurationSection ranks = config.getConfigurationSection("ranks");
        if (ranks == null) {
            return List.of();
        }
        return new ArrayList<>(ranks.getKeys(false));
    }

    private List<String> getPackageCommandNames() {
        Set<String> names = new LinkedHashSet<>();
        ConfigurationSection packages = config.getConfigurationSection("packages");
        if (packages != null) {
            for (String key : packages.getKeys(false)) {
                names.add(key.toLowerCase(Locale.ROOT));
                List<String> aliases = config.getStringList("packages." + key + ".aliases");
                if (aliases != null) {
                    for (String alias : aliases) {
                        if (alias != null && !alias.isBlank()) {
                            names.add(alias.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        }
        return new ArrayList<>(names);
    }

    private String resolvePackageId(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return null;
        }
        String normalized = commandName.toLowerCase(Locale.ROOT);
        ConfigurationSection packages = config.getConfigurationSection("packages");
        if (packages != null && packages.contains(normalized)) {
            return normalized;
        }
        return packageAliases.get(normalized);
    }

    private void rebuildPackageAliases() {
        packageAliases.clear();
        ConfigurationSection packages = config.getConfigurationSection("packages");
        if (packages == null) {
            return;
        }
        for (String key : packages.getKeys(false)) {
            String normalizedKey = key.toLowerCase(Locale.ROOT);
            List<String> aliases = config.getStringList("packages." + key + ".aliases");
            if (aliases == null) {
                continue;
            }
            for (String alias : aliases) {
                if (alias == null || alias.isBlank()) {
                    continue;
                }
                packageAliases.put(alias.toLowerCase(Locale.ROOT), normalizedKey);
            }
        }
    }

    private void rebuildBundleAliases() {
        bundleAliases.clear();
        ConfigurationSection bundles = config.getConfigurationSection("bundles");
        if (bundles == null) {
            return;
        }
        for (String key : bundles.getKeys(false)) {
            String normalizedKey = key.toLowerCase(Locale.ROOT);
            List<String> aliases = config.getStringList("bundles." + key + ".aliases");
            if (aliases == null) {
                continue;
            }
            for (String alias : aliases) {
                if (alias == null || alias.isBlank()) {
                    continue;
                }
                bundleAliases.put(alias.toLowerCase(Locale.ROOT), normalizedKey);
            }
        }
    }

    private List<String> getBundleCommandNames() {
        Set<String> names = new LinkedHashSet<>();
        ConfigurationSection bundles = config.getConfigurationSection("bundles");
        if (bundles != null) {
            for (String key : bundles.getKeys(false)) {
                names.add(key.toLowerCase(Locale.ROOT));
                List<String> aliases = config.getStringList("bundles." + key + ".aliases");
                if (aliases != null) {
                    for (String alias : aliases) {
                        if (alias != null && !alias.isBlank()) {
                            names.add(alias.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        }
        return new ArrayList<>(names);
    }

    private String resolveBundleId(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return null;
        }
        String normalized = commandName.toLowerCase(Locale.ROOT);
        ConfigurationSection bundles = config.getConfigurationSection("bundles");
        if (bundles != null && bundles.contains(normalized)) {
            return normalized;
        }
        return bundleAliases.get(normalized);
    }

    private List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private List<String> filter(List<String> values, String input) {
        String needle = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(needle)) {
                result.add(value);
            }
        }
        return result;
    }
}
