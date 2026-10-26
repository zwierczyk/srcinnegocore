package dev.grzybalson.modules.moderation;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Konfiguracja wiadomości i TTL moderacji (addons/moderation/moderation.yml).
 */
public final class ModerationConfig {

    private final HollyCore plugin;
    private YamlConfiguration yaml;

    private String kickBan;
    private String kickTempban;
    private String kickKick;
    private boolean staffBroadcastEnabled;
    private String staffBroadcastBan;
    private String staffBroadcastMute;
    private String silentFlag;
    private long killCreditTtlMs;
    private String victimNotify;
    private String bossbarTitle;

    public ModerationConfig(HollyCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, "addons/moderation/moderation.yml");
        File file = new File(HollyDataPaths.getAddonFolder(plugin, "moderation"), "moderation.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);

        kickBan = yaml.getString("kick-ban", defaultKickBan());
        kickTempban = yaml.getString("kick-tempban", defaultKickTempban());
        kickKick = yaml.getString("kick-kick", defaultKickKick());
        staffBroadcastEnabled = yaml.getBoolean("staff-broadcast.enabled", true);
        staffBroadcastBan = yaml.getString("staff-broadcast.ban", defaultStaffBan());
        staffBroadcastMute = yaml.getString("staff-broadcast.mute", defaultStaffMute());
        silentFlag = yaml.getString("silent-flag", "-s");
        int days = Math.max(1, yaml.getInt("kill-credit-ttl-days", 7));
        killCreditTtlMs = TimeUnit.DAYS.toMillis(days);
        victimNotify = yaml.getString("victim-notify", defaultVictimNotify());
        bossbarTitle = yaml.getString("bossbar-title", defaultBossbarTitle());
        // Stary domyślny tytuł bez /zglos lista — podmień na nowy.
        if (bossbarTitle == null || bossbarTitle.isBlank()
            || bossbarTitle.equals("Gracz %killer% został zbanowany")) {
            bossbarTitle = defaultBossbarTitle();
        }
    }

    public String kickBan() {
        return kickBan;
    }

    public String kickTempban() {
        return kickTempban;
    }

    public String kickKick() {
        return kickKick;
    }

    public boolean staffBroadcastEnabled() {
        return staffBroadcastEnabled;
    }

    public String staffBroadcastBan() {
        return staffBroadcastBan;
    }

    public String staffBroadcastMute() {
        return staffBroadcastMute;
    }

    public String silentFlag() {
        return silentFlag;
    }

    public long killCreditTtlMs() {
        return killCreditTtlMs;
    }

    public String victimNotify() {
        return victimNotify;
    }

    public String bossbarTitle() {
        return bossbarTitle;
    }

    public String apply(String template, String reason, String staff, String remaining, String target, String action, String time, String killer) {
        String out = template == null ? "" : template;
        out = out.replace("%reason%", reason == null ? "" : reason);
        out = out.replace("%staff%", staff == null ? "" : staff);
        out = out.replace("%remaining%", remaining == null ? "" : remaining);
        out = out.replace("%target%", target == null ? "" : target);
        out = out.replace("%action%", action == null ? "" : action);
        out = out.replace("%time%", time == null ? "" : time);
        out = out.replace("%killer%", killer == null ? "" : killer);
        return out;
    }

    private static String defaultKickBan() {
        return "&c&lZOSTAŁEŚ ZBANOWANY\n\n&7Powód: &f%reason%\n&7Przez: &f%staff%\n&7Czas: &cna zawsze";
    }

    private static String defaultKickTempban() {
        return "&c&lZOSTAŁEŚ ZBANOWANY TYMCZASOWO\n\n&7Powód: &f%reason%\n&7Przez: &f%staff%\n&7Pozostało: &e%remaining%";
    }

    private static String defaultKickKick() {
        return "&c&lWYRZUCONY\n\n&7Powód: &f%reason%\n&7Przez: &f%staff%";
    }

    private static String defaultStaffBan() {
        return "&#00E0FFℹ &8Ξ &f%staff% &7%action% gracza &c%target% &8(&e%time%&8) &7Powód: &f%reason%";
    }

    private static String defaultStaffMute() {
        return "&#00E0FFℹ &8Ξ &f%staff% &7%action% gracza &c%target% &8(&e%time%&8) &7Powód: &f%reason%";
    }

    private static String defaultVictimNotify() {
        return "&#00E0FFℹ &8Ξ &7Gracz &c%killer% &7został zbanowany. &8(&f/zglos lista&8, &f/wyczysclista&8)";
    }

    private static String defaultBossbarTitle() {
        return "&#FF3D3D⚠ &fGracz &#FF6B6B%killer% &fzostał zbanowany &8• &#00E0FF/zglos lista";
    }
}
