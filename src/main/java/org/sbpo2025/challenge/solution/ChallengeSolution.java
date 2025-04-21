package org.sbpo2025.challenge.solution;

import java.util.*;
import java.util.stream.Collectors;

import org.sbpo2025.challenge.model.*;
import org.sbpo2025.challenge.solution.incremental.IncrementalEvaluator;
import org.sbpo2025.challenge.solution.movements.SolutionMover;
import org.sbpo2025.challenge.solution.validation.SolutionValidator;

/**
 * Representa uma solução para o problema do SBPO 2025.
 * Esta classe foi modularizada para separar diferentes responsabilidades em classes auxiliares.
 */
public class ChallengeSolution {

    // Componentes principais da solução
    private final ChallengeInstance instance;
    private Set<Integer> orders;
    private Set<Integer> aisles;
    private double currentCost;

    // Componentes auxiliares
    private final IncrementalEvaluator evaluator;
    private final SolutionMover mover;
    private final SolutionValidator validator;

    // Estruturas de dados auxiliares para avaliação incremental
    private Map<Integer, Set<Integer>> orderToAisles; // Mapa de pedidos para corredores que os atendem
    private Map<Integer, Set<Integer>> aisleToOrders; // Mapa de corredores para pedidos atendidos
    private Map<Integer, Map<Integer, Integer>> coverage; // [orderId][itemId] = cobertura por corredores selecionados

    /**
     * Construtor principal da solução
     */
    public ChallengeSolution(ChallengeInstance instance, Set<Integer> orders, Set<Integer> aisles) {
        this.instance = instance;
        this.orders = new HashSet<>(orders);
        this.aisles = new HashSet<>(aisles);
        this.currentCost = Double.POSITIVE_INFINITY;

        // Inicializa estruturas auxiliares
        this.orderToAisles = new HashMap<>();
        this.aisleToOrders = new HashMap<>();
        this.coverage = new HashMap<>();

        // Inicializa os componentes
        this.evaluator = new IncrementalEvaluator(this);
        this.mover = new SolutionMover(this);
        this.validator = new SolutionValidator(this);

        // Inicializa mapeamentos
        initializeAuxiliaryDataStructures();
    }

    /**
     * Inicializa as estruturas de dados auxiliares para avaliação incremental.
     */
    private void initializeAuxiliaryDataStructures() {
        // Inicializa mapeamento de pedidos para corredores
        for (Pedido pedido : instance.getPedidos()) {
            int orderId = pedido.getId();
            orderToAisles.put(orderId, new HashSet<>());
            coverage.put(orderId, new HashMap<>());

            // Inicializa contadores de cobertura com zero para cada item
            for (ItemRequest item : pedido.getItens()) {
                coverage.get(orderId).put(item.getItemId(), 0);
            }
        }

        // Inicializa mapeamento de corredores para pedidos
        for (Corredor corredor : instance.getCorredores()) {
            int aisleId = corredor.getId();
            aisleToOrders.put(aisleId, new HashSet<>());

            // Identifica pedidos cobertos por este corredor
            for (Pedido pedido : instance.getPedidos()) {
                int orderId = pedido.getId();
                boolean cobreAlgum = false;

                for (ItemStock stock : corredor.getEstoque()) {
                    int itemId = stock.getItemId();
                    // Verifica se o pedido precisa deste item
                    if (pedido.getItens().stream().anyMatch(req -> req.getItemId() == itemId)) {
                        cobreAlgum = true;
                        orderToAisles.get(orderId).add(aisleId);
                    }
                }

                if (cobreAlgum) {
                    aisleToOrders.get(aisleId).add(orderId);
                }
            }
        }

        // Atualiza a cobertura inicial baseada nos corredores selecionados
        updateCoverage();
    }

