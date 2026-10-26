package dev.grzybalson.anarchiagg.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundsUtil {
    public static void error(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    public static void accept(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    public static void retro(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 1.0f);
        }
    }

    public static void arrow(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
        }
    }

    public static void grass(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.BLOCK_WET_GRASS_BREAK, 1.0f, 1.0f);
        }
    }

    public static void dragon(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 999.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 999.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 999.0f, 1.0f);
        }
    }

    public static void shulkerOpen(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.0f);
        }
    }

    public static void shulkerClose(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_CLOSE, 1.0f, 1.0f);
        }
    }

    public static void shield(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
        }
    }

    public static void glass(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 999.0f, 1.0f);
        }
    }

    public static void gui(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 999.0f, 1.0f);
        }
    }

    public static void itemshop(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 999.0f, 1.5f);
        }
    }

    public static void orb(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 999.0f, 1.0f);
        }
    }

    public static void celebrate(Player player) {
        if (player instanceof Player) {
            player.playSound(player.getLocation(), Sound.ENTITY_PILLAGER_CELEBRATE, 999.0f, 1.0f);
        }
    }
    public static void broadcast(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }
}