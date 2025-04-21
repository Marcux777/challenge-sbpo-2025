package org.sbpo2025.challenge.solution.incremental;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.sbpo2025.challenge.model.*;
import org.sbpo2025.challenge.solution.ChallengeSolution;
import org.sbpo2025.challenge.solution.evaluation.SolutionEvaluator;

/**
 * Responsável pela avaliação de custo e cálculo incremental de modificações
 * na solução do problema SBPO 2025.
 */
public class IncrementalEvaluator implements SolutionEvaluator {

    private final double penaltyMissingOrder;
    private final double costPerAisle;
    private final double ratioWeight;

    private final ChallengeSolution solution;
    private final Map<Integer, Corredor> corredorById;
    private final Map<Key, Map<Integer, Integer>> coverageCache;

    // Versão da cobertura para controle de cache
    private volatile long coverageVersion = 0;
    private volatile long lastCacheVersion = -1;

    private record Key(int orderId, int aisleId) {}

    public IncrementalEvaluator(ChallengeSolution solution) {
        this(solution, 1_000.0, 10.0, 50.0);
    }

    public IncrementalEvaluator(ChallengeSolution solution, double penaltyMissingOrder, double costPerAisle, double ratioWeight) {
        this.solution = solution;
        ChallengeInstance instance = solution.getInstance();
        this.corredorById = instance.getCorredores().stream()
            .collect(java.util.stream.Collectors.toMap(Corredor::getId, java.util.function.Function.identity()));
        this.coverageCache = new ConcurrentHashMap<>();
        this.penaltyMissingOrder = penaltyMissingOrder;
        this.costPerAisle = costPerAisle;
        this.ratioWeight = ratioWeight;
    }

    // Deve ser chamado sempre que a cobertura da solução for atualizada
    public void invalidateCoverageCache() {
        coverageVersion++;
        coverageCache.clear();
        lastCacheVersion = coverageVersion;
    }

    // Exemplo de uso: chame este método no início dos métodos de delta
    private void checkCacheVersion() {
        if (lastCacheVersion != coverageVersion) {
            coverageCache.clear();
            lastCacheVersion = coverageVersion;
        }
    }

    private boolean isPenalized(Map<Integer, Integer> cov) {
        if (cov == null) return true;
        for (int v : cov.values()) {
            if (v <= 0) return true;
        }
        return false;
    }

    private Map<Integer, Integer> cloneCoverage(Map<Integer, Integer> original) {
        return (original == null) ? null : new HashMap<>(original);
    }

    private void applyStock(Map<Integer, Integer> cov, Corredor c, int sign) {
        if (cov == null || c == null) return;
        for (ItemStock stock : c.getEstoque()) {
            int itemId = stock.getItemId();
            if (cov.containsKey(itemId)) {
                cov.put(itemId, cov.get(itemId) + sign);
            }
        }
    }

    /**
     * Atualiza a cobertura simulada de um pedido ao adicionar ou remover um corredor.
     * @param orderId id do pedido
     * @param aisleId id do corredor
     * @param delta +1 para adicionar, -1 para remover
     * @param baseCoverage cobertura base (pode ser null)
     * @return novo mapa de cobertura simulada
     */
    private Map<Integer, Integer> simulateCoverageChange(int orderId, int aisleId, int delta, Map<Integer, Integer> baseCoverage) {
        Map<Integer, Integer> simulated = cloneCoverage(baseCoverage);
        Corredor corredor = corredorById.get(aisleId);
        applyStock(simulated, corredor, delta);
        return simulated;
    }

