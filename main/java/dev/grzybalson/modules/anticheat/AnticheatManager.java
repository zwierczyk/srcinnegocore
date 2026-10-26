package dev.grzybalson.modules.anticheat;

import com.github.retrooper.packetevents.PacketEvents;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.config.configs.AnticheatConfig;
import dev.grzybalson.utils.ColorUtils;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AnticheatManager {

    private final HollyCore plugin;
    private final AnticheatConfig config;
    private final Map<UUID, CheatPlayerData> players = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public AnticheatManager(HollyCore plugin) {
        this.plugin = plugin;
        File folder = HollyDataPaths.getAddonFolder(plugin, "anticheat");
        this.config = eu.okaeri.configs.ConfigManager.create(AnticheatConfig.class, it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(folder, "anticheat.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        this.config.save();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(new AnticheatHandler(plugin, this), plugin);
        Bukkit.getPluginManager().registerEvents(new CobwebCheck(this), plugin);
        Bukkit.getPluginManager().registerEvents(new FireworkCheck(this), plugin);
        Bukkit.getPluginManager().registerEvents(new AuraCheck(this), plugin);
        Bukkit.getPluginManager().registerEvents(new ReachCheck(this), plugin);
        Bukkit.getPluginManager().registerEvents(new CobwebPhaseCheck(this), plugin);
        Bukkit.getPluginManager().registerEvents(new AutoTrapCheck(this), plugin);
        Bukkit.getPluginManager().registerEvents(new CobwebThroughWallCheck(this), plugin);
        Bukkit.getPluginManager().registerEvents(new HitThroughBlockCheck(this), plugin);
        if (config.isAimbot()) {
            AimbotCheck aimbotCheck = new AimbotCheck(this);
            PacketEvents.getAPI().getEventManager().registerListener(aimbotCheck);
            Bukkit.getPluginManager().registerEvents(aimbotCheck, plugin);
        }
        plugin.getLogger().info("Anticheat loaded: reach, hit-through, cobweb, auto-trap, aura, aimbot.");
    }

    public void shutdown() {
        players.clear();
    }

    public void reload() {
        config.load();
    }

    public AnticheatConfig getConfig() {
        return config;
    }

    public HollyCore getPlugin() {
        return plugin;
    }

    public CheatPlayerData data(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), ignored -> new CheatPlayerData());
    }

    public void remove(UUID uuid) {
        players.remove(uuid);
    }

    public void notifyStaff(Player player, CheatType type, int vl, String template, Map<String, String> extra) {
        CheatPlayerData data = data(player);
        if (data.getLastNotify() + 5000L > System.currentTimeMillis()) {
            return;
        }
        data.setLastNotify(System.currentTimeMillis());
        String message = template
            .replace("{prefix}", config.getPrefix())
            .replace("{player}", player.getName())
            .replace("{vl}", String.valueOf(vl));
        if (extra != null) {
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                message = message.replace(entry.getKey(), entry.getValue());
            }
        }
        String permission = config.getNotifyPermission();
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(permission) || staff.isOp()) {
                staff.sendMessage(ColorUtils.colorizeComponent(message));
            }
        }
        plugin.getLogger().info("[Anticheat] " + type.name() + " " + player.getName() + " vl=" + vl);
    }

    public void swapHands(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        player.getInventory().setItemInOffHand(main);
        player.getInventory().setItemInMainHand(off);
    }

    public void randomLook(Player player) {
        var location = player.getLocation();
        location.setYaw(random.nextFloat() * 360f - 180f);
        location.setPitch(random.nextFloat() * 180f - 90f);
        player.teleport(location);
    }

    public void punishBlockPlace(Player player, org.bukkit.Material material, int cooldownTicks, long blockMs) {
        if (material != null && material.isItem()) {
            player.setCooldown(material, Math.max(1, cooldownTicks));
        }
        data(player).setBlockPlaceUntil(System.currentTimeMillis() + Math.max(0L, blockMs));
    }

    public void punishCobwebPlace(Player player, int cooldownTicks, long blockMs) {
        punishBlockPlace(player, org.bukkit.Material.COBWEB, cooldownTicks, blockMs);
    }

    public void punishCobwebMove(Player player, long blockMs) {
        data(player).setBlockMoveUntil(System.currentTimeMillis() + Math.max(0L, blockMs));
    }

    public void scrambleLook(Player player, int times) {
        for (int i = 0; i < times; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    randomLook(player);
                }
            }, 5L * i);
        }
    }
}