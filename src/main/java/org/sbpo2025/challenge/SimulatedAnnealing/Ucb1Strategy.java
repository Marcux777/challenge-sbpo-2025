package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Ucb1Strategy<S> implements SelectionStrategy<S> {
    private final double ucbExplorationFactor;
    public Ucb1Strategy(double ucbExplorationFactor) {
        this.ucbExplorationFactor = ucbExplorationFactor;
    }
    @Override
    public Operator<S> select(List<Operator<S>> ops, OperatorSelector<S>.Stats stats) {
        Operator<S> selected = null;
        double maxUcb = Double.NEGATIVE_INFINITY;
        double logTotalApplications = Math.log(Math.max(2, stats.totalApplications.sum()));
        for (int i = 0; i < ops.size(); i++) {
            if (stats.usageCounts[i].get() == 0) {
                return ops.get(i);
            }
        }
        for (int i = 0; i < ops.size(); i++) {
            int usages = stats.usageCounts[i].get();
            double exploitation = ops.get(i).getMeanReward();
            double exploration = ucbExplorationFactor * Math.sqrt(logTotalApplications / usages);
            double ucbValue = exploitation + exploration;
            if (ucbValue > maxUcb) {
                maxUcb = ucbValue;
                selected = ops.get(i);
            }
        }
        return selected != null ? selected : ops.get(ThreadLocalRandom.current().nextInt(ops.size()));
    }
}
