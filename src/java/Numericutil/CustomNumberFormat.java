package numericutil;

import java.text.DecimalFormatSymbols;
import java.text.FieldPosition;  // <-- Add this import
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;

public class CustomNumberFormat extends NumberFormat {

    @Override
    public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
        // Use the current decimal separator for formatting
        String formattedNumber = String.valueOf(number).replace('.', getCurrentDecimalSeparator().charAt(0));
        return new StringBuffer(formattedNumber);
    }

    @Override
    public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
        return new StringBuffer(String.valueOf(number));
    }

    @Override
    public Number parse(String source, ParsePosition parsePosition) {
        String currentSeparator = getCurrentDecimalSeparator();
        String alternativeSeparator = getAlternativeDecimalSeparator();

        if (source.contains(alternativeSeparator)) {
            source = source.replace(alternativeSeparator, currentSeparator);
        }

        try {
            Double result = Double.parseDouble(source);
            parsePosition.setIndex(source.length());
            return result;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Number parse(String source) throws ParseException {
        String currentSeparator = getCurrentDecimalSeparator();
        String alternativeSeparator = getAlternativeDecimalSeparator();

        String modifiedSource = source;
        if (source.contains(alternativeSeparator)) {
            modifiedSource = source.replace(alternativeSeparator, currentSeparator);
        }

        try {
            return Double.parseDouble(modifiedSource);
        } catch (NumberFormatException e1) {
            try {
                // If parsing the modified source fails, try parsing the original source
                return Double.parseDouble(source);
            } catch (NumberFormatException e2) {
                throw new ParseException("Unable to parse number: " + source, 0);
            }
        }
    }

    private String getCurrentDecimalSeparator() {
        return String.valueOf(new DecimalFormatSymbols().getDecimalSeparator());
    }

    private String getAlternativeDecimalSeparator() {
        char currentSeparator = new DecimalFormatSymbols().getDecimalSeparator();
        return currentSeparator == '.' ? "," : ".";
    }
}
