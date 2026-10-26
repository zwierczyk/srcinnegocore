package dev.grzybalson.config.modules.home;

import eu.okaeri.configs.OkaeriConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HomeConfig extends OkaeriConfig {

    private GeneralSection general = new GeneralSection();
    private TeleportSection teleport = new TeleportSection();
    private HomesSection homes = new HomesSection();
    private Map<String, HomeSlotSection> homeSlots = createDefaultHomeSlots();
    private GuiSection gui = new GuiSection();
    private SoundsSection sounds = new SoundsSection();
    private ParticlesSection particles = new ParticlesSection();
    private CommandsSection commands = new CommandsSection();
    private SecuritySection security = new SecuritySection();
    private PerformanceSection performance = new PerformanceSection();

    public GeneralSection getGeneral() { return general; }
    public TeleportSection getTeleport() { return teleport; }
    public HomesSection getHomes() { return homes; }
    public Map<String, HomeSlotSection> getHomeSlots() { return homeSlots; }
    public GuiSection getGui() { return gui; }
    public SoundsSection getSounds() { return sounds; }
    public ParticlesSection getParticles() { return particles; }
    public CommandsSection getCommands() { return commands; }
    public SecuritySection getSecurity() { return security; }
    public PerformanceSection getPerformance() { return performance; }

    private static Map<String, HomeSlotSection> createDefaultHomeSlots() {
        Map<String, HomeSlotSection> map = new LinkedHashMap<>();
        map.put("slot1", new HomeSlotSection(19, 1, "holly.home.1", "&eDomek #1", "WHITE_BED"));
        map.put("slot2", new HomeSlotSection(20, 2, "holly.home.2", "&eDomek #2", "WHITE_BED"));
        map.put("slot3", new HomeSlotSection(21, 3, "holly.home.3", "&eDomek #3", "WHITE_BED"));
        map.put("slot4", new HomeSlotSection(22, 4, "holly.home.4", "&eDomek #4", "WHITE_BED"));
        map.put("slot5", new HomeSlotSection(23, 5, "holly.home.5", "&eDomek #5", "WHITE_BED"));
        map.put("slot6", new HomeSlotSection(24, 6, "holly.home.6", "&eDomek #6", "WHITE_BED"));
        map.put("slot7", new HomeSlotSection(25, 7, "holly.home.7", "&eDomek #7", "WHITE_BED"));
        return map;
    }

    public static class GeneralSection extends OkaeriConfig {
        private boolean debug = false;
        private int autoSaveInterval = 5;
        private boolean confirmDelete = true;

        public boolean isDebug() { return debug; }
        public int getAutoSaveInterval() { return autoSaveInterval; }
        public boolean isConfirmDelete() { return confirmDelete; }
    }

    public static class TeleportSection extends OkaeriConfig {
        private int countdown = 5;
        private boolean showMilliseconds = true;
        private boolean cancelOnMove = true;
        private double maxMoveDistance = 0.5;
        private boolean cancelOnDamage = true;
        private boolean cancelOnAttack = false;
        private SoundConfigItem countdownSound = new SoundConfigItem(true, "BLOCK_NOTE_BLOCK_HAT", 0.5f, 1.0f);
        private boolean showParticles = false;
        private ParticleConfigItem teleportParticles = new ParticleConfigItem(true, "PORTAL", 50, 0.5, 1.0, 0.5, 0.5, 10);

        public int getCountdown() { return countdown; }
        public boolean isShowMilliseconds() { return showMilliseconds; }
        public boolean isCancelOnMove() { return cancelOnMove; }
        public double getMaxMoveDistance() { return maxMoveDistance; }
        public boolean isCancelOnDamage() { return cancelOnDamage; }
        public boolean isCancelOnAttack() { return cancelOnAttack; }
        public SoundConfigItem getCountdownSound() { return countdownSound; }
        public boolean isShowParticles() { return showParticles; }
        public ParticleConfigItem getTeleportParticles() { return teleportParticles; }
    }

    public static class HomesSection extends OkaeriConfig {
        private int minNameLength = 3;
        private int maxNameLength = 20;
        private String allowedNameCharacters = "^[a-zA-Z0-9\\s!@#$%^&*()_+\\-=\\[\\]{};':,.<>?/\\\\|`~&#]+$";
        private List<String> allowedWorlds = new ArrayList<>();
        private List<String> blockedWorlds = new ArrayList<>();
        private boolean defaultShowCoordinates = false;

        public int getMinNameLength() { return minNameLength; }
        public int getMaxNameLength() { return maxNameLength; }
        public String getAllowedNameCharacters() { return allowedNameCharacters; }
        public List<String> getAllowedWorlds() { return allowedWorlds; }
        public List<String> getBlockedWorlds() { return blockedWorlds; }
        public boolean isDefaultShowCoordinates() { return defaultShowCoordinates; }
    }

    public static class HomeSlotSection extends OkaeriConfig {
        private int guiSlot;
        private int homeNumber;
        private String permission;
        private String defaultName;
        private String defaultIcon;

        public HomeSlotSection() {}

        public HomeSlotSection(int guiSlot, int homeNumber, String permission, String defaultName, String defaultIcon) {
            this.guiSlot = guiSlot;
            this.homeNumber = homeNumber;
            this.permission = permission;
            this.defaultName = defaultName;
            this.defaultIcon = defaultIcon;
        }

        public int getGuiSlot() { return guiSlot; }
        public int getHomeNumber() { return homeNumber; }
        public String getPermission() { return permission; }
        public String getDefaultName() { return defaultName; }
        public String getDefaultIcon() { return defaultIcon; }
    }

    public static class GuiSection extends OkaeriConfig {
        private MainMenuSection mainMenu = new MainMenuSection();
        private EditMenuSection editMenu = new EditMenuSection();
        private DeleteConfirmMenuSection deleteConfirmMenu = new DeleteConfirmMenuSection();
        private IconPickerMenuSection iconPickerMenu = new IconPickerMenuSection();

        public MainMenuSection getMainMenu() { return mainMenu; }
        public EditMenuSection getEditMenu() { return editMenu; }
        public DeleteConfirmMenuSection getDeleteConfirmMenu() { return deleteConfirmMenu; }
        public IconPickerMenuSection getIconPickerMenu() { return iconPickerMenu; }
    }

    public static class MainMenuSection extends OkaeriConfig {
        private String title = "&8Twoje domki";
        private int size = 45;
        private GuiItemConfig closeButton = new GuiItemConfig(40, "BARRIER", "&cZamknij!", new ArrayList<>(), false);
        private GuiItemConfig noPermissionItem = new GuiItemConfig(-1, "RED_BED", "&eDomek #{HOME_NUMBER}", Arrays.asList("&7", "&8 » &cNie posiadasz wystarczającej rangi!", "&7"), false);
        private GuiItemConfig unsetItem = new GuiItemConfig(-1, "BLUE_BED", "&eDomek #{HOME_NUMBER}", Arrays.asList("&7", "&8 » &7Kliknij &fLEWYM&7, aby zapisać lokalizacje!", "&7"), false);
        private GuiItemConfig setItemWithoutCoords = new GuiItemConfig(-1, "{CURRENT_ICON}", "&eDomek #{HOME_NUMBER}", Arrays.asList("&7", "&8 » &7Kliknij &fLEWYM&7, aby przeteleportować!", "&8 » &7Kliknij &fPRAWYM&7, aby zarządzać domkiem!", "&7"), false);
        private GuiItemConfig setItemWithCoords = new GuiItemConfig(-1, "{CURRENT_ICON}", "&eDomek #{HOME_NUMBER}", Arrays.asList("&7", "&8 » &7Domek znajduje się na &fx: {X}, y: {Y}, z: {Z}", "&7", "&8 » &7Kliknij &fLEWYM&7, aby przeteleportować!", "&8 » &7Kliknij &fPRAWYM&7, aby zarządzać domkiem!", "&7"), false);

        public String getTitle() { return title; }
        public int getSize() { return size; }
        public GuiItemConfig getCloseButton() { return closeButton; }
        public GuiItemConfig getNoPermissionItem() { return noPermissionItem; }
        public GuiItemConfig getUnsetItem() { return unsetItem; }
        public GuiItemConfig getSetItemWithoutCoords() { return setItemWithoutCoords; }
        public GuiItemConfig getSetItemWithCoords() { return setItemWithCoords; }
    }

    public static class EditMenuSection extends OkaeriConfig {
        private String title = "&8Domek #{NUMBER}";
        private int size = 45;
        private GuiItemConfig iconButton = new GuiItemConfig(21, "STRUCTURE_VOID", "&6Zmień ikonke", Arrays.asList("&8 » &7Możesz wybrać ikonkę domku.", "&7", "&8 » &7Kliknij &fLEWYM&7, aby zmienić!"), false);
        private CoordinatesButtonConfig coordinatesButton = new CoordinatesButtonConfig(22, "IRON_NUGGET", "&6Wyświetlanie koordynatów", Arrays.asList("&8 » &7Aktualnie: {STATUS}", "&7", "&8 » &7Kliknij &fLEWYM&7, aby zmienić!"), false, "&atak", "&cnie");
        private GuiItemConfig deleteButton = new GuiItemConfig(23, "RED_TERRACOTTA", "&cUsuń", Arrays.asList("&7", "&8 » &7Kliknij &fLEWYM&7, aby usunąć dom!"), false);
        private GuiItemConfig backButton = new GuiItemConfig(40, "BARRIER", "&cPowrót!", new ArrayList<>(), false);

        public String getTitle() { return title; }
        public int getSize() { return size; }
        public GuiItemConfig getIconButton() { return iconButton; }
        public CoordinatesButtonConfig getCoordinatesButton() { return coordinatesButton; }
        public GuiItemConfig getDeleteButton() { return deleteButton; }
        public GuiItemConfig getBackButton() { return backButton; }
    }

    public static class DeleteConfirmMenuSection extends OkaeriConfig {
        private String title = "&8Usuwanie domku #{NUMBER}";
        private int size = 45;
        private GuiItemConfig confirmButton = new GuiItemConfig(20, "LIME_WOOL", "&aZatwierdzam", Arrays.asList("&7", "&8 » &7Kliknij &fLEWYM&7, aby usunąć domek!", "&7"), false);
        private GuiItemConfig cancelButton = new GuiItemConfig(24, "RED_WOOL", "&4Anuluj", Arrays.asList("&7", "&8 » &7Kliknij &fLEWYM&7, aby anulować!", "&7"), false);
        private GuiItemConfig backButton = new GuiItemConfig(40, "BARRIER", "&cPowrót!", new ArrayList<>(), false);

        public String getTitle() { return title; }
        public int getSize() { return size; }
        public GuiItemConfig getConfirmButton() { return confirmButton; }
        public GuiItemConfig getCancelButton() { return cancelButton; }
        public GuiItemConfig getBackButton() { return backButton; }
    }

    public static class IconPickerMenuSection extends OkaeriConfig {
        private String title = "&8Wybierz ikonę";
        private int size = 54;
        private List<Integer> itemSlots = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
        private GuiItemConfig itemFormat = new GuiItemConfig(-1, "", "&7Ikona: &6{MATERIAL_NAME}", Arrays.asList("&8 » &7Kliknij &fLEWYM&7, aby wybrać!"), false);
        private GuiItemConfig previousPageButton = new GuiItemConfig(48, "ARROW", "&cPoprzednia strona", new ArrayList<>(), false);
        private GuiItemConfig nextPageButton = new GuiItemConfig(50, "ARROW", "&aNastępna strona", new ArrayList<>(), false);
        private GuiItemConfig backButton = new GuiItemConfig(49, "BARRIER", "&cPowrót!", new ArrayList<>(), false);
        private List<String> hiddenMaterials = Arrays.asList("AIR", "CAVE_AIR", "VOID_AIR", "BARRIER", "STRUCTURE_VOID", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "STRUCTURE_BLOCK", "JIGSAW");

        public String getTitle() { return title; }
        public int getSize() { return size; }
        public List<Integer> getItemSlots() { return itemSlots; }
        public GuiItemConfig getItemFormat() { return itemFormat; }
        public GuiItemConfig getPreviousPageButton() { return previousPageButton; }
        public GuiItemConfig getNextPageButton() { return nextPageButton; }
        public GuiItemConfig getBackButton() { return backButton; }
        public List<String> getHiddenMaterials() { return hiddenMaterials; }
    }

    public static class GuiItemConfig extends OkaeriConfig {
        private int slot = -1;
        private String material = "STONE";
        private String name = "";
        private List<String> lore = new ArrayList<>();
        private boolean glow = false;

        public GuiItemConfig() {}

        public GuiItemConfig(int slot, String material, String name, List<String> lore, boolean glow) {
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.glow = glow;
        }

        public int getSlot() { return slot; }
        public String getMaterial() { return material; }
        public String getName() { return name; }
        public List<String> getLore() { return lore; }
        public boolean isGlow() { return glow; }
    }

    public static class CoordinatesButtonConfig extends GuiItemConfig {
        private String statusEnabled = "&atak";
        private String statusDisabled = "&cnie";

        public CoordinatesButtonConfig() {}

        public CoordinatesButtonConfig(int slot, String material, String name, List<String> lore, boolean glow, String statusEnabled, String statusDisabled) {
            super(slot, material, name, lore, glow);
            this.statusEnabled = statusEnabled;
            this.statusDisabled = statusDisabled;
        }

        public String getStatusEnabled() { return statusEnabled; }
        public String getStatusDisabled() { return statusDisabled; }
    }

    public static class SoundsSection extends OkaeriConfig {
        private SoundConfigItem openGui = new SoundConfigItem(true, "BLOCK_CHEST_OPEN", 0.5f, 1.0f);
        private SoundConfigItem closeGui = new SoundConfigItem(false, "BLOCK_CHEST_CLOSE", 0.5f, 1.0f);
        private SoundConfigItem guiClick = new SoundConfigItem(true, "UI_BUTTON_CLICK", 0.3f, 1.0f);
        private SoundConfigItem setHome = new SoundConfigItem(true, "ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.0f);
        private SoundConfigItem deleteHome = new SoundConfigItem(true, "ENTITY_ITEM_BREAK", 1.0f, 0.8f);
        private SoundConfigItem teleportStart = new SoundConfigItem(true, "BLOCK_PORTAL_TRIGGER", 0.5f, 1.0f);
        private SoundConfigItem teleportSuccess = new SoundConfigItem(true, "ENTITY_ENDERMAN_TELEPORT", 1.0f, 1.0f);
        private SoundConfigItem teleportCancel = new SoundConfigItem(true, "ENTITY_VILLAGER_NO", 1.0f, 1.0f);
        private SoundConfigItem changeName = new SoundConfigItem(true, "BLOCK_ANVIL_USE", 0.5f, 1.2f);
        private SoundConfigItem changeIcon = new SoundConfigItem(true, "ITEM_ARMOR_EQUIP_LEATHER", 1.0f, 1.0f);
        private SoundConfigItem changeLocation = new SoundConfigItem(true, "BLOCK_BEACON_ACTIVATE", 0.5f, 1.5f);
        private SoundConfigItem error = new SoundConfigItem(true, "ENTITY_VILLAGER_NO", 1.0f, 0.8f);
        private SoundConfigItem noPermission = new SoundConfigItem(true, "BLOCK_ANVIL_LAND", 0.5f, 0.5f);

        public SoundConfigItem getOpenGui() { return openGui; }
        public SoundConfigItem getCloseGui() { return closeGui; }
        public SoundConfigItem getGuiClick() { return guiClick; }
        public SoundConfigItem getSetHome() { return setHome; }
        public SoundConfigItem getDeleteHome() { return deleteHome; }
        public SoundConfigItem getTeleportStart() { return teleportStart; }
        public SoundConfigItem getTeleportSuccess() { return teleportSuccess; }
        public SoundConfigItem getTeleportCancel() { return teleportCancel; }
        public SoundConfigItem getChangeName() { return changeName; }
        public SoundConfigItem getChangeIcon() { return changeIcon; }
        public SoundConfigItem getChangeLocation() { return changeLocation; }
        public SoundConfigItem getError() { return error; }
        public SoundConfigItem getNoPermission() { return noPermission; }
    }

    public static class SoundConfigItem extends OkaeriConfig {
        private boolean enabled = true;
        private String sound = "UI_BUTTON_CLICK";
        private float volume = 1.0f;
        private float pitch = 1.0f;

        public SoundConfigItem() {}

        public SoundConfigItem(boolean enabled, String sound, float volume, float pitch) {
            this.enabled = enabled;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }

        public boolean isEnabled() { return enabled; }
        public String getSound() { return sound; }
        public float getVolume() { return volume; }
        public float getPitch() { return pitch; }
    }

    public static class ParticlesSection extends OkaeriConfig {
        private boolean enabled = true;
        private ParticleConfigItem setHome = new ParticleConfigItem(true, "VILLAGER_HAPPY", 20, 0.5, 0.5, 0.5, 0.1, 0);
        private ParticleConfigItem deleteHome = new ParticleConfigItem(true, "SMOKE_NORMAL", 15, 0.3, 0.3, 0.3, 0.05, 0);
        private ParticleConfigItem teleportCountdown = new ParticleConfigItem(true, "PORTAL", 10, 0.3, 0.5, 0.3, 0.5, 10);

        public boolean isEnabled() { return enabled; }
        public ParticleConfigItem getSetHome() { return setHome; }
        public ParticleConfigItem getDeleteHome() { return deleteHome; }
        public ParticleConfigItem getTeleportCountdown() { return teleportCountdown; }
    }

    public static class ParticleConfigItem extends OkaeriConfig {
        private boolean enabled = true;
        private String type = "PORTAL";
        private int amount = 10;
        private double offsetX = 0.5;
        private double offsetY = 0.5;
        private double offsetZ = 0.5;
        private double speed = 0.1;
        private int interval = 10;

        public ParticleConfigItem() {}

        public ParticleConfigItem(boolean enabled, String type, int amount, double offsetX, double offsetY, double offsetZ, double speed, int interval) {
            this.enabled = enabled;
            this.type = type;
            this.amount = amount;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.speed = speed;
            this.interval = interval;
        }

        public boolean isEnabled() { return enabled; }
        public String getType() { return type; }
        public int getAmount() { return amount; }
        public double getOffsetX() { return offsetX; }
        public double getOffsetY() { return offsetY; }
        public double getOffsetZ() { return offsetZ; }
        public double getSpeed() { return speed; }
        public int getInterval() { return interval; }
    }

    public static class CommandsSection extends OkaeriConfig {
        private HomeCommandConfig home = new HomeCommandConfig();

        public HomeCommandConfig getHome() { return home; }
    }

    public static class HomeCommandConfig extends OkaeriConfig {
        private boolean enabled = true;
        private List<String> aliases = Arrays.asList("h", "dom");
        private int cooldown = 0;
        private boolean cooldownForAdmins = false;

        public boolean isEnabled() { return enabled; }
        public List<String> getAliases() { return aliases; }
        public int getCooldown() { return cooldown; }
        public boolean isCooldownForAdmins() { return cooldownForAdmins; }
    }

    public static class SecuritySection extends OkaeriConfig {
        private double maxHeight = 256;
        private double minHeight = -70;
        private boolean blockAirHomes = false;
        private boolean blockLavaHomes = true;
        private boolean blockFireHomes = true;

        public double getMaxHeight() { return maxHeight; }
        public double getMinHeight() { return minHeight; }
        public boolean isBlockAirHomes() { return blockAirHomes; }
        public boolean isBlockLavaHomes() { return blockLavaHomes; }
        public boolean isBlockFireHomes() { return blockFireHomes; }
    }

    public static class PerformanceSection extends OkaeriConfig {
        private boolean asyncLoading = true;
        private boolean asyncSaving = true;
        private int saveQueueSize = 50;
        private boolean batchSaves = true;
        private int batchSize = 10;
        private boolean optimizeGUI = true;
        private boolean cacheGUIItems = true;
        private int maxConcurrentGUI = 50;

        public boolean isAsyncLoading() { return asyncLoading; }
        public boolean isAsyncSaving() { return asyncSaving; }
        public int getSaveQueueSize() { return saveQueueSize; }
        public boolean isBatchSaves() { return batchSaves; }
        public int getBatchSize() { return batchSize; }
        public boolean isOptimizeGUI() { return optimizeGUI; }
        public boolean isCacheGUIItems() { return cacheGUIItems; }
        public int getMaxConcurrentGUI() { return maxConcurrentGUI; }
    }
}
