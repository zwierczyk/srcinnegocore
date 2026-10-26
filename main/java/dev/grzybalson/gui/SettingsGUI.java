package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private Inventory inventory;
    private YamlConfiguration config;

    public SettingsGUI(HollyCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        loadConfig();
        int rows = Math.max(1, Math.min(6, config.getInt("rows", 5)));
        inventory = Bukkit.createInventory(this, rows * 9, ColorUtils.colorizeComponent(config.getString("title", "&8Ustawienia")));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        setToggleItem("recharge-music", plugin.getUserSettingsManager().isRechargeMusicEnabled(player.getUniqueId()));
        setToggleItem("private-messages", plugin.getUserSettingsManager().isPrivateMessagesEnabled(player.getUniqueId()));
        setToggleItem("kill-info", plugin.getUserSettingsManager().isKillInfoEnabled(player.getUniqueId()));
        setToggleItem("kill-totem-effect", plugin.getUserSettingsManager().isKillTotemEffectEnabled(player.getUniqueId()));

        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        ItemStack clicked = inventory.getItem(slot);
        String action = NBTUtils.getString(clicked, "settings_action");
        if (action == null || action.isBlank()) {
            return;
        }

        switch (action.toLowerCase(Locale.ROOT)) {
            case "recharge-music" -> plugin.getUserSettingsManager().toggleRechargeMusic(player.getUniqueId());
            case "private-messages" -> plugin.getUserSettingsManager().togglePrivateMessages(player.getUniqueId());
            case "kill-info" -> plugin.getUserSettingsManager().toggleKillInfo(player.getUniqueId());
            case "kill-totem-effect" -> plugin.getUserSettingsManager().toggleKillTotemEffect(player.getUniqueId());
            default -> { return; }
        }
        open();
    }

    private void setToggleItem(String key, boolean enabled) {
        String path = "items." + key + ".";
        int slot = config.getInt(path + "slot", switch (key) {
            case "private-messages" -> 20;
            case "recharge-music" -> 22;
            case "kill-info" -> 24;
            case "kill-totem-effect" -> 31;
            default -> 22;
        });
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        ItemStack item = createToggleItem(path, enabled);
        NBTUtils.setString(item, "settings_action", key);
        inventory.setItem(slot, item);
    }

    private ItemStack createToggleItem(String path, boolean enabled) {
        String materialName = config.getString(path + "material", "PAPER");
        Material material = Material.matchMaterial(materialName == null ? "PAPER" : materialName);
        if (material == null) {
            material = Material.PAPER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String status = enabled
                ? config.getString("status.enabled", "&aWłączone! &a(✔)")
                : config.getString("status.disabled", "&cWyłączone! &c(✖)");
            String name = replace(config.getString(path + "name", "&6&lUSTAWIENIE"), enabled, status);
            meta.displayName(noItalic(ColorUtils.colorizeComponent(name)));

            List<Component> lore = new ArrayList<>();
            for (String line : config.getStringList(path + "lore")) {
                lore.add(noItalic(ColorUtils.colorizeComponent(replace(line, enabled, status))));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String replace(String input, boolean enabled, String status) {
        return input
            .replace("{STATUS}", status == null ? "" : status)
            .replace("{status}", status == null ? "" : status)
            .replace("{BOOL}", String.valueOf(enabled))
            .replace("{bool}", String.valueOf(enabled));
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private void loadConfig() {
        File file = new File(plugin.getDataFolder(), "guis/settings.yml");
        if (!file.exists()) {
            plugin.saveResource("guis/settings.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        applyMissingDefaults(file);
    }

    private void applyMissingDefaults(File file) {
        boolean changed = false;
        changed |= setIfMissing("items.kill-totem-effect.slot", 31);
        changed |= setIfMissing("items.kill-totem-effect.material", "TOTEM_OF_UNDYING");
        changed |= setIfMissing("items.kill-totem-effect.name", "&6&lEFEKT TOTEMU PO KILLU");
        changed |= setIfMissing("items.kill-totem-effect.lore", List.of(
            "",
            "&8x &fJesli nie chcesz widziec efektu",
            "&8x &ftotemu po zabiciu gracza, wylacz go tutaj.",
            "",
            "&8x &fStatus: {STATUS}",
            "",
            "&8> &eKliknij, aby zmienic status!"
        ));

        if (!changed) {
            return;
        }

        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac domyslnych ustawien GUI: " + exception.getMessage());
        }
    }

    private boolean setIfMissing(String path, Object value) {
        if (config.isSet(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
