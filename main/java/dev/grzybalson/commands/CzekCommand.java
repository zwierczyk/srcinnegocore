package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.czek.CzekConfig;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CzekCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String CHECK_KEY = "check_amount";

    private final HollyCore plugin;
    private volatile Material cachedCzekMaterial;

    public CzekCommand(HollyCore plugin) {
        this.plugin = plugin;
        refreshCachedMaterial();
    }

    public void refreshCachedMaterial() {
        try {
            cachedCzekMaterial = Material.valueOf(getConfig().getItemMaterial().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            cachedCzekMaterial = Material.PAPER;
        }
    }

    private CzekConfig getConfig() {
        return plugin.getModuleConfigManager().getCzekConfig();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.czek.reload") && !sender.hasPermission("Holly.cmd.czek") && !sender.isOp()) {
                sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.czek.reload"));
                return true;
            }
            getConfig().load(true);
            refreshCachedMaterial();
            sender.sendMessage(ColorUtils.colorizeComponent(getConfig().getMessageReloaded()));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.czek")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.czek"));
            return true;
        }

        if (args.length < 1) {
            ColorUtils.sendUsage(player, "/czek <ilość>");
            return true;
        }

        Double parsedAmount = MoneyAmountUtil.parse(args[0]);
        if (parsedAmount == null || !MoneyAmountUtil.isPositiveFinite(parsedAmount)) {
            player.sendMessage(ColorUtils.colorizeComponent(getConfig().getMessageInvalidAmount()));
            return true;
        }

        double amount = parsedAmount;

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ColorUtils.colorizeComponent(getConfig().getMessageInventoryFull()));
            return true;
        }

        if (!plugin.getEconomy().has(player, amount)) {
            player.sendMessage(ColorUtils.colorizeComponent(getConfig().getMessageNotEnoughMoney()));
            return true;
        }

        plugin.getEconomy().removeBalance(player.getUniqueId(), amount).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!success) {
                player.sendMessage(ColorUtils.colorizeComponent(getConfig().getMessageNotEnoughMoney()));
                return;
            }

            if (player.getInventory().firstEmpty() == -1) {
                plugin.getEconomy().addBalance(player.getUniqueId(), amount);
                player.sendMessage(ColorUtils.colorizeComponent(getConfig().getMessageInventoryFull()));
                return;
            }

            ItemStack chequeItem = createChequeItem(amount);
            player.getInventory().addItem(chequeItem);
            String formattedAmount = formatMoneyAmount(amount);
            player.sendMessage(ColorUtils.colorizeComponent(getConfig().getMessageCreated().replace("${AMOUNT}", formattedAmount)));
        }));

        return true;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) return;

        // Fast-path: skip PDC lookup for items that can't be cheques
        if (item.getType() != cachedCzekMaterial) return;

        Double amount = NBTUtils.getDouble(item, CHECK_KEY);
        if (amount == null || !MoneyAmountUtil.isPositiveFinite(amount)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            if (event.getHand() == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(null);
            } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                item.setAmount(0);
            }
        }

        plugin.getEconomy().addBalance(player.getUniqueId(), amount).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (success) {
                String formattedAmount = formatMoneyAmount(amount);
                player.sendMessage(ColorUtils.colorizeComponent(getConfig().getMessageDeposited().replace("${AMOUNT}", formattedAmount)));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            } else {
                player.sendMessage(ColorUtils.colorizeComponent(getConfig().getMessageError()));
            }
        }));
    }

    public ItemStack createChequeItem(double amount) {
        CzekConfig cfg = getConfig();
        Material mat;
        try {
            mat = Material.valueOf(cfg.getItemMaterial().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mat = Material.PAPER;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(cfg.getItemName()));

            String formattedAmount = formatMoneyAmount(amount);
            List<Component> lore = new ArrayList<>();
            if (cfg.getItemLore() != null) {
                for (String line : cfg.getItemLore()) {
                    String processed = line.replace("${AMOUNT}", formattedAmount);
                    lore.add(ColorUtils.colorizeComponent(processed));
                }
            }
            meta.lore(lore);

            item.setItemMeta(meta);
        }
        return NBTUtils.setDouble(item, CHECK_KEY, amount);
    }

    private static String formatMoneyAmount(double amount) {
        if (amount == (long) amount) {
            return String.format(Locale.US, "%d", (long) amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String input = args[0].toLowerCase();
            if ("reload".startsWith(input) && (sender.hasPermission("Holly.cmd.czek.reload") || sender.hasPermission("Holly.cmd.czek") || sender.isOp())) {
                suggestions.add("reload");
            }
            for (String val : List.of("100", "500", "1000", "5000", "10000")) {
                if (val.startsWith(input)) {
                    suggestions.add(val);
                }
            }
            return suggestions;
        }
        return List.of();
    }
}
