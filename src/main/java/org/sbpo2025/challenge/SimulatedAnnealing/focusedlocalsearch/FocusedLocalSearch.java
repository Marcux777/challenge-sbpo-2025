package org.sbpo2025.challenge.SimulatedAnnealing.focusedlocalsearch;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import org.sbpo2025.challenge.SimulatedAnnealing.neighborhood.Neighborhood;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Implementation of the "Focused Local Search" intensification technique.
 *
 * Applies an iterative descent within a predefined neighborhood, using:
 * - Best-improvement: evaluates all N(x) and selects the best neighbor
 * - First-improvement: stops exploration upon finding the first better neighbor
 */
public class FocusedLocalSearch {

    /**
     * Search modes for local search: best improvement or first improvement.
     */
    public enum Mode {
        BEST_IMPROVEMENT,
        FIRST_IMPROVEMENT
    }

    private final List<Neighborhood<ChallengeSolution>> neighborhoods;

    /**
     * Constructs a FocusedLocalSearch with the given neighborhoods.
     * @param neighborhoods List of neighborhoods to use in the search.
     */
    public FocusedLocalSearch(List<Neighborhood<ChallengeSolution>> neighborhoods) {
        this.neighborhoods = neighborhoods;
    }

    /**
     * Applies focused local search (VND) to a solution.
     *
     * @param solution The solution to be optimized
     * @param mode Search mode: BEST_IMPROVEMENT or FIRST_IMPROVEMENT
     * @return The locally optimized solution
     */
    public ChallengeSolution apply(ChallengeSolution solution, Mode mode) {
        return apply(solution, mode, FocusedLocalSearchConfig.builder().build());
    }

    /**
     * Applies focused local search (VND) to a solution with a custom configuration.
     *
     * @param solution The solution to be optimized
     * @param mode Search mode: BEST_IMPROVEMENT or FIRST_IMPROVEMENT
     * @param config Configuration for the local search
     * @return The locally optimized solution
     */
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
                    // Simple iteration instead of stream
                    Iterator<ChallengeSolution> it = nbh.neighbors(current).iterator();
                    while (it.hasNext()) {
                        ChallengeSolution neighbor = it.next();
                        if (neighbor.isViable() && neighbor.cost() < neighborBestCost) {
                            neighborBestCost = neighbor.cost();
                            bestNeighbor = neighbor;
                        }
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
                        // Collect neighbors into a list, shuffle once, and iterate sequentially
                        List<ChallengeSolution> neighbors = new ArrayList<>();
                        nbh.neighbors(currentHolder[0]).forEach(neighbors::add);
                        Collections.shuffle(neighbors, ThreadLocalRandom.current());
                        for (ChallengeSolution neighbor : neighbors) {
                            if (neighbor.cost() < currentHolder[0].cost() - config.improvementEpsilon && neighbor.isViable()) {
                                currentHolder[0] = neighbor;
                                localImproved = true;
                                improved = true;
                                break;
                            }
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
        if (!mutated.getOrders().isEmpty() && ThreadLocalRandom.current().nextBoolean()) {
            List<Integer> orders = new ArrayList<>(mutated.getOrders());
            int idx = ThreadLocalRandom.current().nextInt(orders.size());
            mutated.applyRemoveOrder(orders.get(idx));
        } else if (!mutated.getAisles().isEmpty()) {
            List<Integer> aisles = new ArrayList<>(mutated.getAisles());
            int idx = ThreadLocalRandom.current().nextInt(aisles.size());
            mutated.applyRemoveAisle(aisles.get(idx));
        }
        if (!mutated.isViable()) mutated.repair();
        return mutated;
    }
}
