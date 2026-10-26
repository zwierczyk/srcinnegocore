package dev.grzybalson.modules.worlds;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * /world — create, delete, confirmdelete, tp, list, load, unload.
 * Port z hollyservices-engine WorldCommand (zamiennik MultiverseCore).
 */
public final class WorldCommand implements CommandExecutor, TabCompleter {

    private static final String CONFIG_RESOURCE = "addons/worlds/worlds.yml";
    private static final List<String> WORLD_TYPES = List.of("flat", "void", "normal", "nether", "end");
        private static final List<String> SUBS = List.of(
        "create", "delete", "confirmdelete", "tp", "list", "load", "unload", "info", "help"
    );

    private final HollyCore plugin;
    private final WorldManager manager;
    private final File configFile;
    private YamlConfiguration config;
    private final Set<String> pendingDeletes = new HashSet<>();

    public WorldCommand(HollyCore plugin, WorldManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.configFile = HollyDataPaths.getRootFile(plugin, CONFIG_RESOURCE);
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.world")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.world"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "confirmdelete" -> confirmDelete(sender, args);
            case "tp" -> tp(sender, args);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "load" -> load(sender, args);
            case "unload" -> unload(sender, args);
            case "reload" -> {
                reload();
                sender.sendMessage(msg("reloaded"));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length < 3) {
            ColorUtils.sendUsage(sender, "/" + "world create <nazwa> <typ>",
                "typy: flat, void, normal, nether, end");
            return;
        }
        String name = args[1];
        String type = args[2].toLowerCase(Locale.ROOT);
        if (Bukkit.getWorld(name) != null) {
            send(sender, "already-exists", Map.of("NAME", name));
            playFail(sender);
            return;
        }
        if (!WORLD_TYPES.contains(type)) {
            send(sender, "unknown-type", Map.of("TYPE", type));
            playFail(sender);
            return;
        }

        send(sender, "creating", Map.of("NAME", name));
        play(sender, Sound.BLOCK_NOTE_BLOCK_BELL, 0.4F, 1.2F);

        Bukkit.getScheduler().runTask(plugin, () -> {
            WorldCreator creator = new WorldCreator(name);
            switch (type) {
                case "flat" -> {
                    creator.type(WorldType.FLAT);
                    creator.environment(World.Environment.NORMAL);
                }
                case "void" -> {
                    creator.type(WorldType.FLAT);
                    creator.environment(World.Environment.NORMAL);
                    creator.generator(new VoidGenerator());
                    creator.generateStructures(false);
                }
                case "nether" -> creator.environment(World.Environment.NETHER);
                case "end" -> creator.environment(World.Environment.THE_END);
                default -> {
                    creator.type(WorldType.NORMAL);
                    creator.environment(World.Environment.NORMAL);
                }
            }
            creator.createWorld();
            send(sender, "created", Map.of("NAME", name, "TYPE", type));
            play(sender, Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.6F);
        });
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/world delete <nazwa>");
            return;
        }
        String name = args[1];
        World world = Bukkit.getWorld(name);
        if (world == null) {
            send(sender, "not-found", Map.of("NAME", name));
            playFail(sender);
            return;
        }
        if (world.equals(Bukkit.getWorlds().get(0))) {
            send(sender, "cannot-delete-default", Map.of());
            playFail(sender);
            return;
        }
        pendingDeletes.add(name.toLowerCase(Locale.ROOT));
        send(sender, "delete-confirm", Map.of("NAME", name));
        play(sender, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5F, 0.8F);
    }

    private void confirmDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/world confirmdelete <nazwa>");
            return;
        }
        String name = args[1];
        if (!pendingDeletes.remove(name.toLowerCase(Locale.ROOT))) {
            send(sender, "not-found", Map.of("NAME", name));
            return;
        }
        World world = Bukkit.getWorld(name);
        if (world == null) {
            send(sender, "not-found", Map.of("NAME", name));
            return;
        }

        World mainWorld = Bukkit.getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            player.teleport(mainWorld.getSpawnLocation());
        }

        File worldFolder = world.getWorldFolder();
        Bukkit.unloadWorld(world, false);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteFolder(worldFolder));

        send(sender, "deleted", Map.of("NAME", name));
        play(sender, Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.6F);
    }

    private void tp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✖ Tylko dla graczy."));
            return;
        }
        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/world tp <nazwa>");
            return;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            send(sender, "not-found", Map.of("NAME", args[1]));
            playFail(sender);
            return;
        }
        if (dev.grzybalson.modules.event.dungeon.DungeonWorldService.isDungeonWorld(world.getName())) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTen swiat jest ukryta instancja dungeona!"));
            playFail(sender);
            return;
        }
        player.teleport(world.getSpawnLocation());
        send(sender, "teleported", Map.of("NAME", world.getName()));
        play(sender, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.4F);
    }

    private void list(CommandSender sender) {
        send(sender, "header", Map.of());
        send(sender, "title", Map.of());
        for (World world : Bukkit.getWorlds()) {
            if (dev.grzybalson.modules.event.dungeon.DungeonWorldService.isDungeonWorld(world.getName())) {
                continue;
            }
            String env = switch (world.getEnvironment()) {
                case NETHER -> "nether";
                case THE_END -> "end";
                default -> "normal";
            };
            sender.sendMessage(ColorUtils.colorizeComponent(
                "  &8- &f" + world.getName()
                    + " &8— &#AAAAAA" + env
                    + " &8(&#AAAAAA" + world.getPlayers().size() + " graczy&8)"
            ));
        }
        for (String unloaded : manager.getUnloadedWorldNames()) {
            if (dev.grzybalson.modules.event.dungeon.DungeonWorldService.isDungeonWorld(unloaded)) {
                continue;
            }
            sender.sendMessage(ColorUtils.colorizeComponent(
                "  &8- &7" + unloaded + " &8— &#FF5C5Codladowany"
            ));
        }
        send(sender, "header", Map.of());
        play(sender, Sound.UI_BUTTON_CLICK, 0.4F, 1.4F);
    }

    private void info(CommandSender sender, String[] args) {
        World world;
        if (args.length >= 2) {
            world = Bukkit.getWorld(args[1]);
            if (world == null) {
                send(sender, "not-found", Map.of("NAME", args[1]));
                playFail(sender);
                return;
            }
        } else if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            ColorUtils.sendUsage(sender, "/world info [nazwa]");
            return;
        }

        String env = switch (world.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> "Overworld";
        };
        long time = world.getTime();
        long hours = (time / 1000L + 6L) % 24L;
        long minutes = (time % 1000L) * 60L / 1000L;
        String timeStr = String.format("%02d:%02d", hours, minutes);
        String weather = world.hasStorm() ? (world.isThundering() ? "burza" : "deszcz") : "slonecznie";
        int players = world.getPlayers().size();

        send(sender, "header", Map.of());
        sender.sendMessage(ColorUtils.colorizeComponent("  &#89E2FF&lWORLD INFO"));
        sender.sendMessage(ColorUtils.colorizeComponent("  &8» &7Swiat: &f" + world.getName()));
        sender.sendMessage(ColorUtils.colorizeComponent("  &8» &7Srodowisko: &f" + env));
        sender.sendMessage(ColorUtils.colorizeComponent("  &8» &7Graczy online: &f" + players));
        sender.sendMessage(ColorUtils.colorizeComponent("  &8» &7Trudnosc: &f" + world.getDifficulty().name().toLowerCase(Locale.ROOT)));
        sender.sendMessage(ColorUtils.colorizeComponent("  &8» &7Czas: &f" + timeStr + " &8(&#AAAAAA" + time + " ticks&8)"));
        sender.sendMessage(ColorUtils.colorizeComponent("  &8» &7Pogoda: &f" + weather));
        sender.sendMessage(ColorUtils.colorizeComponent("  &8» &7PvP: &f" + (world.getPVP() ? "tak" : "nie")));
        sender.sendMessage(ColorUtils.colorizeComponent(
            "  &8» &7Spawn: &f"
                + world.getSpawnLocation().getBlockX() + " "
                + world.getSpawnLocation().getBlockY() + " "
                + world.getSpawnLocation().getBlockZ()
        ));
        if (sender instanceof Player player && player.getWorld().equals(world)) {
            var loc = player.getLocation();
            sender.sendMessage(ColorUtils.colorizeComponent(
                "  &8» &7Twoja pozycja: &f"
                    + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
            ));
        }
        if (!world.getPlayers().isEmpty()) {
            StringBuilder names = new StringBuilder();
            int shown = 0;
            for (Player online : world.getPlayers()) {
                if (shown >= 12) {
                    names.append("&8, &7...");
                    break;
                }
                if (shown > 0) {
                    names.append("&8, ");
                }
                names.append("&f").append(online.getName());
                shown++;
            }
            sender.sendMessage(ColorUtils.colorizeComponent("  &8» &7Gracze: " + names));
        }
        send(sender, "header", Map.of());
        play(sender, Sound.UI_BUTTON_CLICK, 0.4F, 1.4F);
    }

    private void load(CommandSender sender, String[] args) {
        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/world load <nazwa>");
            return;
        }
        String name = args[1];
        if (Bukkit.getWorld(name) != null) {
            send(sender, "already-exists", Map.of("NAME", name));
            playFail(sender);
            return;
        }
        File worldFolder = new File(Bukkit.getWorldContainer(), name);
        if (!worldFolder.exists() || !new File(worldFolder, "level.dat").exists()) {
            send(sender, "not-found", Map.of("NAME", name));
            playFail(sender);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            new WorldCreator(name).createWorld();
            send(sender, "loaded", Map.of("NAME", name));
            play(sender, Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.6F);
        });
    }

    private void unload(CommandSender sender, String[] args) {
        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/world unload <nazwa>");
            return;
        }
        String name = args[1];
        World world = Bukkit.getWorld(name);
        if (world == null) {
            send(sender, "not-found", Map.of("NAME", name));
            playFail(sender);
            return;
        }
        if (world.equals(Bukkit.getWorlds().get(0))) {
            send(sender, "cannot-unload-default", Map.of());
            playFail(sender);
            return;
        }
        World mainWorld = Bukkit.getWorlds().get(0);
        for (Player player : world.getPlayers()) {
            player.teleport(mainWorld.getSpawnLocation());
        }
        Bukkit.unloadWorld(world, true);
        send(sender, "unloaded", Map.of("NAME", name));
        play(sender, Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.6F);
    }

    private void sendHelp(CommandSender sender) {
        send(sender, "header", Map.of());
        send(sender, "title", Map.of());
        send(sender, "help-create", Map.of());
        send(sender, "help-delete", Map.of());
        send(sender, "help-tp", Map.of());
        send(sender, "help-list", Map.of());
        send(sender, "help-info", Map.of());
        send(sender, "help-load", Map.of());
        send(sender, "help-unload", Map.of());
        send(sender, "header", Map.of());
    }

    private void send(CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(ColorUtils.colorizeComponent(msg(key, placeholders)));
    }

    private String msg(String key) {
        return msg(key, Map.of());
    }

    private String msg(String key, Map<String, String> placeholders) {
        String value = config.getString("messages." + key, "&cBrak wiadomosci: " + key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    }

    private void play(CommandSender sender, Sound sound, float volume, float pitch) {
        if (sender instanceof Player player) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private void playFail(CommandSender sender) {
        play(sender, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.8F);
    }

    private void deleteFolder(File folder) {
        if (folder == null || !folder.exists()) {
            return;
        }
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        folder.delete();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.world")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> List.of("<nazwa>");
                case "tp", "unload", "delete", "info" -> filter(manager.getLoadedWorldNames(), args[1]);
                case "load" -> filter(manager.getUnloadedWorldNames(), args[1]);
                case "confirmdelete" -> filter(new ArrayList<>(pendingDeletes), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return filter(WORLD_TYPES, args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    /** Void world generator — puste chunki. */
    public static final class VoidGenerator extends ChunkGenerator {
    }
}
