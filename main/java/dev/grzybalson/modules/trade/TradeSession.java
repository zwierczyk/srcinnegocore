package dev.grzybalson.modules.trade;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class TradeSession {

    private final UUID id;
    private final UUID playerA;
    private final UUID playerB;
    private final ItemStack[] offerA = new ItemStack[TradeSlots.OFFER_CAPACITY];
    private final ItemStack[] offerB = new ItemStack[TradeSlots.OFFER_CAPACITY];
    private boolean readyA;
    private boolean readyB;
    private boolean completing;

    public TradeSession(UUID playerA, UUID playerB) {
        this.id = UUID.randomUUID();
        this.playerA = playerA;
        this.playerB = playerB;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayerA() {
        return playerA;
    }

    public UUID getPlayerB() {
        return playerB;
    }

    public UUID partnerOf(UUID uuid) {
        if (playerA.equals(uuid)) {
            return playerB;
        }
        if (playerB.equals(uuid)) {
            return playerA;
        }
        return null;
    }

    public boolean isSideA(UUID uuid) {
        return playerA.equals(uuid);
    }

    public ItemStack[] offerFor(UUID uuid) {
        return isSideA(uuid) ? offerA : offerB;
    }

    public ItemStack[] offerOfPartner(UUID uuid) {
        return isSideA(uuid) ? offerB : offerA;
    }

    public boolean isReady(UUID uuid) {
        return isSideA(uuid) ? readyA : readyB;
    }

    public boolean isPartnerReady(UUID uuid) {
        return isSideA(uuid) ? readyB : readyA;
    }

    public void setReady(UUID uuid, boolean ready) {
        if (isSideA(uuid)) {
            readyA = ready;
        } else {
            readyB = ready;
        }
    }

    public void resetReady() {
        readyA = false;
        readyB = false;
    }

    public boolean bothReady() {
        return readyA && readyB;
    }

    public boolean isCompleting() {
        return completing;
    }

    public void setCompleting(boolean completing) {
        this.completing = completing;
    }

    public boolean involves(UUID uuid) {
        return playerA.equals(uuid) || playerB.equals(uuid);
    }

    public Player otherPlayer(Player player) {
        UUID partner = partnerOf(player.getUniqueId());
        return partner == null ? null : player.getServer().getPlayer(partner);
    }
}
