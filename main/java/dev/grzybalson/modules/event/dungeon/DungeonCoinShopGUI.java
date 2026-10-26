package dev.grzybalson.modules.event.dungeon;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop za coiny — osobny od darmowych upgrade'ow. Mozna pominac.
 */
public final class DungeonCoinShopGUI implements InventoryHolder {

    public enum Offer {
        HEAL(Material.GOLDEN_CARROT, 8, "<gradient:#00FF5A:#9FFF00><bold>Leczenie</bold></gradient></shadow>",
            List.of("&8▪ +8 HP", "&7Koszt: &#FFD9008 coin")),
        ABSORB(Material.GOLDEN_APPLE, 12, "<gradient:#FFD900:#FF9B00><bold>Absorption</bold></gradient></shadow>",
            List.of("&8▪ Absorption I (45s)", "&7Koszt: &#FFD90012 coin")),
        SPEED(Material.SUGAR, 10, "<gradient:#7CFFCB:#00C1FF><bold>Speed burst</bold></gradient></shadow>",
            List.of("&8▪ Speed II (40s)", "&7Koszt: &#FFD90010 coin")),
        STRENGTH(Material.BLAZE_POWDER, 14, "<gradient:#FF5C5C:#FF9B00><bold>Sila burst</bold></gradient></shadow>",
            List.of("&8▪ Strength I (30s)", "&7Koszt: &#FFD90014 coin")),
        DASH(Material.FEATHER, 6, "<gradient:#E8F4FF:#00C1FF><bold>Reset Dash</bold></gradient></shadow>",
            List.of("&8▪ Odnowienie dasha", "&7Koszt: &#FFD9006 coin")),
        GAPPLES(Material.GOLDEN_APPLE, 12, "<gradient:#FFD900:#FFFFFF><bold>+3 Gapples</bold></gradient></shadow>",
            List.of("&8▪ +3 zlotych jablek", "&7Koszt: &#FFD90012 coin"));

        private final Material material;
        private final int cost;
        private final String name;
        private final List<String> lore;

        Offer(Material material, int cost, String name, List<String> lore) {
            this.material = material;
            this.cost = cost;
            this.name = name;
            this.lore = lore;
        }

        public int cost() {
            return cost;
        }
    }

    private static final int[] OFFER_SLOTS = {10, 11, 12, 14, 15, 16};

    private final HollyCore plugin;
    private final DungeonSession session;
    private final Player player;
    private Inventory inventory;

    public DungeonCoinShopGUI(HollyCore plugin, DungeonSession session, Player player) {
        this.plugin = plugin;
        this.session = session;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 27, ColorUtils.colorizeComponent(
            "<gradient:#FFD900:#FF9B00><bold>SHOP COINOW</bold></gradient></shadow>"));
        render();
        player.openInventory(inventory);
    }

    public void render() {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        int coins = session.getCoinBalance(player.getUniqueId());
        Offer[] offers = Offer.values();
        for (int i = 0; i < OFFER_SLOTS.length && i < offers.length; i++) {
            Offer offer = offers[i];
            List<String> lore = new ArrayList<>(offer.lore);
            lore.add("");
            lore.add(coins >= offer.cost ? "&aKliknij aby kupic" : "&cZa malo coinow");
            inventory.setItem(OFFER_SLOTS[i], item(offer.material, offer.name, lore));
        }
        inventory.setItem(22, item(Material.BARRIER,
            "<gradient:#FF5C5C:#FF9B00><bold>POMIN / DALEJ</bold></gradient></shadow>",
            List.of("&8▪ Masz: &#FFD900" + coins + " coin", "&7Mozesz nic nie kupowac.")));
        inventory.setItem(4, item(Material.GOLD_NUGGET,
            "<gradient:#FFD900:#FFFFFF><bold>TWOJE COINY</bold></gradient></shadow>",
            List.of("&8▪ Saldo: &#FFD900" + coins)));
    }

    public void handleClick(int slot) {
        if (slot == 22) {
            session.finishCoinShop(player);
            return;
        }
        Offer[] offers = Offer.values();
        for (int i = 0; i < OFFER_SLOTS.length && i < offers.length; i++) {
            if (slot == OFFER_SLOTS[i]) {
                session.buyCoinShopOffer(player, offers[i]);
                render();
                return;
            }
        }
    }

    private ItemStack item(Material material, String name, List<String> loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
            meta.lore(loreLines.stream()
                .map(line -> ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false))
                .toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            if (material == Material.GOLD_NUGGET) {
                meta.setCustomModelData(7001);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
