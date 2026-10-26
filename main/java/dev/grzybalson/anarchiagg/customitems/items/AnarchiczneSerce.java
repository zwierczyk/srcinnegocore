package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.PlayerHealthBase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AnarchiczneSerce implements Listener {

    private static final String PDC_EXTRA_HEARTS_KEY = "anarchic_extra_hearts";

    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int cooldownSeconds;

    public AnarchiczneSerce(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;

        FileConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        List<String> regionList = config.getStringList("blocked-regions");
        if (regionList.isEmpty() && !config.isSet("blocked-regions")) {
            config.set("blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }

        cooldownSeconds = config.getInt("eventowki.anarchiczeneserce.cooldown", 0);
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;
        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) {
                return true;
            }
        }
        return false;
    }

    public static int getExtraHearts(JavaPlugin plugin, Player player) {
        if (player == null) return 0;
        NamespacedKey key = new NamespacedKey(plugin, PDC_EXTRA_HEARTS_KEY);
        Integer val = player.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return val == null ? 0 : Math.max(0, Math.min(10, val));
    }

    public static void setExtraHearts(JavaPlugin plugin, Player player, int count) {
        if (player == null) return;
        NamespacedKey key = new NamespacedKey(plugin, PDC_EXTRA_HEARTS_KEY);
        if (count <= 0) {
            player.getPersistentDataContainer().remove(key);
        } else {
            player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, Math.min(10, count));
        }
    }

    public static ItemStack createDefaultItem(JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize("&4&lAnarchiczne serce"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtils.colorize(" &8» &7To &cserce &7możesz użyć poza limitem &c30 serc&7."));
            lore.add(ColorUtils.colorize(" &8» &7Maksymalnie możesz uzyskać &f40 serduszek&7!"));
            lore.add(ColorUtils.colorize("&7"));
            meta.setLore(lore);
            meta.setCustomModelData(4);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (itemInHand.getType() == Material.AIR) return;
        if (!EventItemRegistry.matches(plugin, "anarchiczeneserce", itemInHand)) return;
        if (!EventItemUseGuard.tryUse(plugin, player)) return;

        event.setCancelled(true);

        if (EventItemUseGuard.hasItemCooldown(plugin, player, "anarchiczeneserce", itemInHand, cooldownSeconds)) {
            return;
        }

        int currentExtra = getExtraHearts(plugin, player);
        if (currentExtra >= 10) {
            ColorUtils.sendTitle(player, "", "§cMaksymalnie możesz uzyskać 40 serduszek!", 0, 50, 10);
            return;
        }

        int newExtra = currentExtra + 1;
        setExtraHearts(plugin, player, newExtra);

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null && plugin instanceof HollyCore holly) {
            PlayerHealthBase.resetBaseMaxHealth(holly, player, false);
            maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttr != null) {
                player.setHealth(Math.min(player.getHealth() + 2.0D, maxHealthAttr.getValue()));
            }
        } else if (maxHealthAttr != null) {
            double targetMaxHealth = 60.0D + (newExtra * 2.0D);
            maxHealthAttr.setBaseValue(targetMaxHealth);
            player.setHealth(Math.min(player.getHealth() + 2.0D, targetMaxHealth));
        }

        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        EventItemUseGuard.applyItemCooldown(plugin, player, "anarchiczeneserce", itemInHand, cooldownSeconds);
        ColorUtils.sendTitle(player, "", "§7Użyłeś §cAnarchicznego serca§7! (§f" + (30 + newExtra) + " serduszek§7)", 0, 50, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        int currentExtra = getExtraHearts(plugin, victim);
        if (currentExtra > 0) {
            ItemStack drop = EventItemRegistry.getTemplateFromGui(plugin, "anarchiczeneserce");
            if (drop == null || drop.getType() == Material.AIR) {
                drop = createDefaultItem(plugin);
            }
            event.getDrops().add(drop.clone());
            setExtraHearts(plugin, victim, currentExtra - 1);
        }
    }
}
