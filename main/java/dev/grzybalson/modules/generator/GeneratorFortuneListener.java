package dev.grzybalson.modules.generator;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * OPTYMALIZACJA: Obsługa Fortune dla generatorów
 * Bloki generatora normalnie nie respektują Fortune, bo są regenerowane przez setType().
 * Ten listener dodaje dodatkowe dropy bazując na poziomie Fortune na łopacie.
 */
public class GeneratorFortuneListener implements Listener {

    private final GeneratorManager generatorManager;

    public GeneratorFortuneListener(GeneratorManager generatorManager) {
        this.generatorManager = generatorManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Sprawdź czy gracz kopie w generatorze
        if (generatorManager.findGeneratorAt(event.getBlock().getLocation()) == null) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        // Sprawdź czy łopata ma Fortune
        if (tool == null || !tool.hasItemMeta()) {
            return;
        }

        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS);
        if (fortuneLevel <= 0) {
            return;
        }

        Block block = event.getBlock();
        Material blockType = block.getType();

        // Tylko dla bloków które mogą dropować więcej z Fortune
        if (!canMultiplyWithFortune(blockType)) {
            return;
        }

        // Oblicz bonus drop (Fortune I-III)
        // Fortune wzór: 1 + random(0, fortune_level)
        int bonusDrops = ThreadLocalRandom.current().nextInt(0, fortuneLevel + 1);

        if (bonusDrops > 0) {
            // Dodaj bonus dropy
            ItemStack dropItem = new ItemStack(blockType, bonusDrops);
            block.getWorld().dropItemNaturally(block.getLocation(), dropItem);
        }
    }

    /**
     * Sprawdza czy blok może mieć zwiększony drop z Fortune
     */
    private boolean canMultiplyWithFortune(Material material) {
        if (material != null && (material.name().endsWith("_WOOL") || material.name().equals("WOOL"))) {
            return true;
        }
        switch (material) {
            // Dirt-like blocks
            case DIRT:
            case COARSE_DIRT:
            case GRASS_BLOCK:
            case PODZOL:
            case MYCELIUM:
            case SAND:
            case RED_SAND:
            case GRAVEL:
            case CLAY:
            case SOUL_SAND:
            case SOUL_SOIL:
            // Snow
            case SNOW:
            case SNOW_BLOCK:
            // Concrete powder
            case WHITE_CONCRETE_POWDER:
            case ORANGE_CONCRETE_POWDER:
            case MAGENTA_CONCRETE_POWDER:
            case LIGHT_BLUE_CONCRETE_POWDER:
            case YELLOW_CONCRETE_POWDER:
            case LIME_CONCRETE_POWDER:
            case PINK_CONCRETE_POWDER:
            case GRAY_CONCRETE_POWDER:
            case LIGHT_GRAY_CONCRETE_POWDER:
            case CYAN_CONCRETE_POWDER:
            case PURPLE_CONCRETE_POWDER:
            case BLUE_CONCRETE_POWDER:
            case BROWN_CONCRETE_POWDER:
            case GREEN_CONCRETE_POWDER:
            case RED_CONCRETE_POWDER:
            case BLACK_CONCRETE_POWDER:
                return true;
            default:
                return false;
        }
    }
}