    /**
     * Atualiza os contadores de cobertura com base nos corredores atualmente selecionados.
     */
    public void updateCoverage() {
        // Reinicia contadores de cobertura
        for (Map.Entry<Integer, Map<Integer, Integer>> orderEntry : coverage.entrySet()) {
            for (Map.Entry<Integer, Integer> itemEntry : orderEntry.getValue().entrySet()) {
                itemEntry.setValue(0);
            }
        }

        // Atualiza a cobertura para cada corredor selecionado
        for (Integer aisleId : aisles) {
            if (!aisleToOrders.containsKey(aisleId)) continue;

            Corredor corredor = instance.getCorredores().stream()
                .filter(c -> c.getId() == aisleId)
                .findFirst().orElse(null);
            if (corredor == null) continue;

            // Para cada pedido coberto por este corredor
            for (Integer orderId : aisleToOrders.get(aisleId)) {
                if (!orders.contains(orderId)) continue;

                Pedido pedido = instance.getPedidos().stream()
                    .filter(p -> p.getId() == orderId)
                    .findFirst().orElse(null);
                if (pedido == null) continue;

                // Incrementa contadores para itens em comum
                for (ItemStock stock : corredor.getEstoque()) {
                    int itemId = stock.getItemId();
                    // Se o pedido precisa deste item
                    if (coverage.get(orderId).containsKey(itemId)) {
                        coverage.get(orderId).put(itemId, coverage.get(orderId).get(itemId) + 1);
                    }
                }
            }
        }
    }

