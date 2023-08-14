package numericutil;

import java.text.DecimalFormatSymbols;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;

public class CustomNumberFormat extends NumberFormat {

    @Override
    public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
        // Format with respect to maximum fraction digits
        String formatPattern = "#";
        int maxFracDigits = getMaximumFractionDigits();
        if (maxFracDigits > 0) {
            formatPattern += "." + new String(new char[maxFracDigits]).replace("\0", "#");
        }

        java.text.DecimalFormat defaultFormat = new java.text.DecimalFormat(formatPattern);
        String formattedNumber = defaultFormat.format(number);

        // Use the current decimal separator for formatting
        formattedNumber = formattedNumber.replace('.', getCurrentDecimalSeparator().charAt(0));
        return new StringBuffer(formattedNumber);
    }

    @Override
    public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
        return new StringBuffer(String.valueOf(number));
    }

    @Override
    public Number parse(String source) throws ParseException {
        Double result = tryParse(source);
        if (result != null) {
            return result;
        }
        throw new ParseException("Unable to parse number: " + source, 0);
    }

    @Override
    public Number parse(String source, ParsePosition parsePosition) {
        Double result = tryParse(source);
        if (result != null) {
            parsePosition.setIndex(source.length()); // Indicate successful parsing
            return result;
        }
        parsePosition.setErrorIndex(0); // Indicate where the error occurred
        return null;
    }

    private Double tryParse(String source) {
        try {
            return Double.parseDouble(source.replace(',', '.'));
        } catch (NumberFormatException e1) {
            try {
                return Double.parseDouble(source);
            } catch (NumberFormatException e2) {
                return null;
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
