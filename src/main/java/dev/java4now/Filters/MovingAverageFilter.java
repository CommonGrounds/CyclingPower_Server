package dev.java4now.Filters;

import java.util.LinkedList;
import java.util.Queue;

public class MovingAverageFilter {
    private final Queue<Double> window = new LinkedList<>();
    private final int period; // Number of readings to average
    private double sum = 0.0;

    public MovingAverageFilter(int period) {
        this.period = period;
    }

    public double filter(double newValue) {
        sum += newValue;
        window.add(newValue);

        if (window.size() > period) {
            sum -= window.remove();
        }

        return sum / window.size();
    }
}
