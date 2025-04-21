package org.sbpo2025.challenge.solution.evaluation;

import org.sbpo2025.challenge.solution.ChallengeSolution;

public interface SolutionEvaluator {
    double evaluateCost();
    double calculateAddOrderDelta(int orderId);
    double calculateRemoveOrderDelta(int orderId);
    double calculateAddAisleDelta(int aisleId);
    double calculateRemoveAisleDelta(int aisleId);
    double calculateSwapOrdersDelta(int orderId1, int orderId2);
    double calculateSwapAisleDelta(int aisleToRemove, int aisleToAdd);
}
