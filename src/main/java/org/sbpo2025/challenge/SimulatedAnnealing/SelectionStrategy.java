package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.List;

public interface SelectionStrategy<S> {
    Operator<S> select(List<Operator<S>> ops, OperatorSelector<S>.Stats stats);
}
