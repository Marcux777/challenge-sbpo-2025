package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.*;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Implementação da técnica de intensificação "Memetic Hybrid".
 *
 * Mantém um arquivo de soluções elite e periodicamente aplica busca tabu intensiva
 * para refiná-las antes de reintroduzi-las ao processo de busca principal.
 */
public class MemeticHybrid {

    // Parâmetros da estratégia memética
    private static final int DEFAULT_ELITE_SIZE = 5;
    private static final int DEFAULT_TABU_TENURE = 10;
    private static final int DEFAULT_MAX_ITERATIONS = 100;

    // Controle de diversidade
    private final double DIVERSITY_WEIGHT = 0.3; // Peso para diversidade vs qualidade
    private final double MIN_DISTANCE = 0.2;     // Distância mínima entre soluções elite (em similaridade normalizada)

    // Arquivo de soluções elite
    private final List<ChallengeSolution> eliteArchive;
    private final int eliteSize;

    // Parâmetros para busca tabu
    private final int tabuTenure;
    private final int maxIterations;

    /**
     * Construtor com parâmetros padrão.
     */
    public MemeticHybrid() {
        this(DEFAULT_ELITE_SIZE, DEFAULT_TABU_TENURE, DEFAULT_MAX_ITERATIONS);
    }

    /**
     * Construtor com parâmetros configuráveis.
     *
     * @param eliteSize Tamanho do arquivo de soluções elite
     * @param tabuTenure Tamanho da lista tabu (quantos movimentos recentes são proibidos)
     * @param maxIterations Número máximo de iterações para a busca tabu
     */
    public MemeticHybrid(int eliteSize, int tabuTenure, int maxIterations) {
        this.eliteSize = eliteSize;
        this.tabuTenure = tabuTenure;
        this.maxIterations = maxIterations;
        this.eliteArchive = new ArrayList<>(eliteSize);
    }

    /**
     * Adiciona uma solução ao arquivo elite, mantendo apenas as melhores.
     * Inclui critérios de diversidade para manter soluções de diferentes regiões do espaço.
     *
     * @param solution A solução candidata
     */
    public void updateElite(ChallengeSolution solution) {
        // Verifica se a solução é viável antes de considerá-la
        if (!solution.isViable()) {
            return;
        }

        // Verifica se a solução já existe no arquivo
        boolean exists = false;
        for (ChallengeSolution elite : eliteArchive) {
            if (elite.equals(solution)) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            // Se o arquivo não está cheio, adiciona diretamente
            if (eliteArchive.size() < eliteSize) {
                eliteArchive.add(solution.copy());
            } else {
                // Calcula um score que combina qualidade e diversidade
                // para decidir qual solução substituir

                // 1. Calcula diversidade em relação ao arquivo existente
                double minDistance = Double.MAX_VALUE;
                for (ChallengeSolution elite : eliteArchive) {
                    double distance = calculateDistance(solution, elite);
                    if (distance < minDistance) {
                        minDistance = distance;
                    }
                }

                // 2. Identifica a pior solução no arquivo elite pelo score combinado
                int worstIndex = -1;
                double worstScore = -Double.MAX_VALUE;

                for (int i = 0; i < eliteArchive.size(); i++) {
                    ChallengeSolution elite = eliteArchive.get(i);

                    // Calcula diversidade mínima desta solução elite em relação às outras
                    double eliteMinDistance = Double.MAX_VALUE;
                    for (int j = 0; j < eliteArchive.size(); j++) {
                        if (i != j) {
                            double distance = calculateDistance(elite, eliteArchive.get(j));
                            eliteMinDistance = Math.min(eliteMinDistance, distance);
                        }
                    }

                    // Score combina qualidade e diversidade
                    // - Qualidade: menor custo é melhor (negativo para maximizar)
                    // - Diversidade: maior distância é melhor (positivo para maximizar)
                    double qualityScore = -1.0 / elite.cost(); // Invertido para maximização
                    double diversityScore = eliteMinDistance;
                    double combinedScore = (1 - DIVERSITY_WEIGHT) * qualityScore + DIVERSITY_WEIGHT * diversityScore;

                    // Encontra a pior solução pelo score combinado
                    if (combinedScore > worstScore) {
                        worstScore = combinedScore;
                        worstIndex = i;
                    }
                }

                // 3. Compara a candidata com a pior do arquivo
                ChallengeSolution worst = eliteArchive.get(worstIndex);
                double candidateQualityScore = -1.0 / solution.cost();
                double candidateDiversityScore = minDistance;
                double candidateScore = (1 - DIVERSITY_WEIGHT) * candidateQualityScore +
                                       DIVERSITY_WEIGHT * candidateDiversityScore;

                double worstQualityScore = -1.0 / worst.cost();
                double worstDiversityScore = calculateMinDistance(worst, worstIndex);
                double worstCombinedScore = (1 - DIVERSITY_WEIGHT) * worstQualityScore +
                                          DIVERSITY_WEIGHT * worstDiversityScore;

                // 4. Substitui se a candidata for melhor pelo critério combinado
                if (candidateScore > worstCombinedScore ||
                    (solution.cost() < worst.cost() && minDistance >= MIN_DISTANCE)) {
                    eliteArchive.set(worstIndex, solution.copy());
                }
            }

            // Ordena o arquivo por custo (melhor para pior)
            eliteArchive.sort(Comparator.comparingDouble(ChallengeSolution::cost));
        }
    }

