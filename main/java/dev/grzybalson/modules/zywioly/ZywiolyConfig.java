package dev.grzybalson.modules.zywioly;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.ConfigAutoUpdater;
import dev.grzybalson.config.HollyDataPaths;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ZywiolyConfig {

    private static final String RESOURCE = "addons/zywioly/zywioly.yml";

    private final HollyCore plugin;
    private YamlConfiguration yaml;
    private File file;

    ZywiolyConfig(HollyCore plugin) {
        this.plugin = plugin;
        reload();
    }

    void reload() {
        HollyDataPaths.ensureResource(plugin, RESOURCE);
        // Dopisz tylko brakujące klucze z JAR — nigdy nie nadpisuj istniejących wartości (m.in. custom-model-data).
        ConfigAutoUpdater.updateResource(plugin, RESOURCE, false);
        this.file = HollyDataPaths.getRootFile(plugin, RESOURCE);
        this.yaml = YamlConfiguration.loadConfiguration(file);

        // Snapshot CMD przed ewentualnym zapisem migracji lokalnych.
        Map<String, Integer> preservedModels = snapshotElementModels();

        boolean changed = false;
        changed |= setIfMissing("no-gui-worlds", List.of("pvp"));
        changed |= setIfMissing("gui.roll-spinning-lore", List.of(
            "&8x &eTrwa losowanie żywiołu...",
            "&8x &fAktualnie: %element%",
            " ",
            "&7Poczekaj na wynik!"
        ));
        changed |= setIfMissing("gui.roll-winner-lore", List.of(
            "&a&l✔ Wylosowano!",
            "&8x &fŻywioł: %element%",
            "&8x &fStawka: &#BEFFBA$%kwota%"
        ));
        changed |= setIfMissing("gui.roll-choice-lore", List.of(
            "&8x &fGracz: &#BEFFBA%nick%",
            "&8x &fWybrany żywioł: %element%",
            "&8x &fStawka: &#BEFFBA$%kwota%"
        ));
        changed |= setIfMissing("gui-3.create-slots", List.of(21, 22, 23));
        changed |= setIfMissing("gui-3.game-slots", List.of(10, 11, 12, 13, 14, 15, 16));
        changed |= setIfMissing("gui-3.previous-slot", 18);
        changed |= setIfMissing("gui-3.next-slot", 26);
        changed |= setIfMissing("gui.game-slots", List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        ));
        changed |= setIfMissing("gui.previous-slot", 37);
        changed |= setIfMissing("gui.next-slot", 43);

        // Draw: tylko gdy brak placeholdera (nie nadpisuj własnej wiadomości użytkownika).
        String draw = yaml.getString("messages.draw", "");
        if (draw != null && !draw.isBlank() && (!draw.contains("%opponent%") || !draw.contains("%nick%"))) {
            yaml.set("messages.draw",
                "&#FF9A7A&lż&#D8BC84&lʏ&#B2DD8E&lᴡ&#8BFF98&lɪ&#85FDBA&lᴏ&#80FCDD&lʟ&#7AFAFF&lʏ &8⁝⁝⁝ &fGracz &#BEFFBA%nick%&f zremisował z &#BEFFBA%opponent%&f o kwotę &#BEFFBA$%kwota%&f!");
            changed = true;
        } else {
            changed |= setIfMissing("messages.draw",
                "&#FF9A7A&lż&#D8BC84&lʏ&#B2DD8E&lᴡ&#8BFF98&lɪ&#85FDBA&lᴏ&#80FCDD&lʟ&#7AFAFF&lʏ &8⁝⁝⁝ &fGracz &#BEFFBA%nick%&f zremisował z &#BEFFBA%opponent%&f o kwotę &#BEFFBA$%kwota%&f!");
        }

        if (changed) {
            // Przywróć CMD po zapisie — YamlConfiguration.save potrafi gubić / przestawiać wartości przy pełnym rewrite.
            for (Map.Entry<String, Integer> entry : preservedModels.entrySet()) {
                yaml.set(entry.getKey(), entry.getValue());
            }
            try {
                yaml.save(file);
            } catch (Exception ignored) {
            }
            this.yaml = YamlConfiguration.loadConfiguration(file);
        }
    }

    private boolean setIfMissing(String path, Object value) {
        if (yaml.contains(path)) {
            return false;
        }
        yaml.set(path, value);
        return true;
    }

    private Map<String, Integer> snapshotElementModels() {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (ZywiolyElement element : ZywiolyElement.values()) {
            String path = "elements." + element.configKey() + ".custom-model-data";
            if (yaml.contains(path)) {
                out.put(path, yaml.getInt(path));
            }
        }
        return out;
    }

    boolean isEnabled() {
        return yaml.getBoolean("enabled", true);
    }

    double minBet() {
        return Math.max(1.0D, yaml.getDouble("min-bet", 100.0D));
    }

    double maxBalancePercent() {
        return Math.max(1.0D, Math.min(100.0D, yaml.getDouble("max-balance-percent", 50.0D)));
    }

    int guiRows() {
        return Math.max(3, Math.min(6, yaml.getInt("gui-rows", 5)));
    }

    int guiRefreshSeconds() {
        return Math.max(0, yaml.getInt("gui-refresh-seconds", 2));
    }

    List<String> noGuiWorlds() {
        List<String> worlds = yaml.getStringList("no-gui-worlds");
        if (worlds == null || worlds.isEmpty()) {
            return List.of("pvp");
        }
        List<String> out = new ArrayList<>();
        for (String world : worlds) {
            if (world != null && !world.isBlank()) {
                out.add(world.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? List.of("pvp") : out;
    }

    boolean isNoGuiWorld(org.bukkit.World world) {
        if (world == null) {
            return false;
        }
        return noGuiWorlds().contains(world.getName().toLowerCase(Locale.ROOT));
    }

    boolean isNoGuiWorld(org.bukkit.entity.Player player) {
        return player != null && isNoGuiWorld(player.getWorld());
    }

    String mainTitle() {
        return yaml.getString("gui.main-title", "&7Żywioły");
    }

    String pickTitle() {
        return yaml.getString("gui.pick-title", "&7Wybierz żywioł");
    }

    String rollTitle() {
        return yaml.getString("gui.roll-title", "&7Losowanie żywiołu");
    }

    List<Integer> createSlots() {
        return createSlots(guiRows() * 9);
    }

    List<Integer> createSlots(int inventorySize) {
        String path = inventorySize <= 27 ? "gui-3.create-slots" : "gui.create-slots";
        List<Integer> slots = yaml.getIntegerList(path);
        if (slots == null || slots.isEmpty()) {
            return inventorySize <= 27 ? List.of(21, 22, 23) : List.of(39, 40, 41);
        }
        return filterSlots(slots, inventorySize);
    }

    List<Integer> gameSlots(int inventorySize) {
        String path = inventorySize <= 27 ? "gui-3.game-slots" : "gui.game-slots";
        List<Integer> slots = yaml.getIntegerList(path);
        if (slots == null || slots.isEmpty()) {
            if (inventorySize <= 27) {
                return List.of(10, 11, 12, 13, 14, 15, 16);
            }
            return List.of(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
            );
        }
        return filterSlots(slots, inventorySize);
    }

    int previousSlot(int inventorySize) {
        String path = inventorySize <= 27 ? "gui-3.previous-slot" : "gui.previous-slot";
        int fallback = inventorySize <= 27 ? 18 : 37;
        int slot = yaml.getInt(path, fallback);
        return slot >= 0 && slot < inventorySize ? slot : fallback;
    }

    int nextSlot(int inventorySize) {
        String path = inventorySize <= 27 ? "gui-3.next-slot" : "gui.next-slot";
        int fallback = inventorySize <= 27 ? 26 : 43;
        int slot = yaml.getInt(path, fallback);
        return slot >= 0 && slot < inventorySize ? slot : fallback;
    }

    private static List<Integer> filterSlots(List<Integer> slots, int inventorySize) {
        List<Integer> out = new ArrayList<>();
        for (Integer slot : slots) {
            if (slot != null && slot >= 0 && slot < inventorySize) {
                out.add(slot);
            }
        }
        return out;
    }

    Material createMaterial() {
        return material("gui.create-item.material", Material.LIME_DYE);
    }

    String createName() {
        return yaml.getString("gui.create-item.name", "&aStwórz grę");
    }

    List<String> createLore() {
        return yaml.getStringList("gui.create-item.lore");
    }

    Material gameMaterial() {
        return material("gui.game-item.material", Material.PLAYER_HEAD);
    }

    String gameName() {
        return yaml.getString("gui.game-item.name", "&a%nick%");
    }

    List<String> gameLore() {
        return yaml.getStringList("gui.game-item.lore");
    }

    Material ownGameMaterial() {
        return material("gui.own-game-item.material", Material.PLAYER_HEAD);
    }

    String ownGameName() {
        return yaml.getString("gui.own-game-item.name", "&c%nick%");
    }

    List<String> ownGameLore() {
        return yaml.getStringList("gui.own-game-item.lore");
    }

    Material backMaterial() {
        return material("gui.back-item.material", Material.ARROW);
    }

    String backName() {
        return yaml.getString("gui.back-item.name", "&cPowrót");
    }

    List<String> backLore() {
        return yaml.getStringList("gui.back-item.lore");
    }

    Material previousMaterial() {
        return material("gui.previous-item.material", Material.SPECTRAL_ARROW);
    }

    String previousName() {
        return yaml.getString("gui.previous-item.name", "&aPoprzednia strona");
    }

    Material nextMaterial() {
        return material("gui.next-item.material", Material.SPECTRAL_ARROW);
    }

    String nextName() {
        return yaml.getString("gui.next-item.name", "&aNastępna strona");
    }

    int elementSlot(ZywiolyElement element) {
        return yaml.getInt("elements." + element.configKey() + ".slot", defaultSlot(element));
    }

    Material elementMaterial(ZywiolyElement element) {
        return material("elements." + element.configKey() + ".material", Material.PAPER);
    }

    int elementModel(ZywiolyElement element) {
        return yaml.getInt("elements." + element.configKey() + ".custom-model-data", 0);
    }

    String elementName(ZywiolyElement element) {
        return yaml.getString("elements." + element.configKey() + ".name", element.name());
    }

    String elementDisplay(ZywiolyElement element) {
        String display = yaml.getString("elements." + element.configKey() + ".display", "");
        if (display != null && !display.isBlank()) {
            return display;
        }
        return switch (element) {
            case OGIEN -> "&#FF3535Ogień";
            case NATURA -> "&#58FF35Natura";
            case WODA -> "&#35BDFFWoda";
        };
    }

    List<String> elementLore(ZywiolyElement element) {
        List<String> lore = yaml.getStringList("elements." + element.configKey() + ".lore");
        return lore == null ? List.of() : lore;
    }

    List<String> rollSpinningLore() {
        List<String> lore = yaml.getStringList("gui.roll-spinning-lore");
        if (lore == null || lore.isEmpty()) {
            return List.of(
                "&8x &eTrwa losowanie żywiołu...",
                "&8x &fAktualnie: %element%",
                " ",
                "&7Poczekaj na wynik!"
            );
        }
        return lore;
    }

    List<String> rollWinnerLore() {
        List<String> lore = yaml.getStringList("gui.roll-winner-lore");
        if (lore == null || lore.isEmpty()) {
            return List.of(
                "&a&l✔ Wylosowano!",
                "&8x &fŻywioł: %element%",
                "&8x &fStawka: &#BEFFBA$%kwota%"
            );
        }
        return lore;
    }

    List<String> rollChoiceLore() {
        List<String> lore = yaml.getStringList("gui.roll-choice-lore");
        if (lore == null || lore.isEmpty()) {
            return List.of(
                "&8x &fGracz: &#BEFFBA%nick%",
                "&8x &fWybrany żywioł: %element%",
                "&8x &fStawka: &#BEFFBA$%kwota%"
            );
        }
        return lore;
    }

    int rollCreatorSlot() {
        return yaml.getInt("gui.roll-creator-slot", 11);
    }

    int rollOpponentSlot() {
        return yaml.getInt("gui.roll-opponent-slot", 15);
    }

    String signLine(int index, String fallback) {
        return yaml.getString("sign.line-" + index, fallback);
    }

    String message(String key) {
        return yaml.getString("messages." + key, "");
    }

    private Material material(String path, Material fallback) {
        String raw = yaml.getString(path, fallback.name());
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static int defaultSlot(ZywiolyElement element) {
        return switch (element) {
            case OGIEN -> 11;
            case NATURA -> 13;
            case WODA -> 15;
        };
    }
}
