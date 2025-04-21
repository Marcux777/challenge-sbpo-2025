package org.sbpo2025.challenge.solution.movements;

import org.sbpo2025.challenge.solution.ChallengeSolution;

public interface SolutionMovement {
    boolean perturb(int dimensionIndex, double delta);
    void applyAddOrder(int orderId);
    void applyRemoveOrder(int orderId);
    void applyAddAisle(int aisleId);
    void applyRemoveAisle(int aisleId);
    boolean multipleAisleSwap(int count);
    boolean apply2OptMove(double[][] distance);
    boolean intensePerturbation(double intensityFactor);
}
