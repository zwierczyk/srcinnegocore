package dev.grzybalson.anarchiagg;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.coach.CoachConfig;
import dev.grzybalson.config.anarchiagg.AnarchiaGGModulesConfig;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;

import java.io.File;

public class AnarchiaGGConfigManager {
    private final HollyCore plugin;
    private AnarchiaGGModulesConfig modulesConfig;
    private AdminItemsConfig adminItemsConfig;
    private CoachConfig coachConfig;

    public AnarchiaGGConfigManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File root = AnarchiaGGPaths.getRootFolder(plugin);
        AnarchiaGGPaths.ensureResource(plugin, "modules.yml");
        modulesConfig = eu.okaeri.configs.ConfigManager.create(AnarchiaGGModulesConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(root, "modules.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        adminItemsConfig = eu.okaeri.configs.ConfigManager.create(AdminItemsConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(root, "adminitems.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        File trenerDir = new File(plugin.getDataFolder(), "addons/trener");
        if (!trenerDir.exists()) {
            trenerDir.mkdirs();
        }
        File trenerFile = new File(trenerDir, "trener.yml");
        File legacyFile = new File(root, "trener.yml");
        if (!trenerFile.exists() && legacyFile.exists()) {
            try {
                java.nio.file.Files.copy(legacyFile.toPath(), trenerFile.toPath());
            } catch (Exception ignored) {}
        }

        File targetFile = trenerFile;

        coachConfig = eu.okaeri.configs.ConfigManager.create(CoachConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(targetFile);
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        if (targetFile.exists()) {
            try {
                if (!legacyFile.getParentFile().exists()) legacyFile.getParentFile().mkdirs();
                java.nio.file.Files.copy(targetFile.toPath(), legacyFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {}
        }
    }

    public void reload() {
        if (modulesConfig == null) {
            load();
            return;
        }
        modulesConfig.load(true);
        if (adminItemsConfig != null) adminItemsConfig.load(true);
        if (coachConfig != null) coachConfig.load(true);
    }

    public AnarchiaGGModulesConfig getModulesConfig() {
        return modulesConfig;
    }

    public AdminItemsConfig getAdminItemsConfig() {
        return adminItemsConfig;
    }

    public CoachConfig getCoachConfig() {
        return coachConfig;
    }
}

