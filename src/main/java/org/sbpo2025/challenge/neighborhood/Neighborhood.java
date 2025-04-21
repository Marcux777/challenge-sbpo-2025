package org.sbpo2025.challenge.neighborhood;

import org.sbpo2025.challenge.solution.ChallengeSolution;

public interface Neighborhood<S> {
    Iterable<ChallengeSolution> neighbors(ChallengeSolution sol);
}
