package org.sbpo2025.challenge;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.time.StopWatch;
import org.sbpo2025.challenge.SimulatedAnnealing.AdaptiveSimulatedAnnealing;
import org.sbpo2025.challenge.model.*;
import org.sbpo2025.challenge.solution.ChallengeSolution;

public class ChallengeSolver {
    private final long MAX_RUNTIME = 600000; // milliseconds; 10 minutes

    protected List<Map<Integer, Integer>> orders;
    protected List<Map<Integer, Integer>> aisles;
    protected int nItems;
    protected int waveSizeLB;
    protected int waveSizeUB;

    // Contadores para métricas de avaliação
    private long fullEvalCount = 0;
    private long incrementalEvalCount = 0;
    private long fullEvalTime = 0;
    private long incrementalEvalTime = 0;

    // Estruturas auxiliares para avaliação incremental
    protected Map<Integer, Set<Integer>> aislesByOrder; // Corredores que atendem cada pedido
    protected Map<Integer, Set<Integer>> ordersByAisle; // Pedidos cobertos por cada corredor
    protected int[] totalUnitsPicked;    // Unidades selecionadas por item
    protected int[] totalUnitsAvailable; // Unidades disponíveis por item

    public ChallengeSolver(
            List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nItems = nItems;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;

        // Inicializa estruturas de dados auxiliares
        initializeAuxiliaryStructures();
    }

    /**
     * Inicializa estruturas auxiliares para avaliação incremental
     */
    private void initializeAuxiliaryStructures() {
        aislesByOrder = new HashMap<>();
        ordersByAisle = new HashMap<>();
        totalUnitsPicked = new int[nItems];
        totalUnitsAvailable = new int[nItems];

        // Inicializa mapeamento de pedidos para corredores
        for (int i = 0; i < orders.size(); i++) {
            aislesByOrder.put(i, new HashSet<>());
        }

        // Inicializa mapeamento de corredores para pedidos
        for (int j = 0; j < aisles.size(); j++) {
            ordersByAisle.put(j, new HashSet<>());

            // Para cada item deste corredor
            for (Map.Entry<Integer, Integer> itemEntry : aisles.get(j).entrySet()) {
                int itemId = itemEntry.getKey();

                // Verifica quais pedidos necessitam deste item
                for (int i = 0; i < orders.size(); i++) {
                    if (orders.get(i).containsKey(itemId)) {
                        aislesByOrder.get(i).add(j);
                        ordersByAisle.get(j).add(i);
                    }
                }
            }
        }
    }

    public ChallengeSolution solve(StopWatch stopWatch) {
        // Implementação básica com inicialização de solução e melhoria iterativa
        ChallengeSolution solution = createInitialSolution();

        // Verifica se a solução inicial é viável
        if (!solution.isViable()) {
            // Repara a solução para torná-la viável
            solution.repair();
        }

        // Avalia o custo usando avaliação completa uma vez
        registerFullEvaluation(() -> solution.evaluateCost());

        double initialCost = solution.cost();
        double bestCost = initialCost;
        ChallengeSolution bestSolution = solution.copy();

        // Loop principal de busca local com avaliação incremental
        Random random = new Random();
        int noImprovementCount = 0;
        int maxNoImprovementIterations = 1000;

        while (getRemainingTime(stopWatch) > 0 && noImprovementCount < maxNoImprovementIterations) {
            // Escolhe um tipo de movimento aleatório
            int moveType = random.nextInt(4);
            boolean improved = false;

            switch (moveType) {
                case 0: // Adicionar pedido
                    improved = tryAddOrder(solution, random);
                    break;
                case 1: // Remover pedido
                    improved = tryRemoveOrder(solution, random);
                    break;
                case 2: // Adicionar corredor
                    improved = tryAddAisle(solution, random);
                    break;
                case 3: // Remover corredor ou trocar corredor
                    if (random.nextBoolean()) {
                        improved = tryRemoveAisle(solution, random);
                    } else {
                        improved = trySwapAisle(solution, random);
                    }
                    break;
            }

            // Verifica se a solução atual é melhor que a melhor conhecida
            if (solution.cost() < bestCost && solution.isViable()) {
                bestCost = solution.cost();
                bestSolution.copyFrom(solution);
                noImprovementCount = 0;

                // Registra estatísticas para análise de desempenho
                System.out.printf("Nova melhor solução: %.2f (pedidos: %d, corredores: %d)%n",
                    bestCost, bestSolution.getOrders().size(), bestSolution.getAisles().size());
                System.out.printf("Estatísticas - Avaliações completas: %d (%.2fms), Avaliações incrementais: %d (%.2fms)%n",
                    fullEvalCount, fullEvalTime/1000000.0, incrementalEvalCount, incrementalEvalTime/1000000.0);
            } else {
                noImprovementCount++;

                // A cada 100 iterações sem melhoria, aplica uma perturbação mais intensa
                if (noImprovementCount % 100 == 0) {
                    solution.intensePerturbation(0.3);

                    // Repara se necessário e faz uma avaliação completa
                    if (!solution.isViable()) {
                        solution.repair();
                    }
                    registerFullEvaluation(() -> solution.evaluateCost());
                }
            }
        }

        // Exibe estatísticas finais
        System.out.println("Estatísticas finais:");
        System.out.printf("- Avaliações completas: %d (tempo médio: %.2fms)%n",
            fullEvalCount, fullEvalCount > 0 ? fullEvalTime/1000000.0/fullEvalCount : 0);
        System.out.printf("- Avaliações incrementais: %d (tempo médio: %.2fms)%n",
            incrementalEvalCount, incrementalEvalCount > 0 ? incrementalEvalTime/1000000.0/incrementalEvalCount : 0);

        return bestSolution;
    }

