package dev.grzybalson.modules.trade;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TradeGui implements InventoryHolder {

    private final HollyCore plugin;
    private final TradeSession session;
    private final UUID viewerId;
    private final Inventory inventory;

    public TradeGui(HollyCore plugin, TradeSession session, Player viewer) {
        this.plugin = plugin;
        this.session = session;
        this.viewerId = viewer.getUniqueId();
        TradeConfig config = plugin.getTradeManager().getConfig();
        Player partner = session.otherPlayer(viewer);
        String title = config.tradeTitle().replace("{PARTNER}", partner != null ? partner.getName() : "?");
        this.inventory = plugin.getGuiFactory().createInventory(this, TradeSlots.SIZE, title);
        refresh();
    }

    public void refresh() {
        plugin.getGuiFactory().applyDefaultTemplate(inventory);
        fillDecorations();
        renderOffers();
        renderControls();
    }

    private void fillDecorations() {
        ItemStack divider = pane(Material.CYAN_STAINED_GLASS_PANE, " ");
        for (int slot : TradeSlots.dividerSlots()) {
            inventory.setItem(slot, divider);
        }
        Set<Integer> decorative = TradeSlots.decorativeSlots();
        ItemStack gray = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot : decorative) {
            if (inventory.getItem(slot) == null || inventory.getItem(slot).getType() == Material.AIR) {
                inventory.setItem(slot, gray);
            }
        }
    }

    private void renderOffers() {
        boolean viewerIsA = session.isSideA(viewerId);
        ItemStack[] own = session.offerFor(viewerId);
        ItemStack[] partner = session.offerOfPartner(viewerId);

        int[] ownSlots = TradeSlots.sideSlots(viewerIsA);
        for (int i = 0; i < ownSlots.length; i++) {
            inventory.setItem(ownSlots[i], cloneOrNull(own[i]));
        }

        int[] partnerSlots = TradeSlots.sideSlots(!viewerIsA);
        for (int i = 0; i < partnerSlots.length; i++) {
            inventory.setItem(partnerSlots[i], cloneOrNull(partner[i]));
        }
    }

    private void renderControls() {
        boolean viewerIsA = session.isSideA(viewerId);
        Player partner = Bukkit.getPlayer(session.partnerOf(viewerId));
        boolean ownReady = session.isReady(viewerId);
        boolean partnerReady = session.isPartnerReady(viewerId);

        int ownReadySlot = viewerIsA ? TradeSlots.READY_A : TradeSlots.READY_B;
        int partnerReadySlot = viewerIsA ? TradeSlots.READY_B : TradeSlots.READY_A;

        inventory.setItem(ownReadySlot, readyButton(ownReady, true));
        inventory.setItem(partnerReadySlot, readyButton(partnerReady, false));

        String partnerName = partner != null ? partner.getName() : "?";
        inventory.setItem(TradeSlots.STATUS, statusItem(partnerName, ownReady, partnerReady));
    }

    private ItemStack readyButton(boolean ready, boolean own) {
        Material material = ready ? Material.LIME_WOOL : Material.YELLOW_WOOL;
        String name = own
            ? (ready ? "&#32F55F&lGOTOWY" : "&#FFB800&lKLIKNIJ: GOTOWY")
            : (ready ? "&#32F55F&lPARTNER GOTOWY" : "&#FF5555&lPARTNER NIE GOTOWY");
        List<String> lore = own
            ? List.of("", ready ? "&7Cofnij gotowość klikając ponownie." : "&aKliknij, gdy skończysz układać itemy.")
            : List.of("", "&7Status drugiej osoby.");
        return pane(material, name, lore);
    }

    private ItemStack statusItem(String partner, boolean ownReady, boolean partnerReady) {
        return pane(
            Material.NETHER_STAR,
            "&#00E0FF&lWYMIANA",
            List.of(
                "",
                "&7Partner: &#00E0FF" + partner,
                "",
                "&8▸ &7Ty: " + (ownReady ? "&#32F55F✔" : "&#FF5555✘"),
                "&8▸ &7On: " + (partnerReady ? "&#32F55F✔" : "&#FF5555✘"),
                "",
                "&7Kliknij item w EQ, aby dodać.",
                "&7Kliknij item w oknie, aby wyciągnąć."
            )
        );
    }

    private ItemStack pane(Material material, String name) {
        return pane(material, name, List.of());
    }

    private ItemStack pane(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize(name));
            if (!lore.isEmpty()) {
                meta.setLore(lore.stream().map(ColorUtils::colorize).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    public TradeSession getSession() {
        return session;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public void open(Player viewer) {
        viewer.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public static List<ItemStack> collectOfferItems(ItemStack[] offer) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : offer) {
            if (stack != null && !stack.getType().isAir()) {
                items.add(stack.clone());
            }
        }
        return items;
    }
}
