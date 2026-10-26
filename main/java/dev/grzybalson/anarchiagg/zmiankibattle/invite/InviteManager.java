package dev.grzybalson.anarchiagg.zmiankibattle.invite;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import dev.grzybalson.anarchiagg.zmiankibattle.ZmiankaBattle;
import dev.grzybalson.anarchiagg.zmiankibattle.util.SoundResolver;
import dev.grzybalson.anarchiagg.zmiankibattle.util.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InviteManager implements Listener {

    private final ZmiankaBattle plugin;
    private final Map<UUID, Entry> invitesByInvited = new HashMap<>();
    /**
     * Indeks zaproszeń wychodzących: inviter -> invited.
     * Dzięki temu łatwo blokujemy wysyłanie wielu bitew naraz.
     */
    private final Map<UUID, UUID> outgoingByInviter = new HashMap<>();

    private static final class Entry {
        Invite invite;
        BukkitTask expireTask;
        Entry(Invite invite, BukkitTask expireTask) {
            this.invite = invite;
            this.expireTask = expireTask;
        }
    }

    public InviteManager(ZmiankaBattle plugin) {
        this.plugin = plugin;
    }

    public void clearAll() {
        for (Entry e : invitesByInvited.values()) {
            if (e.expireTask != null) e.expireTask.cancel();
        }
        invitesByInvited.clear();
        outgoingByInviter.clear();
    }

    public boolean hasInviteFor(Player invited) {
        Entry e = invitesByInvited.get(invited.getUniqueId());
        if (e == null) return false;
        if (e.invite.isExpired()) {
            removeInternal(invited.getUniqueId(), e.invite.inviter());
            return false;
        }
        return true;
    }

    /**
     * Czy gracz jest "zajęty" zaproszeniem (jako inviter lub invited).
     * To dokładnie blokuje sytuację: nie można wysłać kilku bitew naraz.
     */
    public boolean isBusy(UUID playerId) {
        // incoming
        Entry in = invitesByInvited.get(playerId);
        if (in != null) {
            if (in.invite.isExpired()) {
                removeInternal(in.invite.invited(), in.invite.inviter());
            } else {
                return true;
            }
        }

        // outgoing
        UUID invitedId = outgoingByInviter.get(playerId);
        if (invitedId == null) return false;
        Entry e = invitesByInvited.get(invitedId);
        if (e == null) {
            outgoingByInviter.remove(playerId);
            return false;
        }
        if (e.invite.isExpired()) {
            removeInternal(invitedId, playerId);
            return false;
        }
        return true;
    }

    public boolean hasOutgoingInvite(UUID inviterId) {
        return isBusy(inviterId) && outgoingByInviter.containsKey(inviterId);
    }

    /**
     * Tworzy zaproszenie. Zwraca false jeśli inviter/target ma już aktywne zaproszenie.
     */
    public boolean createInvite(Player inviter, Player invited, double amount) {
        FileConfiguration cfg = plugin.getConfig();
        int expireSeconds = cfg.getInt("settings.invite-expire-seconds", 30);
        long expiresAt = System.currentTimeMillis() + expireSeconds * 1000L;

        // blokada wielu zaproszeń naraz (oba kierunki)
        if (isBusy(inviter.getUniqueId()) || isBusy(invited.getUniqueId())) {
            return false;
        }

        // sanity: jeśli target ma już zaproszenie (w praktyce isBusy powyżej powinno złapać)
        Entry prev = invitesByInvited.get(invited.getUniqueId());
        if (prev != null && !prev.invite.isExpired()) {
            return false;
        }

        Invite invite = new Invite(inviter.getUniqueId(), invited.getUniqueId(), amount, expiresAt);
        final String inviterName = inviter.getName();
        final String invitedName = invited.getName();

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin.holly(), () -> {
            Entry current = invitesByInvited.get(invited.getUniqueId());
            if (current == null) return;
            if (current.invite.isExpired()) {
                removeInternal(invited.getUniqueId(), inviter.getUniqueId());
                Player inv = Bukkit.getPlayer(invite.inviter());
                Player ino = Bukkit.getPlayer(invite.invited());

                if (inv != null) {
                    inv.sendMessage(Text.color(Text.apply(cfg.getString("messages.invite-expired-inviter"), Map.of(
                            "{player}", invitedName
                    ))));
                    plugin.sounds().play(inv, SoundResolver.Type.ERROR);
                }
                if (ino != null) {
                    ino.sendMessage(Text.color(Text.apply(cfg.getString("messages.invite-expired-invited"), Map.of(
                            "{player}", inviterName,
                            "{playername}", inviterName
                    ))));
                    plugin.sounds().play(ino, SoundResolver.Type.ERROR);
                }
            }
        }, expireSeconds * 20L);

        invitesByInvited.put(invited.getUniqueId(), new Entry(invite, task));
        outgoingByInviter.put(inviter.getUniqueId(), invited.getUniqueId());

        // wiadomość do invited
        List<String> lines = cfg.getStringList("messages.invite-received");
        String money = Text.formatMoney(amount);
        Map<String, String> ph = new HashMap<>();
        ph.put("{player}", inviter.getName());
        ph.put("{playername}", inviter.getName());
        ph.put("{ilość}", money);
        ph.put("{ilosc}", money);

        if (!lines.isEmpty()) {
            invited.sendMessage(Text.color(Text.apply(lines.get(0), ph)));
        }
        if (lines.size() >= 2) {
            String raw = Text.apply(lines.get(1), ph);
            String cmd = "/zmianki bitwa zakceptuj " + inviter.getName();
            invited.sendMessage(buildClickableLine(raw, cmd));
        }

        // wiadomość do inviter
        String sent = cfg.getString("messages.invite-sent", "&aWysłałeś zaproszenie do &f{player}&a.");
        inviter.sendMessage(Text.color(Text.apply(sent, Map.of(
                "{player}", invited.getName(),
                "{playername}", invited.getName(),
                "{ilość}", money,
                "{ilosc}", money
        ))));

        plugin.sounds().play(inviter, SoundResolver.Type.SUCCESS);
        return true;
    }

    public Invite consumeInvite(Player invited, String inviterName) {
        Entry e = invitesByInvited.get(invited.getUniqueId());
        if (e == null) return null;
        if (e.invite.isExpired()) {
            removeInternal(invited.getUniqueId(), e.invite.inviter());
            return null;
        }

        Player inviter = Bukkit.getPlayer(e.invite.inviter());
        if (inviter == null || !inviter.isOnline()) return null;
        if (!inviter.getName().equalsIgnoreCase(inviterName)) return null;

        // usuń i oddaj
        if (e.expireTask != null) e.expireTask.cancel();
        removeInternal(invited.getUniqueId(), e.invite.inviter());
        return e.invite;
    }

    /**
     * Zwraca zaproszenie bez zdejmowania go z mapy.
     */
    public Invite peekInvite(Player invited, UUID inviterId) {
        Entry e = invitesByInvited.get(invited.getUniqueId());
        if (e == null) return null;
        if (e.invite.isExpired()) {
            if (e.expireTask != null) e.expireTask.cancel();
            removeInternal(invited.getUniqueId(), e.invite.inviter());
            return null;
        }
        if (!e.invite.inviter().equals(inviterId)) return null;
        return e.invite;
    }

    /**
     * Zdejmuje zaproszenie z mapy.
     */
    public Invite takeInvite(Player invited, UUID inviterId) {
        Entry e = invitesByInvited.get(invited.getUniqueId());
        if (e == null) return null;
        if (e.invite.isExpired()) {
            if (e.expireTask != null) e.expireTask.cancel();
            removeInternal(invited.getUniqueId(), e.invite.inviter());
            return null;
        }
        if (!e.invite.inviter().equals(inviterId)) return null;

        if (e.expireTask != null) e.expireTask.cancel();
        removeInternal(invited.getUniqueId(), e.invite.inviter());
        return e.invite;
    }

    private void removeInternal(UUID invitedId, UUID inviterId) {
        Entry removed = invitesByInvited.remove(invitedId);
        if (removed != null && removed.expireTask != null) removed.expireTask.cancel();
        if (inviterId != null) outgoingByInviter.remove(inviterId);
    }

    /**
     * Anuluje zaproszenie jeśli gracz jest w nie zaangażowany (inviter lub invited).
     * Używane np. gdy ktoś opuści dozwolony region WG zanim dojdzie do startu bitwy.
     */
    public boolean cancelDueToLeavingRegion(UUID playerId, String regionId) {
        boolean cancelled = false;
        if (playerId == null) return false;

        FileConfiguration cfg = plugin.getConfig();
        String msg = cfg.getString("messages.invite-cancelled-left-region",
                "&#FF0000✘ &8Ξ &cZaproszenie na zmianki zostało anulowane, ponieważ &f{player} &copuścił region &f{region}&c.");

        // 1) gracz jako invited
        Entry asInvited = invitesByInvited.get(playerId);
        if (asInvited != null && !asInvited.invite.isExpired()) {
            Invite inv = asInvited.invite;
            if (asInvited.expireTask != null) asInvited.expireTask.cancel();
            removeInternal(inv.invited(), inv.inviter());
            cancelled = true;

            String leaverName = nameOf(playerId);
            String region = (regionId == null || regionId.isBlank()) ? "spawn" : regionId;

            var ph = Map.of(
                    "{player}", leaverName,
                    "{playername}", leaverName,
                    "{region}", region
            );

            Player inviter = Bukkit.getPlayer(inv.inviter());
            Player invited = Bukkit.getPlayer(inv.invited());
            if (msg != null && !msg.isBlank()) {
                if (inviter != null) inviter.sendMessage(Text.color(Text.apply(msg, ph)));
                if (invited != null) invited.sendMessage(Text.color(Text.apply(msg, ph)));
            }
            if (inviter != null) plugin.sounds().play(inviter, SoundResolver.Type.ERROR);
            if (invited != null) plugin.sounds().play(invited, SoundResolver.Type.ERROR);
        }

        // 2) gracz jako inviter
        UUID invitedId = outgoingByInviter.get(playerId);
        if (invitedId != null) {
            Entry entry = invitesByInvited.get(invitedId);
            if (entry != null && !entry.invite.isExpired() && entry.invite.inviter().equals(playerId)) {
                Invite inv = entry.invite;
                if (entry.expireTask != null) entry.expireTask.cancel();
                removeInternal(inv.invited(), inv.inviter());
                cancelled = true;

                String leaverName = nameOf(playerId);
                String region = (regionId == null || regionId.isBlank()) ? "spawn" : regionId;

                var ph = Map.of(
                        "{player}", leaverName,
                        "{playername}", leaverName,
                        "{region}", region
                );

                Player inviter = Bukkit.getPlayer(inv.inviter());
                Player invited = Bukkit.getPlayer(inv.invited());
                if (msg != null && !msg.isBlank()) {
                    if (inviter != null) inviter.sendMessage(Text.color(Text.apply(msg, ph)));
                    if (invited != null) invited.sendMessage(Text.color(Text.apply(msg, ph)));
                }
                if (inviter != null) plugin.sounds().play(inviter, SoundResolver.Type.ERROR);
                if (invited != null) plugin.sounds().play(invited, SoundResolver.Type.ERROR);
            } else {
                // posprzątaj indeks jeśli nie ma już wpisu
                outgoingByInviter.remove(playerId);
            }
        }

        return cancelled;
    }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        return Bukkit.getOfflinePlayer(id).getName() != null ? Bukkit.getOfflinePlayer(id).getName() : id.toString();
    }


    private Component buildClickableLine(String raw, String runCommand) {
        // Najprościej i najczytelniej: cała linia klikalna (w praktyce spełnia "KLIKNIJ TUTAJ")
        return Text.component(raw)
                .clickEvent(ClickEvent.runCommand(runCommand))
                .hoverEvent(HoverEvent.showText(Text.component("&7Kliknij aby zaakceptować")));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();

        // jeśli ktoś wychodzi jako invited – usuń jego zaproszenie
        Entry entry = invitesByInvited.get(u);
        if (entry != null) {
            removeInternal(u, entry.invite.inviter());
        }

        // jeśli wychodzi inviter – usuń wszystkie zaproszenia, które on wysłał
        invitesByInvited.entrySet().removeIf(en -> {
            boolean match = en.getValue().invite.inviter().equals(u);
            if (match && en.getValue().expireTask != null) en.getValue().expireTask.cancel();
            if (match) outgoingByInviter.remove(u);
            return match;
        });

        // w razie gdyby był index bez entry
        outgoingByInviter.remove(u);
    }
}
