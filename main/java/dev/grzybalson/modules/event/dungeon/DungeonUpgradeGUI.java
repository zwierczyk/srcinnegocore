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

public final class DungeonUpgradeGUI implements InventoryHolder {

    public enum UpgradeType {
        ARMOR(Material.NETHERITE_CHESTPLATE,
            "<gradient:#00FF5A:#9FFF00><bold>Protection</bold></gradient></shadow>",
            List.of("&8▪ +1 Protection (max II)", "&7Trwale na zbroi.")),
        SWORD(Material.DIAMOND_SWORD,
            "<gradient:#00C1FF:#2BD4FF><bold>Sharpness</bold></gradient></shadow>",
            List.of("&8▪ +1 Sharpness", "&7Na Twojej katanie.")),
        APPLES(Material.GOLDEN_APPLE,
            "<gradient:#FFD900:#FF9B00><bold>+6 Gapples</bold></gradient></shadow>",
            List.of("&8▪ +6 zlotych jablek.")),
        SPEED(Material.SUGAR,
            "<gradient:#7CFFCB:#00FFC1><bold>Predkosc</bold></gradient></shadow>",
            List.of("&8▪ Speed I &e(2 min)")),
        STRENGTH(Material.BLAZE_POWDER,
            "<gradient:#FF5C5C:#FF9B00><bold>Sila</bold></gradient></shadow>",
            List.of("&8▪ Strength I &e(2 min)")),
        RESIST(Material.IRON_CHESTPLATE,
            "<gradient:#B5CFD4:#FFFFFF><bold>Odpornosc</bold></gradient></shadow>",
            List.of("&8▪ Resistance I &e(2 min)")),
        MAX_HP(Material.RED_DYE,
            "<gradient:#FF3B3B:#FF5C5C><bold>+1 Serce</bold></gradient></shadow>",
            List.of("&8▪ +2 HP max (1 serce).")),
        DASH(Material.FEATHER,
            "<gradient:#E8F4FF:#00C1FF><bold>Szybki Dash</bold></gradient></shadow>",
            List.of("&8▪ Dash cooldown -2s.")),
        HEAL(Material.GOLDEN_CARROT,
            "<gradient:#00FF5A:#9FFF00><bold>Pelne leczenie</bold></gradient></shadow>",
            List.of("&8▪ Full HP + saturation.")),
        HASTE(Material.GOLDEN_PICKAXE,
            "<gradient:#FFD900:#FFFFFF><bold>Haste</bold></gradient></shadow>",
            List.of("&8▪ Haste II &e(2 min)")),
        JUMP(Material.RABBIT_FOOT,
            "<gradient:#C800FF:#FC73F7><bold>Skok</bold></gradient></shadow>",
            List.of("&8▪ Jump Boost II &e(2 min)")),
        STEAK(Material.COOKED_BEEF,
            "<gradient:#FF9B00:#FFD900><bold>Zapasy</bold></gradient></shadow>",
            List.of("&8▪ +24 stekow.")),
        ABSORB(Material.ENCHANTED_GOLDEN_APPLE,
            "<gradient:#FFD900:#C800FF><bold>Absorption</bold></gradient></shadow>",
            List.of("&8▪ Absorption II na 90s.")),
        LIFESTEAL(Material.GHAST_TEAR,
            "<gradient:#FF5C5C:#C800FF><bold>Lifesteal</bold></gradient></shadow>",
            List.of("&8▪ +1❤ przy zabiciu moba.")),
        // Totem tylko z glosowania fali 12 — nie oferowany w upgrade GUI.
        TOTEM(Material.TOTEM_OF_UNDYING,
            "<gradient:#FFD900:#00FF5A><bold>Totem</bold></gradient></shadow>",
            List.of("&7Tylko glosowanie fali 12."));

        private final Material material;
        private final String displayName;
        private final List<String> lore;

        UpgradeType(Material material, String displayName, List<String> lore) {
            this.material = material;
            this.displayName = displayName;
            this.lore = lore;
        }

        public Material material() {
            return material;
        }

        public String displayName() {
            return displayName;
        }

        public List<String> lore() {
            return lore;
        }

        public String shortLabel() {
            return switch (this) {
                case ARMOR -> "&aProtection";
                case SWORD -> "&bSharpness";
                case APPLES -> "&e+6 Gapples";
                case SPEED -> "&aSpeed";
                case STRENGTH -> "&cStrength";
                case RESIST -> "&fResistance";
                case MAX_HP -> "&c+1 Serce";
                case DASH -> "&bSzybki Dash";
                case TOTEM -> "&eTotem";
                case HEAL -> "&aHeal";
                case HASTE -> "&eHaste";
                case JUMP -> "&dJump";
                case STEAK -> "&6Steki";
                case ABSORB -> "&dAbsorption";
                case LIFESTEAL -> "&cLifesteal";
            };
        }
    }

    private static final int[] OFFER_SLOTS = {10, 11, 12, 14, 15, 16};

    private final HollyCore plugin;
    private final DungeonSession session;
    private final Player player;
    private Inventory inventory;

    public DungeonUpgradeGUI(HollyCore plugin, DungeonSession session, Player player) {
        this.plugin = plugin;
        this.session = session;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 27, ColorUtils.colorizeComponent(
            "<gradient:#00C1FF:#C800FF><bold>WYBIERZ 2 UPGRADE</bold></gradient></shadow>"));
        render();
        player.openInventory(inventory);
    }

    public void render() {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        List<UpgradeType> offered = session.getOfferedUpgrades(player.getUniqueId());
        List<UpgradeType> picked = session.getPendingUpgradePicks(player.getUniqueId());
        for (int i = 0; i < OFFER_SLOTS.length && i < offered.size(); i++) {
            UpgradeType type = offered.get(i);
            boolean selected = picked.contains(type);
            List<String> lore = new ArrayList<>(type.lore());
            lore.add("");
            lore.add(selected ? "&a✔ Wybrane" : "&7Kliknij aby wybrac");
            lore.add("&8Wybrano: &f" + picked.size() + "&8/&f2");
            inventory.setItem(OFFER_SLOTS[i], item(type.material(), type.displayName(), lore, type == UpgradeType.SWORD));
        }
        inventory.setItem(22, item(Material.PAPER,
            "<gradient:#FFFFFF:#B5CFD4><bold>STATUS</bold></gradient></shadow>",
            List.of(
                "&8▪ Wybierz &e2 &fulepszenia",
                "&7Nie mozesz powtorzyc",
                "&7wyborow z poprzedniej fali.",
                "",
                "&fTeraz: &#FFD900" + picked.size() + "&f/2"
            ), false));
    }

    public void handleClick(int slot) {
        List<UpgradeType> offered = session.getOfferedUpgrades(player.getUniqueId());
        for (int i = 0; i < OFFER_SLOTS.length && i < offered.size(); i++) {
            if (slot == OFFER_SLOTS[i]) {
                session.pickUpgrade(player, offered.get(i));
                render();
                return;
            }
        }
    }

    private ItemStack item(Material material, String name, List<String> loreLines, boolean ninjaSword) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
            meta.lore(loreLines.stream()
                .map(line -> ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false))
                .toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            if (ninjaSword) {
                meta.setCustomModelData(1);
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