    /**
     * Implementação completa do método evaluateCost, usando os dados da instância.
     * Esta é a implementação detalhada da função objetivo.
     */
    public double evaluateCost() {
        solution.updateCoverage();
        Set<Integer> orders = solution.getOrders();
        Set<Integer> aisles = solution.getAisles();
        int totalOrders = orders.size();
        int totalAisles = aisles.size();
        Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();

        // 1. Penalidade para pedidos não cobertos completamente (paralelo)
        double penalty = orders.parallelStream()
            .filter(orderId -> coverage.containsKey(orderId))
            .mapToDouble(orderId -> {
                Map<Integer, Integer> itemCoverage = coverage.get(orderId);
                boolean fullyDemandMet = true;
                for (int v : itemCoverage.values()) {
                    if (v <= 0) {
                        fullyDemandMet = false;
                        break;
                    }
                }
                return fullyDemandMet ? 0.0 : penaltyMissingOrder;
            })
            .sum();

        double totalCost = penalty;
        totalCost += totalAisles * costPerAisle;
        if (totalOrders > 0) {
            double ratioCost = (double) totalAisles / totalOrders;
            totalCost += ratioCost * ratioWeight;
        }
        if (totalOrders == 0) {
            totalCost = Double.POSITIVE_INFINITY;
        }
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
        delta += (newRatioCost - oldRatioCost) * ratioWeight;

        // Adiciona penalidade se o pedido não puder ser atendido
        if (!canBeFulfilled) {
            delta += penaltyMissingOrder; // Penalidade para pedido não atendido
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
            delta -= penaltyMissingOrder;
        }

        // 2. Atualiza custo de razão corredores/pedidos
        double oldRatioCost = (double) totalAisles / oldTotalOrders;
        double newRatioCost = (newTotalOrders > 0) ? (double) totalAisles / newTotalOrders : Double.POSITIVE_INFINITY;

        if (newTotalOrders == 0) {
            // Se não sobrar nenhum pedido, o custo será infinito
            return Double.POSITIVE_INFINITY;
        } else {
            delta += (newRatioCost - oldRatioCost) * ratioWeight;
        }

        return delta;
    }

    /**
     * Calcula incrementalmente o impacto de adicionar um corredor na solução.
     */
    public double calculateAddAisleDelta(int aisleId) {
        checkCacheVersion();
        Set<Integer> orders = solution.getOrders();
        Set<Integer> aisles = solution.getAisles();
        Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();
        Map<Integer, Set<Integer>> aisleToOrders = solution.getAisleToOrders();

        if (aisles.contains(aisleId)) {
            return 0.0; // Corredor já está na solução
        }

        int totalOrders = orders.size();
        int oldTotalAisles = aisles.size();
        int newTotalAisles = oldTotalAisles + 1;
        double delta = 0.0;
        delta += costPerAisle;
        if (totalOrders > 0) {
            double oldRatioCost = (double) oldTotalAisles / totalOrders;
            double newRatioCost = (double) newTotalAisles / totalOrders;
            delta += (newRatioCost - oldRatioCost) * ratioWeight;
        }
        Set<Integer> affectedOrders = new HashSet<>();
        if (aisleToOrders.containsKey(aisleId)) {
            affectedOrders.addAll(aisleToOrders.get(aisleId));
            affectedOrders.retainAll(orders);
        }
        Corredor corredor = corredorById.get(aisleId);
        if (corredor != null) {
            for (Integer orderId : affectedOrders) {
                boolean currentlyPenalized = isPenalized(coverage.get(orderId));
                // Memoization: busca ou calcula a cobertura simulada
                Key key = new Key(orderId, aisleId);
                Map<Integer, Integer> simulatedCoverage = coverageCache.get(key);
                if (simulatedCoverage == null) {
                    simulatedCoverage = simulateCoverageChange(orderId, aisleId, 1, coverage.get(orderId));
                    coverageCache.put(key, simulatedCoverage);
                }
                boolean willBePenalized = isPenalized(simulatedCoverage);
                if (currentlyPenalized && !willBePenalized) {
                    delta -= penaltyMissingOrder;
                }
            }
        }
        return delta;
    }

