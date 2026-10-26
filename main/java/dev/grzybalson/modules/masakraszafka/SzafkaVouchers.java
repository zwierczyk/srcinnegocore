package dev.grzybalson.modules.masakraszafka;

import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SzafkaVouchers implements Listener {
    private final MasakraSzafkaManager manager;
    private final NamespacedKey idKey;
    private final NamespacedKey itemKey;
    private final NamespacedKey durationKey;

    SzafkaVouchers(Plugin plugin, MasakraSzafkaManager manager) {
        this.manager = manager;
        idKey = new NamespacedKey(plugin, "szafka-voucher-id");
        itemKey = new NamespacedKey(plugin, "szafka-voucher-item");
        durationKey = new NamespacedKey(plugin, "szafka-voucher-duration");
    }

    ItemStack create(SzafkaCatalog catalog, SzafkaItem item, long duration) {
        ItemStack stack = item.createIcon(item.kind() == SzafkaItem.Kind.COSTUME
            ? catalog.material("vouchers.costume-material", item.icon())
            : item.icon());
        ItemMeta meta = stack.getItemMeta();
        Map<String, String> values = Map.of("NAME", item.name(), "DURATION", catalog.formatDuration(duration));
        meta.displayName(text(SzafkaCatalog.replace(catalog.text("vouchers.name", "{NAME}"), values)));
        meta.lore(SzafkaCatalog.expand(catalog.lines("vouchers.lore"), Map.of(
            "{PROPERTIES}", manager.propertyLines(item),
            "{RARITY}", List.of(item.rarity())), values).stream().map(SzafkaVouchers::text).toList());
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        data.set(itemKey, PersistentDataType.STRING, item.kind().label + ":" + item.id());
        data.set(durationKey, PersistentDataType.LONG, duration);
        stack.setItemMeta(meta);
        return stack;
    }

    Voucher read(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
        String id = data.get(idKey, PersistentDataType.STRING);
        String item = data.get(itemKey, PersistentDataType.STRING);
        Long duration = data.get(durationKey, PersistentDataType.LONG);
        if (id == null || item == null || duration == null || duration <= 0) return null;
        String[] parts = item.split(":", 2);
        SzafkaItem.Kind kind = SzafkaItem.Kind.parse(parts[0]);
        if (kind == null || parts.length < 2) return null;
        try {
            return new Voucher(UUID.fromString(id), kind, parts[1], duration);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Voucher voucher = read(event.getItem());
        if (voucher == null) return;
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() != null) manager.redeem(event.getPlayer(), event.getHand(), voucher);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityUse(PlayerInteractEntityEvent event) {
        if (read(event.getPlayer().getInventory().getItem(event.getHand())) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (read(event.getItemInHand()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (read(ingredient) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    private static Component text(String line) {
        return ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false);
    }

    record Voucher(UUID id, SzafkaItem.Kind kind, String item, long duration) {
    }
}
