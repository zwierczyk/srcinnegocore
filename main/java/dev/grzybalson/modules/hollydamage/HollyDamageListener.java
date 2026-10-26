package dev.grzybalson.modules.hollydamage;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.szafa.BoostHandler;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HollyDamageListener implements Listener {

    private static final String UPGRADE_LEVEL_KEY = "holly_upgrade_level";

    private final HollyCore plugin;
    private final HollyDamageConfig config;

    public HollyDamageListener(HollyCore plugin, HollyDamageConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!config.isEnabled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        // Liczymy raz na cios — bez odbiorcy nie ma po co budowac stringow debugu.
        boolean debug = config.isDebugEnabled() && hasDebugViewer(attacker, victim);

        double original = event.getDamage();
        String mode = config.getMode();
        List<String> debugLines = new ArrayList<>();
        double afterMult = original;
        double appliedMultiplier = 1.0;

        if ("simple".equalsIgnoreCase(mode)) {
            appliedMultiplier = config.getSimpleMultiplier();
            afterMult = original * appliedMultiplier;
            if (debug) {
                debugLines.add("&8&m                              ");
                debugLines.add("&#89E2FF&lDamage debug &8— &7tryb &fsimple");
                debugLines.add("&8» &7Atakujacy: &f" + attacker.getName() + " &8→ &7Cel: &f" + victim.getName());
                debugLines.add("&8» &7Bazowe obrazenia: &f" + formatD(original));
                debugLines.add("&8» &7Mnoznik: &f" + formatD(appliedMultiplier));
                debugLines.add("&8» &7Po mnozniku: &f" + formatD(afterMult));
            }
        } else if ("advanced".equalsIgnoreCase(mode)) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            if (!config.isWeaponsMatchAll() && !config.getWeapons().contains(weapon.getType())) {
                afterMult = original;
                appliedMultiplier = 1.0;
                if (debug) {
                    debugLines.add("&8&m                              ");
                    debugLines.add("&#89E2FF&lDamage debug &8— &7tryb &fadvanced");
                    debugLines.add("&8» &7Bron: &f" + weapon.getType().name() + " &8(&cbrak na liscie&8)");
                    debugLines.add("&8» &7Mnoznik: &f1.0 &7(vanilla)");
                }
            } else {
                int weaponPerk = itemPerk(weapon, true);
                int weaponSlot = config.toSlot(weaponPerk);
                ArmorCalc armorCalc = calcArmor(victim, debug);
                int diff = weaponSlot - armorCalc.slot();
                appliedMultiplier = config.getMultiplier(diff);
                double cooldown = 0.2D + 0.8D * clamp(attacker.getAttackCooldown(), 0.0F, 1.0F);
                double critMul = isCriticalAttack(attacker) ? 1.5D : 1.0D;
                double base = config.getBaseDamage();
                double potionBonus = potionAttackBonus(attacker);
                double zmiankiBonus = zmiankiAttackBonus(weapon);
                SzafkaAttack szafka = szafkaAttack(attacker);
                afterMult = Math.max(0.5D, base + potionBonus + zmiankiBonus) * cooldown * critMul * appliedMultiplier;
                afterMult = (afterMult + szafka.flatBonus()) * szafka.nurekMul() * szafka.percentMul();
                double weaponMul = config.getWeaponMultiplier(weapon.getType());
                afterMult *= weaponMul;
                if (debug) {
                    debugLines.add("&8&m                              ");
                    debugLines.add("&#89E2FF&lDamage debug &8— &7tryb &fadvanced (stala baza)");
                    debugLines.add("&8» &7Atakujacy: &f" + attacker.getName() + " &8→ &7Cel: &f" + victim.getName());
                    debugLines.add("&8» &7Bron perk: &f" + weaponPerk + " &7→ slot &f" + signed(weaponSlot));
                    debugLines.add("&8» &7" + armorCalc.detailLine());
                    debugLines.add("&8» &7Skala perkow: &f" + config.getMinLevel() + "&7–&f" + config.getMaxLevel()
                        + " &7baza: &f" + formatD(base));
                    debugLines.add("&8» &7Sila/slabosc: &f" + signedD(potionBonus)
                        + " &7koniczynka: &f" + signedD(zmiankiBonus)
                        + " &7→ baza: &f" + formatD(Math.max(0.5D, base + potionBonus + zmiankiBonus)));
                    debugLines.add("&8» &7Szafka: &f" + signedD(szafka.flatBonus())
                        + " &7nurek: &f" + formatD(szafka.nurekMul())
                        + " &7% : &f" + formatD(szafka.percentMul()));
                    debugLines.add("&8» &7Roznica slotow: &f" + signed(diff) + " &7mnoznik: &f" + formatD(appliedMultiplier));
                    debugLines.add("&8» &7Bron: &f" + weapon.getType().name() + " &7mnoznik materialu: &fx" + formatD(weaponMul));
                    debugLines.add("&8» &7CD: &f" + formatD(cooldown) + " &7crit: &f" + formatD(critMul));
                    debugLines.add("&8» &7Vanilla getDamage: &f" + formatD(original) + " &8(ignorowane)");
                    debugLines.add("&8» &7Wynik: &f" + formatD(afterMult));
                }
            }
        }

        double dripstoneMul = dripstoneMultiplier(attacker);
        if (dripstoneMul != 1.0D) {
            afterMult *= dripstoneMul;
            if (debug) {
                debugLines.add("&8» &7Dripstone: &f x" + formatD(dripstoneMul) + " &7→ &f" + formatD(afterMult));
            }
        }

        double maxD = config.getMaxDamage();
        double finalDamage = afterMult;
        boolean capped = false;
        if (maxD > 0D && finalDamage > maxD) {
            finalDamage = maxD;
            capped = true;
        }

        ArmorSetScale setScale = armorSetScale(victim, finalDamage);
        finalDamage = setScale.baseToSet();

        if (debug && !debugLines.isEmpty()) {
            debugLines.add("&8» &7Max damage: &f" + (maxD > 0D ? formatD(maxD) : "&7wylaczony"));
            if (capped) {
                debugLines.add("&#FF8300&l! &7Obciete do: &f" + formatD(afterMult > maxD ? maxD : afterMult));
            }
            if (setScale.diamond() + setScale.netherite() > 0) {
                debugLines.add("&8» &7Set: &f" + setScale.diamond() + " diax &8/ &f" + setScale.netherite()
                    + " neth &7→ kara: &f" + formatD(setScale.factor())
                    + " &8(" + Math.round((setScale.factor() - 1.0D) * 100.0D) + "% vs neth)");
            }
            debugLines.add("&8» &7Wynik BASE: &f" + formatD(finalDamage));
            debugLines.add("&8&m                              ");
            sendDebug(attacker, victim, debugLines);
        }

        event.setDamage(finalDamage);
    }

    /** Bicie dripstonem — osobny mnoznik z damage.yml (dziala tez poza lista broni). */
    private double dripstoneMultiplier(Player attacker) {
        if (!config.isDripstoneEnabled()) {
            return 1.0D;
        }
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || !config.getDripstoneMaterials().contains(hand.getType())) {
            return 1.0D;
        }
        return config.getDripstoneMultiplier();
    }

    private ArmorSetScale armorSetScale(Player victim, double holly) {
        int diamond = 0;
        int netherite = 0;
        int epf = 0;
        for (ItemStack piece : victim.getInventory().getArmorContents()) {
            if (piece == null || piece.getType().isAir()) {
                continue;
            }
            String name = piece.getType().name();
            if (name.startsWith("NETHERITE_")) {
                netherite++;
            } else if (name.startsWith("DIAMOND_")) {
                diamond++;
            }
            epf += protectionOf(piece);
        }
        int ranked = diamond + netherite;
        if (ranked <= 0) {
            return new ArmorSetScale(0, 0, 1.0D, holly);
        }
        double diamondRatio = diamond / (double) ranked;
        double factor = 1.0D + config.getDiamondSetPenalty() * diamondRatio;
        double armorPts = attributeValue(victim, Attribute.GENERIC_ARMOR, 5.0D * ranked);
        double toughPts = attributeValue(victim, Attribute.GENERIC_ARMOR_TOUGHNESS, 2.0D * diamond + 3.0D * netherite);
        double actualPass = vanillaArmorPassThrough(holly, armorPts, toughPts);
        double nethEqTough = toughPts + diamond;
        double nethPass = vanillaArmorPassThrough(holly, armorPts, nethEqTough);
        double protPass = protectionPassThrough(epf);
        double prot5Pass = protectionPassThrough(20);
        double expectedFinal = holly * actualPass * protPass;
        double nethProt5Final = holly * nethPass * prot5Pass;
        double targetFinal = nethProt5Final * factor;
        if (expectedFinal <= 1.0E-6D) {
            return new ArmorSetScale(diamond, netherite, factor, holly);
        }
        double base = holly * (targetFinal / expectedFinal);
        return new ArmorSetScale(diamond, netherite, factor, base);
    }

    private static double attributeValue(Player player, Attribute attribute, double fallback) {
        try {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                return instance.getValue();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static double protectionPassThrough(int epf) {
        return 1.0D - Math.min(20.0D, Math.max(0.0D, epf)) / 25.0D;
    }

    /** 1.9+ armor formula: ile % BASE przechodzi po pancerzu. */
    private static double vanillaArmorPassThrough(double damage, double armor, double toughness) {
        if (damage <= 0.0D) {
            return 1.0D;
        }
        double inner = Math.max(armor / 5.0D, armor - damage / (2.0D + toughness / 4.0D));
        double reduction = Math.min(20.0D, Math.max(0.0D, inner)) / 25.0D;
        return 1.0D - reduction;
    }

    private ArmorCalc calcArmor(Player player, boolean debug) {
        int totalPerk = 0;
        int pieces = 0;
        StringBuilder sb = debug ? new StringBuilder("Zbroja: ") : null;
        boolean first = true;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType().isAir()) {
                continue;
            }
            boolean listed = config.getArmor().contains(armor.getType());
            // perk poza lista trafia wylacznie do debugu — bez odbiorcy nie ma po co go liczyc
            int perk = (listed || debug) ? itemPerk(armor, false) : 0;
            if (debug) {
                if (!first) {
                    sb.append("&8, ");
                }
                first = false;
            }
            if (listed) {
                totalPerk += perk;
                pieces++;
                if (debug) {
                    sb.append("&f").append(shortName(armor.getType().name())).append("&7(p").append(perk).append(")");
                }
            } else if (debug) {
                sb.append("&8").append(shortName(armor.getType().name())).append("&7(p").append(perk).append(", off)");
            }
        }
        if (pieces == 0) {
            int slot = config.toSlot(config.getMinLevel());
            return new ArmorCalc(slot, debug ? sb + " &8(brak) → slot " + signed(slot) : "");
        }
        int avgPerk = Math.round(totalPerk / (float) pieces);
        int slot = config.toSlot(avgPerk);
        return new ArmorCalc(slot, debug ? sb + " &7| sredni perk: &f" + avgPerk + " &7→ slot &f" + signed(slot) : "");
    }

    private int itemPerk(ItemStack item, boolean weapon) {
        if (item == null || item.getType().isAir()) {
            return config.getMinLevel();
        }
        // UpgradeManager czyta dokladnie ten sam klucz PDC — osobny odczyt to dodatkowa kopia ItemMeta
        var upgradeManager = plugin.getUpgradeManager();
        if (upgradeManager != null) {
            int level = upgradeManager.getUpgradeLevel(item);
            if (level > 0) {
                return level;
            }
        } else {
            Integer nbt = NBTUtils.getInt(item, UPGRADE_LEVEL_KEY);
            if (nbt != null && nbt > 0) {
                return nbt;
            }
        }
        if (weapon) {
            int sharpness = sharpnessOf(item);
            if (sharpness <= 0) {
                return config.getMinLevel();
            }
            return Math.max(1, (sharpness - 1) / 2);
        }
        int protection = protectionOf(item);
        return Math.max(0, protection / 2);
    }

    private static int sharpnessOf(ItemStack item) {
        try {
            return item.getEnchantmentLevel(Enchantment.DAMAGE_ALL);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int protectionOf(ItemStack item) {
        try {
            return item.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean isCriticalAttack(Player attacker) {
        if (attacker.isOnGround() || attacker.getFallDistance() <= 0.0F) {
            return false;
        }
        if (attacker.isInsideVehicle() || attacker.isClimbing() || attacker.isInWater() || attacker.isGliding()) {
            return false;
        }
        return !attacker.hasPotionEffect(PotionEffectType.BLINDNESS);
    }

    /** getBonus skanuje lore, wiec pytamy kazdy manager dokladnie raz i uzywamy tego, co zwrocil. */
    private double zmiankiAttackBonus(ItemStack weapon) {
        var mslf = plugin.getMslfSzczesciarzManager();
        if (mslf != null && mslf.getZmiankiManager() != null) {
            ZmiankiManager manager = mslf.getZmiankiManager();
            Integer bonus = manager.getBonus(weapon);
            if (bonus != null) {
                return bonus.doubleValue() * manager.getDamageMultiplier();
            }
        }
        var ogHub = plugin.getOgHubZmiankiManager();
        if (ogHub != null && ogHub.getZmiankiManager() != null) {
            ZmiankiManager manager = ogHub.getZmiankiManager();
            Integer bonus = manager.getBonus(weapon);
            if (bonus != null) {
                return bonus.doubleValue() * manager.getDamageMultiplier();
            }
        }
        return 0.0D;
    }

    private SzafkaAttack szafkaAttack(Player attacker) {
        BoostHandler handler = resolveBoostHandler();
        if (handler == null) {
            return SzafkaAttack.NONE;
        }
        return new SzafkaAttack(
            handler.extraAttackDamage(attacker),
            handler.nurekWaterBoost(attacker) ? 1.2D : 1.0D,
            handler.attackDamageMultiplier(attacker)
        );
    }

    private BoostHandler resolveBoostHandler() {
        var mslf = plugin.getMslfKostiumyManager();
        if (mslf != null && mslf.getBoostHandler() != null) {
            return mslf.getBoostHandler();
        }
        var ogHub = plugin.getOgHubSzafkaManager();
        if (ogHub != null && ogHub.getBoostHandler() != null) {
            return ogHub.getBoostHandler();
        }
        return null;
    }

    private double potionAttackBonus(Player attacker) {
        double bonus = 0.0D;
        PotionEffect strength = attacker.getPotionEffect(strengthType());
        if (strength != null) {
            bonus += config.getStrengthPerLevel() * (strength.getAmplifier() + 1);
        }
        PotionEffect weakness = attacker.getPotionEffect(PotionEffectType.WEAKNESS);
        if (weakness != null) {
            bonus -= config.getWeaknessPerLevel() * (weakness.getAmplifier() + 1);
        }
        return bonus;
    }

    private static PotionEffectType strengthType() {
        PotionEffectType type = PotionEffectType.INCREASE_DAMAGE;
        if (type != null) {
            return type;
        }
        PotionEffectType renamed = PotionEffectType.getByName("STRENGTH");
        return renamed != null ? renamed : PotionEffectType.INCREASE_DAMAGE;
    }

    private boolean hasDebugViewer(Player attacker, Player victim) {
        String perm = config.getDebugPermission();
        return (config.isDebugNotifyAttacker() && attacker.hasPermission(perm))
            || (config.isDebugNotifyVictim() && victim.hasPermission(perm));
    }

    private void sendDebug(Player attacker, Player victim, List<String> lines) {
        String perm = config.getDebugPermission();
        boolean toAttacker = config.isDebugNotifyAttacker() && attacker.hasPermission(perm);
        boolean toVictim = config.isDebugNotifyVictim() && victim.hasPermission(perm);
        if (!toAttacker && !toVictim) {
            return;
        }
        Component msg = joinDebug(lines);
        if (toAttacker) {
            attacker.sendMessage(msg);
        }
        if (toVictim) {
            victim.sendMessage(msg);
        }
    }

    private static Component joinDebug(List<String> lines) {
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            out = out.append(ColorUtils.colorizeComponent(lines.get(i)));
            if (i < lines.size() - 1) {
                out = out.append(Component.newline());
            }
        }
        return out;
    }

    private static String formatD(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private static String signedD(double value) {
        String body = formatD(Math.abs(value));
        if (value > 0.0D) {
            return "+" + body;
        }
        if (value < 0.0D) {
            return "-" + body;
        }
        return body;
    }

    private static String shortName(String mat) {
        return mat.length() > 18 ? mat.substring(mat.length() - 18) : mat;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ArmorCalc(int slot, String detailLine) {
    }

    private record ArmorSetScale(int diamond, int netherite, double factor, double baseToSet) {
    }

    private record SzafkaAttack(double flatBonus, double nurekMul, double percentMul) {
        private static final SzafkaAttack NONE = new SzafkaAttack(0.0D, 1.0D, 1.0D);
    }
}
