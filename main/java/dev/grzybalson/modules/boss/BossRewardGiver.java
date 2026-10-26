package dev.grzybalson.modules.boss;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BossRewardGiver {

    private BossRewardGiver() {
    }

    public static void grantTop(List<Player> ranking, Location dropAt, BossRewards rewards) {
        grantTop(ranking, dropAt, rewards, "");
    }

    public static void grantTop(List<Player> ranking, Location dropAt, BossRewards rewards, String bossName) {
        if (rewards == null) {
            return;
        }

        Player first = ranking == null || ranking.isEmpty() ? null : ranking.get(0);
        if (first != null) {
            runCommands(rewards.commands(), first);
            giveItems(first, dropAt, rewards.items(), rewards.dropToInventory());
        } else if (!rewards.dropToInventory()) {
            dropItems(dropAt, rewards.items());
        }

        if (ranking != null) {
            for (int index = 0; index < ranking.size(); index++) {
                int place = index + 1;
                BossRewards.PlaceReward placeReward = rewards.top() == null ? null : rewards.top().get(place);
                if (placeReward == null) {
                    continue;
                }
                Player player = ranking.get(index);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                runCommands(placeReward.commands(), player);
                giveItems(player, dropAt, placeReward.items(), rewards.dropToInventory());
            }
        }

        String broadcast = rewards.broadcast();
        if (broadcast != null && !broadcast.isBlank()) {
            String name = first == null ? "-" : first.getName();
            String boss = bossName == null ? "" : bossName;
            Bukkit.broadcast(ColorUtils.colorizeComponent(
                broadcast.replace("{PLAYER}", name).replace("{player}", name).replace("{NAME}", boss)
            ));
        }
    }

    private static void runCommands(List<String> commands, Player player) {
        if (commands == null || player == null) {
            return;
        }
        for (String command : commands) {
            if (command == null || command.isBlank()) {
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{PLAYER}", player.getName())
                .replace("{player}", player.getName()));
        }
    }

    private static void giveItems(Player player, Location dropAt,
                                  List<BossRewards.ItemDrop> drops, boolean toInventory) {
        if (drops == null || drops.isEmpty()) {
            return;
        }
        for (BossRewards.ItemDrop drop : drops) {
            if (drop == null || drop.item() == null) {
                continue;
            }
            if (drop.chance() < 100.0D && ThreadLocalRandom.current().nextDouble() * 100.0D > drop.chance()) {
                continue;
            }
            ItemStack stack = drop.item().clone();
            if (toInventory && player != null && player.isOnline()) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            } else {
                Location at = dropAt != null ? dropAt : (player != null ? player.getLocation() : null);
                if (at != null && at.getWorld() != null) {
                    at.getWorld().dropItemNaturally(at, stack);
                }
            }
        }
    }

    private static void dropItems(Location dropAt, List<BossRewards.ItemDrop> drops) {
        if (dropAt == null || dropAt.getWorld() == null || drops == null) {
            return;
        }
        for (BossRewards.ItemDrop drop : drops) {
            if (drop == null || drop.item() == null) {
                continue;
            }
            if (drop.chance() < 100.0D && ThreadLocalRandom.current().nextDouble() * 100.0D > drop.chance()) {
                continue;
            }
            dropAt.getWorld().dropItemNaturally(dropAt, drop.item().clone());
        }
    }
}