    /**
     * Calcula incrementalmente o impacto de remover um corredor da solução.
     */
    public double calculateRemoveAisleDelta(int aisleId) {
        checkCacheVersion();
        Set<Integer> orders = solution.getOrders();
        Set<Integer> aisles = solution.getAisles();
        Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();
        Map<Integer, Set<Integer>> aisleToOrders = solution.getAisleToOrders();

        if (!aisles.contains(aisleId)) {
            return 0.0; // Corredor não está na solução
        }

        int totalOrders = orders.size();
        int oldTotalAisles = aisles.size();
        int newTotalAisles = oldTotalAisles - 1;
        double delta = 0.0;
        delta -= costPerAisle;
        if (totalOrders > 0) {
            double oldRatioCost = (double) oldTotalAisles / totalOrders;
            double newRatioCost = (double) newTotalAisles / totalOrders;
            delta += (newRatioCost - oldRatioCost) * ratioWeight;
        }
        Set<Integer> affectedOrders = new HashSet<>();
        if (aisleToOrders.containsKey(aisleId)) {
            affectedOrders.addAll(aisleToOrders.get(aisleId));
            affectedOrders.retainAll(orders);
        }
        Corredor corredor = corredorById.get(aisleId);
        if (corredor != null) {
            for (Integer orderId : affectedOrders) {
                boolean currentlyPenalized = isPenalized(coverage.get(orderId));
                // Memoization: busca ou calcula a cobertura simulada
                Key key = new Key(orderId, aisleId);
                Map<Integer, Integer> simulatedCoverage = coverageCache.get(key);
                if (simulatedCoverage == null) {
                    simulatedCoverage = simulateCoverageChange(orderId, aisleId, -1, coverage.get(orderId));
                    coverageCache.put(key, simulatedCoverage);
                }
                boolean willBePenalized = isPenalized(simulatedCoverage);
                if (!currentlyPenalized && willBePenalized) {
                    delta += penaltyMissingOrder;
                }
            }
        }
        return delta;
    }

    /**
     * Calcula incrementalmente o impacto de trocar um corredor por outro.
     * Combina os deltas de remoção e adição, ajustando para o estado intermediário.
     *
     * @param aisleToRemove ID do corredor a ser removido
     * @param aisleToAdd ID do corredor a ser adicionado
     * @return O delta de custo da troca
     */
    public double calculateSwapAisleDelta(int aisleToRemove, int aisleToAdd) {
        checkCacheVersion();
        Set<Integer> aisles = solution.getAisles();
        if (aisleToRemove == aisleToAdd || !aisles.contains(aisleToRemove) || aisles.contains(aisleToAdd)) {
            return 0.0;
        }
        // Reuso dos métodos auxiliares de delta
        double deltaRemove = calculateRemoveAisleDelta(aisleToRemove);
        double deltaAdd = calculateAddAisleDelta(aisleToAdd);
        return deltaRemove + deltaAdd;
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

    /**
     * Calcula em paralelo o delta de adicionar ou remover vários corredores.
     * @param aisleIds ids dos corredores
     * @param add true para adicionar, false para remover
     * @return mapa de id do corredor para delta
     */
    public Map<Integer, Double> calculateAisleDeltasInParallel(Collection<Integer> aisleIds, boolean add) {
        return aisleIds.parallelStream()
            .collect(java.util.stream.Collectors.toMap(
                id -> id,
                id -> add ? calculateAddAisleDelta(id) : calculateRemoveAisleDelta(id)
            ));
    }

    /**
     * Calcula em paralelo o delta de adicionar ou remover vários pedidos.
     * @param orderIds ids dos pedidos
     * @param add true para adicionar, false para remover
     * @return mapa de id do pedido para delta
     */
    public Map<Integer, Double> calculateOrderDeltasInParallel(Collection<Integer> orderIds, boolean add) {
        return orderIds.parallelStream()
            .collect(java.util.stream.Collectors.toMap(
                id -> id,
                id -> add ? calculateAddOrderDelta(id) : calculateRemoveOrderDelta(id)
            ));
    }
}
