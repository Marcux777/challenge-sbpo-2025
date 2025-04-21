package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class EpsilonGreedyStrategy<S> implements SelectionStrategy<S> {
    private final double epsilon;
    public EpsilonGreedyStrategy(double epsilon) {
        this.epsilon = epsilon;
    }
    @Override
    public Operator<S> select(List<Operator<S>> ops, OperatorSelector<S>.Stats stats) {
        if (ThreadLocalRandom.current().nextDouble() < epsilon) {
            return ops.get(ThreadLocalRandom.current().nextInt(ops.size()));
        }
        Operator<S> best = null;
        double maxScore = Double.NEGATIVE_INFINITY;
        for (Operator<S> op : ops) {
            double score = op.getMeanReward();
            if (score > maxScore) {
                maxScore = score;
                best = op;
            }
        }
        return best != null ? best : ops.get(ThreadLocalRandom.current().nextInt(ops.size()));
    }
}
