package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CheckConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CheckManager {

    private final HollyCore plugin;
    private final Map<UUID, UUID> beingChecked; // player -> admin
    private final Map<UUID, UUID> checking; // admin -> player
    private final Map<UUID, Location> previousLocations; // player -> previous location

    public CheckManager(HollyCore plugin) {
        this.plugin = plugin;
        this.beingChecked = new HashMap<>();
        this.checking = new HashMap<>();
        this.previousLocations = new HashMap<>();
    }

    public boolean startCheck(Player admin, Player target) {
        CheckConfig config = plugin.getConfigManager().getCheckConfig();

        if (!config.isCheckLocationSet()) {
            admin.sendMessage(ColorUtils.colorizeComponent(config.getMessageCheckLocationNotSet()));
            return false;
        }

        if (isBeingChecked(target)) {
            admin.sendMessage(ColorUtils.colorizeComponent(config.getMessageAlreadyBeingChecked()));
            return false;
        }

        World world = Bukkit.getWorld(config.getCheckWorld());
        if (world == null) {
            admin.sendMessage(ColorUtils.error("Świat sprawdzania nie istnieje!"));
            return false;
        }

        Location checkLocation = new Location(
            world,
            config.getCheckX(),
            config.getCheckY(),
            config.getCheckZ(),
            config.getCheckYaw(),
            config.getCheckPitch()
        );

        previousLocations.put(target.getUniqueId(), target.getLocation().clone());

        beingChecked.put(target.getUniqueId(), admin.getUniqueId());
        checking.put(admin.getUniqueId(), target.getUniqueId());

        target.teleport(checkLocation);
        admin.teleport(checkLocation);

        target.sendMessage(ColorUtils.colorizeComponent(
            config.getMessageCheckStarted().replace("{admin}", admin.getName())
        ));
        admin.sendMessage(ColorUtils.colorizeComponent(
            config.getMessageAdminCheckStarted().replace("{player}", target.getName())
        ));

        return true;
    }

    public void endCheck(Player admin, CheckResult result) {
        CheckConfig config = plugin.getConfigManager().getCheckConfig();

        UUID targetUuid = checking.get(admin.getUniqueId());
        if (targetUuid == null) {
            admin.sendMessage(ColorUtils.colorizeComponent(config.getMessageNotChecking()));
            return;
        }

        Player target = Bukkit.getPlayer(targetUuid);
        String targetName = target != null ? target.getName() : Bukkit.getOfflinePlayer(targetUuid).getName();

        checking.remove(admin.getUniqueId());
        beingChecked.remove(targetUuid);

        String banCommand = switch (result) {
            case CLEAN -> config.getCleanBanCommand();
            case NO_COOPERATION -> config.getNoCooperationBanCommand();
            case CHEATER -> config.getCheaterBanCommand();
            case ADMITTED -> config.getAdmittedBanCommand();
        };

        if (result == CheckResult.CLEAN) {
            if (target != null) {
                Location spawnLoc = getSpawnLocation();
                if (spawnLoc != null) {
                    target.teleport(spawnLoc);
                }
                target.sendMessage(ColorUtils.colorizeComponent(config.getMessageCheckEnded()));
            }
        } else {
            dispatchCheckBan(banCommand, targetName, admin.getName());
        }

        previousLocations.remove(targetUuid);

        admin.sendMessage(ColorUtils.colorizeComponent(
            config.getMessageAdminCheckEnded().replace("{player}", targetName)
        ));
    }

    public void handlePlayerQuit(Player player) {
        UUID playerUuid = player.getUniqueId();

        if (beingChecked.containsKey(playerUuid)) {
            CheckConfig config = plugin.getConfigManager().getCheckConfig();
            UUID adminUuid = beingChecked.get(playerUuid);

            String adminName = resolveName(adminUuid);
            dispatchCheckBan(config.getLogoutBanCommand(), player.getName(), adminName);

            beingChecked.remove(playerUuid);
            checking.remove(adminUuid);
            previousLocations.remove(playerUuid);

            Player admin = Bukkit.getPlayer(adminUuid);
            if (admin != null) {
                admin.sendMessage(ColorUtils.colorizeComponent(
                    "&#FF0000✘ &8Ξ &cGracz &e" + player.getName() + " &cwylogował się podczas sprawdzania i został zbanowany!"
                ));
            }
        }

        if (checking.containsKey(playerUuid)) {
            UUID targetUuid = checking.get(playerUuid);
            checking.remove(playerUuid);
            beingChecked.remove(targetUuid);

            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null) {
                Location spawnLoc = getSpawnLocation();
                if (spawnLoc != null) {
                    target.teleport(spawnLoc);
                }
                target.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Administrator się wylogował. Sprawdzanie anulowane."
                ));
            }
            previousLocations.remove(targetUuid);
        }
    }

    public boolean isBeingChecked(Player player) {
        return beingChecked.containsKey(player.getUniqueId());
    }

    public boolean isChecking(Player admin) {
        return checking.containsKey(admin.getUniqueId());
    }

    public Player getCheckingAdmin(Player target) {
        UUID adminUuid = beingChecked.get(target.getUniqueId());
        return adminUuid != null ? Bukkit.getPlayer(adminUuid) : null;
    }

    public String getCheckingAdminName(UUID targetUuid) {
        if (targetUuid == null) {
            return null;
        }
        return resolveName(beingChecked.get(targetUuid));
    }

    public boolean isParticipant(Player player) {
        UUID uuid = player.getUniqueId();
        return beingChecked.containsKey(uuid) || checking.containsKey(uuid);
    }

    public Player getCheckedPlayer(Player admin) {
        UUID targetUuid = checking.get(admin.getUniqueId());
        return targetUuid != null ? Bukkit.getPlayer(targetUuid) : null;
    }

    private void dispatchCheckBan(String banCommand, String targetName, String adminName) {
        if (banCommand == null || banCommand.isBlank() || targetName == null || targetName.isBlank()) {
            return;
        }
        String parsed = banCommand
            .replace("{player}", targetName)
            .replace("{admin}", adminName == null ? "" : adminName);
        parsed = appendAdminNick(parsed, adminName);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
    }

    public static String appendAdminNick(String text, String adminName) {
        if (text == null || text.isBlank() || adminName == null || adminName.isBlank()) {
            return text;
        }
        String suffix = "~" + adminName;
        String trimmed = text.trim();
        if (trimmed.endsWith(suffix)) {
            return trimmed;
        }
        return trimmed + " ~" + adminName;
    }

    private String resolveName(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        return Bukkit.getOfflinePlayer(uuid).getName();
    }

    public boolean isCommandAllowed(String command) {
        CheckConfig config = plugin.getConfigManager().getCheckConfig();
        String cmd = command.toLowerCase().split(" ")[0].replace("/", "");
        return config.getAllowedCommands().stream()
            .anyMatch(allowed -> allowed.equalsIgnoreCase(cmd));
    }

    private Location getSpawnLocation() {
        var spawnConfig = plugin.getConfigManager().getSpawnConfig();
        if (!spawnConfig.isSpawnSet()) {
            return null;
        }
        World world = Bukkit.getWorld(spawnConfig.getWorld());
        if (world == null) {
            return null;
        }
        return new Location(
            world,
            spawnConfig.getX(),
            spawnConfig.getY(),
            spawnConfig.getZ(),
            spawnConfig.getYaw(),
            spawnConfig.getPitch()
        );
    }

    public enum CheckResult {
        CLEAN,
        NO_COOPERATION,
        CHEATER,
        ADMITTED
    }
}
