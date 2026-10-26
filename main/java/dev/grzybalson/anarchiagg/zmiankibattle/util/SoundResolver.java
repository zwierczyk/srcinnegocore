package dev.grzybalson.anarchiagg.zmiankibattle.util;

import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SoundResolver {

    public enum Type {
        CLICK,
        ERROR,
        SUCCESS,
        ROLL,
        WIN,
        LOSE
    }

    private final Map<Type, Sound> sounds = new HashMap<>();

    public SoundResolver(FileConfiguration cfg) {
        // aliases dokładnie pod nazwy które podałeś
        var alias = new HashMap<String, Sound>();
        alias.put("ui.click", Sound.UI_BUTTON_CLICK);
        alias.put("villager.no", Sound.ENTITY_VILLAGER_NO);
        alias.put("expieriene_pickup", Sound.ENTITY_EXPERIENCE_ORB_PICKUP); // literówka w configu – celowo wspierana
        alias.put("experience_pickup", Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        alias.put("block.dispenser.dispense", Sound.BLOCK_DISPENSER_DISPENSE);
        alias.put("player_lvlup", Sound.ENTITY_PLAYER_LEVELUP);
        alias.put("villager.hurt", Sound.ENTITY_VILLAGER_HURT);

        sounds.put(Type.CLICK, resolve(cfg.getString("sounds.click"), alias, Sound.UI_BUTTON_CLICK));
        sounds.put(Type.ERROR, resolve(cfg.getString("sounds.error"), alias, Sound.ENTITY_VILLAGER_NO));
        sounds.put(Type.SUCCESS, resolve(cfg.getString("sounds.succes"), alias, Sound.ENTITY_EXPERIENCE_ORB_PICKUP));
        sounds.put(Type.ROLL, resolve(cfg.getString("sounds.roll"), alias, Sound.BLOCK_DISPENSER_DISPENSE));
        sounds.put(Type.WIN, resolve(cfg.getString("sounds.win_sound"), alias, Sound.ENTITY_PLAYER_LEVELUP));
        sounds.put(Type.LOSE, resolve(cfg.getString("sounds.lose_sound"), alias, Sound.ENTITY_VILLAGER_HURT));
    }

    private Sound resolve(String raw, Map<String, Sound> alias, Sound def) {
        if (raw == null || raw.isBlank()) return def;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (alias.containsKey(key)) return alias.get(key);

        // pozwól też na podanie enum name
        try {
            return Sound.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return def;
        }
    }

    public void play(Player p, Type type) {
        Sound s = sounds.get(type);
        if (s == null || p == null) return;
        p.playSound(p.getLocation(), s, 1.0f, 1.0f);
    }
}
