package numericutil;

import javax.swing.text.NumberFormatter;
import java.text.ParseException;
import java.util.Locale;

public class CustomNumberFormatter extends NumberFormatter {

    public CustomNumberFormatter() {
        super(new CustomNumberFormat());
    }

    public CustomNumberFormatter(Locale locale) {
        super(new CustomNumberFormat());
    }

    @Override
    public Object stringToValue(String text) throws ParseException {
        return getFormat().parseObject(text);
    }

    @Override
    public String valueToString(Object value) throws ParseException {
        return getFormat().format(value);
    }

    @Override
    public CustomNumberFormat getFormat() {
        return (CustomNumberFormat) super.getFormat();
    }
}
