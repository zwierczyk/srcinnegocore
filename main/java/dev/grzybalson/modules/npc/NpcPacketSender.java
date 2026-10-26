package dev.grzybalson.modules.npc;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.grzybalson.utils.ColorUtils;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NpcPacketSender {

    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> ADD_PLAYER_ACTIONS = EnumSet.of(
        WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME
    );

    private static JavaPlugin pluginInstance;
    private static NpcVisibilityTracker visibilityTracker;

    public static void init(JavaPlugin plugin) {
        pluginInstance = plugin;
    }

    public static void setVisibilityTracker(NpcVisibilityTracker tracker) {
        visibilityTracker = tracker;
    }

    public static boolean isVisibleTo(Player player, Npc npc) {
        return visibilityTracker != null
            && visibilityTracker.isVisible(player.getUniqueId(), npc.getId());
    }

    public static boolean shouldSeeNpc(Player player, Npc npc) {
        if (npc.getLocation().getWorld() == null) return false;
        if (!player.getWorld().equals(npc.getLocation().getWorld())) return false;
        int renderDistance = npc.getRenderDistance();
        if (renderDistance <= 0) return true;
        double distSq = player.getLocation().distanceSquared(npc.getLocation());
        return distSq <= (double) renderDistance * renderDistance;
    }

    public static void showNpcTo(Player player, Npc npc) {
        showNpcTo(player, npc, false);
    }

    public static void showNpcTo(Player player, Npc npc, boolean force) {
        if (!isPacketEventsReady()) return;
        if (!player.getWorld().equals(npc.getLocation().getWorld())) return;

        int renderDistance = npc.getRenderDistance();
        if (renderDistance > 0) {
            double distSq = player.getLocation().distanceSquared(npc.getLocation());
            if (distSq > (double) renderDistance * renderDistance) return;
        }
        if (!force && isVisibleTo(player, npc)) {
            return;
        }
        if (visibilityTracker != null) {
            visibilityTracker.markVisible(player.getUniqueId(), npc.getId());
        }

        boolean hideDisplayName = isHiddenDisplayName(npc);
        String profileName = profileNameFor(npc);

        // Team PRZED spawnem — inaczej klient przez chwile pokazuje nick profilu.
        applyNpcTeam(npc, profileName, hideDisplayName, player);

        UserProfile profile = buildProfile(npc, profileName);
        Component displayName = Component.empty();

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
            profile,
            false,
            0,
            GameMode.CREATIVE,
            displayName,
            null
        );
        send(player, new WrapperPlayServerPlayerInfoUpdate(ADD_PLAYER_ACTIONS, List.of(info)));

        Location loc = npc.getLocation();
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
            npc.getEntityId(),
            Optional.of(npc.getUuid()),
            EntityTypes.PLAYER,
            new Vector3d(loc.getX(), loc.getY(), loc.getZ()),
            loc.getPitch(),
            loc.getYaw(),
            loc.getYaw(),
            0,
            Optional.empty()
        );
        send(player, spawn);

        sendMetadata(player, npc);
        send(player, new WrapperPlayServerEntityHeadLook(npc.getEntityId(), loc.getYaw()));
        send(player, new WrapperPlayServerEntityRotation(npc.getEntityId(), loc.getYaw(), loc.getPitch(), true));

        if (pluginInstance != null) {
            Bukkit.getScheduler().runTaskLater(pluginInstance, () -> {
                if (!player.isOnline()) return;
                applyNpcTeam(npc, profileName, true, player);
                sendMetadata(player, npc);
            }, 1L);
            Bukkit.getScheduler().runTaskLater(pluginInstance, () -> {
                if (!player.isOnline()) return;
                applyNpcTeam(npc, profileName, true, player);
                sendMetadata(player, npc);
                send(player, new WrapperPlayServerPlayerInfoRemove(List.of(npc.getUuid())));
            }, 8L);
            Bukkit.getScheduler().runTaskLater(pluginInstance, () -> {
                if (!player.isOnline()) return;
                applyNpcTeam(npc, profileName, true, player);
                sendMetadata(player, npc);
            }, 25L);
        }

        sendEquipment(player, npc);
    }

    public static void hideNpcFrom(Player player, Npc npc) {
        if (visibilityTracker != null) {
            visibilityTracker.markHidden(player.getUniqueId(), npc.getId());
        }
        if (!isPacketEventsReady()) return;
        send(player, new WrapperPlayServerDestroyEntities(npc.getEntityId()));
        send(player, new WrapperPlayServerPlayerInfoRemove(List.of(npc.getUuid())));
    }

    public static void sendMetadata(Player player, Npc npc) {
        if (!isPacketEventsReady()) return;

        boolean hideDisplayName = isHiddenDisplayName(npc);
        List<EntityData> metadata = new ArrayList<>();

        byte status = 0;
        if (npc.isGlowing()) status |= 0x40;
        metadata.add(new EntityData(0, EntityDataTypes.BYTE, status));

        if (!hideDisplayName) {
            metadata.add(new EntityData(2, EntityDataTypes.OPTIONAL_COMPONENT,
                Optional.of(ColorUtils.colorizeComponent(npc.getDisplayName()))));
        } else {
            metadata.add(new EntityData(2, EntityDataTypes.OPTIONAL_COMPONENT, Optional.empty()));
        }
        metadata.add(new EntityData(3, EntityDataTypes.BOOLEAN, !hideDisplayName));

        byte skinFlags = (byte) (0x01 | 0x02 | 0x04 | 0x08 | 0x10 | 0x20 | 0x40);
        metadata.add(new EntityData(17, EntityDataTypes.BYTE, skinFlags));

        send(player, new WrapperPlayServerEntityMetadata(npc.getEntityId(), metadata));
    }

    public static void sendEquipment(Player player, Npc npc) {
        if (!isPacketEventsReady() || npc.getEquipment().isEmpty()) return;

        List<Equipment> list = new ArrayList<>();
        for (Map.Entry<String, ItemStack> entry : npc.getEquipment().entrySet()) {
            EquipmentSlot slot = switch (entry.getKey().toLowerCase()) {
                case "head" -> EquipmentSlot.HELMET;
                case "chest" -> EquipmentSlot.CHEST_PLATE;
                case "legs" -> EquipmentSlot.LEGGINGS;
                case "feet" -> EquipmentSlot.BOOTS;
                case "mainhand" -> EquipmentSlot.MAIN_HAND;
                case "offhand" -> EquipmentSlot.OFF_HAND;
                default -> null;
            };
            if (slot == null || entry.getValue() == null) {
                continue;
            }
            list.add(new Equipment(slot, SpigotConversionUtil.fromBukkitItemStack(entry.getValue())));
        }
        if (list.isEmpty()) return;
        send(player, new WrapperPlayServerEntityEquipment(npc.getEntityId(), list));
    }

    public static void refreshForAll(Npc npc) {
        if (!npc.isGlowing()) {
            removeGlowTeam(npc);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            hideNpcFrom(p, npc);
            if (shouldSeeNpc(p, npc)) {
                showNpcTo(p, npc, true);
            }
        }
    }

    public static void refreshNpcSkinForAll(Npc npc) {
        // Pelny hide+show — sam PLAYER_INFO nie aktualizuje skorki juz zespawnowanego entity.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!shouldSeeNpc(player, npc)) {
                if (isVisibleTo(player, npc)) {
                    hideNpcFrom(player, npc);
                }
                continue;
            }
            hideNpcFrom(player, npc);
            showNpcTo(player, npc, true);
        }
    }

    public static void refreshNpcFullyForAll(Npc npc) {
        refreshNpcSkinForAll(npc);
    }

    private static void refreshNpcSkin(Player player, Npc npc) {
        if (!isPacketEventsReady()) return;
        if (!player.getWorld().equals(npc.getLocation().getWorld())) return;

        hideNpcFrom(player, npc);
        showNpcTo(player, npc, true);
    }

    public static void showAllTo(Player player, java.util.Collection<Npc> npcs) {
        for (Npc npc : npcs) {
            if (shouldSeeNpc(player, npc)) {
                showNpcTo(player, npc);
            }
        }
    }

    private static UserProfile buildProfile(Npc npc, String profileName) {
        UserProfile profile = new UserProfile(npc.getUuid(), profileName);
        if (npc.getSkinTexture() != null && npc.getSkinSignature() != null) {
            profile.setTextureProperties(List.of(
                new TextureProperty("textures", npc.getSkinTexture(), npc.getSkinSignature())
            ));
        }
        return profile;
    }

    private static String profileNameFor(Npc npc) {
        String hex = String.format("%08x", Math.abs(npc.getEntityId()));
        StringBuilder name = new StringBuilder(16);
        for (int i = 0; i < hex.length(); i++) {
            name.append('\u00A7').append(hex.charAt(i));
        }
        return name.toString();
    }

    public static void hideNametag(Player player, Npc npc) {
        if (player == null || npc == null) {
            return;
        }
        applyNpcTeam(npc, profileNameFor(npc), true, player);
        sendMetadata(player, npc);
    }

    public static void reapplyNametags(Player player, java.util.Collection<Npc> npcs) {
        if (player == null || npcs == null || npcs.isEmpty()) {
            return;
        }
        for (Npc npc : npcs) {
            if (isVisibleTo(player, npc) || shouldSeeNpc(player, npc)) {
                hideNametag(player, npc);
            }
        }
    }

    private static boolean isHiddenDisplayName(Npc npc) {
        return npc.getDisplayName() == null
            || npc.getDisplayName().equalsIgnoreCase("@none")
            || npc.getDisplayName().isEmpty();
    }

    @SuppressWarnings("deprecation")
    private static void applyNpcTeam(Npc npc, String profileName, boolean hideDisplayName, Player viewer) {
        String teamName = "ht" + Integer.toHexString(npc.getEntityId());
        if (teamName.length() > 16) teamName = teamName.substring(0, 16);

        Scoreboard playerBoard = viewer.getScoreboard();
        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();

        for (Scoreboard scoreboard : new Scoreboard[]{playerBoard, mainBoard}) {
            if (scoreboard == null) continue;

            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }

            if (npc.isGlowing()) {
                team.setColor(parseChatColor(npc.getGlowingColor()));
            } else {
                team.setColor(ChatColor.WHITE);
            }

            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            try {
                team.setOption(Team.Option.DEATH_MESSAGE_VISIBILITY, Team.OptionStatus.NEVER);
            } catch (Throwable ignored) {
            }

            if (!team.hasEntry(profileName)) {
                team.addEntry(profileName);
            }
        }
    }

    private static void removeGlowTeam(Npc npc) {
        String teamName = "ht" + Integer.toHexString(npc.getEntityId());
        if (teamName.length() > 16) teamName = teamName.substring(0, 16);

        Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team mainTeam = mainBoard.getTeam(teamName);
        if (mainTeam != null) mainTeam.unregister();

        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard board = p.getScoreboard();
            if (board != null && board != mainBoard) {
                Team team = board.getTeam(teamName);
                if (team != null) {
                    try { team.unregister(); } catch (Exception ignored) {}
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static ChatColor parseChatColor(String colorName) {
        if (colorName == null) return ChatColor.WHITE;
        try {
            return ChatColor.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChatColor.WHITE;
        }
    }

    private static void send(Player player, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private static boolean isPacketEventsReady() {
        return PacketEvents.getAPI() != null && PacketEvents.getAPI().isLoaded();
    }
}