    /**
     * Cria uma cópia profunda desta solução.
     */
    public ChallengeSolution copy() {
        ChallengeSolution newSolution = new ChallengeSolution(this.instance, this.orders, this.aisles);
        newSolution.currentCost = this.currentCost;

        // Copia estruturas de dados auxiliares
        for (Map.Entry<Integer, Set<Integer>> entry : this.orderToAisles.entrySet()) {
            newSolution.orderToAisles.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        for (Map.Entry<Integer, Set<Integer>> entry : this.aisleToOrders.entrySet()) {
            newSolution.aisleToOrders.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        for (Map.Entry<Integer, Map<Integer, Integer>> orderEntry : this.coverage.entrySet()) {
            Map<Integer, Integer> itemMap = new HashMap<>();
            for (Map.Entry<Integer, Integer> itemEntry : orderEntry.getValue().entrySet()) {
                itemMap.put(itemEntry.getKey(), itemEntry.getValue());
            }
            newSolution.coverage.put(orderEntry.getKey(), itemMap);
        }

        return newSolution;
    }

    /**
     * Copia o estado de outra solução para esta.
     */
    public void copyFrom(ChallengeSolution other) {
        // Garante que estamos copiando de uma solução da mesma instância
        if (this.instance != other.instance) {
            throw new IllegalArgumentException("Cannot copy from a solution of a different instance.");
        }

        this.orders = new HashSet<>(other.orders);
        this.aisles = new HashSet<>(other.aisles);
        this.currentCost = other.currentCost;

        // Limpa e copia estruturas de dados auxiliares
        this.orderToAisles.clear();
        this.aisleToOrders.clear();
        this.coverage.clear();

        for (Map.Entry<Integer, Set<Integer>> entry : other.orderToAisles.entrySet()) {
            this.orderToAisles.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        for (Map.Entry<Integer, Set<Integer>> entry : other.aisleToOrders.entrySet()) {
            this.aisleToOrders.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        for (Map.Entry<Integer, Map<Integer, Integer>> orderEntry : other.coverage.entrySet()) {
            Map<Integer, Integer> itemMap = new HashMap<>();
            for (Map.Entry<Integer, Integer> itemEntry : orderEntry.getValue().entrySet()) {
                itemMap.put(itemEntry.getKey(), itemEntry.getValue());
            }
            this.coverage.put(orderEntry.getKey(), itemMap);
        }
    }

    // --- Métodos delegados para IncrementalEvaluator ---

    public void evaluateCost() {
        this.currentCost = evaluator.evaluateCost();
    }

    public double cost() {
        // Se o custo nunca foi calculado, calcula agora.
        if (this.currentCost == Double.POSITIVE_INFINITY) {
            evaluateCost();
        }
        return this.currentCost;
    }

    /**
     * Calcula incrementalmente o impacto de adicionar um pedido na solução.
     * @param orderId ID do pedido a ser adicionado
     * @return O delta de custo (positivo se aumentar, negativo se diminuir)
     */
    public double calculateAddOrderDelta(int orderId) {
        return evaluator.calculateAddOrderDelta(orderId);
    }

    /**
     * Calcula incrementalmente o impacto de remover um pedido da solução.
     * @param orderId ID do pedido a ser removido
     * @return O delta de custo (positivo se aumentar, negativo se diminuir)
     */
    public double calculateRemoveOrderDelta(int orderId) {
        return evaluator.calculateRemoveOrderDelta(orderId);
    }

    /**
     * Calcula incrementalmente o impacto de adicionar um corredor na solução.
     * @param aisleId ID do corredor a ser adicionado
     * @return O delta de custo (positivo se aumentar, negativo se diminuir)
     */
    public double calculateAddAisleDelta(int aisleId) {
        return evaluator.calculateAddAisleDelta(aisleId);
    }

    /**
     * Calcula incrementalmente o impacto de remover um corredor da solução.
     * @param aisleId ID do corredor a ser removido
     * @return O delta de custo (positivo se aumentar, negativo se diminuir)
     */
    public double calculateRemoveAisleDelta(int aisleId) {
        return evaluator.calculateRemoveAisleDelta(aisleId);
    }

    /**
     * Calcula incrementalmente o custo de trocar dois pedidos.
     * @param orderId1 ID do primeiro pedido
     * @param orderId2 ID do segundo pedido
     * @return O delta de custo da troca
     */
    public double calculateSwapOrdersDelta(int orderId1, int orderId2) {
        return evaluator.calculateSwapOrdersDelta(orderId1, orderId2);
    }

    /**
     * Calcula incrementalmente o impacto de trocar um corredor por outro.
     * @param aisleToRemove ID do corredor a ser removido
     * @param aisleToAdd ID do corredor a ser adicionado
     * @return O delta de custo da troca
     */
    public double calculateSwapAisleDelta(int aisleToRemove, int aisleToAdd) {
        return evaluator.calculateSwapAisleDelta(aisleToRemove, aisleToAdd);
    }

    // --- Métodos delegados para SolutionMover ---

    public boolean perturb(int dimensionIndex, double delta) {
        return mover.perturb(dimensionIndex, delta);
    }

    public void applyAddOrder(int orderId) {
        mover.applyAddOrder(orderId);
    }

    public void applyRemoveOrder(int orderId) {
        mover.applyRemoveOrder(orderId);
    }

    public void applyAddAisle(int aisleId) {
        mover.applyAddAisle(aisleId);
    }

    public void applyRemoveAisle(int aisleId) {
        mover.applyRemoveAisle(aisleId);
    }

    public boolean multipleAisleSwap(int count) {
        return mover.multipleAisleSwap(count);
    }

    public boolean apply2OptMove(double[][] distance) {
        return mover.apply2OptMove(distance);
    }

    public boolean intensePerturbation(double intensityFactor) {
        return mover.intensePerturbation(intensityFactor);
    }

    // --- Métodos delegados para SolutionValidator ---

    public boolean isViable() {
        return validator.isViable();
    }

    public boolean repair() {
        return validator.repair();
    }

    // --- Getters para os dados da solução ---

    public Set<Integer> getOrders() {
        return new HashSet<>(orders);
    }

    public Set<Integer> getAisles() {
        return new HashSet<>(aisles);
    }

    public ChallengeInstance getInstance() {
        return instance;
    }

    public Map<Integer, Map<Integer, Integer>> getCoverage() {
        return coverage;
    }

    public Map<Integer, Set<Integer>> getOrderToAisles() {
        return orderToAisles;
    }

    public Map<Integer, Set<Integer>> getAisleToOrders() {
        return aisleToOrders;
    }

    public void setCurrentCost(double cost) {
        this.currentCost = cost;
    }

    // Permite aos componentes modificar diretamente os conjuntos
    public void setOrders(Set<Integer> orders) {
        this.orders = orders;
    }

    public void setAisles(Set<Integer> aisles) {
        this.aisles = aisles;
    }

    // --- Métodos padrão (equals, hashCode, toString) ---

    @Override
    public String toString() {
        return "ChallengeSolution{" +
               "instance=" + (instance != null ? instance.getFilename() : "null") +
               ", orders=" + orders + ", aisles=" + aisles +
               ", currentCost=" + (currentCost == Double.POSITIVE_INFINITY ? "INF" : String.format("%.2f", currentCost)) +
               '}';
    }

    // É importante implementar equals e hashCode se for usar Solutions em Sets ou Maps.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChallengeSolution that = (ChallengeSolution) o;
        // Compara pela instância e pelos conjuntos de pedidos e corredores
        return java.util.Objects.equals(instance, that.instance) &&
               java.util.Objects.equals(orders, that.orders) &&
               java.util.Objects.equals(aisles, that.aisles);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(instance, orders, aisles);
    }
}
