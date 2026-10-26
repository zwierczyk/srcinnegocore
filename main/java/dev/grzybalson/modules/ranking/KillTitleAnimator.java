package dev.grzybalson.modules.ranking;

import dev.grzybalson.config.modules.clans.EloSettingsConfig;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.SmallCaps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Animowany title po zabójstwie/śmierci — small caps, fala koloru od środka,
 * błysk na krawędzi i delikatny pulse po pełnym odkryciu.
 */
public final class KillTitleAnimator {

    private static final Map<UUID, BukkitTask> ACTIVE = new HashMap<>();
    private static final TextColor FLASH = TextColor.color(0xFFFFFF);

    private KillTitleAnimator() {
    }

    public static void playKill(JavaPlugin plugin, EloSettingsConfig config, Player player,
                                List<String> subtitleParts) {
        play(plugin, config, player, config.getKillAnimationTitle(), config.getKillAnimationTitleCenterIndex(),
            subtitleParts,
            config.getKillAnimationTitleGray(), config.getKillAnimationTitleColorStart(), config.getKillAnimationTitleColorEnd(),
            config.getKillAnimationSoundMain(), config.getKillAnimationSoundExtra(),
            config.getKillAnimationSoundVolume(), config.getKillAnimationSoundMainPitch(), config.getKillAnimationSoundExtraPitch());
    }

    public static void playDeath(JavaPlugin plugin, EloSettingsConfig config, Player player,
                                 List<String> subtitleParts) {
        play(plugin, config, player, config.getDeathAnimationTitle(), config.getDeathAnimationTitleCenterIndex(),
            subtitleParts,
            config.getDeathAnimationTitleGray(), config.getDeathAnimationTitleColorStart(), config.getDeathAnimationTitleColorEnd(),
            config.getDeathAnimationSoundMain(), config.getDeathAnimationSoundExtra(),
            config.getDeathAnimationSoundVolume(), config.getDeathAnimationSoundMainPitch(), config.getDeathAnimationSoundExtraPitch());
    }

    public static void shutdown() {
        for (BukkitTask task : ACTIVE.values()) {
            task.cancel();
        }
        ACTIVE.clear();
    }

    private static void play(JavaPlugin plugin, EloSettingsConfig config, Player player,
                             String titleText, int centerIndex, List<String> subtitleParts,
                             String grayHex, String colorStartHex, String colorEndHex,
                             String mainSoundName, String extraSoundName,
                             float volume, float mainPitch, float extraPitch) {
        cancelAnimation(player.getUniqueId());
        playSounds(player, mainSoundName, extraSoundName, volume, mainPitch, extraPitch);

        String displayTitle = SmallCaps.convert(titleText);
        TextColor gray = parseColor(grayHex, 0x8A8A8A);
        TextColor start = parseColor(colorStartHex, 0xFFB347);
        TextColor end = parseColor(colorEndHex, 0xFF2200);

        int length = displayTitle.length();
        int safeCenter = Math.max(0, Math.min(centerIndex, Math.max(0, length - 1)));
        int maxSpread = Math.max(safeCenter, length - 1 - safeCenter);
        int spreadEvery = Math.max(1, config.getKillAnimationSpreadEveryTicks());
        int subtitleDelay = Math.max(0, config.getKillAnimationSubtitleDelayTicks());
        int partEvery = Math.max(1, config.getKillAnimationSubtitlePartEveryTicks());
        int holdTicks = Math.max(20, config.getKillAnimationHoldTicks());

        int subtitleStartTick = subtitleDelay;
        int subtitleEndTick = subtitleStartTick + subtitleParts.size() * partEvery;
        int endTick = Math.max(subtitleEndTick, (maxSpread + 1) * spreadEvery) + holdTicks;

        BukkitRunnable runnable = new BukkitRunnable() {
            int tick = 0;
            int lastSpread = -1;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelAnimation(player.getUniqueId());
                    this.cancel();
                    return;
                }

                int spread = Math.min(maxSpread, tick / spreadEvery);
                boolean fullyRevealed = spread >= maxSpread;
                float pulse = fullyRevealed
                    ? 0.78f + 0.22f * (float) ((Math.sin(tick * 0.28) + 1.0) / 2.0)
                    : 1f;

                Component title = buildSpreadTitle(
                    displayTitle, safeCenter, spread, fullyRevealed, pulse, gray, start, end
                );

                int visibleParts = 0;
                if (tick >= subtitleStartTick) {
                    visibleParts = Math.min(subtitleParts.size(),
                        1 + (tick - subtitleStartTick) / partEvery);
                }
                Component subtitle = Component.empty();
                for (int i = 0; i < visibleParts; i++) {
                    subtitle = subtitle.append(ColorUtils.colorizeComponent(subtitleParts.get(i)));
                }

                player.showTitle(Title.title(
                    title,
                    subtitle,
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(350), Duration.ZERO)
                ));

