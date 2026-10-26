package dev.grzybalson.modules.backup;

import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BackupItemText {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private BackupItemText() {
    }

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    public static String displayName(ItemStack item) {
        if (isEmpty(item)) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component component = meta.displayName();
            if (component != null) {
                String plain = PLAIN.serialize(component).trim();
                if (!plain.isEmpty()) {
                    return plain;
                }
            }
            if (meta.hasDisplayName()) {
                String legacy = ColorUtils.stripColors(meta.getDisplayName());
                if (legacy != null && !legacy.isBlank()) {
                    return legacy.trim();
                }
            }
        }
        return prettifyMaterial(item.getType());
    }

    /** Nazwa gotowa do wyświetlenia poza grą — ozdobne czcionki zamienione na zwykły tekst. */
    public static String readableName(ItemStack item) {
        if (isEmpty(item)) {
            return "";
        }
        String readable = PlainText.toReadable(displayName(item));
        if (PlainText.letters(readable) < 2) {
            return prettifyMaterial(item.getType());
        }
        return readable;
    }

    /** Scala takie same przedmioty w jedną pozycję i sortuje: sprzęt, potem enchanty, potem reszta. */
    public static List<ItemSummary> summarize(ItemStack[] inventory, ItemStack[] armor) {
        List<ItemStack> items = new ArrayList<>();
        if (armor != null) {
            for (int i = armor.length - 1; i >= 0; i--) {
                items.add(armor[i]);
            }
        }
        if (inventory != null) {
            java.util.Collections.addAll(items, inventory);
        }

        Map<String, ItemSummary> merged = new java.util.LinkedHashMap<>();
        for (ItemStack item : items) {
            if (isEmpty(item)) {
                continue;
            }
            String name = readableName(item);
            List<String> tags = shortEnchantTags(item);
            String key = name.toLowerCase(Locale.ROOT) + "|" + String.join(",", tags);
            ItemSummary existing = merged.get(key);
            if (existing == null) {
                merged.put(key, new ItemSummary(name, item.getAmount(), tags, item, priority(item, tags)));
            } else {
                merged.put(key, new ItemSummary(
                    existing.name(),
                    existing.amount() + item.getAmount(),
                    existing.tags(),
                    existing.sample(),
                    existing.priority()
                ));
            }
        }

        List<ItemSummary> out = new ArrayList<>(merged.values());
        out.sort(java.util.Comparator.comparingInt(ItemSummary::priority)
            .thenComparing(summary -> -summary.amount())
            .thenComparing(ItemSummary::name));
        return out;
    }

    private static int priority(ItemStack item, List<String> tags) {
        String key = item.getType().getKey().getKey();
        boolean gear = key.endsWith("_helmet") || key.endsWith("_chestplate") || key.endsWith("_leggings")
            || key.endsWith("_boots") || key.endsWith("_sword") || key.endsWith("_axe") || key.endsWith("_pickaxe")
            || key.equals("bow") || key.equals("crossbow") || key.equals("trident") || key.equals("shield")
            || key.equals("elytra") || key.equals("totem_of_undying");
        if (gear) {
            return 0;
        }
        return tags.isEmpty() ? 2 : 1;
    }

    public static String materialName(ItemStack item) {
        if (isEmpty(item)) {
            return "";
        }
        return prettifyMaterial(item.getType());
    }

    public static List<String> extraLines(ItemStack item) {
        List<String> lines = new ArrayList<>();
        if (isEmpty(item)) {
            return lines;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return lines;
        }

        if (meta.hasEnchants()) {
            for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                lines.add(formatEnchant(entry.getKey(), entry.getValue()));
            }
        }
        if (meta instanceof EnchantmentStorageMeta stored && stored.hasStoredEnchants()) {
            for (Map.Entry<Enchantment, Integer> entry : stored.getStoredEnchants().entrySet()) {
                lines.add(formatEnchant(entry.getKey(), entry.getValue()));
            }
        }
        if (meta instanceof PotionMeta potion) {
            for (PotionEffect effect : potion.getCustomEffects()) {
                lines.add(prettifyKey(effect.getType().getKey().getKey()) + " " + (effect.getAmplifier() + 1));
            }
        }
        if (item.getAmount() > 1) {
            lines.add("x" + item.getAmount());
        }
        return lines;
    }

    public static int countItems(ItemStack[] inventory, ItemStack[] armor) {
        return countItems(inventory) + countItems(armor);
    }

    public static int countItems(ItemStack[] items) {
        if (items == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : items) {
            if (!isEmpty(item)) {
                count++;
            }
        }
        return count;
    }

    public static String prettifyMaterial(Material material) {
        if (material == null) {
            return "Pusty";
        }
        return prettifyKey(material.name());
    }

    public static String prettifyKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.toLowerCase(Locale.ROOT).replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    public static String shortEnchants(ItemStack item) {
        return String.join("  ", shortEnchantTags(item));
    }

    public static List<String> shortEnchantTags(ItemStack item) {
        List<String> parts = new ArrayList<>();
        if (isEmpty(item)) {
            return parts;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return parts;
        }
        Map<Enchantment, Integer> enchants = new java.util.LinkedHashMap<>();
        if (meta.hasEnchants()) {
            enchants.putAll(meta.getEnchants());
        }
        if (meta instanceof EnchantmentStorageMeta stored && stored.hasStoredEnchants()) {
            enchants.putAll(stored.getStoredEnchants());
        }
        enchants.entrySet().stream()
            .sorted((a, b) -> Integer.compare(enchantPriority(a.getKey()), enchantPriority(b.getKey())))
            .limit(2)
            .forEach(entry -> parts.add(enchantAbbr(entry.getKey()) + " " + entry.getValue()));
        return parts;
    }

    private static int enchantPriority(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        return switch (key) {
            case "sharpness", "smite", "efficiency", "power", "protection" -> 0;
            case "fortune", "looting", "silk_touch", "unbreaking" -> 1;
            default -> 2;
        };
    }

    private static String enchantAbbr(Enchantment enchantment) {
        return switch (enchantment.getKey().getKey()) {
            case "sharpness" -> "s";
            case "smite" -> "sm";
            case "bane_of_arthropods" -> "bane";
            case "efficiency" -> "eff";
            case "unbreaking" -> "unb";
            case "fortune" -> "for";
            case "silk_touch" -> "silk";
            case "protection" -> "prot";
            case "fire_protection" -> "fp";
            case "blast_protection" -> "bp";
            case "projectile_protection" -> "pp";
            case "feather_falling" -> "ff";
            case "thorns" -> "th";
            case "respiration" -> "resp";
            case "aqua_affinity" -> "aa";
            case "depth_strider" -> "ds";
            case "frost_walker" -> "fw";
            case "soul_speed" -> "ss";
            case "swift_sneak" -> "snk";
            case "mending" -> "men";
            case "infinity" -> "inf";
            case "power" -> "pow";
            case "punch" -> "pun";
            case "flame" -> "fl";
            case "looting" -> "loot";
            case "knockback" -> "kb";
            case "fire_aspect" -> "fa";
            case "sweeping" -> "sweep";
            case "loyalty" -> "loy";
            case "impaling" -> "imp";
            case "riptide" -> "rip";
            case "channeling" -> "ch";
            case "piercing" -> "pie";
            case "quick_charge" -> "qc";
            case "multishot" -> "ms";
            case "luck_of_the_sea" -> "luck";
            case "lure" -> "lure";
            default -> {
                String key = enchantment.getKey().getKey();
                yield key.length() <= 4 ? key : key.substring(0, 3);
            }
        };
    }

    private static String formatEnchant(Enchantment enchantment, int level) {
        String name = prettifyKey(enchantment.getKey().getKey());
        return level > 1 ? name + " " + toRoman(level) : name;
    }

    public record ItemSummary(String name, int amount, List<String> tags, ItemStack sample, int priority) {
    }

    private static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }
}
