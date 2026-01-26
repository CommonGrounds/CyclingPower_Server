package dev.java4now.Filters;

public class ExponentialSmoothing {
    private double smoothedValue;
    private final double alpha; // Smoothing factor (0 < alpha < 1)

    public ExponentialSmoothing(double alpha) {
        this.alpha = alpha;
    }

    public double filter(double newValue) {
        if (Double.isNaN(smoothedValue)) {
            smoothedValue = newValue; // Initialize with the first reading
        } else {
            smoothedValue = alpha * newValue + (1 - alpha) * smoothedValue;
        }
        return smoothedValue;
    }
}
