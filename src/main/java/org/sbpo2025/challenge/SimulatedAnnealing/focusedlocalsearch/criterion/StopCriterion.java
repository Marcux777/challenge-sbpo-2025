package org.sbpo2025.challenge.SimulatedAnnealing.focusedlocalsearch.criterion;

public interface StopCriterion {
    void onIteration(int iteration, double currentCost);
    void onCostUpdate(double bestCost, double initialCost);
    boolean shouldStop();
}
