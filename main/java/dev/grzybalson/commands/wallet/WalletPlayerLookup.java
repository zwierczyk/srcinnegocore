package dev.grzybalson.commands.wallet;

import com.mongodb.client.model.Filters;
import dev.grzybalson.HollyCore;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

final class WalletPlayerLookup {

    private WalletPlayerLookup() {
    }

    static ResolvedTarget resolveLocal(HollyCore plugin, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();

        Player online = Bukkit.getPlayerExact(trimmed);
        if (online == null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName() != null && player.getName().equalsIgnoreCase(trimmed)) {
                    online = player;
                    break;
                }
            }
        }
        if (online != null) {
            return new ResolvedTarget(online.getUniqueId(), online.getName());
        }

        try {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(trimmed);
            if (cached != null && cached.getUniqueId() != null
                && (cached.hasPlayedBefore() || cached.getName() != null)) {
                return new ResolvedTarget(cached.getUniqueId(),
                    cached.getName() != null ? cached.getName() : trimmed);
            }
        } catch (NoSuchMethodError | NoClassDefFoundError ignored) {
        }

        if (plugin.getJoinInfoManager() != null) {
            UUID uuid = plugin.getJoinInfoManager().getUuidByName(trimmed);
            if (uuid != null) {
                String stored = plugin.getJoinInfoManager().getPlayerName(uuid);
                return new ResolvedTarget(uuid, stored != null ? stored : trimmed);
            }
        }

        if (plugin.getTopManager() != null) {
            UUID uuid = plugin.getTopManager().getUuidByName(trimmed);
            if (uuid != null) {
                return new ResolvedTarget(uuid, trimmed);
            }
        }

        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(trimmed)) {
                return new ResolvedTarget(offline.getUniqueId(), offline.getName());
            }
        }

        OfflinePlayer fallback = Bukkit.getOfflinePlayer(trimmed);
        if (fallback.hasPlayedBefore() || fallback.isOnline()) {
            return new ResolvedTarget(fallback.getUniqueId(),
                fallback.getName() != null ? fallback.getName() : trimmed);
        }
        return null;
    }

    static CompletableFuture<ResolvedTarget> resolve(HollyCore plugin, String name) {
        ResolvedTarget local = resolveLocal(plugin, name);
        if (local != null) {
            return CompletableFuture.completedFuture(local);
        }
        if (name == null || name.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String trimmed = name.trim();
        List<CompletableFuture<Optional<ResolvedTarget>>> lookups = new ArrayList<>();

        if (plugin.getWalletManager() != null && plugin.getWalletManager().getDataManager() != null) {
            lookups.add(plugin.getWalletManager().getDataManager()
                .resolvePlayerByName(trimmed)
                .thenApply(optional -> optional.map(player ->
                    new ResolvedTarget(player.uuid(), player.name()))));
        }

        // join_info w Mongo — offline gracze bez usercache.
        if (plugin.getMongoManager() != null) {
            try {
                Pattern exactName = Pattern.compile("^" + Pattern.quote(trimmed) + "$", Pattern.CASE_INSENSITIVE);
                lookups.add(plugin.getMongoManager().toFuture(
                    plugin.getMongoManager().getCollection("join_info")
                        .find(Filters.regex("name", exactName)).first()
                ).thenApply(doc -> mapJoinInfo(doc, trimmed)));
            } catch (Throwable ignored) {
            }
        }

        if (lookups.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.allOf(lookups.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                for (CompletableFuture<Optional<ResolvedTarget>> future : lookups) {
                    try {
                        Optional<ResolvedTarget> value = future.join();
                        if (value != null && value.isPresent()) {
                            return value.get();
                        }
                    } catch (Throwable ignoredEx) {
                    }
                }
                return null;
            });
    }

    private static Optional<ResolvedTarget> mapJoinInfo(Document doc, String fallbackName) {
        if (doc == null) {
            return Optional.empty();
        }
        String uuidRaw = doc.getString("uuid");
        if (uuidRaw == null) {
            Object id = doc.get("_id");
            if (id != null) {
                uuidRaw = String.valueOf(id);
            }
        }
        if (uuidRaw == null) {
            return Optional.empty();
        }
        try {
            String resolvedName = doc.getString("name");
            return Optional.of(new ResolvedTarget(
                UUID.fromString(uuidRaw),
                resolvedName != null && !resolvedName.isBlank() ? resolvedName : fallbackName
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    static List<String> suggestNames(HollyCore plugin, String partial) {
        String prefix = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        Set<String> names = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName() != null && player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(player.getName());
            }
        }
        if (plugin.getJoinInfoManager() != null) {
            for (String name : plugin.getJoinInfoManager().getKnownPlayerNames()) {
                if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(name);
                }
            }
        }
        if (plugin.getWalletManager() != null && plugin.getWalletManager().getDataManager() != null) {
            for (String name : plugin.getWalletManager().getDataManager().listCachedPlayerNames()) {
                if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    record ResolvedTarget(UUID uuid, String name) {
        OfflinePlayer asOffline() {
            return Bukkit.getOfflinePlayer(uuid);
        }
    }
}
