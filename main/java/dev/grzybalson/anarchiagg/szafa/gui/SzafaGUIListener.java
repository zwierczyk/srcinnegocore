package dev.grzybalson.anarchiagg.szafa.gui;

import dev.grzybalson.anarchiagg.szafa.gui.SzafaGUIHandler;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyArmorHandler;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyHandler;
import dev.grzybalson.anarchiagg.szafa.papugi.PapugiHandler;
import dev.grzybalson.anarchiagg.szafa.papugi.PapugiVisualHandler;
import dev.grzybalson.anarchiagg.szafa.pety.PetyHandler;
import dev.grzybalson.anarchiagg.szafa.pety.PetyVisualHandler;
import dev.grzybalson.utils.NBTUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class SzafaGUIListener
implements Listener {
    public static final String KOSTIUM_DURATION_TAG = "szafka_kostium_duration";
    public static final String PET_DURATION_TAG = "szafka_pet_duration";
    public static final String KOSTIUM_EXPIRATION_TAG = "szafka_kostium_expiration";
    public static final String PET_EXPIRATION_TAG = "szafka_pet_expiration";
    public static final String KOSTIUM_KEY_TAG = "szafka_kostium_key";
    public static final String PET_KEY_TAG = "szafka_pet_key";

    private final SzafaGUIHandler guiHandler;
    private final KostiumyArmorHandler armorHandler;
    private final PetyVisualHandler petArmorHandler;
    private final PapugiVisualHandler papugiVisualHandler;
    private final Map<Material, Map<String, String>> kostiumKeyByTypeAndBaseName = new HashMap<>();
    private final Map<Material, Map<String, String>> petKeyByTypeAndBaseName = new HashMap<>();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([dhm])");
    private static final Pattern BASE_NAME_SUFFIX = Pattern.compile("(\\sna\\s(czas\\s)?\\d+[dhm]|\\sNA\\sZAWSZE|\\sna\\s%czas%)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_SUFFIX = Pattern.compile(".*(\\sna\\s(czas\\s)?)(\\d+[dhm])$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_TOKEN = Pattern.compile("(?i)&#[0-9a-f]{6}");
    private static final Pattern DATE_IN_LORE = Pattern.compile("(\\d{2}:\\d{2}:\\d{2}\\s\\d{2}-\\d{2}-\\d{4})");
    private static final Pattern DATE_LINE = Pattern.compile(".*\\d{2}:\\d{2}:\\d{2}\\s\\d{2}-\\d{2}-\\d{4}.*");
    private static final long LOOKUP_REBUILD_COOLDOWN_MILLIS = 5000L;
    private long lastLookupRebuildMillis;

    public SzafaGUIListener(SzafaGUIHandler guiHandler, KostiumyArmorHandler armorHandler, PetyVisualHandler petArmorHandler, PapugiVisualHandler papugiVisualHandler) {
        this.guiHandler = guiHandler;
        this.armorHandler = armorHandler;
        this.petArmorHandler = petArmorHandler;
        this.papugiVisualHandler = papugiVisualHandler;
        rebuildItemLookupCache();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack handItem = event.getItem();
        if (handItem == null || !handItem.hasItemMeta()) {
            return;
        }

        Player player = event.getPlayer();
        Material offHandType = player.getInventory().getItemInOffHand().getType();
        if (offHandType == Material.BOW || offHandType == Material.TRIDENT) {
            return;
        }

        ItemMeta meta = handItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemDisplayName = meta.getDisplayName();
        if (handItem.getType() == SzafaGUIHandler.ZWOJ_KOSTIUM_TYPE && SzafaGUIHandler.ZWOJ_KOSTIUM_DISPLAY_NAME.equals(itemDisplayName)) {
            return;
        }
        if (handItem.getType() == SzafaGUIHandler.ZWOJ_PET_TYPE && SzafaGUIHandler.ZWOJ_PET_DISPLAY_NAME.equals(itemDisplayName)) {
            return;
        }

        boolean isMainHand = (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND);

        String foundKostiumKey = pdc.get(NBTUtils.createKey(KOSTIUM_KEY_TAG), PersistentDataType.STRING);
        if (foundKostiumKey == null || !this.guiHandler.getKostiumyHandler().getKostiumy().containsKey(foundKostiumKey)) {
            foundKostiumKey = this.findKostiumKeyByDisplayName(itemDisplayName, handItem.getType(), this.guiHandler.getKostiumyHandler().getKostiumy());
        }
        if (foundKostiumKey != null) {
            KostiumyHandler.KostiumData foundKostiumData = this.guiHandler.getKostiumyHandler().getKostiumy().get(foundKostiumKey);
            event.setCancelled(true);
            this.handleKostiumActivation(player, handItem, itemDisplayName, foundKostiumKey, foundKostiumData, isMainHand);
            return;
        }

        if (petsDisabled()) {
            return;
        }

        String foundPetKey = pdc.get(NBTUtils.createKey(PET_KEY_TAG), PersistentDataType.STRING);
        if (foundPetKey == null || !this.guiHandler.getPetyHandler().getPety().containsKey(foundPetKey)) {
            foundPetKey = this.findPetKeyByDisplayName(itemDisplayName, handItem.getType(), this.guiHandler.getPetyHandler().getPety());
        }
        if (foundPetKey != null) {
            PetyHandler.PetData foundPetData = this.guiHandler.getPetyHandler().getPety().get(foundPetKey);
            event.setCancelled(true);
            this.handlePetActivation(player, handItem, itemDisplayName, foundPetKey, foundPetData, isMainHand);
            return;
        }

        if (papugiDisabled() || this.papugiVisualHandler == null) {
            return;
        }

        String papugaKey = NBTUtils.getString(handItem, PapugiHandler.PAPUGA_KEY_TAG);
        if (papugaKey != null && this.guiHandler.getPapugiHandler().getPapugi().containsKey(papugaKey)) {
            event.setCancelled(true);
            this.handlePapugaActivation(player, handItem, itemDisplayName, papugaKey);
        }
    }

    private boolean petsDisabled() {
        return this.guiHandler.isCostumesOnly()
            || this.guiHandler.getPetyHandler() == null;
    }

    private boolean papugiDisabled() {
        return petsDisabled() || this.guiHandler.getPapugiHandler() == null;
    }

    private void handleKostiumActivation(Player player, ItemStack handItem, String itemDisplayName, String foundKostiumKey, KostiumyHandler.KostiumData foundData, boolean isMainHand) {
        if (handItem.getAmount() != 1) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cMo\u017cesz za\u0142o\u017cy\u0107 maksymalnie \u00a741 \u00a7ckostium!", 0, 50, 10);
            return;
        }

        String expirationDate = this.determineExpirationDate(handItem, itemDisplayName, foundData);
        if (isExpired(expirationDate)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cTen kostium wygasł i nie może zostać założony!", 0, 50, 10);
            if (isMainHand) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
            return;
        }

        String activeKostiumKey = this.guiHandler.getKostiumyHandler().getDatabase().getActiveKostium(player.getUniqueId());
        ItemStack previousKostiumItem = null;
        if (activeKostiumKey != null) {
            KostiumyHandler.KostiumData oldData = this.guiHandler.getKostiumyHandler().getKostiumData(activeKostiumKey);
            if (oldData != null) {
                previousKostiumItem = this.createItemFromData(oldData, player, true);
            }
            this.armorHandler.removeArmor(player);
        }

        this.guiHandler.getKostiumyHandler().getDatabase().setActiveKostium(player.getUniqueId(), foundKostiumKey, itemDisplayName, expirationDate);

        if (isMainHand) {
            player.getInventory().setItemInMainHand(previousKostiumItem);
        } else {
            player.getInventory().setItemInOffHand(previousKostiumItem);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7aPomy\u015blnie za\u0142o\u017cono kostium!", 0, 50, 10);
        this.armorHandler.refreshPlayer(player);
    }

    private void handlePetActivation(Player player, ItemStack handItem, String itemDisplayName, String foundPetKey, PetyHandler.PetData foundData, boolean isMainHand) {
        if (handItem.getAmount() != 1) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cMo\u017cesz za\u0142o\u017cy\u0107 maksymalnie \u00a741 \u00a7cpeta!", 0, 50, 10);
            return;
        }

        String expirationDate = this.determineExpirationDate(handItem, itemDisplayName, foundData);
        if (isExpired(expirationDate)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cTen pet wygasł i nie może zostać założony!", 0, 50, 10);
            if (isMainHand) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
            return;
        }

        String activePetKey = this.guiHandler.getPetyHandler().getDatabase().getActivePet(player.getUniqueId());
        ItemStack previousPetItem = null;
        if (activePetKey != null) {
            PetyHandler.PetData oldData = this.guiHandler.getPetyHandler().getPetData(activePetKey);
            if (oldData != null) {
                previousPetItem = this.createItemFromData(oldData, player, false);
            }
        }

        this.guiHandler.getPetyHandler().getDatabase().setActivePet(player.getUniqueId(), foundPetKey, itemDisplayName, expirationDate);

        if (isMainHand) {
            player.getInventory().setItemInMainHand(previousPetItem);
        } else {
            player.getInventory().setItemInOffHand(previousPetItem);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7aPomy\u015blnie za\u0142o\u017cono peta!", 0, 50, 10);
    }


    private void handlePapugaActivation(Player player, ItemStack handItem, String itemDisplayName, String papugaKey) {
        if (handItem.getAmount() != 1) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cMożesz założyć maksymalnie §41 §cpapugę!", 0, 50, 10);
            return;
        }
        String expirationDate = this.guiHandler.getPapugiHandler().buildExpirationFromItem(handItem);
        if (isExpired(expirationDate)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cTa papuga wygasła i nie może zostać założona!", 0, 50, 10);
            if (player.getInventory().getItemInMainHand().equals(handItem)) {
                player.getInventory().setItemInMainHand(null);
            } else if (player.getInventory().getItemInOffHand().equals(handItem)) {
                player.getInventory().setItemInOffHand(null);
            }
            return;
        }

        String activePapugaKey = this.guiHandler.getPapugiHandler().getDatabase().getActivePapuga(player.getUniqueId());
        if (activePapugaKey != null) {
            if (player.getInventory().firstEmpty() == -1) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie masz miejsca w ekwipunku!", 0, 50, 10);
                return;
            }
            if (this.removeActivePapugaAndGiveBack(player, activePapugaKey)) {
                return;
            }
        }
        String bonusKey = NBTUtils.getString(handItem, PapugiHandler.PAPUGA_BONUS_TAG);
        Integer rerolls = NBTUtils.getInt(handItem, PapugiHandler.PAPUGA_ROLLS_TAG);
        if (rerolls == null) {
            rerolls = 0;
        }
        if (bonusKey == null) {
            PapugiHandler.PapugaData papugaData = this.guiHandler.getPapugiHandler().getPapuga(papugaKey);
            PapugiHandler.BonusData rolled = papugaData == null ? null : this.guiHandler.getPapugiHandler().rollBonusForPapuga(papugaData, null);
            bonusKey = rolled != null ? rolled.key() : null;
        }
        String activationDisplayName = this.guiHandler.getPapugiHandler().resolveActivationDisplayName(handItem, papugaKey, itemDisplayName);
        this.guiHandler.getPapugiHandler().getDatabase().setActivePapuga(player.getUniqueId(), papugaKey, activationDisplayName, expirationDate, bonusKey, rerolls);
        if (player.getInventory().getItemInMainHand().equals(handItem)) {
            player.getInventory().setItemInMainHand(null);
        } else if (player.getInventory().getItemInOffHand().equals(handItem)) {
            player.getInventory().setItemInOffHand(null);
        }
        this.papugiVisualHandler.syncPapuga(player);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§aPomyślnie założono papugę!", 0, 50, 10);
    }

    private String determineExpirationDate(ItemStack item, String itemDisplayName, Object dataObject) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "NA ZAWSZE";
        }
        boolean isKostium = dataObject instanceof KostiumyHandler.KostiumData;
        String storedExpiration = NBTUtils.getString(item, isKostium ? KOSTIUM_EXPIRATION_TAG : PET_EXPIRATION_TAG);
        if (storedExpiration != null && !storedExpiration.isBlank() && !isExpired(storedExpiration)) {
            return storedExpiration;
        }

        String duration = this.getDurationFromDisplayName(itemDisplayName);
        String storedDuration = NBTUtils.getString(item, isKostium ? KOSTIUM_DURATION_TAG : PET_DURATION_TAG);
        if ((duration == null || duration.isEmpty()) && storedDuration != null && !storedDuration.isBlank()) {
            duration = storedDuration;
        }

        List<String> currentLore = meta.getLore() != null ? meta.getLore() : new ArrayList<String>();
        String timeLoreLine = this.getTimeLoreLine(currentLore);
        String strippedTimeLore = timeLoreLine != null ? ChatColor.stripColor(timeLoreLine) : null;
        String extractedDateString = this.extractDateFromLoreLine(strippedTimeLore);

        if (extractedDateString != null && !extractedDateString.isBlank() && !isExpired(extractedDateString)) {
            return extractedDateString;
        }

        if (dataObject != null && duration != null && !duration.isEmpty()) {
            LocalDateTime startTime = LocalDateTime.now();
            LocalDateTime newExpirationTime = this.calculateExpirationTime(startTime, duration);
            if (newExpirationTime != null) {
                String expirationDate = newExpirationTime.format(DATE_FORMATTER);
                ArrayList<String> newLore = new ArrayList<String>();
                List<String> dataLore = dataObject instanceof KostiumyHandler.KostiumData ? ((KostiumyHandler.KostiumData)dataObject).lore() : ((PetyHandler.PetData)dataObject).lore();
                if (dataLore != null) {
                    for (String line : dataLore) {
                        String tempLine = line.replace("%czas%", duration);
                        if (tempLine.contains("%uzycie%")) {
                            tempLine = tempLine.replace("%uzycie%", expirationDate);
                        }
                        newLore.add(dev.grzybalson.utils.ColorUtils.colorize(tempLine));
                    }
                    meta.setLore(newLore);
                    item.setItemMeta(meta);
                }
                return expirationDate;
            }
        }
        return "NA ZAWSZE";
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity humanEntity = event.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player player = (Player)humanEntity;
        if (!this.guiHandler.isKostiumView(event.getView())) {
            return;
        }
        event.setCancelled(true);
        // Kliki w dolnym (player) ekwipunku przy otwartym GUI — tylko blokada, bez akcji GUI.
        if (event.getClickedInventory() != null
            && event.getClickedInventory().equals(event.getView().getBottomInventory())) {
            return;
        }
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR || clickedItem.getItemMeta() == null) {
            return;
        }
        ItemMeta clickedMeta = clickedItem.getItemMeta();
        String clickedDisplayName = clickedMeta.getDisplayName();
        boolean petsDisabled = petsDisabled();
        String openTitle = ChatColor.stripColor(event.getView().getTitle());
        if ("Lista kostiumów".equalsIgnoreCase(openTitle) || "Lista petów".equalsIgnoreCase(openTitle)) {
            int backSlot = Math.max(0, event.getView().getTopInventory().getSize() - 5);
            if (event.getSlot() == backSlot
                || (clickedItem.getType() == Material.ARROW
                    && ChatColor.stripColor(clickedDisplayName).equalsIgnoreCase("Powrót"))) {
                this.guiHandler.openGUI(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
            return;
        }

        if (event.getSlot() == this.guiHandler.KOSTIUMY_LIST_SLOT) {
            this.guiHandler.openKostiumyList(player);
            return;
        }
        if (!petsDisabled && event.getSlot() == this.guiHandler.PETY_LIST_SLOT) {
            this.guiHandler.openPetyList(player);
            return;
        }
        if (!petsDisabled && event.getSlot() == this.guiHandler.PET_VISIBILITY_SLOT) {
            if (!this.guiHandler.isPetVisibilityEnabled()) {
                return;
            }
            this.guiHandler.togglePetVisibility(player.getUniqueId());
            this.guiHandler.openGUI(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        if (event.getSlot() == this.guiHandler.CLOSE_BUTTON_SLOT) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }
        if (event.getSlot() == this.guiHandler.REMOVE_KOSTIUM_SLOT && this.guiHandler.REMOVE_KOSTIUM_NAME.equals(clickedDisplayName)) {
            String activeKostium = this.guiHandler.getKostiumyHandler().getDatabase().getActiveKostium(player.getUniqueId());
            if (activeKostium != null) {
                if (this.removeActiveKostiumAndGiveBack(player, activeKostium)) {
                    return;
                }
                this.guiHandler.openGUI(player);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7aPomy\u015blnie zdj\u0119to kostium!", 0, 50, 10);
            }
            return;
        }
        if (event.getSlot() == this.guiHandler.EXTEND_KOSTIUM_SLOT && this.guiHandler.EXTEND_KOSTIUM_NAME.equals(clickedDisplayName)) {
            this.handleExtendAction(player, true);
            return;
        }
        if (petsDisabled) {
            return;
        }
        if (event.getSlot() == this.guiHandler.REMOVE_PET_SLOT && this.guiHandler.REMOVE_PET_NAME.equals(clickedDisplayName)) {
            String activePet = this.guiHandler.getPetyHandler().getDatabase().getActivePet(player.getUniqueId());
            if (activePet != null) {
                if (this.removeActivePetAndGiveBack(player, activePet)) {
                    return;
                }
                this.guiHandler.openGUI(player);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7aPomy\u015blnie zdj\u0119to peta!", 0, 50, 10);
            }
            return;
        }
        if (event.getSlot() == this.guiHandler.EXTEND_PET_SLOT && this.guiHandler.EXTEND_PET_NAME.equals(clickedDisplayName)) {
            this.handleExtendAction(player, false);
            return;
        }
        if (papugiDisabled() || this.papugiVisualHandler == null) {
            return;
        }
        if (event.getSlot() == this.guiHandler.REMOVE_PAPUGA_SLOT && this.guiHandler.REMOVE_PAPUGA_NAME.equals(clickedDisplayName)) {
            String activePapuga = this.guiHandler.getPapugiHandler().getDatabase().getActivePapuga(player.getUniqueId());
            if (activePapuga != null) {
                if (this.removeActivePapugaAndGiveBack(player, activePapuga)) {
                    return;
                }
                this.papugiVisualHandler.clearShoulderPapuga(player);
                this.guiHandler.openGUI(player);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§aPomyślnie zdjęto papugę!", 0, 50, 10);
            }
            return;
        }
        if (event.getSlot() == this.guiHandler.REROLL_PAPUGA_SLOT && clickedDisplayName.startsWith(this.guiHandler.REROLL_PAPUGA_NAME.replace("%cost%", ""))) {
            this.handlePapugaReroll(player);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (this.guiHandler.isKostiumView(event.getView())) {
            event.setCancelled(true);
        }
    }

    private void handlePapugaReroll(Player player) {
        String activePapugaKey = this.guiHandler.getPapugiHandler().getDatabase().getActivePapuga(player.getUniqueId());
        if (activePapugaKey == null) {
            this.guiHandler.openGUI(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie masz aktywnej papugi!", 0, 50, 10);
            return;
        }
        PapugiHandler.PapugaData papugaData = this.guiHandler.getPapugiHandler().getPapuga(activePapugaKey);
        if (papugaData == null) {
            this.guiHandler.openGUI(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie znaleziono danych papugi!", 0, 50, 10);
            return;
        }
        if (!this.guiHandler.getPapugiHandler().hasEconomy()) {
            this.guiHandler.openGUI(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cBrak ekonomii Vault do przelosowania!", 0, 50, 10);
            return;
        }
        int rerolls = this.guiHandler.getPapugiHandler().getDatabase().getActivePapugaRerolls(player.getUniqueId());
        double cost = this.guiHandler.getPapugiHandler().getRollCost(rerolls);
        if (!this.guiHandler.getPapugiHandler().canAfford(player, cost)) {
            this.guiHandler.openGUI(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie masz §4" + this.guiHandler.getPapugiHandler().formatMoney(cost) + "$ §cna przelosowanie!", 0, 50, 10);
            return;
        }
        String currentBonus = this.guiHandler.getPapugiHandler().getDatabase().getActivePapugaBonus(player.getUniqueId());
        PapugiHandler.BonusData rolled = this.guiHandler.getPapugiHandler().rollBonusForPapuga(papugaData, currentBonus);
        if (rolled == null) {
            this.guiHandler.openGUI(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie udało się wylosować bonusu!", 0, 50, 10);
            return;
        }
        if (!this.guiHandler.getPapugiHandler().withdraw(player, cost)) {
            this.guiHandler.openGUI(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cNie udało się pobrać pieniędzy!", 0, 50, 10);
            return;
        }
        this.guiHandler.getPapugiHandler().getDatabase().updateActivePapugaBonus(player.getUniqueId(), rolled.key(), rerolls + 1);
        this.guiHandler.openGUI(player);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§aWylosowano bonus: §f" + ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', rolled.displayName())), 0, 50, 10);
    }

    private boolean removeActivePapugaAndGiveBack(Player player, String activePapugaKey) {
        String displayName = this.guiHandler.getPapugiHandler().getDatabase().getActivePapugaDisplayName(player.getUniqueId());
        String expiration = this.guiHandler.getPapugiHandler().getDatabase().getPapugaExpiration(player.getUniqueId());
        String bonusKey = this.guiHandler.getPapugiHandler().getDatabase().getActivePapugaBonus(player.getUniqueId());
        int rerolls = this.guiHandler.getPapugiHandler().getDatabase().getActivePapugaRerolls(player.getUniqueId());
        ItemStack itemToGive = this.guiHandler.getPapugiHandler().createStoredItem(activePapugaKey, displayName, expiration, bonusKey, rerolls);
        if (player.getInventory().firstEmpty() == -1) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "§cMasz pełny ekwipunek!", 0, 50, 10);
            this.guiHandler.openGUI(player);
            return true;
        }
        player.getInventory().addItem(itemToGive);
        this.guiHandler.getPapugiHandler().getDatabase().removeActivePapuga(player.getUniqueId());
        this.papugiVisualHandler.clearShoulderPapuga(player);
        return false;
    }

    private void handleExtendAction(Player player, boolean isKostium) {
        Material zwojType;
        String activeKey = isKostium ? this.guiHandler.getKostiumyHandler().getDatabase().getActiveKostium(player.getUniqueId()) : this.guiHandler.getPetyHandler().getDatabase().getActivePet(player.getUniqueId());
        String expirationDateString = isKostium ? this.guiHandler.getKostiumyHandler().getDatabase().getKostiumExpiration(player.getUniqueId()) : this.guiHandler.getPetyHandler().getDatabase().getPetExpiration(player.getUniqueId());
        String zwojDisplayName = isKostium ? SzafaGUIHandler.ZWOJ_KOSTIUM_DISPLAY_NAME : SzafaGUIHandler.ZWOJ_PET_DISPLAY_NAME;
        Material material = zwojType = isKostium ? SzafaGUIHandler.ZWOJ_KOSTIUM_TYPE : SzafaGUIHandler.ZWOJ_PET_TYPE;
        if (activeKey == null || expirationDateString == null || expirationDateString.equalsIgnoreCase("NA ZAWSZE")) {
            this.guiHandler.openGUI(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cNie masz " + (isKostium ? "kostiumu" : "peta") + " do przed\u0142u\u017cenia!", 0, 50, 10);
            return;
        }
        ItemStack zwojItem = this.findZwojPrzedluzenia(player.getInventory(), zwojType, zwojDisplayName);
        if (zwojItem == null) {
            this.guiHandler.openGUI(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cNie posiadasz zwoju przed\u0142u\u017cenia!", 0, 50, 10);
            return;
        }
        String duration = this.getDurationFromZwojLore(zwojItem);
        if (duration.isEmpty()) {
            this.guiHandler.openGUI(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cTen zw\u00f3j nie posiada czasu przed\u0142u\u017cenia!", 0, 50, 10);
            return;
        }
        try {
            LocalDateTime currentExpiration = LocalDateTime.parse(expirationDateString, DATE_FORMATTER);
            LocalDateTime newExpiration = this.calculateExpirationTime(currentExpiration, duration);
            if (newExpiration != null) {
                String displayName;
                String newExpirationDateString = newExpiration.format(DATE_FORMATTER);
                String string = displayName = isKostium ? this.guiHandler.getKostiumyHandler().getDatabase().getActiveKostiumDisplayName(player.getUniqueId()) : this.guiHandler.getPetyHandler().getDatabase().getActivePetDisplayName(player.getUniqueId());
                if (isKostium) {
                    this.guiHandler.getKostiumyHandler().getDatabase().setActiveKostium(player.getUniqueId(), activeKey, displayName, newExpirationDateString);
                } else {
                    this.guiHandler.getPetyHandler().getDatabase().setActivePet(player.getUniqueId(), activeKey, displayName, newExpirationDateString);
                }
                zwojItem.setAmount(zwojItem.getAmount() - 1);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7aPomy\u015blnie przed\u0142u\u017cono " + (isKostium ? "kostium" : "peta") + " o \u00a72" + duration, 0, 50, 10);
                this.guiHandler.openGUI(player);
            } else {
                this.guiHandler.openGUI(player);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cWyst\u0105pi\u0142 b\u0142\u0105d podczas obliczania czasu!", 0, 50, 10);
            }
        }
        catch (DateTimeParseException e) {
            this.guiHandler.openGUI(player);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cWyst\u0105pi\u0142 b\u0142\u0105d daty!", 0, 50, 10);
        }
    }

    private boolean removeActiveKostiumAndGiveBack(Player player, String activeKostiumKey) {
        KostiumyHandler.KostiumData data = this.guiHandler.getKostiumyHandler().getKostiumData(activeKostiumKey);
        if (data != null) {
            ItemStack itemToGive = this.createItemFromData(data, player, true);
            if (player.getInventory().firstEmpty() == -1) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cMasz pe\u0142ny ekwipunek!", 0, 50, 10);
                this.guiHandler.openGUI(player);
                return true;
            }
            player.getInventory().addItem(itemToGive);
        }
        this.guiHandler.getKostiumyHandler().getDatabase().removeActiveKostium(player.getUniqueId());
        this.armorHandler.removeArmor(player);
        return false;
    }

    private boolean removeActivePetAndGiveBack(Player player, String activePetKey) {
        PetyHandler.PetData data = this.guiHandler.getPetyHandler().getPetData(activePetKey);
        if (data != null) {
            ItemStack itemToGive = this.createItemFromData(data, player, false);
            if (player.getInventory().firstEmpty() == -1) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cMasz pe\u0142ny ekwipunek!", 0, 50, 10);
                this.guiHandler.openGUI(player);
                return true;
            }
            player.getInventory().addItem(itemToGive);
        }
        this.guiHandler.getPetyHandler().getDatabase().removeActivePet(player.getUniqueId());
        return false;
    }

    private ItemStack createItemFromData(Object dataObject, Player player, boolean isKostium) {
        List<String> dataLore;
        String expirationDate;
        String storedDisplayName;
        ItemStack item;
        if (isKostium) {
            KostiumyHandler.KostiumData data = (KostiumyHandler.KostiumData) dataObject;
            item = data.displayItem() != null ? data.displayItem().clone() : new ItemStack(data.type());
            storedDisplayName = this.guiHandler.getKostiumyHandler().getDatabase().getActiveKostiumDisplayName(player.getUniqueId());
            expirationDate = this.guiHandler.getKostiumyHandler().getDatabase().getKostiumExpiration(player.getUniqueId());
            dataLore = data.lore();
        } else {
            PetyHandler.PetData data = (PetyHandler.PetData) dataObject;
            item = this.guiHandler.getPetyHandler().getPetItem(data.itemKey());
            item = item == null ? new ItemStack(Material.STONE) : item.clone();
            storedDisplayName = this.guiHandler.getPetyHandler().getDatabase().getActivePetDisplayName(player.getUniqueId());
            expirationDate = this.guiHandler.getPetyHandler().getDatabase().getPetExpiration(player.getUniqueId());
            dataLore = data.lore();
        }
        ItemMeta meta = item.getItemMeta();
        String defaultDisplayName = isKostium ? ((KostiumyHandler.KostiumData) dataObject).displayName() : ((PetyHandler.PetData) dataObject).displayName();
        // Kostium/pet bez skonfigurowanej nazwy — bez tego translateAlternateColorCodes rzuca NPE.
        String defaultColored = defaultDisplayName == null ? "" : ChatColor.translateAlternateColorCodes('&', defaultDisplayName);
        String finalDisplayName = storedDisplayName != null ? storedDisplayName : defaultColored;
        ArrayList<String> lore = new ArrayList<String>();
        String duration = this.getDurationFromDisplayName(finalDisplayName);
        if (duration.isEmpty()) {
            duration = this.getDurationFromDisplayName(defaultDisplayName);
        }
        if (duration.isEmpty()) {
            String nbtDuration = NBTUtils.getString(item, isKostium ? KOSTIUM_DURATION_TAG : PET_DURATION_TAG);
            if (nbtDuration != null && !nbtDuration.isBlank()) {
                duration = nbtDuration;
            }
        }
        for (String line_2 : dataLore) {
            String formatted = line_2.replace("%czas%", duration.isEmpty() ? "NA ZAWSZE" : duration);
            if (formatted.contains("%uzycie%")) {
                formatted = formatted.replace("%uzycie%", expirationDate != null ? expirationDate : "NA ZAWSZE");
            }
            lore.add(dev.grzybalson.utils.ColorUtils.colorize(formatted));
        }
        if (meta != null) {
            meta.setDisplayName(finalDisplayName);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        String activeKey = isKostium ? this.guiHandler.getKostiumyHandler().getDatabase().getActiveKostium(player.getUniqueId()) : null;
        String armorColor = isKostium
            ? KostiumyHandler.getEffectiveColor(activeKey, ((KostiumyHandler.KostiumData) dataObject).displayName(), ((KostiumyHandler.KostiumData) dataObject).color())
            : ((PetyHandler.PetData) dataObject).color();
        if (item.getType().name().startsWith("LEATHER_") && armorColor != null) {
            this.guiHandler.applyColorToLeatherArmor(item, armorColor);
        }
        if (duration != null && !duration.isEmpty()) {
            NBTUtils.setString(item, isKostium ? KOSTIUM_DURATION_TAG : PET_DURATION_TAG, duration);
        }
        if (expirationDate != null && !expirationDate.isBlank()) {
            NBTUtils.setString(item, isKostium ? KOSTIUM_EXPIRATION_TAG : PET_EXPIRATION_TAG, expirationDate);
        }
        String activeKeyToStore = isKostium 
            ? this.guiHandler.getKostiumyHandler().getDatabase().getActiveKostium(player.getUniqueId()) 
            : this.guiHandler.getPetyHandler().getDatabase().getActivePet(player.getUniqueId());
        if (activeKeyToStore != null && !activeKeyToStore.isBlank()) {
            NBTUtils.setString(item, isKostium ? KOSTIUM_KEY_TAG : PET_KEY_TAG, activeKeyToStore);
        }
        return item;
    }

    private ItemStack findZwojPrzedluzenia(PlayerInventory inventory, Material type, String targetDisplayName) {
        String strippedTarget = ChatColor.stripColor(targetDisplayName).trim();
        for (ItemStack item : inventory.getContents()) {
            String displayName;
            String strippedDisplay;
            if (item == null || item.getType() != type || !item.hasItemMeta() || !(strippedDisplay = ChatColor.stripColor(displayName = item.getItemMeta().getDisplayName()).trim()).equalsIgnoreCase(strippedTarget)) continue;
            return item;
        }
        return null;
    }

    private String getDurationFromZwojLore(ItemStack zwojItem) {
        if (zwojItem == null || !zwojItem.hasItemMeta() || !zwojItem.getItemMeta().hasLore()) {
            return "";
        }
        Pattern timePattern = Pattern.compile("(\\d+[dhm])");
        for (String line_3 : Objects.requireNonNull(zwojItem.getItemMeta().getLore())) {
            String stripped = ChatColor.stripColor(line_3);
            Matcher m = timePattern.matcher(stripped);
            if (!m.find()) continue;
            return m.group(1);
        }
        return "";
    }

    private String findKostiumKeyByDisplayName(String itemDisplayName, Material itemType, Map<String, KostiumyHandler.KostiumData> kostiumy) {
        return findCachedItemKey(itemDisplayName, itemType, kostiumKeyByTypeAndBaseName);
    }

    private String findPetKeyByDisplayName(String itemDisplayName, Material itemType, Map<String, PetyHandler.PetData> pety) {
        return findCachedItemKey(itemDisplayName, itemType, petKeyByTypeAndBaseName);
    }

    private String findCachedItemKey(String itemDisplayName, Material itemType, Map<Material, Map<String, String>> lookup) {
        Map<String, String> byName = lookup.get(itemType);
        // Zaden kostium/pet nie uzywa tego materialu — nie liczymy regexow przy kazdym prawym kliku.
        if (byName == null && !canRebuildItemLookupCache()) {
            return null;
        }
        String handNameBase = this.getBaseNameFromDisplayName(itemDisplayName);
        String found = byName == null ? null : byName.get(handNameBase);
        if (found == null && canRebuildItemLookupCache()) {
            rebuildItemLookupCache();
            byName = lookup.get(itemType);
            found = byName == null ? null : byName.get(handNameBase);
        }
        return found;
    }

    /** Nietrafiony item nie moze przebudowywac calego cache co klik — dane zmienia tylko reload. */
    private boolean canRebuildItemLookupCache() {
        return System.currentTimeMillis() - this.lastLookupRebuildMillis >= LOOKUP_REBUILD_COOLDOWN_MILLIS;
    }

    public void rebuildItemLookupCache() {
        this.lastLookupRebuildMillis = System.currentTimeMillis();
        kostiumKeyByTypeAndBaseName.clear();
        petKeyByTypeAndBaseName.clear();

        for (Map.Entry<String, KostiumyHandler.KostiumData> entry : this.guiHandler.getKostiumyHandler().getKostiumy().entrySet()) {
            KostiumyHandler.KostiumData data = entry.getValue();
            cacheItemKey(kostiumKeyByTypeAndBaseName, data.type(), data.displayName(), entry.getKey());
        }

        if (this.guiHandler.getPetyHandler() == null) {
            return;
        }

        for (Map.Entry<String, PetyHandler.PetData> entry : this.guiHandler.getPetyHandler().getPety().entrySet()) {
            PetyHandler.PetData data = entry.getValue();
            ItemStack petItem = this.guiHandler.getPetyHandler().getPetItem(data.itemKey());
            Material expectedType = petItem != null ? petItem.getType() : Material.STONE;
            cacheItemKey(petKeyByTypeAndBaseName, expectedType, data.displayName(), entry.getKey());
        }
    }

    private void cacheItemKey(Map<Material, Map<String, String>> lookup, Material type, String displayName, String key) {
        String expectedDisplay = dev.grzybalson.utils.ColorUtils.colorize(displayName);
        String expectedBase = this.getBaseNameFromDisplayName(expectedDisplay);
        lookup.computeIfAbsent(type, ignored -> new HashMap<>()).put(expectedBase, key);
    }

    private String getBaseNameFromDisplayName(String displayName) {
        String stripped = stripAllColors(displayName);
        Matcher m = BASE_NAME_SUFFIX.matcher(stripped);
        if (m.find()) {
            return stripped.substring(0, m.start()).trim();
        }
        if (stripped.contains(" na ")) {
            return stripped.substring(0, stripped.lastIndexOf(" na ")).trim();
        }
        return stripped.trim();
    }

    private String getDurationFromDisplayName(String displayName) {
        String stripped = stripAllColors(displayName);
        Matcher m = DURATION_SUFFIX.matcher(stripped);
        if (m.find()) {
            return m.group(3);
        }
        return "";
    }

    private static String stripAllColors(String input) {
        if (input == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(dev.grzybalson.utils.ColorUtils.colorize(input));
        return HEX_TOKEN.matcher(stripped).replaceAll("").trim();
    }

    private String extractDateFromLoreLine(String strippedLine) {
        if (strippedLine == null) {
            return null;
        }
        Matcher m = DATE_IN_LORE.matcher(strippedLine);
        return m.find() ? m.group(1) : null;
    }

    private String getTimeLoreLine(List<String> lore) {
        for (String line_4 : lore) {
            String stripped = ChatColor.stripColor(line_4);
            if (!stripped.contains("u\u017cycie:") && !stripped.contains("wyga\u015bnie:") && !stripped.contains("NA ZAWSZE") && !DATE_LINE.matcher(stripped).matches()) continue;
            return line_4;
        }
        return null;
    }

    private LocalDateTime calculateExpirationTime(LocalDateTime start, String duration) {
        Matcher matcher = DURATION_PATTERN.matcher(duration);
        if (matcher.matches()) {
            int amount = Integer.parseInt(matcher.group(1));
            return switch (matcher.group(2)) {
                case "d" -> start.plusDays(amount);
                case "h" -> start.plusHours(amount);
                case "m" -> start.plusMinutes(amount);
                default -> null;
            };
        }
        return null;
    }

    private boolean isExpired(String expirationDate) {
        if (expirationDate == null 
                || expirationDate.isBlank() 
                || expirationDate.equalsIgnoreCase("NA ZAWSZE") 
                || expirationDate.equalsIgnoreCase("Brak")
                || expirationDate.equalsIgnoreCase("Nieokreślony")
                || expirationDate.equalsIgnoreCase("null")
                || expirationDate.startsWith("00:00:00")) {
            return false;
        }
        try {
            LocalDateTime expirationTime = LocalDateTime.parse(expirationDate.trim(), DATE_FORMATTER);
            return LocalDateTime.now().isAfter(expirationTime);
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }
}
