package dev.grzybalson.modules.killeffect;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.EntityEffect;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KillEffectManager implements Listener, CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final File configFile;
    private YamlConfiguration config;

    private boolean enabled;
    private boolean totemEnabled;
    private boolean beehcEnabled;
    private final List<SoundEffectSpec> beehcSounds = new ArrayList<>();

    public KillEffectManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, "killeffect"), "killeffect.yml");
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, "addons/killeffect/killeffect.yml");
        if (configFile.exists() && configFile.length() > 0) {
            this.config = YamlConfiguration.loadConfiguration(configFile);
        } else {
            this.config = new YamlConfiguration();
        }

        this.enabled = config.getBoolean("enabled", true);
        this.totemEnabled = config.getBoolean("totem.enabled", true);
        this.beehcEnabled = config.getBoolean("beehc.enabled", true);

        this.beehcSounds.clear();
        if (config.isList("beehc.sounds")) {
            List<Map<?, ?>> soundList = config.getMapList("beehc.sounds");
            for (Map<?, ?> entry : soundList) {
                String soundName = String.valueOf(entry.get("sound"));
                if (isAchievementSound(soundName)) {
                    continue;
                }
                double volume = entry.containsKey("volume") ? Double.parseDouble(String.valueOf(entry.get("volume"))) : 10000000.0;
                double pitch = entry.containsKey("pitch") ? Double.parseDouble(String.valueOf(entry.get("pitch"))) : 1.0;
                beehcSounds.add(new SoundEffectSpec(soundName, (float) volume, (float) pitch));
            }
        }

        if (beehcSounds.isEmpty()) {
            beehcSounds.add(new SoundEffectSpec("entity.ender_dragon.shoot", 10000000f, 0.75f));
            beehcSounds.add(new SoundEffectSpec("entity.ender_dragon.ambient", 10000000f, 2.0f));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!enabled) return;

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) {
            if (victim.getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent damageEvent) {
                if (damageEvent.getDamager() instanceof Player p) {
                    killer = p;
                } else if (damageEvent.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) {
                    killer = p;
                }
            }
        }

        if (killer == null || killer.equals(victim)) return;

        if (totemEnabled && plugin.getUserSettingsManager() != null && plugin.getUserSettingsManager().isKillTotemEffectEnabled(killer.getUniqueId())) {
            playTotemEffect(killer, victim);
        }

        if (beehcEnabled) {
            playBeehcSounds(killer);
        }
    }

    private void playTotemEffect(Player killer, Player victim) {
        try {
            victim.playEffect(EntityEffect.TOTEM_RESURRECT);
        } catch (Throwable ignored) {}
    }

    private void playBeehcSounds(Player killer) {
        for (SoundEffectSpec spec : beehcSounds) {
            try {
                killer.playSound(killer.getLocation(), spec.sound(), SoundCategory.MASTER, spec.volume(), spec.pitch());
            } catch (Throwable t) {
                try {
                    org.bukkit.Sound s = org.bukkit.Sound.valueOf(spec.sound().toUpperCase().replace('.', '_'));
                    killer.playSound(killer.getLocation(), s, spec.volume(), spec.pitch());
                } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.killeffect.reload") && !sender.hasPermission("Holly.cmd.killeffect")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.killeffect.reload"));
                return true;
            }
            reload();
            sender.sendMessage(ColorUtils.success("Przeładowano konfigurację killeffect!"));
            return true;
        }

        ColorUtils.sendUsage(sender, "/" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload");
        }
        return List.of();
    }

    private static boolean isAchievementSound(String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return true;
        }
        String normalized = soundName.trim().toUpperCase(java.util.Locale.ROOT).replace('.', '_');
        return normalized.contains("LEVEL_UP")
            || normalized.contains("LEVELUP")
            || normalized.contains("TOAST")
            || normalized.equals("NONE");
    }

    private record SoundEffectSpec(String sound, float volume, float pitch) {}
}
