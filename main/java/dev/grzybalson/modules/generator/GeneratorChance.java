package dev.grzybalson.modules.generator;

import org.bukkit.Material;

public class GeneratorChance {

    private double chance;
    private Material material;

    public GeneratorChance(double chance, Material material) {
        this.chance = chance;
        this.material = material;
    }

    public double getChance() {
        return this.chance;
    }

    public void setChance(double chance) {
        this.chance = chance;
    }

    public Material getMaterial() {
        return this.material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }
}
