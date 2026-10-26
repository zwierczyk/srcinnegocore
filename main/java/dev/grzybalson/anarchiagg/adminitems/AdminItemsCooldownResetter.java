package dev.grzybalson.anarchiagg.adminitems;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredListener;
import dev.grzybalson.HollyCore;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminItemsCooldownResetter {

    private static final String ADMIN_ITEMS_LISTENER_PACKAGE = "dev.grzybalson.anarchiagg.adminitems.listener";
    private static final Map<Class<?>, List<Field>> COOLDOWN_FIELD_CACHE = new ConcurrentHashMap<>();

    private static final Set<Material> EVENT_ITEM_MATERIALS = Set.of(
            Material.BLAZE_ROD,
            Material.ARROW,
            Material.BONE,
            Material.CARROT,
            Material.CHAINMAIL_CHESTPLATE,
            Material.CHORUS_FRUIT,
            Material.ELYTRA,
            Material.FIREWORK_ROCKET,
            Material.FISHING_ROD,
            Material.FLOWER_POT,
            Material.GOLDEN_APPLE,
            Material.GOLDEN_PICKAXE,
            Material.HEART_OF_THE_SEA,
            Material.HONEY_BOTTLE,
            Material.JACK_O_LANTERN,
            Material.LEAD,
            Material.ORANGE_SHULKER_BOX,
            Material.PHANTOM_MEMBRANE,
            Material.PLAYER_HEAD,
            Material.SHULKER_SHELL,
            Material.SOUL_TORCH,
            Material.SPLASH_POTION,
            Material.STICK,
            Material.TRIDENT,
            Material.WEEPING_VINES
    );

    private AdminItemsCooldownResetter() {
    }

    public static void reset(Player player, HollyCore plugin) {
        if (player == null || plugin == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        resetStoredCooldowns(uuid, plugin);
        clearVisualCooldowns(player);
    }

    public static void clearVisualCooldowns(Player player) {
        if (player == null) {
            return;
        }

        AdminItemsCooldowns.clear(player);
        clearBukkitCooldowns(player);
    }

    private static void resetStoredCooldowns(UUID uuid, HollyCore plugin) {
        for (RegisteredListener registeredListener : HandlerList.getRegisteredListeners(plugin)) {
            Listener listener = registeredListener.getListener();
            Class<?> listenerClass = listener.getClass();
            String packageName = listenerClass.getPackageName();

            if (!packageName.startsWith(ADMIN_ITEMS_LISTENER_PACKAGE)
                && !packageName.startsWith("dev.grzybalson.anarchiagg.customitems")
                && !packageName.startsWith("dev.grzybalson.anarchiagg.adminitems")) {
                continue;
            }

            resetCooldownFields(listener, listenerClass, uuid);
        }
    }

    private static void resetCooldownFields(Listener listener, Class<?> listenerClass, UUID uuid) {
        for (Field field : COOLDOWN_FIELD_CACHE.computeIfAbsent(listenerClass, AdminItemsCooldownResetter::findCooldownFields)) {
            removeUuidFromField(listener, field, uuid);
        }
    }

    private static List<Field> findCooldownFields(Class<?> listenerClass) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = listenerClass;

        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (!field.getName().toLowerCase(Locale.ROOT).contains("cooldown")) {
                    continue;
                }

                Class<?> fieldType = field.getType();
                if (!Map.class.isAssignableFrom(fieldType) && !Set.class.isAssignableFrom(fieldType)) {
                    continue;
                }

                fields.add(field);
            }

            currentClass = currentClass.getSuperclass();
        }

        return List.copyOf(fields);
    }

    @SuppressWarnings("unchecked")
    private static void removeUuidFromField(Listener listener, Field field, UUID uuid) {
        Object target = Modifier.isStatic(field.getModifiers()) ? null : listener;
        boolean accessible = field.canAccess(target);

        try {
            field.setAccessible(true);
            Object value = field.get(target);

            if (value instanceof Map<?, ?> map) {
                ((Map<UUID, ?>) map).remove(uuid);
                return;
            }

            if (value instanceof Set<?> set) {
                ((Set<UUID>) set).remove(uuid);
            }
        } catch (IllegalAccessException | SecurityException ignored) {
        } finally {
            try {
                field.setAccessible(accessible);
            } catch (SecurityException ignored) {
            }
        }
    }

    private static void clearBukkitCooldowns(Player player) {
        for (Material material : EVENT_ITEM_MATERIALS) {
            player.setCooldown(material, 0);
        }
    }
}
