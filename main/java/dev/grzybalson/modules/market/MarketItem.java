package dev.grzybalson.modules.market;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class MarketItem {

    private final String id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long listedAt;
    private final long showTime;
    private final MarketCategory category;

    public MarketItem(String id, UUID sellerUuid, String sellerName, ItemStack item, double price, long listedAt, MarketCategory category) {
        this(id, sellerUuid, sellerName, item, price, listedAt, category, listedAt);
    }

    public MarketItem(String id, UUID sellerUuid, String sellerName, ItemStack item, double price, long listedAt, MarketCategory category, long showTime) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
        this.listedAt = listedAt;
        this.showTime = showTime;
        this.category = category;
    }

    public boolean isVisible() {
        return System.currentTimeMillis() >= showTime;
    }

    public String getId() {
        return id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item;
    }

    public double getPrice() {
        return price;
    }

    public long getListedAt() {
        return listedAt;
    }

    public long getShowTime() {
        return showTime;
    }

    public MarketCategory getCategory() {
        return category;
    }
}
