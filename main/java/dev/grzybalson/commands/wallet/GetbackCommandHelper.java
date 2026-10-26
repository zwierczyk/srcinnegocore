package dev.grzybalson.commands.wallet;

import dev.grzybalson.managers.WalletGetbackService;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.CommandSender;

public final class GetbackCommandHelper {

    private GetbackCommandHelper() {
    }

    public static void sendResult(CommandSender sender, String targetLabel, WalletGetbackService.GetbackResult result) {
        if (!result.success()) {
            sender.sendMessage(ColorUtils.error(result.error()));
            return;
        }

        if (result.purchases() == 0) {
            if (!result.details().isEmpty()) {
                sender.sendMessage(ColorUtils.colorizeComponent(result.details().get(0)));
            } else {
                sender.sendMessage(ColorUtils.colorizeComponent(
                    "&c✕ &8Ξ &cBrak zakupow portfela dla &f" + targetLabel + "&c."));
            }
            return;
        }

        sender.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
        sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Przywrocono produkty z portfela"));
        sender.sendMessage(ColorUtils.colorizeComponent("&7Gracz: &f" + targetLabel));
        if (result.productLabel() != null && !result.productLabel().isBlank()) {
            sender.sendMessage(ColorUtils.colorizeComponent("&7Produkt: &f" + result.productLabel()));
        }
        sender.sendMessage(ColorUtils.colorizeComponent("&7Zakupy w logach: &f" + result.purchases()));
        sender.sendMessage(ColorUtils.colorizeComponent("&7Nagród w &f/odbierz&7: &a" + result.queuedRewards()));
        if (result.sigilsRestored() > 0) {
            sender.sendMessage(ColorUtils.colorizeComponent("&7Przywróconych sigili (Mongo): &a" + result.sigilsRestored()));
        }
        if (result.unmatched() > 0) {
            sender.sendMessage(ColorUtils.colorizeComponent("&7Niedopasowane: &e" + result.unmatched()));
        }
        if (result.missingPackages() > 0) {
            sender.sendMessage(ColorUtils.colorizeComponent("&7Brakujace paczki /odbierz: &c" + result.missingPackages()));
        }
        for (String detail : result.details()) {
            sender.sendMessage(ColorUtils.colorizeComponent(detail));
        }
        sender.sendMessage(ColorUtils.colorizeComponent("&7Gracze odbieraja nagrody przez &f/odbierz&7."));
        sender.sendMessage(ColorUtils.colorizeComponent("&8&m--------------------------------------------------"));
    }
}
