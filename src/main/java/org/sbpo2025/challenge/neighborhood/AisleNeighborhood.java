package org.sbpo2025.challenge.neighborhood;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.sbpo2025.challenge.solution.ChallengeSolution;

public class AisleNeighborhood implements Neighborhood<ChallengeSolution> {
    private final Map<ChallengeSolution, List<ChallengeSolution>> cache = new ConcurrentHashMap<>();

    @Override
    public Iterable<ChallengeSolution> neighbors(ChallengeSolution solution) {
        return cache.computeIfAbsent(solution, sol -> {
            List<ChallengeSolution> neighbors = new ArrayList<>();
            Set<Integer> currentAisles = sol.getAisles();
            int maxAisleId = sol.getInstance().getCorredores().size() - 1;
            // Adição de corredores
            for (int aisleId = 0; aisleId <= maxAisleId; aisleId++) {
                if (!currentAisles.contains(aisleId)) {
                    ChallengeSolution neighbor = sol.copy();
                    neighbor.applyAddAisle(aisleId);
                    neighbors.add(neighbor);
                }
            }
            // Remoção de corredores
            for (Integer aisleId : currentAisles) {
                ChallengeSolution neighbor = sol.copy();
                neighbor.applyRemoveAisle(aisleId);
                if (!neighbor.isViable()) neighbor.repair();
                neighbors.add(neighbor);
            }
            // Troca de corredores (swap)
            List<Integer> availableAisles = new ArrayList<>();
            for (int aisleId = 0; aisleId <= maxAisleId; aisleId++) {
                if (!currentAisles.contains(aisleId)) {
                    availableAisles.add(aisleId);
                }
            }
            for (Integer aisleToRemove : currentAisles) {
                for (Integer aisleToAdd : availableAisles) {
                    ChallengeSolution neighbor = sol.copy();
                    neighbor.applyRemoveAisle(aisleToRemove);
                    neighbor.applyAddAisle(aisleToAdd);
                    if (!neighbor.isViable()) neighbor.repair();
                    neighbors.add(neighbor);
                }
            }
            return neighbors;
        });
    }
}
