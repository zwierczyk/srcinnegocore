package dev.grzybalson.utils;

import dev.grzybalson.HollyCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Komunikat dla funkcji odblokowywanych w późniejszym etapie (brak permisji).
 */
public final class LockedFeatureMessages {

    private static final String CROSS = "\uE0C0"; // KRZYZYK X

    private LockedFeatureMessages() {
    }

    public static void send(Player player, String featureLabel) {
        if (player == null) {
            return;
        }

        HollyCore plugin = HollyCore.getInstance();
        if (plugin != null && plugin.getLockedFeaturesManager() != null) {
            plugin.getLockedFeaturesManager().sendLocked(player, featureLabel);
            return;
        }

        String label = featureLabel == null || featureLabel.isBlank() ? "Ta funkcja" : featureLabel.trim();

        String title = "&#FF1A1A" + CROSS + " &c&lZablokowane &#FF1A1A" + CROSS;
        String subtitle = "&7" + label + " &8— &#FF5C5Cdostępne później";

        ColorUtils.sendTitle(player, title, subtitle, 8, 55, 12);

        Component chat = ColorUtils.colorizeComponent(
            "&#FF1A1A" + CROSS + " &8• &#FF5C5C" + label
                + " &7zostanie odblokowane w &#FF5C5C&lpóźniejszym etapie &7rozgrywki."
        );
        player.sendMessage(chat);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.75F, 0.65F);
    }
}
