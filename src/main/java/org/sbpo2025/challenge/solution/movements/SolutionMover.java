package org.sbpo2025.challenge.solution.movements;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.sbpo2025.challenge.model.*;
import org.sbpo2025.challenge.solution.ChallengeSolution;
import org.sbpo2025.challenge.solution.incremental.IncrementalEvaluator;
import org.sbpo2025.challenge.solution.movements.SolutionMovement;

/**
 * Responsável por aplicar movimentos e perturbações na solução.
 * Implementa os diferentes tipos de movimentos que podem ser aplicados durante a
 * busca por soluções ótimas.
 */
public class SolutionMover implements SolutionMovement {

    private final ChallengeSolution solution;
    private final IncrementalEvaluator evaluator;
    private final Random random = new Random();
    private final int maxTrials = 100;  // Limite de tentativas para perturbações
    private final Map<Integer, Corredor> corridorById;
    private final List<Integer> reuseList;

    private enum Op { ADD_ORDER, REMOVE_ORDER, ADD_AISLE, REMOVE_AISLE }

    private static class OpKey {
        enum Type { ADD_AISLE, REMOVE_AISLE, ADD_ORDER, REMOVE_ORDER }
        final Type type;
        final int id;
        OpKey(Type type, int id) {
            this.type = type;
            this.id = id;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OpKey opKey = (OpKey) o;
            return id == opKey.id && type == opKey.type;
        }
        @Override
        public int hashCode() {
            return Objects.hash(type, id);
        }
    }

    // Representa o delta de um operador para um id específico
    private static class OpDelta {
        final Op op;
        final int id;
        final double delta;
        OpDelta(Op op, int id, double delta) {
            this.op = op;
            this.id = id;
            this.delta = delta;
        }
    }

    // Thread-safe para uso concorrente
    private final Map<OpKey, Double> deltaCache = new ConcurrentHashMap<>();

