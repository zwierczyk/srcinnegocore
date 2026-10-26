package dev.grzybalson.modules.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DungeonParty {

    private final String code;
    private final UUID leaderId;
    private final String leaderName;
    private final int maxSize;
    private final boolean publicParty;
    private final int ticketCost;
    private final Map<UUID, Member> members = new LinkedHashMap<>();
    private boolean started;

    public DungeonParty(String code, UUID leaderId, String leaderName, int maxSize, boolean publicParty, int ticketCost) {
        this.code = code;
        this.leaderId = leaderId;
        this.leaderName = leaderName;
        this.maxSize = maxSize;
        this.publicParty = publicParty;
        this.ticketCost = ticketCost;
        this.members.put(leaderId, new Member(leaderId, leaderName, false));
    }

    public String getCode() {
        return code;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public String getLeaderName() {
        return leaderName;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public boolean isPublicParty() {
        return publicParty;
    }

    public int getTicketCost() {
        return ticketCost;
    }

    public boolean isPremium() {
        return ticketCost > 0;
    }

    public String getTicketLabel() {
        return ticketCost > 0 ? String.valueOf(ticketCost) : "Darmowe";
    }

    public String getModeLabel() {
        return ticketCost > 0 ? "Premium" : "Darmowe";
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public Map<UUID, Member> getMembers() {
        return members;
    }

    public List<Member> getMemberList() {
        return new ArrayList<>(members.values());
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean isFull() {
        return members.size() >= maxSize;
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return leaderId.equals(uuid);
    }

    public boolean addMember(UUID uuid, String name) {
        if (started || isFull() || members.containsKey(uuid)) {
            return false;
        }
        members.put(uuid, new Member(uuid, name, false));
        return true;
    }

    public boolean removeMember(UUID uuid) {
        return members.remove(uuid) != null;
    }

    public void setReady(UUID uuid, boolean ready) {
        Member member = members.get(uuid);
        if (member != null) {
            member.ready = ready;
        }
    }

    public boolean isReady(UUID uuid) {
        Member member = members.get(uuid);
        return member != null && member.ready;
    }

    public int getReadyCount() {
        int count = 0;
        for (Member member : members.values()) {
            if (member.ready) {
                count++;
            }
        }
        return count;
    }

    public boolean allReady() {
        if (members.isEmpty()) {
            return false;
        }
        for (Member member : members.values()) {
            if (!member.ready) {
                return false;
            }
        }
        return true;
    }

    public void resetReady() {
        for (Member member : members.values()) {
            member.ready = false;
        }
    }

    public static final class Member {
        private final UUID uuid;
        private final String name;
        private boolean ready;

        public Member(UUID uuid, String name, boolean ready) {
            this.uuid = uuid;
            this.name = name;
            this.ready = ready;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public boolean isReady() {
            return ready;
        }
    }
}
