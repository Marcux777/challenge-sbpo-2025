package org.sbpo2025.challenge.SimulatedAnnealing.operators;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.sbpo2025.challenge.SimulatedAnnealing.BaseOperator;
import org.sbpo2025.challenge.model.Pedido;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Operador especializado para maximizar a função objetivo (unidades/corredores).
 * Este operador analisa os pedidos em termos de sua contribuição para a
 * função objetivo e tenta realizar trocas que aumentem a densidade.
 */
public class ObjectiveFocusedOperator extends BaseOperator<ChallengeSolution> {

    private double intensityFactor;

    /**
     * Construtor com fator de intensidade configurável.
     *
     * @param intensityFactor Fator de intensidade (0.0-1.0)
     */
    public ObjectiveFocusedOperator(double intensityFactor) {
        super("ObjFocus");
        this.intensityFactor = intensityFactor;
    }

    /**
     * Construtor padrão.
     */
    public ObjectiveFocusedOperator() {
        this(0.2);
    }

    @Override
    public double apply(ChallengeSolution solution) {
        Set<Integer> currentOrders = solution.getOrders();
        Set<Integer> currentAisles = solution.getAisles();

        // Se não há pedidos ou corredores, retorna falha
        if (currentOrders.isEmpty() || currentAisles.isEmpty()) {
            return 0;
        }

        // Salva o custo inicial
        double initialCost = solution.cost();

        // Calcula a contribuição de cada pedido para a função objetivo
        Map<Integer, Double> orderContributions = calculateOrderContributions(solution);

        // Identifica pedidos com baixa contribuição que são candidatos a serem substituídos
        List<Integer> lowContributionOrders = orderContributions.entrySet().stream()
            .sorted(Comparator.comparingDouble(Map.Entry::getValue))
            .limit((int) Math.max(1, currentOrders.size() * intensityFactor))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        // Lista de pedidos disponíveis (não incluídos na solução)
        List<Integer> availableOrders = solution.getInstance().getPedidos().stream()
            .map(p -> p.getId())
            .filter(id -> !currentOrders.contains(id))
            .collect(Collectors.toList());

        // Calcula o impacto potencial de cada pedido disponível
        Map<Integer, Double> potentialImpacts = new HashMap<>();

        for (Integer orderId : availableOrders) {
            double delta = solution.calculateAddOrderDelta(orderId);
            int unidades = calcularUnidadesPedido(solution, orderId);

            // Avalia o benefício potencial: quanto mais unidades e menor delta, melhor
            double impact = unidades / (1.0 + Math.max(0, delta));
            potentialImpacts.put(orderId, impact);
        }

        // Ordenar pedidos disponíveis por impacto potencial (maior primeiro)
        List<Integer> sortedCandidates = potentialImpacts.entrySet().stream()
            .sorted(Comparator.comparingDouble(e -> -e.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        // Remove pedidos de baixa contribuição e adiciona candidatos com alto impacto
        boolean changed = false;
        Set<Integer> removedOrders = new HashSet<>();

        for (Integer orderToRemove : lowContributionOrders) {
            solution.applyRemoveOrder(orderToRemove);
            removedOrders.add(orderToRemove);
            changed = true;
        }

        int addCount = 0;
        for (Integer orderToAdd : sortedCandidates) {
            if (addCount >= removedOrders.size()) break;

            // Adiciona o pedido
            solution.applyAddOrder(orderToAdd);
            addCount++;
            changed = true;

            // Verifica se a solução continua viável
            if (!solution.isViable()) {
                // Se não estiver viável, faz reparo
                solution.repair();
            }
        }

        // Se não conseguimos aplicar nenhuma mudança, retorna falha
        if (!changed) {
            return 0;
        }

        // Retorna o delta global
        double finalCost = solution.cost();
        return finalCost - initialCost;
    }

    /**
     * Calcula a contribuição de cada pedido para a função objetivo.
     * Pedidos com mais unidades e que compartilham corredores com outros pedidos
     * têm maior contribuição.
     *
     * @param solution A solução atual
     * @return Mapa de ordem para sua contribuição na função objetivo
     */
    private Map<Integer, Double> calculateOrderContributions(ChallengeSolution solution) {
        Set<Integer> currentOrders = solution.getOrders();
        Map<Integer, Double> contributions = new HashMap<>();
        Map<Integer, Set<Integer>> orderToAisles = solution.getOrderToAisles();

        // Para cada pedido, calcula sua contribuição
        for (Integer orderId : currentOrders) {
            // Unidades do pedido
            int unidadesPedido = calcularUnidadesPedido(solution, orderId);

            // Corredores exclusivos deste pedido
            Set<Integer> exclusiveAisles = new HashSet<>(orderToAisles.getOrDefault(orderId, new HashSet<>()));

            for (Integer otherOrder : currentOrders) {
                if (!otherOrder.equals(orderId)) {
                    // Remove corredores compartilhados com outros pedidos
                    exclusiveAisles.removeAll(orderToAisles.getOrDefault(otherOrder, new HashSet<>()));
                }
            }

            // Contribuição = unidades / (corredores exclusivos + 1)
            // Quanto mais corredores exclusivos, menor a contribuição
            double contribution = (double) unidadesPedido / (exclusiveAisles.size() + 1);
            contributions.put(orderId, contribution);
        }

        return contributions;
    }

    /**
     * Calcula o total de unidades de um pedido.
     *
     * @param solution A solução atual
     * @param orderId ID do pedido
     * @return Total de unidades do pedido
     */
    private int calcularUnidadesPedido(ChallengeSolution solution, int orderId) {
        Pedido pedido = solution.getInstance().getPedidos().stream()
            .filter(p -> p.getId() == orderId)
            .findFirst()
            .orElse(null);

        if (pedido == null) return 0;

        return pedido.getItens().stream()
            .mapToInt(item -> item.getUnidades()) // Correção: usar getUnidades() em vez de getQuantidade()
            .sum();
    }

    /**
     * Define o fator de intensidade do operador.
     *
     * @param factor Fator de intensidade (0.0-1.0)
     */
    public void setIntensityFactor(double factor) {
        if (factor <= 0 || factor > 1) {
            throw new IllegalArgumentException("Fator de intensidade deve estar entre 0 e 1");
        }
        this.intensityFactor = factor;
    }
}
