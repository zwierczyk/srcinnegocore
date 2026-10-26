package dev.grzybalson.modules.masakraszafka;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

record SzafkaPerks(double miningPercent, double damagePercent, double reductionPercent, double lifestealChance,
                   double lifestealHearts, boolean noHunger, Map<PotionEffectType, Integer> effects) {
    static final SzafkaPerks NONE = new SzafkaPerks(0, 0, 0, 0, 0, false, Map.of());

    static SzafkaPerks parse(ConfigurationSection section, Consumer<String> warn) {
        if (section == null) return NONE;
        Map<PotionEffectType, Integer> effects = new HashMap<>();
        for (String raw : section.getStringList("effects")) {
            String[] parts = raw.split(":", 2);
            PotionEffectType type = effectType(parts[0].trim());
            if (type == null) {
                warn.accept("nieznany efekt '" + raw + "' w " + section.getCurrentPath());
                continue;
            }
            int amplifier = 0;
            if (parts.length > 1) {
                try {
                    amplifier = Math.max(0, Math.min(9, Integer.parseInt(parts[1].trim())));
                } catch (NumberFormatException exception) {
                    warn.accept("nieprawidłowy poziom efektu '" + raw + "' w " + section.getCurrentPath());
                }
            }
            effects.merge(type, amplifier, Math::max);
        }
        return new SzafkaPerks(
            clamp(section.getDouble("mining-percent"), 1000),
            clamp(section.getDouble("damage-percent"), 1000),
            clamp(section.getDouble("damage-reduction-percent"), 90),
            clamp(section.getDouble("lifesteal-chance"), 100),
            clamp(section.getDouble("lifesteal-hearts"), 20),
            section.getBoolean("no-hunger"),
            Map.copyOf(effects));
    }

    SzafkaPerks plus(SzafkaPerks other) {
        if (other == NONE) return this;
        if (this == NONE) return other;
        Map<PotionEffectType, Integer> merged = new HashMap<>(effects);
        other.effects.forEach((type, amplifier) -> merged.merge(type, amplifier, Math::max));
        return new SzafkaPerks(
            miningPercent + other.miningPercent,
            damagePercent + other.damagePercent,
            Math.min(90, reductionPercent + other.reductionPercent),
            Math.min(100, lifestealChance + other.lifestealChance),
            Math.max(lifestealHearts, other.lifestealHearts),
            noHunger || other.noHunger,
            Map.copyOf(merged));
    }

    private static PotionEffectType effectType(String name) {
        try {
            PotionEffectType byKey = PotionEffectType.getByKey(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
            if (byKey != null) return byKey;
        } catch (IllegalArgumentException ignored) {
        }
        return PotionEffectType.getByName(name.toUpperCase(Locale.ROOT));
    }

    private static double clamp(double value, double max) {
        return Double.isFinite(value) ? Math.max(0, Math.min(max, value)) : 0;
    }
}