    /**
     * Calcula a distância mínima entre uma solução elite e as outras no arquivo
     */
    private double calculateMinDistance(ChallengeSolution solution, int excludeIndex) {
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < eliteArchive.size(); i++) {
            if (i != excludeIndex) {
                double distance = calculateDistance(solution, eliteArchive.get(i));
                minDistance = Math.min(minDistance, distance);
            }
        }
        return minDistance;
    }

    /**
     * Calcula uma medida de distância/diferença entre duas soluções
     * @return Valor entre 0 (idênticas) e 1 (completamente diferentes)
     */
    private double calculateDistance(ChallengeSolution sol1, ChallengeSolution sol2) {
        Set<Integer> orders1 = sol1.getOrders();
        Set<Integer> orders2 = sol2.getOrders();
        Set<Integer> aisles1 = sol1.getAisles();
        Set<Integer> aisles2 = sol2.getAisles();

        // Calcula diferença simétrica (elementos em um conjunto mas não no outro)
        Set<Integer> orderDiff = new HashSet<>(orders1);
        orderDiff.addAll(orders2);

        Set<Integer> orderIntersection = new HashSet<>(orders1);
        orderIntersection.retainAll(orders2);

        orderDiff.removeAll(orderIntersection);

        Set<Integer> aisleDiff = new HashSet<>(aisles1);
        aisleDiff.addAll(aisles2);

        Set<Integer> aisleIntersection = new HashSet<>(aisles1);
        aisleIntersection.retainAll(aisles2);

        aisleDiff.removeAll(aisleIntersection);

        // Normaliza pelo tamanho total dos conjuntos
        int totalOrders = Math.max(1, orders1.size() + orders2.size());
        int totalAisles = Math.max(1, aisles1.size() + aisles2.size());

        // Distância Jaccard normalizada: |AΔB| / (|A|+|B|)
        double orderDistance = (double) orderDiff.size() / totalOrders;
        double aisleDistance = (double) aisleDiff.size() / totalAisles;

        // Média ponderada (dá mais peso aos corredores porque são mais críticos)
        return 0.4 * orderDistance + 0.6 * aisleDistance;
    }

    /**
     * Aplica a intensificação memética nas soluções elite.
     * Para cada solução elite, aplica uma busca tabu intensa para refiná-la.
     *
     * @return A melhor solução encontrada após a intensificação
     */
    public ChallengeSolution intensify() {
        if (eliteArchive.isEmpty()) {
            return null;
        }

        // Cria cópias das soluções elite para não modificar o arquivo original
        List<ChallengeSolution> refinedSolutions = new ArrayList<>(eliteArchive.size());

        // Aplica busca tabu em cada solução elite
        for (ChallengeSolution elite : eliteArchive) {
            ChallengeSolution refined = tabuIntensification(elite.copy());
            refinedSolutions.add(refined);
        }

        // Retorna a melhor solução refinada
        return Collections.min(refinedSolutions, Comparator.comparingDouble(ChallengeSolution::cost));
    }

    /**
     * Implementação da busca tabu para intensificação.
     * Explora mais profundamente o espaço de soluções em torno da solução elite.
     *
     * @param solution A solução inicial para a busca tabu
     * @return A solução refinada após busca tabu
     */
    private ChallengeSolution tabuIntensification(ChallengeSolution solution) {
        ChallengeSolution currentSolution = solution.copy();
        ChallengeSolution bestSolution = currentSolution.copy();
        double bestCost = currentSolution.cost();

        // Estruturas da busca tabu
        Queue<MovementKey> tabuList = new LinkedList<>();
        int iteration = 0;

        while (iteration < maxIterations) {
            iteration++;

            // Gera todos os movimentos possíveis na vizinhança
            List<Movement> movements = generateMovements(currentSolution);
            Collections.shuffle(movements); // Evita viés de ordem

            // Encontra o melhor movimento não-tabu (ou que satisfaz critério de aspiração)
            Movement bestMove = null;
            double bestMoveCost = Double.MAX_VALUE;

            for (Movement move : movements) {
                // Aplica o movimento para avaliação
                ChallengeSolution candidate = currentSolution.copy();
                applyMovement(candidate, move);

                // Calcula o custo e verifica viabilidade
                if (!candidate.isViable()) {
                    candidate.repair();
                }
                double candidateCost = candidate.cost();

                // Verifica se o movimento é tabu
                MovementKey moveKey = new MovementKey(move);
                boolean isTabu = tabuList.contains(moveKey);

                // Aceita se não é tabu ou satisfaz critério de aspiração (melhor que global)
                if ((!isTabu || candidateCost < bestCost) && candidateCost < bestMoveCost) {
                    bestMove = move;
                    bestMoveCost = candidateCost;
                }
            }

            // Se não encontrou movimento válido, sai do loop
            if (bestMove == null) {
                break;
            }

            // Aplica o melhor movimento
            applyMovement(currentSolution, bestMove);
            if (!currentSolution.isViable()) {
                currentSolution.repair();
            }

            // Atualiza lista tabu
            MovementKey key = new MovementKey(bestMove);
            tabuList.offer(key);
            while (tabuList.size() > tabuTenure) {
                tabuList.poll();
            }

            // Atualiza melhor solução se necessário
            double currentCost = currentSolution.cost();
            if (currentCost < bestCost) {
                bestSolution = currentSolution.copy();
                bestCost = currentCost;
            }
        }

        return bestSolution;
    }

    /**
     * Gera todos os movimentos possíveis na vizinhança da solução.
     *
     * @param solution A solução atual
     * @return Lista de movimentos possíveis
     */
    private List<Movement> generateMovements(ChallengeSolution solution) {
        List<Movement> movements = new ArrayList<>();
        Set<Integer> currentOrders = solution.getOrders();
        Set<Integer> currentAisles = solution.getAisles();

        // 1. Movimentos de adicionar pedido
        int maxOrderId = solution.getInstance().getPedidos().size() - 1;
        for (int orderId = 0; orderId <= maxOrderId; orderId++) {
            if (!currentOrders.contains(orderId)) {
                movements.add(new Movement(Movement.Type.ADD_ORDER, orderId, -1));
            }
        }

        // 2. Movimentos de remover pedido
        for (Integer orderId : currentOrders) {
            movements.add(new Movement(Movement.Type.REMOVE_ORDER, orderId, -1));
        }

        // 3. Movimentos de adicionar corredor
        int maxAisleId = solution.getInstance().getCorredores().size() - 1;
        for (int aisleId = 0; aisleId <= maxAisleId; aisleId++) {
            if (!currentAisles.contains(aisleId)) {
                movements.add(new Movement(Movement.Type.ADD_AISLE, -1, aisleId));
            }
        }

        // 4. Movimentos de remover corredor
        for (Integer aisleId : currentAisles) {
            movements.add(new Movement(Movement.Type.REMOVE_AISLE, -1, aisleId));
        }

        // 5. Movimentos de troca (swap) entre corredores
        for (Integer aisleToRemove : currentAisles) {
            for (int aisleToAdd = 0; aisleToAdd <= maxAisleId; aisleToAdd++) {
                if (!currentAisles.contains(aisleToAdd)) {
                    movements.add(new Movement(Movement.Type.SWAP_AISLE, aisleToRemove, aisleToAdd));
                }
            }
        }

        return movements;
    }

    /**
     * Aplica um movimento específico à solução.
     *
     * @param solution A solução a ser modificada
     * @param movement O movimento a ser aplicado
     */
    private void applyMovement(ChallengeSolution solution, Movement movement) {
        switch (movement.type) {
            case ADD_ORDER:
                solution.applyAddOrder(movement.id1);
                break;
            case REMOVE_ORDER:
                solution.applyRemoveOrder(movement.id1);
                break;
            case ADD_AISLE:
                solution.applyAddAisle(movement.id2);
                break;
            case REMOVE_AISLE:
                solution.applyRemoveAisle(movement.id2);
                break;
            case SWAP_AISLE:
                solution.applyRemoveAisle(movement.id1);
                solution.applyAddAisle(movement.id2);
                break;
        }
    }

    /**
     * Retorna o tamanho atual do arquivo elite.
     *
     * @return Número de soluções no arquivo elite
     */
    public int getEliteCount() {
        return eliteArchive.size();
    }

    /**
     * Verifica se o arquivo elite está vazio.
     *
     * @return true se o arquivo estiver vazio
     */
    public boolean isEmpty() {
        return eliteArchive.isEmpty();
    }

    /**
     * Obtém a melhor solução do arquivo elite.
     *
     * @return A melhor solução atual, ou null se o arquivo estiver vazio
     */
    public ChallengeSolution getBestElite() {
        if (eliteArchive.isEmpty()) {
            return null;
        }
        return eliteArchive.get(0).copy();
    }

    /**
     * Obtém uma solução específica do arquivo elite.
     *
     * @param index Índice da solução no arquivo elite (0 para a melhor)
     * @return A solução solicitada, ou null se o índice for inválido
     */
    public ChallengeSolution getEliteSolution(int index) {
        if (index < 0 || index >= eliteArchive.size()) {
            return null;
        }
        return eliteArchive.get(index).copy();
    }

    /**
     * Classe interna para representar um movimento na busca tabu.
     */
    private static class Movement {
        enum Type {
            ADD_ORDER, REMOVE_ORDER, ADD_AISLE, REMOVE_AISLE, SWAP_AISLE
        }

        final Type type;
        final int id1; // orderId para operações de pedidos, ou aisleId para remoção no caso de SWAP_AISLE
        final int id2; // aisleId para operações de corredores

        public Movement(Type type, int id1, int id2) {
            this.type = type;
            this.id1 = id1;
            this.id2 = id2;
        }
    }

    /**
     * Chave para identificação única de um movimento na lista tabu.
     */
    private static class MovementKey {
        private final Movement.Type type;
        private final int id1;
        private final int id2;

        public MovementKey(Movement move) {
            this.type = move.type;
            this.id1 = move.id1;
            this.id2 = move.id2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MovementKey key = (MovementKey) o;
            return id1 == key.id1 && id2 == key.id2 && type == key.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, id1, id2);
        }
    }
}
