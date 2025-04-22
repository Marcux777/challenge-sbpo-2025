package org.sbpo2025.challenge.SimulatedAnnealing.focusedlocalsearch.criterion;

import org.sbpo2025.challenge.SimulatedAnnealing.focusedlocalsearch.FocusedLocalSearchConfig;

public class DefaultStopCriterion implements StopCriterion {
    private final int maxIterations;
    private final long timeoutMillis;
    private final double targetCost;
    private final int patience;
    private final long startTime;
    private int currentIteration = 0;
    private int noImprove = 0;
    private double bestCost;
    private double initialCost;

    public DefaultStopCriterion(FocusedLocalSearchConfig config, double initialCost) {
        this.maxIterations = config.maxIterations;
        this.timeoutMillis = config.timeoutMillis;
        this.targetCost = config.targetCost;
        this.patience = Math.max(1, config.patienceFactor);
        this.startTime = System.currentTimeMillis();
        this.initialCost = initialCost;
        this.bestCost = initialCost;
    }

    @Override
    public void onIteration(int iteration, double currentCost) {
        this.currentIteration = iteration;
    }

    @Override
    public void onCostUpdate(double bestCost, double initialCost) {
        if (bestCost < this.bestCost) {
            this.noImprove = 0;
            this.bestCost = bestCost;
        } else {
            this.noImprove++;
        }
    }

    @Override
    public boolean shouldStop() {
        if (currentIteration >= maxIterations) return true;
        if (System.currentTimeMillis() - startTime > timeoutMillis) return true;
        if (bestCost <= targetCost) return true;
        if (noImprove >= patience) return true;
        return false;
    }
}
