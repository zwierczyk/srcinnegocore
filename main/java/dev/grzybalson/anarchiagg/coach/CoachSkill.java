package dev.grzybalson.anarchiagg.coach;

import org.bukkit.Material;

public enum CoachSkill {
    STRENGTH("strength", "&#E41B53&lU&#E11A4F&lm&#DF194B&li&#DC1748&le&#DA1644&lj&#D71540&lę&#D5133D&lt&#D21239&ln&#CF1135&lo&#CD0F32&lś&#CA0E2E&lć &#C50B27&ls&#C30A23&li&#C0091F&lł&#BE071C&ly", Material.DIAMOND_SWORD, 20),
    DEXTERITY("dexterity", "&#F1E03C&lU&#F1DE3B&lm&#F1DC3A&li&#F0DB38&le&#F0D937&lj&#F0D736&lę&#EFD535&lt&#EFD433&ln&#EFD232&lo&#EED031&lś&#EECF2F&lć &#EDCB2D&lz&#EDCA2B&lr&#ECC82A&lę&#ECC629&lc&#ECC428&lz&#EBC326&ln&#EBC125&lo&#EBBF24&lś&#EABE22&lc&#EABC21&li", Material.HONEYCOMB, 21),
    DEFENSE("defense", "&#44CBF0&lU&#42C7EF&lm&#3FC4EE&li&#3DC1ED&le&#3ABEEC&lj&#38BAEB&lę&#35B7EA&lt&#33B4E9&ln&#30B1E8&lo&#2EADE7&lś&#2BAAE6&lć &#26A4E4&lo&#24A0E3&lb&#219DE2&lr&#1F9AE1&lo&#1C97E0&ln&#1A93DF&ly", Material.SHIELD, 22),
    LUCK("luck", "&#5FE03D&lU&#5BDE3B&lm&#57DC39&li&#53DA36&le&#4FD934&lj&#4BD731&lę&#47D52F&lt&#43D32D&ln&#3FD12A&lo&#3CCF28&lś&#38CD26&lć &#30C921&ls&#2CC71F&lz&#28C51C&lc&#24C41A&lz&#20C217&lę&#1CC015&lś&#18BE13&lc&#14BC10&li&#10BA0E&la", Material.SCUTE, 23),
    INTELLIGENCE("intelligence", "&#D878F6&lU&#D776F6&lm&#D674F5&li&#D672F5&le&#D570F5&lj&#D46EF5&lę&#D46CF5&lt&#D36AF5&ln&#D268F4&lo&#D266F4&lś&#D164F4&lć &#CF61F4&li&#CF5FF4&ln&#CE5DF3&lt&#CD5BF3&le&#CD59F3&ll&#CC57F3&li&#CB55F3&lg&#CB53F3&le&#CA51F2&ln&#C94FF2&lc&#C94DF2&lj&#C84BF2&li", Material.WRITABLE_BOOK, 24);

    private final String id;
    private final String displayName;
    private final Material material;
    private final int mainSlot;

    CoachSkill(String id, String displayName, Material material, int mainSlot) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.mainSlot = mainSlot;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public int getMainSlot() {
        return mainSlot;
    }
}
