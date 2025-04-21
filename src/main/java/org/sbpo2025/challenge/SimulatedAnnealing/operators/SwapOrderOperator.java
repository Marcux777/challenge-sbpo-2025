package org.sbpo2025.challenge.SimulatedAnnealing.operators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.sbpo2025.challenge.SimulatedAnnealing.BaseOperator;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Operador que troca um pedido presente na solução por outro pedido não selecionado.
 */
public class SwapOrderOperator extends BaseOperator<ChallengeSolution> {

    /**
     * Construtor padrão.
     */
    public SwapOrderOperator() {
        super("SwpOrder");
    }

    @Override
    public double apply(ChallengeSolution solution) {
        Set<Integer> currentOrders = solution.getOrders();

        // Se não há pedidos na solução, retorna falha
        if (currentOrders.isEmpty()) {
            return 0;
        }

        // Lista de pedidos disponíveis (não incluídos na solução)
        List<Integer> availableOrders = solution.getInstance().getPedidos().stream()
            .map(p -> p.getId())
            .filter(id -> !currentOrders.contains(id))
            .collect(Collectors.toList());

        // Se não há pedidos disponíveis, retorna falha
        if (availableOrders.isEmpty()) {
            return 0;
        }

        // Seleciona aleatoriamente um pedido para remover e um para adicionar
        List<Integer> ordersList = new ArrayList<>(currentOrders);
        int orderToRemove = ordersList.get(random.nextInt(ordersList.size()));
        int orderToAdd = availableOrders.get(random.nextInt(availableOrders.size()));

        // Salva o custo inicial
        double initialCost = solution.cost();

        // Realiza a troca: primeiro remove, depois adiciona
        solution.applyRemoveOrder(orderToRemove); // Correção: usar o método correto para remover pedido
        solution.applyAddOrder(orderToAdd);

        // Repara a solução se necessário
        if (!solution.isViable()) {
            solution.repair();
        }

        // Retorna o delta global
        double finalCost = solution.cost();
        return finalCost - initialCost;
    }
}
