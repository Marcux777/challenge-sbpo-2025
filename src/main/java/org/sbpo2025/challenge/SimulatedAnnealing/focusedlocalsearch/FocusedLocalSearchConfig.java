package org.sbpo2025.challenge.SimulatedAnnealing.focusedlocalsearch;

/**
 * Configuration class for FocusedLocalSearch.
 * Contains parameters to control the local search process.
 */
public class FocusedLocalSearchConfig {
    /** Maximum number of iterations allowed in the local search. */
    public final int maxIterations;
    /** Maximum time (in milliseconds) allowed for the local search. */
    public final long timeoutMillis;
    /** Target cost: if reached, the search is stopped. */
    public final double targetCost;
    /** Maximum number of iterations without improvement before stopping (stagnation criterion). */
    public final int maxNoImprovement;
    /** Allows restarting the local search after stagnation. */
    public final boolean allowRestart;
    /** Patience factor: controls how many iterations without improvement are tolerated before stopping. */
    public final int patienceFactor;
    /** Window size for strategies that use history (e.g., tabu search). */
    public final int windowSize;
    /** Minimum tolerance to consider a significant cost improvement. */
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

    /**
     * Returns a new builder for FocusedLocalSearchConfig.
     */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for FocusedLocalSearchConfig.
     */
    public static class Builder {
        private int maxIterations = Integer.MAX_VALUE;
        private long timeoutMillis = Long.MAX_VALUE;
        private double targetCost = Double.NEGATIVE_INFINITY;
        private int maxNoImprovement = Integer.MAX_VALUE;
        private boolean allowRestart = false;
        /** Number of iterations without improvement before stopping. */
        private int patienceFactor = 10;
        private int windowSize = 100;
        /** Tolerance to consider improvement. */
        private double improvementEpsilon = 1e-8;

        /**
         * Sets the maximum number of iterations.
         */
        public Builder maxIterations(int v) { this.maxIterations = v; return this; }
        /**
         * Sets the maximum allowed time in milliseconds.
         */
        public Builder timeoutMillis(long v) { this.timeoutMillis = v; return this; }
        /**
         * Sets the target cost to stop the search.
         */
        public Builder targetCost(double v) { this.targetCost = v; return this; }
        /**
         * Sets the maximum number of iterations without improvement.
         */
        public Builder maxNoImprovement(int v) { this.maxNoImprovement = v; return this; }
        /**
         * Enables or disables restart after stagnation.
         */
        public Builder allowRestart(boolean v) { this.allowRestart = v; return this; }
        /**
         * Sets the patience factor.
         */
        public Builder patienceFactor(int v) { this.patienceFactor = v; return this; }
        /**
         * Sets the window size for history-based strategies.
         */
        public Builder windowSize(int v) { this.windowSize = v; return this; }
        /**
         * Sets the minimum improvement epsilon.
         */
        public Builder improvementEpsilon(double v) { this.improvementEpsilon = v; return this; }
        /**
         * Builds the configuration object.
         */
        public FocusedLocalSearchConfig build() { return new FocusedLocalSearchConfig(this); }
    }
}
