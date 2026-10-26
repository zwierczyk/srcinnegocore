package dev.grzybalson.modules.event;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DungeonLobbyGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final EventManager manager;
    private final DungeonParty party;
    private Inventory inventory;

    public DungeonLobbyGUI(HollyCore plugin, Player player, EventManager manager, DungeonParty party) {
        this.plugin = plugin;
        this.player = player;
        this.manager = manager;
        this.party = party;
    }

    public void open() {
        YamlConfiguration config = manager.getConfig();
        ConfigurationSection lobby = config.getConfigurationSection("lobby");
        int rows = Math.max(1, Math.min(6, config.getInt("lobby.rows", config.getInt("rows", 5))));
        int maxSlot = maxConfiguredSlot(lobby);
        rows = Math.max(rows, (maxSlot / 9) + 1);
        rows = Math.min(6, Math.max(4, rows));
        inventory = Bukkit.createInventory(this, rows * 9, ColorUtils.colorizeComponent(config.getString("lobby-title", "&8Lobby party")));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);
        render();
        player.openInventory(inventory);
    }

    public void handleClick(int rawSlot) {
        ConfigurationSection lobby = manager.getConfig().getConfigurationSection("lobby");
        if (lobby == null) {
            return;
        }
        int readySlot = lobby.getInt("ready-slot", 29);
        int disbandSlot = lobby.getInt("disband-slot", 31);
        int leaveSlot = lobby.getInt("leave-slot", 33);
        int backSlot = lobby.getInt("back-slot", 35);

        if (rawSlot == readySlot) {
            manager.toggleReady(player);
            return;
        }
        if (rawSlot == disbandSlot && party.isLeader(player.getUniqueId())) {
            manager.disbandParty(party, true);
            manager.openMain(player);
            return;
        }
        if ((rawSlot == leaveSlot || rawSlot == disbandSlot) && !party.isLeader(player.getUniqueId())) {
            manager.leaveParty(player);
            return;
        }
        if (rawSlot == backSlot) {
            manager.openMain(player);
        }
    }

    /** Q na glowce czlonka (tylko lider) = wyrzuc. */
    public void handleDrop(int rawSlot) {
        if (!party.isLeader(player.getUniqueId()) || party.isStarted()) {
            return;
        }
        ConfigurationSection lobby = manager.getConfig().getConfigurationSection("lobby");
        if (lobby == null) {
            return;
        }
        List<Integer> memberSlots = lobby.getIntegerList("member-slots");
        List<DungeonParty.Member> members = party.getMemberList();
        for (int i = 0; i < memberSlots.size() && i < members.size(); i++) {
            if (memberSlots.get(i) != rawSlot) {
                continue;
            }
            DungeonParty.Member member = members.get(i);
            if (member.getUuid().equals(player.getUniqueId())) {
                return;
            }
            manager.kickFromParty(player, member.getUuid());
            return;
        }
    }

    private void render() {
        ConfigurationSection lobby = manager.getConfig().getConfigurationSection("lobby");
        if (lobby == null || inventory == null) {
            return;
        }

        setItemSafe(lobby.getInt("info-slot", 4), named(
            match(lobby.getString("info-material", "BOOK"), Material.BOOK),
            lobby.getString("info-name", "&e&lINFORMACJE O PARTY"),
            replaceInfo(lobby.getStringList("info-lore"))
        ));

        List<Integer> memberSlots = lobby.getIntegerList("member-slots");
        List<DungeonParty.Member> members = party.getMemberList();
        for (int i = 0; i < memberSlots.size(); i++) {
            int slot = memberSlots.get(i);
            if (i >= party.getMaxSize()) {
                setItemSafe(slot, named(
                    match(lobby.getString("locked-material", "BARRIER"), Material.BARRIER),
                    lobby.getString("locked-name", "&cSlot zablokowany"),
                    replaceInfo(lobby.getStringList("locked-lore"))
                ));
                continue;
            }
            if (i >= members.size()) {
                setItemSafe(slot, named(
                    match(lobby.getString("empty-material", "GRAY_DYE"), Material.GRAY_DYE),
                    lobby.getString("empty-name", "&7Wolny slot"),
                    lobby.getStringList("empty-lore")
                ));
                continue;
            }
            DungeonParty.Member member = members.get(i);
            List<String> lore = new ArrayList<>(member.isReady()
                ? lobby.getStringList("member-ready-lore")
                : lobby.getStringList("member-waiting-lore"));
            if (party.isLeader(player.getUniqueId()) && !member.getUuid().equals(player.getUniqueId())) {
                lore.add("");
                lore.add("&7Nacisnij &eQ &7zeby wyrzucic z party");
            }
            setItemSafe(slot, playerHead(member, lore));
        }

        boolean ready = party.isReady(player.getUniqueId());
        setItemSafe(lobby.getInt("ready-slot", 29), named(
            match(lobby.getString(ready ? "ready-on-material" : "ready-off-material", ready ? "LIME_DYE" : "GRAY_DYE"),
                ready ? Material.LIME_DYE : Material.GRAY_DYE),
            lobby.getString(ready ? "ready-on-name" : "ready-off-name", ready ? "&a&lGOTOWOSC: WLACZONA" : "&e&lGOTOWOSC: WYLACZONA"),
            lobby.getStringList(ready ? "ready-on-lore" : "ready-off-lore")
        ));

        if (party.isLeader(player.getUniqueId())) {
            setItemSafe(lobby.getInt("disband-slot", 31), named(
                match(lobby.getString("disband-material", "RED_DYE"), Material.RED_DYE),
                lobby.getString("disband-name", "&c&lUSUN LOBBY"),
                lobby.getStringList("disband-lore")
            ));
        } else {
            setItemSafe(lobby.getInt("leave-slot", 33), named(
                match(lobby.getString("leave-material", "RED_DYE"), Material.RED_DYE),
                lobby.getString("leave-name", "&c&lOPUSC PARTY"),
                lobby.getStringList("leave-lore")
            ));
        }

        setItemSafe(lobby.getInt("back-slot", 35), named(
            Material.ARROW,
            lobby.getString("back-name", "&c&lPOWROT"),
            lobby.getStringList("back-lore")
        ));
    }

    private int maxConfiguredSlot(ConfigurationSection lobby) {
        if (lobby == null) {
            return 35;
        }
        int max = 0;
        max = Math.max(max, lobby.getInt("info-slot", 4));
        max = Math.max(max, lobby.getInt("ready-slot", 29));
        max = Math.max(max, lobby.getInt("disband-slot", 31));
        max = Math.max(max, lobby.getInt("leave-slot", 33));
        max = Math.max(max, lobby.getInt("back-slot", 35));
        for (int slot : lobby.getIntegerList("member-slots")) {
            max = Math.max(max, slot);
        }
        return max;
    }

    private void setItemSafe(int slot, ItemStack item) {
        if (inventory == null || item == null || slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
    }

    private ItemStack playerHead(DungeonParty.Member member, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(member.getUuid());
            meta.setOwningPlayer(offline);
            String color = member.isReady() ? "&a" : "&e";
            meta.displayName(noItalic(ColorUtils.colorizeComponent(color + member.getName())));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(noItalic(ColorUtils.colorizeComponent(line)));
            }
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(noItalic(ColorUtils.colorizeComponent(name)));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(noItalic(ColorUtils.colorizeComponent(line)));
            }
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> replaceInfo(List<String> lore) {
        List<String> out = new ArrayList<>();
        String visibility = party.isPublicParty() ? "Publiczne" : "Prywatne";
        String mode = party.isPremium() ? "Premium" : "Darmowe";
        for (String line : lore) {
            out.add(line
                .replace("{LEADER}", party.getLeaderName())
                .replace("{CODE}", party.getCode())
                .replace("{VISIBILITY}", visibility)
                .replace("{SIZE}", String.valueOf(party.getMaxSize()))
                .replace("{MODE}", mode)
                .replace("{TICKETS}", String.valueOf(party.getTicketCost()))
                .replace("{READY}", String.valueOf(party.getReadyCount()))
                .replace("{TOTAL}", String.valueOf(party.getMemberList().size()))
            );
        }
        return out;
    }

    private Material match(String raw, Material fallback) {
        Material material = Material.matchMaterial(raw == null ? "" : raw);
        return material == null ? fallback : material;
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
