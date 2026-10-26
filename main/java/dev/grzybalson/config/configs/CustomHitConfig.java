package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.LinkedHashMap;
import java.util.Map;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class CustomHitConfig extends OkaeriConfig {

    private double multiplierdmg = 1.10;
    private double multiplierarmor = 1.15;
    private double armorthoughnes = 0.0;
    private double maceMultiplierdmg = 1.0;
    private double maceSmashMultiplierdmg = 1.0;
    private double sharpnessMultiplier = 1.15;
    private double sharpnessLvl = 0.20;
    private double protectionLvl = 0.05;
    private double dmgoddripstone = 1.0;
    private double dmgodcrystal = 1.0;
    private double dmgoddvoida = 1.0;
    private Map<String, Double> maxDamageByArmorPieces = createDefaultMaxDamageByArmorPieces();
    private double absorptionMultiplier = 1.1;
    private Map<String, Double> effectModifiers = createDefaultEffectModifiers();
    private double elytraMaxdamage = 6.5;
    private double elytraDamagemultiplayer = 1.4;
    /** Zużycie dury elytry przy uderzeniach: >1 wolniej, <1 szybciej, 0 = nie bije się, 1 = vanilla. */
    private double elytraToughness = 1.0;
    private double criticalMultiplier = 1.20;
    private double noCritMultiplier = 1.0;
    private double levitationKnockbackMultiplier = 0.50;
    private double diamondArmorKnockbackMultiplier = 0.50;
    /** Miecz diamentowy bije mniej niz netherytowy (Sharp 5 diax < Sharp 6 neth). */
    private double diamondSwordMultiplier = 0.90;
    /** Pelny diax bierze wiecej dmg niz netherite (~12%). */
    private double diamondArmorTakenMultiplier = 1.12;

    private static Map<String, Double> createDefaultMaxDamageByArmorPieces() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("pieces4", 6.0);
        values.put("pieces3", 7.0);
        values.put("pieces2", 8.0);
        values.put("pieces1", 9.0);
        values.put("pieces0", 120.0);
        return values;
    }

    private static Map<String, Double> createDefaultEffectModifiers() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("strength", 2.0);
        values.put("weakness", -3.0);
        values.put("resistance", 0.0);
        return values;
    }

    public double getMultiplierdmg() {
        return multiplierdmg;
    }

    public void setMultiplierdmg(double multiplierdmg) {
        this.multiplierdmg = multiplierdmg;
    }

    public double getMultiplierarmor() {
        return multiplierarmor;
    }

    public void setMultiplierarmor(double multiplierarmor) {
        this.multiplierarmor = multiplierarmor;
    }

    public double getArmorthoughnes() {
        return armorthoughnes;
    }

    public void setArmorthoughnes(double armorthoughnes) {
        this.armorthoughnes = armorthoughnes;
    }

    public double getMaceMultiplierdmg() {
        return maceMultiplierdmg;
    }

    public void setMaceMultiplierdmg(double maceMultiplierdmg) {
        this.maceMultiplierdmg = maceMultiplierdmg;
    }

    public double getMaceSmashMultiplierdmg() {
        return maceSmashMultiplierdmg;
    }

    public void setMaceSmashMultiplierdmg(double maceSmashMultiplierdmg) {
        this.maceSmashMultiplierdmg = maceSmashMultiplierdmg;
    }

    public double getSharpnessMultiplier() {
        return sharpnessMultiplier;
    }

    public void setSharpnessMultiplier(double sharpnessMultiplier) {
        this.sharpnessMultiplier = sharpnessMultiplier;
    }

    public double getSharpnessLvl() {
        return sharpnessLvl;
    }

    public void setSharpnessLvl(double sharpnessLvl) {
        this.sharpnessLvl = sharpnessLvl;
    }

    public double getProtectionLvl() {
        return protectionLvl;
    }

    public void setProtectionLvl(double protectionLvl) {
        this.protectionLvl = protectionLvl;
    }

    public double getDmgoddripstone() {
        return dmgoddripstone;
    }

    public void setDmgoddripstone(double dmgoddripstone) {
        this.dmgoddripstone = dmgoddripstone;
    }

    public double getDmgodcrystal() {
        return dmgodcrystal;
    }

    public void setDmgodcrystal(double dmgodcrystal) {
        this.dmgodcrystal = dmgodcrystal;
    }

    public double getDmgoddvoida() {
        return dmgoddvoida;
    }

    public void setDmgoddvoida(double dmgoddvoida) {
        this.dmgoddvoida = dmgoddvoida;
    }

    public Map<String, Double> getMaxDamageByArmorPieces() {
        return maxDamageByArmorPieces;
    }

    public void setMaxDamageByArmorPieces(Map<String, Double> maxDamageByArmorPieces) {
        this.maxDamageByArmorPieces = maxDamageByArmorPieces;
    }

    public double getAbsorptionMultiplier() {
        return absorptionMultiplier;
    }

    public void setAbsorptionMultiplier(double absorptionMultiplier) {
        this.absorptionMultiplier = absorptionMultiplier;
    }

    public Map<String, Double> getEffectModifiers() {
        return effectModifiers;
    }

    public void setEffectModifiers(Map<String, Double> effectModifiers) {
        this.effectModifiers = effectModifiers;
    }

    public double getElytraMaxdamage() {
        return elytraMaxdamage;
    }

    public void setElytraMaxdamage(double elytraMaxdamage) {
        this.elytraMaxdamage = elytraMaxdamage;
    }

    public double getElytraDamagemultiplayer() {
        return elytraDamagemultiplayer;
    }

    public void setElytraDamagemultiplayer(double elytraDamagemultiplayer) {
        this.elytraDamagemultiplayer = elytraDamagemultiplayer;
    }

    public double getElytraToughness() {
        return elytraToughness;
    }

    public void setElytraToughness(double elytraToughness) {
        this.elytraToughness = elytraToughness;
    }

    public double getCriticalMultiplier() {
        return criticalMultiplier;
    }

    public void setCriticalMultiplier(double criticalMultiplier) {
        this.criticalMultiplier = criticalMultiplier;
    }

    public double getNoCritMultiplier() {
        return noCritMultiplier;
    }

    public void setNoCritMultiplier(double noCritMultiplier) {
        this.noCritMultiplier = noCritMultiplier;
    }

    public double getLevitationKnockbackMultiplier() {
        return levitationKnockbackMultiplier;
    }

    public void setLevitationKnockbackMultiplier(double levitationKnockbackMultiplier) {
        this.levitationKnockbackMultiplier = levitationKnockbackMultiplier;
    }

    public double getDiamondArmorKnockbackMultiplier() {
        return diamondArmorKnockbackMultiplier;
    }

    public void setDiamondArmorKnockbackMultiplier(double diamondArmorKnockbackMultiplier) {
        this.diamondArmorKnockbackMultiplier = diamondArmorKnockbackMultiplier;
    }

    public double getDiamondSwordMultiplier() {
        return diamondSwordMultiplier;
    }

    public void setDiamondSwordMultiplier(double diamondSwordMultiplier) {
        this.diamondSwordMultiplier = diamondSwordMultiplier;
    }

    public double getDiamondArmorTakenMultiplier() {
        return diamondArmorTakenMultiplier;
    }

    public void setDiamondArmorTakenMultiplier(double diamondArmorTakenMultiplier) {
        this.diamondArmorTakenMultiplier = diamondArmorTakenMultiplier;
    }

}
