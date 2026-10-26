package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.database.ClanDataManager.Clan;
import dev.grzybalson.managers.ClanManager.ClanCreateResult;
import dev.grzybalson.utils.ChatInputSanitizer;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public ClanCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (args.length < 1) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "zaloz", "stworz", "create" -> handleCreate(player, args);
            case "usun", "delete" -> handleDelete(player);
            case "zapros", "invite" -> handleInvite(player, args);
            case "dolacz", "join", "accept" -> handleAccept(player, args);
            case "opusc", "leave" -> handleLeave(player);
            case "wyrzuc", "kick" -> handleKick(player, args);
            case "lider", "leader" -> handleLeader(player, args);
            case "zastepca", "deputy" -> handleDeputy(player, args);
            case "pomoc", "help" -> handleHelpRequest(player);
            case "info" -> handleInfo(player, args);
            case "pvp" -> handlePvp(player);
            case "ogloszenie", "announcement", "announce" -> handleAnnouncement(player, args);
            case "panel", "gui", "ulepsz", "ulepszenia", "upgrades" -> handleUpgrades(player);
            case "wplac", "deposit" -> handleDeposit(player, args);
            case "reload" -> handleReload(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (!plugin.getConfigManager().getClanConfig().isClansEnabled()) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getClanConfig().getClanDisableMessage()
            ));
            plugin.getSoundManager().playError(player);
            return;
        }
        if (args.length < 2) {
            sendUsage(player, "/klan stworz <tag>", "/klan zaloz <tag>");
            plugin.getSoundManager().playError(player);
            return;
        }

        String tag = args[1].trim();
        int minTagLength = plugin.getConfigManager().getClanConfig().getMinTagLength();
        int maxTagLength = plugin.getConfigManager().getClanConfig().getMaxTagLength();

        if (tag.isEmpty()) {
            player.sendMessage(ColorUtils.error("Musisz podać tag klanu!"));
            plugin.getSoundManager().playError(player);
            return;
        }

        if (tag.length() < minTagLength) {
            player.sendMessage(ColorUtils.error("Tag musi mieć minimum " + minTagLength + " znaki!"));
            plugin.getSoundManager().playError(player);
            return;
        }

        if (tag.length() > maxTagLength) {
            player.sendMessage(ColorUtils.error("Tag może mieć maksymalnie " + maxTagLength + " znaków!"));
            plugin.getSoundManager().playError(player);
            return;
        }

        if (ChatInputSanitizer.rejectIfBlocked(player, tag)) {
            plugin.getSoundManager().playError(player);
            return;
        }

        double cost = plugin.getConfigManager().getClanConfig().getCreationCost();

        plugin.getClanManager().createClan(player, tag).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (result) {
                    case SUCCESS -> {
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getClanConfig().getClanCreatedMessage()
                                .replace("{clan}", tag.toUpperCase())
                                .replace("{CLAN}", tag.toUpperCase())
                        ));
                        plugin.getSoundManager().playClanJoin(player);
                        broadcastClan(plugin.getConfigManager().getClanConfig().getClanCreateBroadcast(), player.getName(), tag.toUpperCase());
                    }
                    case ALREADY_IN_CLAN -> {
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getClanConfig().getClanAlreadyInClanMessage()
                        ));
                        plugin.getSoundManager().playError(player);
                    }
                    case TAG_EXISTS -> {
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getClanConfig().getClanTagExistsMessage()
                        ));
                        plugin.getSoundManager().playError(player);
                    }
                    case NOT_ENOUGH_MONEY, PAYMENT_FAILED -> {
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getClanConfig().getClanNotEnoughMoneyMessage()
                                .replace("{cost}", String.format("%.2f", cost))
                        ));
                        plugin.getSoundManager().playError(player);
                    }
                    case CREATE_FAILED -> {
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getClanConfig().getCreateClanError()
                        ));
                        plugin.getSoundManager().playError(player);
                    }
                }
            });
        });
    }

    private void handleDelete(Player player) {
        String tag = plugin.getClanManager().getCachedPlayerClan(player.getUniqueId());
        plugin.getClanManager().deleteClan(player).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getClanConfig().getClanDeletedMessage()
                    ));
                    plugin.getSoundManager().playClanLeave(player);
                    if (tag != null) {
                        broadcastClan(plugin.getConfigManager().getClanConfig().getClanDeleteBroadcast(), player.getName(), tag);
                    }
                } else {
                    sendClanPermissionFailure(player, true);
                }
            });
        });
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "/klan zapros <użytkownik>");
            plugin.getSoundManager().playError(player);
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getClanConfig().getClanPlayerNotFoundMessage()
            ));
            plugin.getSoundManager().playError(player);
            return;
        }

        plugin.getClanManager().invitePlayer(player, target).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getClanConfig().getClanInviteSentMessage()
                            .replace("{player}", target.getName())
                    ));

                    plugin.getClanManager().getPlayerClan(player.getUniqueId()).thenAccept(clanTag -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            String messageText = plugin.getConfigManager().getClanConfig().getClanInviteReceivedMessage()
                                .replace("{clan}", clanTag != null ? clanTag : "");

                            Component clickableJoin = ColorUtils.colorizeComponent("&a&l[DOŁĄCZ]")
                                .clickEvent(ClickEvent.runCommand("/klan dolacz"))
                                .hoverEvent(HoverEvent.showText(ColorUtils.colorizeComponent("&7Kliknij aby dołączyć do klanu!")));

                            Component message = ColorUtils.colorizeComponent(
                                messageText.replace("&a[DOŁĄCZ]", "")
                            ).append(clickableJoin);

                            target.sendMessage(message);
                            plugin.getSoundManager().playClanInvite(target);
                        });
                    });

                    plugin.getSoundManager().playSuccess(player);
                } else {
                    sendInviteFailure(player, target);
                }
            });
        });
    }

    private void handleAccept(Player player, String[] args) {
        if (!plugin.getClanManager().hasPendingInvite(player.getUniqueId())) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getClanConfig().getClanNoInviteMessage()
            ));
            plugin.getSoundManager().playError(player);
            return;
        }

        String clanTag = plugin.getClanManager().getPendingInviteClan(player.getUniqueId());
        if (args.length >= 2 && clanTag != null && !clanTag.equalsIgnoreCase(args[1])) {
            player.sendMessage(ColorUtils.error("Masz aktywne zaproszenie tylko do klanu " + clanTag + "!"));
            plugin.getSoundManager().playError(player);
            return;
        }

        plugin.getClanManager().acceptInvite(player).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getClanConfig().getClanJoinedMessage()
                            .replace("{clan}", clanTag)
                            .replace("{CLAN}", clanTag)
                    ));
                    plugin.getSoundManager().playClanJoin(player);
                    broadcastClan(plugin.getConfigManager().getClanConfig().getUserJoinClanBroadcast(), player.getName(), clanTag);
                } else {
                    player.sendMessage(ColorUtils.error("Nie udało się dołączyć do klanu!"));
                    plugin.getSoundManager().playError(player);
                }
            });
        });
    }

    private void handleLeave(Player player) {
        plugin.getClanManager().leaveClan(player).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getClanConfig().getClanLeftMessage()
                    ));
                    plugin.getSoundManager().playClanLeave(player);
                } else {
                    sendLeaveFailure(player);
                }
            });
        });
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "/klan wyrzuc <użytkownik>");
            plugin.getSoundManager().playError(player);
            return;
        }

        String targetName = args[1];
        plugin.getClanManager().kickMemberByName(player, targetName).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    Player target = Bukkit.getPlayer(targetName);
                    String displayName = target != null ? target.getName() : targetName;
                    player.sendMessage(ColorUtils.success("Wyrzucono gracza " + displayName + " z klanu!"));
                    plugin.getSoundManager().playSuccess(player);

                    if (target != null && target.isOnline()) {
                        target.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getClanConfig().getClanKickedMessage()
                        ));
                        plugin.getSoundManager().playClanLeave(target);
                    }
                } else {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getClanConfig().getClanPlayerNotFoundMessage()
                    ));
                    plugin.getSoundManager().playError(player);
                }
            });
        });
    }

    private void handleLeader(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "/klan lider <użytkownik>");
            plugin.getSoundManager().playError(player);
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getClanConfig().getClanPlayerNotFoundMessage()
            ));
            plugin.getSoundManager().playError(player);
            return;
        }

        plugin.getClanManager().transferLeader(player, target.getUniqueId()).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (Boolean.TRUE.equals(success)) {
                    player.sendMessage(ColorUtils.success("Przekazano lidera graczowi " + target.getName() + "!"));
                    target.sendMessage(ColorUtils.success("Zostałeś nowym liderem klanu!"));
                    plugin.getSoundManager().playSuccess(player);
                    plugin.getSoundManager().playSuccess(target);
                } else {
                    sendMemberActionFailure(player, target.getUniqueId(), true);
                }
            });
        });
    }

    private void handleDeputy(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "/klan zastepca <użytkownik>");
            plugin.getSoundManager().playError(player);
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getClanConfig().getClanPlayerNotFoundMessage()
            ));
            plugin.getSoundManager().playError(player);
            return;
        }

        plugin.getClanManager().setDeputy(player, target.getUniqueId()).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getClanConfig().getClanDeputySetMessage()
                            .replace("{player}", target.getName())
                    ));
                    plugin.getSoundManager().playSuccess(player);
                } else {
                    sendMemberActionFailure(player, target.getUniqueId(), true);
                }
            });
        });
    }

    private void handleHelpRequest(Player player) {
        plugin.getClanManager().getPlayerClan(player.getUniqueId()).thenAccept(clanTag -> {
            if (clanTag == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getClanConfig().getClanNotInClanMessage()
                    ));
                    plugin.getSoundManager().playError(player);
                });
                return;
            }

            plugin.getClanManager().getClan(clanTag).thenAccept(clan -> {
                if (clan == null) return;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    int x = player.getLocation().getBlockX();
                    int y = player.getLocation().getBlockY();
                    int z = player.getLocation().getBlockZ();

                    String message = plugin.getConfigManager().getClanConfig().getClanHelpRequestMessage()
                        .replace("{player}", player.getName())
                        .replace("{x}", String.valueOf(x))
                        .replace("{y}", String.valueOf(y))
                        .replace("{z}", String.valueOf(z));

                    for (UUID memberUuid : clan.getMembers()) {
                        Player member = Bukkit.getPlayer(memberUuid);
                        if (member != null && member.isOnline() && !member.equals(player)) {
                            member.sendMessage(ColorUtils.colorizeComponent(message));
                            plugin.getSoundManager().playClanAnnouncement(member);

                            dev.grzybalson.utils.ColorUtils.sendTitle(member, 
                                ColorUtils.colorize("&c&lPOMOC!"),
                                ColorUtils.colorize("&7" + player.getName() + " &eprosi o pomoc!"),
                                10, 60, 20
                            );
                        }
                    }

                    player.sendMessage(ColorUtils.success("Wysłano prośbę o pomoc do klanowiczów!"));
                    plugin.getSoundManager().playSuccess(player);
                });
            });
        });
    }

    private void handleInfo(Player player, String[] args) {
        String tag;
        if (args.length >= 2) {
            tag = args[1];
        } else {
            plugin.getClanManager().getPlayerClan(player.getUniqueId()).thenAccept(playerClan -> {
                if (playerClan == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getClanConfig().getClanNotInClanMessage()
                        ));
                        sendUsage(player, "/klan info <tag>", "/klan ulepsz");
                        plugin.getSoundManager().playError(player);
                    });
                    return;
                }
                showClanInfo(player, playerClan);
            });
            return;
        }

        showClanInfo(player, tag);
    }

    private void handlePvp(Player player) {
        plugin.getClanManager().togglePvp(player).thenAccept(newState -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (newState == null) {
                    sendClanPermissionFailure(player, false);
                    return;
                }

                if (newState) {
                    player.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getClanConfig().getClanPvpEnabledMessage()));
                } else {
                    player.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getClanConfig().getClanPvpDisabledMessage()));
                }
                plugin.getSoundManager().playSuccess(player);
            });
        });
    }

    private void handleAnnouncement(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "/klan ogloszenie <wiadomość>");
            plugin.getSoundManager().playError(player);
            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (ChatInputSanitizer.rejectIfBlocked(player, message)) {
            plugin.getSoundManager().playError(player);
            return;
        }
        plugin.getClanManager().sendAnnouncement(player, message).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getClanConfig().getClanAnnouncementSentMessage()));
                    plugin.getSoundManager().playSuccess(player);
                } else {
                    sendClanPermissionFailure(player, false);
                }
            });
        });
    }

    private void handleUpgrades(Player player) {
        var config = plugin.getConfigManager().getClanConfig();
        if (!config.isClansEnabled() || !config.isPanelAccess()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getClanDisableMessage()));
            plugin.getSoundManager().playError(player);
            return;
        }
        new dev.grzybalson.gui.ClanUpgradesGUI(plugin, player).open();
    }

    private void handleDeposit(Player player, String[] args) {
        if (args.length < 2) {
            sendUsage(player, "/klan wplac <kwota>");
            plugin.getSoundManager().playError(player);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getClanConfig().getClanDepositInvalidAmountMessage()
            ));
            plugin.getSoundManager().playError(player);
            return;
        }

        if (amount <= 0) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getClanConfig().getClanDepositInvalidAmountMessage()
            ));
            plugin.getSoundManager().playError(player);
            return;
        }

        plugin.getClanManager().getPlayerClan(player.getUniqueId()).thenAccept(clanTag -> {
            if (clanTag == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getClanConfig().getClanNotInClanMessage()
                    ));
                    plugin.getSoundManager().playError(player);
                });
                return;
            }

            plugin.getClanManager().depositCoins(player, amount).thenAccept(success -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!success) {
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getClanConfig().getClanDepositNotEnoughMoneyMessage()
                                .replace("{amount}", String.valueOf(amount))
                        ));
                        plugin.getSoundManager().playError(player);
                        return;
                    }

                    String currency = plugin.getConfigManager().getClanConfig().getUpgrades() != null
                        ? plugin.getConfigManager().getClanConfig().getUpgrades().getCurrencyName()
                        : "";
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getClanConfig().getClanDepositSuccessMessage()
                            .replace("{amount}", String.valueOf(amount))
                            .replace("{currency}", currency)
                    ));
                    plugin.getSoundManager().playSuccess(player);
                });
            });
        });
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("Holly.cmd.klan.reload")) {
            player.sendMessage(ColorUtils.noPermission("/klan reload", "Holly.cmd.klan.reload"));
            plugin.getSoundManager().playError(player);
            return;
        }

        plugin.getConfigManager().reloadClanConfig();
        plugin.getModuleConfigManager().getEloSettingsConfig().load(true);
        player.sendMessage(ColorUtils.success("Przeładowano konfigurację klanów!"));
        plugin.getSoundManager().playSuccess(player);
    }

    private void sendClanPermissionFailure(Player player, boolean leaderOnly) {
        plugin.getClanManager().getPlayerClan(player.getUniqueId()).thenAccept(clanTag -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (clanTag == null) {
                    sendClanError(player, plugin.getConfigManager().getClanConfig().getClanNotInClanMessage());
                    return;
                }

                sendClanError(player, leaderOnly
                    ? plugin.getConfigManager().getClanConfig().getClanNotLeaderMessage()
                    : plugin.getConfigManager().getClanConfig().getClanNotLeaderOrDeputyMessage());
            });
        });
    }

    private void sendLeaveFailure(Player player) {
        plugin.getClanManager().getPlayerClan(player.getUniqueId()).thenAccept(clanTag -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (clanTag == null) {
                    sendClanError(player, plugin.getConfigManager().getClanConfig().getClanNotInClanMessage());
                    return;
                }

                sendClanError(player, plugin.getConfigManager().getClanConfig().getClanLeaderCannotLeaveMessage());
            });
        });
    }

    private void sendInviteFailure(Player player, Player target) {
        plugin.getClanManager().getPlayerClan(player.getUniqueId()).thenAccept(playerClan -> {
            if (playerClan == null) {
                Bukkit.getScheduler().runTask(plugin, () -> sendClanError(player, plugin.getConfigManager().getClanConfig().getClanNotInClanMessage()));
                return;
            }

            plugin.getClanManager().getClan(playerClan).thenAccept(clan -> {
                plugin.getClanManager().getPlayerClan(target.getUniqueId()).thenAccept(targetClan -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (clan == null) {
                            sendClanError(player, plugin.getConfigManager().getClanConfig().getClanNotInClanMessage());
                            return;
                        }

                        if (!clan.isLeaderOrDeputy(player.getUniqueId())) {
                            sendClanError(player, plugin.getConfigManager().getClanConfig().getClanNotLeaderOrDeputyMessage());
                            return;
                        }

                        if (targetClan != null) {
                            sendClanError(player, plugin.getConfigManager().getClanConfig().getClanTargetAlreadyInClanMessage());
                            return;
                        }

                        sendClanError(player, plugin.getConfigManager().getClanConfig().getClanNotLeaderOrDeputyMessage());
                    });
                });
            });
        });
    }

    private void sendMemberActionFailure(Player player, UUID targetUuid, boolean leaderOnly) {
        plugin.getClanManager().getPlayerClan(player.getUniqueId()).thenAccept(playerClan -> {
            if (playerClan == null) {
                Bukkit.getScheduler().runTask(plugin, () -> sendClanError(player, plugin.getConfigManager().getClanConfig().getClanNotInClanMessage()));
                return;
            }

            plugin.getClanManager().getClan(playerClan).thenAccept(clan -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (clan == null) {
                        sendClanError(player, plugin.getConfigManager().getClanConfig().getClanNotInClanMessage());
                        return;
                    }

                    boolean hasRole = leaderOnly
                        ? clan.isLeader(player.getUniqueId())
                        : clan.isLeaderOrDeputy(player.getUniqueId());

                    if (!hasRole) {
                        sendClanError(player, leaderOnly
                            ? plugin.getConfigManager().getClanConfig().getClanNotLeaderMessage()
                            : plugin.getConfigManager().getClanConfig().getClanNotLeaderOrDeputyMessage());
                        return;
                    }

                    sendClanError(player, plugin.getConfigManager().getClanConfig().getClanTargetNotInYourClanMessage());
                });
            });
        });
    }

    private void sendClanError(Player player, String message) {
        player.sendMessage(ColorUtils.colorizeComponent(message));
        plugin.getSoundManager().playError(player);
    }

    private void showClanInfo(Player player, String tag) {
        plugin.getClanManager().getClan(tag).thenAccept(clan -> {
            if (clan == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.error("Klan nie został znaleziony!"));
                    plugin.getSoundManager().playError(player);
                });
                return;
            }

            plugin.getClanManager().getTopClans(1000).thenAccept(topClans -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> placeholders = buildClanInfoPlaceholders(clan, topClans);

                    for (String line : plugin.getConfigManager().getClanConfig().getClanInfoLayout()) {
                        if (line == null || line.isEmpty()) {
                            player.sendMessage(Component.empty());
                            continue;
                        }

                        String parsedLine = line;
                        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                            parsedLine = parsedLine.replace(entry.getKey(), entry.getValue());
                        }

                        player.sendMessage(ColorUtils.colorizeComponent(parsedLine));
                    }

                    plugin.getSoundManager().playGuiOpen(player);
                });
            });
        });
    }

    private Map<String, String> buildClanInfoPlaceholders(Clan clan, List<Clan> topClans) {
        String leaderName = resolvePlayerName(clan.getLeader());
        String deputyName = clan.getDeputy() != null
            ? resolvePlayerName(clan.getDeputy())
            : plugin.getConfigManager().getClanConfig().getClanNoDeputyText();

        int onlineMembers = (int) clan.getMembers().stream()
            .map(Bukkit::getPlayer)
            .filter(online -> online != null && online.isOnline())
            .count();

        String membersList = clan.getMembers().stream()
            .map(this::resolvePlayerName)
            .collect(Collectors.joining(", "));

        String rank = resolveClanRank(clan, topClans);
        String pvpStatus = clan.isPvpEnabled()
            ? plugin.getConfigManager().getClanConfig().getClanPvpEnabledText()
            : plugin.getConfigManager().getClanConfig().getClanPvpDisabledText();

        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("{CLAN_TAG}", clan.getTag());
        placeholders.put("{CLAN_NAME}", clan.getTag());
        placeholders.put("{CLAN}", clan.getTag());
        placeholders.put("{LEADER_NAME}", leaderName);
        placeholders.put("{LEADER}", leaderName);
        placeholders.put("{DEPUTY_NAMES}", deputyName);
        placeholders.put("{DEPUTIES}", deputyName);
        placeholders.put("{ONLINE}", String.valueOf(onlineMembers));
        placeholders.put("{TOTAL}", String.valueOf(clan.getMembers().size()));
        placeholders.put("{MEMBERS_LIST}", membersList);
        placeholders.put("{MEMBERS}", membersList);
        placeholders.put("{ELO}", String.valueOf(clan.getElo()));
        placeholders.put("{POINTS}", String.valueOf(clan.getElo()));
        placeholders.put("{RANK}", rank);
        placeholders.put("{KILLS}", String.valueOf(clan.getKills()));
        placeholders.put("{DEATHS}", String.valueOf(clan.getDeaths()));
        placeholders.put("{PVP_STATUS}", pvpStatus);
        placeholders.put("{COINS}", String.valueOf(clan.getCoins()));
        String currency = plugin.getConfigManager().getClanConfig().getUpgrades() != null
            ? plugin.getConfigManager().getClanConfig().getUpgrades().getCurrencyName()
            : "";
        placeholders.put("{CURRENCY}", currency);
        return placeholders;
    }

    private String resolveClanRank(Clan clan, List<Clan> topClans) {
        if (clan == null || clan.getTag() == null || clan.getTag().isBlank()) {
            return plugin.getConfigManager().getClanConfig().getClanNotRankedText();
        }

        int rank = 1;
        for (Clan rankedClan : topClans) {
            if (rankedClan == null || rankedClan.getTag() == null || rankedClan.getTag().isBlank()) {
                continue;
            }

            if (rankedClan.getTag().equalsIgnoreCase(clan.getTag())) {
                return String.valueOf(rank);
            }

            rank++;
        }

        return plugin.getConfigManager().getClanConfig().getClanNotRankedText();
    }

    private String resolvePlayerName(UUID uuid) {
        if (uuid == null) {
            return "-";
        }

        // Incognito: zawsze prawdziwy nick (nigdy UKRYTY*).
        if (plugin.getIncognitoManager() != null) {
            String real = plugin.getIncognitoManager().realName(uuid);
            if (real != null && !real.isBlank() && !real.toUpperCase(Locale.ROOT).startsWith("UKRYTY")) {
                return real;
            }
        }

        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            String name = onlinePlayer.getName();
            if (name != null && !name.toUpperCase(Locale.ROOT).startsWith("UKRYTY")) {
                return name;
            }
        }

        if (plugin.getJoinInfoManager() != null) {
            String stored = plugin.getJoinInfoManager().getPlayerName(uuid);
            if (stored != null && !stored.isBlank() && !stored.toUpperCase(Locale.ROOT).startsWith("UKRYTY")) {
                return stored;
            }
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null && !offlinePlayer.getName().isBlank()
            && !offlinePlayer.getName().toUpperCase(Locale.ROOT).startsWith("UKRYTY")) {
            return offlinePlayer.getName();
        }

        return uuid.toString().substring(0, 8);
    }

    private void sendHelp(Player player) {
        List<String> help = plugin.getConfigManager().getClanConfig().getClanHelpMessage();
        if (help != null && !help.isEmpty()) {
            for (String line : help) {
                if (line == null || line.isEmpty()) {
                    player.sendMessage(Component.empty());
                    continue;
                }
                player.sendMessage(ColorUtils.colorizeComponent(line));
            }
            return;
        }
        sendUsage(player, "/klan stworz <tag>", "/klan ulepsz", "/klan info <tag>");
    }

    private void broadcastClan(String template, String playerName, String clanTag) {
        if (template == null || template.isBlank()) {
            return;
        }
        String message = template
            .replace("{PLAYER}", playerName)
            .replace("{player}", playerName)
            .replace("{CLAN}", clanTag)
            .replace("{clan}", clanTag);
        Bukkit.broadcast(ColorUtils.colorizeComponent(message));
    }

    private void sendUsage(Player player, String... commands) {
        ColorUtils.sendUsage(player, commands);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList(
                "zaloz", "stworz", "usun", "zapros", "dolacz", "opusc", "wyrzuc",
                "lider", "zastepca", "pomoc", "info", "pvp", "ogloszenie", "panel",
                "ulepsz", "ulepszenia", "upgrades", "wplac"
            ));
            if (sender.hasPermission("Holly.cmd.klan.reload")) {
                completions.add("reload");
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("wplac") || sub.equals("deposit")) {
                completions.addAll(Arrays.asList("10", "50", "100", "500"));
            } else if (sub.equals("zapros") || sub.equals("invite")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            } else if (sub.equals("wyrzuc") || sub.equals("kick")) {
                if (sender instanceof Player player) {
                    String clanTag = plugin.getClanManager().getCachedPlayerClan(player.getUniqueId());
                    if (clanTag != null) {
                        var clan = plugin.getClanManager().getCachedClan(clanTag);
                        if (clan != null) {
                            for (UUID memberUuid : clan.getMembers()) {
                                if (memberUuid.equals(player.getUniqueId()) || clan.isLeader(memberUuid)) {
                                    continue;
                                }
                                OfflinePlayer offline = Bukkit.getOfflinePlayer(memberUuid);
                                if (offline.getName() != null) {
                                    completions.add(offline.getName());
                                }
                            }
                        }
                    }
                }
            } else if (sub.equals("zastepca") || sub.equals("deputy")
                || sub.equals("lider") || sub.equals("leader")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(lastArg));
        return completions;
    }
}
