package dev.grzybalson.modules.cases;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record CaseGuiDecoration(List<Integer> slots, ItemStack item) {
}
