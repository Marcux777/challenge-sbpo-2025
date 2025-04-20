package org.sbpo2025.challenge.solution.incremental;

import java.util.*;
import org.sbpo2025.challenge.model.*;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Responsável pela avaliação de custo e cálculo incremental de modificações
 * na solução do problema SBPO 2025.
 */
public class IncrementalEvaluator {

    private final ChallengeSolution solution;

    public IncrementalEvaluator(ChallengeSolution solution) {
        this.solution = solution;
    }

    /**
     * Implementação completa do método evaluateCost, usando os dados da instância.
     * Esta é a implementação detalhada da função objetivo.
     */
    public double evaluateCost() {
        // Verifica que a cobertura está atualizada
        solution.updateCoverage();

        // Variáveis para o cálculo do custo
        double totalCost = 0.0;
        Set<Integer> orders = solution.getOrders();
        Set<Integer> aisles = solution.getAisles();
        int totalOrders = orders.size();
        int totalAisles = aisles.size();
        Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();

        // 1. Penalidade para pedidos não cobertos completamente
        for (Integer orderId : orders) {
            if (!coverage.containsKey(orderId)) continue;

            Map<Integer, Integer> itemCoverage = coverage.get(orderId);
            boolean fullyDemandMet = true;

            // Verifica se todos os itens deste pedido estão cobertos
            for (Map.Entry<Integer, Integer> entry : itemCoverage.entrySet()) {
                if (entry.getValue() <= 0) {
                    fullyDemandMet = false;
                    break;
                }
            }

            // Adiciona penalidade para pedidos não atendidos completamente
            if (!fullyDemandMet) {
                totalCost += 1000; // Penalidade alta para pedidos que não podem ser atendidos
            }
        }

        // 2. Custo básico proporcional ao número de corredores (desejamos minimizar)
        totalCost += totalAisles * 10; // Peso para número de corredores

        // 3. Custo proporcional à eficiência (corredores por pedido)
        if (totalOrders > 0) {
            double ratioCost = (double) totalAisles / totalOrders;
            totalCost += ratioCost * 50; // Peso para razão corredores/pedidos
        }

        // 4. Penalidade para soluções vazias ou triviais
        if (totalOrders == 0) {
            totalCost = Double.POSITIVE_INFINITY;
        }

        // Tratamento para valores inválidos
        if (Double.isNaN(totalCost)) {
            totalCost = Double.POSITIVE_INFINITY;
        }

        return totalCost;
    }

    /**
     * Calcula incrementalmente o impacto de adicionar um pedido na solução.
     */
    public double calculateAddOrderDelta(int orderId) {
        Set<Integer> orders = solution.getOrders();
        Set<Integer> aisles = solution.getAisles();
        Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();

        if (orders.contains(orderId)) {
            return 0.0; // Pedido já está na solução
        }

        // Custos da configuração atual
        int oldTotalOrders = orders.size();
        int totalAisles = aisles.size();

        // Simula adição do pedido
        int newTotalOrders = oldTotalOrders + 1;

        // 1. Verifica cobertura do novo pedido
        boolean canBeFulfilled = true;
        if (coverage.containsKey(orderId)) {
            for (Map.Entry<Integer, Integer> itemEntry : coverage.get(orderId).entrySet()) {
                // Se a cobertura for zero, o pedido não pode ser atendido com os corredores atuais
                if (itemEntry.getValue() <= 0) {
                    canBeFulfilled = false;
                    break;
                }
            }
        } else {
            canBeFulfilled = false; // Pedido não existe na instância
        }

        // 2. Calcula novo custo
        double newRatioCost = (double) totalAisles / newTotalOrders;
        double oldRatioCost = (oldTotalOrders > 0) ? (double) totalAisles / oldTotalOrders : 0;

        double delta = 0.0;

        // Atualiza custo de razão corredores/pedidos
        delta += (newRatioCost - oldRatioCost) * 50;

        // Adiciona penalidade se o pedido não puder ser atendido
        if (!canBeFulfilled) {
            delta += 1000; // Penalidade para pedido não atendido
        }

        return delta;
    }

