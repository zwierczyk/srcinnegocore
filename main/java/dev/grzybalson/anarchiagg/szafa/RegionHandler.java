package dev.grzybalson.anarchiagg.szafa;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class RegionHandler {
    private final JavaPlugin plugin;
    private final FileConfiguration config;
    private final List<String> regionNames = new ArrayList<>();

    public RegionHandler(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.loadRegions();
    }

    public void reload() {
        this.loadRegions();
    }

    private void loadRegions() {
        this.regionNames.clear();
        Set<String> merged = new LinkedHashSet<>();

        List<String> szafkaRegions = this.config.getStringList(SzafkaConfig.path("blocked-regions"));
        if (szafkaRegions.isEmpty()) {
            szafkaRegions = this.config.getStringList("blocked-regions");
        }
        if (szafkaRegions != null) {
            for (String name : szafkaRegions) {
                if (name != null && !name.isBlank()) {
                    merged.add(name.trim());
                }
            }
        }

        // Ten sam zestaw co oghub-customitems (np. spawn).
        File customItemsFile = new File(this.plugin.getDataFolder(), "addons/oghub-customitems/oghub-customitems.yml");
        if (customItemsFile.exists()) {
            YamlConfiguration customItems = YamlConfiguration.loadConfiguration(customItemsFile);
            List<String> customBlocked = customItems.getStringList("blocked-regions");
            if (customBlocked != null) {
                for (String name : customBlocked) {
                    if (name != null && !name.isBlank()) {
                        merged.add(name.trim());
                    }
                }
            }
        }

        this.regionNames.addAll(merged);
    }

    public boolean isInAnyRegion(Location loc) {
        if (this.regionNames.isEmpty() || loc == null || loc.getWorld() == null) {
            return false;
        }
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldedit.util.Location wgLoc = BukkitAdapter.adapt(loc);
            ApplicableRegionSet set = query.getApplicableRegions(wgLoc);
            return set.getRegions().stream().anyMatch(region -> this.regionNames.contains(region.getId()));
        } catch (NoClassDefFoundError e) {
            this.plugin.getLogger().severe("WorldGuard nie został znaleziony!");
            return false;
        } catch (Exception e) {
            this.plugin.getLogger().severe("Błąd podczas sprawdzania regionów WorldGuard: " + e.getMessage());
            return false;
        }
    }
}
