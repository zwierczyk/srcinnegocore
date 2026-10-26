package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.SoundsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundManager {

    private final HollyCore plugin;

    public SoundManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void playSound(Player player, SoundsConfig.SoundEntry entry) {
        if (entry == null || player == null) return;
        try {
            Sound sound = Sound.valueOf(entry.getSound());
            player.playSound(player.getLocation(), sound, entry.getVolume(), entry.getPitch());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound: " + entry.getSound());
        }
    }

    public void playSoundGlobal(SoundsConfig.SoundEntry entry) {
        if (entry == null) return;
        try {
            Sound sound = Sound.valueOf(entry.getSound());
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), sound, entry.getVolume(), entry.getPitch());
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound: " + entry.getSound());
        }
    }

    public void playTeleportStart(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getTeleportStart());
    }

    public void playTeleportEnd(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getTeleportEnd());
    }

    public void playTeleportCancelled(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getTeleportCancelled());
    }

    public void playGamemodeChange(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getGamemodeChange());
    }

    public void playFlyEnable(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getFlyEnable());
    }

    public void playFlyDisable(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getFlyDisable());
    }

    public void playPurchaseSuccess(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getPurchaseSuccess());
    }

    public void playPurchaseFail(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getPurchaseFail());
    }

    public void playWalletRecharge(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getWalletRecharge());
    }

    public void playWalletRechargeGlobal() {
        playSoundGlobal(plugin.getConfigManager().getSoundsConfig().getWalletRechargeGlobal());
    }

    public void playClearlagTick() {
        playSoundGlobal(plugin.getConfigManager().getSoundsConfig().getClearlagTick());
    }

    public void playClearlagClear() {
        playSoundGlobal(plugin.getConfigManager().getSoundsConfig().getClearlagClear());
    }

    public void playAfkReward(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getAfkReward());
    }

    public void playClanInvite(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getClanInvite());
    }

    public void playClanAnnouncement(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getClanAnnouncement());
    }

    public void playClanJoin(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getClanJoin());
    }

    public void playClanLeave(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getClanLeave());
    }

    public void playVanishEnable(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getVanishEnable());
    }

    public void playVanishDisable(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getVanishDisable());
    }

    public void playGuiOpen(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getGuiOpen());
    }

    public void playGuiClick(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getGuiClick());
    }

    public void playGuiClose(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getGuiClose());
    }

    public void playSuccess(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getSuccess());
    }

    public void playError(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getError());
    }

    public void playPickupReceive(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getPickupReceive());
    }

    public void playPickupAvailable(Player player) {
        playSound(player, plugin.getConfigManager().getSoundsConfig().getPickupAvailable());
    }
}
