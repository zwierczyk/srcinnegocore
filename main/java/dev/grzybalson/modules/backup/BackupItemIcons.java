package dev.grzybalson.modules.backup;

import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BackupItemIcons {

    private static final Map<String, BufferedImage> MEMORY = new ConcurrentHashMap<>();

    private BackupItemIcons() {
    }

    public static void prefetch(Iterable<ItemStack> items) {
        List<String> keys = new ArrayList<>();
        for (ItemStack item : items) {
            if (BackupItemText.isEmpty(item)) {
                continue;
            }
            String key = materialKey(item);
            if (!MEMORY.containsKey(key) && !keys.contains(key)) {
                keys.add(key);
            }
        }
        keys.parallelStream().forEach(key -> MEMORY.computeIfAbsent(key, BackupItemIcons::loadKey));
    }

    public static BufferedImage icon(ItemStack item) {
        if (BackupItemText.isEmpty(item)) {
            return null;
        }
        String key = materialKey(item);
        BufferedImage base = MEMORY.computeIfAbsent(key, BackupItemIcons::loadKey);
        return applyMeta(copy(base), item);
    }

    public static BufferedImage iconByKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return MEMORY.computeIfAbsent(key.toLowerCase(Locale.ROOT), BackupItemIcons::loadKey);
    }

    private static String materialKey(ItemStack item) {
        return item.getType().getKey().getKey().toLowerCase(Locale.ROOT);
    }

    private static BufferedImage loadKey(String key) {
        for (String candidate : candidates(key)) {
            BufferedImage image = readResource("backup-icons/item/" + candidate + ".png");
            if (image == null) {
                image = readResource("backup-icons/block/" + candidate + ".png");
            }
            if (image == null) {
                image = readResource("backup-icons/block/" + candidate + "_side.png");
            }
            if (image == null) {
                image = readResource("backup-icons/block/" + candidate + "_top.png");
            }
            if (image != null) {
                return image;
            }
        }
        BufferedImage fallback = readResource("backup-icons/item/stick.png");
        if (fallback == null) {
            fallback = readResource("backup-icons/block/stone.png");
        }
        return fallback != null ? fallback : blank();
    }

    private static List<String> candidates(String key) {
        Set<String> out = new LinkedHashSet<>();
        out.add(key);
        if ("tripwire_hook".equals(key)) {
            out.add("lever");
            out.add("tripwire");
            out.add("string");
        }
        addSuffixSwap(out, key, "_stairs", "_planks");
        addSuffixSwap(out, key, "_stairs", "_mosaic");
        addSuffixSwap(out, key, "_slab", "_planks");
        addSuffixSwap(out, key, "_fence_gate", "_planks");
        addSuffixSwap(out, key, "_fence", "_planks");
        addSuffixSwap(out, key, "_button", "_planks");
        addSuffixSwap(out, key, "_pressure_plate", "_planks");
        addSuffixSwap(out, key, "_sign", "_planks");
        addSuffixSwap(out, key, "_hanging_sign", "_planks");
        addSuffixSwap(out, key, "_carpet", "_wool");
        addSuffixSwap(out, key, "_bed", "_wool");
        addSuffixSwap(out, key, "_banner", "_wool");
        addSuffixSwap(out, key, "_wall", "");
        addSuffixSwap(out, key, "_door", "_door_bottom");
        if (key.endsWith("_head")) {
            out.add("player_head");
        }
        if (key.endsWith("_skull")) {
            out.add("skeleton_skull");
        }
        return new ArrayList<>(out);
    }

    private static void addSuffixSwap(Set<String> out, String key, String suffix, String replacement) {
        if (key.endsWith(suffix)) {
            out.add(key.substring(0, key.length() - suffix.length()) + replacement);
        }
    }

    private static BufferedImage readResource(String path) {
        try (InputStream input = BackupItemIcons.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                return null;
            }
            return ImageIO.read(input);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BufferedImage applyMeta(BufferedImage image, ItemStack item) {
        if (image == null) {
            return blank();
        }
        if (item.getItemMeta() instanceof LeatherArmorMeta leather) {
            Color color = leather.getColor();
            tint(image, color.getRed(), color.getGreen(), color.getBlue());
        } else if (item.getItemMeta() instanceof PotionMeta potion) {
            try {
                Color color = potion.getColor();
                if (color != null) {
                    tint(image, color.getRed(), color.getGreen(), color.getBlue());
                }
            } catch (Throwable ignored) {
            }
        }
        return image;
    }

    private static void tint(BufferedImage image, int r, int g, int b) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                int pr = (argb >> 16) & 0xFF;
                int pg = (argb >> 8) & 0xFF;
                int pb = argb & 0xFF;
                image.setRGB(x, y, (a << 24) | ((pr * r / 255) << 16) | ((pg * g / 255) << 8) | (pb * b / 255));
            }
        }
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(source, 0, 0, null);
        return copy;
    }

    private static BufferedImage blank() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }
}