    /**
     * Tenta adicionar um pedido à solução atual.
     * Usa avaliação incremental para calcular o impacto no custo.
     */
    private boolean tryAddOrder(ChallengeSolution solution, Random random) {
        // Obtém pedidos que não estão na solução
        Set<Integer> currentOrders = solution.getOrders();
        List<Integer> availableOrders = new ArrayList<>();

        for (int i = 0; i < orders.size(); i++) {
            if (!currentOrders.contains(i)) {
                availableOrders.add(i);
            }
        }

        if (availableOrders.isEmpty()) {
            return false;
        }

        // Seleciona um pedido aleatoriamente
        int orderToAdd = availableOrders.get(random.nextInt(availableOrders.size()));

        // Calcula o delta de custo usando avaliação incremental
        long t0 = System.nanoTime();
        double delta = solution.calculateAddOrderDelta(orderToAdd);
        registerIncrementalEvaluation(System.nanoTime() - t0);

        // Aplica a mudança se o delta for aceitável
        if (delta < 0 || (delta > 0 && random.nextDouble() < Math.exp(-delta / (solution.cost() * 0.1)))) {
            solution.applyAddOrder(orderToAdd);

            // Verifica viabilidade e repara se necessário
            if (!solution.isViable()) {
                solution.repair();
                registerFullEvaluation(() -> solution.evaluateCost());
                return true;
            }
            return true;
        }

        return false;
    }

    /**
     * Tenta remover um pedido da solução atual.
     * Usa avaliação incremental para calcular o impacto no custo.
     */
    private boolean tryRemoveOrder(ChallengeSolution solution, Random random) {
        // Obtém pedidos na solução
        Set<Integer> currentOrders = solution.getOrders();
        if (currentOrders.isEmpty()) {
            return false;
        }

        // Converte para lista para seleção aleatória
        List<Integer> ordersList = new ArrayList<>(currentOrders);
        int orderToRemove = ordersList.get(random.nextInt(ordersList.size()));

        // Calcula o delta de custo usando avaliação incremental
        long t0 = System.nanoTime();
        double delta = solution.calculateRemoveOrderDelta(orderToRemove);
        registerIncrementalEvaluation(System.nanoTime() - t0);

        // Aplica a mudança se o delta for aceitável
        if (delta < 0 || (delta > 0 && random.nextDouble() < Math.exp(-delta / (solution.cost() * 0.1)))) {
            solution.applyRemoveOrder(orderToRemove);
            return true;
        }

        return false;
    }

