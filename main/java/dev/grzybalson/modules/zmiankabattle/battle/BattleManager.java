package dev.grzybalson.modules.zmiankabattle.battle;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import dev.grzybalson.modules.zmiankabattle.ZmiankaBattle;
import dev.grzybalson.modules.zmiankabattle.gui.BattleGuiHolder;
import dev.grzybalson.modules.zmiankabattle.util.SoundResolver;
import dev.grzybalson.modules.zmiankabattle.util.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BattleManager implements Listener {

    private final ZmiankaBattle plugin;
    private final Map<UUID, Battle> activeByPlayer = new HashMap<>();

    public BattleManager(ZmiankaBattle plugin) {
        this.plugin = plugin;
    }

    public boolean isInBattle(UUID playerId) {
        return activeByPlayer.containsKey(playerId);
    }

    public void stopAll() {
        for (Battle b : activeByPlayer.values().toArray(new Battle[0])) {
            b.forceCancelAndRefund();
        }
        activeByPlayer.clear();
    }

    void register(Battle battle) {
        activeByPlayer.put(battle.inviterId(), battle);
        activeByPlayer.put(battle.invitedId(), battle);
    }

    void unregister(Battle battle) {
        activeByPlayer.remove(battle.inviterId());
        activeByPlayer.remove(battle.invitedId());
    }

    public boolean startBattle(Player inviter, Player invited, double amount) {
        FileConfiguration cfg = plugin.getConfig();

        if (inviter == null || invited == null) return false;
        if (isInBattle(inviter.getUniqueId()) || isInBattle(invited.getUniqueId())) {
            sendErr(inviter, cfg.getString("messages.errors.already-battle"));
            sendErr(invited, cfg.getString("messages.errors.already-battle"));
            return false;
        }

        if (!inviter.getWorld().equals(invited.getWorld())) {
            sendErr(invited, cfg.getString("messages.errors.different-world"));
            return false;
        }

        if (!plugin.ensureEconomy()) {
            String ecoMsg = cfg.getString("messages.errors.economy-unavailable",
                    "&cEkonomia jeszcze się ładuje. Spróbuj za chwilę.");
            sendErr(inviter, ecoMsg);
            sendErr(invited, ecoMsg);
            return false;
        }

        Economy eco = plugin.getEconomy();
        if (eco == null) {
            String ecoMsg = cfg.getString("messages.errors.economy-unavailable",
                    "&cEkonomia jeszcze się ładuje. Spróbuj za chwilę.");
            sendErr(inviter, ecoMsg);
            sendErr(invited, ecoMsg);
            return false;
        }

        if (!eco.has(inviter, amount)) {
            sendErr(inviter, cfg.getString("messages.errors.no-money"), amount);
            return false;
        }
        if (!eco.has(invited, amount)) {
            sendErr(invited, cfg.getString("messages.errors.no-money"), amount);
            sendErr(inviter, cfg.getString("messages.errors.other-no-money"));
            return false;
        }

        EconomyResponse r1 = eco.withdrawPlayer(inviter, amount);
        if (!r1.transactionSuccess()) {
            sendErr(inviter, cfg.getString("messages.errors.no-money"), amount);
            return false;
        }

        EconomyResponse r2 = eco.withdrawPlayer(invited, amount);
        if (!r2.transactionSuccess()) {
            eco.depositPlayer(inviter, amount); // refund inviter
            sendErr(invited, cfg.getString("messages.errors.no-money"), amount);
            sendErr(inviter, cfg.getString("messages.errors.other-no-money"));
            return false;
        }

        Battle battle = new Battle(plugin, this, inviter.getUniqueId(), invited.getUniqueId(), amount);
        register(battle);
        battle.start();

        String started = cfg.getString("messages.battle-started", "&aBitwa start!");
        String money = Text.formatMoney(amount);
        inviter.sendMessage(Text.color(Text.apply(started, Map.of("{ilość}", money, "{ilosc}", money))));
        invited.sendMessage(Text.color(Text.apply(started, Map.of("{ilość}", money, "{ilosc}", money))));

        plugin.sounds().play(inviter, SoundResolver.Type.SUCCESS);
        plugin.sounds().play(invited, SoundResolver.Type.SUCCESS);
        return true;
    }

    public void payout(UUID winnerId, UUID loserId, double amount) {
        Economy eco = plugin.getEconomy();
        if (eco == null && plugin.ensureEconomy()) {
            eco = plugin.getEconomy();
        }
        if (eco == null) {
            plugin.getLogger().severe("Brak provider'a ekonomii podczas payout. Nie mogę wypłacić nagrody " + (amount * 2.0));
            return;
        }

        OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerId);
        eco.depositPlayer(winner, amount * 2.0);
    }

    public void refundBoth(UUID p1, UUID p2, double amount) {
        Economy eco = plugin.getEconomy();
        if (eco == null && plugin.ensureEconomy()) {
            eco = plugin.getEconomy();
        }
        if (eco == null) {
            plugin.getLogger().severe("Brak provider'a ekonomii podczas refundu. Nie mogę oddać środków.");
            return;
        }

        eco.depositPlayer(Bukkit.getOfflinePlayer(p1), amount);
        eco.depositPlayer(Bukkit.getOfflinePlayer(p2), amount);
    }

    public Battle getBattle(UUID playerId) {
        return activeByPlayer.get(playerId);
    }

    private void sendErr(Player p, String msg) {
        if (p == null || msg == null) return;
        p.sendMessage(Text.color(msg));
        plugin.sounds().play(p, SoundResolver.Type.ERROR);
    }

    private void sendErr(Player p, String msg, double amount) {
        if (p == null || msg == null) return;
        String money = Text.formatMoney(amount);
        p.sendMessage(Text.color(Text.apply(msg, Map.of("{ilość}", money, "{ilosc}", money))));
        plugin.sounds().play(p, SoundResolver.Type.ERROR);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // The battle task keeps rolling after logout; stakes were withdrawn at start.
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof BattleGuiHolder holder)) return;
        Battle b = holder.getBattle();
        if (b == null || b.isFinished()) return;

        // jeśli gracz zamknął GUI w trakcie losowania – otwórz ponownie
        if (e.getPlayer() instanceof Player p) {
            Bukkit.getScheduler().runTaskLater(plugin.holly(), () -> {
                Battle current = activeByPlayer.get(p.getUniqueId());
                if (p.isOnline() && current != null && !current.isFinished()) {
                    p.openInventory(current.inventory());
                }
            }, 1L);
        }
    }
}
