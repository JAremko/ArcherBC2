package numericutil;

import java.text.DecimalFormatSymbols;
import java.text.FieldPosition;
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
    public Number parse(String source) throws ParseException {
        try {
            // First, try parsing the source string after replacing any commas with dots
            return Double.parseDouble(source.replace(',', '.'));
        } catch (NumberFormatException e1) {
            try {
                // If that fails, try parsing the original source string
                return Double.parseDouble(source);
            } catch (NumberFormatException e2) {
                throw new ParseException("Unable to parse number: " + source, 0);
            }
        }
    }

    @Override
    public Number parse(String source, ParsePosition parsePosition) {
        try {
            // First, try parsing the source string after replacing any commas with dots
            Double result = Double.parseDouble(source.replace(',', '.'));
            parsePosition.setIndex(source.length()); // Indicate successful parsing
            return result;
        } catch (NumberFormatException e1) {
            try {
                // If that fails, try parsing the original source string
                Double result = Double.parseDouble(source);
                parsePosition.setIndex(source.length()); // Indicate successful parsing
                return result;
            } catch (NumberFormatException e2) {
                parsePosition.setErrorIndex(0); // Indicate where the error occurred
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