    // Executor fixo para paralelismo controlado
    private static final ExecutorService deltaExecutor = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors())
    );

    // ALNS: scores, weights e contadores para operadores
    private final double[] opScores = new double[Op.values().length];
    private final double[] opWeights = new double[Op.values().length];
    private final int[] opCounts = new int[Op.values().length];
    private final double reactionFactor = 0.2; // taxa de aprendizado
    private final double rewardSuccess = 5.0;
    private final double rewardAccept = 2.0;
    private final double rewardTry = 0.5;
    private final int resetScoresInterval = 100; // Reinicializa scores a cada 100 iterações
    private int alnsIteration = 0;

    {
        // Inicializa scores e pesos
        Arrays.fill(opScores, 1.0);
        Arrays.fill(opWeights, 1.0);
    }

    public SolutionMover(ChallengeSolution solution) {
        this.solution = solution;
        this.evaluator = new IncrementalEvaluator(solution);
        this.corridorById = solution.getInstance().getCorredores()
            .stream().collect(Collectors.toMap(Corredor::getId, c -> c));
        // Capacidade máxima: maior entre pedidos e corredores
        int maxSize = Math.max(
            solution.getInstance().getPedidos().size(),
            solution.getInstance().getCorredores().size()
        );
        this.reuseList = new ArrayList<>(maxSize);
    }

    /**
     * Aplica uma pequena modificação (perturbação) aleatória na solução.
     * Pode adicionar/remover um pedido ou adicionar/remover um corredor.
     * Usa paralelismo para pré-calcular deltas dos operadores.
     */
    public boolean perturb(int dimensionIndex, double delta) {
        return precomputeDeltasAndTryOpParallel();
    }

    /**
     * Pré-calcula os deltas de todos os operadores em paralelo e tenta aplicar um deles.
     */
    private boolean precomputeDeltasAndTryOpParallel() {
        List<Op> ops = Arrays.asList(Op.ADD_ORDER, Op.REMOVE_ORDER, Op.ADD_AISLE, Op.REMOVE_AISLE);
        Collections.shuffle(ops, random);
        Map<Op, CompletableFuture<OpDelta>> deltaFutures = new EnumMap<>(Op.class);
        for (Op op : ops) {
            // Pré-calcula o melhor OpDelta para cada operador
            deltaFutures.put(op, CompletableFuture.supplyAsync(() -> computeBestDeltaForOp(op), deltaExecutor));
        }
        // Aguarda todos os cálculos terminarem
        List<OpDelta> opDeltas = new ArrayList<>();
        for (Op op : ops) {
            try {
                OpDelta od = deltaFutures.get(op).get();
                if (od != null) opDeltas.add(od);
            } catch (Exception e) {
                // Ignora
            }
        }
        // Ordena pelo menor delta
        opDeltas.sort(Comparator.comparingDouble(od -> od.delta));
        // Aplica o melhor movimento possível
        for (OpDelta od : opDeltas) {
            if (tryOpWithPrecomputedDelta(od)) return true;
        }
        return false;
    }

    // Avalia todos os candidatos, armazena no cache e retorna o melhor OpDelta
    private OpDelta computeBestDeltaForOp(Op op) {
        List<Integer> candidates = new ArrayList<>();
        switch (op) {
            case ADD_ORDER:
                for (Pedido p : solution.getInstance().getPedidos()) {
                    int id = p.getId();
                    if (!solution.getOrders().contains(id)) candidates.add(id);
                }
                break;
            case REMOVE_ORDER:
                candidates.addAll(solution.getOrders());
                break;
            case ADD_AISLE:
                for (Corredor c : solution.getInstance().getCorredores()) {
                    int id = c.getId();
                    if (!solution.getAisles().contains(id)) candidates.add(id);
                }
                break;
            case REMOVE_AISLE:
                candidates.addAll(solution.getAisles());
                break;
        }
        OpDelta best = null;
        for (int id : candidates) {
            double delta = getDeltaAndCache(op, id);
            if (best == null || delta < best.delta) {
                best = new OpDelta(op, id, delta);
            }
        }
        return best;
    }

    // Calcula delta, armazena no cache e retorna
    private double getDeltaAndCache(Op op, int id) {
        OpKey.Type type;
        switch (op) {
            case ADD_ORDER: type = OpKey.Type.ADD_ORDER; break;
            case REMOVE_ORDER: type = OpKey.Type.REMOVE_ORDER; break;
            case ADD_AISLE: type = OpKey.Type.ADD_AISLE; break;
            case REMOVE_AISLE: type = OpKey.Type.REMOVE_AISLE; break;
            default: throw new IllegalArgumentException();
        }
        OpKey key = new OpKey(type, id);
        Double cached = deltaCache.get(key);
        if (cached != null) return cached;
        double delta;
        switch (type) {
            case ADD_AISLE:
                delta = evaluator.calculateAddAisleDelta(id); break;
            case REMOVE_AISLE:
                delta = evaluator.calculateRemoveAisleDelta(id); break;
            case ADD_ORDER:
                delta = evaluator.calculateAddOrderDelta(id); break;
            case REMOVE_ORDER:
                delta = evaluator.calculateRemoveOrderDelta(id); break;
            default:
                throw new IllegalArgumentException("Tipo de operação desconhecido");
        }
        deltaCache.put(key, delta);
        return delta;
    }

    // Aplica exatamente o id e delta pré-calculados
    private boolean tryOpWithPrecomputedDelta(OpDelta od) {
        switch (od.op) {
            case ADD_ORDER:
                if (!solution.getOrders().contains(od.id)) {
                    applyAddOrderWithDelta(od.id, od.delta);
                    return true;
                }
                break;
            case REMOVE_ORDER:
                if (solution.getOrders().contains(od.id)) {
                    applyRemoveOrderWithDelta(od.id, od.delta);
                    return true;
                }
                break;
            case ADD_AISLE:
                if (!solution.getAisles().contains(od.id)) {
                    applyAddAisleWithDelta(od.id, od.delta);
                    return true;
                }
                break;
            case REMOVE_AISLE:
                if (solution.getAisles().contains(od.id)) {
                    applyRemoveAisleWithDelta(od.id, od.delta);
                    return true;
                }
                break;
        }
        return false;
    }

    // Remove apenas deltas de corredores do cache
    private void invalidateAisleDeltaCache() {
        deltaCache.keySet().removeIf(key ->
            key.type == OpKey.Type.ADD_AISLE || key.type == OpKey.Type.REMOVE_AISLE
        );
    }

    // Remove apenas deltas de pedidos do cache
    private void invalidateOrderDeltaCache() {
        deltaCache.keySet().removeIf(key ->
            key.type == OpKey.Type.ADD_ORDER || key.type == OpKey.Type.REMOVE_ORDER
        );
    }

    // Versões que usam delta já calculado
    private void applyAddOrderWithDelta(int orderId, double delta) {
        Set<Integer> orders = solution.getOrders();
        if (orders.contains(orderId)) return;
        orders.add(orderId);
        invalidateOrderDeltaCache();
        solution.setCurrentCost(solution.cost() + delta);
    }
    private void applyRemoveOrderWithDelta(int orderId, double delta) {
        Set<Integer> orders = solution.getOrders();
        if (!orders.contains(orderId)) return;
        orders.remove(orderId);
        invalidateOrderDeltaCache();
        solution.setCurrentCost(solution.cost() + delta);
    }
    private void applyAddAisleWithDelta(int aisleId, double delta) {
        Set<Integer> aisles = solution.getAisles();
        if (aisles.contains(aisleId)) return;
        aisles.add(aisleId);
        invalidateAisleDeltaCache();
        solution.setCurrentCost(solution.cost() + delta);
    }
    private void applyRemoveAisleWithDelta(int aisleId, double delta) {
        Set<Integer> aisles = solution.getAisles();
        if (!aisles.contains(aisleId)) return;
        aisles.remove(aisleId);
        invalidateAisleDeltaCache();
        solution.setCurrentCost(solution.cost() + delta);
    }

    private double getDelta(OpKey.Type type, int id) {
        OpKey key = new OpKey(type, id);
        if (deltaCache.containsKey(key)) {
            return deltaCache.get(key);
        }
        double delta;
        switch (type) {
            case ADD_AISLE:
                delta = evaluator.calculateAddAisleDelta(id); break;
            case REMOVE_AISLE:
                delta = evaluator.calculateRemoveAisleDelta(id); break;
            case ADD_ORDER:
                delta = evaluator.calculateAddOrderDelta(id); break;
            case REMOVE_ORDER:
                delta = evaluator.calculateRemoveOrderDelta(id); break;
            default:
                throw new IllegalArgumentException("Tipo de operação desconhecido");
        }
        deltaCache.put(key, delta);
        return delta;
    }

    private void invalidateDeltaCache() {
        deltaCache.clear();
    }

    /**
     * Aplica incrementalmente a adição de um pedido atualizando a estrutura de dados.
     */
    public void applyAddOrder(int orderId) {
        Set<Integer> orders = solution.getOrders();
        if (orders.contains(orderId)) return;

        double delta = getDelta(OpKey.Type.ADD_ORDER, orderId);
        orders.add(orderId); // Modifica diretamente
        invalidateDeltaCache();
        // Atualize cobertura incrementalmente se necessário
        solution.setCurrentCost(solution.cost() + delta);
    }

    /**
     * Aplica incrementalmente a remoção de um pedido atualizando a estrutura de dados.
     */
    public void applyRemoveOrder(int orderId) {
        Set<Integer> orders = solution.getOrders();
        if (!orders.contains(orderId)) return;

        double delta = getDelta(OpKey.Type.REMOVE_ORDER, orderId);
        orders.remove(orderId); // Modifica diretamente
        invalidateDeltaCache();
        // Atualize cobertura incrementalmente se necessário
        solution.setCurrentCost(solution.cost() + delta);
    }

    /**
     * Aplica incrementalmente a adição de um corredor atualizando a estrutura de dados.
     */
    public void applyAddAisle(int aisleId) {
        Set<Integer> aisles = solution.getAisles();
        if (aisles.contains(aisleId)) return;

        double delta = getDelta(OpKey.Type.ADD_AISLE, aisleId);
        aisles.add(aisleId); // Modifica diretamente
        invalidateDeltaCache();
        // Atualize cobertura incrementalmente se necessário
        Map<Integer, Set<Integer>> aisleToOrders = solution.getAisleToOrders();
        if (aisleToOrders.containsKey(aisleId)) {
            Corredor corredor = corridorById.get(aisleId);

            if (corredor != null) {
                Set<Integer> orders = solution.getOrders();
                Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();

                for (Integer orderId : aisleToOrders.get(aisleId)) {
                    if (!orders.contains(orderId) || !coverage.containsKey(orderId)) continue;

                    // Incrementa cobertura para itens deste corredor
                    for (ItemStock stock : corredor.getEstoque()) {
                        int itemId = stock.getItemId();
                        if (coverage.get(orderId).containsKey(itemId)) {
                            coverage.get(orderId).put(itemId, coverage.get(orderId).get(itemId) + 1);
                        }
                    }
                }
            }
        }

        solution.setCurrentCost(solution.cost() + delta);
    }

    /**
     * Aplica incrementalmente a remoção de um corredor atualizando a estrutura de dados.
     */
    public void applyRemoveAisle(int aisleId) {
        Set<Integer> aisles = solution.getAisles();
        if (!aisles.contains(aisleId)) return;

        double delta = getDelta(OpKey.Type.REMOVE_AISLE, aisleId);
        aisles.remove(aisleId); // Modifica diretamente
        invalidateDeltaCache();
        // Atualize cobertura incrementalmente se necessário
        Map<Integer, Set<Integer>> aisleToOrders = solution.getAisleToOrders();
        if (aisleToOrders.containsKey(aisleId)) {
            Corredor corredor = corridorById.get(aisleId);

            if (corredor != null) {
                Set<Integer> orders = solution.getOrders();
                Map<Integer, Map<Integer, Integer>> coverage = solution.getCoverage();

                for (Integer orderId : aisleToOrders.get(aisleId)) {
                    if (!orders.contains(orderId) || !coverage.containsKey(orderId)) continue;

                    // Decrementa cobertura para itens deste corredor
                    for (ItemStock stock : corredor.getEstoque()) {
                        int itemId = stock.getItemId();
                        if (coverage.get(orderId).containsKey(itemId)) {
                            coverage.get(orderId).put(itemId, coverage.get(orderId).get(itemId) - 1);
                        }
                    }
                }
            }
        }

        solution.setCurrentCost(solution.cost() + delta);
    }

    /**
     * Implementação de movimento avançado: swap múltiplo de corredores.
     * Troca um conjunto de corredores da solução por outro conjunto fora da solução.
     */
    private boolean accept(double delta) {
        // Aceita apenas se o delta for negativo (melhora a solução)
        return delta < 0;
    }

    public boolean multipleAisleSwap(int count) {
        ChallengeInstance instance = solution.getInstance();
        Set<Integer> aisles = solution.getAisles();

        // Verifica se há corredores suficientes dentro e fora da solução
        if (aisles.size() < count || instance.getCorredores().size() - aisles.size() < count) {
            return false;
        }

        // Seleciona aleatoriamente 'count' corredores da solução
        reuseList.clear();
        reuseList.addAll(aisles);
        Collections.shuffle(reuseList);
        List<Integer> aislesToRemove = new ArrayList<>(reuseList.subList(0, count));

        // Seleciona aleatoriamente 'count' corredores fora da solução
        reuseList.clear();
        for (Corredor c : instance.getCorredores()) {
            int id = c.getId();
            if (!aisles.contains(id)) reuseList.add(id);
        }
        Collections.shuffle(reuseList);
        List<Integer> aislesToAdd = new ArrayList<>(reuseList.subList(0, count));

        // Calcula o delta total dos swaps
        double totalDelta = 0.0;
        for (int i = 0; i < count; i++) {
            int a1 = aislesToRemove.get(i);
            int a2 = aislesToAdd.get(i);
            double d1 = getDelta(OpKey.Type.REMOVE_AISLE, a1);
            double d2 = getDelta(OpKey.Type.ADD_AISLE, a2);
            totalDelta += d1 + d2;
        }

        // Aplica as mudanças apenas se a política de aceitação permitir
        if (accept(totalDelta)) {
            for (int i = 0; i < count; i++) {
                applyRemoveAisle(aislesToRemove.get(i));
                applyAddAisle(aislesToAdd.get(i));
            }
            return true;
        }

        return false;
    }

    /**
     * Implementação do movimento 2-opt para otimização de rotas.
     * No contexto do problema SBPO, podemos adaptar para reorganizar a sequência de corredores.
     */
    public boolean apply2OptMove(double[][] distance) {
        // Converte o conjunto de corredores em uma lista ordenada (rota)
        List<Integer> route = new ArrayList<>(solution.getAisles());
        if (route.size() < 4) { // Precisa de pelo menos 4 elementos para 2-opt fazer sentido
            return false;
        }

        // Seleciona aleatoriamente dois pontos de corte
        int i = random.nextInt(route.size() - 2); // Primeiro ponto
        int j = i + 2 + random.nextInt(route.size() - i - 2); // Segundo ponto, pelo menos 2 posições depois

        // Calcula o delta de distância para o movimento 2-opt
        int a = route.get(i);
        int b = route.get(i + 1);
        int c = route.get(j);
        int d = route.get((j + 1) % route.size()); // Circular

        double oldDistance = distance[a][b] + distance[c][d];
        double newDistance = distance[a][c] + distance[b][d];
        double delta = newDistance - oldDistance;

        // Aplica o movimento revertendo o segmento entre i+1 e j
        Collections.reverse(route.subList(i + 1, j + 1));

        // Atualiza a ordem dos corredores na solução
        // Se solution.getAisles() for um LinkedHashSet, preserve a ordem
        Set<Integer> newAisles = new LinkedHashSet<>(route);
        solution.setAisles(newAisles);
        // Atualize o custo se necessário
        solution.setCurrentCost(solution.cost() + delta);

        return true;
    }

    /**
     * Realiza uma perturbação mais intensa na solução para escapar de ótimos locais.
     * Esta perturbação é mais agressiva que o movimento padrão.
     */
    public boolean intensePerturbation(double intensityFactor) {
        // Limita o fator entre 0.1 e 0.5 para evitar destruir completamente a solução
        intensityFactor = Math.min(0.5, Math.max(0.1, intensityFactor));
        ChallengeInstance instance = solution.getInstance();

        // 1. Perturbação em pedidos
        Set<Integer> orders = solution.getOrders();
        int orderCount = (int) Math.ceil(orders.size() * intensityFactor);
        if (orderCount > 0 && !orders.isEmpty()) {
            List<Integer> selectedOrders = new ArrayList<>(orders);
            Collections.shuffle(selectedOrders);

            // Remove aleatoriamente alguns pedidos
            for (int i = 0; i < Math.min(orderCount, selectedOrders.size()); i++) {
                applyRemoveOrder(selectedOrders.get(i));
            }

            // Adiciona aleatoriamente alguns pedidos que não estão na solução
            List<Integer> availableOrders = instance.getPedidos().stream()
                .map(p -> p.getId())
                .filter(id -> !solution.getOrders().contains(id))
                .collect(Collectors.toList());

            if (!availableOrders.isEmpty()) {
                Collections.shuffle(availableOrders);
                int addCount = Math.min(orderCount, availableOrders.size());
                for (int i = 0; i < addCount; i++) {
                    applyAddOrder(availableOrders.get(i));
                }
            }
        }

        // 2. Perturbação em corredores
        Set<Integer> aisles = solution.getAisles();
        int aisleCount = (int) Math.ceil(aisles.size() * intensityFactor);
        if (aisleCount > 0 && !aisles.isEmpty()) {
            List<Integer> selectedAisles = new ArrayList<>(aisles);
            Collections.shuffle(selectedAisles);

            // Remove aleatoriamente alguns corredores
            for (int i = 0; i < Math.min(aisleCount, selectedAisles.size()); i++) {
                applyRemoveAisle(selectedAisles.get(i));
            }

            // Adiciona aleatoriamente alguns corredores que não estão na solução
            List<Integer> availableAisles = instance.getCorredores().stream()
                .map(c -> c.getId())
                .filter(id -> !solution.getAisles().contains(id))
                .collect(Collectors.toList());

            if (!availableAisles.isEmpty()) {
                Collections.shuffle(availableAisles);
                int addCount = Math.min(aisleCount, availableAisles.size());
                for (int i = 0; i < addCount; i++) {
                    applyAddAisle(availableAisles.get(i));
                }
            }
        }

        return true; // A perturbação sempre é aplicada, mesmo que parcialmente
    }

    // Reinicializa scores para evitar estagnação
    private void maybeResetOpScores() {
        alnsIteration++;
        if (alnsIteration % resetScoresInterval == 0) {
            Arrays.fill(opScores, 1.0);
            Arrays.fill(opWeights, 1.0);
        }
    }

    // Seleciona operador via roleta ponderada pelo score acumulado
    private Op selectOpALNS() {
        double total = 0.0;
        for (double w : opScores) total += w;
        double r = random.nextDouble() * total;
        double acc = 0.0;
        for (int i = 0; i < opScores.length; i++) {
            acc += opScores[i];
            if (r <= acc) return Op.values()[i];
        }
        return Op.values()[opScores.length - 1];
    }

    // Atualiza score do operador conforme política ALNS
    private void updateOpScore(Op op, boolean improved, boolean accepted) {
        int idx = op.ordinal();
        if (improved) {
            opScores[idx] += 5.0;
        } else if (accepted) {
            opScores[idx] += 1.0;
        }
        // Não soma nada se não foi aceito
        opWeights[idx] = Math.max(0.01, opScores[idx]);
        opCounts[idx]++;
    }

    // ALNS: perturbação autodirigida com política de score e reset
    public boolean perturbALNS(double delta, double temperature) {
        maybeResetOpScores();
        Op op = selectOpALNS();
        boolean improved = false;
        boolean accepted = false;
        boolean tried = false;
        switch (op) {
            case ADD_ORDER:
                tried = tryOp(op);
                if (tried) {
                    // Aqui você pode comparar o custo antes/depois para definir improved/accepted
                    improved = true;
                    accepted = true;
                }
                break;
            case REMOVE_ORDER:
                tried = tryOp(op);
                if (tried) {
                    improved = true;
                    accepted = true;
                }
                break;
            case ADD_AISLE:
                tried = tryOp(op);
                if (tried) {
                    improved = true;
                    accepted = true;
                }
                break;
            case REMOVE_AISLE:
                tried = tryOp(op);
                if (tried) {
                    improved = true;
                    accepted = true;
                }
                break;
        }
        updateOpScore(op, improved, accepted);
        return tried;
    }

    // Implementação do método faltante para corrigir o erro de compilação
    private boolean tryOp(Op op) {
        OpDelta od = computeBestDeltaForOp(op);
        if (od == null) return false;
        return tryOpWithPrecomputedDelta(od);
    }
}
