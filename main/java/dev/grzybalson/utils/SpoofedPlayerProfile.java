package dev.grzybalson.utils;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Wirtualny profil bota spoofera — ten sam pipeline co Player w TAB / nametag. */
public record SpoofedPlayerProfile(
        UUID uuid,
        String name,
        String primaryGroup,
        int tabPriority,
        String luckPermsPrefix,
        String luckPermsSuffix,
        String nameColor,
        ItemStack helmet,
        ItemStack chestplate,
        ItemStack leggings,
        ItemStack boots,
        ItemStack weapon
) {
}
