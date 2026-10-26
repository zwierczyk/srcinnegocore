package dev.grzybalson.modules.event.dungeon;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DungeonWorldService {

    public static final String TEMPLATE = "ninjaevent";
    public static final String PREFIX = "ninja_dungeon_";

    private final HollyCore plugin;

    public DungeonWorldService(HollyCore plugin) {
        this.plugin = plugin;
    }

    public static boolean isDungeonWorld(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    public CompletableFuture<World> cloneTemplateAsync() {
        String instanceName = PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Path container = Bukkit.getWorldContainer().toPath();
        Path source = container.resolve(TEMPLATE);
        Path target = container.resolve(instanceName);

        return CompletableFuture.supplyAsync(() -> {
            if (!Files.isDirectory(source) || !Files.exists(source.resolve("level.dat"))) {
                throw new IllegalStateException("missing-template");
            }
            try {
                if (Files.exists(target)) {
                    deleteRecursive(target);
                }
                copyWorld(source, target);
                Files.deleteIfExists(target.resolve("uid.dat"));
                Files.deleteIfExists(target.resolve("session.lock"));
            } catch (IOException exception) {
                throw new IllegalStateException("copy-failed", exception);
            }
            return instanceName;
        }).thenCompose(name -> loadWorldAsync(name));
    }

    /** Zaladuj istniejaca instancje dungeona (po restarcie). */
    public World loadExistingSync(String worldName) {
        if (worldName == null || worldName.isBlank() || !isDungeonWorld(worldName)) {
            return null;
        }
        World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            applyDungeonWorldRules(existing);
            return existing;
        }
        Path folder = Bukkit.getWorldContainer().toPath().resolve(worldName);
        if (!Files.isDirectory(folder) || !Files.exists(folder.resolve("level.dat"))) {
            return null;
        }
        try {
            Files.deleteIfExists(folder.resolve("session.lock"));
            Files.deleteIfExists(folder.resolve("uid.dat"));
        } catch (IOException ignored) {
        }
        try {
            World world = Bukkit.createWorld(new WorldCreator(worldName));
            if (world != null) {
                applyDungeonWorldRules(world);
            }
            return world;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[Dungeon] Nie udalo sie zaladowac swiata " + worldName + ": " + throwable.getMessage());
            return null;
        }
    }

    private CompletableFuture<World> loadWorldAsync(String name) {
        CompletableFuture<World> loaded = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                World world = Bukkit.createWorld(new WorldCreator(name));
                if (world == null) {
                    loaded.completeExceptionally(new IllegalStateException("create-failed"));
                    return;
                }
                applyDungeonWorldRules(world);
                loaded.complete(world);
            } catch (Throwable throwable) {
                loaded.completeExceptionally(throwable);
            }
        });
        return loaded;
    }

    private static void applyDungeonWorldRules(World world) {
        world.setAutoSave(true);
        world.setDifficulty(org.bukkit.Difficulty.HARD);
        world.setPVP(false);
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, false);
        world.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setTime(18000L);
    }

    /** Unload z zapisem — NIE kasuj folderu (restart serwera). */
    public void unloadKeep(World world) {
        if (world == null) {
            return;
        }
        String name = world.getName();
        try {
            world.save();
        } catch (Throwable ignored) {
        }
        Bukkit.unloadWorld(world, true);
        plugin.getLogger().info("[Dungeon] Zachowano swiat po restarcie: " + name);
    }

    public void unloadAndDelete(World world) {
        if (world == null) {
            return;
        }
        String name = world.getName();
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            if (main != null) {
                player.teleport(main.getSpawnLocation());
            }
        }
        Bukkit.unloadWorld(world, false);
        Path folder = Bukkit.getWorldContainer().toPath().resolve(name);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                deleteRecursive(folder);
            } catch (IOException ignored) {
            }
        });
    }

    private void copyWorld(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                String folderName = relative.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (folderName.equals("playerdata") || folderName.equals("stats") || folderName.equals("advancements")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (fileName.equals("session.lock") || fileName.equals("uid.dat")) {
                    return FileVisitResult.CONTINUE;
                }
                Path relative = source.relativize(file);
                Files.copy(file, target.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
