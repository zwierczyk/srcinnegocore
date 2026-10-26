package dev.grzybalson.modules.combat;

import org.bukkit.entity.Player;

public final class CombatHealthFormat {

    private CombatHealthFormat() {
    }

    public static String buildActionBar(Player target, String template) {
        return buildActionBar(target, template, 0.0D);
    }

    public static String buildActionBar(Player target, String template, double incomingDamage) {
        return buildActionBar(target, null, template, incomingDamage);
    }

    public static String buildActionBar(Player target, String nickOverride, String template, double incomingDamage) {
        if (target == null) {
            return "";
        }

        double healthHp = target.getHealth();
        double absorptionHp = target.getAbsorptionAmount();
        if (incomingDamage > 0.0D) {
            HealthAfterDamage projected = projectHealthAfterDamage(healthHp, absorptionHp, incomingDamage);
            healthHp = projected.healthHp();
            absorptionHp = projected.absorptionHp();
        }

        double maxHp = target.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null
            ? target.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()
            : 20.0D;
        double hearts = toDisplayHearts(healthHp, maxHp);
        double goldenHearts = toDisplayHearts(absorptionHp, maxHp);
        String heartText = formatHeartNumber(hearts);
        String goldenPart = goldenHearts > 0.0D
            ? " &#FF6700❤" + formatHeartNumber(goldenHearts)
            : "";

        String nick = nickOverride != null && !nickOverride.isBlank() ? nickOverride : target.getName();

        return template
            .replace("{nick}", nick)
            .replace("{hearts}", heartText)
            .replace("{golden}", goldenPart)
            .replace("{health}", "&#FD7A7A❤" + heartText);
    }

    private static HealthAfterDamage projectHealthAfterDamage(double healthHp, double absorptionHp, double damage) {
        double remaining = Math.max(0.0D, damage);
        if (absorptionHp > 0.0D && remaining > 0.0D) {
            double absorbed = Math.min(absorptionHp, remaining);
            absorptionHp -= absorbed;
            remaining -= absorbed;
        }
        healthHp = Math.max(0.0D, healthHp - remaining);
        return new HealthAfterDamage(healthHp, absorptionHp);
    }

    private record HealthAfterDamage(double healthHp, double absorptionHp) {
    }

    /** HP → liczba serc, zaokrąglone do 0.5. */
    public static double toDisplayHearts(double hp) {
        return toDisplayHearts(hp, 20.0D);
    }

    public static double toDisplayHearts(double hp, double maxHp) {
        double maxHearts = Math.max(1.0D, maxHp / 2.0D);
        double hearts = Math.max(0.0D, hp) / 2.0D;
        hearts = Math.min(maxHearts, hearts);
        return Math.round(hearts * 2.0D) / 2.0D;
    }

    public static String formatHeartNumber(double hearts) {
        double rounded = Math.round(hearts * 2.0D) / 2.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.001D) {
            return String.valueOf((int) Math.rint(rounded));
        }
        return String.valueOf(rounded);
    }
}
