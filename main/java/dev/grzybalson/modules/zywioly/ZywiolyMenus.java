package dev.grzybalson.modules.zywioly;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ZywiolyMenus implements Listener {

    private static final int BACK_SLOT = 22;
    private static final int ROLL_SLOT = 13;
    private static final int[] ROLL_DELAYS = {
        2, 2, 2, 2, 3, 3, 3, 4, 4, 5, 6, 8, 10, 12, 16, 20
    };

    private final HollyCore plugin;
    private final ZywiolyManager manager;
    private final ZywiolyConfig config;
    private final Map<UUID, Integer> pages = new ConcurrentHashMap<>();
    private final Map<UUID, RollView> rollViews = new ConcurrentHashMap<>();
    private final Set<UUID> reopenRoll = ConcurrentHashMap.newKeySet();
    private BukkitTask refreshTask;

    ZywiolyMenus(HollyCore plugin, ZywiolyManager manager, ZywiolyConfig config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
        int seconds = config.guiRefreshSeconds();
        if (seconds > 0) {
            refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshMains, seconds * 20L, seconds * 20L);
        }
    }

    void openMain(Player player, int page) {
        openMain(player, page, true);
    }

    void openMain(Player player, int page, boolean playSound) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (rollViews.containsKey(player.getUniqueId())) {
            return;
        }
        int size = config.guiRows() * 9;
        if (isMainOpen(player) && player.getOpenInventory().getTopInventory().getSize() == size) {
            renderMain(player, player.getOpenInventory().getTopInventory(), page);
            return;
        }
        ZywiolyHolder holder = new ZywiolyHolder(ZywiolyHolder.Type.MAIN, null, false);
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtils.colorizeComponent(config.mainTitle()));
        holder.attach(inventory);
        renderMain(player, inventory, page);
        player.openInventory(inventory);
        if (playSound) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
        }
    }

    private void renderMain(Player player, Inventory inventory, int page) {
        int size = inventory.getSize();
        // Zawsze nakładaj template od nowa — inaczej znikają szkła (np. sloty prev/next).
        inventory.clear();
        fillCoreTemplate(inventory);

        List<Integer> gameSlots = config.gameSlots(size);
        int prevSlot = config.previousSlot(size);
        int nextSlot = config.nextSlot(size);

        List<ZywiolyGame> games = manager.openGames();
        int perPage = Math.max(1, gameSlots.size());
        int maxPage = Math.max(0, games.isEmpty() ? 0 : (games.size() - 1) / perPage);
        int safePage = Math.max(0, Math.min(page, maxPage));
        pages.put(player.getUniqueId(), safePage);
        int from = safePage * perPage;
        Map<Integer, String> bySlot = new HashMap<>();
        for (int i = 0; i < perPage && from + i < games.size(); i++) {
            ZywiolyGame game = games.get(from + i);
            int slot = gameSlots.get(i);
            boolean own = game.creatorUuid().equals(player.getUniqueId());
            inventory.setItem(slot, gameItem(game, own));
            bySlot.put(slot, game.id());
        }
        for (int slot : config.createSlots(size)) {
            inventory.setItem(slot, createItem());
        }
        if (safePage > 0) {
            inventory.setItem(prevSlot, named(config.previousMaterial(), config.previousName(), List.of(), 0));
        }
        if (safePage < maxPage) {
            inventory.setItem(nextSlot, named(config.nextMaterial(), config.nextName(), List.of(), 0));
        }
        if (inventory.getHolder() instanceof ZywiolyHolder holder) {
            holder.setGamesBySlot(bySlot);
        }
    }

    private static boolean isMainOpen(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof ZywiolyHolder holder
            && holder.type() == ZywiolyHolder.Type.MAIN;
    }

    void openPick(Player player, double amount, boolean joining, String gameId) {
        ZywiolyHolder holder = new ZywiolyHolder(ZywiolyHolder.Type.PICK, gameId, joining);
        Inventory inventory = Bukkit.createInventory(holder, 27, ColorUtils.colorizeComponent(config.pickTitle()));
        holder.attach(inventory);
        fillCoreTemplate(inventory);
        for (ZywiolyElement element : ZywiolyElement.values()) {
            inventory.setItem(config.elementSlot(element), elementItem(element));
        }
        inventory.setItem(BACK_SLOT, named(config.backMaterial(), config.backName(), config.backLore(), 0));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
    }

    void startRoll(Player creator, Player opponent, ZywiolyGame game, ZywiolyElement winner, Runnable onDone) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            openRoll(creator, game);
            openRoll(opponent, game);
            ItemStack[] spinning = new ItemStack[ZywiolyElement.values().length];
            ItemStack[] winners = new ItemStack[ZywiolyElement.values().length];
            int winnerIndex = 0;
            for (int i = 0; i < ZywiolyElement.values().length; i++) {
                ZywiolyElement element = ZywiolyElement.values()[i];
                spinning[i] = rollSpinItem(element, game);
                winners[i] = rollWinnerItem(element, game);
                if (element == winner) {
                    winnerIndex = i;
                }
            }
            AtomicBoolean done = new AtomicBoolean(false);
            scheduleRollStep(creator, opponent, game, spinning, winners, winnerIndex, 0, done, onDone);
        });
    }

    void refreshMains() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (rollViews.containsKey(player.getUniqueId())) {
                continue;
            }
            if (isMainOpen(player)) {
                renderMain(player, player.getOpenInventory().getTopInventory(),
                    pages.getOrDefault(player.getUniqueId(), 0));
            }
        }
    }

    void endRoll(UUID uuid) {
        if (uuid == null) {
            return;
        }
        rollViews.remove(uuid);
        reopenRoll.remove(uuid);
    }

    void endRoll(UUID first, UUID second) {
        endRoll(first);
        endRoll(second);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof ZywiolyHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder.type() == ZywiolyHolder.Type.MAIN) {
            handleMainClick(player, holder, slot);
            return;
        }
        if (holder.type() == ZywiolyHolder.Type.PICK) {
            handlePickClick(player, slot);
            return;
        }
        if (holder.type() == ZywiolyHolder.Type.ROLL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ZywiolyHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pages.remove(uuid);
        rollViews.remove(uuid);
        reopenRoll.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        RollView view = rollViews.get(uuid);
        if (view == null) {
            return;
        }
        // Na world pvp nie otwieraj ponownie — gracze się biją.
        if (config.isNoGuiWorld(player)) {
            rollViews.remove(uuid);
            reopenRoll.remove(uuid);
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ZywiolyHolder holder)
            || holder.type() != ZywiolyHolder.Type.ROLL) {
            return;
        }
        if (reopenRoll.contains(uuid)) {
            return;
        }
        reopenRoll.add(uuid);
        Bukkit.getScheduler().runTask(plugin, () -> {
            reopenRoll.remove(uuid);
            if (!player.isOnline() || !rollViews.containsKey(uuid) || config.isNoGuiWorld(player)) {
                return;
            }
            openRoll(player, view.game());
            if (view.lastItem() != null) {
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top.getHolder() instanceof ZywiolyHolder roll && roll.type() == ZywiolyHolder.Type.ROLL) {
                    top.setItem(ROLL_SLOT, view.lastItem().clone());
                }
            }
        });
    }

    private void handleMainClick(Player player, ZywiolyHolder holder, int slot) {
        int size = player.getOpenInventory().getTopInventory().getSize();
        if (config.createSlots(size).contains(slot)) {
            manager.beginCreate(player);
            return;
        }
        if (slot == config.previousSlot(size)) {
            openMain(player, pages.getOrDefault(player.getUniqueId(), 0) - 1, false);
            return;
        }
        if (slot == config.nextSlot(size)) {
            openMain(player, pages.getOrDefault(player.getUniqueId(), 0) + 1, false);
            return;
        }
        Map<Integer, String> bySlot = holder.gamesBySlot();
        String gameId = bySlot.get(slot);
        if (gameId == null) {
            return;
        }
        ZywiolyGame own = null;
        for (ZywiolyGame game : manager.openGames()) {
            if (game.id().equals(gameId)) {
                own = game;
                break;
            }
        }
        if (own != null && own.creatorUuid().equals(player.getUniqueId())) {
            manager.cancelOwnGame(player, gameId);
            return;
        }
        manager.startPickForJoin(player, gameId);
    }

    private void handlePickClick(Player player, int slot) {
        if (slot == BACK_SLOT) {
            manager.cancelPick(player);
            return;
        }
        for (ZywiolyElement element : ZywiolyElement.values()) {
            if (config.elementSlot(element) == slot) {
                manager.selectElement(player, element);
                return;
            }
        }
    }

    private void openRoll(Player player, ZywiolyGame game) {
        if (player == null || !player.isOnline() || game == null) {
            return;
        }
        if (config.isNoGuiWorld(player)) {
            rollViews.remove(player.getUniqueId());
            return;
        }
        ZywiolyHolder holder = new ZywiolyHolder(ZywiolyHolder.Type.ROLL, game.id(), false);
        Inventory inventory = Bukkit.createInventory(holder, 27, ColorUtils.colorizeComponent(config.rollTitle()));
        holder.attach(inventory);
        fillRollTemplate(inventory);
        fillRollChoices(inventory, game);
        RollView previous = rollViews.get(player.getUniqueId());
        if (previous != null && previous.lastItem() != null) {
            inventory.setItem(ROLL_SLOT, previous.lastItem().clone());
        }
        rollViews.put(player.getUniqueId(), new RollView(game, previous == null ? null : previous.lastItem()));
        player.openInventory(inventory);
    }

    private void fillRollChoices(Inventory inventory, ZywiolyGame game) {
        if (game == null) {
            return;
        }
        int creatorSlot = config.rollCreatorSlot();
        int opponentSlot = config.rollOpponentSlot();
        if (creatorSlot >= 0 && creatorSlot < inventory.getSize() && game.creatorElement() != null) {
            inventory.setItem(creatorSlot, rollChoiceItem(game.creatorElement(), game.creatorName(), game));
        }
        if (opponentSlot >= 0 && opponentSlot < inventory.getSize() && game.opponentElement() != null) {
            inventory.setItem(opponentSlot, rollChoiceItem(game.opponentElement(), game.opponentName(), game));
        }
    }

    private void scheduleRollStep(
        Player creator,
        Player opponent,
        ZywiolyGame game,
        ItemStack[] spinning,
        ItemStack[] winners,
        int winnerIndex,
        int step,
        AtomicBoolean done,
        Runnable onDone
    ) {
        if (step >= ROLL_DELAYS.length) {
            if (done.compareAndSet(false, true)) {
                ItemStack finalItem = winners[winnerIndex];
                showRoll(creator, game, finalItem);
                showRoll(opponent, game, finalItem);
                onDone.run();
            }
            return;
        }
        int shown = ThreadLocalRandom.current().nextInt(spinning.length);
        ItemStack item;
        if (step == ROLL_DELAYS.length - 1) {
            item = winners[winnerIndex];
        } else {
            item = spinning[shown];
        }
        showRoll(creator, game, item);
        showRoll(opponent, game, item);
        playRollSound(creator);
        playRollSound(opponent);
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            scheduleRollStep(creator, opponent, game, spinning, winners, winnerIndex, step + 1, done, onDone), ROLL_DELAYS[step]);
    }

    private void showRoll(Player player, ZywiolyGame game, ItemStack item) {
        if (player == null || !player.isOnline() || item == null || game == null) {
            return;
        }
        if (config.isNoGuiWorld(player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        rollViews.put(uuid, new RollView(game, item.clone()));
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof ZywiolyHolder holder) || holder.type() != ZywiolyHolder.Type.ROLL) {
            openRoll(player, game);
            top = player.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof ZywiolyHolder again) || again.type() != ZywiolyHolder.Type.ROLL) {
                return;
            }
        }
        top.setItem(ROLL_SLOT, item.clone());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7F, 1.4F);
    }

    private void playRollSound(Player player) {
        if (player != null && player.isOnline() && !config.isNoGuiWorld(player)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.55F, 1.6F);
        }
    }

    private ItemStack createItem() {
        return named(config.createMaterial(), config.createName(), config.createLore(), 0);
    }

    private ItemStack gameItem(ZywiolyGame game, boolean own) {
        Map<String, String> placeholders = Map.of(
            "%nick%", game.creatorName() == null ? "?" : game.creatorName(),
            "%kwota%", ZywiolyManager.format(game.amount())
        );
        Material material = own ? config.ownGameMaterial() : config.gameMaterial();
        String name = replace(own ? config.ownGameName() : config.gameName(), placeholders);
        List<String> lore = replace(own ? config.ownGameLore() : config.gameLore(), placeholders);
        ItemStack item = named(material, name, lore, 0);
        try {
            if (item.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(game.creatorUuid()));
                item.setItemMeta(meta);
            }
        } catch (Throwable ignored) {
        }
        return item;
    }

    private ItemStack elementItem(ZywiolyElement element) {
        return named(
            config.elementMaterial(element),
            config.elementName(element),
            config.elementLore(element),
            config.elementModel(element)
        );
    }

    private ItemStack rollSpinItem(ZywiolyElement element, ZywiolyGame game) {
        Map<String, String> placeholders = rollPlaceholders(element, null, game);
        return named(
            config.elementMaterial(element),
            config.elementName(element),
            replace(config.rollSpinningLore(), placeholders),
            config.elementModel(element)
        );
    }

    private ItemStack rollWinnerItem(ZywiolyElement element, ZywiolyGame game) {
        Map<String, String> placeholders = rollPlaceholders(element, null, game);
        ItemStack item = named(
            config.elementMaterial(element),
            config.elementName(element),
            replace(config.rollWinnerLore(), placeholders),
            config.elementModel(element)
        );
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack rollChoiceItem(ZywiolyElement element, String nick, ZywiolyGame game) {
        Map<String, String> placeholders = rollPlaceholders(element, nick, game);
        return named(
            config.elementMaterial(element),
            config.elementName(element),
            replace(config.rollChoiceLore(), placeholders),
            config.elementModel(element)
        );
    }

    private Map<String, String> rollPlaceholders(ZywiolyElement element, String nick, ZywiolyGame game) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%element%", config.elementDisplay(element));
        placeholders.put("%nick%", nick == null ? "?" : nick);
        placeholders.put("%kwota%", ZywiolyManager.format(game == null ? 0.0D : game.amount()));
        return placeholders;
    }

    private ItemStack named(Material material, String name, List<String> lore, int model) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(ColorUtils.colorizeComponent(name == null ? " " : name).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        if (lore != null) {
            for (String line : lore) {
                if (line == null) {
                    continue;
                }
                if (line.isBlank()) {
                    lines.add(Component.text(" ").decoration(TextDecoration.ITALIC, false));
                    continue;
                }
                lines.add(ColorUtils.createLoreComponent(line));
            }
        }
        meta.lore(lines);
        if (model > 0) {
            meta.setCustomModelData(model);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void fillCoreTemplate(Inventory inventory) {
        if (plugin.getGuiFactory() != null) {
            plugin.getGuiFactory().applyDefaultTemplate(inventory);
            return;
        }
        fillRollTemplate(inventory);
    }

    private void fillRollTemplate(Inventory inventory) {
        HollyDataPaths.ensureResource(plugin, "guis/zywioly.yml");
        File file = new File(plugin.getDataFolder(), "guis/zywioly.yml");
        YamlConfiguration yaml;
        if (file.exists()) {
            yaml = YamlConfiguration.loadConfiguration(file);
        } else {
            InputStream stream = plugin.getResource("guis/zywioly.yml");
            if (stream == null) {
                return;
            }
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        int size = inventory.getSize();
        ConfigurationSection section = yaml.getConfigurationSection("template" + size);
        if (section == null) {
            section = yaml.getConfigurationSection(size >= 45 ? "template45" : "template27");
        }
        if (section == null) {
            return;
        }
        String emptyName = yaml.getString("backgroundItemName", " ");
        for (String key : section.getKeys(false)) {
            Material material;
            try {
                material = Material.valueOf(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ItemStack pane = named(material, emptyName, List.of(), 0);
            for (int slot : section.getIntegerList(key)) {
                if (slot >= 0 && slot < size) {
                    inventory.setItem(slot, pane.clone());
                }
            }
        }
    }

    private static String replace(String text, Map<String, String> placeholders) {
        String out = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static List<String> replace(List<String> lines, Map<String, String> placeholders) {
        List<String> out = new ArrayList<>();
        if (lines == null) {
            return out;
        }
        for (String line : lines) {
            out.add(replace(line, placeholders));
        }
        return out;
    }

    private static final class RollView {
        private final ZywiolyGame game;
        private final ItemStack lastItem;

        private RollView(ZywiolyGame game, ItemStack lastItem) {
            this.game = game;
            this.lastItem = lastItem;
        }

        private ZywiolyGame game() {
            return game;
        }

        private ItemStack lastItem() {
            return lastItem;
        }
    }
}
