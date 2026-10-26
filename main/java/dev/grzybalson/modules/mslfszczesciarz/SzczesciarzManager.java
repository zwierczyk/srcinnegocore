package dev.grzybalson.modules.mslfszczesciarz;

import dev.grzybalson.anarchiagg.zmianki.ZmiankiManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Szczęściarz: roll -10%..60%.
 * Głównie niskie/środkowe; 30–36 ciężkie; 37+ ekstremalnie rzadkie.
 */
public final class SzczesciarzManager extends ZmiankiManager {

    public static final int MIN_BONUS = -10;
    public static final int MAX_BONUS = 60;

    public SzczesciarzManager(JavaPlugin plugin, File configFile) {
        super(plugin, configFile, "mslf_szczesciarz");
    }

    @Override
    public int clampBonus(int bonus) {
        return Math.max(MIN_BONUS, Math.min(MAX_BONUS, bonus));
    }

    @Override
    public int rollBonus() {
        int totalWeight = 0;
        int span = MAX_BONUS - MIN_BONUS + 1;
        int[] weights = new int[span];
        for (int i = 0; i < span; i++) {
            int bonus = MIN_BONUS + i;
            int weight = weightFor(bonus);
            weights[i] = weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0) {
            return 22;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (int i = 0; i < span; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return MIN_BONUS + i;
            }
        }
        return 22;
    }

    /**
     * Bardzo twarde losowanie:
     * - masa w 16–26 (szczyt ~20–23)
     * - 30–36 rzadkie / ciężkie
     * - 37+ prawie nie do wylosowania
     */
    private static int weightFor(int bonus) {
        if (bonus >= 51) {
            return 0; // praktycznie wyłączone
        }
        if (bonus >= 43) {
            return 1; // ~raz na setki
        }
        if (bonus >= 37) {
            return 2;
        }
        if (bonus >= 30 && bonus <= 36) {
            // sweet spot — ciężki
            return switch (bonus) {
                case 30, 31 -> 8;
                case 32, 33 -> 6;
                case 34, 35 -> 4;
                default -> 3; // 36
            };
        }
        if (bonus >= 27 && bonus <= 29) {
            return 12 - (bonus - 27) * 2; // 12, 10, 8
        }
        if (bonus >= 20 && bonus <= 26) {
            // najczęstsze, ale bez dominacji 40+
            return switch (bonus) {
                case 20, 21 -> 55;
                case 22, 23 -> 60;
                case 24 -> 48;
                case 25 -> 36;
                default -> 28; // 26
            };
        }
        if (bonus >= 14 && bonus <= 19) {
            return 20 + (bonus - 14); // 20 → 25
        }
        if (bonus >= 5 && bonus <= 13) {
            return 14;
        }
        if (bonus >= 0 && bonus <= 4) {
            return 9;
        }
        // -10 .. -1
        return Math.max(4, 7 + bonus / 3);
    }

    @Override
    public String formatDamageLoreColor(int bonus) {
        return "&#B70000⚠ &8Ξ &#E05454Zwiększone obrażenia o&f ";
    }

    @Override
    protected List<String> buildDamageLoreTail(int bonus) {
        return List.of(
            color(""),
            color(formatDamageLoreColor(bonus) + bonus + "%")
        );
    }
}
