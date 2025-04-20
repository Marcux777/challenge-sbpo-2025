package org.sbpo2025.challenge.solution.validation;

import java.util.*;
import org.sbpo2025.challenge.model.*;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Responsável pela validação e reparo de soluções.
 * Verifica se uma solução é viável e fornece métodos para torná-la viável
 * quando necessário.
 */
public class SolutionValidator {

    private final ChallengeSolution solution;

    public SolutionValidator(ChallengeSolution solution) {
        this.solution = solution;
    }

    /**
     * Verifica a viabilidade da solução atual.
     * Uma solução é viável se todos os pedidos selecionados podem ser atendidos
     * pelos corredores selecionados.
     *
     * @return true se a solução for viável, false caso contrário
     */
    public boolean isViable() {
        Set<Integer> orders = solution.getOrders();
        Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();

        // Se não há pedidos, a solução é trivialmente viável
        if (orders.isEmpty()) {
            return true;
        }

        // Verifica se todos os pedidos estão completamente cobertos
        for (Integer orderId : orders) {
            if (!coverage.containsKey(orderId)) continue;

            Map<Integer, Integer> itemCoverage = coverage.get(orderId);
            for (Map.Entry<Integer, Integer> entry : itemCoverage.entrySet()) {
                if (entry.getValue() <= 0) {
                    return false; // Item não coberto, solução inviável
                }
            }
        }

        return true;
    }

    /**
     * Faz uma reparação da solução para torná-la viável.
     * Adiciona corredores necessários para atender pedidos não cobertos.
     *
     * @return true se a reparação foi bem-sucedida, false caso contrário
     */
    public boolean repair() {
        Set<Integer> orders = solution.getOrders();
        Set<Integer> aisles = solution.getAisles();
        Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();
        ChallengeInstance instance = solution.getInstance();

        // Se a solução já é viável, não precisa reparar
        if (isViable()) {
            return true;
        }

        // Mapeia itens não cobertos para pedidos
        Map<Integer, Set<Integer>> uncoveredItems = new HashMap<>(); // itemId -> Set<orderId>
        Map<Integer, Set<Integer>> itemToAisles = new HashMap<>(); // itemId -> Set<aisleId> que têm o item

        // Identifica itens não cobertos
        for (Integer orderId : orders) {
            if (!coverage.containsKey(orderId)) continue;

            Map<Integer, Integer> itemCoverage = coverage.get(orderId);
            for (Map.Entry<Integer, Integer> entry : itemCoverage.entrySet()) {
                int itemId = entry.getKey();
                if (entry.getValue() <= 0) {
                    // Item não coberto
                    if (!uncoveredItems.containsKey(itemId)) {
                        uncoveredItems.put(itemId, new HashSet<>());
                    }
                    uncoveredItems.get(itemId).add(orderId);
                }
            }
        }

        // Se não há itens não cobertos, a solução já é viável
        if (uncoveredItems.isEmpty()) {
            return true;
        }

        // Mapeia itens para corredores que os possuem
        for (Corredor corredor : instance.getCorredores()) {
            int aisleId = corredor.getId();

            for (ItemStock stock : corredor.getEstoque()) {
                int itemId = stock.getItemId();
                if (!itemToAisles.containsKey(itemId)) {
                    itemToAisles.put(itemId, new HashSet<>());
                }
                itemToAisles.get(itemId).add(aisleId);
            }
        }

        // Adiciona corredores para cobrir itens não cobertos
        boolean changes = false;
        // Fazemos uma cópia do mapa para não modificá-lo durante a iteração
        Map<Integer, Set<Integer>> workingUncoveredItems = new HashMap<>(uncoveredItems);

        for (Map.Entry<Integer, Set<Integer>> entry : workingUncoveredItems.entrySet()) {
            int itemId = entry.getKey();

            // Se não há corredores que tenham este item, não podemos reparar
            if (!itemToAisles.containsKey(itemId)) {
                continue;
            }

            // Encontra o corredor que cobre mais pedidos não atendidos
            int bestAisleId = -1;
            int maxCoverage = 0;

            for (Integer aisleId : itemToAisles.get(itemId)) {
                if (aisles.contains(aisleId)) continue; // Corredor já na solução

                // Conta quantos pedidos este corredor ajudaria a cobrir
                int coverageCount = 0;
                Corredor candidateCorridor = null;

                // Encontra o corredor correspondente
                for (Corredor c : instance.getCorredores()) {
                    if (c.getId() == aisleId) {
                        candidateCorridor = c;
                        break;
                    }
                }

                if (candidateCorridor == null) continue;

                // Conta cobertura para cada item no estoque deste corredor
                for (ItemStock stock : candidateCorridor.getEstoque()) {
                    int stockItemId = stock.getItemId();
                    if (uncoveredItems.containsKey(stockItemId)) {
                        coverageCount += uncoveredItems.get(stockItemId).size();
                    }
                }

                if (coverageCount > maxCoverage) {
                    maxCoverage = coverageCount;
                    bestAisleId = aisleId;
                }
            }

            // Adiciona o melhor corredor à solução
            if (bestAisleId != -1) {
                solution.applyAddAisle(bestAisleId);
                changes = true;

                // Atualiza os itens não cobertos
                Corredor addedCorridor = null;
                for (Corredor c : instance.getCorredores()) {
                    if (c.getId() == bestAisleId) {
                        addedCorridor = c;
                        break;
                    }
                }

                if (addedCorridor != null) {
                    for (ItemStock stock : addedCorridor.getEstoque()) {
                        int coveredItemId = stock.getItemId();
                        if (uncoveredItems.containsKey(coveredItemId)) {
                            // Remove este item da lista de não cobertos
                            uncoveredItems.remove(coveredItemId);
                        }
                    }
                }
            }
        }

        // Verifica se a solução agora é viável
        return isViable();
    }

