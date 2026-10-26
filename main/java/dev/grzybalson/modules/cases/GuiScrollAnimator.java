package dev.grzybalson.modules.cases;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Przewija jedną środkową linię (9 slotów) inventory — używane m.in. przy otwieraniu skrzynek.
 * Opóźnienia między krokami są w pełni konfigurowalne (tablica ticków).
 */
public final class GuiScrollAnimator {

    private GuiScrollAnimator() {}

    public static void run(JavaPlugin plugin, Inventory inv, ItemStack[] strip, int[] delays, int rows, Runnable onFinish) {
        run(plugin, inv, strip, delays, rows, null, onFinish);
    }

    /**
     * @param cancelled gdy {@code true}, animacja się zatrzymuje bez wywołania {@code onFinish}
     */
    public static void run(JavaPlugin plugin, Inventory inv, ItemStack[] strip, int[] delays, int rows,
                           AtomicBoolean cancelled, Runnable onFinish) {
        if (delays == null || delays.length == 0) {
            if (cancelled == null || !cancelled.get()) {
                onFinish.run();
            }
            return;
        }
        scheduleStep(plugin, inv, strip, delays, 0, rows, cancelled, onFinish);
    }

    private static void scheduleStep(JavaPlugin plugin, Inventory inv, ItemStack[] strip, int[] delays, int step, int rows,
                                     AtomicBoolean cancelled, Runnable onFinish) {
        if (cancelled != null && cancelled.get()) {
            return;
        }
        if (step >= delays.length) {
            if (cancelled == null || !cancelled.get()) {
                onFinish.run();
            }
            return;
        }
        int midRow = rows / 2;
        int startSlot = midRow * 9;
        if (inv != null) {
            for (int i = 0; i < 9; i++) {
                int stripIdx = step + i;
                ItemStack item = stripIdx < strip.length && strip[stripIdx] != null ? strip[stripIdx].clone() : new ItemStack(org.bukkit.Material.AIR);
                try {
                    inv.setItem(startSlot + i, item);
                } catch (Exception ignored) {
                }
            }
        }
        for (Player viewer : viewersOf(inv)) {
            float pitch = 1.6f - ((float) step / delays.length) * 0.8f;
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, pitch);
        }
        int delay = Math.max(1, delays[step]);
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () ->
            scheduleStep(plugin, inv, strip, delays, step + 1, rows, cancelled, onFinish), delay);
    }

    private static List<Player> viewersOf(Inventory inv) {
        List<Player> out = new ArrayList<>();
        if (inv == null) return out;
        for (var h : inv.getViewers()) {
            if (h instanceof Player p) out.add(p);
        }
        return out;
    }
}
