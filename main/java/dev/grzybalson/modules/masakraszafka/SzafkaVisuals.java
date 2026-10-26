package dev.grzybalson.modules.masakraszafka;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import dev.grzybalson.utils.HeadUtils;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SzafkaVisuals extends PacketListenerAbstract {
    private final MasakraSzafkaManager manager;
    private final Map<Integer, Wearer> wearers = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> entityIds = new ConcurrentHashMap<>();
    private final Map<String, Look> looks = new HashMap<>();
    private volatile boolean keepElytra = true;
    private boolean registered;

    SzafkaVisuals(MasakraSzafkaManager manager) {
        super(PacketListenerPriority.LOW);
        this.manager = manager;
    }

    boolean register() {
        try {
            if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isLoaded()) return false;
            PacketEvents.getAPI().getEventManager().registerListener(this);
            registered = true;
            return true;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    void unregister() {
        if (!registered) return;
        List<Player> affected = new ArrayList<>();
        for (Wearer wearer : wearers.values()) {
            Player player = Bukkit.getPlayer(wearer.owner);
            if (player != null) affected.add(player);
        }
        wearers.clear();
        entityIds.clear();
        affected.forEach(this::resync);
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        registered = false;
    }

    void reload(SzafkaCatalog catalog) {
        keepElytra = catalog.keepElytra;
        looks.clear();
    }

    void update(Player player, SzafkaItem costume) {
        UUID uuid = player.getUniqueId();
        int entityId = player.getEntityId();
        Integer previousId = entityIds.put(uuid, entityId);
        if (previousId != null && previousId != entityId) wearers.remove(previousId);
        Look look = costume == null ? null : looks.computeIfAbsent(costume.id(), id -> createLook(costume));
        Wearer previous = look == null ? wearers.remove(entityId) : wearers.put(entityId, new Wearer(uuid, look));
        if ((previous == null ? null : previous.look) != look) resync(player);
    }

    void forget(Player player) {
        Integer entityId = entityIds.remove(player.getUniqueId());
        if (entityId != null) wearers.remove(entityId);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (wearers.isEmpty() || event.getPacketType() != PacketType.Play.Server.ENTITY_EQUIPMENT) return;
        WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(event);
        Wearer wearer = wearers.get(packet.getEntityId());
        UUID viewer = event.getUser() == null ? null : event.getUser().getUUID();
        if (wearer == null || viewer == null || viewer.equals(wearer.owner)) return;
        List<Equipment> disguised = wearer.look.apply(packet.getEquipment(), keepElytra);
        if (disguised == null) return;
        packet.setEquipment(disguised);
        event.markForReEncode(true);
    }

    private void resync(Player player) {
        if (!registered || !player.isOnline()) return;
        EntityEquipment equipment = player.getEquipment();
        List<Equipment> real = List.of(
            new Equipment(EquipmentSlot.HELMET, packetItem(equipment.getHelmet())),
            new Equipment(EquipmentSlot.CHEST_PLATE, packetItem(equipment.getChestplate())),
            new Equipment(EquipmentSlot.LEGGINGS, packetItem(equipment.getLeggings())),
            new Equipment(EquipmentSlot.BOOTS, packetItem(equipment.getBoots())));
        Wearer wearer = wearers.get(player.getEntityId());
        List<Equipment> visible = wearer == null ? null : wearer.look.apply(real, keepElytra);
        for (Player viewer : player.getTrackedBy()) {
            if (viewer.equals(player)) continue;
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                    new WrapperPlayServerEntityEquipment(player.getEntityId(), new ArrayList<>(visible == null ? real : visible)));
            } catch (RuntimeException exception) {
                manager.plugin().getLogger().fine("[masakra-szafka] Nie wysłano wyglądu kostiumu: " + exception.getMessage());
            }
        }
    }

    private Look createLook(SzafkaItem costume) {
        Map<EquipmentSlot, com.github.retrooper.packetevents.protocol.item.ItemStack> items = new EnumMap<>(EquipmentSlot.class);
        ItemStack helmet = costume.texture() != null
            ? HeadUtils.setHeadTexture(new ItemStack(Material.PLAYER_HEAD), costume.texture())
            : leather(Material.LEATHER_HELMET, costume);
        items.put(EquipmentSlot.HELMET, SpigotConversionUtil.fromBukkitItemStack(helmet));
        items.put(EquipmentSlot.CHEST_PLATE, SpigotConversionUtil.fromBukkitItemStack(leather(Material.LEATHER_CHESTPLATE, costume)));
        items.put(EquipmentSlot.LEGGINGS, SpigotConversionUtil.fromBukkitItemStack(leather(Material.LEATHER_LEGGINGS, costume)));
        items.put(EquipmentSlot.BOOTS, SpigotConversionUtil.fromBukkitItemStack(leather(Material.LEATHER_BOOTS, costume)));
        return new Look(items);
    }

    private static com.github.retrooper.packetevents.protocol.item.ItemStack packetItem(ItemStack item) {
        return SpigotConversionUtil.fromBukkitItemStack(item == null ? new ItemStack(Material.AIR) : item);
    }

    private static ItemStack leather(Material material, SzafkaItem costume) {
        ItemStack item = new ItemStack(material);
        if (costume.color() != null && item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(costume.color());
            item.setItemMeta(meta);
        }
        return item;
    }

    private record Wearer(UUID owner, Look look) {
    }

    private record Look(Map<EquipmentSlot, com.github.retrooper.packetevents.protocol.item.ItemStack> items) {
        List<Equipment> apply(List<Equipment> source, boolean keepElytra) {
            List<Equipment> result = null;
            for (int index = 0; index < source.size(); index++) {
                Equipment equipment = source.get(index);
                com.github.retrooper.packetevents.protocol.item.ItemStack replacement = items.get(equipment.getSlot());
                if (replacement == null) continue;
                if (keepElytra && equipment.getSlot() == EquipmentSlot.CHEST_PLATE && equipment.getItem() != null
                    && equipment.getItem().getType() == ItemTypes.ELYTRA) continue;
                if (result == null) result = new ArrayList<>(source);
                result.set(index, new Equipment(equipment.getSlot(), replacement.copy()));
            }
            return result;
        }
    }
}
