package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import java.util.*;

public class Excalibur implements Listener {
    private final JavaPlugin plugin;
    private final RegionHandler regionHandler;
    private final String excaliburName;
    private final Double excaliburDamage;
    private final int sameVictimCooldownSeconds;
    private final Set<String> blockedRegions = new HashSet<>();
    private final int LORE_PROGRESS_INDEX = 6;
    private final Map<UUID, Map<UUID, Long>> lastKillOnPlayer = new HashMap<>();

    public Excalibur(JavaPlugin plugin, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.regionHandler = regionHandler;
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);
        List<String> regionList = eventowkiConfig.getStringList("blocked-regions");
        if (regionList.isEmpty() && !eventowkiConfig.isSet("blocked-regions")) {
            eventowkiConfig.set("blocked-regions", List.of("spawn"));
            this.blockedRegions.add("spawn");
        } else {
            this.blockedRegions.addAll(regionList);
        }

        excaliburDamage = eventowkiConfig.getDouble("eventowki.excalibur.damage", 0.05);
        sameVictimCooldownSeconds = eventowkiConfig.getInt("eventowki.excalibur.cooldown", 600);
        excaliburName = eventowkiConfig.getString("eventowki.excalibur.name", "§e§lExcalibur").replace("&", "§");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        if (isLocationBlocked(killer.getLocation())) return;
        if (isLocationBlocked(victim.getLocation())) return;

        UUID killerId = killer.getUniqueId();
        UUID victimId = victim.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownTime = EventItemUseGuard.getCooldownSeconds(plugin, "excalibur", sameVictimCooldownSeconds) * 1000L;

        Map<UUID, Long> victimsMap = lastKillOnPlayer.computeIfAbsent(killerId, k -> new HashMap<>());

        if (cooldownTime > 0 && victimsMap.containsKey(victimId)) {
            long lastKill = victimsMap.get(victimId);
            if (now - lastKill < cooldownTime) {
                return;
            }
        }

        victimsMap.put(victimId, now);

        ItemStack item = killer.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!EventItemRegistry.matches(plugin, "excalibur", item)) return;
        if (!EventItemUseGuard.tryUse(plugin, killer)) return;
        if (!item.getType().name().endsWith("_SWORD")) return;

        updateExcaliburLore(item, meta);
        killer.getInventory().setItemInMainHand(item);
    }

    private void updateExcaliburLore(ItemStack item, ItemMeta meta) {
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();
        while (lore.size() <= LORE_PROGRESS_INDEX) {
            lore.add(ChatColor.WHITE.toString());
        }
        int LORE_KILL_INDEX = 5;
        int currentKills = extractKillsFromLine(lore.get(LORE_KILL_INDEX));
        int newKills = currentKills + 1;
        String LORE_KILL_COUNT_FORMAT = " &8» &7Aktualnie zabójstw: &f%d";
        String killCountLore = String.format(LORE_KILL_COUNT_FORMAT, newKills).replace("&", "§");
        lore.set(LORE_KILL_INDEX, killCountLore);
        int MAX_PROGRESS_KILLS = 100;
        int progressPercent = Math.min(newKills, MAX_PROGRESS_KILLS);
        String progressBar = generateProgressBar(newKills);
        String LORE_PROGRESS_PREFIX = " &8» ";
        String progressBarLore = LORE_PROGRESS_PREFIX.replace("&", "§") + progressBar + ChatColor.YELLOW + progressPercent + "%";
        lore.set(LORE_PROGRESS_INDEX, progressBarLore);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private int extractKillsFromLine(String line) {
        line = ChatColor.stripColor(line);
        int index = line.lastIndexOf(":");
        if (index != -1 && index + 1 < line.length()) {
            try {
                String countStr = line.substring(index + 1).trim();
                return Integer.parseInt(countStr);
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private int extractPercentFromProgressLine(String line) {
        line = ChatColor.stripColor(line);
        int percentIndex = line.lastIndexOf("%");
        if (percentIndex != -1) {
            int start = percentIndex - 1;
            while (start >= 0 && Character.isDigit(line.charAt(start))) start--;
            try {
                return Integer.parseInt(line.substring(start + 1, percentIndex).trim());
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    public static void setKills(ItemStack item, int newKills) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();
        int LORE_PROGRESS_INDEX = 6;
        while (lore.size() <= LORE_PROGRESS_INDEX) {
            lore.add(ChatColor.WHITE.toString());
        }
        int LORE_KILL_INDEX = 5;
        String LORE_KILL_COUNT_FORMAT = " &8» &7Aktualnie zabójstw: &f%d";
        String killCountLore = String.format(LORE_KILL_COUNT_FORMAT, newKills).replace("&", "§");
        lore.set(LORE_KILL_INDEX, killCountLore);
        int MAX_PROGRESS_KILLS = 100;
        int progressPercent = Math.min(newKills, MAX_PROGRESS_KILLS);
        String progressBar = generateProgressBarStatic(newKills);
        String LORE_PROGRESS_PREFIX = " &8» ";
        String progressBarLore = LORE_PROGRESS_PREFIX.replace("&", "§") + progressBar + ChatColor.YELLOW + progressPercent + "%";
        lore.set(LORE_PROGRESS_INDEX, progressBarLore);
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private static String generateProgressBarStatic(int kills) {
        int DASH_COUNT = 10;
        int filledDashes = Math.min(kills / 10, DASH_COUNT);
        int emptyDashes = DASH_COUNT - filledDashes;
        StringBuilder bar = new StringBuilder();
        String style = ChatColor.STRIKETHROUGH.toString() + ChatColor.BOLD;
        String filledColor = ChatColor.GREEN + style;
        for (int i = 0; i < filledDashes; i++) bar.append(filledColor).append("-");
        String emptyColor = ChatColor.WHITE + style;
        for (int i = 0; i < emptyDashes; i++) bar.append(emptyColor).append("-");
        bar.append(ChatColor.RESET).append(" ");
        return bar.toString();
    }

    private String generateProgressBar(int kills) {
        return generateProgressBarStatic(kills);
    }

    public int getExcaliburKills(Player player) {
        return 0;
    }
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        // Reaching 100 kills gives no extra damage bonus
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;
        for (String regionName : blockedRegions) {
            if (regionHandler.isLocationInRegion(loc, regionName)) return true;
        }
        return false;
    }
}
