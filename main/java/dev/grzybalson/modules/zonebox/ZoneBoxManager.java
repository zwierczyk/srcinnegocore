package dev.grzybalson.modules.zonebox;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.customitems.items.CudownaLatarnia;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.managers.MultiActionBarService;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZoneBoxManager implements Listener {

    private static final String ZONEBOX_RESOURCE = "addons/zonebox/zonebox.yml";
    private static final String BLOCKS_ECONOMY_RESOURCE = "addons/economy/blockseconomy.yml";
    private static final long ACTION_BAR_THROTTLE_MILLIS = 1000L;

    private final HollyCore plugin;
    private final File zoneBoxFile;
    private final File blocksEconomyFile;
    private final BreakPermissionChecker breakPermissionChecker;
    private Predicate<Location> generatorVolumeCheck = location -> false;

    private YamlConfiguration zoneBoxConfig;
    private YamlConfiguration blocksEconomyConfig;
    private Set<Material> allowedBlocks = EnumSet.noneOf(Material.class);
    private List<String> allowedPlaceRegions = new ArrayList<>();
    private Map<Material, Double> blockPrices = new EnumMap<>(Material.class);
    private boolean blockEconomyEnabled;
    private boolean payInCreative;
    private boolean actionBarEnabled;
    private long actionBarDurationTicks;
    private String actionBarChannel;
    private String actionBarTemplate;
    private double fortunePercentPerLevel;
    private int fortuneLevelsPerStep;
    private double fortunePercentPerStep;
    private final Map<UUID, Long> lastActionBarSentAt = new HashMap<>();

    public ZoneBoxManager(HollyCore plugin) {
        this.plugin = plugin;
        HollyDataPaths.ensureResource(plugin, ZONEBOX_RESOURCE);
        HollyDataPaths.ensureResource(plugin, BLOCKS_ECONOMY_RESOURCE);
        this.zoneBoxFile = new File(plugin.getDataFolder(), ZONEBOX_RESOURCE);
        this.blocksEconomyFile = new File(plugin.getDataFolder(), BLOCKS_ECONOMY_RESOURCE);
        this.breakPermissionChecker = createBreakPermissionChecker();
        reload();
    }

    public void reload() {
        this.zoneBoxConfig = YamlConfiguration.loadConfiguration(zoneBoxFile);
        this.blocksEconomyConfig = YamlConfiguration.loadConfiguration(blocksEconomyFile);
        this.allowedBlocks = loadAllowedBlocks();
        this.allowedPlaceRegions = loadAllowedPlaceRegions();
        reloadBlockEconomy();
    }

    public void setGeneratorVolumeCheck(Predicate<Location> check) {
        this.generatorVolumeCheck = check != null ? check : location -> false;
    }

    public boolean isInGeneratorVolume(Location location) {
        return location != null && generatorVolumeCheck.test(location);
    }

    public void reloadBlockEconomy() {
        this.blocksEconomyConfig = YamlConfiguration.loadConfiguration(blocksEconomyFile);
        ensureBlockEconomyDefaults();
        this.blockPrices = loadBlockPrices();
        this.blockEconomyEnabled = blocksEconomyConfig.getBoolean("enabled", true);
        this.payInCreative = blocksEconomyConfig.getBoolean("pay-in-creative", false);
        this.actionBarEnabled = blocksEconomyConfig.getBoolean("actionbar.enabled",
            blocksEconomyConfig.getBoolean("messages.payout-enabled", true));
        this.actionBarDurationTicks = Math.max(10L, blocksEconomyConfig.getLong("actionbar.duration-ticks", 40L));
        this.actionBarChannel = blocksEconomyConfig.getString("actionbar.channel", "blockeconomy");
        if (this.actionBarChannel == null || this.actionBarChannel.isBlank()) {
            this.actionBarChannel = "blockeconomy";
        }
        this.actionBarTemplate = blocksEconomyConfig.getString("actionbar.message",
            blocksEconomyConfig.getString("messages.payout",
                "&2$ &fWykopałeś: &#92FF74+<money>$&8, &7Oraz &8⛃ &fZarobek na minutę wynosi: &#92FF74<per_minute>$"));
        this.fortunePercentPerLevel = blocksEconomyConfig.getDouble("fortune.percent-per-level", 3.0D);
        this.fortuneLevelsPerStep = Math.max(1, blocksEconomyConfig.getInt("fortune.levels-per-step", 1));
        this.fortunePercentPerStep = Math.max(0.0D, blocksEconomyConfig.getDouble("fortune.percent-per-step", 3.0D));
    }

    public boolean addAllowedBlock(Material material) {
        if (material == null || !material.isBlock()) {
            return false;
        }

        List<String> configuredBlocks = new ArrayList<>(zoneBoxConfig.getStringList("allowed-blocks"));
        String materialName = material.name();
        for (String configuredBlock : configuredBlocks) {
            if (configuredBlock.equalsIgnoreCase(materialName)) {
                return false;
            }
        }

        configuredBlocks.add(materialName);
        zoneBoxConfig.set("allowed-blocks", configuredBlocks);
        try {
            zoneBoxConfig.save(zoneBoxFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac configu zonebox: " + exception.getMessage());
            return false;
        }

        allowedBlocks.add(material);
        return true;
    }

    /**
     * Dodaje bloki z generatorow do zonebox allowed-blocks (pamiec + config).
     * @return ile nowych materialow zapisano
     */
    public int syncGeneratorBlocks(Iterable<Material> materials) {
        if (materials == null) {
            return 0;
        }

        List<String> configuredBlocks = new ArrayList<>(zoneBoxConfig.getStringList("allowed-blocks"));
        Set<String> existing = new java.util.HashSet<>();
        for (String configured : configuredBlocks) {
            if (configured != null && !configured.isBlank()) {
                existing.add(configured.trim().toUpperCase(Locale.ROOT));
            }
        }

        int added = 0;
        for (Material material : materials) {
            if (material == null || !material.isBlock() || material.isAir()) {
                continue;
            }
            String name = material.name();
            if (existing.add(name)) {
                configuredBlocks.add(name);
                allowedBlocks.add(material);
                added++;
            } else {
                allowedBlocks.add(material);
            }
        }

        if (added > 0) {
            zoneBoxConfig.set("allowed-blocks", configuredBlocks);
            try {
                zoneBoxConfig.save(zoneBoxFile);
                plugin.getLogger().info("[ZoneBox] Dodano " + added + " blokow z generatorow do allowed-blocks.");
            } catch (IOException exception) {
                plugin.getLogger().warning("Nie udalo sie zapisac configu zonebox po sync generatorow: " + exception.getMessage());
            }
        }
        return added;
    }

    public boolean isAllowedBlock(Material material) {
        return allowedBlocks.contains(material);
    }

    public boolean isBlockEconomyBlock(Material material) {
        return material != null && isBlockEconomyActive() && blockPrices.containsKey(material);
    }

    public Set<Material> getAllowedBlocks() {
        return Set.copyOf(allowedBlocks);
    }

    public Map<Material, Double> getBlockEconomyPrices() {
        return Map.copyOf(blockPrices);
    }

    public double getBlockPrice(Material material) {
        return material == null ? 0.0D : blockPrices.getOrDefault(material, 0.0D);
    }

    public List<Material> getConfiguredBlockEconomyMaterials() {
        return blockPrices.keySet().stream()
            .sorted(java.util.Comparator.comparing(Enum::name))
            .toList();
    }

    public boolean setBlockPrice(Material material, double price) {
        if (material == null || !material.isBlock() || !MoneyAmountUtil.isFinite(price) || price < 0.0D) {
            return false;
        }
        double rounded = roundMoney(price);
        blockPrices.put(material, rounded);
        blocksEconomyConfig.set("blocks." + material.name(), rounded);
        return saveBlocksEconomyFile();
    }

    public boolean removeBlockPrice(Material material) {
        if (material == null || !blockPrices.containsKey(material)) {
            return false;
        }
        blockPrices.remove(material);
        blocksEconomyConfig.set("blocks." + material.name(), null);
        return saveBlocksEconomyFile();
    }

    public List<Double> getEditorStepValues() {
        List<Double> values = new ArrayList<>();
        for (String raw : blocksEconomyConfig.getStringList("editor-step-values")) {
            try {
                values.add(Double.parseDouble(raw.replace(',', '.')));
            } catch (NumberFormatException ignored) {
            }
        }
        if (values.isEmpty()) {
            return List.of(1000D, 100D, 10D, 1D, 0.1D, 0.01D);
        }
        return values;
    }

    public int getBlockEconomyGuiRows() {
        return Math.max(3, Math.min(6, blocksEconomyConfig.getInt("gui.rows", 6)));
    }

    public String getBlockEconomyGuiTitle() {
        return blocksEconomyConfig.getString("gui.title", "&8Block Economy");
    }

    private boolean saveBlocksEconomyFile() {
        try {
            blocksEconomyConfig.save(blocksEconomyFile);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac blockseconomy: " + exception.getMessage());
            return false;
        }
    }

    private void ensureBlockEconomyDefaults() {
        boolean dirty = false;
        if (!blocksEconomyConfig.contains("gui.title")) {
            blocksEconomyConfig.set("gui.title", "&8Block Economy");
            dirty = true;
        }
        if (!blocksEconomyConfig.contains("gui.rows")) {
            blocksEconomyConfig.set("gui.rows", 6);
            dirty = true;
        }
        if (!blocksEconomyConfig.contains("editor-step-values")) {
            blocksEconomyConfig.set("editor-step-values", List.of("1000", "100", "10", "1", "0.1", "0.01"));
            dirty = true;
        }
        if (!blocksEconomyConfig.contains("actionbar.enabled")) {
            blocksEconomyConfig.set("actionbar.enabled", true);
            dirty = true;
        }
        if (!blocksEconomyConfig.contains("actionbar.duration-ticks")) {
            blocksEconomyConfig.set("actionbar.duration-ticks", 40);
            dirty = true;
        }
        if (!blocksEconomyConfig.contains("actionbar.channel")) {
            blocksEconomyConfig.set("actionbar.channel", "blockeconomy");
            dirty = true;
        }
        if (!blocksEconomyConfig.contains("actionbar.message")) {
            blocksEconomyConfig.set("actionbar.message",
                "&2$ &fWykopałeś: &#92FF74+<money>$&8, &7Oraz &8⛃ &fZarobek na minutę wynosi: &#92FF74<per_minute>$");
            dirty = true;
        }
        if (dirty) {
            saveBlocksEconomyFile();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (CudownaLatarnia.isActiveBeacon(event.getBlock().getLocation())) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Material material = event.getBlock().getType();
        boolean blockEconomyBlock = isBlockEconomyBlock(material);
        boolean inGenerator = isInGeneratorVolume(event.getBlock().getLocation());

        // Bloki z blockeconomy wolno kopac TYLKO w volume generatora.
        if (blockEconomyBlock && !inGenerator) {
            event.setCancelled(true);
            sendActionBar(player, zoneBoxConfig.getString(
                "messages.economy-break-outside-generator",
                "&#FF5C5C✘ &8• &#FF5C5CBloki z ekonomii możesz kopać tylko w generatorze."
            ));
            return;
        }

        if (!inGenerator) {
            if (!breakPermissionChecker.canBreakHere(player, event.getBlock().getLocation())) {
                event.setCancelled(true);
                return;
            }
            if (!isAllowedBlock(material)) {
                event.setCancelled(true);
                sendActionBar(player, zoneBoxConfig.getString("messages.blocked", "&#FF5C5C✘ &8• &#FF5C5CTego bloku nie możesz tutaj wykopać."));
                return;
            }
        }

        double basePrice = blockPrices.getOrDefault(material, 0.0D);
        if (!isBlockEconomyActive() || basePrice <= 0.0D || !inGenerator) {
            return;
        }

        event.setDropItems(false);
        event.setExpToDrop(0);

        double fortuneMultiplier = getFortuneMultiplier(player, player.getInventory().getItemInMainHand());
        double szamanMultiplier = getPerkDropMultiplier(player);
        double turboDropMultiplier = plugin.getTurboDropManager() != null ? plugin.getTurboDropManager().getMultiplier() : 1.0D;
        double petMultiplier = getPetDropMultiplier(player);
        double clanDropMultiplier = getClanBoostDropMultiplier(player);
        double sigilMultiplier = getSigilDropMultiplier(player);
        double scheduledDropMultiplier = dev.grzybalson.modules.bossbar.ScheduledDropService.getMultiplier(plugin, event.getBlock().getLocation());

        double totalMultiplier = fortuneMultiplier * szamanMultiplier * turboDropMultiplier * petMultiplier * clanDropMultiplier * sigilMultiplier * scheduledDropMultiplier;
        var masakra = plugin.getMasakraCustomItemsManager();
        if (masakra != null) totalMultiplier *= masakra.getMiningMultiplier(player,
            player.getInventory().getItemInMainHand(), event.getBlock().getLocation());
        var szafka = plugin.getMasakraSzafkaManager();
        if (szafka != null) totalMultiplier *= 1.0D + szafka.miningBonus(player, event.getBlock().getLocation());
        double payout = roundMoney(basePrice * totalMultiplier);
        if (payout <= 0.0D) {
            return;
        }

        // Skarb: szansa na podwójny drop (osobno od stałego +% Szczęścia).
        if (plugin.getSigilManager() != null) {
            double skarbChance = plugin.getSigilManager().getDropChanceBonus(player);
            if (skarbChance > 0.0D && java.util.concurrent.ThreadLocalRandom.current().nextDouble(100.0D) < skarbChance) {
                payout = roundMoney(payout * 2.0D);
            }
        }

        plugin.getEconomy().addBalance(player.getUniqueId(), payout).exceptionally(throwable -> {
            plugin.getLogger().warning("Nie udalo sie dodac pieniedzy za wykopanie bloku " + material.name() + ": " + throwable.getMessage());
            return false;
        });

        if (actionBarEnabled) {
            double perMinute = roundMoney(payout * 120.0D);
            String money = MoneyAmountUtil.formatCompact(payout);
            String perMinuteText = MoneyAmountUtil.formatCompact(perMinute);
            String message = actionBarTemplate
                .replace("<money_plain>", money)
                .replace("<per_minute_plain>", perMinuteText)
                .replace("<money>", money)
                .replace("<per_minute>", perMinuteText)
                .replace("<material>", material.name())
                .replace("{MONEY}", money)
                .replace("{PER_MINUTE}", perMinuteText)
                .replace("{MATERIAL}", material.name());
            sendMultiBar(player, actionBarChannel, message, actionBarDurationTicks, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Material material = event.getBlockPlaced().getType();

        if (isCudownaLatarniaItem(event.getItemInHand())) {
            return;
        }

        if (isBlockEconomyBlock(material)) {
            event.setCancelled(true);
            applyPlaceDenyCooldown(player, material);
            sendActionBar(player, zoneBoxConfig.getString(
                "messages.economy-place-blocked",
                "&#FF5C5C✘ &8• &#FF5C5CBloków z ekonomii nie można stawiać."
            ));
            return;
        }

        if (!isAllowedBlock(material)) {
            event.setCancelled(true);
            applyPlaceDenyCooldown(player, material);
            sendActionBar(player, zoneBoxConfig.getString(
                "messages.place-blocked",
                "&#FF5C5C✘ &8• &#FF5C5CTego bloku nie możesz tutaj postawić."
            ));
            return;
        }

        if (!allowedPlaceRegions.isEmpty() && !breakPermissionChecker.isInAllowedRegion(event.getBlockPlaced().getLocation(), allowedPlaceRegions)) {
            event.setCancelled(true);
            applyPlaceDenyCooldown(player, material);
            sendActionBar(player, zoneBoxConfig.getString(
                "messages.place-blocked",
                "&#FF5C5C✘ &8• &#FF5C5CTego bloku nie możesz tutaj postawić."
            ));
        }
    }

    private void applyPlaceDenyCooldown(Player player, Material material) {
        if (player == null || material == null || material.isAir()) {
            return;
        }
        int seconds = Math.max(0, zoneBoxConfig.getInt("place-deny-cooldown-seconds", 2));
        if (seconds <= 0) {
            return;
        }
        // Tak samo jak cooldown perły — wizualny cooldown na item w hotbarze.
        player.setCooldown(material, seconds * 20);
    }

    public boolean addAllowedRegion(String regionName) {
        if (regionName == null || regionName.isBlank()) {
            return false;
        }

        List<String> configuredRegions = new ArrayList<>(zoneBoxConfig.getStringList("allowed-place-regions"));
        for (String existing : configuredRegions) {
            if (existing.equalsIgnoreCase(regionName)) {
                return false;
            }
        }

        configuredRegions.add(regionName);
        zoneBoxConfig.set("allowed-place-regions", configuredRegions);
        try {
            zoneBoxConfig.save(zoneBoxFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac configu zonebox: " + exception.getMessage());
            return false;
        }

        reload();
        return true;
    }

    private List<String> loadAllowedPlaceRegions() {
        if (!zoneBoxConfig.contains("allowed-place-regions")) {
            List<String> defaultRegions = List.of("bezelek");
            zoneBoxConfig.set("allowed-place-regions", defaultRegions);
            try {
                zoneBoxConfig.save(zoneBoxFile);
            } catch (IOException ignored) {
            }
            return defaultRegions;
        }
        List<String> list = zoneBoxConfig.getStringList("allowed-place-regions");
        if (list == null || list.isEmpty()) {
            return List.of("bezelek");
        }
        return list;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            sendActionBar(player, zoneBoxConfig.getString(
                "messages.craft-blocked",
                "&#FF5C5C✘ &8• &#FF5C5CCrafting jest wyłączony na tym trybie."
            ));
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        playerToolMultiplierCache.remove(event.getPlayer().getUniqueId());
        lastActionBarSentAt.remove(event.getPlayer().getUniqueId());
    }

    private boolean isCudownaLatarniaItem(ItemStack item) {
        return plugin.isAddonEnabled("anarchiagg")
            && item != null
            && item.getType() == Material.BEACON
            && EventItemRegistry.matches(plugin, "cudownalatarnia", item);
    }

    private BreakPermissionChecker createBreakPermissionChecker() {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return new DefaultBreakPermissionChecker();
        }

        try {
            return new WorldGuardBreakPermissionChecker();
        } catch (LinkageError error) {
            plugin.getLogger().warning("WorldGuard jest wlaczony, ale nie udalo sie podpiac checker'a zonebox: " + error.getMessage());
            return new DefaultBreakPermissionChecker();
        }
    }

    private Set<Material> loadAllowedBlocks() {
        EnumSet<Material> blocks = EnumSet.noneOf(Material.class);
        List<String> configured = new ArrayList<>(zoneBoxConfig.getStringList("allowed-blocks"));
        if (!configured.contains("COBWEB")) {
            configured.add("COBWEB");
            zoneBoxConfig.set("allowed-blocks", configured);
            try {
                zoneBoxConfig.save(zoneBoxFile);
            } catch (IOException ignored) {
            }
        }
        for (String rawBlock : configured) {
            Material material = parseBlockMaterial(rawBlock);
            if (material != null) {
                blocks.add(material);
            }
        }
        return blocks;
    }

    private Map<Material, Double> loadBlockPrices() {
        EnumMap<Material, Double> prices = new EnumMap<>(Material.class);
        ConfigurationSection section = blocksEconomyConfig.getConfigurationSection("blocks");
        if (section == null) {
            return prices;
        }

        for (String key : section.getKeys(false)) {
            Material material = parseBlockMaterial(key);
            double price = section.getDouble(key, 0.0D);
            if (material != null && price >= 0.0D) {
                prices.put(material, price);
            }
        }
        return prices;
    }

    private boolean isBlockEconomyActive() {
        return blockEconomyEnabled && plugin.isAddonEnabled("economy") && plugin.isAddonEnabled("blockseconomy");
    }

    private Material parseBlockMaterial(String rawMaterial) {
        if (rawMaterial == null || rawMaterial.isBlank()) {
            return null;
        }

        Material material = Material.matchMaterial(rawMaterial.trim().toUpperCase(Locale.ROOT));
        if (material == null || !material.isBlock()) {
            plugin.getLogger().warning("Nieprawidlowy blok w zonebox/blockseconomy configu: " + rawMaterial);
            return null;
        }
        return material;
    }

    private static final class CachedToolData {
        final ItemStack toolRef;
        final int fortuneLevel;
        final double multiplier;

        CachedToolData(ItemStack toolRef, int fortuneLevel, double multiplier) {
            this.toolRef = toolRef;
            this.fortuneLevel = fortuneLevel;
            this.multiplier = multiplier;
        }

        boolean matches(ItemStack tool) {
            if (tool == null) return false;
            if (this.toolRef == tool) return true;
            return this.toolRef.getType() == tool.getType()
                && this.fortuneLevel == tool.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS)
                && this.toolRef.isSimilar(tool);
        }
    }

    private final Map<UUID, CachedToolData> playerToolMultiplierCache = new ConcurrentHashMap<>();

    private static final Pattern LORE_COLOR_PATTERN = Pattern.compile("(?i)[&§][0-9a-fk-orx]");
    private static final Pattern LORE_MULTIPLIER_KEYWORD_PATTERN = Pattern.compile("(?i)(?:mnożnik|mnoznik|drop|multiplier)[^0-9xX]*([0-9]+(?:[.,][0-9]+)?)\\s*x");
    private static final Pattern LORE_MULTIPLIER_ANY_X_PATTERN = Pattern.compile("(?i)([0-9]+(?:[.,][0-9]+)?)\\s*x");

    private double getFortuneMultiplier(Player player, ItemStack tool) {
        if (tool == null || tool.getType().isAir()) {
            return 1.0D;
        }

        if (player != null) {
            CachedToolData cached = playerToolMultiplierCache.get(player.getUniqueId());
            if (cached != null && cached.matches(tool)) {
                return cached.multiplier;
            }
        }

        double multiplier = computeFortuneMultiplier(tool);

        if (player != null) {
            playerToolMultiplierCache.put(player.getUniqueId(), new CachedToolData(
                tool.clone(),
                tool.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS),
                multiplier
            ));
        }

        return multiplier;
    }

    private double computeFortuneMultiplier(ItemStack tool) {
        double multiplier = 1.0D;
        boolean foundLoreMultiplier = false;

        if (tool.hasItemMeta()) {
            ItemMeta meta = tool.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                if (lore != null) {
                    for (String line : lore) {
                        if (line == null || line.isEmpty()) continue;
                        if (line.indexOf('x') == -1 && line.indexOf('X') == -1) continue;

                        String translated = ChatColor.translateAlternateColorCodes('&', line);
                        String stripped = ChatColor.stripColor(translated);
                        String cleanLine = LORE_COLOR_PATTERN.matcher(stripped).replaceAll("").trim();

                        Matcher keywordMatcher = LORE_MULTIPLIER_KEYWORD_PATTERN.matcher(cleanLine);
                        if (keywordMatcher.find()) {
                            try {
                                String valStr = keywordMatcher.group(1).replace(',', '.');
                                double parsed = Double.parseDouble(valStr);
                                if (parsed > 0.0D) {
                                    multiplier = parsed;
                                    foundLoreMultiplier = true;
                                    break;
                                }
                            } catch (NumberFormatException ignored) {}
                        } else {
                            Matcher fallbackMatcher = LORE_MULTIPLIER_ANY_X_PATTERN.matcher(cleanLine);
                            if (fallbackMatcher.find()) {
                                try {
                                    String valStr = fallbackMatcher.group(1).replace(',', '.');
                                    double parsed = Double.parseDouble(valStr);
                                    if (parsed > 0.0D) {
                                        multiplier = parsed;
                                        foundLoreMultiplier = true;
                                        break;
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
            }
        }

        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS);
        if (fortuneLevel > 0) {
            double enchantBonus;
            if (blocksEconomyConfig.contains("fortune.percent-per-level")) {
                enchantBonus = 1.0D + (fortuneLevel * fortunePercentPerLevel / 100.0D);
            } else if (fortuneLevelsPerStep == 1) {
                enchantBonus = 1.0D + (fortuneLevel * fortunePercentPerStep / 100.0D);
            } else {
                enchantBonus = 1.0D + (fortuneLevel * 0.03D);
            }

            if (!foundLoreMultiplier) {
                multiplier = enchantBonus;
            } else {
                multiplier *= enchantBonus;
            }
        }

        return Math.max(1.0D, multiplier);
    }

    private double getPerkDropMultiplier(Player player) {
        if (player == null || plugin.getSzamanManager() == null) return 1.0D;
        double value = plugin.getSzamanManager().getPerkValue(player.getUniqueId(), dev.grzybalson.modules.szaman.PerkType.DROP);
        return value > 0.0D ? value : 1.0D;
    }

    private double getSigilDropMultiplier(Player player) {
        if (player == null || plugin.getSigilManager() == null) {
            return 1.0D;
        }
        double multiplier = plugin.getSigilManager().getDropMultiplier(player);
        return multiplier > 0.0D ? multiplier : 1.0D;
    }

    /** Hypestar BOOST_DROP: mnożnik ekonomii z kopania (chance<=0 = zawsze). */
    private double getClanBoostDropMultiplier(Player player) {
        if (player == null || plugin.getClanManager() == null) {
            return 1.0D;
        }
        String tag = plugin.getClanManager().getCachedPlayerClan(player.getUniqueId());
        if (tag == null) {
            return 1.0D;
        }
        var clan = plugin.getClanManager().getCachedClan(tag);
        if (clan == null || plugin.getClanManager().getUpgradeLevel(clan, "BOOST_DROP") <= 0) {
            return 1.0D;
        }
        double amplifier = plugin.getClanManager().getAmplifier(clan, "BOOST_DROP");
        if (amplifier <= 0.0D) {
            return 1.0D;
        }
        double chance = plugin.getClanManager().getChance(clan, "BOOST_DROP");
        if (chance > 0.0D && !dev.grzybalson.anarchiagg.util.ValueUtil.getChance(chance)) {
            return 1.0D;
        }
        return amplifier;
    }

    private double getPetDropMultiplier(Player player) {
        if (player == null) {
            return 1.0D;
        }
        try {
            if (!plugin.isAddonEnabled("anarchiagg")) {
                return 1.0D;
            }
            dev.grzybalson.anarchiagg.AnarchiaGGModuleManager anarchiaManager = plugin.getAnarchiaGGModuleManager();
            if (anarchiaManager == null || anarchiaManager.getPetyHandler() == null) {
                return 1.0D;
            }
            dev.grzybalson.anarchiagg.szafa.pety.PetyHandler petyHandler = anarchiaManager.getPetyHandler();
            if (petyHandler.getDatabase() == null) {
                return 1.0D;
            }
            String activePetKey = petyHandler.getDatabase().getActivePet(player.getUniqueId());
            if (activePetKey != null && !activePetKey.isBlank()) {
                String keyLower = activePetKey.toLowerCase(Locale.ROOT);
                if (keyLower.contains("biznesmenik") || keyLower.contains("biznesmen")) {
                    return 2.0D;
                }
                dev.grzybalson.anarchiagg.szafa.pety.PetyHandler.PetData petData = petyHandler.getPety().get(activePetKey);
                if (petData != null && petData.boosts() != null && petData.boosts().doubleDrop()) {
                    return 2.0D;
                }
            }
            String displayName = petyHandler.getDatabase().getActivePetDisplayName(player.getUniqueId());
            if (displayName != null && displayName.toLowerCase(Locale.ROOT).contains("biznesmenik")) {
                return 2.0D;
            }
        } catch (Throwable ignored) {
        }
        return 1.0D;
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private void sendActionBar(Player player, String message) {
        sendMultiBar(player, "zonebox", message, 40L, true);
    }

    private void sendMultiBar(Player player, String channel, String message, long durationTicks, boolean throttle) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (throttle) {
            long now = System.currentTimeMillis();
            Long lastSent = lastActionBarSentAt.get(player.getUniqueId());
            if (lastSent != null && now - lastSent < ACTION_BAR_THROTTLE_MILLIS) {
                return;
            }
            lastActionBarSentAt.put(player.getUniqueId(), now);
        }
        MultiActionBarService service = MultiActionBarService.get();
        if (service != null) {
            service.send(player, channel, message, durationTicks);
            return;
        }
        player.sendActionBar(ColorUtils.colorizeComponent(message));
    }
}
