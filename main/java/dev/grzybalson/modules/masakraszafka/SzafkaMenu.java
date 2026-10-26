package dev.grzybalson.modules.masakraszafka;

import dev.grzybalson.utils.ClickCooldown;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class SzafkaMenu implements Listener {
    private static final Set<ClickType> ALLOWED_CLICKS = Set.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT);

    private final MasakraSzafkaManager manager;
    private final NamespacedKey marker;

    SzafkaMenu(Plugin plugin, MasakraSzafkaManager manager) {
        this.manager = manager;
        marker = new NamespacedKey(plugin, "szafka-gui-item");
    }

    void open(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        render(player, holder);
        player.openInventory(holder.inventory);
        manager.catalog().play(player, "open");
    }

    void refreshOpen() {
        long minute = System.currentTimeMillis() / 60_000L;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder && holder.owner.equals(player.getUniqueId())
                && (holder.renderedMinute != minute || holder.renderedRevision != manager.revision(holder.owner))) {
                render(player, holder);
            }
        }
    }

    void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) player.closeInventory();
        }
    }

    private void render(Player player, Holder holder) {
        SzafkaCatalog catalog = manager.catalog();
        SzafkaProfile profile = manager.profile(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (holder.inventory == null) {
            holder.inventory = Bukkit.createInventory(holder, SzafkaCatalog.GUI_SIZE, ColorUtils.colorizeComponent(catalog.text("gui.title", "&8Szafka")));
        }
        holder.actions.clear();
        ItemStack[] contents = new ItemStack[SzafkaCatalog.GUI_SIZE];
        ItemStack filler = pane(catalog.material("gui.filler", Material.BLACK_STAINED_GLASS_PANE));
        ItemStack separator = pane(catalog.material("gui.separator", Material.GRAY_STAINED_GLASS_PANE));
        for (int slot = 0; slot < contents.length; slot++) contents[slot] = filler;
        for (int slot : catalog.slots("gui.separator-slots")) contents[slot] = separator;

        contents[catalog.slot("gui.header-slot", 4)] = header(player, catalog, profile, now);
        renderCostumeSlot(contents, holder, player, catalog, profile, now);
        renderTalismanSlots(contents, holder, player, catalog, profile, now);
        renderList(contents, holder, player, catalog, profile, now);

        contents[catalog.slot("gui.filter-slot", 45)] = button(catalog, "gui.items.filter", Map.of("FILTER", catalog.text("gui.filters." + holder.filter.key, holder.filter.key)));
        holder.actions.put(catalog.slot("gui.filter-slot", 45), () -> {
            holder.filter = holder.filter.next();
            holder.page = 0;
            catalog.play(player, "click");
        });
        int close = catalog.slot("gui.close-slot", 49);
        contents[close] = button(catalog, "gui.items.close", Map.of());
        holder.actions.put(close, () -> Bukkit.getScheduler().runTask(manager.plugin(), () -> player.closeInventory()));
        contents[catalog.slot("gui.info-slot", 53)] = button(catalog, "gui.items.info", Map.of());
        holder.inventory.setContents(contents);
        holder.renderedMinute = now / 60_000L;
        holder.renderedRevision = manager.revision(holder.owner);
    }

    private void renderCostumeSlot(ItemStack[] contents, Holder holder, Player player, SzafkaCatalog catalog, SzafkaProfile profile, long now) {
        int slot = catalog.slot("gui.costume-slot", 11);
        SzafkaItem costume = profile == null ? null : manager.activeCostume(profile, now);
        if (costume == null) {
            contents[slot] = button(catalog, "gui.items.costume-empty", Map.of());
            return;
        }
        contents[slot] = entry(catalog, profile, costume, now);
        holder.actions.put(slot, () -> manager.toggle(player, costume));
    }

    private void renderTalismanSlots(ItemStack[] contents, Holder holder, Player player, SzafkaCatalog catalog, SzafkaProfile profile, long now) {
        List<Integer> slots = catalog.talismanSlots();
        List<SzafkaItem> equipped = profile == null ? List.of() : manager.activeTalismans(profile, now);
        for (int index = 0; index < slots.size(); index++) {
            int slot = slots.get(index);
            if (index >= catalog.maxTalismans) {
                contents[slot] = button(catalog, "gui.items.talisman-locked", Map.of());
            } else if (index < equipped.size()) {
                SzafkaItem talisman = equipped.get(index);
                contents[slot] = entry(catalog, profile, talisman, now);
                holder.actions.put(slot, () -> manager.toggle(player, talisman));
            } else {
                contents[slot] = button(catalog, "gui.items.talisman-empty", Map.of("SLOT", String.valueOf(index + 1)));
            }
        }
    }

    private void renderList(ItemStack[] contents, Holder holder, Player player, SzafkaCatalog catalog, SzafkaProfile profile, long now) {
        List<Integer> slots = catalog.slots("gui.list-slots");
        if (slots.isEmpty()) return;
        List<SzafkaItem> owned = new ArrayList<>();
        List<SzafkaItem> locked = new ArrayList<>();
        for (SzafkaItem.Kind kind : SzafkaItem.Kind.values()) {
            if (!holder.filter.accepts(kind)) continue;
            for (SzafkaItem item : catalog.items(kind)) {
                if (profile != null && profile.owns(kind, item.id(), now)) owned.add(item);
                else locked.add(item);
            }
        }
        owned.addAll(locked);
        int pages = Math.max(1, (owned.size() + slots.size() - 1) / slots.size());
        holder.page = Math.max(0, Math.min(holder.page, pages - 1));
        if (owned.isEmpty()) {
            contents[slots.get(slots.size() / 2)] = button(catalog, "gui.items.empty-list", Map.of());
        }
        for (int index = 0; index < slots.size(); index++) {
            int position = holder.page * slots.size() + index;
            if (position >= owned.size()) break;
            SzafkaItem item = owned.get(position);
            int slot = slots.get(index);
            contents[slot] = entry(catalog, profile, item, now);
            holder.actions.put(slot, () -> manager.toggle(player, item));
        }
        Map<String, String> pageValues = Map.of("PAGE", String.valueOf(holder.page + 1), "PAGES", String.valueOf(pages));
        if (holder.page > 0) {
            int previous = catalog.slot("gui.previous-slot", 48);
            contents[previous] = button(catalog, "gui.items.previous", pageValues);
            holder.actions.put(previous, () -> {
                holder.page--;
                catalog.play(player, "click");
            });
        }
        if (holder.page < pages - 1) {
            int next = catalog.slot("gui.next-slot", 50);
            contents[next] = button(catalog, "gui.items.next", pageValues);
            holder.actions.put(next, () -> {
                holder.page++;
                catalog.play(player, "click");
            });
        }
    }

    private ItemStack entry(SzafkaCatalog catalog, SzafkaProfile profile, SzafkaItem item, long now) {
        boolean owned = profile != null && profile.owns(item.kind(), item.id(), now);
        boolean equipped = owned && profile.isEquipped(item.kind(), item.id());
        long expiry = owned ? profile.expiry(item.kind(), item.id()) : 0;
        String kind = item.kind() == SzafkaItem.Kind.COSTUME ? "costume" : "talisman";
        String action;
        if (!owned) action = catalog.text("gui.actions.locked", "");
        else if (equipped) action = catalog.text("gui.actions." + kind + "-unequip", "");
        else if (item.kind() == SzafkaItem.Kind.TALISMAN && profile.talismans().size() >= catalog.maxTalismans) action = catalog.text("gui.actions.talisman-full", "");
        else action = catalog.text("gui.actions." + kind + "-equip", "");
        Map<String, String> values = Map.of(
            "EXPIRES", owned ? catalog.formatDate(expiry) : "",
            "REMAINING", owned ? catalog.formatDuration(expiry == SzafkaCatalog.PERMANENT ? expiry : expiry - now) : "");
        Map<String, List<String>> blocks = new HashMap<>();
        blocks.put("{PROPERTIES}", manager.propertyLines(item));
        blocks.put("{STATE}", catalog.lines(owned ? "gui.state-owned" : "gui.state-locked"));
        blocks.put("{ACTION}", List.of(action));
        blocks.put("{RARITY}", List.of(item.rarity()));

        ItemStack stack = item.createIcon(item.icon());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(text((equipped ? catalog.text("gui.equipped-prefix", "") : "") + item.name()));
        meta.lore(SzafkaCatalog.expand(catalog.lines("gui.entry-lore"), blocks, values).stream().map(SzafkaMenu::text).toList());
        if (equipped) meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        mark(meta);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack header(Player player, SzafkaCatalog catalog, SzafkaProfile profile, long now) {
        SzafkaItem costume = profile == null ? null : manager.activeCostume(profile, now);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("COSTUME", costume == null ? catalog.text("gui.items.header.no-costume", "brak") : costume.name());
        values.put("TALISMANS", String.valueOf(profile == null ? 0 : manager.activeTalismans(profile, now).size()));
        values.put("MAX_TALISMANS", String.valueOf(catalog.maxTalismans));
        values.put("OWNED_COSTUMES", String.valueOf(countOwned(catalog, profile, SzafkaItem.Kind.COSTUME, now)));
        values.put("OWNED_TALISMANS", String.valueOf(countOwned(catalog, profile, SzafkaItem.Kind.TALISMAN, now)));
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(text(SzafkaCatalog.replace(catalog.text("gui.items.header.name", "&6Szafka"), values)));
        meta.lore(catalog.lines("gui.items.header.lore").stream().map(line -> text(SzafkaCatalog.replace(line, values))).toList());
        mark(meta);
        head.setItemMeta(meta);
        return head;
    }

    private static long countOwned(SzafkaCatalog catalog, SzafkaProfile profile, SzafkaItem.Kind kind, long now) {
        if (profile == null) return 0;
        return catalog.items(kind).stream().filter(item -> profile.owns(kind, item.id(), now)).count();
    }

    private ItemStack button(SzafkaCatalog catalog, String path, Map<String, String> values) {
        ItemStack stack = new ItemStack(catalog.material(path + ".material", Material.PAPER));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(text(SzafkaCatalog.replace(catalog.text(path + ".name", " "), values)));
        meta.lore(catalog.lines(path + ".lore").stream().map(line -> text(SzafkaCatalog.replace(line, values))).toList());
        meta.addItemFlags(ItemFlag.values());
        mark(meta);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        mark(meta);
        stack.setItemMeta(meta);
        return stack;
    }

    private void mark(ItemMeta meta) {
        meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean isMarked(ItemStack stack) {
        return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (event.getClickedInventory() != event.getView().getTopInventory() || !ALLOWED_CLICKS.contains(event.getClick())) return;
        Runnable action = holder.actions.get(event.getRawSlot());
        if (action == null || !ClickCooldown.tryUse(player, "masakra-szafka-gui", manager.catalog().clickCooldown)) return;
        action.run();
        if (player.getOpenInventory().getTopInventory().getHolder() == holder) render(player, holder);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreative(InventoryCreativeEvent event) {
        if (!isMarked(event.getCursor()) && !isMarked(event.getCurrentItem())) return;
        event.setCancelled(true);
        event.setCursor(new ItemStack(Material.AIR));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) purge(event.getPlayer());
    }

    private void purge(HumanEntity player) {
        if (isMarked(player.getItemOnCursor())) player.setItemOnCursor(null);
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isMarked(contents[slot])) player.getInventory().setItem(slot, null);
        }
    }

    private static Component text(String line) {
        return ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false);
    }

    private enum Filter {
        ALL("all"),
        COSTUMES("costumes"),
        TALISMANS("talismans");

        final String key;

        Filter(String key) {
            this.key = key;
        }

        Filter next() {
            return values()[(ordinal() + 1) % values().length];
        }

        boolean accepts(SzafkaItem.Kind kind) {
            return this == ALL || (this == COSTUMES) == (kind == SzafkaItem.Kind.COSTUME);
        }
    }

    static final class Holder implements InventoryHolder {
        private final UUID owner;
        private final Map<Integer, Runnable> actions = new HashMap<>();
        private Inventory inventory;
        private Filter filter = Filter.ALL;
        private int page;
        private long renderedMinute = -1;
        private int renderedRevision = -1;

        private Holder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
