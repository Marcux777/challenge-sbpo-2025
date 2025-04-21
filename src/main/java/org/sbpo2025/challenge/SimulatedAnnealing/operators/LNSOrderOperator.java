package org.sbpo2025.challenge.SimulatedAnnealing.operators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.sbpo2025.challenge.SimulatedAnnealing.BaseOperator;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Implementação do operador Large Neighborhood Search (LNS) para pedidos.
 * Este operador remove um conjunto significativo de pedidos e os reconstrói
 * utilizando uma heurística gulosa.
 */
public class LNSOrderOperator extends BaseOperator<ChallengeSolution> {

    // Porcentagem de pedidos a serem destruídos (removidos)
    private double destructionRate;

    /**
     * Construtor com taxa de destruição configurável.
     *
     * @param destructionRate Taxa de destruição (0.0-1.0)
     */
    public LNSOrderOperator(double destructionRate) {
        super("LNSOrder");
        this.destructionRate = destructionRate;
    }

    /**
     * Construtor padrão com taxa de destruição de 30%.
     */
    public LNSOrderOperator() {
        this(0.3);
    }

    @Override
    public double apply(ChallengeSolution solution) {
        Set<Integer> currentOrders = solution.getOrders();

        // Se não há pedidos na solução, retorna falha
        if (currentOrders.isEmpty()) {
            return 0;
        }

        // Salva o custo inicial antes das modificações
        double initialCost = solution.cost();

        // Seleciona aleatoriamente um subconjunto de pedidos para remover (destruição)
        List<Integer> ordersList = new ArrayList<>(currentOrders);
        int numToRemove = Math.max(1, (int)(ordersList.size() * destructionRate));
        Set<Integer> ordersToRemove = new HashSet<>();

        while (ordersToRemove.size() < numToRemove && !ordersList.isEmpty()) {
            int randomIndex = random.nextInt(ordersList.size());
            ordersToRemove.add(ordersList.get(randomIndex));
            ordersList.remove(randomIndex);
        }

        // Remove os pedidos selecionados
        for (Integer orderId : ordersToRemove) {
            solution.applyRemoveOrder(orderId);
        }

        // Fase de reconstrução: adiciona pedidos de volta na ordem de benefício
        List<Integer> availableOrders = solution.getInstance().getPedidos().stream()
            .map(p -> p.getId())
            .filter(id -> !solution.getOrders().contains(id))
            .collect(Collectors.toList());

        // Constrói uma lista de pares (ordemId, delta)
        List<OrderDelta> candidateOrders = new ArrayList<>();
        for (Integer orderId : availableOrders) {
            double delta = solution.calculateAddOrderDelta(orderId);
            candidateOrders.add(new OrderDelta(orderId, delta));
        }

        // Ordena por delta (benefício) - valores mais negativos primeiro
        candidateOrders.sort((a, b) -> Double.compare(a.delta, b.delta));

        // Adiciona os melhores candidatos até um certo limite
        int numToAdd = numToRemove;  // Tentamos adicionar a mesma quantidade que removemos
        for (int i = 0; i < Math.min(numToAdd, candidateOrders.size()); i++) {
            solution.applyAddOrder(candidateOrders.get(i).orderId);

            // Se a solução ficar inviável, aplicamos reparo
            if (!solution.isViable()) {
                solution.repair();
            }
        }

        // Retorna o delta global da operação
        double finalCost = solution.cost();
        return finalCost - initialCost;
    }

    /**
     * Define a taxa de destruição.
     *
     * @param rate Taxa de destruição (0.0-1.0)
     */
    public void setDestructionRate(double rate) {
        if (rate <= 0 || rate >= 1) {
            throw new IllegalArgumentException("Taxa de destruição deve estar entre 0 e 1");
        }
        this.destructionRate = rate;
    }

    /**
     * Classe auxiliar para armazenar pares de (ordemId, delta).
     */
    private static class OrderDelta {
        int orderId;
        double delta;

        OrderDelta(int orderId, double delta) {
            this.orderId = orderId;
            this.delta = delta;
        }
    }
}
