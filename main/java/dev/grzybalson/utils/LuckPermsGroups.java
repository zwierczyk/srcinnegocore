package dev.grzybalson.utils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LuckPermsGroups {

    private static final List<String> RANK_ORDER = List.of(
        "root", "wlasciciel", "owner", "zarzad", "headadmin", "hadmin", "admin",
        "mod", "moderator", "kidmod", "jmod", "helper", "thelper", "trialhelper",
        "builder", "budowniczy", "partner", "tworca", "media", "youtuber", "yt",
        "tiktok", "minimedia", "sponsor", "patron", "elita", "champion", "boss",
        "dig", "mvip", "svip", "vip", "gracz", "player", "default"
    );

    private static volatile List<String> liveOrder = RANK_ORDER;

    private LuckPermsGroups() {
    }

    public static void setRankOrder(List<String> order) {
        if (order == null || order.isEmpty()) {
            liveOrder = RANK_ORDER;
            return;
        }
        liveOrder = List.copyOf(order);
    }

    public static Set<String> collect(LuckPerms luckPerms, User user) {
        Set<String> groups = new LinkedHashSet<>();
        if (user == null) {
            return groups;
        }

        String primary = user.getPrimaryGroup();
        if (primary != null && !primary.isBlank()) {
            groups.add(primary.toLowerCase(Locale.ROOT));
        }
        if (luckPerms == null) {
            return groups;
        }

        try {
            QueryOptions options = resolveQueryOptions(luckPerms, user);
            for (Group group : user.getInheritedGroups(options)) {
                if (group == null || group.getName() == null || group.getName().isBlank()) {
                    continue;
                }
                groups.add(group.getName().toLowerCase(Locale.ROOT));
            }
            for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
                if (node == null || node.hasExpired()) {
                    continue;
                }
                String name = node.getGroupName();
                if (name != null && !name.isBlank()) {
                    groups.add(name.toLowerCase(Locale.ROOT));
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return groups;
    }

    public static String bestGroup(LuckPerms luckPerms, User user) {
        String fallback = user != null && user.getPrimaryGroup() != null && !user.getPrimaryGroup().isBlank()
            ? user.getPrimaryGroup()
            : "default";
        return bestGroup(collect(luckPerms, user), fallback);
    }

    public static String bestGroup(Iterable<String> owned, String fallback) {
        String best = fallback == null || fallback.isBlank() ? "default" : fallback;
        int bestIndex = rankIndex(best);
        if (owned == null) {
            return best;
        }
        for (String ownedGroup : owned) {
            int index = rankIndex(ownedGroup);
            if (index < bestIndex) {
                bestIndex = index;
                best = ownedGroup;
            }
        }
        return best;
    }

    public static String bestPrefix(LuckPerms luckPerms, User user) {
        String bestPrefix = "";
        int bestIndex = Integer.MAX_VALUE;
        if (luckPerms != null) {
            for (String name : collect(luckPerms, user)) {
                try {
                    Group group = luckPerms.getGroupManager().getGroup(name);
                    if (group == null) {
                        continue;
                    }
                    String prefix = group.getCachedData().getMetaData().getPrefix();
                    if (prefix == null || ColorUtils.isBlankLoreLine(prefix)) {
                        continue;
                    }
                    int index = rankIndex(name);
                    if (index < bestIndex) {
                        bestIndex = index;
                        bestPrefix = prefix;
                    }
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        }
        if (!ColorUtils.isBlankLoreLine(bestPrefix)) {
            return bestPrefix;
        }
        if (user == null) {
            return "";
        }
        try {
            String meta = user.getCachedData().getMetaData().getPrefix();
            return meta == null ? "" : meta;
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    public static int rankIndex(String group) {
        if (group == null || group.isBlank()) {
            return Integer.MAX_VALUE;
        }
        String key = group.toLowerCase(Locale.ROOT);
        int index = liveOrder.indexOf(key);
        if (index >= 0) {
            return index;
        }
        index = RANK_ORDER.indexOf(key);
        return index >= 0 ? index : Integer.MAX_VALUE - 1;
    }

    private static QueryOptions resolveQueryOptions(LuckPerms luckPerms, User user) {
        try {
            return luckPerms.getContextManager().getQueryOptions(user)
                .orElseGet(QueryOptions::defaultContextualOptions);
        } catch (RuntimeException | LinkageError ignored) {
            return QueryOptions.defaultContextualOptions();
        }
    }
}
