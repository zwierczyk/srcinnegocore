package dev.grzybalson.modules.tops;

import dev.grzybalson.utils.ArmorStarsUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EquipmentTopTracker {

    private final Map<UUID, Double> maxSword = new ConcurrentHashMap<>();
    private final Map<UUID, Double> maxArmor = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();

    public void update(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        double sword = ArmorStarsUtil.getSwordLevel(ArmorStarsUtil.findBestWeapon(inventory));
        double armor = ArmorStarsUtil.getArmorLevel(inventory);
        UUID uuid = player.getUniqueId();
        names.put(uuid, player.getName());
        maxSword.merge(uuid, sword, Math::max);
        maxArmor.merge(uuid, armor, Math::max);
    }

    public void remove(UUID uuid) {
        if (uuid == null) {
            return;
        }
        maxSword.remove(uuid);
        maxArmor.remove(uuid);
        names.remove(uuid);
    }

    public List<TopsManager.TopEntry> topSword(int limit) {
        return buildTop(maxSword, limit);
    }

    public List<TopsManager.TopEntry> topArmor(int limit) {
        return buildTop(maxArmor, limit);
    }

    private List<TopsManager.TopEntry> buildTop(Map<UUID, Double> source, int limit) {
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(source.entrySet());
        sorted.sort(Comparator.comparingDouble((Map.Entry<UUID, Double> e) -> e.getValue()).reversed());
        List<TopsManager.TopEntry> result = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : sorted) {
            if (entry.getValue() == null || entry.getValue() <= 0.0D) {
                continue;
            }
            String name = names.get(entry.getKey());
            if (name == null || name.isBlank()) {
                continue;
            }
            result.add(new TopsManager.TopEntry(name, entry.getValue(), entry.getKey().toString()));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    public static String normalizeType(String raw) {
        if (raw == null) {
            return "";
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "sword", "miecz" -> "SWORD";
            case "zbroja", "armor", "armour" -> "ZBROJA";
            default -> raw.toUpperCase(Locale.ROOT);
        };
    }

    public void scanOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }
}
