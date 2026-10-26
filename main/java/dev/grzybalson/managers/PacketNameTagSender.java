package dev.grzybalson.managers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Nametagi pakietami {@code TEAMS}. Kolizja zawsze {@code never} — {@code always}
 * na teamie nadpisuje Paper {@code enable-player-collisions: false} po stronie klienta.
 * Po {@code CREATE} idzie też {@code UPDATE}: klient ignoruje CREATE, gdy team już istnieje.
 */
public final class PacketNameTagSender {

    private PacketNameTagSender() {
    }

    public static boolean ready() {
        try {
            return PacketEvents.getAPI() != null && PacketEvents.getAPI().isLoaded();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static void apply(
        Player viewer,
        String teamName,
        String previousTeamName,
        Component prefix,
        Component suffix,
        NamedTextColor color,
        String... entries
    ) {
        if (viewer == null || teamName == null || teamName.isBlank() || entries == null || entries.length == 0 || !ready()) {
            return;
        }
        // Nie wysyłaj REMOVE ze starego teamu — ADD_ENTITIES na nowym teamie przenosi wpis u klienta.
        // REMOVE gdy gracz jest już na innym teamie = Network Protocol Error (kick z serwera).
        boolean create = previousTeamName == null || !previousTeamName.equals(teamName);
        if (create) {
            sendTeamInfo(viewer, teamName, prefix, suffix, color, WrapperPlayServerTeams.TeamMode.CREATE);
        }
        sendTeamInfo(viewer, teamName, prefix, suffix, color, WrapperPlayServerTeams.TeamMode.UPDATE);
        sendAddEntities(viewer, teamName, entries);
    }

    public static void removeEntities(Player viewer, String teamName, String... entries) {
        if (viewer == null || teamName == null || teamName.isBlank() || entries == null || entries.length == 0 || !ready()) {
            return;
        }
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES,
                    (WrapperPlayServerTeams.ScoreBoardTeamInfo) null,
                    entries
                )
            );
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    public static void remove(Player viewer, String teamName) {
        if (viewer == null || teamName == null || teamName.isBlank() || !ready()) {
            return;
        }
        try {
            WrapperPlayServerTeams packet = new WrapperPlayServerTeams(
                teamName,
                WrapperPlayServerTeams.TeamMode.REMOVE,
                (WrapperPlayServerTeams.ScoreBoardTeamInfo) null
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static void sendTeamInfo(
        Player viewer,
        String teamName,
        Component prefix,
        Component suffix,
        NamedTextColor color,
        WrapperPlayServerTeams.TeamMode mode
    ) {
        WrapperPlayServerTeams.ScoreBoardTeamInfo info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
            Component.text(teamName),
            prefix != null ? prefix : Component.empty(),
            suffix != null ? suffix : Component.empty(),
            WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
            WrapperPlayServerTeams.CollisionRule.NEVER,
            color != null ? color : NamedTextColor.WHITE,
            WrapperPlayServerTeams.OptionData.FRIENDLY_FIRE
        );
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerTeams(teamName, mode, info)
            );
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static void sendAddEntities(Player viewer, String teamName, String... entries) {
        if (entries == null || entries.length == 0) {
            return;
        }
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerTeams(
                    teamName,
                    WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
                    (WrapperPlayServerTeams.ScoreBoardTeamInfo) null,
                    entries
                )
            );
        } catch (RuntimeException | LinkageError ignored) {
        }
    }
}
