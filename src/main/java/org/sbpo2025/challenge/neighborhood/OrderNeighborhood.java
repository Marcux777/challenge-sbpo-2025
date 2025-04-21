package org.sbpo2025.challenge.neighborhood;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.sbpo2025.challenge.solution.ChallengeSolution;

public class OrderNeighborhood implements Neighborhood<ChallengeSolution> {
    private final Map<ChallengeSolution, List<ChallengeSolution>> cache = new ConcurrentHashMap<>();
    @Override
    public Iterable<ChallengeSolution> neighbors(ChallengeSolution solution) {
        return cache.computeIfAbsent(solution, sol -> {
            List<ChallengeSolution> neighbors = new ArrayList<>();
            Set<Integer> currentOrders = sol.getOrders();
            int maxOrderId = sol.getInstance().getPedidos().size() - 1;
            // Adição de pedidos
            for (int orderId = 0; orderId <= maxOrderId; orderId++) {
                if (!currentOrders.contains(orderId)) {
                    ChallengeSolution neighbor = sol.copy();
                    neighbor.applyAddOrder(orderId);
                    if (!neighbor.isViable()) neighbor.repair();
                    neighbors.add(neighbor);
                }
            }
            // Remoção de pedidos
            for (Integer orderId : currentOrders) {
                ChallengeSolution neighbor = sol.copy();
                neighbor.applyRemoveOrder(orderId);
                neighbors.add(neighbor);
            }
            return neighbors;
        });
    }
}
