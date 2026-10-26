package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Lizak extends BukkitRunnable implements Listener {
    private final JavaPlugin plugin;
    private final String lizakName;
    public final Material lizakMaterial;

    private static Attribute cachedScaleAttribute;
    private static boolean scaleAttributeResolved = false;
    private static final Map<UUID, Double> ACTIVE_SCALES = new ConcurrentHashMap<>();

    public Lizak(JavaPlugin plugin) {
        this.plugin = plugin;

        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);

        String itemMat = eventowkiConfig.getString("eventowki.lizak.item", "ALLIUM");
        lizakName = eventowkiConfig.getString("eventowki.lizak.name", "&d&lLizak").replace("&", "§");
        lizakMaterial = Material.valueOf(itemMat.toUpperCase(Locale.ROOT));
    }

    private boolean isLizak(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return EventItemRegistry.matches(plugin, "lizak", item);
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean holding = isLizak(player.getInventory().getItemInMainHand()) || isLizak(player.getInventory().getItemInOffHand());
            double targetScale = holding ? 0.8D : 1.0D;
            UUID uuid = player.getUniqueId();

            Double currentScale = ACTIVE_SCALES.get(uuid);
            if (currentScale == null || Double.compare(currentScale, targetScale) != 0) {
                applyScale(player, targetScale);
                if (holding) {
                    ACTIVE_SCALES.put(uuid, targetScale);
                } else {
                    ACTIVE_SCALES.remove(uuid);
                }
            }

            if (holding) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 100, 0, false, false));
            }
        }
    }

    private static void applyScale(Player player, double scale) {
        if (player == null || !player.isOnline()) return;

        // 1. Try Vanilla / Paper Attribute (1.20.5+ / Paper)
        Attribute scaleAttr = resolveScaleAttribute();
        if (scaleAttr != null) {
            AttributeInstance instance = player.getAttribute(scaleAttr);
            if (instance != null) {
                instance.setBaseValue(scale);
            }
        }

        // 2. Try Pehkui Mod/Plugin API if present
        try {
            Class<?> scaleTypesClass = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            Field baseField = scaleTypesClass.getField("BASE");
            Object baseType = baseField.get(null);
            Method getScaleDataMethod = baseType.getClass().getMethod("getScaleData", org.bukkit.entity.Entity.class);
            Object scaleData = getScaleDataMethod.invoke(baseType, player);
            Method setTargetScaleMethod = scaleData.getClass().getMethod("setTargetScale", float.class);
            setTargetScaleMethod.invoke(scaleData, (float) scale);
        } catch (Throwable ignored) {
        }
    }

    private static Attribute resolveScaleAttribute() {
        if (scaleAttributeResolved) {
            return cachedScaleAttribute;
        }
        scaleAttributeResolved = true;
        for (String candidate : new String[]{"GENERIC_SCALE", "SCALE"}) {
            try {
                cachedScaleAttribute = Attribute.valueOf(candidate);
                return cachedScaleAttribute;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (ACTIVE_SCALES.remove(uuid) != null) {
            applyScale(player, 1.0D);
        }
    }
}
