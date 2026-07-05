package powder;

public final class SensitivityCalculator {

    public static double calculateSensitivity(double[][] data) {
        int count = 0;

        if ( data != null && data.length > 1) {
            double z_t = data[0][0];
            double z_v = data[0][1];
            double percentile = 0.0;
            for (int i = 1; i < data.length; i++) {
                double temperature = data[i][0];
                double velocity = data[i][1];
                double divider = (temperature - z_t)/15.0;
                if (divider == 0.0) {
                    // Defensive: a duplicate of the reference temperature would
                    // divide by zero (callers dedup, but don't rely on it).
                    continue;
                }
                double delta_v = (velocity - z_v)/divider;
                percentile += delta_v*100.0/velocity;
                ++count;
            }
            return count > 0 ? percentile/count : 0.0;
        }

        return 0.0;
    }
}
