package dev.grzybalson.modules.generator;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class Generator {

    private String center;
    private GeneratorHologram hologram;
    private int radius;
    private int height;
    private long time;
    private Material defaultBlock;
    private String permission;
    private String worldName;
    private boolean legacyFormat = true;
    private final List<GeneratorChance> blocks;

    public Generator(String center, GeneratorHologram hologram, int radius, int height, long time, Material defaultBlock, String permission, List<GeneratorChance> blocks) {
        this(center, hologram, radius, height, time, defaultBlock, permission, blocks, null, true);
    }

    public Generator(
        String center,
        GeneratorHologram hologram,
        int radius,
        int height,
        long time,
        Material defaultBlock,
        String permission,
        List<GeneratorChance> blocks,
        String worldName,
        boolean legacyFormat
    ) {
        this.center = center;
        this.hologram = hologram;
        this.radius = radius;
        this.height = height;
        this.time = time;
        this.defaultBlock = defaultBlock;
        this.permission = permission;
        this.worldName = worldName;
        this.legacyFormat = legacyFormat;
        this.blocks = new ArrayList<>(blocks);
    }

    public Generator copy() {
        List<GeneratorChance> copiedBlocks = this.blocks.stream()
            .map(chance -> new GeneratorChance(chance.getChance(), chance.getMaterial()))
            .toList();
        Generator copy = new Generator(
            this.center,
            new GeneratorHologram(this.hologram.getName(), this.hologram.getLocation()),
            this.radius,
            this.height,
            this.time,
            this.defaultBlock,
            this.permission,
            copiedBlocks,
            this.worldName,
            this.legacyFormat
        );
        return copy;
    }

    public String getCenter() {
        return this.center;
    }

    public void setCenter(String center) {
        this.center = center;
    }

    public GeneratorHologram getHologram() {
        return this.hologram;
    }

    public void setHologram(GeneratorHologram hologram) {
        this.hologram = hologram;
    }

    public int getRadius() {
        return this.radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public int getHeight() {
        return this.height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public long getTime() {
        return this.time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public Material getDefaultBlock() {
        return this.defaultBlock;
    }

    public void setDefaultBlock(Material defaultBlock) {
        this.defaultBlock = defaultBlock;
    }

    public String getPermission() {
        return this.permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public List<GeneratorChance> getBlocks() {
        return this.blocks;
    }

    public String getWorldName() {
        return this.worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public boolean isLegacyFormat() {
        return this.legacyFormat;
    }

    public void setLegacyFormat(boolean legacyFormat) {
        this.legacyFormat = legacyFormat;
    }

    public boolean containsWool() {
        if (this.defaultBlock != null && isWoolMaterial(this.defaultBlock)) {
            return true;
        }
        if (this.blocks != null) {
            for (GeneratorChance chance : this.blocks) {
                if (chance != null && isWoolMaterial(chance.getMaterial())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isWoolMaterial(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.endsWith("_WOOL") || name.equals("WOOL");
    }
}
