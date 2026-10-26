package dev.grzybalson.modules.zmiankabattle.battle;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import dev.grzybalson.modules.zmiankabattle.ZmiankaBattle;
import dev.grzybalson.modules.zmiankabattle.gui.BattleGui;
import dev.grzybalson.modules.zmiankabattle.util.SoundResolver;
import dev.grzybalson.modules.zmiankabattle.util.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Battle {

    private final ZmiankaBattle plugin;
    private final BattleManager manager;
    private final UUID inviterId;
    private final UUID invitedId;
    private final double amount;

    private final int minPercent;
    private final int maxPercent;

    private final int finalInviterPercent;
    private final int finalInvitedPercent;

    private int currentInviterPercent;
    private int currentInvitedPercent;

    private final Inventory inventory;
    private final BattleGui gui;

    private BossBar createBar;
    private BossBar winBar;

    private BukkitTask task;
    private BukkitTask winBarTask;

    private boolean finished = false;

    private enum Stage { ROLL_INVITER, PAUSE, ROLL_INVITED, FINISH }
    private Stage stage = Stage.ROLL_INVITER;

    private int stepTicks = 2;
    private int updatesTotal = 1;
    private int inviterUpdatesLeft = 1;
    private int invitedUpdatesLeft = 1;
    private int pauseTicksLeft = 0;
    private int rollSoundEveryUpdates = 2;
    private int inviterRollUpdateCounter = 0;
    private int invitedRollUpdateCounter = 0;

    public Battle(ZmiankaBattle plugin, BattleManager manager, UUID inviterId, UUID invitedId, double amount) {
        this.plugin = plugin;
        this.manager = manager;
        this.inviterId = inviterId;
        this.invitedId = invitedId;
        this.amount = amount;

        FileConfiguration cfg = plugin.getConfig();
        this.minPercent = cfg.getInt("battle.percent-range.min", -24);
        this.maxPercent = cfg.getInt("battle.percent-range.max", 52);

        int[] rolled = rollNonTie(minPercent, maxPercent);
        this.finalInviterPercent = rolled[0];
        this.finalInvitedPercent = rolled[1];

        this.currentInviterPercent = maxPercent;
        this.currentInvitedPercent = maxPercent;

        this.gui = new BattleGui(plugin, this);
        this.inventory = gui.createInventory();
    }

    private int[] rollNonTie(int min, int max) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int a;
        int b;
        int tries = 0;
        do {
            a = rnd.nextInt(min, max + 1);
            b = rnd.nextInt(min, max + 1);
            tries++;
        } while (a == b && tries < 10);

        if (a == b) {
            // awaryjnie: jeśli nadal remis, rozstrzygnij minimalną korektą
            b = (b == max) ? b - 1 : b + 1;
        }
        return new int[]{a, b};
    }

    public void start() {
        Player inviter = Bukkit.getPlayer(inviterId);
        Player invited = Bukkit.getPlayer(invitedId);
        if (inviter == null || invited == null) {
            forceCancelAndRefund();
            return;
        }

        FileConfiguration cfg = plugin.getConfig();

        // bossbar create alert (na czas losowań)
        String money = Text.formatMoney(amount);
        Map<String, String> ph = new HashMap<>();
        ph.put("{inviter_name}", inviter.getName());
        ph.put("{invited_name}", invited.getName());
        ph.put("{ilość}", money);
        ph.put("{ilosc}", money);

        BarColor c = plugin.bossBars().readColor(cfg, "bossbars.create-alert.color", BarColor.RED);
        String title = Text.apply(cfg.getString("bossbars.create-alert.title", ""), ph);
        this.createBar = plugin.bossBars().createGlobalBar(title, c);

        // otwórz GUI dla obu
        inviter.openInventory(inventory);
        invited.openInventory(inventory);

        // start animacji od MAX
        gui.updateInviterPercent(maxPercent);
        gui.updateInvitedPercent(maxPercent);

        // wspieraj też stare / błędnie wklejone klucze bez "settings." (compat)
        this.stepTicks = Math.max(1, readIntCompat(cfg,
                "settings.roll-step-ticks",
                "roll-step-ticks",
                2));
        int pauseBetween = Math.max(0, readIntCompat(cfg,
                "settings.roll-pause-between-players-ticks",
                "roll-pause-between-players-ticks",
                20));
        this.rollSoundEveryUpdates = Math.max(1, readIntCompat(cfg,
                "settings.roll-sound-every-updates",
                "roll-sound-every-updates",
                2));

        // Przywrócona animacja jak w oryginale (bez osobnego configa na sekundy).
        // Domyślnie zachowujemy tempo odpowiadające ~5s na gracza przy roll-step-ticks=2.
        final int defaultRollSecondsPerPlayer = 5;
        this.updatesTotal = Math.max(1, (defaultRollSecondsPerPlayer * 20) / stepTicks);
        this.inviterUpdatesLeft = updatesTotal;
        this.invitedUpdatesLeft = updatesTotal;

        this.stage = Stage.ROLL_INVITER;
        this.pauseTicksLeft = pauseBetween;

        task = Bukkit.getScheduler().runTaskTimer(plugin.holly(), () -> {
            if (finished) return;

            switch (stage) {
                case ROLL_INVITER -> {
                    int next = nextStepDown(currentInviterPercent, finalInviterPercent, inviterUpdatesLeft);
                    if (next != currentInviterPercent) {
                        currentInviterPercent = next;
                        gui.updateInviterPercent(currentInviterPercent);
                        playRollSoundThrottled(true);
                    }
                    inviterUpdatesLeft = Math.max(0, inviterUpdatesLeft - 1);

                    if (currentInviterPercent == finalInviterPercent) {
                        stage = Stage.PAUSE;
                    }
                }
                case PAUSE -> {
                    pauseTicksLeft -= stepTicks;
                    if (pauseTicksLeft <= 0) {
                        stage = Stage.ROLL_INVITED;
                    }
                }
                case ROLL_INVITED -> {
                    int next = nextStepDown(currentInvitedPercent, finalInvitedPercent, invitedUpdatesLeft);
                    if (next != currentInvitedPercent) {
                        currentInvitedPercent = next;
                        gui.updateInvitedPercent(currentInvitedPercent);
                        playRollSoundThrottled(false);
                    }
                    invitedUpdatesLeft = Math.max(0, invitedUpdatesLeft - 1);

                    if (currentInvitedPercent == finalInvitedPercent) {
                        stage = Stage.FINISH;
                        finishNormal();
                    }
                }
                case FINISH -> {
                    // noop
                }
            }
        }, 1L, stepTicks);
    }


    /**
     * Countdown od max -> target, ale gdy dystans jest duży to "skacze" po kilka,
     * żeby nie schodziło pojedynczo (np. 52 -> -20).
     *
     * updatesLeft - ile update'ów jeszcze zostało w tym etapie (łącznie z obecnym krokiem).
     */
    private int nextStepDown(int current, int target, int updatesLeft) {
        if (current <= target) return target;
        int remaining = current - target;
        int step = (int) Math.ceil(remaining / (double) Math.max(1, updatesLeft));
        if (step < 1) step = 1;
        int next = current - step;
        return Math.max(next, target);
    }

    private void playRollSoundThrottled(boolean inviterStage) {
        if (rollSoundEveryUpdates <= 1) {
            playToBoth(SoundResolver.Type.ROLL);
            return;
        }

        if (inviterStage) {
            inviterRollUpdateCounter++;
            if ((inviterRollUpdateCounter % rollSoundEveryUpdates) == 0) {
                playToBoth(SoundResolver.Type.ROLL);
            }
        } else {
            invitedRollUpdateCounter++;
            if ((invitedRollUpdateCounter % rollSoundEveryUpdates) == 0) {
                playToBoth(SoundResolver.Type.ROLL);
            }
        }
    }

    private int readIntCompat(FileConfiguration cfg, String primaryPath, String legacyPath, int def) {
        if (cfg.isInt(primaryPath)) return cfg.getInt(primaryPath);
        if (cfg.contains(primaryPath)) return cfg.getInt(primaryPath, def);
        if (cfg.isInt(legacyPath)) return cfg.getInt(legacyPath);
        if (cfg.contains(legacyPath)) return cfg.getInt(legacyPath, def);
        return def;
    }

    private void playToBoth(SoundResolver.Type type) {

        Player a = Bukkit.getPlayer(inviterId);
        Player b = Bukkit.getPlayer(invitedId);
        if (a != null) plugin.sounds().play(a, type);
        if (b != null) plugin.sounds().play(b, type);
    }

    private void finishNormal() {
        if (finished) return;
        finished = true;

        if (task != null) task.cancel();
        task = null;

        if (createBar != null) {
            plugin.bossBars().remove(createBar);
            createBar = null;
        }

        // winner
        UUID winnerId = (finalInviterPercent > finalInvitedPercent) ? inviterId : invitedId;
        UUID loserId = winnerId.equals(inviterId) ? invitedId : inviterId;

        // payout
        manager.payout(winnerId, loserId, amount);

        // sounds
        Player winner = Bukkit.getPlayer(winnerId);
        Player loser = Bukkit.getPlayer(loserId);
        if (winner != null) plugin.sounds().play(winner, SoundResolver.Type.WIN);
        if (loser != null) plugin.sounds().play(loser, SoundResolver.Type.LOSE);

        // message
        FileConfiguration cfg = plugin.getConfig();
        String ended = cfg.getString("messages.battle-ended", "");
        String money = Text.formatMoney(amount);

        String winnerName = nameOf(winnerId);
        String loserName = nameOf(loserId);
        int wPercent = winnerId.equals(inviterId) ? finalInviterPercent : finalInvitedPercent;
        int lPercent = loserId.equals(inviterId) ? finalInviterPercent : finalInvitedPercent;

        Map<String, String> ph = new HashMap<>();
        ph.put("{winer}", winnerName);
        ph.put("{loser}", loserName);
        ph.put("{winer_percent}", String.valueOf(wPercent));
        ph.put("{loser_percent}", String.valueOf(lPercent));
        ph.put("{ilość}", money);
        ph.put("{ilosc}", money);

        Player inviter = Bukkit.getPlayer(inviterId);
        Player invited = Bukkit.getPlayer(invitedId);
        if (ended != null && !ended.isBlank()) {
            if (inviter != null) inviter.sendMessage(Text.color(Text.apply(ended, ph)));
            if (invited != null) invited.sendMessage(Text.color(Text.apply(ended, ph)));
        }

        Bukkit.getScheduler().runTask(plugin.holly(), () -> showWinBarProgressive(ph));

        // nie zamykaj GUI od razu - zamknij po delay (lub nigdy)
        scheduleGuiClose();

        manager.unregister(this);
    }

    public void handleQuit(UUID leaverId) {
        if (finished) return;

        UUID winnerId = leaverId.equals(inviterId) ? invitedId : inviterId;
        UUID loserId = leaverId;

        // payout dla zwycięzcy
        manager.payout(winnerId, loserId, amount);

        finished = true;
        if (task != null) task.cancel();
        task = null;

        if (createBar != null) {
            plugin.bossBars().remove(createBar);
            createBar = null;
        }

        FileConfiguration cfg = plugin.getConfig();
        String money = Text.formatMoney(amount);
        Map<String, String> ph = new HashMap<>();
        ph.put("{winer}", nameOf(winnerId));
        ph.put("{loser}", nameOf(loserId));
        ph.put("{ilość}", money);
        ph.put("{ilosc}", money);

        Bukkit.getScheduler().runTask(plugin.holly(), () -> showWinBarProgressive(ph));

        Player other = Bukkit.getPlayer(winnerId);
        if (other != null) plugin.sounds().play(other, SoundResolver.Type.WIN);

        // nie zamykaj GUI od razu
        scheduleGuiClose();

        manager.unregister(this);
    }

    public void forceCancelAndRefund() {
        if (finished) return;
        finished = true;

        if (task != null) task.cancel();
        task = null;

        if (createBar != null) {
            plugin.bossBars().remove(createBar);
            createBar = null;
        }
        if (winBar != null) {
            plugin.bossBars().remove(winBar);
            winBar = null;
        }
        if (winBarTask != null) {
            winBarTask.cancel();
            winBarTask = null;
        }

        manager.refundBoth(inviterId, invitedId, amount);

        // w cancelu możemy zamknąć po krótkim delay (żeby nie zostawiać "martwego" GUI)
        scheduleGuiClose();

        manager.unregister(this);
    }

    private void showWinBarProgressive(Map<String, String> ph) {
        FileConfiguration cfg = plugin.getConfig();

        BarColor col = plugin.bossBars().readColor(cfg, "bossbars.win-alert.color", BarColor.RED);
        String winTitle = Text.apply(cfg.getString("bossbars.win-alert.title", ""), ph);

        if (winBar != null) {
            plugin.bossBars().remove(winBar);
            winBar = null;
        }
        if (winBarTask != null) {
            winBarTask.cancel();
            winBarTask = null;
        }

        winBar = plugin.bossBars().createGlobalBar(winTitle, col);
        winBar.setProgress(1.0);

        int duration = Math.max(1, cfg.getInt("settings.win-bossbar-duration-ticks", 40));
        final int[] t = {0};
        winBarTask = Bukkit.getScheduler().runTaskTimer(plugin.holly(), () -> {
            if (winBar == null) {
                if (winBarTask != null) winBarTask.cancel();
                winBarTask = null;
                return;
            }

            double progress = Math.max(0.0, 1.0 - (t[0] / (double) duration));
            winBar.setProgress(progress);
            t[0]++;

            // Pozwól pokazać klientowi stan 0.0 przez 1 tick, dopiero potem usuń bar.
            if (t[0] > duration) {
                plugin.bossBars().remove(winBar);
                winBar = null;
                if (winBarTask != null) winBarTask.cancel();
                winBarTask = null;
            }
        }, 0L, 1L);
    }

    private void scheduleGuiClose() {
        FileConfiguration cfg = plugin.getConfig();
        int delay = cfg.getInt("settings.gui-close-after-finish-ticks", 60);
        if (delay < 0) return;

        Bukkit.getScheduler().runTaskLater(plugin.holly(), () -> {
            Player inviter = Bukkit.getPlayer(inviterId);
            Player invited = Bukkit.getPlayer(invitedId);
            if (inviter != null && inviter.getOpenInventory().getTopInventory().equals(inventory)) {
                inviter.closeInventory();
            }
            if (invited != null && invited.getOpenInventory().getTopInventory().equals(inventory)) {
                invited.closeInventory();
            }
        }, delay);
    }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        return op.getName() != null ? op.getName() : id.toString();
    }

    public UUID inviterId() { return inviterId; }
    public UUID invitedId() { return invitedId; }
    public double amount() { return amount; }

    public int minPercent() { return minPercent; }
    public int maxPercent() { return maxPercent; }

    public int finalInviterPercent() { return finalInviterPercent; }
    public int finalInvitedPercent() { return finalInvitedPercent; }

    public Inventory inventory() { return inventory; }
    public boolean isFinished() { return finished; }

    /**
     * Natychmiast kończy bitwę (bez czekania na animację), używając już wylosowanych wartości.
     * Przydatne np. gdy gracz opuści dozwolony region.
     */
    public void finishInstant() {
        if (finished) return;

        // pokaż od razu finalne wartości w GUI
        this.currentInviterPercent = this.finalInviterPercent;
        this.currentInvitedPercent = this.finalInvitedPercent;
        gui.updateInviterPercent(this.currentInviterPercent);
        gui.updateInvitedPercent(this.currentInvitedPercent);

        finishNormal();
    }
}