    /**
     * Tenta adicionar um corredor à solução atual.
     * Usa avaliação incremental para calcular o impacto no custo.
     */
    private boolean tryAddAisle(ChallengeSolution solution, Random random) {
        // Obtém corredores que não estão na solução
        Set<Integer> currentAisles = solution.getAisles();
        List<Integer> availableAisles = new ArrayList<>();

        for (int i = 0; i < aisles.size(); i++) {
            if (!currentAisles.contains(i)) {
                availableAisles.add(i);
            }
        }

        if (availableAisles.isEmpty()) {
            return false;
        }

        // Seleciona um corredor aleatoriamente
        int aisleToAdd = availableAisles.get(random.nextInt(availableAisles.size()));

        // Calcula o delta de custo usando avaliação incremental
        long t0 = System.nanoTime();
        double delta = solution.calculateAddAisleDelta(aisleToAdd);
        registerIncrementalEvaluation(System.nanoTime() - t0);

        // Aplica a mudança se o delta for aceitável
        if (delta < 0 || (delta > 0 && random.nextDouble() < Math.exp(-delta / (solution.cost() * 0.1)))) {
            solution.applyAddAisle(aisleToAdd);
            return true;
        }

        return false;
    }

    /**
     * Tenta remover um corredor da solução atual.
     * Usa avaliação incremental para calcular o impacto no custo.
     */
    private boolean tryRemoveAisle(ChallengeSolution solution, Random random) {
        // Obtém corredores na solução
        Set<Integer> currentAisles = solution.getAisles();
        if (currentAisles.isEmpty()) {
            return false;
        }

        // Converte para lista para seleção aleatória
        List<Integer> aislesList = new ArrayList<>(currentAisles);
        int aisleToRemove = aislesList.get(random.nextInt(aislesList.size()));

        // Calcula o delta de custo usando avaliação incremental
        long t0 = System.nanoTime();
        double delta = solution.calculateRemoveAisleDelta(aisleToRemove);
        registerIncrementalEvaluation(System.nanoTime() - t0);

        // Aplica a mudança se o delta for aceitável
        if (delta < 0 || (delta > 0 && random.nextDouble() < Math.exp(-delta / (solution.cost() * 0.1)))) {
            solution.applyRemoveAisle(aisleToRemove);

            // Verifica viabilidade e repara se necessário
            if (!solution.isViable()) {
                solution.repair();
                registerFullEvaluation(() -> solution.evaluateCost());
                return true;
            }
            return true;
        }

        return false;
    }

    /**
     * Tenta trocar um corredor da solução por outro fora da solução.
     * Usa avaliação incremental para calcular o impacto no custo.
     */
    private boolean trySwapAisle(ChallengeSolution solution, Random random) {
        // Obtém corredores na solução e fora da solução
        Set<Integer> currentAisles = solution.getAisles();
        if (currentAisles.isEmpty()) {
            return false;
        }

        List<Integer> availableAisles = new ArrayList<>();
        for (int i = 0; i < aisles.size(); i++) {
            if (!currentAisles.contains(i)) {
                availableAisles.add(i);
            }
        }

        if (availableAisles.isEmpty()) {
            return false;
        }

        // Seleciona um corredor para remover e outro para adicionar
        List<Integer> aislesList = new ArrayList<>(currentAisles);
        int aisleToRemove = aislesList.get(random.nextInt(aislesList.size()));
        int aisleToAdd = availableAisles.get(random.nextInt(availableAisles.size()));

        // Calcula o delta de custo combinado de remover um e adicionar outro
        long t0 = System.nanoTime();
        double removeAisleDelta = solution.calculateRemoveAisleDelta(aisleToRemove);

        // Simula a remoção para calcular corretamente o delta de adição
        solution.applyRemoveAisle(aisleToRemove);
        double addAisleDelta = solution.calculateAddAisleDelta(aisleToAdd);

        // Restaura o estado anterior
        solution.applyAddAisle(aisleToRemove);

        double totalDelta = removeAisleDelta + addAisleDelta;
        registerIncrementalEvaluation(System.nanoTime() - t0);

        // Aplica a mudança se o delta for aceitável
        if (totalDelta < 0 || (totalDelta > 0 && random.nextDouble() < Math.exp(-totalDelta / (solution.cost() * 0.1)))) {
            solution.applyRemoveAisle(aisleToRemove);
            solution.applyAddAisle(aisleToAdd);

            // Verifica viabilidade e repara se necessário
            if (!solution.isViable()) {
                solution.repair();
                registerFullEvaluation(() -> solution.evaluateCost());
                return true;
            }
            return true;
        }

        return false;
    }

