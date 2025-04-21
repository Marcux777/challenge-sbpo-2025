package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.*;
import java.util.stream.*;
import org.sbpo2025.challenge.solution.ChallengeSolution;
import org.sbpo2025.challenge.neighborhood.Neighborhood;
import java.util.Spliterators;
import java.util.stream.StreamSupport;
import java.util.stream.Stream;

/**
 * Implementação da técnica de intensificação "Busca Local Focada".
 *
 * Aplica uma descida iterativa dentro de uma vizinhança pré-definida, usando:
 * - Best-improvement: avalia todo N(x) e seleciona o melhor vizinho
 * - First-improvement: interrompe a exploração ao encontrar o primeiro vizinho melhor
 */
public class FocusedLocalSearch {

    public enum Mode {
        BEST_IMPROVEMENT,
        FIRST_IMPROVEMENT
    }

    private final List<Neighborhood<ChallengeSolution>> neighborhoods;
    private final Random rand;

    public FocusedLocalSearch(List<Neighborhood<ChallengeSolution>> neighborhoods) {
        this(neighborhoods, new Random());
    }

    public FocusedLocalSearch(List<Neighborhood<ChallengeSolution>> neighborhoods, Random rand) {
        this.neighborhoods = neighborhoods;
        this.rand = rand;
    }

    /**
     * Aplica busca local focada (VND) a uma solução.
     *
     * @param solution A solução a ser otimizada
     * @param mode Modo de busca: BEST_IMPROVEMENT ou FIRST_IMPROVEMENT
     * @return A solução otimizada localmente
     */
    public ChallengeSolution apply(ChallengeSolution solution, Mode mode) {
        return apply(solution, mode, FocusedLocalSearchConfig.builder().build());
    }

    public ChallengeSolution apply(ChallengeSolution solution, Mode mode, FocusedLocalSearchConfig config) {
        ChallengeSolution current = solution.copy();
        double initialCost = current.cost();
        double bestCost = initialCost;
        ChallengeSolution bestSolution = current.copy();
        int solutionSize = current.getOrders().size() + current.getAisles().size();
        int patience0 = Math.max(1, config.patienceFactor * solutionSize);
        int patience = patience0;
        int noImprove = 0;
        int iterations = 0;
        long startTime = System.currentTimeMillis();
        boolean improved;
        do {
            improved = false;
            if (shouldStop(iterations, startTime, config, bestCost, initialCost, noImprove, patience)) break;
            if (mode == Mode.BEST_IMPROVEMENT) {
                ChallengeSolution bestNeighbor = null;
                double neighborBestCost = current.cost();
                for (Neighborhood<ChallengeSolution> nbh : neighborhoods) {
                    Optional<ChallengeSolution> best =
                        StreamSupport.stream(nbh.neighbors(current).spliterator(), true)
                            .filter(ChallengeSolution::isViable)
                            .min(Comparator.comparingDouble(ChallengeSolution::cost));
                    if (best.isPresent() && best.get().cost() < neighborBestCost) {
                        neighborBestCost = best.get().cost();
                        bestNeighbor = best.get();
                    }
                }
                if (bestNeighbor != null && bestNeighbor.cost() < current.cost() - config.improvementEpsilon) {
                    current = bestNeighbor;
                    improved = true;
                }
            } else { // FIRST_IMPROVEMENT
                final ChallengeSolution[] currentHolder = new ChallengeSolution[] { current };
                for (Neighborhood<ChallengeSolution> nbh : neighborhoods) {
                    boolean localImproved;
                    do {
                        localImproved = false;
                        // Criar uma cópia final da variável rand para usar no lambda
                        final Random myRand = this.rand;
                        Stream<ChallengeSolution> stream = StreamSupport.stream(nbh.neighbors(currentHolder[0]).spliterator(), false);
                        ChallengeSolution found = stream
                            .collect(Collectors.collectingAndThen(Collectors.toList(), lst -> {
                                // Usar a cópia final myRand dentro do lambda
                                Collections.shuffle(lst, myRand);
                                return lst.stream();
                            }))
                            .filter(neighbor -> neighbor.cost() < currentHolder[0].cost() - config.improvementEpsilon && neighbor.isViable())
                            .findFirst().orElse(null);
                        if (found != null) {
                            currentHolder[0] = found;
                            localImproved = true;
                            improved = true;
                        }
                    } while (localImproved);
                }
                current = currentHolder[0];
            }
            iterations++;
            if (current.cost() < bestCost - config.improvementEpsilon) {
                bestCost = current.cost();
                bestSolution = current.copy();
                noImprove = 0;
                double improvementRatio = (initialCost - bestCost) / (initialCost == 0 ? 1 : initialCost);
                patience = Math.max(1, (int)(patience0 * (1 - improvementRatio)));
            } else {
                noImprove++;
            }
            if (config.allowRestart && noImprove >= config.maxNoImprovement) {
                current = mutateLightly(bestSolution);
                noImprove = 0;
            }
        } while (!shouldStop(iterations, startTime, config, bestCost, initialCost, noImprove, patience));
        return bestSolution;
    }

