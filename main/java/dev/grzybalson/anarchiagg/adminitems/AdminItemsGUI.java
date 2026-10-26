package dev.grzybalson.anarchiagg.adminitems;

import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.util.ItemCompatibilityUtil;
import dev.grzybalson.anarchiagg.util.SoundsUtil;
import dev.grzybalson.utils.ColorUtils;

public class AdminItemsGUI
implements InventoryHolder {
    private final Inventory inventory;
    private static final Map<String, ItemStack> ITEM_CACHE = new HashMap<String, ItemStack>();
    public static NamespacedKey KEY_LASSO;
    public static NamespacedKey KEY_ROZGA;
    public static NamespacedKey KEY_DYNIO_GRANAT;
    public static NamespacedKey KEY_KLATWA_GORNIKA;
    public static NamespacedKey KEY_POKEBALL;
    public static NamespacedKey KEY_KOLCE;
    public static NamespacedKey KEY_PUSZKA_PANDORY;
    public static NamespacedKey KEY_LODOWA_ROZDZKA;
    public static NamespacedKey KEY_NOS_OLAFA;
    public static NamespacedKey KEY_MIKSTURA_ODMLODZENIA;
    public static NamespacedKey KEY_KOZA;
    public static NamespacedKey KEY_HAK;
    public static NamespacedKey KEY_TORNADO;
    public static NamespacedKey KEY_LUPA_OSWIECENIA;
    public static NamespacedKey KEY_CHORUS_UCIECZKI;
    public static NamespacedKey KEY_TROJZAB_ZNISZCZENIA;
    public static NamespacedKey KEY_TORBA_KANGURA;
    public static NamespacedKey KEY_GWIEZDNA_KLATA;
    public static NamespacedKey KEY_SMOCZA_FAJERWERKA;
    public static NamespacedKey KEY_ZLOTA_CZASZKA;
    public static NamespacedKey KEY_ANTY_COBWEB;
    public static NamespacedKey KEY_NIESKONCZONE_ZLOTE_JAPKO;
    public static NamespacedKey KEY_KARTY_PIEKLA;
    public static NamespacedKey KEY_MAGICZNY_KRYSZTAL;
    public static NamespacedKey KEY_LASKA_MISTRZA_WU;
    public static NamespacedKey KEY_SERCE_ZIMY;
    public static NamespacedKey KEY_SMOCZY_KIEL;

    public static void prepareCache(HollyCore plugin) {
        AdminItemsConfig config = plugin.getAdminItemsConfig();
        KEY_LASSO = new NamespacedKey((Plugin)plugin, "admin_item_lasso");
        KEY_ROZGA = new NamespacedKey((Plugin)plugin, "admin_item_rozga");
        KEY_DYNIO_GRANAT = new NamespacedKey((Plugin)plugin, "admin_item_dyniowy_granat");
        KEY_KLATWA_GORNIKA = new NamespacedKey((Plugin)plugin, "admin_item_klatwa_gornika");
        KEY_POKEBALL = new NamespacedKey((Plugin)plugin, "admin_item_pokeball");
        KEY_KOLCE = new NamespacedKey((Plugin)plugin, "admin_item_kolce");
        KEY_PUSZKA_PANDORY = new NamespacedKey((Plugin)plugin, "admin_item_puszka_pandory");
        KEY_LODOWA_ROZDZKA = new NamespacedKey((Plugin)plugin, "admin_item_lodowa_rozdzka");
        KEY_NOS_OLAFA = new NamespacedKey((Plugin)plugin, "admin_item_nos_olafa");
        KEY_MIKSTURA_ODMLODZENIA = new NamespacedKey((Plugin)plugin, "admin_item_mikstura_odmlodzenia");
        KEY_KOZA = new NamespacedKey((Plugin)plugin, "admin_item_koza");
        KEY_HAK = new NamespacedKey((Plugin)plugin, "admin_item_hak");
        KEY_TORNADO = new NamespacedKey((Plugin)plugin, "admin_item_tornado");
        KEY_LUPA_OSWIECENIA = new NamespacedKey((Plugin)plugin, "admin_item_lupa_oswiecenia");
        KEY_CHORUS_UCIECZKI = new NamespacedKey((Plugin)plugin, "admin_item_chorus_ucieczki");
        KEY_TROJZAB_ZNISZCZENIA = new NamespacedKey((Plugin)plugin, "admin_item_trojzab_zniszczenia");
        KEY_TORBA_KANGURA = new NamespacedKey((Plugin)plugin, "admin_item_torba_kangura");
        KEY_GWIEZDNA_KLATA = new NamespacedKey((Plugin)plugin, "admin_item_gwiezdna_klata");
        KEY_SMOCZA_FAJERWERKA = new NamespacedKey((Plugin)plugin, "admin_item_smocza_fajerwerka");
        KEY_ZLOTA_CZASZKA = new NamespacedKey((Plugin)plugin, "admin_item_zlota_czaszka");
        KEY_ANTY_COBWEB = new NamespacedKey((Plugin)plugin, "admin_item_anty_cobweb");
        KEY_NIESKONCZONE_ZLOTE_JAPKO = new NamespacedKey((Plugin)plugin, "admin_item_nieskonczone_zlote_japko");
        KEY_KARTY_PIEKLA = new NamespacedKey((Plugin)plugin, "admin_item_karty_piekla");
        KEY_MAGICZNY_KRYSZTAL = new NamespacedKey((Plugin)plugin, "admin_item_magiczny_krysztal");
        KEY_LASKA_MISTRZA_WU = new NamespacedKey((Plugin)plugin, "admin_item_laska_mistrza_wu");
        KEY_SERCE_ZIMY = new NamespacedKey((Plugin)plugin, "admin_item_serce_zimy");
        KEY_SMOCZY_KIEL = new NamespacedKey((Plugin)plugin, "admin_item_smoczy_kiel");
        ITEM_CACHE.clear();
        if (config.getLasso().isEnabled()) {
            ITEM_CACHE.put("lasso", AdminItemsGUI.buildLasso(config));
        }
        if (config.getRod().isEnabled()) {
            ITEM_CACHE.put("rozga", AdminItemsGUI.buildRod(config));
        }
        if (config.getPumpkinGrenade().isEnabled()) {
            ITEM_CACHE.put("granat", AdminItemsGUI.buildPumpkinGrenade(config));
        }
        if (config.getMinerCurse().isEnabled()) {
            ITEM_CACHE.put("klatwa", AdminItemsGUI.buildMinerCurse(config));
        }
        if (config.getPokeBall().isEnabled()) {
            ITEM_CACHE.put("pokeball", AdminItemsGUI.buildPokeBall(config));
        }
        if (config.getSpikes().isEnabled()) {
            ITEM_CACHE.put("kolce", AdminItemsGUI.buildSpikes(config));
        }
        if (config.getPandoraBox().isEnabled()) {
            ITEM_CACHE.put("puszka", AdminItemsGUI.buildPandoraBox(config));
        }
        if (config.getIceWand().isEnabled()) {
            ITEM_CACHE.put("lodowa", AdminItemsGUI.buildIceWand(config));
        }
        if (config.getOlafNose().isEnabled()) {
            ITEM_CACHE.put("nos", AdminItemsGUI.buildOlafNose(config));
        }
        if (config.getRejuvenationPotion().isEnabled()) {
            ITEM_CACHE.put("mikstura", AdminItemsGUI.buildRejuvenationPotion(config));
        }
        if (config.getGoat().isEnabled()) {
            ITEM_CACHE.put("koza", AdminItemsGUI.buildKoza(config));
        }
        if (config.getHook().isEnabled()) {
            ITEM_CACHE.put("hak", AdminItemsGUI.buildHook(config));
        }
        if (config.getTornado().isEnabled()) {
            ITEM_CACHE.put("tornado", AdminItemsGUI.buildTornado(config));
        }
        if (config.getEnlightenmentMagnifier().isEnabled()) {
            ITEM_CACHE.put("lupa", AdminItemsGUI.buildEnlightenmentMagnifier(config));
        }
        if (config.getEscapeChorus().isEnabled()) {
            ITEM_CACHE.put("chorus", AdminItemsGUI.buildEscapeChorus(config));
        }
        if (config.getDestructionTrident().isEnabled()) {
            ITEM_CACHE.put("trojzab", AdminItemsGUI.buildDestructionTrident(config));
        }
        if (config.getKangarooBag().isEnabled()) {
            ITEM_CACHE.put("torba", AdminItemsGUI.buildKangarooBag(config));
        }
        if (config.getStarChestplate().isEnabled()) {
            ITEM_CACHE.put("klata", AdminItemsGUI.buildStarChestplate(config));
        }
        if (config.getDragonFirework().isEnabled()) {
            ITEM_CACHE.put("smocza", AdminItemsGUI.buildDragonFirework(config));
        }
        if (config.getGoldenSkull() != null && config.getGoldenSkull().isEnabled()) {
            ITEM_CACHE.put("czaszka", AdminItemsGUI.buildGoldenSkull(config));
        }
        if (config.getAntiCobweb().isEnabled()) {
            ITEM_CACHE.put("antycobweb", AdminItemsGUI.buildAntiCobweb(config));
        }
        if (config.getInfiniteGoldenApple().isEnabled()) {
            ITEM_CACHE.put("japko", AdminItemsGUI.buildInfiniteGoldenApple(config));
        }
        if (config.getHellCards().isEnabled()) {
            ITEM_CACHE.put("kartypiekla", AdminItemsGUI.buildHellCards(config));
        }
        if (config.getMagicCrystal().isEnabled()) {
            ITEM_CACHE.put("krysztal", AdminItemsGUI.buildMagicCrystal(config));
        }
        if (config.getWuStaff().isEnabled()) {
            ITEM_CACHE.put("laskawu", AdminItemsGUI.buildWuStaff(config));
        }
        if (config.getWinterHeart().isEnabled()) {
            ITEM_CACHE.put("sercezimy", AdminItemsGUI.buildWinterHeart(config));
        }
        if (config.getDragonFang().isEnabled()) {
            ITEM_CACHE.put("smoczykiel", AdminItemsGUI.buildDragonFang(config));
        }
    }

    public AdminItemsGUI(HollyCore plugin) {
        AdminItemsConfig config = plugin.getAdminItemsConfig();
        String title = config.getGui().getItemsGuiTitle();
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)54, (Component)Component.text((String)dev.grzybalson.utils.ColorUtils.colorize(title)));
        this.fillSlot(0, "lasso");
        this.fillSlot(1, "rozga");
        this.fillSlot(2, "granat");
        this.fillSlot(3, "klatwa");
        this.fillSlot(4, "pokeball");
        this.fillSlot(5, "kolce");
        this.fillSlot(6, "puszka");
        this.fillSlot(7, "lodowa");
        this.fillSlot(8, "nos");
        this.fillSlot(9, "mikstura");
        this.fillSlot(10, "koza");
        this.fillSlot(11, "hak");
        this.fillSlot(12, "tornado");
        this.fillSlot(13, "lupa");
        this.fillSlot(14, "chorus");
        this.fillSlot(15, "trojzab");
        this.fillSlot(16, "torba");
        this.fillSlot(17, "klata");
        this.fillSlot(18, "czaszka");
        this.fillSlot(19, "smocza");
        this.fillSlot(20, "antycobweb");
        this.fillSlot(21, "japko");
        this.fillSlot(22, "kartypiekla");
        this.fillSlot(23, "krysztal");
        this.fillSlot(24, "laskawu");
        this.fillSlot(25, "sercezimy");
        this.fillSlot(26, "smoczykiel");
    }

    private void fillSlot(int slot, String cacheKey) {
        ItemStack cachedItem = ITEM_CACHE.get(cacheKey);
        if (cachedItem != null) {
            this.inventory.setItem(slot, cloneWithoutUniqueUid(cachedItem));
        }
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public void open(Player player) {
        player.openInventory(this.inventory);
        SoundsUtil.gui(player);
    }

    public static ItemStack getCachedItem(String key) {
        ItemStack item = ITEM_CACHE.get(key);
        return item != null ? cloneWithoutUniqueUid(item) : null;
    }

    /** Szablony /aitems bez UUID — Unique stampuje dopiero skanem EQ gracza bez bypass. */
    private static ItemStack cloneWithoutUniqueUid(ItemStack source) {
        ItemStack clone = source.clone();
        Plugin plugin = Bukkit.getPluginManager().getPlugin("HollyCore");
        if (plugin instanceof HollyCore hollyCore && hollyCore.getUniqueItemService() != null) {
            hollyCore.getUniqueItemService().clearUid(clone);
        }
        return clone;
    }

    private static ItemStack buildLasso(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.LEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.LassoSettings settings = config.getLasso();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_LASSO, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildRod(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.RodSettings settings = config.getRod();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_ROZGA, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildPumpkinGrenade(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.JACK_O_LANTERN);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.PumpkinGrenadeSettings settings = config.getPumpkinGrenade();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_DYNIO_GRANAT, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildMinerCurse(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.GOLDEN_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.MinerCurseSettings settings = config.getMinerCurse();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_KLATWA_GORNIKA, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildPokeBall(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.PokeBallSettings settings = config.getPokeBall();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_POKEBALL, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildSpikes(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.WEEPING_VINES);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.SpikesSettings settings = config.getSpikes();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_KOLCE, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildPandoraBox(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.FLOWER_POT);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.PandoraBoxSettings settings = config.getPandoraBox();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_PUSZKA_PANDORY, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildIceWand(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.IceWandSettings settings = config.getIceWand();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_LODOWA_ROZDZKA, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildOlafNose(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.CARROT);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.OlafNoseSettings settings = config.getOlafNose();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_NOS_OLAFA, PersistentDataType.BYTE, (byte)1);
        meta.addEnchant(Enchantment.KNOCKBACK, 4, true);
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildRejuvenationPotion(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.HONEY_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.RejuvenationPotionSettings settings = config.getRejuvenationPotion();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_MIKSTURA_ODMLODZENIA, PersistentDataType.BYTE, (byte)1);
        ItemCompatibilityUtil.addSafeItemFlags(meta);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildKoza(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.SHULKER_SHELL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.GoatSettings settings = config.getGoat();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_KOZA, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildHook(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.HookSettings settings = config.getHook();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_HAK, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildTornado(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.PHANTOM_MEMBRANE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.TornadoSettings settings = config.getTornado();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_TORNADO, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildEnlightenmentMagnifier(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.SOUL_TORCH);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.EnlightenmentMagnifierSettings settings = config.getEnlightenmentMagnifier();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_LUPA_OSWIECENIA, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildEscapeChorus(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.CHORUS_FRUIT);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.EscapeChorusSettings settings = config.getEscapeChorus();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_CHORUS_UCIECZKI, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildDestructionTrident(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.DestructionTridentSettings settings = config.getDestructionTrident();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_TROJZAB_ZNISZCZENIA, PersistentDataType.BYTE, (byte)1);
        meta.setUnbreakable(true);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildKangarooBag(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.ORANGE_SHULKER_BOX);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.KangarooBagSettings settings = config.getKangarooBag();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_TORBA_KANGURA, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildStarChestplate(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.StarChestplateSettings settings = config.getStarChestplate();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_GWIEZDNA_KLATA, PersistentDataType.BYTE, (byte)1);
        meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 44, true);
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
        meta.setUnbreakable(true);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildDragonFirework(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.DragonFireworkSettings settings = config.getDragonFirework();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_SMOCZA_FAJERWERKA, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildAntiCobweb(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.AntiCobwebSettings settings = config.getAntiCobweb();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_ANTY_COBWEB, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildInfiniteGoldenApple(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.InfiniteGoldenAppleSettings settings = config.getInfiniteGoldenApple();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_NIESKONCZONE_ZLOTE_JAPKO, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildHellCards(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.HellCardsSettings settings = config.getHellCards();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_KARTY_PIEKLA, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildMagicCrystal(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.MagicCrystalSettings settings = config.getMagicCrystal();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_MAGICZNY_KRYSZTAL, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildWuStaff(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.WuStaffSettings settings = config.getWuStaff();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_LASKA_MISTRZA_WU, PersistentDataType.BYTE, (byte)1);
        meta.addEnchant(Enchantment.KNOCKBACK, Math.max(1, settings.getKnockbackLevel()), true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildWinterHeart(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.BLUE_ICE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.WinterHeartSettings settings = config.getWinterHeart();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_SERCE_ZIMY, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildDragonFang(AdminItemsConfig config) {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.DragonFangSettings settings = config.getDragonFang();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_SMOCZY_KIEL, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildGoldenSkull(AdminItemsConfig config) {
        String b64;
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta)item.getItemMeta();
        if (meta == null) {
            return item;
        }
        AdminItemsConfig.GoldenSkullSettings settings = config.getGoldenSkull();
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
        meta.setLore(settings.getLore().stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList());
        meta.getPersistentDataContainer().set(KEY_ZLOTA_CZASZKA, PersistentDataType.BYTE, (byte)1);
        if (settings.getCustomModelData() != 0) {
            meta.setCustomModelData(Integer.valueOf(settings.getCustomModelData()));
        }
        if ((b64 = settings.getTexture()) != null && !b64.isEmpty()) {
            try {
                String decoded = new String(Base64.getDecoder().decode(b64));
                String urlString = decoded.substring(decoded.indexOf("http://"), decoded.lastIndexOf("\""));
                PlayerProfile profile = Bukkit.createPlayerProfile((UUID)UUID.nameUUIDFromBytes(b64.getBytes()));
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(new URL(urlString));
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
            }
            catch (Exception exception) {
            }
        }
        item.setItemMeta((ItemMeta)meta);
        return item;
    }
}