    /**
     * Cria uma solução inicial para o problema.
     * Pode ser aleatória ou gulosa.
     */
    private ChallengeSolution createInitialSolution() {
        // Implementação inicial simples: seleciona aleatoriamente alguns pedidos e corredores
        Random random = new Random();
        Set<Integer> selectedOrders = new HashSet<>();
        Set<Integer> selectedAisles = new HashSet<>();

        // Seleciona alguns pedidos aleatoriamente (entre 20% e 50% do total)
        int orderCount = orders.size();
        int ordersToSelect = random.nextInt((int)(orderCount * 0.3)) + (int)(orderCount * 0.2);

        for (int i = 0; i < ordersToSelect && i < orderCount; i++) {
            selectedOrders.add(random.nextInt(orderCount));
        }

        // Seleciona corredores que cobrem os pedidos selecionados
        // Para cada pedido, seleciona pelo menos um corredor que o atende
        for (Integer orderId : selectedOrders) {
            if (aislesByOrder.containsKey(orderId)) {
                // Seleciona pelo menos um corredor aleatório que atende este pedido
                Set<Integer> coveringAisles = aislesByOrder.get(orderId);
                if (!coveringAisles.isEmpty()) {
                    List<Integer> aislesList = new ArrayList<>(coveringAisles);
                    selectedAisles.add(aislesList.get(random.nextInt(aislesList.size())));
                }
            }
        }

        // Adiciona mais alguns corredores aleatoriamente para garantir diversidade
        int aisleCount = aisles.size();
        int moreAisles = random.nextInt((int)(aisleCount * 0.1)) + 1;

        for (int i = 0; i < moreAisles && i < aisleCount; i++) {
            selectedAisles.add(random.nextInt(aisleCount));
        }

        // Cria a solução inicial
        return new ChallengeSolution(
            new ChallengeInstance("", orders, aisles, nItems, waveSizeLB, waveSizeUB),
            selectedOrders,
            selectedAisles
        );
    }

    /*
     * Get the remaining time in seconds
     */
    protected long getRemainingTime(StopWatch stopWatch) {
        return Math.max(
                TimeUnit.SECONDS.convert(MAX_RUNTIME - stopWatch.getTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS),
                0);
    }

    protected boolean isSolutionFeasible(ChallengeSolution challengeSolution) {
        Set<Integer> selectedOrders = challengeSolution.getOrders();
        Set<Integer> visitedAisles = challengeSolution.getAisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return false;
        }

        int[] totalUnitsPicked = new int[nItems];
        int[] totalUnitsAvailable = new int[nItems];

        // Calculate total units picked
        for (int order : selectedOrders) {
            for (Map.Entry<Integer, Integer> entry : orders.get(order).entrySet()) {
                totalUnitsPicked[entry.getKey()] += entry.getValue();
            }
        }

        // Calculate total units available
        for (int aisle : visitedAisles) {
            for (Map.Entry<Integer, Integer> entry : aisles.get(aisle).entrySet()) {
                totalUnitsAvailable[entry.getKey()] += entry.getValue();
            }
        }

        // Check if the total units picked are within bounds
        int totalUnits = Arrays.stream(totalUnitsPicked).sum();
        if (totalUnits < waveSizeLB || totalUnits > waveSizeUB) {
            return false;
        }

        // Check if the units picked do not exceed the units available
        for (int i = 0; i < nItems; i++) {
            if (totalUnitsPicked[i] > totalUnitsAvailable[i]) {
                return false;
            }
        }

        return true;
    }

    protected double computeObjectiveFunction(ChallengeSolution challengeSolution) {
        Set<Integer> selectedOrders = challengeSolution.getOrders();
        Set<Integer> visitedAisles = challengeSolution.getAisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return 0.0;
        }
        int totalUnitsPicked = 0;

        // Calculate total units picked
        for (int order : selectedOrders) {
            totalUnitsPicked += orders.get(order).values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        // Calculate the number of visited aisles
        int numVisitedAisles = visitedAisles.size();

        // Objective function: total units picked / number of visited aisles
        return (double) totalUnitsPicked / numVisitedAisles;
    }

    /**
     * Registra o tempo de uma avaliação incremental para métricas.
     * @param timeNanos Tempo da avaliação em nanossegundos
     */
    protected void registerIncrementalEvaluation(long timeNanos) {
        incrementalEvalCount++;
        incrementalEvalTime += timeNanos;
    }

    /**
     * Registra o tempo de uma avaliação completa para métricas.
     * @param evaluation Runnable que executa a avaliação
     */
    protected void registerFullEvaluation(Runnable evaluation) {
        long start = System.nanoTime();
        evaluation.run();
        long end = System.nanoTime();
        fullEvalCount++;
        fullEvalTime += (end - start);
    }
}
