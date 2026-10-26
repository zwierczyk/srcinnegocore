package dev.grzybalson.modules.zmiankabattle.global;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import dev.grzybalson.modules.zmiankabattle.ZmiankaBattle;
import dev.grzybalson.modules.zmiankabattle.util.SoundResolver;
import dev.grzybalson.modules.zmiankabattle.util.Text;
import dev.grzybalson.modules.zmiankabattle.util.WorldGuardRegionGate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GlobalChallengeManager implements Listener {

    private final ZmiankaBattle plugin;

    private final Map<UUID, GlobalChallenge> byChallenger = new HashMap<>();
    private final Map<UUID, GlobalChallenge> byInteractionEntity = new HashMap<>();
    private final Map<UUID, Long> clickDebounce = new HashMap<>();

    private enum CloseReason {
        STARTED,
        EXPIRED,
        CHALLENGER_LEFT,
        CANCELLED
    }

    private static final class GlobalChallenge {
        final UUID challengerId;
        final double amount;
        int ticksElapsed = 0;
        int durationTicks;
        int notifyEveryTicks;
        double radius;
        double hologramYOffset;
        int hologramUpdateIntervalTicks;
        int loopPeriodTicks;
        int nextHologramUpdateTick;
        int nextNotifyTick;
        double hologramMinMoveDistanceSq;
        Location lastHologramBase;

        BukkitTask task;
        TextDisplay hologram;
        Interaction clickbox;

        GlobalChallenge(UUID challengerId, double amount) {
            this.challengerId = challengerId;
            this.amount = amount;
        }
    }

    public GlobalChallengeManager(ZmiankaBattle plugin) {
        this.plugin = plugin;
    }

    public void clearAll() {
        for (GlobalChallenge gc : byChallenger.values().toArray(new GlobalChallenge[0])) {
            close(gc, CloseReason.CANCELLED);
        }
        byChallenger.clear();
        byInteractionEntity.clear();
        clickDebounce.clear();
    }

    public boolean hasOpenChallenge(UUID challengerId) {
        GlobalChallenge gc = byChallenger.get(challengerId);
        return gc != null;
    }

    /**
     * Anuluje otwarte globalne zmianki (jeśli istnieją) dla danego challengera.
     * Przydatne np. gdy gracz opuści dozwolony region WorldGuard.
     */
    public boolean cancelDueToLeavingRegion(UUID challengerId, String regionId) {
        if (challengerId == null) return false;
        GlobalChallenge gc = byChallenger.get(challengerId);
        if (gc == null) return false;

        close(gc, CloseReason.CANCELLED);

        Player p = Bukkit.getPlayer(challengerId);
        if (p != null && p.isOnline()) {
            String region = (regionId == null || regionId.isBlank()) ? "spawn" : regionId;
            String msg = plugin.getConfig().getString(
                    "messages.global-cancelled-left-region",
                    "&#FF0000✘ &8Ξ &cGlobalne zmianki zostały anulowane, ponieważ opuściłeś region &f{region}&c."
            );
            if (msg != null && !msg.isBlank()) {
                p.sendMessage(Text.color(Text.apply(msg, Map.of("{region}", region))));
            }
            plugin.sounds().play(p, SoundResolver.Type.ERROR);
        }
        return true;
    }

    public boolean openGlobal(Player challenger, double amount) {
        if (challenger == null || !challenger.isOnline()) return false;
        if (byChallenger.containsKey(challenger.getUniqueId())) return false;

        GlobalChallenge gc = new GlobalChallenge(challenger.getUniqueId(), amount);
        gc.radius = Math.max(1.0D, plugin.getConfig().getDouble("settings.global.radius", 8.0D));
        gc.durationTicks = Math.max(20, plugin.getConfig().getInt("settings.global.duration-seconds", 60) * 20);
        gc.notifyEveryTicks = Math.max(20, plugin.getConfig().getInt("settings.global.notify-interval-seconds", 10) * 20);
        gc.hologramYOffset = plugin.getConfig().getDouble("settings.global.hologram-y-offset", 2.35D);
        gc.hologramUpdateIntervalTicks = Math.max(2, plugin.getConfig().getInt("settings.global.hologram-update-interval-ticks", 6));
        gc.loopPeriodTicks = Math.max(2, plugin.getConfig().getInt("settings.global.loop-period-ticks", 4));
        gc.nextHologramUpdateTick = 0;
        gc.nextNotifyTick = gc.notifyEveryTicks;
        double minMove = Math.max(0.0D, plugin.getConfig().getDouble("settings.global.hologram-min-move-distance", 0.35D));
        gc.hologramMinMoveDistanceSq = minMove * minMove;

        byChallenger.put(challenger.getUniqueId(), gc);
        ensureHologram(gc, challenger);
        sendOpened(challenger, amount);

        // natychmiastowe powiadomienie + potem co X sekund
        notifyNearby(gc, challenger, true);

        gc.task = Bukkit.getScheduler().runTaskTimer(plugin.holly(), () -> tick(gc), gc.loopPeriodTicks, gc.loopPeriodTicks);
        return true;
    }

    private void tick(GlobalChallenge gc) {
        Player challenger = Bukkit.getPlayer(gc.challengerId);
        if (challenger == null || !challenger.isOnline()) {
            close(gc, CloseReason.CHALLENGER_LEFT);
            return;
        }

        if (plugin.battles().isInBattle(gc.challengerId)) {
            close(gc, CloseReason.STARTED);
            return;
        }

        if (gc.hologram == null || !gc.hologram.isValid() || gc.clickbox == null || !gc.clickbox.isValid()) {
            ensureHologram(gc, challenger);
            gc.lastHologramBase = null; // wymuś pierwszy teleport po respawnie encji
        }

        gc.ticksElapsed = Math.min(gc.durationTicks, gc.ticksElapsed + gc.loopPeriodTicks);

        if (gc.ticksElapsed >= gc.nextHologramUpdateTick) {
            updateHologramPositionIfNeeded(gc, challenger);
            gc.nextHologramUpdateTick = gc.ticksElapsed + gc.hologramUpdateIntervalTicks;
        }

        while (gc.ticksElapsed >= gc.nextNotifyTick && gc.nextNotifyTick < gc.durationTicks) {
            notifyNearby(gc, challenger, false);
            gc.nextNotifyTick += gc.notifyEveryTicks;
        }

        if (gc.ticksElapsed >= gc.durationTicks) {
            close(gc, CloseReason.EXPIRED);
        }
    }

    private void ensureHologram(GlobalChallenge gc, Player challenger) {
        Location loc = challenger.getLocation().clone().add(0.0, gc.hologramYOffset, 0.0);
        World world = challenger.getWorld();

        if (gc.hologram == null || !gc.hologram.isValid()) {
            Entity ent = world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
            if (ent instanceof TextDisplay td) {
                gc.hologram = td;
                td.setPersistent(false);
                td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                td.setText(hologramText(gc.amount));
            } else {
                ent.remove();
            }
        }

        if (gc.clickbox == null || !gc.clickbox.isValid()) {
            Entity ent = world.spawnEntity(loc.clone().add(0.0, -0.35, 0.0), EntityType.INTERACTION);
            if (ent instanceof Interaction interaction) {
                gc.clickbox = interaction;
                interaction.setPersistent(false);
                interaction.setInteractionWidth(1.8f);
                interaction.setInteractionHeight(1.4f);
                interaction.setResponsive(true);
                byInteractionEntity.put(interaction.getUniqueId(), gc);
            } else {
                ent.remove();
            }
        }

    }

    private void updateHologramPositionIfNeeded(GlobalChallenge gc, Player challenger) {
        Location base = challenger.getLocation().clone().add(0.0, gc.hologramYOffset, 0.0);

        boolean shouldTeleport = false;
        if (gc.lastHologramBase == null) {
            shouldTeleport = true;
        } else if (!gc.lastHologramBase.getWorld().equals(base.getWorld())) {
            shouldTeleport = true;
        } else if (gc.lastHologramBase.distanceSquared(base) >= gc.hologramMinMoveDistanceSq) {
            shouldTeleport = true;
        }

        if (!shouldTeleport) return;

        if (gc.hologram != null && gc.hologram.isValid()) {
            gc.hologram.teleport(base);
        }
        if (gc.clickbox != null && gc.clickbox.isValid()) {
            gc.clickbox.teleport(base.clone().add(0.0, -0.35, 0.0));
        }
        gc.lastHologramBase = base;
    }

    private String hologramText(double amount) {
        String money = Text.formatMoney(amount);
        return Text.color(
                "&3&lOTWARTA GRA\n" +
                "&bWyzywam Cię na pojedynek o &3" + money + "$\n" +
                "&e&lKLIKNIJ"
        );
    }

    private void notifyNearby(GlobalChallenge gc, Player challenger, boolean first) {
        double radiusSq = gc.radius * gc.radius;
        String money = Text.formatMoney(gc.amount);
        int secondsLeft = Math.max(0, (gc.durationTicks - gc.ticksElapsed) / 20);

        List<String> lines = plugin.getConfig().getStringList("messages.global-nearby");
        if (lines == null || lines.isEmpty()) {
            lines = List.of(
                    "&3&lOTWARTA GRA &8- &f{player}",
                    "&bPojedynek o &3{ilosc}$ &7(koniec za {seconds}s)",
                    "&eKliknij hologram nad nim."
            );
        }

        for (Entity entity : challenger.getNearbyEntities(gc.radius, gc.radius, gc.radius)) {
            if (!(entity instanceof Player p)) continue;
            if (p.getUniqueId().equals(challenger.getUniqueId())) continue;
            if (!p.getWorld().equals(challenger.getWorld())) continue;
            if (p.getLocation().distanceSquared(challenger.getLocation()) > radiusSq) continue;

            Map<String, String> ph = new HashMap<>();
            ph.put("{player}", challenger.getName());
            ph.put("{playername}", challenger.getName());
            ph.put("{ilość}", money);
            ph.put("{ilosc}", money);
            ph.put("{seconds}", String.valueOf(secondsLeft));

            for (String line : lines) {
                p.sendMessage(Text.color(Text.apply(line, ph)));
            }

            if (first) {
                plugin.sounds().play(p, SoundResolver.Type.CLICK);
            }
        }
    }

    private void sendOpened(Player challenger, double amount) {
        int durationSec = Math.max(1, plugin.getConfig().getInt("settings.global.duration-seconds", 60));
        int radius = (int) Math.round(Math.max(1.0D, plugin.getConfig().getDouble("settings.global.radius", 8.0D)));
        String msg = plugin.getConfig().getString("messages.global-opened",
                "&aWłączyłeś &fglobalne zmianki&a o &f{ilosc}$&a. " +
                "&7Przez {seconds}s gracze w promieniu {radius} kratek mogą kliknąć hologram.");
        challenger.sendMessage(Text.color(Text.apply(msg, Map.of(
                "{ilość}", Text.formatMoney(amount),
                "{ilosc}", Text.formatMoney(amount),
                "{seconds}", String.valueOf(durationSec),
                "{radius}", String.valueOf(radius)
        ))));
        plugin.sounds().play(challenger, SoundResolver.Type.SUCCESS);
    }

    private void sendExpired(Player challenger, double amount) {
        String msg = plugin.getConfig().getString("messages.global-expired",
                "&cGlobalne zmianki o &f{ilosc}$&c wygasły.");
        challenger.sendMessage(Text.color(Text.apply(msg, Map.of(
                "{ilość}", Text.formatMoney(amount),
                "{ilosc}", Text.formatMoney(amount)
        ))));
        plugin.sounds().play(challenger, SoundResolver.Type.ERROR);
    }

    private void close(GlobalChallenge gc, CloseReason reason) {
        byChallenger.remove(gc.challengerId);

        if (gc.task != null) {
            gc.task.cancel();
            gc.task = null;
        }

        if (gc.clickbox != null) {
            byInteractionEntity.remove(gc.clickbox.getUniqueId());
            if (gc.clickbox.isValid()) gc.clickbox.remove();
            gc.clickbox = null;
        }

        if (gc.hologram != null) {
            if (gc.hologram.isValid()) gc.hologram.remove();
            gc.hologram = null;
        }

        if (reason == CloseReason.EXPIRED) {
            Player challenger = Bukkit.getPlayer(gc.challengerId);
            if (challenger != null && challenger.isOnline()) {
                sendExpired(challenger, gc.amount);
            }
        }
    }

    private boolean debounce(Player player) {
        long now = System.currentTimeMillis();
        Long prev = clickDebounce.get(player.getUniqueId());
        clickDebounce.put(player.getUniqueId(), now);
        return prev != null && (now - prev) < 250L;
    }

    private void tryJoinByClickingPlayer(Player clicker, Player clickedPlayer) {
        GlobalChallenge gc = byChallenger.get(clickedPlayer.getUniqueId());
        if (gc == null) return;
        tryJoin(clicker, gc);
    }

    private void tryJoinByClickingHologram(Player clicker, Entity clickedEntity) {
        GlobalChallenge gc = byInteractionEntity.get(clickedEntity.getUniqueId());
        if (gc == null) return;
        tryJoin(clicker, gc);
    }

    private void tryJoin(Player clicker, GlobalChallenge gc) {
        if (clicker == null || !clicker.isOnline()) return;
        if (debounce(clicker)) return;

        Player challenger = Bukkit.getPlayer(gc.challengerId);
        if (challenger == null || !challenger.isOnline()) {
            close(gc, CloseReason.CHALLENGER_LEFT);
            return;
        }

        if (clicker.getUniqueId().equals(challenger.getUniqueId())) return;

        WorldGuardRegionGate.CheckResult acceptRegion = plugin.regions().check(WorldGuardRegionGate.Action.ACCEPT, clicker, challenger);
        if (!acceptRegion.ok()) {
            sendRegionFailure(clicker, acceptRegion);
            return;
        }

        double radius = gc.radius;
        if (!clicker.getWorld().equals(challenger.getWorld())
                || clicker.getLocation().distanceSquared(challenger.getLocation()) > (radius * radius)) {
            String msg = plugin.getConfig().getString("messages.errors.global-too-far",
                    "&cMusisz być bliżej gracza (&f" + (int) radius + " kratek&c).");
            clicker.sendMessage(Text.color(msg));
            plugin.sounds().play(clicker, SoundResolver.Type.ERROR);
            return;
        }

        if (plugin.battles().isInBattle(clicker.getUniqueId()) || plugin.battles().isInBattle(challenger.getUniqueId())) {
            sendError(clicker, "messages.errors.already-battle",
                    "&cKtoś z was jest już w bitwie.");
            return;
        }

        // Nie mieszamy globala z klasycznymi invite'ami.
        if (plugin.invites().isBusy(clicker.getUniqueId())) {
            sendError(clicker, "messages.errors.invite-busy",
                    "&cMasz już aktywne zaproszenie na zmianki.");
            return;
        }
        if (plugin.invites().isBusy(challenger.getUniqueId())) {
            sendError(clicker, "messages.errors.target-busy",
                    "&cTen gracz jest teraz zajęty.", Map.of(
                            "{player}", challenger.getName(),
                            "{playername}", challenger.getName()
                    ));
            return;
        }

        if (!plugin.ensureEconomy()) {
            sendError(clicker, "messages.errors.economy-unavailable",
                    "&cEkonomia jeszcze się ładuje. Spróbuj za chwilę.");
            return;
        }

        if (plugin.getEconomy() == null || !plugin.getEconomy().has(challenger, gc.amount)) {
            // Gospodarz nie ma już kasy -> zamknij globala
            sendError(clicker, "messages.errors.other-no-money",
                    "&cPrzeciwnik nie ma pieniędzy.");
            close(gc, CloseReason.EXPIRED);
            return;
        }

        boolean started = plugin.battles().startBattle(challenger, clicker, gc.amount);
        if (started) {
            close(gc, CloseReason.STARTED);
        }
    }

    private void sendRegionFailure(Player p, WorldGuardRegionGate.CheckResult result) {
        if (p == null) return;
        switch (result.reason()) {
            case WORLDGUARD_UNAVAILABLE -> sendError(p, "messages.errors.worldguard-unavailable",
                    "&cWorldGuard jest niedostępny. Nie można sprawdzić regionu.");
            case REGION_NOT_FOUND -> sendError(p, "messages.errors.region-not-found",
                    "&#FF0000✘ &8Ξ &cRegion WorldGuard &f{region} &cnie istnieje w tym świecie.", Map.of(
                            "{region}", result.region()
                    ));
            case OTHER_NOT_IN_REGION -> sendError(p, "messages.errors.other-not-in-region",
                    "&cGracz &f{player}&c musi być w regionie &f{region}&c.", Map.of(
                            "{player}", (result.otherPlayerName() == null ? "?" : result.otherPlayerName()),
                            "{playername}", (result.otherPlayerName() == null ? "?" : result.otherPlayerName()),
                            "{region}", result.region()
                    ));
            case ACTOR_NOT_IN_REGION -> sendError(p, "messages.errors.region-not-allowed",
                    "&cMożesz użyć tej akcji tylko w regionie &f{region}&c.", Map.of(
                            "{region}", result.region()
                    ));
            default -> {
            }
        }
    }

    private void sendError(Player p, String path, String def) {
        sendError(p, path, def, Map.of());
    }

    private void sendError(Player p, String path, String def, Map<String, String> placeholders) {
        if (p == null) return;
        String msg = plugin.getConfig().getString(path, def);
        p.sendMessage(Text.color(Text.apply(msg, placeholders)));
        plugin.sounds().play(p, SoundResolver.Type.ERROR);
    }

    @EventHandler
    public void onInteractPlayer(PlayerInteractEntityEvent e) {
        // Akcept globala tylko przez hologram/clickbox (Interaction), nie przez klikanie gracza.
        if (e.getRightClicked() instanceof Player) return;
        tryJoinByClickingHologram(e.getPlayer(), e.getRightClicked());
    }

    @EventHandler
    public void onInteractAt(PlayerInteractAtEntityEvent e) {
        // Akcept globala tylko przez hologram/clickbox (Interaction), nie przez klikanie gracza.
        if (e.getRightClicked() instanceof Player) return;
        tryJoinByClickingHologram(e.getPlayer(), e.getRightClicked());
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player clicker)) return;

        if (e.getEntity() instanceof Player) {
            return;
        }

        if (byInteractionEntity.containsKey(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
            tryJoinByClickingHologram(clicker, e.getEntity());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        GlobalChallenge gc = byChallenger.get(e.getPlayer().getUniqueId());
        if (gc != null) {
            close(gc, CloseReason.CHALLENGER_LEFT);
        }
        clickDebounce.remove(e.getPlayer().getUniqueId());
    }
}
