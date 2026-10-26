package dev.grzybalson.anarchiagg.customitems;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.plugin.java.JavaPlugin;
import dev.grzybalson.anarchiagg.customitems.items.HydroTrojzab;
import dev.grzybalson.anarchiagg.customitems.items.RozdzkaIluzjonisty;

public class DeathReset implements Listener {

    private final Material hydroklatkaMaterial;
    private final Material watacukrowaMaterial;
    private final Material balonikMaterial;
    private final Material hydrotrojzabMaterial;
    private final Material blokwidmoMaterial;
    private final Material ulepszonaelytraMaterial;
    private final Material turbotrapMaterial;
    private final Material parawanMaterial;
    private final Material sakwaMaterial;
    private final Material boskitoporMaterial;
    private final Material zlamaneserduszkoMaterial;
    private final Material bombardaMaterial;
    private final Material lizakMaterial;
    private final Material nielotaMaterial;
    private final Material rozakupidynaMaterial;
    private final Material kroliczymieczMaterial;
    private final Material kostkarubikaMaterial;
    private final Material marchewkowymieczMaterial;
    private final Material koronaMaterial;
    private final Material siekieragrinczaMaterial;
    private final Material kosaMaterial;
    private final Material krewwampiraMaterial;
    private final Material totemulaskawieniaMaterial;
    private final Material sniezkateleportacjiMaterial;
    private final Material zatrutyolowekMaterial;
    private final Material excaliburMaterial;
    private final Material jablkoMaterial;
    private final Material cieplemlekoMaterial;
    private final Material piernikMaterial;
    private final Material lopatagrinczaMaterial;
    private final Material smoczymieczMaterial;
    private final Material jajkoOlafaMaterial;
    private final Material cudownalatarniaMaterial;
    private final Material rozdzkailuzjonistyMaterial;
    private final Material sniezynkaMaterial;
    private final Material dynamitMaterial;
    private final Material anarchiczeneserceMaterial;

    public DeathReset(JavaPlugin plugin) {
        anarchiczeneserceMaterial = getMaterialFlexible(plugin, "eventowki.anarchiczeneserce", "RED_DYE");
        hydroklatkaMaterial = getMaterialFlexible(plugin, "eventowki.hydroklatka", "BLAZE_ROD");
        watacukrowaMaterial = getMaterialFlexible(plugin, "eventowki.watacukrowa", "PINK_DYE");
        balonikMaterial = getMaterialFlexible(plugin, "eventowki.balonik", "PLAYER_HEAD");
        dynamitMaterial = getMaterialFlexible(plugin, "eventowki.dynamit", "RED_CANDLE");
        hydrotrojzabMaterial = getMaterialFlexible(plugin, "eventowki.hydrotrojzab", "BOW");
        blokwidmoMaterial = getMaterialFlexible(plugin, "eventowki.blokwidmo", "STRUCTURE_BLOCK");
        ulepszonaelytraMaterial = getMaterialFlexible(plugin, "eventowki.ulepszonaelytra", "ELYTRA");
        turbotrapMaterial = getMaterialFlexible(plugin, "eventowki.turbotrap", "AIR");
        parawanMaterial = getMaterialFlexible(plugin, "eventowki.parawan", "FEATHER");
        sakwaMaterial = getMaterialFlexible(plugin, "eventowki.sakwa", "RABBIT_FOOT");
        boskitoporMaterial = getMaterialFlexible(plugin, "eventowki.boskitopor", "IRON_AXE");
        zlamaneserduszkoMaterial = getMaterialFlexible(plugin, "eventowki.zlamaneserduszko", "PURPLE_DYE");
        bombardaMaterial = getMaterialFlexible(plugin, "eventowki.bombarda", "FIRE_CHARGE");
        lizakMaterial = getMaterialFlexible(plugin, "eventowki.lizak", "ALLIUM");
        nielotaMaterial = getMaterialFlexible(plugin, "eventowki.nielota", "FISHING_ROD");
        rozakupidynaMaterial = getMaterialFlexible(plugin, "eventowki.rozakupidyna", "POPPY");
        kroliczymieczMaterial = getMaterialFlexible(plugin, "eventowki.kroliczymiecz", "NETHERITE_SWORD");
        kostkarubikaMaterial = getMaterialFlexible(plugin, "eventowki.kostkarubika", "PLAYER_HEAD");
        marchewkowymieczMaterial = getMaterialFlexible(plugin, "eventowki.marchewkowymiecz", "GOLDEN_SWORD");
        koronaMaterial = getMaterialFlexible(plugin, "eventowki.korona", "GOLDEN_HELMET");
        siekieragrinczaMaterial = getMaterialFlexible(plugin, "eventowki.siekieragrincza", "GOLDEN_AXE");
        kosaMaterial = getMaterialFlexible(plugin, "eventowki.kosa", "NETHERITE_HOE");
        krewwampiraMaterial = getMaterialFlexible(plugin, "eventowki.krewwampira", "BEETROOT_SOUP");
        totemulaskawieniaMaterial = getMaterialFlexible(plugin, "eventowki.totemulaskawienia", "AIR");
        sniezkateleportacjiMaterial = getMaterialFlexible(plugin, "eventowki.sniezkateleportacji", "SNOWBALL");
        zatrutyolowekMaterial = getMaterialFlexible(plugin, "eventowki.zatrutyolowek", "LIME_CANDLE");
        excaliburMaterial = getMaterialFlexible(plugin, "eventowki.excalibur", "AIR");
        jablkoMaterial = getMaterialFlexible(plugin, "eventowki.jablko", "ENCHANTED_GOLDEN_APPLE");
        cieplemlekoMaterial = getMaterialFlexible(plugin, "eventowki.cieplemleko", "BOWL");
        piernikMaterial = getMaterialFlexible(plugin, "eventowki.piernik", "COOKIE");
        lopatagrinczaMaterial = getMaterialFlexible(plugin, "eventowki.lopatagrincza", "DIAMOND_SHOVEL");
        smoczymieczMaterial = getMaterialFlexible(plugin, "eventowki.smoczymiecz", "NETHERITE_SWORD");
        jajkoOlafaMaterial = getMaterialFlexible(plugin, "eventowki.jajkoolafa", "AIR");
        cudownalatarniaMaterial = getMaterialFlexible(plugin, "eventowki.cudownalatarnia", "AIR");
        rozdzkailuzjonistyMaterial = getMaterialFlexible(plugin, "eventowki.rozdzkailuzjonisty", "AIR");
        sniezynkaMaterial = getMaterialFlexible(plugin, "eventowki.sniezynka", "AIR");
    }