    private boolean shouldStop(int iterations, long startTime, FocusedLocalSearchConfig config, double bestCost, double initialCost, int noImprove, int patience) {
        if (iterations >= config.maxIterations) return true;
        if (System.currentTimeMillis() - startTime > config.timeoutMillis) return true;
        if (bestCost <= config.targetCost) return true;
        if (noImprove >= patience) return true;
        return false;
    }

    private ChallengeSolution mutateLightly(ChallengeSolution solution) {
        ChallengeSolution mutated = solution.copy();
        if (!mutated.getOrders().isEmpty() && rand.nextBoolean()) {
            List<Integer> orders = new ArrayList<>(mutated.getOrders());
            int idx = rand.nextInt(orders.size());
            mutated.applyRemoveOrder(orders.get(idx));
        } else if (!mutated.getAisles().isEmpty()) {
            List<Integer> aisles = new ArrayList<>(mutated.getAisles());
            int idx = rand.nextInt(aisles.size());
            mutated.applyRemoveAisle(aisles.get(idx));
        }
        if (!mutated.isViable()) mutated.repair();
        return mutated;
    }

    /**
     * Aplica Path Relinking entre duas soluções de alta qualidade.
     * Explora soluções intermediárias no "caminho" entre a solução de origem e a guia.
     *
     * @param origin Solução de origem
     * @param guide Solução guia
     * @param neighborhoods Lista de vizinhanças
     * @return A melhor solução encontrada no caminho entre origem e guia
     */
    public static ChallengeSolution applyPathRelinking(ChallengeSolution origin, ChallengeSolution guide, List<Neighborhood<ChallengeSolution>> neighborhoods) {
        // Se as soluções são iguais, não há caminho a traçar
        if (origin.equals(guide)) {
            return origin.copy();
        }

        // Cria cópia da solução de origem para modificar durante o processo
        ChallengeSolution current = origin.copy();
        ChallengeSolution best = current.copy();
        double bestCost = current.cost();

        // Identifica diferenças em pedidos
        Set<Integer> originOrders = new HashSet<>(origin.getOrders());
        Set<Integer> guideOrders = guide.getOrders();

        // 1. Pedidos para adicionar (presentes na guia mas não na origem)
        Set<Integer> ordersToAdd = new HashSet<>(guideOrders);
        ordersToAdd.removeAll(originOrders);

        // 2. Pedidos para remover (presentes na origem mas não na guia)
        Set<Integer> ordersToRemove = new HashSet<>(originOrders);
        ordersToRemove.removeAll(guideOrders);

        // Identifica diferenças em corredores
        Set<Integer> originAisles = new HashSet<>(origin.getAisles());
        Set<Integer> guideAisles = guide.getAisles();

        // 3. Corredores para adicionar
        Set<Integer> aislesToAdd = new HashSet<>(guideAisles);
        aislesToAdd.removeAll(originAisles);

        // 4. Corredores para remover
        Set<Integer> aislesToRemove = new HashSet<>(originAisles);
        aislesToRemove.removeAll(guideAisles);

        // Cria listas de movimentos para aplicar
        List<PathMove> moves = new ArrayList<>();

        // Adiciona movimentos de pedidos
        for (Integer orderId : ordersToAdd) {
            moves.add(new PathMove(PathMove.Type.ADD_ORDER, orderId));
        }

        for (Integer orderId : ordersToRemove) {
            moves.add(new PathMove(PathMove.Type.REMOVE_ORDER, orderId));
        }

        // Adiciona movimentos de corredores
        for (Integer aisleId : aislesToAdd) {
            moves.add(new PathMove(PathMove.Type.ADD_AISLE, aisleId));
        }

        for (Integer aisleId : aislesToRemove) {
            moves.add(new PathMove(PathMove.Type.REMOVE_AISLE, aisleId));
        }

        // Embaralha os movimentos para evitar viés de ordem
        Collections.shuffle(moves);

        // Aplica movimentos em sequência, registrando a melhor solução
        for (PathMove move : moves) {
            // Aplica o movimento na solução atual
            switch (move.type) {
                case ADD_ORDER:
                    current.applyAddOrder(move.id);
                    break;
                case REMOVE_ORDER:
                    current.applyRemoveOrder(move.id);
                    break;
                case ADD_AISLE:
                    current.applyAddAisle(move.id);
                    break;
                case REMOVE_AISLE:
                    current.applyRemoveAisle(move.id);
                    break;
            }

            // Garante viabilidade
            if (!current.isViable()) {
                current.repair();
            }

            // Verifica se a solução atual é melhor que a melhor conhecida
            double currentCost = current.cost();
            if (currentCost < bestCost) {
                best = current.copy();
                bestCost = currentCost;

                // Opcional: aplica busca local na solução intermediária promissora
                ChallengeSolution improved = new FocusedLocalSearch(neighborhoods).apply(best, Mode.FIRST_IMPROVEMENT);
                if (improved.cost() < bestCost) {
                    best = improved;
                    bestCost = improved.cost();
                }
            }
        }

        return best;
    }

    /**
     * Classe interna que representa um movimento no Path Relinking
     */
    private static class PathMove {
        enum Type {
            ADD_ORDER, REMOVE_ORDER, ADD_AISLE, REMOVE_AISLE
        }

        final Type type;
        final int id; // ID do pedido ou corredor

        public PathMove(Type type, int id) {
            this.type = type;
            this.id = id;
        }
    }
}