    /**
     * Calcula incrementalmente o impacto de remover um pedido da solução.
     */
    public double calculateRemoveOrderDelta(int orderId) {
        Set<Integer> orders = solution.getOrders();
        Set<Integer> aisles = solution.getAisles();
        Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();

        if (!orders.contains(orderId)) {
            return 0.0; // Pedido não está na solução
        }

        // Custos da configuração atual
        int oldTotalOrders = orders.size();
        int totalAisles = aisles.size();

        // Simula remoção do pedido
        int newTotalOrders = oldTotalOrders - 1;

        double delta = 0.0;

        // 1. Verifica se o pedido estava sendo penalizado por falta de cobertura
        boolean wasPenalized = false;
        if (coverage.containsKey(orderId)) {
            for (Map.Entry<Integer, Integer> itemEntry : coverage.get(orderId).entrySet()) {
                if (itemEntry.getValue() <= 0) {
                    wasPenalized = true;
                    break;
                }
            }
        }

        // Remove penalidade se o pedido era penalizado
        if (wasPenalized) {
            delta -= 1000;
        }

        // 2. Atualiza custo de razão corredores/pedidos
        double oldRatioCost = (double) totalAisles / oldTotalOrders;
        double newRatioCost = (newTotalOrders > 0) ? (double) totalAisles / newTotalOrders : Double.POSITIVE_INFINITY;

        if (newTotalOrders == 0) {
            // Se não sobrar nenhum pedido, o custo será infinito
            return Double.POSITIVE_INFINITY - solution.cost();
        } else {
            delta += (newRatioCost - oldRatioCost) * 50;
        }

        return delta;
    }

    /**
     * Calcula incrementalmente o impacto de adicionar um corredor na solução.
     */
    public double calculateAddAisleDelta(int aisleId) {
        Set<Integer> orders = solution.getOrders();
        Set<Integer> aisles = solution.getAisles();
        Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();
        Map<Integer, Set<Integer>> aisleToOrders = solution.getAisleToOrders();
        ChallengeInstance instance = solution.getInstance();

        if (aisles.contains(aisleId)) {
            return 0.0; // Corredor já está na solução
        }

        // Custos da configuração atual
        int totalOrders = orders.size();
        int oldTotalAisles = aisles.size();

        // Simula adição do corredor
        int newTotalAisles = oldTotalAisles + 1;

        double delta = 0.0;

        // 1. Custo adicional do corredor
        delta += 10; // Custo fixo do corredor

        // 2. Atualiza custo de razão corredores/pedidos
        if (totalOrders > 0) {
            double oldRatioCost = (double) oldTotalAisles / totalOrders;
            double newRatioCost = (double) newTotalAisles / totalOrders;
            delta += (newRatioCost - oldRatioCost) * 50;
        }

        // 3. Verifica quais pedidos agora podem ser atendidos que antes não podiam
        Set<Integer> affectedOrders = new HashSet<>();
        if (aisleToOrders.containsKey(aisleId)) {
            affectedOrders.addAll(aisleToOrders.get(aisleId));
            affectedOrders.retainAll(orders); // Intersecção com pedidos selecionados
        }

        // Obtém informações do corredor
        Corredor corredor = instance.getCorredores().stream()
            .filter(c -> c.getId() == aisleId)
            .findFirst().orElse(null);

        if (corredor != null) {
            // Para cada pedido afetado
            for (Integer orderId : affectedOrders) {
                // Verifica o status atual de cobertura do pedido
                boolean currentlyPenalized = false;
                if (coverage.containsKey(orderId)) {
                    for (Map.Entry<Integer, Integer> itemEntry : coverage.get(orderId).entrySet()) {
                        if (itemEntry.getValue() <= 0) {
                            currentlyPenalized = true;
                            break;
                        }
                    }
                }

                // Simula a adição do corredor para este pedido
                Map<Integer, Integer> simulatedCoverage = new HashMap<>();
                if (coverage.containsKey(orderId)) {
                    for (Map.Entry<Integer, Integer> entry : coverage.get(orderId).entrySet()) {
                        simulatedCoverage.put(entry.getKey(), entry.getValue());
                    }
                }

                // Incrementa a cobertura simulada para itens do corredor
                for (ItemStock stock : corredor.getEstoque()) {
                    int itemId = stock.getItemId();
                    if (simulatedCoverage.containsKey(itemId)) {
                        simulatedCoverage.put(itemId, simulatedCoverage.get(itemId) + 1);
                    }
                }

                // Verifica se o pedido ainda será penalizado após adicionar o corredor
                boolean willBePenalized = false;
                for (Map.Entry<Integer, Integer> entry : simulatedCoverage.entrySet()) {
                    if (entry.getValue() <= 0) {
                        willBePenalized = true;
                        break;
                    }
                }

                // Se pedido era penalizado mas agora não será, subtrai a penalidade
                if (currentlyPenalized && !willBePenalized) {
                    delta -= 1000;
                }
            }
        }

        return delta;
    }

