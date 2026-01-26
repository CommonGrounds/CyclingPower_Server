package dev.java4now.Filters;

public class LowPassFilter {
    private double smoothedValue;
    private final double alpha; // Smoothing factor (0 < alpha < 1)

    public LowPassFilter(double alpha) {
        this.alpha = alpha;
    }

    public double filter(double newValue) {
        if (Double.isNaN(smoothedValue)) {
            smoothedValue = newValue; // Initialize with the first reading
        } else {
            smoothedValue = smoothedValue + alpha * (newValue - smoothedValue);
        }
        return smoothedValue;
    }
}
