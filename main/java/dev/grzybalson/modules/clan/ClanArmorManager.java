package dev.grzybalson.modules.clan;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import dev.grzybalson.HollyCore;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClanArmorManager extends PacketListenerAbstract implements Listener {

    private static final long CLAN_CACHE_TTL_MS = 60_000L;

    private final HollyCore plugin;
    private final Map<Integer, UUID> entityIdToUuid = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> uuidToEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, CachedClan> clanCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> clanRefreshInProgress = new ConcurrentHashMap<>();
    private final Map<EquipmentSlot, Equipment> fakeArmorTemplates = new EnumMap<>(EquipmentSlot.class);
    private boolean enabled = false;

    public ClanArmorManager(HollyCore plugin) {
        this.plugin = plugin;
        initializeArmorTemplates();
    }

    public void init() {
        if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isLoaded()) {
            plugin.getLogger().warning("PacketEvents API not loaded! Clan armor feature disabled.");
            return;
        }

        PacketEvents.getAPI().getEventManager().registerListener(this);
        enabled = true;

        for (Player player : Bukkit.getOnlinePlayers()) {
            trackPlayer(player);
        }

        plugin.getLogger().info("ClanArmorManager initialized with PacketEvents!");
    }

    public void shutdown() {
        if (enabled) {
            PacketEvents.getAPI().getEventManager().unregisterListener(this);
        }
        entityIdToUuid.clear();
        uuidToEntityId.clear();
        clanCache.clear();
        clanRefreshInProgress.clear();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!enabled || !(event.getPlayer() instanceof Player viewer)) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            handleEquipmentPacket(event, viewer);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            handleMetadataPacket(event, viewer);
        }
    }

    private void handleEquipmentPacket(PacketSendEvent event, Player viewer) {
        WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(event);
        UUID targetUuid = entityIdToUuid.get(packet.getEntityId());

        if (targetUuid == null || targetUuid.equals(viewer.getUniqueId()) || !areSameClan(viewer.getUniqueId(), targetUuid)) {
            return;
        }

        List<Equipment> newEquipment = new ArrayList<>(packet.getEquipment().size());
        boolean modified = false;

        for (Equipment equipment : packet.getEquipment()) {
            EquipmentSlot slot = equipment.getSlot();
            if (isArmorSlot(slot)) {
                Equipment template = fakeArmorTemplates.get(slot);
                newEquipment.add(template != null ? template : equipment);
                modified = true;
            } else {
                newEquipment.add(equipment);
            }
        }

        if (modified) {
            packet.setEquipment(newEquipment);
        }
    }

    private void handleMetadataPacket(PacketSendEvent event, Player viewer) {
        WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        UUID targetUuid = entityIdToUuid.get(packet.getEntityId());

        if (targetUuid == null || targetUuid.equals(viewer.getUniqueId()) || !areSameClan(viewer.getUniqueId(), targetUuid)) {
            return;
        }

        List<EntityData> metadata = new ArrayList<>(packet.getEntityMetadata());
        boolean hasFlags = false;

        for (int i = 0; i < metadata.size(); i++) {
            EntityData data = metadata.get(i);
            if (data.getIndex() == 0 && data.getType() == EntityDataTypes.BYTE) {
                byte flags = (byte) data.getValue();
                flags |= 0x40;
                metadata.set(i, new EntityData(0, EntityDataTypes.BYTE, flags));
                hasFlags = true;
                break;
            }
        }

        if (!hasFlags) {
            metadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) 0x40));
        }

        packet.setEntityMetadata(metadata);
    }

    private boolean areSameClan(UUID viewerUuid, UUID targetUuid) {
        String viewerClan = getClanFromCache(viewerUuid);
        String targetClan = getClanFromCache(targetUuid);
        return viewerClan != null && viewerClan.equals(targetClan);
    }

    private String getClanFromCache(UUID playerUuid) {
        long now = System.currentTimeMillis();
        CachedClan cachedClan = clanCache.get(playerUuid);

        if (cachedClan == null || cachedClan.expiresAt < now) {
            refreshClanAsync(playerUuid);
        }

        return cachedClan != null ? cachedClan.tag : null;
    }

    private void refreshClanAsync(UUID playerUuid) {
        if (clanRefreshInProgress.putIfAbsent(playerUuid, Boolean.TRUE) != null) {
            return;
        }

        plugin.getClanManager().getPlayerClan(playerUuid).whenComplete((clanTag, throwable) -> {
            clanRefreshInProgress.remove(playerUuid);
            if (throwable != null) {
                return;
            }
            clanCache.put(playerUuid, new CachedClan(clanTag, System.currentTimeMillis() + CLAN_CACHE_TTL_MS));
        });
    }

    private void initializeArmorTemplates() {
        fakeArmorTemplates.put(EquipmentSlot.HELMET, new Equipment(EquipmentSlot.HELMET,
            SpigotConversionUtil.fromBukkitItemStack(createLimeLeatherArmor(Material.LEATHER_HELMET))));
        fakeArmorTemplates.put(EquipmentSlot.CHEST_PLATE, new Equipment(EquipmentSlot.CHEST_PLATE,
            SpigotConversionUtil.fromBukkitItemStack(createLimeLeatherArmor(Material.LEATHER_CHESTPLATE))));
        fakeArmorTemplates.put(EquipmentSlot.LEGGINGS, new Equipment(EquipmentSlot.LEGGINGS,
            SpigotConversionUtil.fromBukkitItemStack(createLimeLeatherArmor(Material.LEATHER_LEGGINGS))));
        fakeArmorTemplates.put(EquipmentSlot.BOOTS, new Equipment(EquipmentSlot.BOOTS,
            SpigotConversionUtil.fromBukkitItemStack(createLimeLeatherArmor(Material.LEATHER_BOOTS))));
    }

    private ItemStack createLimeLeatherArmor(Material material) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(Color.LIME);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HELMET
            || slot == EquipmentSlot.CHEST_PLATE
            || slot == EquipmentSlot.LEGGINGS
            || slot == EquipmentSlot.BOOTS;
    }

    private void trackPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        int entityId = player.getEntityId();

        Integer previousEntityId = uuidToEntityId.put(uuid, entityId);
        if (previousEntityId != null && previousEntityId != entityId) {
            entityIdToUuid.remove(previousEntityId);
        }

        entityIdToUuid.put(entityId, uuid);
        refreshClanAsync(uuid);
    }

    private void untrackPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Integer entityId = uuidToEntityId.remove(uuid);
        if (entityId != null) {
            entityIdToUuid.remove(entityId);
        }
        clanCache.remove(uuid);
        clanRefreshInProgress.remove(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        trackPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        untrackPlayer(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> trackPlayer(event.getPlayer()));
    }

    public void sendFakeArmorToViewer(Player viewer, Player clanMember) {
        if (!enabled || !areSameClan(viewer.getUniqueId(), clanMember.getUniqueId())) {
            return;
        }

        List<Equipment> equipment = List.of(
            fakeArmorTemplates.get(EquipmentSlot.HELMET),
            fakeArmorTemplates.get(EquipmentSlot.CHEST_PLATE),
            fakeArmorTemplates.get(EquipmentSlot.LEGGINGS),
            fakeArmorTemplates.get(EquipmentSlot.BOOTS)
        );

        WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(clanMember.getEntityId(), equipment);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);

        List<EntityData> metadata = List.of(new EntityData(0, EntityDataTypes.BYTE, (byte) 0x40));
        WrapperPlayServerEntityMetadata metaPacket = new WrapperPlayServerEntityMetadata(clanMember.getEntityId(), metadata);
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metaPacket);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void invalidateClanCache(UUID playerUuid) {
        if (playerUuid != null) {
            clanCache.remove(playerUuid);
            clanRefreshInProgress.remove(playerUuid);
        }
    }

    private static final class CachedClan {
        private final String tag;
        private final long expiresAt;

        private CachedClan(String tag, long expiresAt) {
            this.tag = tag;
            this.expiresAt = expiresAt;
        }
    }
}
