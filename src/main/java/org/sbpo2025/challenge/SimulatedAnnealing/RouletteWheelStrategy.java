package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RouletteWheelStrategy<S> implements SelectionStrategy<S> {
    @Override
    public Operator<S> select(List<Operator<S>> ops, OperatorSelector<S>.Stats stats) {
        double minScore = ops.stream().mapToDouble(Operator::getMeanReward).min().orElse(0.0);
        double offset = minScore < 0 ? -minScore : 0;
        double sum = ops.stream().mapToDouble(op -> op.getMeanReward() + offset).sum();
        if (sum <= 1e-9) {
            return ops.get(ThreadLocalRandom.current().nextInt(ops.size()));
        }
        double r = ThreadLocalRandom.current().nextDouble() * sum;
        double acc = 0;
        for (Operator<S> op : ops) {
            acc += (op.getMeanReward() + offset);
            if (r <= acc) {
                return op;
            }
        }
        return ops.get(ThreadLocalRandom.current().nextInt(ops.size()));
    }
}
