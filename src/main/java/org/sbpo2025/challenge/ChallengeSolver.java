package org.sbpo2025.challenge;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.time.StopWatch;
import org.sbpo2025.challenge.SimulatedAnnealing.ASASolver;
import org.sbpo2025.challenge.SimulatedAnnealing.AdaptiveOperatorSelector;
import org.sbpo2025.challenge.SimulatedAnnealing.IntensificationManager;
import org.sbpo2025.challenge.SimulatedAnnealing.IntensificationStrategy;
import org.sbpo2025.challenge.SimulatedAnnealing.OperatorSelector;
import org.sbpo2025.challenge.SimulatedAnnealing.SelectionStrategy;
import org.sbpo2025.challenge.SimulatedAnnealing.Ucb1Strategy;
import org.sbpo2025.challenge.SimulatedAnnealing.EpsilonGreedyStrategy;
import org.sbpo2025.challenge.SimulatedAnnealing.RouletteWheelStrategy;
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
        ChallengeSolution initialSolution = createInitialSolution();

        final IntensificationManager intensifier = new IntensificationManager(5, 10, 100);

        if (!initialSolution.isViable()) {
            initialSolution.repair();
        }

        final ChallengeSolution solutionForEval = initialSolution;
        registerFullEvaluation(() -> solutionForEval.evaluateCost());

        // Utiliza o novo sistema de seleção adaptativa de operadores
        AdaptiveOperatorSelector adaptiveOperators = new AdaptiveOperatorSelector();

        // Configura o seletor adaptativo
        adaptiveOperators.setUpdateFrequency(100);
        adaptiveOperators.setSelectionStrategy(new Ucb1Strategy<>(Math.sqrt(2.0))); // Substituído por instância da estratégia
        adaptiveOperators.setEpsilonExplorationFactor(0.1); // Configura o fator para Epsilon-Greedy (caso a estratégia mude)

        // Implementação da interface SolutionEvaluator
        ASASolver.SolutionEvaluator<ChallengeSolution> evaluator = new ASASolver.SolutionEvaluator<ChallengeSolution>() {
            @Override
            public double getCost(ChallengeSolution solution) {
                return -solution.cost(); // Negativo porque estamos maximizando
            }

            @Override
            public boolean isViable(ChallengeSolution solution) {
                return solution.isViable();
            }

            @Override
            public ChallengeSolution copy(ChallengeSolution solution) {
                return solution.copy();
            }

            @Override
            public void copyFrom(ChallengeSolution target, ChallengeSolution source) {
                target.copyFrom(source);
            }

            @Override
            public boolean repair(ChallengeSolution solution) {
                solution.repair();
                return solution.isViable();
            }
        };

        // Adaptando o IntensificationManager para a interface IntensificationStrategy
        IntensificationStrategy<ChallengeSolution> adaptedIntensifier = intensifier;

        // Criação do ASASolver com o nosso sistema adaptativo de operadores
        ASASolver<ChallengeSolution> asaSolver = new ASASolver<>(
                adaptiveOperators, // Usando o sistema adaptativo em vez do neighborhood hardcoded
                evaluator,
                adaptedIntensifier,
                () -> getRemainingTime(stopWatch),
                MAX_RUNTIME,
                1000 // maxNoImprovementIterations
        );

        // Configurar parâmetros adicionais com frequência maior para intensificação
        asaSolver.setParameters(
                150, // intensificationFrequency (reduzido para intensificar mais frequentemente)
                400, // pathRelinkingFrequency
                30,  // eliteUpdateFrequency (reduzido para manter arquivo elite mais atualizado)
                0.15 // temperatureScaleFactor (ligeiramente aumentado para exploração)
        );

        // Executar o algoritmo ASA
        ChallengeSolution bestSolution = asaSolver.solve(initialSolution);

        // Exibir estatísticas finais
        System.out.println("Estatísticas finais:");
        System.out.printf("- Avaliações completas: %d (tempo médio: %.2fms)%n",
            fullEvalCount, fullEvalCount > 0 ? fullEvalTime/1000000.0/fullEvalCount : 0);
        System.out.printf("- Avaliações incrementais: %d (tempo médio: %.2fms)%n",
            incrementalEvalCount, incrementalEvalCount > 0 ? incrementalEvalTime/1000000.0/incrementalEvalCount : 0);

        // Exibe estatísticas sobre o uso dos operadores
        System.out.println("\nEstatísticas de operadores:");
        adaptiveOperators.printStatistics();

        return bestSolution;
    }

    private boolean tryAddOrder(ChallengeSolution solution, Random random) {
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

        int orderToAdd = availableOrders.get(random.nextInt(availableOrders.size()));

        long t0 = System.nanoTime();
        double delta = solution.calculateAddOrderDelta(orderToAdd);
        registerIncrementalEvaluation(System.nanoTime() - t0);

        if (delta < 0 || (delta > 0 && random.nextDouble() < Math.exp(-delta / (solution.cost() * 0.1)))) {
            solution.applyAddOrder(orderToAdd);

            if (!solution.isViable()) {
                solution.repair();
                return true;
            }
            return true;
        }

        return false;
    }

    private boolean tryRemoveOrder(ChallengeSolution solution, Random random) {
        Set<Integer> currentOrders = solution.getOrders();
        if (currentOrders.isEmpty()) {
            return false;
        }

        List<Integer> ordersList = new ArrayList<>(currentOrders);
        int orderToRemove = ordersList.get(random.nextInt(ordersList.size()));

        long t0 = System.nanoTime();
        double delta = solution.calculateRemoveOrderDelta(orderToRemove);
        registerIncrementalEvaluation(System.nanoTime() - t0);

        if (delta < 0 || (delta > 0 && random.nextDouble() < Math.exp(-delta / (solution.cost() * 0.1)))) {
            solution.applyRemoveOrder(orderToRemove);
            return true;
        }

        return false;
    }

    private boolean tryAddAisle(ChallengeSolution solution, Random random) {
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

        int aisleToAdd = availableAisles.get(random.nextInt(availableAisles.size()));

        long t0 = System.nanoTime();
        double delta = solution.calculateAddAisleDelta(aisleToAdd);
        registerIncrementalEvaluation(System.nanoTime() - t0);

        if (delta < 0 || (delta > 0 && random.nextDouble() < Math.exp(-delta / (solution.cost() * 0.1)))) {
            solution.applyAddAisle(aisleToAdd);
            return true;
        }

        return false;
    }

    private boolean tryRemoveAisle(ChallengeSolution solution, Random random) {
        Set<Integer> currentAisles = solution.getAisles();
        if (currentAisles.isEmpty()) {
            return false;
        }

        List<Integer> aislesList = new ArrayList<>(currentAisles);
        int aisleToRemove = aislesList.get(random.nextInt(aislesList.size()));

        long t0 = System.nanoTime();
        double delta = solution.calculateRemoveAisleDelta(aisleToRemove);
        registerIncrementalEvaluation(System.nanoTime() - t0);

        if (delta < 0 || (delta > 0 && random.nextDouble() < Math.exp(-delta / (solution.cost() * 0.1)))) {
            solution.applyRemoveAisle(aisleToRemove);

            if (!solution.isViable()) {
                solution.repair();
                return true;
            }
            return true;
        }

        return false;
    }

    private boolean trySwapAisle(ChallengeSolution solution, Random random) {
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

        List<Integer> aislesList = new ArrayList<>(currentAisles);
        int aisleToRemove = aislesList.get(random.nextInt(aislesList.size()));
        int aisleToAdd = availableAisles.get(random.nextInt(availableAisles.size()));

        long t0 = System.nanoTime();
        double totalDelta = solution.calculateSwapAisleDelta(aisleToRemove, aisleToAdd);
        registerIncrementalEvaluation(System.nanoTime() - t0);

        if (totalDelta < 0 || (totalDelta > 0 && random.nextDouble() < Math.exp(-totalDelta / (solution.cost() * 0.1)))) {
            solution.applyRemoveAisle(aisleToRemove);
            solution.applyAddAisle(aisleToAdd);

            if (!solution.isViable()) {
                solution.repair();
                return true;
            }
            return true;
        }

        return false;
    }

    private ChallengeSolution createInitialSolution() {
        Random random = new Random();
        Set<Integer> selectedOrders = new HashSet<>();
        Set<Integer> selectedAisles = new HashSet<>();

        int orderCount = orders.size();
        int minOrdersToSelect = (int)(orderCount * 0.2);
        int maxOrdersOffset = (int)(orderCount * 0.3);
        int ordersToSelect = minOrdersToSelect + (maxOrdersOffset > 0 ? random.nextInt(maxOrdersOffset) : 0);

        for (int i = 0; i < ordersToSelect && i < orderCount; i++) {
            selectedOrders.add(random.nextInt(orderCount));
        }

        for (Integer orderId : selectedOrders) {
            if (aislesByOrder.containsKey(orderId)) {
                Set<Integer> coveringAisles = aislesByOrder.get(orderId);
                if (!coveringAisles.isEmpty()) {
                    List<Integer> aislesList = new ArrayList<>(coveringAisles);
                    selectedAisles.add(aislesList.get(random.nextInt(aislesList.size())));
                }
            }
        }

        int aisleCount = aisles.size();
        int minAislesToAdd = 1;
        int maxAislesOffset = (int)(aisleCount * 0.1);
        int moreAisles = minAislesToAdd + (maxAislesOffset > 0 ? random.nextInt(maxAislesOffset) : 0);

        for (int i = 0; i < moreAisles && i < aisleCount; i++) {
            selectedAisles.add(random.nextInt(aisleCount));
        }

        ChallengeInstance instance = new ChallengeInstance("", orders, aisles, nItems, waveSizeLB, waveSizeUB);
        return new ChallengeSolution.Builder(instance)
            .orders(selectedOrders)
            .aisles(selectedAisles)
            .build();
    }

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

        for (int order : selectedOrders) {
            for (Map.Entry<Integer, Integer> entry : orders.get(order).entrySet()) {
                totalUnitsPicked[entry.getKey()] += entry.getValue();
            }
        }

        for (int aisle : visitedAisles) {
            for (Map.Entry<Integer, Integer> entry : aisles.get(aisle).entrySet()) {
                totalUnitsAvailable[entry.getKey()] += entry.getValue();
            }
        }

        int totalUnits = Arrays.stream(totalUnitsPicked).sum();
        if (totalUnits < waveSizeLB || totalUnits > waveSizeUB) {
            return false;
        }

        for (int i = 0; i < nItems; i++) {
            if (totalUnitsPicked[i] > totalUnitsAvailable[i]) {
                return false;
            }
        }

        return true;
    }

    protected double computeObjectiveFunction(ChallengeSolution challengeSolution) {
        Set<Integer> currentSelectedOrders = challengeSolution.getOrders();
        Set<Integer> currentVisitedAisles = challengeSolution.getAisles();
        if (currentSelectedOrders == null || currentVisitedAisles == null || currentSelectedOrders.isEmpty() || currentVisitedAisles.isEmpty()) {
            return 0.0;
        }
        int currentTotalUnitsPicked = 0;

        for (int order : currentSelectedOrders) {
            currentTotalUnitsPicked += orders.get(order).values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        int currentNumVisitedAisles = currentVisitedAisles.size();

        return currentNumVisitedAisles > 0 ? (double) currentTotalUnitsPicked / currentNumVisitedAisles : 0.0;
    }

    protected void registerIncrementalEvaluation(long timeNanos) {
        incrementalEvalCount++;
        incrementalEvalTime += timeNanos;
    }

    protected void registerFullEvaluation(Runnable evaluation) {
        long start = System.nanoTime();
        evaluation.run();
        long end = System.nanoTime();
        fullEvalCount++;
        fullEvalTime += (end - start);
    }
}
