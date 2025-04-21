package org.sbpo2025.challenge.SimulatedAnnealing;

public class FocusedLocalSearchConfig {
    public final int maxIterations;
    public final long timeoutMillis;
    public final double targetCost;
    public final int maxNoImprovement;
    public final boolean allowRestart;
    public final int patienceFactor;
    public final int windowSize;
    public final double improvementEpsilon;

    private FocusedLocalSearchConfig(Builder builder) {
        this.maxIterations = builder.maxIterations;
        this.timeoutMillis = builder.timeoutMillis;
        this.targetCost = builder.targetCost;
        this.maxNoImprovement = builder.maxNoImprovement;
        this.allowRestart = builder.allowRestart;
        this.patienceFactor = builder.patienceFactor;
        this.windowSize = builder.windowSize;
        this.improvementEpsilon = builder.improvementEpsilon;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int maxIterations = Integer.MAX_VALUE;
        private long timeoutMillis = Long.MAX_VALUE;
        private double targetCost = Double.NEGATIVE_INFINITY;
        private int maxNoImprovement = Integer.MAX_VALUE;
        private boolean allowRestart = false;
        private int patienceFactor = 10;
        private int windowSize = 100;
        private double improvementEpsilon = 1e-8;

        public Builder maxIterations(int v) { this.maxIterations = v; return this; }
        public Builder timeoutMillis(long v) { this.timeoutMillis = v; return this; }
        public Builder targetCost(double v) { this.targetCost = v; return this; }
        public Builder maxNoImprovement(int v) { this.maxNoImprovement = v; return this; }
        public Builder allowRestart(boolean v) { this.allowRestart = v; return this; }
        public Builder patienceFactor(int v) { this.patienceFactor = v; return this; }
        public Builder windowSize(int v) { this.windowSize = v; return this; }
        public Builder improvementEpsilon(double v) { this.improvementEpsilon = v; return this; }
        public FocusedLocalSearchConfig build() { return new FocusedLocalSearchConfig(this); }
    }
}