                if (spread != lastSpread) {
                    float pitch = 0.85f + spread * 0.12f;
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, pitch);
                    lastSpread = spread;
                }

                tick++;
                if (tick > endTick) {
                    cancelAnimation(player.getUniqueId());
                    this.cancel();
                }
            }
        };

        BukkitTask task = runnable.runTaskTimer(plugin, 0L, 1L);
        ACTIVE.put(player.getUniqueId(), task);
    }

    private static Component buildSpreadTitle(String text, int centerIndex, int spreadRadius,
                                              boolean fullyRevealed, float pulse,
                                              TextColor gray, TextColor start, TextColor end) {
        Component result = Component.empty();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char ch = text.charAt(i);
            int distance = Math.abs(i - centerIndex);
            TextColor color;
            if (distance > spreadRadius) {
                color = gray;
            } else if (!fullyRevealed && distance == spreadRadius) {
                color = FLASH;
            } else {
                float t = length <= 1 ? 0f : (float) i / (length - 1);
                color = brighten(lerp(start, end, t), pulse);
            }
            result = result.append(
                Component.text(String.valueOf(ch))
                    .color(color)
                    .decorate(TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false)
            );
        }
        return result;
    }

    private static TextColor parseColor(String hex, int fallback) {
        TextColor parsed = hex == null ? null : TextColor.fromHexString(hex);
        return parsed != null ? parsed : TextColor.color(fallback);
    }

    private static TextColor lerp(TextColor from, TextColor to, float t) {
        Color a = new Color(from.value());
        Color b = new Color(to.value());
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return TextColor.color(r, g, bl);
    }

    private static TextColor brighten(TextColor color, float factor) {
        if (factor >= 0.999f) {
            return color;
        }
        Color src = new Color(color.value());
        int r = Math.min(255, Math.round(src.getRed() * factor + 255 * (1f - factor) * 0.15f));
        int g = Math.min(255, Math.round(src.getGreen() * factor + 255 * (1f - factor) * 0.15f));
        int b = Math.min(255, Math.round(src.getBlue() * factor + 255 * (1f - factor) * 0.15f));
        return TextColor.color(r, g, b);
    }

    private static void playSounds(Player player, String mainSoundName, String extraSoundName,
                                   float volume, float mainPitch, float extraPitch) {
        Sound main = parseSound(mainSoundName, Sound.ENTITY_PLAYER_ATTACK_CRIT);
        if (main != null && !isAchievementSound(main)) {
            player.playSound(player.getLocation(), main, volume, mainPitch);
        }
        Sound extra = parseSound(extraSoundName, null);
        if (extra != null && !isAchievementSound(extra)) {
            player.playSound(player.getLocation(), extra, volume * 0.65f, extraPitch);
        }
    }

    private static boolean isAchievementSound(Sound sound) {
        String name = sound.name();
        return name.contains("LEVEL_UP")
            || name.contains("LEVELUP")
            || name.contains("TOAST_CHALLENGE")
            || name.contains("UI_TOAST");
    }

    private static Sound parseSound(String name, Sound fallback) {
        if (name == null || name.isBlank() || name.equalsIgnoreCase("none")) {
            return fallback;
        }
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static void cancelAnimation(UUID uuid) {
        BukkitTask task = ACTIVE.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }
}
