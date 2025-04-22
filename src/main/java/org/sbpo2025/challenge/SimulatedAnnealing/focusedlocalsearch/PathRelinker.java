package org.sbpo2025.challenge.SimulatedAnnealing.focusedlocalsearch;

import org.sbpo2025.challenge.solution.ChallengeSolution;
import org.sbpo2025.challenge.SimulatedAnnealing.neighborhood.Neighborhood;
import java.util.*;
import java.util.function.Function;
import java.util.function.BiFunction;

/**
 * PathRelinker implements path relinking between two solutions, applying moves to transform the origin into the guide solution.
 */
public class PathRelinker {
    private final List<Neighborhood<ChallengeSolution>> neighborhoods;
    private ChallengeSolution lastOriginForEstimate;

    /**
     * Constructs a PathRelinker with the given neighborhoods.
     * @param neighborhoods List of neighborhoods to use for local search refinement.
     */
    public PathRelinker(List<Neighborhood<ChallengeSolution>> neighborhoods) {
        this.neighborhoods = neighborhoods;
    }

    /**
     * Relinks from origin to guide, refining with a custom refiner function.
     * @param origin The starting solution.
     * @param guide The target solution.
     * @param refiner Function to refine intermediate solutions.
     * @return The best solution found along the path.
     */
    public ChallengeSolution relink(ChallengeSolution origin, ChallengeSolution guide, BiFunction<ChallengeSolution, Integer, ChallengeSolution> refiner) {
        this.lastOriginForEstimate = origin;
        if (origin.equals(guide)) {
            return origin.copy();
        }
        ChallengeSolution current = origin.copy();
        ChallengeSolution best = current.copy();
        double bestCost = current.cost();
        for (PathMove move : identifyDifferences(origin, guide)) {
            applyMove(current, move);
            if (!current.isViable()) current.repair();
            double cost = current.cost();
            if (cost < bestCost) {
                best.copyFrom(current);
                bestCost = cost;
                ChallengeSolution improved = refiner.apply(best, 0); // 0 as placeholder
                if (improved.cost() < bestCost) {
                    best.copyFrom(improved);
                    bestCost = improved.cost();
                }
            }
        }
        return best;
    }

    /**
     * Relinks from origin to guide using default refinement.
     * @param origin The starting solution.
     * @param guide The target solution.
     * @return The best solution found along the path.
     */
    public ChallengeSolution relink(ChallengeSolution origin, ChallengeSolution guide) {
        return relink(origin, guide, (sol, ignored) -> refine(sol));
    }

    /**
     * Relinks from origin to guide using a specific local search mode and config.
     * @param origin The starting solution.
     * @param guide The target solution.
     * @param mode Local search mode.
     * @param config Local search configuration.
     * @return The best solution found along the path.
     */
    public ChallengeSolution relink(ChallengeSolution origin, ChallengeSolution guide, FocusedLocalSearch.Mode mode, FocusedLocalSearchConfig config) {
        return relink(origin, guide, (sol, ignored) -> refine(sol, mode, config));
    }

    /**
     * Identifies the moves required to transform origin into guide.
     * @param origin The starting solution.
     * @param guide The target solution.
     * @return List of moves to apply.
     */
    List<PathMove> identifyDifferences(ChallengeSolution origin, ChallengeSolution guide) {
        Set<Integer> originOrders = new HashSet<>(origin.getOrders());
        Set<Integer> guideOrders = guide.getOrders();
        Set<Integer> ordersToAdd = new HashSet<>(guideOrders);
        ordersToAdd.removeAll(originOrders);
        Set<Integer> ordersToRemove = new HashSet<>(originOrders);
        ordersToRemove.removeAll(guideOrders);
        Set<Integer> originAisles = new HashSet<>(origin.getAisles());
        Set<Integer> guideAisles = guide.getAisles();
        Set<Integer> aislesToAdd = new HashSet<>(guideAisles);
        aislesToAdd.removeAll(originAisles);
        Set<Integer> aislesToRemove = new HashSet<>(originAisles);
        aislesToRemove.removeAll(guideAisles);
        List<PathMove> moves = new ArrayList<>();
        for (Integer orderId : ordersToAdd) moves.add(new PathMove(PathMove.Type.ADD_ORDER, orderId));
        for (Integer orderId : ordersToRemove) moves.add(new PathMove(PathMove.Type.REMOVE_ORDER, orderId));
        for (Integer aisleId : aislesToAdd) moves.add(new PathMove(PathMove.Type.ADD_AISLE, aisleId));
        for (Integer aisleId : aislesToRemove) moves.add(new PathMove(PathMove.Type.REMOVE_AISLE, aisleId));
        // Sort by estimated cost delta (most promising first)
        moves.sort(Comparator.comparingDouble(this::estimateDelta).reversed());
        int k = Math.max(1, moves.size() / 4); // top 25% most promising
        if (k < moves.size()) {
            Collections.shuffle(moves.subList(k, moves.size()));
        }
        return moves;
    }

    double estimateDelta(PathMove move) {
        ChallengeSolution temp = getReferenceSolutionForEstimate();
        double before = temp.cost();
        applyMove(temp, move);
        if (!temp.isViable()) temp.repair();
        double after = temp.cost();
        return before - after;
    }

    ChallengeSolution getReferenceSolutionForEstimate() {
        return lastOriginForEstimate.copy();
    }

    void applyMove(ChallengeSolution current, PathMove move) {
        switch (move.type) {
            case ADD_ORDER: current.applyAddOrder(move.id); break;
            case REMOVE_ORDER: current.applyRemoveOrder(move.id); break;
            case ADD_AISLE: current.applyAddAisle(move.id); break;
            case REMOVE_AISLE: current.applyRemoveAisle(move.id); break;
        }
    }

    ChallengeSolution refine(ChallengeSolution solution) {
        return new FocusedLocalSearch(neighborhoods).apply(solution, FocusedLocalSearch.Mode.FIRST_IMPROVEMENT);
    }

    ChallengeSolution refine(ChallengeSolution solution, FocusedLocalSearch.Mode mode, FocusedLocalSearchConfig config) {
        return new FocusedLocalSearch(neighborhoods).apply(solution, mode, config);
    }

    static class PathMove {
        enum Type { ADD_ORDER, REMOVE_ORDER, ADD_AISLE, REMOVE_AISLE }
        final Type type;
        final int id;
        PathMove(Type type, int id) { this.type = type; this.id = id; }
    }
}
