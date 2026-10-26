package dev.grzybalson.anarchiagg.util;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class NumberUtil {

    private static final String[] SUFFIXES = {"", "k", "m", "b", "t", "q", "qq", "s", "ss"};
    private static final ThreadLocal<DecimalFormat> NO_DECIMALS = ThreadLocal.withInitial(() -> formatter("0"));
    private static final ThreadLocal<DecimalFormat> TWO_DECIMALS = ThreadLocal.withInitial(() -> formatter("0.00"));

    public static String formatBalance(double balance) {
        int index = 0;

        while (balance >= 1000 && index < SUFFIXES.length - 1) {
            balance /= 1000.0;
            index++;
        }

        if (index == 0) {
            return NO_DECIMALS.get().format(balance);
        }

        return TWO_DECIMALS.get().format(balance) + SUFFIXES[index];
    }

    private static DecimalFormat formatter(String pattern) {
        DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }
}
