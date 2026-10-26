package dev.grzybalson.modules.customhit;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CustomHitConfig;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CustomHitsService {

    private final HollyCore plugin;
    private final CustomHitConfig config;

    private double damageMultiplier;
    private double armorMultiplier;
    private double armorToughness;
    private double maceDamageMultiplier;
    private double maceSmashDamageMultiplier;
    private double sharpnessMultiplier;
    private double sharpnessLevelBonus;
    private double protectionLevelBonus;
    private double dripstoneDamageMultiplier;
    private double crystalDamageMultiplier;
    private double voidDamageMultiplier;
    private Map<String, Double> maxDamageByArmorPieces;
    private double absorptionMultiplier;
    private Map<String, Double> effectModifiers;
    private double elytraMaxDamage;
    private double elytraDamageMultiplier;
    /** Zużycie dury elytry (jak armorthoughnes): >1 wolniej, <1 szybciej, 0 = nie bije się. */
    private double elytraToughness;
    private double criticalMultiplier;
    private double noCritMultiplier;
    private double levitationKnockbackMultiplier;
    private double diamondArmorKnockbackMultiplier;
    private double diamondSwordMultiplier;
    private double diamondArmorTakenMultiplier;

    private static final long VICTIM_HIT_COOLDOWN_MS = 400L;
    private final java.util.Map<java.util.UUID, Long> victimHitCooldowns = new java.util.concurrent.ConcurrentHashMap<>();

    public CustomHitsService(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager().getCustomHitConfig();
        reload();
    }

    public void reload() {
        this.config.load(true);

        this.damageMultiplier = sane(config.getMultiplierdmg());
        this.armorMultiplier = sane(config.getMultiplierarmor());
        this.armorToughness = saneArmorToughness(config.getArmorthoughnes());
        this.maceDamageMultiplier = sane(config.getMaceMultiplierdmg());
        this.maceSmashDamageMultiplier = sane(config.getMaceSmashMultiplierdmg());
        this.sharpnessMultiplier = sane(config.getSharpnessMultiplier());
        this.sharpnessLevelBonus = saneBonus(config.getSharpnessLvl());
        this.protectionLevelBonus = saneBonus(config.getProtectionLvl());
        this.dripstoneDamageMultiplier = sane(config.getDmgoddripstone());
        this.crystalDamageMultiplier = sane(config.getDmgodcrystal());
        this.voidDamageMultiplier = sane(config.getDmgoddvoida());
        this.maxDamageByArmorPieces = saneMaxDamageByArmorPieces(config.getMaxDamageByArmorPieces());
        this.absorptionMultiplier = sane(config.getAbsorptionMultiplier());
        this.effectModifiers = saneEffectModifiers(config.getEffectModifiers());
        this.elytraMaxDamage = saneMaxDamage(config.getElytraMaxdamage(), 6.5);
        this.elytraDamageMultiplier = sane(config.getElytraDamagemultiplayer());
        this.elytraToughness = saneArmorToughness(config.getElytraToughness());
        this.criticalMultiplier = sane(config.getCriticalMultiplier());
        this.noCritMultiplier = sane(config.getNoCritMultiplier());
        this.levitationKnockbackMultiplier = saneKnockbackMultiplier(config.getLevitationKnockbackMultiplier());
        this.diamondArmorKnockbackMultiplier = saneKnockbackMultiplier(config.getDiamondArmorKnockbackMultiplier());
        this.diamondSwordMultiplier = saneTierMultiplier(config.getDiamondSwordMultiplier(), 0.90);
        this.diamondArmorTakenMultiplier = saneTierMultiplier(config.getDiamondArmorTakenMultiplier(), 1.12);

        config.setMultiplierdmg(this.damageMultiplier);
        config.setMultiplierarmor(this.armorMultiplier);
        config.setArmorthoughnes(this.armorToughness);
        config.setMaceMultiplierdmg(this.maceDamageMultiplier);
        config.setMaceSmashMultiplierdmg(this.maceSmashDamageMultiplier);
        config.setSharpnessMultiplier(this.sharpnessMultiplier);
        config.setSharpnessLvl(this.sharpnessLevelBonus);
        config.setProtectionLvl(this.protectionLevelBonus);
        config.setDmgoddripstone(this.dripstoneDamageMultiplier);
        config.setDmgodcrystal(this.crystalDamageMultiplier);
        config.setDmgoddvoida(this.voidDamageMultiplier);
        config.setMaxDamageByArmorPieces(this.maxDamageByArmorPieces);
        config.setAbsorptionMultiplier(this.absorptionMultiplier);
        config.setEffectModifiers(this.effectModifiers);
        config.setElytraMaxdamage(this.elytraMaxDamage);
        config.setElytraDamagemultiplayer(this.elytraDamageMultiplier);
        config.setElytraToughness(this.elytraToughness);
        config.setCriticalMultiplier(this.criticalMultiplier);
        config.setNoCritMultiplier(this.noCritMultiplier);
        config.setLevitationKnockbackMultiplier(this.levitationKnockbackMultiplier);
        config.setDiamondArmorKnockbackMultiplier(this.diamondArmorKnockbackMultiplier);
        config.setDiamondSwordMultiplier(this.diamondSwordMultiplier);
        config.setDiamondArmorTakenMultiplier(this.diamondArmorTakenMultiplier);
        config.save();
    }

    private static double saneTierMultiplier(double v, double fallback) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0.0) {
            return fallback;
        }
        return Math.min(5.0, v);
    }

    private static double sane(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 1.0;
        if (v <= 0.0) return 1.0;
        if (v > 1000.0) return 1000.0;
        return v;
    }

    private static double saneArmorToughness(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 1.0;
        if (v == 0.0) return 0.0;
        if (v < 0.0) return 1.0;
        if (v > 1000.0) return 1000.0;
        return v;
    }

    private static double saneBonus(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        if (v < 0.0) return 0.0;
        if (v > 1000.0) return 1000.0;
        return v;
    }

    private static double saneKnockbackMultiplier(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.5;
        if (v < 0.0) return 0.0;
        if (v > 10.0) return 10.0;
        return v;
    }

    private static double saneMaxDamage(double v, double fallback) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return fallback;
        if (v < 0.0) return fallback;
        if (v > 2048.0) return 2048.0;
        return v;
    }

    private static Map<String, Double> saneMaxDamageByArmorPieces(Map<String, Double> input) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("pieces4", saneMaxDamage(getMapValue(input, "pieces4", 6.0), 6.0));
        values.put("pieces3", saneMaxDamage(getMapValue(input, "pieces3", 7.0), 7.0));
        values.put("pieces2", saneMaxDamage(getMapValue(input, "pieces2", 8.0), 8.0));
        values.put("pieces1", saneMaxDamage(getMapValue(input, "pieces1", 9.0), 9.0));
        values.put("pieces0", saneMaxDamage(getMapValue(input, "pieces0", 120.0), 120.0));
        return values;
    }

    private static Map<String, Double> saneEffectModifiers(Map<String, Double> input) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("strength", saneSigned(getMapValue(input, "strength", 2.0)));
        values.put("weakness", saneSigned(getMapValue(input, "weakness", -3.0)));
        values.put("resistance", saneSigned(getMapValue(input, "resistance", -3.0)));
        return values;
    }

    private static double getMapValue(Map<String, Double> input, String key, double fallback) {
        if (input == null) return fallback;
        Double direct = input.get(key);
        if (direct != null) return direct;
        for (Map.Entry<String, Double> entry : input.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key) && entry.getValue() != null) {
                return entry.getValue();
            }
        }
        return fallback;
    }

    private static double saneSigned(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        if (v > 1000.0) return 1000.0;
        if (v < -1000.0) return -1000.0;
        return v;
    }

    private void saveConfig() {
        config.save();
    }

    public double getDamageMultiplier() { return damageMultiplier; }
    public double getArmorMultiplier() { return armorMultiplier; }
    public double getArmorToughness() { return armorToughness; }
    public double getMaceDamageMultiplier() { return maceDamageMultiplier; }
    public double getMaceSmashDamageMultiplier() { return maceSmashDamageMultiplier; }
    public double getSharpnessMultiplier() { return sharpnessMultiplier; }
    public double getSharpnessLevelBonus() { return sharpnessLevelBonus; }
    public double getProtectionLevelBonus() { return protectionLevelBonus; }
    public double getDripstoneDamageMultiplier() { return dripstoneDamageMultiplier; }
    public double getCrystalDamageMultiplier() { return crystalDamageMultiplier; }
    public double getVoidDamageMultiplier() { return voidDamageMultiplier; }
    public double getMaxDamageForArmorPieces(int armorPieces) {
        int clamped = Math.max(0, Math.min(4, armorPieces));
        return maxDamageByArmorPieces.getOrDefault("pieces" + clamped, 120.0);
    }
    public double getAbsorptionMultiplier() { return absorptionMultiplier; }
    public double getEffectModifier(String effectName) {
        if (effectName == null) return 0.0;
        return effectModifiers.getOrDefault(effectName.toLowerCase(Locale.ROOT), 0.0);
    }
    public double getElytraMaxDamage() { return elytraMaxDamage; }
    public double getElytraDamageMultiplier() { return elytraDamageMultiplier; }
    public double getElytraToughness() { return elytraToughness; }
    public double getCriticalMultiplier() { return criticalMultiplier; }
    public double getNoCritMultiplier() { return noCritMultiplier; }
    public double getLevitationKnockbackMultiplier() { return levitationKnockbackMultiplier; }
    public double getDiamondArmorKnockbackMultiplier() { return diamondArmorKnockbackMultiplier; }
    public double getDiamondSwordMultiplier() { return diamondSwordMultiplier; }
    public double getDiamondArmorTakenMultiplier() { return diamondArmorTakenMultiplier; }
    public HollyCore getPlugin() { return plugin; }
    public boolean tryRegisterVictimHit(java.util.UUID victimId) {
        long now = System.currentTimeMillis();
        Long lastHit = victimHitCooldowns.get(victimId);
        if (lastHit != null && now - lastHit < VICTIM_HIT_COOLDOWN_MS) {
            return false;
        }

        victimHitCooldowns.put(victimId, now);
        return true;
    }

    public void setDamageMultiplier(double v) {
        this.damageMultiplier = sane(v);
        config.setMultiplierdmg(this.damageMultiplier);
        saveConfig();
    }

    public void setArmorMultiplier(double v) {
        this.armorMultiplier = sane(v);
        config.setMultiplierarmor(this.armorMultiplier);
        saveConfig();
    }

    public void setArmorToughness(double v) {
        this.armorToughness = saneArmorToughness(v);
        config.setArmorthoughnes(this.armorToughness);
        saveConfig();
    }

    public void setMaceDamageMultiplier(double v) {
        this.maceDamageMultiplier = sane(v);
        config.setMaceMultiplierdmg(this.maceDamageMultiplier);
        saveConfig();
    }

    public void setMaceSmashDamageMultiplier(double v) {
        this.maceSmashDamageMultiplier = sane(v);
        config.setMaceSmashMultiplierdmg(this.maceSmashDamageMultiplier);
        saveConfig();
    }

    public void setSharpnessMultiplier(double v) {
        this.sharpnessMultiplier = sane(v);
        config.setSharpnessMultiplier(this.sharpnessMultiplier);
        saveConfig();
    }

    public void setSharpnessLevelBonus(double v) {
        this.sharpnessLevelBonus = saneBonus(v);
        config.setSharpnessLvl(this.sharpnessLevelBonus);
        saveConfig();
    }

    public void setProtectionLevelBonus(double v) {
        this.protectionLevelBonus = saneBonus(v);
        config.setProtectionLvl(this.protectionLevelBonus);
        saveConfig();
    }

    public void setDripstoneDamageMultiplier(double v) {
        this.dripstoneDamageMultiplier = sane(v);
        config.setDmgoddripstone(this.dripstoneDamageMultiplier);
        saveConfig();
    }

    public void setCrystalDamageMultiplier(double v) {
        this.crystalDamageMultiplier = sane(v);
        config.setDmgodcrystal(this.crystalDamageMultiplier);
        saveConfig();
    }

    public void setVoidDamageMultiplier(double v) {
        this.voidDamageMultiplier = sane(v);
        config.setDmgoddvoida(this.voidDamageMultiplier);
        saveConfig();
    }

    public void setLevitationKnockbackMultiplier(double v) {
        this.levitationKnockbackMultiplier = saneKnockbackMultiplier(v);
        config.setLevitationKnockbackMultiplier(this.levitationKnockbackMultiplier);
        saveConfig();
    }

    public void setDiamondArmorKnockbackMultiplier(double v) {
        this.diamondArmorKnockbackMultiplier = saneKnockbackMultiplier(v);
        config.setDiamondArmorKnockbackMultiplier(this.diamondArmorKnockbackMultiplier);
        saveConfig();
    }

    public void resetDamageMultiplier() { setDamageMultiplier(1.0); }
    public void resetArmorMultiplier() { setArmorMultiplier(1.0); }
    public void resetArmorToughness() { setArmorToughness(1.0); }
    public void resetMaceDamageMultiplier() { setMaceDamageMultiplier(1.0); }
    public void resetMaceSmashDamageMultiplier() { setMaceSmashDamageMultiplier(1.0); }
    public void resetSharpnessMultiplier() { setSharpnessMultiplier(1.0); }
    public void resetSharpnessLevelBonus() { setSharpnessLevelBonus(0.0); }
    public void resetDripstoneDamageMultiplier() { setDripstoneDamageMultiplier(1.0); }
    public void resetCrystalDamageMultiplier() { setCrystalDamageMultiplier(1.0); }
    public void resetVoidDamageMultiplier() { setVoidDamageMultiplier(1.0); }
}
