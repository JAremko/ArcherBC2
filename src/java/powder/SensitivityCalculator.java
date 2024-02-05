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
                double delta_v = (velocity - z_v)/divider;
                percentile += delta_v*100.0/velocity;
                ++count;
            }
            return percentile/count;
        }

        return 0.0;
    }
}
