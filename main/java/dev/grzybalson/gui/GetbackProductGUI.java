package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.commands.wallet.GetbackCommandHelper;
import dev.grzybalson.config.configs.WalletConfig;
import dev.grzybalson.managers.WalletGetbackService;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import dev.grzybalson.utils.WalletItemFactory;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class GetbackProductGUI implements InventoryHolder {

    public static final String NBT_ACTION = "getback_action";
    public static final String ACTION_ALL = "all";

    private final HollyCore plugin;
    private final CommandSender sender;
    private final String targetLabel;
    private final UUID targetUuid;
    private final boolean forAll;
    private final WalletGetbackService getbackService;
    private final Map<Integer, Runnable> clickActions = new HashMap<>();
    private Inventory inventory;

    public GetbackProductGUI(HollyCore plugin, CommandSender sender, String targetLabel, UUID targetUuid, boolean forAll) {
        this.plugin = plugin;
        this.sender = sender;
        this.targetLabel = targetLabel;
        this.targetUuid = targetUuid;
        this.forAll = forAll;
        this.getbackService = new WalletGetbackService(plugin);
    }

    public void open() {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Z konsoli uzyj: /getback <nick|all> <produkt|wszystko>"));
            return;
        }

        clickActions.clear();
        int rows = 6;
        inventory = Bukkit.createInventory(this, rows * 9,
            ColorUtils.colorizeComponent("&8Getback &7- &f" + targetLabel));

        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        int slot = 0;
        WalletConfig config = plugin.getConfigManager().getWalletConfig();
        for (Map.Entry<Integer, WalletConfig.ServiceCategory> serviceEntry : config.getServices().entrySet()) {
            int serviceSlot = serviceEntry.getKey();
            WalletConfig.ServiceCategory service = serviceEntry.getValue();
            String serviceTitle = ColorUtils.stripColors(service.getGuiTitle());
            int productIndex = 0;

            for (WalletConfig.WalletProduct product : service.getCategories()) {
                if (slot >= inventory.getSize() - 9) {
                    break;
                }

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("{PRICE}", format(product.getPrice()));
                placeholders.put("{price}", format(product.getPrice()));
                placeholders.put("{CURRENCY}", config.getCurrencyName());

                ItemStack item = WalletItemFactory.create(product.getItem(), placeholders);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    List<net.kyori.adventure.text.Component> lore = meta.lore();
                    if (lore == null) {
                        lore = new ArrayList<>();
                    } else {
                        lore = new ArrayList<>(lore);
                    }
                    lore.add(ColorUtils.colorizeComponent("").decoration(TextDecoration.ITALIC, false));
                    lore.add(ColorUtils.colorizeComponent("&7Kategoria: &f" + serviceTitle).decoration(TextDecoration.ITALIC, false));
                    lore.add(ColorUtils.colorizeComponent("&7Cena: &a" + format(product.getPrice()) + " " + config.getCurrencyName())
                        .decoration(TextDecoration.ITALIC, false));
                    lore.add(ColorUtils.colorizeComponent("&eKliknij, aby przywrocic ten produkt.")
                        .decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    item.setItemMeta(meta);
                }

                WalletGetbackService.WalletProductRef ref =
                    new WalletGetbackService.WalletProductRef(serviceSlot, productIndex);
                NBTUtils.setString(item, NBT_ACTION, ref.encode());

                int guiSlot = slot++;
                WalletGetbackService.ProductSelection selection = getbackService.selectionForRef(ref);
                inventory.setItem(guiSlot, item);
                clickActions.put(guiSlot, () -> runRestore(player, selection));
                productIndex++;
            }
        }

        int allSlot = inventory.getSize() - 5;
        ItemStack allItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta allMeta = allItem.getItemMeta();
        if (allMeta != null) {
            allMeta.displayName(ColorUtils.colorizeComponent("&#00FF3B&lWSZYSTKIE PRODUKTY")
                .decoration(TextDecoration.ITALIC, false));
            allMeta.lore(List.of(
                ColorUtils.colorizeComponent("&7Przywroc wszystkie zakupy z logow portfela.")
                    .decoration(TextDecoration.ITALIC, false),
                ColorUtils.colorizeComponent("&eKliknij, aby przywrocic wszystko.")
                    .decoration(TextDecoration.ITALIC, false)
            ));
            allItem.setItemMeta(allMeta);
        }
        NBTUtils.setString(allItem, NBT_ACTION, ACTION_ALL);
        inventory.setItem(allSlot, allItem);
        clickActions.put(allSlot, () -> runRestore(player, WalletGetbackService.ProductSelection.all()));

        player.openInventory(inventory);
    }

    private void runRestore(Player player, WalletGetbackService.ProductSelection selection) {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00E0FFℹ &8Ξ &7Przywracanie &f" + selection.label() + " &7dla &f" + targetLabel + "&7..."));

        var future = forAll
            ? getbackService.restoreForAll(selection)
            : getbackService.restoreForPlayer(targetUuid, selection);

        future.thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () ->
            GetbackCommandHelper.sendResult(sender, targetLabel, result)
        )).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                sender.sendMessage(ColorUtils.error("Blad przywracania zakupow: " + ex.getMessage())));
            return null;
        });
    }

    public void handleClick(int rawSlot) {
        Runnable action = clickActions.get(rawSlot);
        if (action != null) {
            action.run();
        }
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
