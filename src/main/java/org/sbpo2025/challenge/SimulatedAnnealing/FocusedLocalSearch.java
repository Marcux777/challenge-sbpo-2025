package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.*;
import org.sbpo2025.challenge.solution.ChallengeSolution;

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

    /**
     * Aplica busca local focada a uma solução.
     *
     * @param solution A solução a ser otimizada
     * @param mode Modo de busca: BEST_IMPROVEMENT ou FIRST_IMPROVEMENT
     * @return A solução otimizada localmente
     */
    public static ChallengeSolution apply(ChallengeSolution solution, Mode mode) {
        if (mode == Mode.BEST_IMPROVEMENT) {
            return bestImprovement(solution);
        } else {
            return firstImprovement(solution);
        }
    }

    /**
     * Implementa a estratégia Best-Improvement, avaliando toda a vizinhança
     * e selecionando o melhor vizinho a cada iteração.
     *
     * @param solution A solução a ser otimizada
     * @return A solução otimizada
     */
    private static ChallengeSolution bestImprovement(ChallengeSolution solution) {
        ChallengeSolution currentSolution = solution.copy();
        boolean improved;

        do {
            improved = false;
            ChallengeSolution bestNeighbor = null;
            double bestCost = currentSolution.cost();

            // 1. Explorar vizinhança de adição/remoção de pedidos
            List<ChallengeSolution> orderNeighbors = generateOrderNeighborhood(currentSolution);
            for (ChallengeSolution neighbor : orderNeighbors) {
                double neighborCost = neighbor.cost();
                if (neighborCost < bestCost && neighbor.isViable()) {
                    bestCost = neighborCost;
                    bestNeighbor = neighbor;
                    improved = true;
                }
            }

            // 2. Explorar vizinhança de adição/remoção/swap de corredores
            List<ChallengeSolution> aisleNeighbors = generateAisleNeighborhood(currentSolution);
            for (ChallengeSolution neighbor : aisleNeighbors) {
                double neighborCost = neighbor.cost();
                if (neighborCost < bestCost && neighbor.isViable()) {
                    bestCost = neighborCost;
                    bestNeighbor = neighbor;
                    improved = true;
                }
            }

            // Atualiza a solução com o melhor vizinho encontrado
            if (improved && bestNeighbor != null) {
                currentSolution = bestNeighbor;
            }

        } while (improved);

        return currentSolution;
    }

    /**
     * Implementa a estratégia First-Improvement, aceitando o primeiro
     * vizinho que melhore a solução atual.
     *
     * @param solution A solução a ser otimizada
     * @return A solução otimizada
     */
    private static ChallengeSolution firstImprovement(ChallengeSolution solution) {
        ChallengeSolution currentSolution = solution.copy();
        boolean improved;

        do {
            improved = false;
            double currentCost = currentSolution.cost();

            // Mistura aleatoriamente a ordem dos vizinhos para evitar viés
            List<ChallengeSolution> allNeighbors = new ArrayList<>();
            allNeighbors.addAll(generateOrderNeighborhood(currentSolution));
            allNeighbors.addAll(generateAisleNeighborhood(currentSolution));
            Collections.shuffle(allNeighbors);

            // Examina vizinhos até encontrar uma melhoria
            for (ChallengeSolution neighbor : allNeighbors) {
                double neighborCost = neighbor.cost();
                if (neighborCost < currentCost && neighbor.isViable()) {
                    currentSolution = neighbor;
                    improved = true;
                    break;
                }
            }

        } while (improved);

        return currentSolution;
    }

    /**
     * Gera vizinhança baseada em operações de adição/remoção de pedidos.
     *
     * @param solution A solução atual
     * @return Lista de vizinhos com modificações em pedidos
     */
    private static List<ChallengeSolution> generateOrderNeighborhood(ChallengeSolution solution) {
        List<ChallengeSolution> neighbors = new ArrayList<>();
        Set<Integer> currentOrders = solution.getOrders();

        // Vizinhos por adição de pedidos
        List<Integer> availableOrders = new ArrayList<>();
        int maxOrderId = solution.getInstance().getPedidos().size() - 1;

        for (int orderId = 0; orderId <= maxOrderId; orderId++) {
            if (!currentOrders.contains(orderId)) {
                availableOrders.add(orderId);
            }
        }

        for (Integer orderId : availableOrders) {
            ChallengeSolution neighbor = solution.copy();
            neighbor.applyAddOrder(orderId);

            // Repara se tornou solução inviável
            if (!neighbor.isViable()) {
                neighbor.repair();
            }

            neighbors.add(neighbor);
        }

        // Vizinhos por remoção de pedidos
        for (Integer orderId : currentOrders) {
            ChallengeSolution neighbor = solution.copy();
            neighbor.applyRemoveOrder(orderId);
            neighbors.add(neighbor);
        }

        return neighbors;
    }

    /**
     * Gera vizinhança baseada em operações de adição/remoção/troca de corredores.
     *
     * @param solution A solução atual
     * @return Lista de vizinhos com modificações em corredores
     */
    private static List<ChallengeSolution> generateAisleNeighborhood(ChallengeSolution solution) {
        List<ChallengeSolution> neighbors = new ArrayList<>();
        Set<Integer> currentAisles = solution.getAisles();

        // Vizinhos por adição de corredores
        List<Integer> availableAisles = new ArrayList<>();
        int maxAisleId = solution.getInstance().getCorredores().size() - 1;

        for (int aisleId = 0; aisleId <= maxAisleId; aisleId++) {
            if (!currentAisles.contains(aisleId)) {
                availableAisles.add(aisleId);
            }
        }

        for (Integer aisleId : availableAisles) {
            ChallengeSolution neighbor = solution.copy();
            neighbor.applyAddAisle(aisleId);
            neighbors.add(neighbor);
        }

        // Vizinhos por remoção de corredores
        for (Integer aisleId : currentAisles) {
            ChallengeSolution neighbor = solution.copy();
            neighbor.applyRemoveAisle(aisleId);

            // Repara se tornou solução inviável
            if (!neighbor.isViable()) {
                neighbor.repair();
            }

            neighbors.add(neighbor);
        }

        // Vizinhos por troca de corredores (swap)
        for (Integer aisleToRemove : currentAisles) {
            for (Integer aisleToAdd : availableAisles) {
                ChallengeSolution neighbor = solution.copy();

                neighbor.applyRemoveAisle(aisleToRemove);
                neighbor.applyAddAisle(aisleToAdd);

                // Repara se tornou solução inviável
                if (!neighbor.isViable()) {
                    neighbor.repair();
                }

                neighbors.add(neighbor);
            }
        }

        return neighbors;
    }

    /**
     * Aplica Path Relinking entre duas soluções de alta qualidade.
     * Explora soluções intermediárias no "caminho" entre a solução de origem e a guia.
     *
     * @param origin Solução de origem
     * @param guide Solução guia
     * @return A melhor solução encontrada no caminho entre origem e guia
     */
    public static ChallengeSolution applyPathRelinking(ChallengeSolution origin, ChallengeSolution guide) {
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
                ChallengeSolution improved = firstImprovement(best);
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
