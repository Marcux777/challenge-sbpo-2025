package org.sbpo2025.challenge.solution;

public interface SolutionOperations {
    double evaluateCost();
    double calculateAddOrderDelta(int orderId);
    double calculateRemoveOrderDelta(int orderId);
    double calculateAddAisleDelta(int aisleId);
    double calculateRemoveAisleDelta(int aisleId);
    double calculateSwapOrdersDelta(int orderId1, int orderId2);
    double calculateSwapAisleDelta(int aisleToRemove, int aisleToAdd);
    boolean perturb(int dimensionIndex, double delta);
    void applyAddOrder(int orderId);
    void applyRemoveOrder(int orderId);
    void applyAddAisle(int aisleId);
    void applyRemoveAisle(int aisleId);
    boolean multipleAisleSwap(int count);
    boolean apply2OptMove(double[][] distance);
    boolean intensePerturbation(double intensityFactor);
    boolean isViable();
    boolean repair();
}
