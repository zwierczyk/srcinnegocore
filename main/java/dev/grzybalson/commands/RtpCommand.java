package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.RtpConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RtpCommand implements CommandExecutor {

    private final HollyCore plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private static final Set<Material> UNSAFE_BLOCKS = Set.of(
        Material.LAVA, Material.WATER, Material.FIRE, Material.CACTUS,
        Material.MAGMA_BLOCK, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
        Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW,
        Material.COBWEB, Material.POINTED_DRIPSTONE, Material.VOID_AIR
    );

    private static final Set<Material> UNSAFE_GROUND = Set.of(
        Material.LAVA, Material.WATER, Material.FIRE, Material.CACTUS,
        Material.MAGMA_BLOCK, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
        Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW,
        Material.POINTED_DRIPSTONE, Material.SCAFFOLDING, Material.HONEY_BLOCK,
        Material.SLIME_BLOCK, Material.COBWEB
    );

    public RtpCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    public boolean canStartTeleport(Player player) {
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player)) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getCombatConfig().getCommandBlockedMessage()
            ));
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.rtp")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.rtp"));
            return true;
        }

        RtpConfig config = plugin.getConfigManager().getRtpConfig();

        if (!config.isEnabled()) {
            return true;
        }

        if (!canStartTeleport(player)) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMs = config.getCooldownSeconds() * 1000L;

        if (!player.hasPermission("Holly.rtp.bypass.cooldown")) {
            Long lastUse = cooldowns.get(uuid);
            if (lastUse != null && now - lastUse < cooldownMs) {
                long remaining = (cooldownMs - (now - lastUse)) / 1000;
                player.sendMessage(ColorUtils.colorizeComponent(
                    config.getCooldownMessage().replace("{time}", formatTime(remaining))
                ));
                return true;
            }
        }

        World world = Bukkit.getWorld(config.getWorldName());
        if (world == null) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getWorldNotFoundMessage()));
            return true;
        }

        player.sendMessage(ColorUtils.colorizeComponent(config.getTeleportingMessage()));

        findSafeLocationAsync(world, config, 0, safeLocation -> {
            if (safeLocation != null) {
                if (plugin.getTitleAddonManager() != null) {
                    plugin.getTitleAddonManager().markRtpTeleport(player);
                }
                player.teleportAsync(safeLocation).thenAccept(success -> {
                    if (success) {
                        cooldowns.put(uuid, System.currentTimeMillis());
                        player.sendMessage(ColorUtils.colorizeComponent(config.getTeleportedMessage()));
                        if (plugin.getTitleAddonManager() != null) {
                            Bukkit.getScheduler().runTask(plugin, () -> plugin.getTitleAddonManager().sendRtpSectorTitle(player));
                        }
                    }
                });
            } else {
                player.sendMessage(ColorUtils.colorizeComponent(config.getNoSafeLocationMessage()));
            }
        });

        return true;
    }

    public void findSafeLocationAsync(World world, RtpConfig config, int attempt, java.util.function.Consumer<Location> callback) {
        if (attempt >= config.getMaxAttempts()) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = random.nextInt(config.getMinX(), config.getMaxX() + 1);
        int z = random.nextInt(config.getMinZ(), config.getMaxZ() + 1);

        world.getChunkAtAsync(x >> 4, z >> 4, true).thenAccept(chunk -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int y = world.getHighestBlockYAt(x, z);

                if (y >= 1) {
                    Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
                    if (isSafeLocation(loc)) {
                        callback.accept(loc);
                        return;
                    }
                }

                findSafeLocationAsync(world, config, attempt + 1, callback);
            });
        });
    }

    private boolean isSafeLocation(Location loc) {
        Block ground = loc.clone().subtract(0, 1, 0).getBlock();
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block aboveHead = loc.clone().add(0, 2, 0).getBlock();

        if (!ground.getType().isSolid()) {
            return false;
        }

        if (UNSAFE_GROUND.contains(ground.getType())) {
            return false;
        }

        if (!feet.getType().isAir() && !feet.isPassable()) {
            return false;
        }

        if (!head.getType().isAir() && !head.isPassable()) {
            return false;
        }

        if (UNSAFE_BLOCKS.contains(feet.getType()) || UNSAFE_BLOCKS.contains(head.getType())) {
            return false;
        }

        if (aboveHead.getType() == Material.LAVA || aboveHead.getType() == Material.WATER) {
            return false;
        }

        if (loc.getY() < 1 || loc.getY() > 300) {
            return false;
        }

        return true;
    }

    private String formatTime(long seconds) {
        if (seconds >= 60) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return minutes + "m " + secs + "s";
        }
        return seconds + "s";
    }
}
