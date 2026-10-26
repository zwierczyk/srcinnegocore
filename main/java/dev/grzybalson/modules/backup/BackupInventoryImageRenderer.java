package dev.grzybalson.modules.backup;

import org.bukkit.inventory.ItemStack;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class BackupInventoryImageRenderer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault());

    private static final Color BACKDROP = new Color(0x1A1A1A);
    private static final Color PANEL = new Color(0xC6C6C6);
    private static final Color PANEL_DARK = new Color(0x8B8B8B);
    private static final Color SLOT_FILL = new Color(0x8B8B8B);
    private static final Color SLOT_SHADOW = new Color(0x373737);
    private static final Color SLOT_LIGHT = new Color(0xFFFFFF);
    private static final Color LABEL = new Color(0x3F3F3F);
    private static final Color WHITE = new Color(0xFFFFFF);
    private static final Color GRAY = new Color(0xAAAAAA);
    private static final Color PURPLE = new Color(0xB48CFF);
    private static final Color YELLOW = new Color(0xFFFF55);
    private static final Color TOOLTIP_BG = new Color(0x10, 0x00, 0x10, 0xF0);
    private static final Color TOOLTIP_BORDER_TOP = new Color(0x5000FF);
    private static final Color TOOLTIP_BORDER_BOTTOM = new Color(0x28007F);

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 400;
    private static final int SLOT = 40;
    private static final int STEP = 44;
    private static final int ICON = 32;

    private static final int LIST_COLUMNS = 3;
    private static final int LIST_ROWS = 5;

    private BackupInventoryImageRenderer() {
    }

    public static byte[] renderPng(BackupDataManager.DeathRecord death) throws IOException {
        prefetchIcons(death);

        Info info = new Info(
            death.playerName(),
            death.publicId(),
            TIME.format(death.createdAt()),
            value(death.killerName()),
            value(death.deathCause()),
            death.preDeathElo(),
            BackupItemText.countItems(death.inventory(), death.armor())
        );
        SlotView offhand = view(
            death.inventory() != null && death.inventory().length > 40 ? death.inventory()[40] : null,
            BackupItemIcons.iconByKey("shield")
        );
        return renderCanvas(
            info,
            armorSlots(death),
            offhand,
            inventorySlots(death, 9, 36),
            inventorySlots(death, 0, 9),
            entries(death)
        );
    }

    static byte[] renderCanvas(
        Info info,
        List<SlotView> armor,
        SlotView offhand,
        List<SlotView> main,
        List<SlotView> hotbar,
        List<Entry> entries
    ) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(BACKDROP);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            drawMcPanel(g, 12, 12, WIDTH - 24, HEIGHT - 24);
            McFont.draw(g, "Ekwipunek", 30, 28, 2, LABEL, false);

            int top = 56;
            int armorX = 30;
            for (int i = 0; i < 4; i++) {
                drawSlot(g, armorX, top + i * STEP, armor.get(i));
            }
            drawSlot(g, armorX + STEP, top + 3 * STEP, offhand);

            int gridX = armorX + STEP * 2 + 16;
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    drawSlot(g, gridX + column * STEP, top + row * STEP, main.get(row * 9 + column));
                }
            }
            int hotbarY = top + 3 * STEP + 12;
            for (int column = 0; column < 9; column++) {
                drawSlot(g, gridX + column * STEP, hotbarY, hotbar.get(column));
            }

            int infoX = gridX + 9 * STEP + 20;
            drawInfo(g, info, infoX, top, WIDTH - 28 - infoX, hotbarY + SLOT - top);

            drawItemList(g, entries, 28, hotbarY + SLOT + 14, WIDTH - 56, HEIGHT - 24 - (hotbarY + SLOT + 14));
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void drawMcPanel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(PANEL);
        g.fillRect(x, y, w, h);
        g.setColor(SLOT_LIGHT);
        g.fillRect(x, y, w, 3);
        g.fillRect(x, y, 3, h);
        g.setColor(SLOT_SHADOW);
        g.fillRect(x, y + h - 3, w, 3);
        g.fillRect(x + w - 3, y, 3, h);
        g.setColor(PANEL_DARK);
        g.drawRect(x + 3, y + 3, w - 7, h - 7);
    }

    private static void drawSlot(Graphics2D g, int x, int y, SlotView slot) {
        g.setColor(SLOT_SHADOW);
        g.fillRect(x, y, SLOT, 2);
        g.fillRect(x, y, 2, SLOT);
        g.setColor(SLOT_LIGHT);
        g.fillRect(x, y + SLOT - 2, SLOT, 2);
        g.fillRect(x + SLOT - 2, y, 2, SLOT);
        g.setColor(SLOT_FILL);
        g.fillRect(x + 2, y + 2, SLOT - 4, SLOT - 4);

        if (slot == null) {
            return;
        }
        if (slot.empty) {
            if (slot.placeholder != null) {
                Composite old = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
                drawPixels(g, slot.placeholder, x + (SLOT - ICON) / 2, y + (SLOT - ICON) / 2, ICON, ICON);
                g.setComposite(old);
            }
            return;
        }

        if (slot.icon != null) {
            drawPixels(g, slot.icon, x + (SLOT - ICON) / 2, y + (SLOT - ICON) / 2, ICON, ICON);
        }
        if (slot.enchanted) {
            drawGlint(g, x + 3, y + 3, SLOT - 6, SLOT - 6);
        }
        if (slot.amount > 1) {
            String amount = String.valueOf(slot.amount);
            int width = McFont.width(amount, 2);
            McFont.draw(g, amount, x + SLOT - 3 - width, y + SLOT - 3 - McFont.height(2), 2, WHITE, true);
        }
    }

    private static void drawGlint(Graphics2D g, int x, int y, int w, int h) {
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
        g.setColor(PURPLE);
        for (int offset = -h; offset < w; offset += 8) {
            g.fillPolygon(
                new int[]{x + offset, x + offset + 3, x + offset + 3 + h, x + offset + h},
                new int[]{y + h, y + h, y, y},
                4
            );
        }
        g.setComposite(old);
    }

    private static void drawInfo(Graphics2D g, Info info, int x, int y, int w, int h) {
        drawTooltip(g, x, y, w, h);
        int textX = x + 12;
        int textY = y + 12;
        int line = McFont.height(2) + 6;
        int maxWidth = w - 24;

        McFont.draw(g, McFont.fit(info.playerName(), 2, maxWidth), textX, textY, 2, WHITE, true);
        textY += line + 2;

        String[][] rows = {
            {"Backup", info.publicId()},
            {"Data", info.time()},
            {"Zabojca", info.killer()},
            {"Przyczyna", info.cause()},
            {"ELO", String.valueOf(info.elo())},
            {"Itemy", String.valueOf(info.itemCount())}
        };
        for (String[] row : rows) {
            if (textY + McFont.height(2) > y + h - 8) {
                break;
            }
            int cursor = McFont.draw(g, row[0] + ": ", textX, textY, 2, GRAY, true);
            McFont.draw(g, McFont.fit(row[1], 2, maxWidth - (cursor - textX)), cursor, textY, 2, WHITE, true);
            textY += line;
        }
    }

    private static void drawItemList(Graphics2D g, List<Entry> entries, int x, int y, int w, int h) {
        drawTooltip(g, x, y, w, h);
        int padding = 12;
        int line = 22;
        int columnWidth = (w - padding * 2) / LIST_COLUMNS;
        int capacity = LIST_COLUMNS * LIST_ROWS;
        int shown = entries.size() > capacity ? capacity - 1 : entries.size();

        for (int i = 0; i < shown; i++) {
            int column = i / LIST_ROWS;
            int row = i % LIST_ROWS;
            int cellX = x + padding + column * columnWidth;
            int cellY = y + padding + row * line;
            drawEntry(g, entries.get(i), cellX, cellY, columnWidth - 10);
        }

        if (entries.size() > shown) {
            int index = shown;
            int cellX = x + padding + (index / LIST_ROWS) * columnWidth;
            int cellY = y + padding + (index % LIST_ROWS) * line;
            McFont.draw(g, "+" + (entries.size() - shown) + " wiecej", cellX + 22, cellY + 2, 2, GRAY, true);
        }
        if (entries.isEmpty()) {
            McFont.draw(g, "Brak przedmiotow", x + padding, y + padding, 2, GRAY, true);
        }
    }

    private static void drawEntry(Graphics2D g, Entry entry, int x, int y, int maxWidth) {
        if (entry.icon != null) {
            drawPixels(g, entry.icon, x, y, 18, 18);
        }
        int textX = x + 22;
        int available = maxWidth - 22;

        String tags = String.join(" ", compactTags(entry.tags));
        int tagsWidth = tags.isEmpty() ? 0 : McFont.width(" " + tags, 2);
        String amount = entry.amount > 1 ? " x" + entry.amount : "";
        int amountWidth = McFont.width(amount, 2);

        String name = McFont.fit(entry.name, 2, Math.max(0, available - tagsWidth - amountWidth));
        int cursor = McFont.draw(g, name, textX, y + 2, 2, WHITE, true);
        if (!amount.isEmpty()) {
            cursor = McFont.draw(g, amount, cursor, y + 2, 2, GRAY, true);
        }
        if (!tags.isEmpty()) {
            McFont.draw(g, " " + tags, cursor, y + 2, 2, PURPLE, true);
        }
    }

    private static List<String> compactTags(List<String> tags) {
        List<String> out = new ArrayList<>();
        for (String tag : tags) {
            out.add(tag.replace(" ", ""));
        }
        return out;
    }

    private static void drawTooltip(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(TOOLTIP_BG);
        g.fillRect(x + 2, y, w - 4, h);
        g.fillRect(x, y + 2, w, h - 4);
        for (int i = 0; i < h - 4; i++) {
            float ratio = (h - 4) <= 1 ? 0f : (float) i / (h - 5);
            g.setColor(blend(TOOLTIP_BORDER_TOP, TOOLTIP_BORDER_BOTTOM, ratio));
            g.fillRect(x + 2, y + 2 + i, 2, 1);
            g.fillRect(x + w - 4, y + 2 + i, 2, 1);
        }
        g.setColor(TOOLTIP_BORDER_TOP);
        g.fillRect(x + 4, y + 2, w - 8, 2);
        g.setColor(TOOLTIP_BORDER_BOTTOM);
        g.fillRect(x + 4, y + h - 4, w - 8, 2);
    }

    private static Color blend(Color from, Color to, float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        return new Color(
            Math.round(from.getRed() + (to.getRed() - from.getRed()) * clamped),
            Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * clamped),
            Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * clamped)
        );
    }

    private static void drawPixels(Graphics2D g, BufferedImage icon, int x, int y, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(icon, x, y, w, h, null);
    }

    private static List<Entry> entries(BackupDataManager.DeathRecord death) {
        List<Entry> out = new ArrayList<>();
        for (BackupItemText.ItemSummary summary : BackupItemText.summarize(death.inventory(), death.armor())) {
            out.add(new Entry(
                summary.name(),
                summary.amount(),
                summary.tags(),
                BackupItemIcons.icon(summary.sample()),
                summary.priority()
            ));
        }
        return out;
    }

    private static void prefetchIcons(BackupDataManager.DeathRecord death) {
        List<ItemStack> items = new ArrayList<>();
        if (death.inventory() != null) {
            java.util.Collections.addAll(items, death.inventory());
        }
        if (death.armor() != null) {
            java.util.Collections.addAll(items, death.armor());
        }
        BackupItemIcons.prefetch(items);
        BackupItemIcons.iconByKey("leather_helmet");
        BackupItemIcons.iconByKey("leather_chestplate");
        BackupItemIcons.iconByKey("leather_leggings");
        BackupItemIcons.iconByKey("leather_boots");
        BackupItemIcons.iconByKey("shield");
    }

    private static List<SlotView> armorSlots(BackupDataManager.DeathRecord death) {
        ItemStack[] armor = death.armor() != null ? death.armor() : new ItemStack[0];
        List<SlotView> slots = new ArrayList<>();
        slots.add(view(armor.length > 3 ? armor[3] : null, BackupItemIcons.iconByKey("leather_helmet")));
        slots.add(view(armor.length > 2 ? armor[2] : null, BackupItemIcons.iconByKey("leather_chestplate")));
        slots.add(view(armor.length > 1 ? armor[1] : null, BackupItemIcons.iconByKey("leather_leggings")));
        slots.add(view(armor.length > 0 ? armor[0] : null, BackupItemIcons.iconByKey("leather_boots")));
        return slots;
    }

    private static List<SlotView> inventorySlots(BackupDataManager.DeathRecord death, int from, int to) {
        ItemStack[] inventory = death.inventory() != null ? death.inventory() : new ItemStack[0];
        List<SlotView> slots = new ArrayList<>();
        for (int i = from; i < to; i++) {
            slots.add(view(i < inventory.length ? inventory[i] : null, null));
        }
        return slots;
    }

    private static SlotView view(ItemStack item, BufferedImage placeholder) {
        if (BackupItemText.isEmpty(item)) {
            return new SlotView(true, false, 0, List.of(), null, placeholder);
        }
        boolean enchanted = item.getItemMeta() != null && (item.getItemMeta().hasEnchants()
            || (item.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta stored && stored.hasStoredEnchants()));
        List<String> tags = compactTags(BackupItemText.shortEnchantTags(item));
        return new SlotView(false, enchanted, item.getAmount(), tags, BackupItemIcons.icon(item), placeholder);
    }

    private static String value(String raw) {
        return raw == null || raw.isBlank() ? "Brak" : raw;
    }

    record Info(
        String playerName,
        String publicId,
        String time,
        String killer,
        String cause,
        int elo,
        int itemCount
    ) {
    }

    record SlotView(
        boolean empty,
        boolean enchanted,
        int amount,
        List<String> tags,
        BufferedImage icon,
        BufferedImage placeholder
    ) {
    }

    record Entry(String name, int amount, List<String> tags, BufferedImage icon, int priority) {
    }
}
