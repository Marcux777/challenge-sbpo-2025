package org.sbpo2025.challenge.SimulatedAnnealing.operators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.sbpo2025.challenge.SimulatedAnnealing.BaseOperator;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Operador que remove um pedido aleatório da solução.
 */
public class RemoveOrderOperator extends BaseOperator<ChallengeSolution> {

    /**
     * Construtor padrão.
     */
    public RemoveOrderOperator() {
        super("RemOrder");
    }

    @Override
    public double apply(ChallengeSolution solution) {
        Set<Integer> currentOrders = solution.getOrders();

        // Se não há pedidos para remover, retorna falha
        if (currentOrders.isEmpty()) {
            return 0;
        }

        // Seleciona aleatoriamente um pedido para remover
        List<Integer> ordersList = new ArrayList<>(currentOrders);
        int orderToRemove = ordersList.get(random.nextInt(ordersList.size()));

        // Calcula o impacto antes de aplicar a mudança
        double delta = solution.calculateRemoveOrderDelta(orderToRemove);

        // Aplica a mudança
        solution.applyRemoveOrder(orderToRemove);

        // Repara a solução se necessário
        if (!solution.isViable()) {
            solution.repair();
        }

        return delta;
    }
}
