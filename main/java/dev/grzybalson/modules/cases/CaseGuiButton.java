package dev.grzybalson.modules.cases;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record CaseGuiButton(String name, CaseOpenType type, List<Integer> slots, ItemStack item) {
}