    /**
     * Avalia a qualidade de cobertura da solução.
     * Retorna a porcentagem de pedidos que estão completamente cobertos.
     *
     * @return valor entre 0.0 e 1.0 representando a proporção de pedidos cobertos
     */
    public double coverageQuality() {
        Set<Integer> orders = solution.getOrders();
        Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();

        if (orders.isEmpty()) {
            return 0.0;
        }

        int fullyCovered = 0;

        for (Integer orderId : orders) {
            if (!coverage.containsKey(orderId)) continue;

            boolean orderCovered = true;
            for (Map.Entry<Integer, Integer> entry : coverage.get(orderId).entrySet()) {
                if (entry.getValue() <= 0) {
                    orderCovered = false;
                    break;
                }
            }

            if (orderCovered) {
                fullyCovered++;
            }
        }

        return (double) fullyCovered / orders.size();
    }

    /**
     * Remove os pedidos que não podem ser completamente atendidos
     * com os corredores selecionados atualmente.
     *
     * @return número de pedidos removidos
     */
    public int removeUnfeasibleOrders() {
        Set<Integer> orders = solution.getOrders();
        Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();
        int removedCount = 0;

        // Encontra pedidos que não podem ser atendidos
        List<Integer> ordersToRemove = new ArrayList<>();
        for (Integer orderId : orders) {
            if (!coverage.containsKey(orderId)) {
                ordersToRemove.add(orderId);
                continue;
            }

            // Verifica a cobertura dos itens
            Map<Integer, Integer> itemCoverage = coverage.get(orderId);
            boolean fullyDemandMet = true;
            for (Map.Entry<Integer, Integer> entry : itemCoverage.entrySet()) {
                if (entry.getValue() <= 0) {
                    fullyDemandMet = false;
                    break;
                }
            }

            if (!fullyDemandMet) {
                ordersToRemove.add(orderId);
            }
        }

        // Remove os pedidos inviáveis
        for (Integer orderId : ordersToRemove) {
            solution.applyRemoveOrder(orderId);
            removedCount++;
        }

        return removedCount;
    }
}
