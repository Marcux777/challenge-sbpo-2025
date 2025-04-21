package org.sbpo2025.challenge.SimulatedAnnealing.strategies;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.sbpo2025.challenge.SimulatedAnnealing.SelectionStrategy;
import org.sbpo2025.challenge.SimulatedAnnealing.Operator;
import org.sbpo2025.challenge.SimulatedAnnealing.OperatorSelector;

public class RouletteWheelStrategy<S> implements SelectionStrategy<S> {
    @Override
    public Operator<S> select(List<Operator<S>> ops, OperatorSelector<S>.Stats stats) {
        double total = 0.0;
        for (Operator<S> op : ops) {
            total += op.getSelectionProbability();
        }
        double r = ThreadLocalRandom.current().nextDouble() * total;
        double cumulative = 0.0;
        for (Operator<S> op : ops) {
            cumulative += op.getSelectionProbability();
            if (r <= cumulative) {
                return op;
            }
        }
        return ops.get(ThreadLocalRandom.current().nextInt(ops.size()));
    }
}
