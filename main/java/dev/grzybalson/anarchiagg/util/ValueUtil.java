package dev.grzybalson.anarchiagg.util;

import java.util.concurrent.ThreadLocalRandom;
import org.apache.commons.lang3.Validate;

public class ValueUtil {
    public static int getRandInt(int min, int max) throws IllegalArgumentException {
        if (min == max) {
            return min;
        }
        Validate.isTrue((max > min ? 1 : 0) != 0, (String)"Max can't be smaller than min!", (Object)new Object[0]);
        return ThreadLocalRandom.current().nextInt(max - min + 1) + min;
    }

    public static double getRandDouble(double min, double max) throws IllegalArgumentException {
        if (min == max) {
            return min;
        }
        Validate.isTrue((max > min ? 1 : 0) != 0, (String)"Max can't be smaller than min!", (Object)new Object[0]);
        return ThreadLocalRandom.current().nextDouble() * (max - min) + min;
    }

    public static float getRandFloat(float min, float max) throws IllegalArgumentException {
        if (min == max) {
            return min;
        }
        Validate.isTrue((max > min ? 1 : 0) != 0, (String)"Max can't be smaller than min!", (Object)new Object[0]);
        return ThreadLocalRandom.current().nextFloat() * (max - min) + min;
    }

    public static boolean getChance(double chance) {
        return chance >= 100.0 || chance >= ValueUtil.getRandDouble(0.0, 100.0);
    }
}
