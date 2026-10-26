package dev.grzybalson.commands;

import com.sun.management.OperatingSystemMXBean;
import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lekki profiler wbudowany w core. Nie zastępuje pełnego Sparka, ale wykonuje
 * próbkę głównego wątku i na jej podstawie podaje konkretne miejsca oraz
 * praktyczne zalecenia optymalizacyjne.
 */
public final class ServerInfoCommand implements CommandExecutor, TabCompleter {

    private static final String CONFIG_RESOURCE = "addons/commands/serverinfo.yml";
    private static final int MAX_RESULT_LIMIT = 12;

    private final HollyCore plugin;
    private final Thread mainThread;
    private final File configFile;
    private final AtomicBoolean sampling = new AtomicBoolean(false);
    private volatile YamlConfiguration config;

    public ServerInfoCommand(HollyCore plugin) {
        this.plugin = plugin;
        this.mainThread = Thread.currentThread();
        this.configFile = new File(plugin.getDataFolder(), CONFIG_RESOURCE);
        reloadConfig();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.serverinfo")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.serverinfo"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(ColorUtils.success("Przeładowano ustawienia &f/serverinfo&7."));
            return true;
        }

        SamplingProfile profile;
        if (args.length == 0 || args[0].equalsIgnoreCase("quick") || args[0].equalsIgnoreCase("szybki")) {
            profile = readProfile("quick", "SZYBKI", 60, 50L, 6);
        } else if (args[0].equalsIgnoreCase("deep") || args[0].equalsIgnoreCase("dokladny")
            || args[0].equalsIgnoreCase("dokładny")) {
            profile = readProfile("deep", "DOKŁADNY", 160, 50L, 9);
        } else {
            ColorUtils.sendUsage(sender,
                "/serverinfo",
                "/serverinfo quick",
                "/serverinfo deep",
                "/serverinfo reload"
            );
            return true;
        }

        if (!sampling.compareAndSet(false, true)) {
            sender.sendMessage(ColorUtils.error("Pomiar obciążenia już trwa. Spróbuj ponownie za chwilę."));
            return true;
        }

