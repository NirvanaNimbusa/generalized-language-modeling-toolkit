package de.typology.smoothing;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class LegacyDecimalFormatter {

    private DecimalFormat decimalFormat;

    public LegacyDecimalFormatter(
            int decimalPlaces) {
        String format = "###.";
        for (int i = 0; i < decimalPlaces; i++) {
            format += "#";
        }
        // set decimalFormat to override LOCALE values
        decimalFormat = new DecimalFormat(format);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        decimalFormat.setDecimalFormatSymbols(symbols);
    }

    public String getRoundedResult(double input) {
        return decimalFormat.format(input);
    }
}