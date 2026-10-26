package dev.grzybalson.anarchiagg.util.notice;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.grzybalson.anarchiagg.util.TextUtil;

public class Notice {

    public static void sendP(Player player, NoticeType type, String content) {
        if (player == null) return;
        if (content == null || content.isBlank()) return;

        switch (normalize(type)) {
            case TITLE -> dev.grzybalson.anarchiagg.util.TextUtil.sendTitle(player, content, "&f", 1, 90, 10);
            case SUBTITLE -> dev.grzybalson.anarchiagg.util.TextUtil.sendTitle(player, "&f", content, 1, 90, 10);
            case ACTIONBAR -> dev.grzybalson.anarchiagg.util.TextUtil.sendActionBar(player, content);
            case CHAT -> dev.grzybalson.anarchiagg.util.TextUtil.sendMessage(player, content);
        }
    }

    public static void sendC(CommandSender sender, NoticeType type, String content) {
        if (sender == null) return;
        if (content == null || content.isBlank()) return;

        if (sender instanceof Player player) {
            sendP(player, type, content);
            return;
        }

        dev.grzybalson.anarchiagg.util.TextUtil.sendMessage(sender, content);
    }

    

    

    

    public static void broadcast(NoticeType type, String content) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendP(player, type, content);
        }
    }

    private static NoticeType normalize(NoticeType type) {
        return type == null ? NoticeType.CHAT : type;
    }
}
