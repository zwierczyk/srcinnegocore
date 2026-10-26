package dev.grzybalson.anarchiagg.ksiegi;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

public class KsiegiEffectsListener extends BukkitRunnable implements Listener {
    private final JavaPlugin plugin;
    private final KsiegiManager manager;
    private int passiveCursor;
    private final double unikChance;
    private final double regeneracjiChance;
    private final int regeneracjiDurationTicks;
    private final int regeneracjiAmplifier;
    private final double otrucieChance;
    private final int otrucieDurationTicks;
    private final int otrucieAmplifier;
    private final double spowolnienieChance;
    private final int spowolnienieDurationTicks;
    private final int spowolnienieAmplifier;
    private final double krytyczneChance;
    private final double krytyczneMultiplier;
    private final double wampiryzmChance;
    private final double wampiryzmHealPercent;
    private final double wytrzymalosciNegateChance;

    public KsiegiEffectsListener(JavaPlugin plugin, KsiegiManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        var config = AnarchiaGGSettings.getConfig(plugin);
        this.unikChance = clampChance(config.getDouble("ksiegi.books.unik.chance", 0.15));
        this.regeneracjiChance = clampChance(config.getDouble("ksiegi.books.regeneracji.chance", 0.18));
        this.regeneracjiDurationTicks = config.getInt("ksiegi.books.regeneracji.duration-ticks", 60);
        this.regeneracjiAmplifier = config.getInt("ksiegi.books.regeneracji.amplifier", 1);
        this.otrucieChance = clampChance(config.getDouble("ksiegi.books.otrucie.chance", 0.20));
        this.otrucieDurationTicks = config.getInt("ksiegi.books.otrucie.duration-ticks", 60);
        this.otrucieAmplifier = config.getInt("ksiegi.books.otrucie.amplifier", 0);
        this.spowolnienieChance = clampChance(config.getDouble("ksiegi.books.spowolnienie.chance", 0.20));
        this.spowolnienieDurationTicks = config.getInt("ksiegi.books.spowolnienie.duration-ticks", 60);
        this.spowolnienieAmplifier = config.getInt("ksiegi.books.spowolnienie.amplifier", 0);
        this.krytyczneChance = clampChance(config.getDouble("ksiegi.books.krytyczne.chance", 0.20));
        this.krytyczneMultiplier = Math.max(1.0D, config.getDouble("ksiegi.books.krytyczne.multiplier", 1.35D));
        this.wampiryzmChance = clampChance(config.getDouble("ksiegi.books.wampiryzm.chance", 0.25));
        this.wampiryzmHealPercent = Math.max(0.0D, config.getDouble("ksiegi.books.wampiryzm.heal-percent", 0.30D));
        this.wytrzymalosciNegateChance = clampChance(config.getDouble("ksiegi.books.wytrzymalosci.negate-chance", 0.35));
    }

    @Override
    public void run() {
        // Stagger — nie skanuj zbroi wszystkich naraz co refresh-ticks.
        Player[] online = Bukkit.getOnlinePlayers().toArray(Player[]::new);
        if (online.length == 0) {
            return;
        }
        int batch = Math.max(1, Math.min(6, (online.length + 4) / 5));
        for (int i = 0; i < batch; i++) {
            int index = Math.floorMod(passiveCursor + i, online.length);
            Player player = online[index];
            if (player != null && player.isOnline()) {
                applyPassiveEffects(player);
            }
        }
        passiveCursor = Math.floorMod(passiveCursor + batch, online.length);
    }

    // USUNIĘTO updatePlayerInventoryLore - było głównym źródłem lagów
    // Aktualizacja lore odbywa się teraz tylko w event handlerach (onInventoryClick, onPickup, etc)

    private boolean hasArmorEnchant(Player player, KsiegiManager.BookType type) {
        if (player == null) return false;
        PlayerInventory inv = player.getInventory();
        for (ItemStack armor : inv.getArmorContents()) {
            if (manager.hasEnchant(armor, type)) {
                return true;
            }
        }
        return false;
    }

    private void applyPassiveEffects(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack mainHand = inv.getItemInMainHand();

        if (hasArmorEnchant(player, KsiegiManager.BookType.NIEPODPALANIE)) {
            addOrRefresh(player, PotionEffectType.FIRE_RESISTANCE, 100, 0);
        }
        if (hasArmorEnchant(player, KsiegiManager.BookType.SZYBKOSCI)) {
            addOrRefresh(player, PotionEffectType.SPEED, 100, 0);
        }
        if (manager.hasEnchant(mainHand, KsiegiManager.BookType.POSPIECH)
                && mainHand != null
                && mainHand.getType().name().endsWith("_PICKAXE")) {
            addOrRefresh(player, PotionEffectType.FAST_DIGGING, 100, 1);
        }
    }

    private void addOrRefresh(Player player, PotionEffectType type, int duration, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && current.getAmplifier() >= amplifier && current.getDuration() > 40) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, false));
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent) || !(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!event.isCancelled()
                && hasArmorEnchant(player, KsiegiManager.BookType.UNIK)
                && chance(unikChance)) {
            event.setCancelled(true);
            return;
        }

        if (!event.isCancelled()
                && hasArmorEnchant(player, KsiegiManager.BookType.REGENERACJI)
                && chance(regeneracjiChance)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regeneracjiDurationTicks, regeneracjiAmplifier, true, false, false));
        }
    }

    @EventHandler
    public void onDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) {
            return;
        }

        Entity target = event.getEntity();
        if (manager.hasEnchant(weapon, KsiegiManager.BookType.OTRUCIE)
                && target instanceof LivingEntity livingTarget
                && chance(otrucieChance)) {
            livingTarget.addPotionEffect(new PotionEffect(
                    PotionEffectType.POISON,
                    otrucieDurationTicks,
                    otrucieAmplifier,
                    true,
                    false,
                    false
            ));
        }

        if (manager.hasEnchant(weapon, KsiegiManager.BookType.SPOWOLNIENIE)
                && target instanceof LivingEntity livingTarget
                && chance(spowolnienieChance)) {
            livingTarget.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW,
                    spowolnienieDurationTicks,
                    spowolnienieAmplifier,
                    true,
                    false,
                    false
            ));
        }

        if (manager.hasEnchant(weapon, KsiegiManager.BookType.KRYTYCZNE)
                && chance(krytyczneChance)) {
            event.setDamage(event.getDamage() * krytyczneMultiplier);
        }

        if (manager.hasEnchant(weapon, KsiegiManager.BookType.WAMPIRYZM)
                && chance(wampiryzmChance)
                && attacker.isValid()) {
            double heal = event.getFinalDamage() * wampiryzmHealPercent;
            AttributeInstance maxHealthAttr = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0D;
            attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + heal));
        }
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (manager.hasEnchant(event.getItem(), KsiegiManager.BookType.WYTRZYMALOSCI)
                && chance(wytrzymalosciNegateChance)) {
            event.setCancelled(true);
        }
    }

    private double clampChance(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private boolean chance(double value) {
        return ThreadLocalRandom.current().nextDouble() < value;
    }
}