        List<PluginDescriptor> pluginDescriptors = collectPluginDescriptors();
        long seconds = Math.max(1L, Math.round(profile.durationMillis() / 1000.0D));
        sender.sendMessage(ColorUtils.info("Holly AI analizuje serwer przez &b" + seconds
            + " s&f. Nie zatrzymuje to ticków serwera."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SampleResult result;
            try {
                result = sampleMainThread(profile, pluginDescriptors);
            } finally {
                sampling.set(false);
            }

            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sender instanceof Player player && !player.isOnline()) {
                    return;
                }
                sendReport(sender, profile, result);
            });
        });
        return true;
    }

    private SamplingProfile readProfile(String path, String displayName, int defaultSamples,
                                        long defaultIntervalMillis, int defaultResultLimit) {
        YamlConfiguration current = config;
        int samples = Math.max(20, Math.min(400, current.getInt("profiles." + path + ".samples", defaultSamples)));
        long interval = Math.max(20L, Math.min(250L,
            current.getLong("profiles." + path + ".interval-millis", defaultIntervalMillis)));
        int resultLimit = Math.max(3, Math.min(MAX_RESULT_LIMIT,
            current.getInt("profiles." + path + ".result-limit", defaultResultLimit)));
        return new SamplingProfile(displayName, samples, interval, resultLimit);
    }

    private SampleResult sampleMainThread(SamplingProfile profile, List<PluginDescriptor> pluginDescriptors) {
        Map<String, Integer> pluginMethodHits = new LinkedHashMap<>();
        Map<String, Integer> pluginHits = new LinkedHashMap<>();
        Map<String, Integer> serverMethodHits = new LinkedHashMap<>();
        Map<String, Integer> blockingHits = new LinkedHashMap<>();
        Map<LoadCategory, Integer> categoryHits = new EnumMap<>(LoadCategory.class);
        Map<Thread.State, Integer> threadStateHits = new EnumMap<>(Thread.State.class);
        int serverHits = 0;
        int activeSamples = 0;
        int idleSamples = 0;

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        boolean cpuTimeSupported = threadBean.isThreadCpuTimeSupported();
        if (cpuTimeSupported && !threadBean.isThreadCpuTimeEnabled()) {
            try {
                threadBean.setThreadCpuTimeEnabled(true);
            } catch (UnsupportedOperationException | SecurityException ignored) {
                cpuTimeSupported = false;
            }
        }

        long cpuStart = cpuTimeSupported ? threadBean.getThreadCpuTime(mainThread.getId()) : -1L;
        GcSnapshot gcStart = gcSnapshot();
        long wallStart = System.nanoTime();
        int completedSamples = 0;

        for (int sample = 0; sample < profile.samples(); sample++) {
            StackTraceElement[] trace = mainThread.getStackTrace();
            threadStateHits.merge(mainThread.getState(), 1, Integer::sum);

            if (isIdleTrace(trace)) {
                idleSamples++;
            } else {
                activeSamples++;
                SampledFrame pluginFrame = findPluginFrame(trace, pluginDescriptors);
                if (pluginFrame == null) {
                    serverHits++;
                    String serverFrame = findServerFrame(trace);
                    if (serverFrame != null) {
                        serverMethodHits.merge(serverFrame, 1, Integer::sum);
                    }
                } else {
                    pluginMethodHits.merge(pluginFrame.displayMethod(), 1, Integer::sum);
                    pluginHits.merge(pluginFrame.pluginName(), 1, Integer::sum);
                }

                classifyTrace(trace, categoryHits, blockingHits);
            }
            completedSamples++;

            if (sample + 1 >= profile.samples()) {
                continue;
            }
            try {
                Thread.sleep(profile.intervalMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        long wallEnd = System.nanoTime();
        long cpuEnd = cpuTimeSupported ? threadBean.getThreadCpuTime(mainThread.getId()) : -1L;
        GcSnapshot gcEnd = gcSnapshot();
        long cpuDelta = cpuStart >= 0L && cpuEnd >= cpuStart ? cpuEnd - cpuStart : -1L;

        return new SampleResult(
            pluginMethodHits,
            pluginHits,
            serverMethodHits,
            blockingHits,
            categoryHits,
            threadStateHits,
            Math.max(1, completedSamples),
            activeSamples,
            idleSamples,
            serverHits,
            Math.max(1L, wallEnd - wallStart),
            cpuDelta,
            Math.max(0L, gcEnd.collections() - gcStart.collections()),
            Math.max(0L, gcEnd.timeMillis() - gcStart.timeMillis())
        );
    }

    /**
     * Odróżnia faktyczną pracę od normalnego oczekiwania serwera na następny tick.
     * Bez tego zdrowy serwer wyglądałby tak, jakby MinecraftServer.waitUntilNextTick
     * był najcięższą metodą, mimo że jest to po prostu wolny czas CPU.
     */
    private boolean isIdleTrace(StackTraceElement[] trace) {
        boolean nextTickWait = false;
        boolean parked = false;
        boolean suspiciousBlocking = false;

        for (StackTraceElement element : trace) {
            String normalized = (element.getClassName() + "." + element.getMethodName())
                .toLowerCase(Locale.ROOT);
            if (normalized.contains("waituntilnexttick")
                || normalized.contains("waituntilnexttickend")
                || normalized.contains("sleepfortick")
                || normalized.contains("tickthreadrunner.wait")) {
                nextTickWait = true;
            }
            if (normalized.contains("locksupport.park")
                || normalized.contains("thread.sleep")
                || normalized.contains("managedblock")) {
                parked = true;
            }
            if (normalized.contains("mongodb") || normalized.contains("java.sql")
                || normalized.contains("jdbc") || normalized.contains("java.net")
                || normalized.contains("okhttp") || normalized.contains("completablefuture.get")
                || normalized.contains("completablefuture.join") || normalized.contains("futuretask.get")
                || normalized.contains("yamlconfiguration.save") || normalized.contains("yamlconfiguration.load")) {
                suspiciousBlocking = true;
            }
        }
        return nextTickWait && parked && !suspiciousBlocking;
    }

    private void classifyTrace(StackTraceElement[] trace, Map<LoadCategory, Integer> categoryHits,
                               Map<String, Integer> blockingHits) {
        Set<LoadCategory> matchedCategories = new HashSet<>();
        Set<String> matchedBlocking = new HashSet<>();

        for (StackTraceElement element : trace) {
            String className = element.getClassName();
            String methodName = element.getMethodName();
            String normalized = (className + "." + methodName).toLowerCase(Locale.ROOT);

            for (LoadCategory category : LoadCategory.values()) {
                if (category.matches(normalized)) {
                    matchedCategories.add(category);
                }
            }

            String blocking = detectBlockingOperation(className, methodName, normalized);
            if (blocking != null) {
                matchedBlocking.add(blocking);
            }
        }

        for (LoadCategory category : matchedCategories) {
            categoryHits.merge(category, 1, Integer::sum);
        }
        for (String blocking : matchedBlocking) {
            blockingHits.merge(blocking, 1, Integer::sum);
        }
    }

    private String detectBlockingOperation(String className, String methodName, String normalized) {
        if (normalized.contains("completablefuture.join") || normalized.contains("completablefuture.get")
            || normalized.contains("futuretask.get")) {
            return "Oczekiwanie na Future/CompletableFuture";
        }
        if (normalized.contains("thread.sleep") || normalized.contains("object.wait")
            || normalized.contains("locksupport.park")) {
            return "Uśpienie lub oczekiwanie wątku";
        }
        if (normalized.contains("org.mongodb") || normalized.contains("mongodb")) {
            return "MongoDB na ścieżce głównego wątku";
        }
        if (normalized.contains("org.sqlite") || normalized.contains("java.sql") || normalized.contains("jdbc")) {
            return "SQL/SQLite na ścieżce głównego wątku";
        }
        if (normalized.contains("java.net.") || normalized.contains("okhttp") || normalized.contains("socket")) {
            return "Operacja sieciowa na ścieżce głównego wątku";
        }
        if (normalized.contains("java.nio.file") || normalized.contains("java.io.file")
            || normalized.contains("yamlconfiguration.save") || normalized.contains("yamlconfiguration.load")) {
            return "Odczyt lub zapis pliku na głównym wątku";
        }
        if (className.startsWith("sun.nio.ch") && (methodName.contains("read") || methodName.contains("write"))) {
            return "Blokujące I/O na głównym wątku";
        }
        return null;
    }

    private SampledFrame findPluginFrame(StackTraceElement[] trace, List<PluginDescriptor> plugins) {
        for (StackTraceElement element : trace) {
            String className = element.getClassName();
            for (PluginDescriptor descriptor : plugins) {
                if (!className.startsWith(descriptor.packagePrefix())) {
                    continue;
                }
                return new SampledFrame(
                    descriptor.name(),
                    descriptor.name() + " &8→ &f" + compactMethod(className, element.getMethodName())
                );
            }
        }
        return null;
    }

    private String findServerFrame(StackTraceElement[] trace) {
        for (StackTraceElement element : trace) {
            String className = element.getClassName();
            if (className.startsWith("net.minecraft.") || className.startsWith("io.papermc.paper.")
                || className.startsWith("org.bukkit.craftbukkit.")) {
                return "Minecraft/Paper &8→ &f" + compactMethod(className, element.getMethodName());
            }
        }
        return null;
    }

    private String compactMethod(String className, String methodName) {
        String simpleClass = className.substring(className.lastIndexOf('.') + 1);
        int nestedClass = simpleClass.indexOf('$');
        if (nestedClass >= 0) {
            simpleClass = simpleClass.substring(0, nestedClass);
        }
        return simpleClass + "." + methodName;
    }

    private List<PluginDescriptor> collectPluginDescriptors() {
        List<PluginDescriptor> result = new ArrayList<>();
        for (Plugin loadedPlugin : Bukkit.getPluginManager().getPlugins()) {
            String className = loadedPlugin.getClass().getName();
            int separator = className.lastIndexOf('.');
            if (separator <= 0) {
                continue;
            }
            result.add(new PluginDescriptor(loadedPlugin.getName(), className.substring(0, separator + 1)));
        }
        result.sort(Comparator.comparingInt((PluginDescriptor descriptor) -> descriptor.packagePrefix().length()).reversed());
        return result;
    }

    private void sendReport(CommandSender sender, SamplingProfile profile, SampleResult sampleResult) {
        ServerSnapshot snapshot = createServerSnapshot(sampleResult);
        int resultLimit = profile.resultLimit();

        sender.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
        sender.sendMessage(ColorUtils.colorizeComponent("&#00E0FF&lHOLLY AI &8• &fANALIZA SERWERA &8(&7" + profile.name() + "&8)"));
        sender.sendMessage(ColorUtils.colorizeComponent("&8› &fStan: " + healthColor(snapshot.mspt()) + healthName(snapshot.mspt())
            + " &8| &fPróbki: &#00E0FF" + sampleResult.samples()
            + " &8| &fCzas: &#00E0FF" + formatSeconds(sampleResult.wallNanos())));
        sender.sendMessage(ColorUtils.colorizeComponent("&8› &fAktywna praca: &#FFB800" + sampleResult.activeSamples()
            + " &8| &fBezczynność: &#4EFA76" + formatPercent(sampleResult.idleSamples() / (double) sampleResult.samples())
            + " &8| &fPewność diagnozy: " + confidenceColor(sampleResult) + confidenceName(sampleResult)));
        sender.sendMessage(ColorUtils.colorizeComponent("&8› &fTPS: " + tpsColor(snapshot.tps()[0]) + format(snapshot.tps()[0])
            + " &8/ " + tpsColor(snapshot.tps()[1]) + format(snapshot.tps()[1])
            + " &8/ " + tpsColor(snapshot.tps()[2]) + format(snapshot.tps()[2])
            + " &8| &fMSPT: " + msptColor(snapshot.mspt()) + format(snapshot.mspt())));
        sender.sendMessage(ColorUtils.colorizeComponent("&8› &fCPU main: " + percentColor(snapshot.mainThreadCpu())
            + formatPercent(snapshot.mainThreadCpu()) + " &8| &fProces: " + percentColor(snapshot.processCpu())
            + formatPercent(snapshot.processCpu()) + " &8| &fSystem: " + percentColor(snapshot.systemCpu())
            + formatPercent(snapshot.systemCpu())));
        sender.sendMessage(ColorUtils.colorizeComponent("&8› &fRAM: &#00E0FF" + formatMegabytes(snapshot.usedMemory())
            + " MB &8/ &7" + formatMegabytes(snapshot.maxMemory()) + " MB &8(" + memoryColor(snapshot.usedMemory(), snapshot.maxMemory())
            + formatPercent(ratio(snapshot.usedMemory(), snapshot.maxMemory())) + "&8) &8| &fGC: &#B388FF"
            + sampleResult.gcTimeMillis() + " ms"));
        sender.sendMessage(ColorUtils.colorizeComponent("&8› &fGracze: &#00E0FF" + Bukkit.getOnlinePlayers().size()
            + "&8/&7" + Bukkit.getMaxPlayers() + " &8| &fChunki: &#00E0FF" + snapshot.loadedChunks()
            + " &8| &fEncje: &#00E0FF" + snapshot.entities()));
        sender.sendMessage(ColorUtils.colorizeComponent("&8› &fMoby: &#00E0FF" + snapshot.monsters()
            + " &8| &fItemy: &#00E0FF" + snapshot.items() + " &8| &fDisplaye: &#00E0FF" + snapshot.displays()
            + " &8| &fArmorStandy: &#00E0FF" + snapshot.armorStands()));

        sender.sendMessage(ColorUtils.colorizeComponent(""));
        sender.sendMessage(ColorUtils.colorizeComponent("&#FFB800&lCO DOKŁADNIE OBCIĄŻA &8(&7główny wątek&8)"));
        List<Map.Entry<String, Integer>> hotMethods = mergeHotMethods(sampleResult);
        if (sampleResult.activeSamples() == 0) {
            sender.sendMessage(ColorUtils.colorizeComponent(" &8• &#4EFA76Serwer przez cały pomiar czekał na kolejne ticki — nie było aktywnego obciążenia."));
        } else if (hotMethods.isEmpty()) {
            sender.sendMessage(ColorUtils.colorizeComponent(" &8• &7Nie znaleziono jednego dominującego miejsca w tej próbce."));
        } else {
            for (Map.Entry<String, Integer> entry : hotMethods.stream().limit(resultLimit).toList()) {
                double share = entry.getValue() / (double) loadSamples(sampleResult);
                sender.sendMessage(ColorUtils.colorizeComponent(" &8• &f" + entry.getKey()
                    + " &8— " + loadColor(share) + formatPercent(share)
                    + " &8(&7" + entry.getValue() + "/" + loadSamples(sampleResult) + " aktywnych próbek&8)"));
            }
        }

        List<Map.Entry<String, Integer>> hotPlugins = sortedEntries(sampleResult.pluginHits());
        if (!hotPlugins.isEmpty()) {
            sender.sendMessage(ColorUtils.colorizeComponent(""));
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF7AC8&lPLUGINY NA GORĄCEJ ŚCIEŻCE"));
            for (Map.Entry<String, Integer> entry : hotPlugins.stream().limit(5).toList()) {
                double share = entry.getValue() / (double) loadSamples(sampleResult);
                sender.sendMessage(ColorUtils.colorizeComponent(" &8• &f" + entry.getKey()
                    + " &8— " + loadColor(share) + formatPercent(share)
                    + " &8(&7" + entry.getValue() + "/" + loadSamples(sampleResult) + " aktywnych próbek&8)"));
            }
        }

        List<Map.Entry<LoadCategory, Integer>> categories = sortedCategoryEntries(sampleResult.categoryHits());
        if (!categories.isEmpty()) {
            sender.sendMessage(ColorUtils.colorizeComponent(""));
            sender.sendMessage(ColorUtils.colorizeComponent("&#B388FF&lRODZAJ OBCIĄŻENIA"));
            for (Map.Entry<LoadCategory, Integer> entry : categories.stream().limit(6).toList()) {
                double share = entry.getValue() / (double) loadSamples(sampleResult);
                sender.sendMessage(ColorUtils.colorizeComponent(" &8• &f" + entry.getKey().displayName()
                    + " &8— " + loadColor(share) + formatPercent(share)));
            }
        }

        if (!sampleResult.blockingHits().isEmpty()) {
            sender.sendMessage(ColorUtils.colorizeComponent(""));
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C&lBLOKOWANIE GŁÓWNEGO WĄTKU"));
            for (Map.Entry<String, Integer> entry : sortedEntries(sampleResult.blockingHits()).stream().limit(4).toList()) {
                double share = entry.getValue() / (double) loadSamples(sampleResult);
                sender.sendMessage(ColorUtils.colorizeComponent(" &8• &f" + entry.getKey()
                    + " &8— " + loadColor(share) + formatPercent(share)));
            }
        }

        sender.sendMessage(ColorUtils.colorizeComponent(""));
        sender.sendMessage(ColorUtils.colorizeComponent("&#4EFA76&lHOLLY AI — CO NAJLEPIEJ POPRAWIĆ"));
        List<Finding> findings = createFindings(snapshot, sampleResult);
        int maxFindings = Math.max(2, Math.min(8, config.getInt("analysis.max-recommendations", 5)));
        for (Finding finding : findings.stream().limit(maxFindings).toList()) {
            sender.sendMessage(ColorUtils.colorizeComponent(" " + finding.severity().prefix() + " &f" + finding.title()));
            sender.sendMessage(ColorUtils.colorizeComponent("   &8↳ &7Powód: " + finding.evidence()));
            sender.sendMessage(ColorUtils.colorizeComponent("   &8↳ &7Poprawa: &f" + finding.recommendation()));
        }

        sender.sendMessage(ColorUtils.colorizeComponent(""));
        sender.sendMessage(ColorUtils.colorizeComponent("&#00E0FF&lNAJCIĘŻSZE ŚWIATY"));
        for (WorldLoad world : snapshot.worldLoads().stream().limit(3).toList()) {
            sender.sendMessage(ColorUtils.colorizeComponent(" &8• &f" + world.name() + " &8— &7chunki: &#00E0FF"
                + world.chunks() + "&7, encje: &#00E0FF" + world.entities() + "&7, itemy: &#00E0FF"
                + world.items() + "&7, gracze: &#00E0FF" + world.players()));
        }

        List<Map.Entry<String, Integer>> topEntityTypes = sortedEntries(snapshot.entityTypes());
        if (!topEntityTypes.isEmpty()) {
            sender.sendMessage(ColorUtils.colorizeComponent("&8› &7Najwięcej encji: "
                + topEntityTypes.stream().limit(5)
                    .map(entry -> "&f" + entry.getKey() + " &#00E0FF" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining(" &8• "))));
        }

        List<Map.Entry<String, TaskLoad>> topTasks = sortedTaskEntries(snapshot.pendingTasks());
        if (!topTasks.isEmpty()) {
            sender.sendMessage(ColorUtils.colorizeComponent(""));
            sender.sendMessage(ColorUtils.colorizeComponent("&#B388FF&lZADANIA SCHEDULERA &8(&7liczba ≠ zużycie CPU&8)"));
            for (Map.Entry<String, TaskLoad> entry : topTasks.stream().limit(4).toList()) {
                TaskLoad load = entry.getValue();
                sender.sendMessage(ColorUtils.colorizeComponent(" &8• &f" + entry.getKey() + " &8— &#B388FF"
                    + load.total() + " &7zadań &8(&fsync " + load.sync() + "&8, &fasync " + load.async() + "&8)"));
            }
        }

        double pluginShare = sampleResult.activeSamples() == 0 ? 0.0D : 1.0D - (sampleResult.serverHits() / (double) sampleResult.activeSamples());
        sender.sendMessage(ColorUtils.colorizeComponent(""));
        sender.sendMessage(ColorUtils.colorizeComponent("&8› &7Kod pluginów był na gorącej ścieżce w "
            + loadColor(pluginShare) + formatPercent(pluginShare) + "&7 próbek."));
        sender.sendMessage(ColorUtils.colorizeComponent("&8› &7To analiza próbkowa. Przy skokach lagów użyj też dłuższego &f/serverinfo deep&7 lub Sparka."));
        sender.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
    }

    private ServerSnapshot createServerSnapshot(SampleResult sampleResult) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double[] tps = Bukkit.getTPS();
        double mspt = Bukkit.getAverageTickTime();
        double processCpu = processCpuLoad();
        double systemCpu = systemCpuLoad();
        double mainThreadCpu = sampleResult.cpuNanos() < 0L
            ? -1.0D
            : Math.min(1.0D, sampleResult.cpuNanos() / (double) sampleResult.wallNanos());

        int loadedChunks = 0;
        int entities = 0;
        int items = 0;
        int monsters = 0;
        int animals = 0;
        int displays = 0;
        int armorStands = 0;
        int projectiles = 0;
        List<WorldLoad> worldLoads = new ArrayList<>();
        Map<String, Integer> entityTypes = new HashMap<>();

        for (World world : Bukkit.getWorlds()) {
            int worldChunks = world.getLoadedChunks().length;
            int worldEntities = 0;
            int worldItems = 0;
            int worldMonsters = 0;
            int worldDisplays = 0;

            for (Entity entity : world.getEntities()) {
                worldEntities++;
                entityTypes.merge(entity.getType().name(), 1, Integer::sum);
                if (entity instanceof Item) {
                    items++;
                    worldItems++;
                }
                if (entity instanceof Monster) {
                    monsters++;
                    worldMonsters++;
                } else if (entity instanceof Animals) {
                    animals++;
                }
                if (entity instanceof Display) {
                    displays++;
                    worldDisplays++;
                }
                if (entity instanceof ArmorStand) {
                    armorStands++;
                }
                if (entity instanceof Projectile) {
                    projectiles++;
                }
            }

            loadedChunks += worldChunks;
            entities += worldEntities;
            worldLoads.add(new WorldLoad(world.getName(), worldChunks, worldEntities, worldItems,
                worldMonsters, worldDisplays, world.getPlayers().size()));
        }
        worldLoads.sort(Comparator.comparingInt(WorldLoad::weight).reversed());

        Map<String, TaskLoad> pendingTasks = new LinkedHashMap<>();
        for (BukkitTask task : Bukkit.getScheduler().getPendingTasks()) {
            pendingTasks.compute(task.getOwner().getName(), (name, current) -> {
                TaskLoad load = current == null ? new TaskLoad(0, 0, 0) : current;
                return task.isSync()
                    ? new TaskLoad(load.total() + 1, load.sync() + 1, load.async())
                    : new TaskLoad(load.total() + 1, load.sync(), load.async() + 1);
            });
        }

        return new ServerSnapshot(
            usedMemory, maxMemory, tps, mspt, processCpu, systemCpu, mainThreadCpu,
            loadedChunks, entities, items, monsters, animals, displays, armorStands, projectiles,
            worldLoads, pendingTasks, entityTypes
        );
    }

    private List<Finding> createFindings(ServerSnapshot snapshot, SampleResult result) {
        List<Finding> findings = new ArrayList<>();
        double msptWarning = config.getDouble("thresholds.mspt-warning", 40.0D);
        double msptCritical = config.getDouble("thresholds.mspt-critical", 50.0D);
        double ramWarning = config.getDouble("thresholds.ram-warning-percent", 82.0D) / 100.0D;
        double cpuWarning = config.getDouble("thresholds.cpu-warning-percent", 85.0D) / 100.0D;
        int entitiesPerPlayerWarning = Math.max(20, config.getInt("thresholds.entities-per-player-warning", 160));
        int chunksPerPlayerWarning = Math.max(20, config.getInt("thresholds.chunks-per-player-warning", 180));
        int tasksWarning = Math.max(20, config.getInt("thresholds.pending-tasks-warning", 120));
        double categoryWarning = config.getDouble("thresholds.category-sample-warning-percent", 12.0D) / 100.0D;

        Map.Entry<String, Integer> hottestMethod = firstEntry(mergeHotMethods(result));
        String hottestEvidence = hottestMethod == null
            ? "brak dominującej metody"
            : hottestMethod.getKey() + " &8(" + formatPercent(hottestMethod.getValue() / (double) loadSamples(result)) + "&8)";

        if (result.activeSamples() < 8 && snapshot.mspt() < msptWarning) {
            findings.add(new Finding(Severity.GOOD, "Serwer był głównie bezczynny podczas pomiaru",
                "Aktywnych było tylko &#4EFA76" + result.activeSamples() + "&7 z " + result.samples() + " próbek",
                "Nie ma teraz wyraźnego problemu. Uruchom /serverinfo deep dokładnie w chwili laga, aby AI złapało ciężką metodę."));
        }

        if (snapshot.mspt() >= msptCritical) {
            findings.add(new Finding(Severity.CRITICAL, "Główny wątek nie wyrabia z tickami",
                "MSPT wynosi " + msptColor(snapshot.mspt()) + format(snapshot.mspt()) + " ms&7; najgorętsze miejsce: " + hottestEvidence,
                "Najpierw popraw pierwszą metodę z sekcji „CO DOKŁADNIE OBCIĄŻA”. Celuj w MSPT poniżej 40 ms."));
        } else if (snapshot.mspt() >= msptWarning) {
            findings.add(new Finding(Severity.WARNING, "Serwer jest blisko utraty TPS",
                "MSPT wynosi " + msptColor(snapshot.mspt()) + format(snapshot.mspt()) + " ms&7; najgorętsze miejsce: " + hottestEvidence,
                "Zmniejsz częstotliwość ciężkich zadań i przenieś pracę na partie zamiast wykonywać wszystko w jednym ticku."));
        }

        if (!result.blockingHits().isEmpty()) {
            Map.Entry<String, Integer> blocking = firstEntry(sortedEntries(result.blockingHits()));
            double share = blocking.getValue() / (double) loadSamples(result);
            findings.add(new Finding(Severity.CRITICAL, "Główny wątek czeka na I/O lub Future",
                blocking.getKey() + " pojawiło się w " + loadColor(share) + formatPercent(share) + "&7 próbek",
                "Usuń join()/get() z eventów i komend. MongoDB, SQL, HTTP oraz zapis YAML wykonuj async, a wynik wracaj schedulerem na main thread."));
        }

        if (hottestMethod != null) {
            double hottestShare = hottestMethod.getValue() / (double) loadSamples(result);
            if (hottestShare >= 0.08D) {
                Severity severity = hottestShare >= 0.25D ? Severity.WARNING : Severity.INFO;
                findings.add(new Finding(severity, "Priorytet #1: popraw dokładnie tę metodę",
                    "&f" + hottestMethod.getKey() + " &8— " + loadColor(hottestShare) + formatPercent(hottestShare),
                    recommendForMethod(hottestMethod.getKey())));
            }
        }

        addCategoryFinding(findings, result, LoadCategory.TABLIST, categoryWarning, Severity.WARNING,
            "TAB, scoreboard lub placeholdery są zbyt częste",
            "Cache'uj placeholdery, aktualizuj graczy partiami i ustaw rzadszy update (np. 10–20 ticków zamiast co tick).");
        addCategoryFinding(findings, result, LoadCategory.PLAYER_MOVE, categoryWarning, Severity.WARNING,
            "Obsługa ruchu graczy jest gorącą ścieżką",
            "W PlayerMoveEvent reaguj dopiero po zmianie bloku/chunka. Nie licz regionów i placeholderów przy każdym obrocie głowy.");
        addCategoryFinding(findings, result, LoadCategory.WORLDGUARD, categoryWarning, Severity.WARNING,
            "Zapytania WorldGuard/regionów zabierają tick",
            "Cache'uj wynik regionu na gracza i odświeżaj tylko po zmianie bloku lub wejściu do innego regionu.");
        addCategoryFinding(findings, result, LoadCategory.DATABASE, categoryWarning, Severity.CRITICAL,
            "Baza danych pojawia się na głównym wątku",
            "Każdy odczyt/zapis wykonuj asynchronicznie, dodaj cache graczy i krótkie timeouty. Main thread powinien tylko użyć gotowego wyniku.");
        addCategoryFinding(findings, result, LoadCategory.FILE_IO, categoryWarning, Severity.WARNING,
            "Config lub pliki są czytane/zapisywane podczas ticka",
            "Nie zapisuj YAML po każdym kliknięciu. Grupuj zmiany, zapisuj okresowo lub async i trzymaj config w pamięci.");
        addCategoryFinding(findings, result, LoadCategory.NETWORK, categoryWarning, Severity.CRITICAL,
            "Sieć/HTTP/JDA jest na ścieżce ticka",
            "Połączenia sieciowe wykonuj wyłącznie async. Dodaj timeout i nie czekaj na odpowiedź przez Future#get/join.");
        addCategoryFinding(findings, result, LoadCategory.GENERATOR_HOLOGRAM, categoryWarning, Severity.WARNING,
            "Generatory lub hologramy są aktualizowane zbyt często",
            "Aktualizuj hologram tylko po zmianie tekstu, usuń duplikaty i użyj jednego wspólnego zadania zamiast taska na każdy generator.");
        addCategoryFinding(findings, result, LoadCategory.ENTITY, categoryWarning, Severity.WARNING,
            "Tickowanie lub skanowanie encji dominuje",
            "Nie wywołuj getEntities() co tick. Skanuj partie chunków, ogranicz moby/itemy i wyłącz AI tam, gdzie nie jest potrzebne.");
        addCategoryFinding(findings, result, LoadCategory.CHUNK, categoryWarning, Severity.WARNING,
            "Ładowanie lub iterowanie chunków jest gorące",
            "Zmniejsz view/simulation-distance, unikaj force-load i nie pobieraj całej listy chunków w częstych zadaniach.");
        addCategoryFinding(findings, result, LoadCategory.GUI, categoryWarning, Severity.INFO,
            "GUI jest często przebudowywane",
            "Nie twórz wszystkich ItemStacków od nowa przy każdym kliknięciu. Cache'uj stałe ikony i odświeżaj tylko zmienione sloty.");

        int online = Math.max(1, Bukkit.getOnlinePlayers().size());
        double entitiesPerPlayer = snapshot.entities() / (double) online;
        if (entitiesPerPlayer >= entitiesPerPlayerWarning) {
            WorldLoad heaviest = snapshot.worldLoads().isEmpty() ? null : snapshot.worldLoads().get(0);
            String worldEvidence = heaviest == null ? "" : "; najwięcej ma &f" + heaviest.name() + " &7(" + heaviest.entities() + ")";
            findings.add(new Finding(Severity.WARNING, "Za dużo encji względem liczby graczy",
                "Średnio &#FFB800" + format(entitiesPerPlayer) + " encji/gracza&7" + worldEvidence,
                "Ogranicz spawn mobów, czyść itemy, sprawdź ArmorStand/Display i zmniejsz zasięg aktywacji encji."));
        }

        double chunksPerPlayer = snapshot.loadedChunks() / (double) online;
        if (chunksPerPlayer >= chunksPerPlayerWarning) {
            findings.add(new Finding(Severity.WARNING, "Za dużo załadowanych chunków",
                "Średnio &#FFB800" + format(chunksPerPlayer) + " chunków/gracza",
                "Sprawdź force-load, farmy z chunk loaderami oraz view-distance i simulation-distance."));
        }

        double memoryRatio = ratio(snapshot.usedMemory(), snapshot.maxMemory());
        if (memoryRatio >= ramWarning || result.gcTimeMillis() >= 250L) {
            findings.add(new Finding(memoryRatio >= 0.92D ? Severity.CRITICAL : Severity.WARNING,
                "Duża presja pamięci i garbage collectora",
                "RAM: " + memoryColor(snapshot.usedMemory(), snapshot.maxMemory()) + formatPercent(memoryRatio)
                    + "&7, pauzy GC podczas pomiaru: &#B388FF" + result.gcTimeMillis() + " ms",
                "Znajdź często tworzone ItemStacki/Componenty/listy, ogranicz cache bez limitu i unikaj pełnych skanów encji/chunków."));
        }

        if (snapshot.mainThreadCpu() >= cpuWarning) {
            findings.add(new Finding(Severity.WARNING, "Główny wątek zużywa prawie cały rdzeń",
                "CPU main thread: " + percentColor(snapshot.mainThreadCpu()) + formatPercent(snapshot.mainThreadCpu()),
                "Rozbij duże pętle na batch'e wykonywane przez kilka ticków. Async używaj tylko dla I/O i obliczeń bez Bukkit API."));
        } else if (snapshot.processCpu() >= cpuWarning && snapshot.mainThreadCpu() >= 0.0D
            && snapshot.mainThreadCpu() < 0.45D) {
            findings.add(new Finding(Severity.INFO, "CPU zużywają głównie inne wątki lub hosting",
                "Proces: " + percentColor(snapshot.processCpu()) + formatPercent(snapshot.processCpu())
                    + "&7, main thread: " + percentColor(snapshot.mainThreadCpu()) + formatPercent(snapshot.mainThreadCpu()),
                "Sprawdź async taski, Mongo/JDA, kompresję backupów oraz czy inne serwery nie dzielą tego samego rdzenia."));
        }

        Map.Entry<String, TaskLoad> taskEntry = firstEntry(sortedTaskEntries(snapshot.pendingTasks()));
        if (taskEntry != null && taskEntry.getValue().total() >= tasksWarning) {
            findings.add(new Finding(Severity.INFO, "Jeden plugin ma bardzo dużo zadań schedulera",
                "&f" + taskEntry.getKey() + "&7 ma &#B388FF" + taskEntry.getValue().total() + " zadań",
                "Połącz timery w jeden manager i przetwarzaj obiekty w jednej pętli. Sama liczba tasków nie oznacza laga, ale utrudnia kontrolę."));
        }

        if (findings.isEmpty()) {
            findings.add(new Finding(Severity.GOOD, "Nie wykryto oczywistego wąskiego gardła",
                "TPS/MSPT, CPU, RAM i próbka stosu są w bezpiecznych zakresach",
                "Przy chwilowym lagu uruchom /serverinfo deep dokładnie podczas problemu, aby złapać krótkie skoki."));
        }

        findings.sort(Comparator.comparingInt((Finding finding) -> finding.severity().priority()).reversed());
        return findings;
    }

    private String recommendForMethod(String method) {
        String normalized = method == null ? "" : method.toLowerCase(Locale.ROOT);
        if (normalized.contains("tab") || normalized.contains("scoreboard") || normalized.contains("placeholder")) {
            return "Cache'uj wynik tej metody, aktualizuj graczy partiami i nie parsuj placeholderów częściej niż raz na 10–20 ticków.";
        }
        if (normalized.contains("move") || normalized.contains("worldguard") || normalized.contains("region")) {
            return "Uruchamiaj tę logikę dopiero po zmianie bloku lub chunka, a wynik regionu trzymaj w krótkim cache per gracz.";
        }
        if (normalized.contains("mongo") || normalized.contains("sql") || normalized.contains("database")
            || normalized.contains("repository")) {
            return "Przenieś odczyt/zapis poza main thread, dodaj cache i zwracaj na główny wątek wyłącznie gotowy wynik.";
        }
        if (normalized.contains("yaml") || normalized.contains("config") || normalized.contains("save")
            || normalized.contains("file")) {
            return "Nie czytaj ani nie zapisuj pliku w evencie. Trzymaj dane w pamięci i zapisuj je partiami lub asynchronicznie.";
        }
        if (normalized.contains("generator") || normalized.contains("hologram")
            || normalized.contains("display")) {
            return "Połącz aktualizacje w jeden task, zmieniaj hologram tylko gdy tekst się zmieni i rozkładaj regenerację dużych obszarów na partie.";
        }
        if (normalized.contains("inventory") || normalized.contains("gui") || normalized.contains("itemstack")
            || normalized.contains("itemmeta")) {
            return "Cache'uj stałe ikony GUI i przebudowuj wyłącznie zmienione sloty zamiast tworzyć całe menu od nowa.";
        }
        if (normalized.contains("entity") || normalized.contains("mob") || normalized.contains("getentities")) {
            return "Nie skanuj wszystkich encji co tick. Przetwarzaj chunki partiami i ogranicz liczbę mobów, itemów oraz Display/ArmorStand.";
        }
        if (normalized.contains("chunk") || normalized.contains("world")) {
            return "Ogranicz pełne skany świata, force-load oraz częste getLoadedChunks(). Duże pętle rozbij na kilka ticków.";
        }
        if (normalized.contains("scheduler") || normalized.contains("task") || normalized.contains("runnable")) {
            return "Połącz wiele timerów w jeden manager i wykonuj elementy partiami z limitem pracy na pojedynczy tick.";
        }
        return "Zmniejsz częstotliwość wywołań tej metody, dodaj cache i rozbij jej najdłuższą pętlę na mniejsze partie. Potem porównaj wynik /serverinfo deep.";
    }

    private void addCategoryFinding(List<Finding> findings, SampleResult result, LoadCategory category,
                                    double threshold, Severity severity, String title, String recommendation) {
        int hits = result.categoryHits().getOrDefault(category, 0);
        double share = hits / (double) loadSamples(result);
        if (share < threshold) {
            return;
        }
        findings.add(new Finding(severity, title,
            category.displayName() + " pojawiło się w " + loadColor(share) + formatPercent(share) + "&7 próbek",
            recommendation));
    }

    private List<Map.Entry<String, Integer>> mergeHotMethods(SampleResult result) {
        Map<String, Integer> combined = new LinkedHashMap<>();
        combined.putAll(result.pluginMethodHits());
        for (Map.Entry<String, Integer> entry : result.serverMethodHits().entrySet()) {
            combined.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        return sortedEntries(combined);
    }

    private void reloadConfig() {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!configFile.exists()) {
            try {
                plugin.saveResource(CONFIG_RESOURCE, false);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Brakuje zasobu " + CONFIG_RESOURCE + ": " + exception.getMessage());
            }
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    private static List<Map.Entry<String, Integer>> sortedEntries(Map<String, Integer> values) {
        return values.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .toList();
    }

    private static List<Map.Entry<LoadCategory, Integer>> sortedCategoryEntries(Map<LoadCategory, Integer> values) {
        return values.entrySet().stream()
            .sorted(Map.Entry.<LoadCategory, Integer>comparingByValue().reversed())
            .toList();
    }

    private static List<Map.Entry<String, TaskLoad>> sortedTaskEntries(Map<String, TaskLoad> values) {
        return values.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, TaskLoad> entry) -> entry.getValue().total()).reversed())
            .toList();
    }

    private static <K, V> Map.Entry<K, V> firstEntry(List<Map.Entry<K, V>> entries) {
        return entries.isEmpty() ? null : entries.get(0);
    }

    private static GcSnapshot gcSnapshot() {
        long collections = 0L;
        long time = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean.getCollectionCount() >= 0L) {
                collections += bean.getCollectionCount();
            }
            if (bean.getCollectionTime() >= 0L) {
                time += bean.getCollectionTime();
            }
        }
        return new GcSnapshot(collections, time);
    }

    private static double processCpuLoad() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean operatingSystem) {
            return normalizeLoad(operatingSystem.getProcessCpuLoad());
        }
        return -1.0D;
    }

    private static double systemCpuLoad() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean operatingSystem) {
            return normalizeLoad(operatingSystem.getCpuLoad());
        }
        return -1.0D;
    }

    private static double normalizeLoad(double value) {
        return Double.isFinite(value) && value >= 0.0D ? Math.min(1.0D, value) : -1.0D;
    }

    private static double ratio(long value, long maximum) {
        return maximum <= 0L ? 0.0D : Math.min(1.0D, value / (double) maximum);
    }

    private static int loadSamples(SampleResult result) {
        return Math.max(1, result.activeSamples());
    }

    private static String confidenceName(SampleResult result) {
        int active = result.activeSamples();
        if (active >= 50) return "WYSOKA";
        if (active >= 15) return "ŚREDNIA";
        return "NISKA";
    }

    private static String confidenceColor(SampleResult result) {
        int active = result.activeSamples();
        if (active >= 50) return "&#4EFA76";
        if (active >= 15) return "&#FFB800";
        return "&7";
    }

    private static String healthName(double mspt) {
        if (mspt <= 35.0D) return "DOBRY";
        if (mspt <= 50.0D) return "OSTRZEŻENIE";
        return "KRYTYCZNY";
    }

    private static String healthColor(double mspt) {
        if (mspt <= 35.0D) return "&#4EFA76";
        if (mspt <= 50.0D) return "&#FFB800";
        return "&#FF0000";
    }

    private static String tpsColor(double tps) {
        if (tps >= 19.0D) return "&#4EFA76";
        if (tps >= 17.0D) return "&#FFB800";
        return "&#FF0000";
    }

    private static String msptColor(double mspt) {
        if (mspt <= 35.0D) return "&#4EFA76";
        if (mspt <= 50.0D) return "&#FFB800";
        return "&#FF0000";
    }

    private static String memoryColor(long used, long maximum) {
        return loadColor(ratio(used, maximum));
    }

    private static String percentColor(double load) {
        return loadColor(load);
    }

    private static String loadColor(double share) {
        if (share < 0.0D) return "&7";
        if (share < 0.35D) return "&#4EFA76";
        if (share < 0.70D) return "&#FFB800";
        return "&#FF0000";
    }

    private static String format(double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            return "brak";
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static String formatPercent(double value) {
        if (!Double.isFinite(value) || value < 0.0D) {
            return "brak";
        }
        return String.format(Locale.US, "%.1f%%", value * 100.0D);
    }

    private static String formatSeconds(long nanos) {
        return String.format(Locale.US, "%.1f s", nanos / 1_000_000_000.0D);
    }

    private static long formatMegabytes(long bytes) {
        return Math.max(0L, bytes / 1024L / 1024L);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.serverinfo") || args.length != 1) {
            return List.of();
        }
        String input = args[0].toLowerCase(Locale.ROOT);
        return List.of("quick", "deep", "reload").stream()
            .filter(value -> value.startsWith(input))
            .toList();
    }

    private enum LoadCategory {
        DATABASE("Baza danych", "mongodb", "database", "datasource", "jdbc", "sqlite", "repository"),
        FILE_IO("Pliki i configi", "yaml", "config", "java.io", "java.nio.file", ".save", ".load"),
        NETWORK("Sieć / HTTP / JDA", "java.net", "okhttp", "socket", "websocket", "jda", "packet"),
        PLAYER_MOVE("PlayerMoveEvent", "playermove", "onplayermove", "movement", "moveevent"),
        WORLDGUARD("WorldGuard / regiony", "worldguard", "regioncontainer", "regionquery", "applicableregions"),
        TABLIST("TAB / scoreboard / placeholdery", "tablist", "sidebar", "scoreboard", "placeholder", "fastboard"),
        ENTITY("Encje / moby", "entity", "mob", "living", "activationrange", "ticknonpassenger"),
        CHUNK("Chunki / świat", "chunk", "worldserver", "levelchunk", "distance-manager", "chunkmap"),
        GENERATOR_HOLOGRAM("Generatory / hologramy", "generator", "hologram", "textdisplay", "itemdisplay"),
        GUI("GUI / ekwipunki", "inventory", "gui", "menu", "itemstack", "itemmeta"),
        SCHEDULER("Scheduler / taski", "scheduler", "bukkittask", "runnable", "heartbe"),
        ECONOMY("Ekonomia / sklep", "economy", "vault", "shop", "wallet", "balance"),
        COMMAND("Komendy", "command", "execute", "dispatchcommand"),
        WORLD_SAVE("Zapis świata", "savelevel", "savechunks", "autosave", "playersave");

        private final String displayName;
        private final List<String> tokens;

        LoadCategory(String displayName, String... tokens) {
            this.displayName = displayName;
            this.tokens = List.of(tokens);
        }

        private boolean matches(String normalizedFrame) {
            for (String token : tokens) {
                if (normalizedFrame.contains(token)) {
                    return true;
                }
            }
            return false;
        }

        private String displayName() {
            return displayName;
        }
    }

    private enum Severity {
        GOOD(0, "&#4EFA76✔"),
        INFO(1, "&#00E0FFℹ"),
        WARNING(2, "&#FFB800⚠"),
        CRITICAL(3, "&#FF0000✘");

        private final int priority;
        private final String prefix;

        Severity(int priority, String prefix) {
            this.priority = priority;
            this.prefix = prefix;
        }

        private int priority() {
            return priority;
        }

        private String prefix() {
            return prefix;
        }
    }

    private record SamplingProfile(String name, int samples, long intervalMillis, int resultLimit) {
        private long durationMillis() {
            return samples * intervalMillis;
        }
    }

    private record PluginDescriptor(String name, String packagePrefix) {}

    private record SampledFrame(String pluginName, String displayMethod) {}

    private record SampleResult(Map<String, Integer> pluginMethodHits,
                                Map<String, Integer> pluginHits,
                                Map<String, Integer> serverMethodHits,
                                Map<String, Integer> blockingHits,
                                Map<LoadCategory, Integer> categoryHits,
                                Map<Thread.State, Integer> threadStateHits,
                                int samples,
                                int activeSamples,
                                int idleSamples,
                                int serverHits,
                                long wallNanos,
                                long cpuNanos,
                                long gcCollections,
                                long gcTimeMillis) {}

    private record ServerSnapshot(long usedMemory,
                                  long maxMemory,
                                  double[] tps,
                                  double mspt,
                                  double processCpu,
                                  double systemCpu,
                                  double mainThreadCpu,
                                  int loadedChunks,
                                  int entities,
                                  int items,
                                  int monsters,
                                  int animals,
                                  int displays,
                                  int armorStands,
                                  int projectiles,
                                  List<WorldLoad> worldLoads,
                                  Map<String, TaskLoad> pendingTasks,
                                  Map<String, Integer> entityTypes) {}

    private record TaskLoad(int total, int sync, int async) {}

    private record Finding(Severity severity, String title, String evidence, String recommendation) {}

    private record GcSnapshot(long collections, long timeMillis) {}

    private record WorldLoad(String name, int chunks, int entities, int items,
                             int monsters, int displays, int players) {
        private int weight() {
            return entities * 4 + items * 5 + monsters * 4 + displays * 8 + chunks + players * 10;
        }
    }
}
