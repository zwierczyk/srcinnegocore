package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.WalletConfig;
import dev.grzybalson.managers.WalletProductStatsService;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.CommandPlaceholderUtil;
import dev.grzybalson.utils.NBTUtils;
import dev.grzybalson.utils.WalletItemFactory;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Locale;

public class WalletGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final Map<Integer, Runnable> clickActions = new HashMap<>();
    private Inventory inventory;
    private Integer openedServiceSlot;

    public WalletGUI(HollyCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        openMain();
    }

    private void openMain() {
        WalletConfig config = plugin.getConfigManager().getWalletConfig();
        clickActions.clear();
        openedServiceSlot = null;
        inventory = Bukkit.createInventory(this, plugin.getGuiFactory().getInventorySize(config.getGui().getRows()), ColorUtils.colorizeComponent(config.getGui().getTitle()));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        double balance = plugin.getWalletManager().getCachedBalance(player.getUniqueId());
        Map<String, String> placeholders = basePlaceholders(balance);

        for (Map.Entry<Integer, WalletConfig.ServiceCategory> entry : config.getServices().entrySet()) {
            int slot = entry.getKey();
            WalletConfig.ServiceCategory service = entry.getValue();
            ItemStack item = WalletItemFactory.create(service.getItem(), placeholders);
            NBTUtils.setString(item, "wallet_action", "open_service");
            inventory.setItem(slot, item);
            clickActions.put(slot, () -> {
                playSound(service.getSound());
                openService(slot, service);
            });
        }

        WalletConfig.BalanceItem balanceItem = config.getBalanceItem();
        int balanceSlot = plugin.getGuiFactory().resolveSlot(inventory, balanceItem.getSlot(), plugin.getGuiFactory().bottomRowSlot(inventory, 4));
        plugin.getGuiFactory().setItemIfInBounds(inventory, balanceSlot, WalletItemFactory.create(balanceItem.getItem(), placeholders));

        player.openInventory(inventory);
    }

    public void openService(int serviceSlot, WalletConfig.ServiceCategory service) {
        if (canViewAdminStats()) {
            WalletProductStatsService statsService = plugin.getWalletProductStatsService();
            if (statsService != null) {
                statsService.getStatsMap().thenAccept(stats ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        renderService(serviceSlot, service, stats);
                    })
                );
                return;
            }
        }
        renderService(serviceSlot, service, Map.of());
    }

    private void renderService(int serviceSlot, WalletConfig.ServiceCategory service, Map<String, WalletProductStatsService.ProductStats> statsMap) {
        clickActions.clear();
        openedServiceSlot = serviceSlot;
        inventory = Bukkit.createInventory(this, plugin.getGuiFactory().getInventorySize(service.getGuiRows()),
            ColorUtils.colorizeComponent(WalletItemFactory.apply(service.getGuiTitle(), basePlaceholders(plugin.getWalletManager().getCachedBalance(player.getUniqueId())))));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        double balance = plugin.getWalletManager().getCachedBalance(player.getUniqueId());
        Map<String, String> base = basePlaceholders(balance);
        boolean adminStats = canViewAdminStats();
        WalletProductStatsService statsService = plugin.getWalletProductStatsService();

        for (WalletConfig.WalletProduct product : service.getCategories()) {
            Map<String, String> placeholders = new HashMap<>(base);
            placeholders.put("{PRICE}", format(product.getPrice()));
            String serviceName = ColorUtils.colorize(WalletItemFactory.apply(product.getItem().getMeta().getDisplay(), placeholders));
            placeholders.put("{SERVICE}", serviceName);

            ItemStack item = WalletItemFactory.create(product.getItem(), placeholders);
            if (adminStats && statsService != null) {
                statsService.appendAdminLore(item, product, statsMap);
            }
            inventory.setItem(product.getSlot(), item);
            clickActions.put(product.getSlot(), () -> new WalletConfirmGUI(plugin, player, this, serviceSlot, serviceName, product, service, statsMap).open());
        }

        int backSlot = inventory.getSize() - 5;
        ItemStack back = new ItemStack(org.bukkit.Material.ARROW);
        var meta = back.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent("&c&lPowrót"));
            back.setItemMeta(meta);
        }
        inventory.setItem(backSlot, back);
        clickActions.put(backSlot, this::openMain);

        WalletConfig.BalanceItem balanceItem = plugin.getConfigManager().getWalletConfig().getBalanceItem();
        int balanceSlot = plugin.getGuiFactory().resolveSlot(inventory, balanceItem.getSlot(), plugin.getGuiFactory().bottomRowSlot(inventory, 4));
        plugin.getGuiFactory().setItemIfInBounds(inventory, balanceSlot, WalletItemFactory.create(balanceItem.getItem(), base));

        player.openInventory(inventory);
    }

    private boolean canViewAdminStats() {
        WalletConfig.AdminProductStats settings = plugin.getConfigManager().getWalletConfig().getAdminProductStats();
        if (!settings.isEnabled()) {
            return false;
        }
        String permission = settings.getPermission();
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    public void purchase(String serviceName, WalletConfig.WalletProduct product, WalletConfig.ServiceCategory service) {
        plugin.getWalletManager().removeBalance(
            player.getUniqueId(),
            product.getPrice(),
            serviceName,
            player.getName(),
            "PURCHASE",
            player.getUniqueId().toString(),
            service.getGuiTitle()
        ).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!success) {
                player.sendMessage(ColorUtils.colorizeComponent(applyMessage(plugin.getConfigManager().getWalletConfig().getMessages().getInsufficientFunds(), product.getPrice(), serviceName, player.getName())));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            WalletProductStatsService statsService = plugin.getWalletProductStatsService();
            if (statsService != null) {
                statsService.invalidateCache();
            }

            ConsoleCommandSender console = Bukkit.getConsoleSender();
            for (String command : product.getCommands()) {
                Bukkit.dispatchCommand(console, replaceCommandPlaceholders(command, serviceName, product.getPrice()));
            }
            for (String command : product.getPlayerCommands()) {
                player.performCommand(replaceCommandPlaceholders(command, serviceName, product.getPrice()).replaceFirst("^/", ""));
            }

            player.sendMessage(ColorUtils.colorizeComponent(applyMessage(plugin.getConfigManager().getWalletConfig().getMessages().getBought(), product.getPrice(), serviceName, player.getName())));
            playSound(product.getSound());
            broadcastBuyBar(player.getName(), serviceName, product.getPrice());

            if (product.isCloseAfterBuy()) {
                player.closeInventory();
            } else if (openedServiceSlot != null) {
                WalletConfig.ServiceCategory current = plugin.getConfigManager().getWalletConfig().getServices().get(openedServiceSlot);
                if (current != null) {
                    openService(openedServiceSlot, current);
                }
            }
        })).exceptionally(ex -> {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Blad zakupu portfela: " + player.getName()
                + " -> " + ColorUtils.stripColors(serviceName) + " (" + format(product.getPrice()) + ")", ex);
            return null;
        });
    }

    private void broadcastBuyBar(String playerName, String serviceName, double price) {
        WalletConfig.BuyBar config = plugin.getConfigManager().getWalletConfig().getBuyBar();
        if (!config.isEnabled()) {
            return;
        }

        BossBar.Overlay overlay = parseOverlay(config.getBarStyle());
        boolean premiumPurchase = price >= config.getRainbowMinPrice();
        BossBar.Color baseColor = premiumPurchase ? BossBar.Color.RED : parseColor(config.getBarColor(), BossBar.Color.RED);

        String title = config.getTitle()
            .replace("{PLAYER}", playerName)
            .replace("{SERVICE}", serviceName)
            .replace("{PRICE}", format(price))
            .replace("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName());
        BossBar bossBar = BossBar.bossBar(ColorUtils.colorizeComponent(title), 1.0f, baseColor, overlay);
        Bukkit.getOnlinePlayers().forEach(online -> online.showBossBar(bossBar));

        int totalTicks = Math.max(60, Math.min(100, config.getTicks()));
        int stepTicks = 2;
        AtomicInteger elapsed = new AtomicInteger(0);
        BossBar.Color[] colors = {
            BossBar.Color.RED, BossBar.Color.YELLOW, BossBar.Color.GREEN, BossBar.Color.BLUE,
            BossBar.Color.PINK, BossBar.Color.PURPLE, BossBar.Color.WHITE
        };
        AtomicInteger colorIndex = new AtomicInteger(0);

        if (premiumPurchase) {
            playGlobalSound(config.getPremiumSound(), 1.0f, 1.0f);
        }

        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int current = elapsed.addAndGet(stepTicks);
            double progress = Math.max(0.0, 1.0 - (current / (double) totalTicks));
            bossBar.progress((float) progress);

            if (premiumPurchase && current % Math.max(1, config.getRainbowIntervalTicks()) == 0) {
                bossBar.color(colors[colorIndex.getAndIncrement() % colors.length]);
            }

            if (current >= totalTicks) {
                if (task[0] != null) task[0].cancel();
                Bukkit.getOnlinePlayers().forEach(online -> online.hideBossBar(bossBar));
            }
        }, 0L, stepTicks);
    }

    private BossBar.Color parseColor(String value, BossBar.Color fallback) {
        try {
            return BossBar.Color.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private BossBar.Overlay parseOverlay(String value) {
        try {
            String normalizedStyle = value.toUpperCase(Locale.ROOT)
                .replace("SEGMENTED_6", "NOTCHED_6")
                .replace("SEGMENTED_10", "NOTCHED_10")
                .replace("SEGMENTED_12", "NOTCHED_12")
                .replace("SEGMENTED_20", "NOTCHED_20");
            return BossBar.Overlay.valueOf(normalizedStyle);
        } catch (IllegalArgumentException exception) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    private void playGlobalSound(String soundName, float volume, float pitch) {
        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
        } catch (IllegalArgumentException exception) {
            sound = Sound.ENTITY_ENDER_DRAGON_DEATH;
        }
        Sound finalSound = sound;
        Bukkit.getOnlinePlayers().forEach(online -> online.playSound(online.getLocation(), finalSound, volume, pitch));
    }

    private String replaceCommandPlaceholders(String command, String serviceName, double price) {
        return CommandPlaceholderUtil.stripLeadingSlash(CommandPlaceholderUtil.applyPlayerPlaceholders(command, player)
            .replace("{SERVICE}", ColorUtils.stripColors(serviceName))
            .replace("{service}", ColorUtils.stripColors(serviceName))
            .replace("{PRICE}", format(price))
            .replace("{price}", format(price)));
    }

    private String applyMessage(String message, double amount, String serviceName, String playerName) {
        return message
            .replace("{PLAYER}", playerName)
            .replace("{PRICE}", format(amount))
            .replace("{AMOUNT}", format(amount))
            .replace("{SERVICE}", serviceName)
            .replace("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName());
    }

    private Map<String, String> basePlaceholders(double balance) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{PLAYER}", player.getName());
        placeholders.put("{BALANCE}", format(balance));
        placeholders.put("{MONEY}", format(balance));
        placeholders.put("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName());
        return placeholders;
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private void playSound(String soundName) {
        try {
            String normalized = soundName.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
            player.playSound(player.getLocation(), Sound.valueOf(normalized), 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    public void handleClick(int rawSlot) {
        Runnable action = clickActions.get(rawSlot);
        if (action != null) {
            action.run();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