    private Material getMaterialFlexible(JavaPlugin plugin, String pathPrefix, String def) {
        String itemPath = pathPrefix + ".item";
        String materialPath = pathPrefix + ".material";

        String name = AnarchiaGGSettings.getConfig(plugin).getString(itemPath);
        if (name == null) {
            name = AnarchiaGGSettings.getConfig(plugin).getString(materialPath);
        }
        if (name == null) {
            name = def;
        }

        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Niepoprawny materiał w configu: " + pathPrefix + " (" + itemPath + "/" + materialPath + ") = " + name);
            return Material.AIR;
        }
    }

    @EventHandler
    public void onRiptide(PlayerRiptideEvent event) {
        if (event.getItem().getItemMeta().getDisplayName().contains("Anarchiczny")) {
            event.getPlayer().setCooldown(Material.TRIDENT, 20 * 10);
        } else {
            event.getPlayer().setCooldown(Material.TRIDENT, 20 * 12);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        player.setCooldown(jajkoOlafaMaterial, 0);
        player.setCooldown(cudownalatarniaMaterial, 0);
        player.setCooldown(rozdzkailuzjonistyMaterial, 0);
        player.setCooldown(sniezynkaMaterial, 0);
        player.setCooldown(hydroklatkaMaterial, 0);
        player.setCooldown(watacukrowaMaterial, 0);
        player.setCooldown(balonikMaterial, 0);
        player.setCooldown(hydrotrojzabMaterial, 0);
        player.setCooldown(blokwidmoMaterial, 0);
        player.setCooldown(ulepszonaelytraMaterial, 0);
        player.setCooldown(turbotrapMaterial, 0);
        player.setCooldown(parawanMaterial, 0);
        player.setCooldown(sakwaMaterial, 0);
        player.setCooldown(boskitoporMaterial, 0);
        player.setCooldown(zlamaneserduszkoMaterial, 0);
        player.setCooldown(bombardaMaterial, 0);
        player.setCooldown(lizakMaterial, 0);
        player.setCooldown(nielotaMaterial, 0);
        player.setCooldown(rozakupidynaMaterial, 0);
        player.setCooldown(kroliczymieczMaterial, 0);
        player.setCooldown(kostkarubikaMaterial, 0);
        player.setCooldown(marchewkowymieczMaterial, 0);
        player.setCooldown(koronaMaterial, 0);
        player.setCooldown(siekieragrinczaMaterial, 0);
        player.setCooldown(kosaMaterial, 0);
        player.setCooldown(krewwampiraMaterial, 0);
        player.setCooldown(totemulaskawieniaMaterial, 0);
        player.setCooldown(sniezkateleportacjiMaterial, 0);
        player.setCooldown(zatrutyolowekMaterial, 0);
        player.setCooldown(excaliburMaterial, 0);
        player.setCooldown(jablkoMaterial, 0);
        player.setCooldown(cieplemlekoMaterial, 0);
        player.setCooldown(piernikMaterial, 0);
        player.setCooldown(lopatagrinczaMaterial, 0);
        player.setCooldown(smoczymieczMaterial, 0);
        player.setCooldown(dynamitMaterial, 0);
        player.setCooldown(anarchiczeneserceMaterial, 0);
        player.setCooldown(Material.CROSSBOW, 0);
        if (nielotaMaterial != Material.FISHING_ROD) {
            player.setCooldown(Material.FISHING_ROD, 0);
        }
        HydroTrojzab.resetCooldowns(player.getUniqueId());
        RozdzkaIluzjonisty.resetCooldowns(player.getUniqueId());
    }
}
