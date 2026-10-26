package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.achievement.AchievementType;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdminAchievementCommand implements CommandExecutor, TabCompleter, Listener {

    private final HollyCore plugin;

    public AdminAchievementCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.aosiagniecia")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.aosiagniecia"));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.getModuleConfigManager().getAchievementConfig().load(true);
            if (plugin.getAchievementListener() != null) {
                plugin.getAchievementListener().reloadAllowedBlocks();
            }
            player.sendMessage(ColorUtils.success("Przeładowano konfigurację osiągnięć!"));
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("edit")) {
            sendHelp(player);
            return true;
        }

        AchievementType type;
        try {
            type = AchievementType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(ColorUtils.error("Nieprawidłowy typ! Dostępne: " + java.util.Arrays.stream(AchievementType.values()).map(Enum::name).collect(java.util.stream.Collectors.joining(", "))));
            return true;
        }

        int tier;
        try {
            tier = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ColorUtils.error("Tier musi być liczbą 1-5!"));
            return true;
        }

        if (tier < 1 || tier > 5) {
            player.sendMessage(ColorUtils.error("Tier musi być w zakresie 1-5!"));
            return true;
        }

        openRewardEditor(player, type, tier);
        return true;
    }

    private void sendHelp(Player player) {
        ColorUtils.sendUsage(player,
            "/aosiagniecia edit <typ> <tier>",
            "/aosiagniecia reload"
        );
        player.sendMessage(ColorUtils.info("Typy: &f" + java.util.Arrays.stream(AchievementType.values()).map(Enum::name).collect(java.util.stream.Collectors.joining("&8, &f"))));
        player.sendMessage(ColorUtils.info("Tier: &f1-5"));
    }

    private void openRewardEditor(Player player, AchievementType type, int tier) {
        String title = "&8Edytor: " + type.getDisplayName() + " #" + tier;
        RewardEditorHolder holder = new RewardEditorHolder(type, tier);
        Inventory inv = Bukkit.createInventory(holder, 27, ColorUtils.colorizeComponent(title));

        // Load existing rewards
        ItemStack[] existing = plugin.getAchievementManager().getRewardItems(type, tier);
        if (existing != null) {
            for (int i = 0; i < Math.min(existing.length, 27); i++) {
                if (existing[i] != null && !existing[i].getType().isAir()) {
                    inv.setItem(i, existing[i]);
                }
            }
        }

        holder.setInventory(inv);
        player.openInventory(inv);
        player.sendMessage(ColorUtils.success("Połóż przedmioty i zamknij GUI aby zapisać nagrody."));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RewardEditorHolder holder)) return;

        Inventory inv = event.getInventory();
        List<ItemStack> items = new ArrayList<>();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
        }

        String rewardKey = holder.getType().name() + "_" + holder.getTier();

        if (items.isEmpty()) {
            plugin.getModuleConfigManager().getAchievementConfig().setReward(rewardKey, "");
            event.getPlayer().sendMessage(ColorUtils.success("Wyczyszczono nagrody dla " + rewardKey));
        } else {
            ItemStack[] itemArray = items.toArray(new ItemStack[0]);
            String serialized = ItemSerializer.serializeItems(itemArray);
            plugin.getModuleConfigManager().getAchievementConfig().setReward(rewardKey, serialized);
            event.getPlayer().sendMessage(ColorUtils.success(
                "Zapisano " + items.size() + " nagród dla " + rewardKey
            ));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("edit".startsWith(args[0].toLowerCase())) completions.add("edit");
            if ("reload".startsWith(args[0].toLowerCase())) completions.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            String partial = args[1].toUpperCase();
            for (AchievementType type : AchievementType.values()) {
                if (type.name().startsWith(partial)) {
                    completions.add(type.name());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("edit")) {
            for (int i = 1; i <= 5; i++) {
                completions.add(String.valueOf(i));
            }
        }
        return completions;
    }

    public static class RewardEditorHolder implements InventoryHolder {
        private final AchievementType type;
        private final int tier;
        private Inventory inventory;

        public RewardEditorHolder(AchievementType type, int tier) {
            this.type = type;
            this.tier = tier;
        }

        public AchievementType getType() { return type; }
        public int getTier() { return tier; }

        public void setInventory(Inventory inventory) { this.inventory = inventory; }

        @Override
        public Inventory getInventory() { return inventory; }
    }
}
