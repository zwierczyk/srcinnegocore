package dev.grzybalson.modules.mslf;

import dev.grzybalson.utils.ColorUtils;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MslfItemRegionsGUI implements Listener {

    private static final int BACK_SLOT = 45;
    private static final int STANDING_SLOT = 49;
    private static final int CHAT_SLOT = 53;

    private final MslfManager manager;
    private final Map<UUID, MslfItemType> pendingChat = new ConcurrentHashMap<>();

    public MslfItemRegionsGUI(MslfManager manager) {
        this.manager = manager;
    }

    public void open(Player player, MslfItemType type) {
        if (player == null || type == null) {
            return;
        }
        pendingChat.remove(player.getUniqueId());
        Holder holder = new Holder(type);
        Inventory inventory = Bukkit.createInventory(
            holder,
            54,
            Component.text(ColorUtils.colorize("&8MSLF &7| &f" + type.id()))
        );
        holder.inventory = inventory;
        fill(player, holder);
        player.openInventory(inventory);
    }

    private void fill(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        holder.regionSlots.clear();

        List<String> itemRegions = manager.settings().itemBlockedRegions(holder.type);
        int slot = 0;
        if (itemRegions.isEmpty()) {
            inventory.setItem(slot, named(
                Material.GRAY_STAINED_GLASS_PANE,
                "&7Brak dodatkowych regionow",
                List.of(
                    "&8× &7PPM w glownym GUI otwiera to menu.",
                    "&8× &7Dodaj region przyciskami na dole."
                )
            ));
        } else {
            for (String region : itemRegions) {
                if (slot >= 36) {
                    break;
                }
                inventory.setItem(slot, named(
                    Material.RED_CONCRETE,
                    "&c" + region,
                    List.of(
                        "&8× &7Kliknij, aby &cusunac &7blokade",
                        "&8× &7tego itemu na regionie &f" + region
                    )
                ));
                holder.regionSlots.put(slot, region);
                slot++;
            }
        }

        List<String> global = manager.settings().blockedRegions();
        inventory.setItem(40, named(
            Material.BARRIER,
            "&cGlobalne regiony",
            global.isEmpty()
                ? List.of("&8× &7Brak globalnej blokady w mslf.yml")
                : List.of(
                    "&8× &7Dotyczy &fwszystkich &7eventowek:",
                    "&f" + String.join("&7, &f", global)
                )
        ));

        List<String> standing = MslfRegionUtil.regionIdsAt(player.getLocation());
        List<String> standingLore = new ArrayList<>();
        standingLore.add("&8× &7Kliknij, aby zablokowac ten item");
        standingLore.add("&8× &7na swiecie / regionie, w ktorym stoisz.");
        if (standing.isEmpty()) {
            standingLore.add("&8× &cNie stoisz w zadnym regionie WG.");
        } else {
            standingLore.add("&8× &7Teraz: &f" + String.join("&7, &f", standing));
        }
        inventory.setItem(BACK_SLOT, named(Material.ARROW, "&7« Powrot", List.of("&8× &7Wroc do GUI MSLF")));
        inventory.setItem(STANDING_SLOT, named(Material.COMPASS, "&aRegion, w ktorym stoisz", standingLore));
        inventory.setItem(CHAT_SLOT, named(
            Material.NAME_TAG,
            "&eWpisz nazwe regionu",
            List.of(
                "&8× &7Kliknij i wpisz nazwe w czacie",
                "&8× &7(toggle dodaj/usun). &fanuluj &7przerywa."
            )
        ));
    }

    private ItemStack named(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot == BACK_SLOT) {
            manager.openGui(player);
            return;
        }
        if (slot == STANDING_SLOT) {
            List<String> standing = MslfRegionUtil.regionIdsAt(player.getLocation());
            if (standing.isEmpty()) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#FF0000✘ &8Ξ &cNie stoisz w zadnym regionie WorldGuard. Uzyj wpisywania nazwy."
                ));
                return;
            }
            toggleAndRefresh(player, holder, standing.get(0));
            return;
        }
        if (slot == CHAT_SLOT) {
            pendingChat.put(player.getUniqueId(), holder.type);
            player.closeInventory();
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#4EFA76✔ &8Ξ &7Wpisz nazwe regionu dla &f" + holder.type.id()
                    + " &7albo &fanuluj&7."
            ));
            return;
        }
        String region = holder.regionSlots.get(slot);
        if (region != null) {
            toggleAndRefresh(player, holder, region);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingChat.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        MslfItemType type = pendingChat.remove(event.getPlayer().getUniqueId());
        if (type == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(manager.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            if (message.equalsIgnoreCase("anuluj") || message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cAnulowano wpisywanie regionu."));
                open(player, type);
                return;
            }
            if (!message.matches("[A-Za-z0-9_\\-]{1,32}")) {
                pendingChat.put(player.getUniqueId(), type);
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#FF0000✘ &8Ξ &cNieprawidlowa nazwa regionu. Sprobuj ponownie albo wpisz &fanuluj&c."
                ));
                return;
            }
            boolean added = manager.settings().toggleItemBlockedRegion(type, message);
            player.sendMessage(ColorUtils.colorizeComponent(
                added
                    ? "&#00FF3B✔ &8Ξ &#4EFA76Zablokowano &f" + type.id() + " &ana regionie &f" + message + "&a."
                    : "&#00FF3B✔ &8Ξ &#4EFA76Odblokowano &f" + type.id() + " &ana regionie &f" + message + "&a."
            ));
            open(player, type);
        });
    }

    private void toggleAndRefresh(Player player, Holder holder, String region) {
        boolean added = manager.settings().toggleItemBlockedRegion(holder.type, region);
        player.sendMessage(ColorUtils.colorizeComponent(
            added
                ? "&#00FF3B✔ &8Ξ &#4EFA76Zablokowano &f" + holder.type.id() + " &ana regionie &f" + region + "&a."
                : "&#00FF3B✔ &8Ξ &#4EFA76Odblokowano &f" + holder.type.id() + " &ana regionie &f" + region + "&a."
        ));
        fill(player, holder);
    }

    private static final class Holder implements InventoryHolder {
        private final MslfItemType type;
        private final Map<Integer, String> regionSlots = new ConcurrentHashMap<>();
        private Inventory inventory;

        private Holder(MslfItemType type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
