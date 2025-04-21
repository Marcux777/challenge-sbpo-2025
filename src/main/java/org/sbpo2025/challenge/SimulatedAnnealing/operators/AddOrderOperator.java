package org.sbpo2025.challenge.SimulatedAnnealing.operators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.sbpo2025.challenge.SimulatedAnnealing.BaseOperator;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Operador que adiciona um pedido aleatório à solução.
 */
public class AddOrderOperator extends BaseOperator<ChallengeSolution> {

    /**
     * Construtor padrão.
     */
    public AddOrderOperator() {
        super("AddOrder");
    }

    @Override
    public double apply(ChallengeSolution solution) {
        Set<Integer> currentOrders = solution.getOrders();
        List<Integer> availableOrders = new ArrayList<>();

        // Coleta pedidos disponíveis para adicionar
        for (int i = 0; i < solution.getInstance().getPedidos().size(); i++) {
            if (!currentOrders.contains(i)) {
                availableOrders.add(i);
            }
        }

        // Se não há pedidos disponíveis, retorna falha
        if (availableOrders.isEmpty()) {
            return 0;
        }

        // Seleciona aleatoriamente um pedido para adicionar
        int orderToAdd = availableOrders.get(random.nextInt(availableOrders.size()));

        // Calcula o impacto antes de aplicar a mudança
        double delta = solution.calculateAddOrderDelta(orderToAdd);

        // Aplica a mudança
        solution.applyAddOrder(orderToAdd);

        // Repara a solução se necessário
        if (!solution.isViable()) {
            solution.repair();
        }

        return delta;
    }
}