    /**
     * Calcula incrementalmente o impacto de remover um corredor da solução.
     */
    public double calculateRemoveAisleDelta(int aisleId) {
        Set<Integer> orders = solution.getOrders();
        Set<Integer> aisles = solution.getAisles();
        Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();
        Map<Integer, Set<Integer>> aisleToOrders = solution.getAisleToOrders();
        ChallengeInstance instance = solution.getInstance();

        if (!aisles.contains(aisleId)) {
            return 0.0; // Corredor não está na solução
        }

        // Custos da configuração atual
        int totalOrders = orders.size();
        int oldTotalAisles = aisles.size();

        // Simula remoção do corredor
        int newTotalAisles = oldTotalAisles - 1;

        double delta = 0.0;

        // 1. Custo do corredor removido
        delta -= 10; // Custo fixo do corredor

        // 2. Atualiza custo de razão corredores/pedidos
        if (totalOrders > 0) {
            double oldRatioCost = (double) oldTotalAisles / totalOrders;
            double newRatioCost = (double) newTotalAisles / totalOrders;
            delta += (newRatioCost - oldRatioCost) * 50;
        }

        // 3. Verifica quais pedidos agora não podem ser atendidos
        Set<Integer> affectedOrders = new HashSet<>();
        if (aisleToOrders.containsKey(aisleId)) {
            affectedOrders.addAll(aisleToOrders.get(aisleId));
            affectedOrders.retainAll(orders); // Intersecção com pedidos selecionados
        }

        // Obtém informações do corredor
        Corredor corredor = instance.getCorredores().stream()
            .filter(c -> c.getId() == aisleId)
            .findFirst().orElse(null);

        if (corredor != null) {
            // Para cada pedido afetado
            for (Integer orderId : affectedOrders) {
                // Verifica o status atual de cobertura do pedido
                boolean currentlyPenalized = false;
                if (coverage.containsKey(orderId)) {
                    for (Map.Entry<Integer, Integer> itemEntry : coverage.get(orderId).entrySet()) {
                        if (itemEntry.getValue() <= 0) {
                            currentlyPenalized = true;
                            break;
                        }
                    }
                }

                // Simula a remoção do corredor para este pedido
                Map<Integer, Integer> simulatedCoverage = new HashMap<>();
                if (coverage.containsKey(orderId)) {
                    for (Map.Entry<Integer, Integer> entry : coverage.get(orderId).entrySet()) {
                        simulatedCoverage.put(entry.getKey(), entry.getValue());
                    }
                }

                // Decrementa a cobertura simulada para itens do corredor
                for (ItemStock stock : corredor.getEstoque()) {
                    int itemId = stock.getItemId();
                    if (simulatedCoverage.containsKey(itemId)) {
                        simulatedCoverage.put(itemId, simulatedCoverage.get(itemId) - 1);
                    }
                }

                // Verifica se o pedido ficará sem cobertura após remover o corredor
                boolean willBePenalized = false;
                for (Map.Entry<Integer, Integer> entry : simulatedCoverage.entrySet()) {
                    if (entry.getValue() <= 0) {
                        willBePenalized = true;
                        break;
                    }
                }

                // Se pedido não era penalizado mas agora será, adiciona a penalidade
                if (!currentlyPenalized && willBePenalized) {
                    delta += 1000;
                }
            }
        }

        return delta;
    }

    /**
     * Calcula incrementalmente o custo de trocar dois pedidos.
     */
    public double calculateSwapOrdersDelta(int orderId1, int orderId2) {
        Set<Integer> orders = solution.getOrders();
        boolean hasOrder1 = orders.contains(orderId1);
        boolean hasOrder2 = orders.contains(orderId2);

        if (hasOrder1 == hasOrder2) {
            return 0.0; // Ambos dentro ou ambos fora, sem mudança
        }

        // Se order1 está na solução e order2 não, é equivalente a remover order1 e adicionar order2
        if (hasOrder1) {
            return calculateRemoveOrderDelta(orderId1) + calculateAddOrderDelta(orderId2);
        } else {
            return calculateRemoveOrderDelta(orderId2) + calculateAddOrderDelta(orderId1);
        }
    }
}
