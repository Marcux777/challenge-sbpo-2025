package org.sbpo2025.challenge.solution.movements;

import java.util.*;
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
    private final Random random = new Random();
    private final int maxTrials = 100;  // Limite de tentativas para perturbações

    public SolutionMover(ChallengeSolution solution) {
        this.solution = solution;
    }

    /**
     * Aplica uma pequena modificação (perturbação) aleatória na solução.
     * Pode adicionar/remover um pedido ou adicionar/remover um corredor.
     */
    public boolean perturb(int dimensionIndex, double delta) {
        int changeType = random.nextInt(4); // 0: add order, 1: remove order, 2: add aisle, 3: remove aisle

        // Tentamos várias vezes para encontrar uma perturbação válida
        for (int attempt = 0; attempt < maxTrials; attempt++) {
            switch (changeType) {
                case 0: // Adicionar um pedido
                    if (solution.getInstance().getPedidos().size() > solution.getOrders().size()) {
                        // Seleciona um pedido que não está na solução
                        List<Integer> availableOrders = solution.getInstance().getPedidos().stream()
                            .map(p -> p.getId())
                            .filter(id -> !solution.getOrders().contains(id))
                            .collect(Collectors.toList());

                        if (!availableOrders.isEmpty()) {
                            int orderIdToAdd = availableOrders.get(random.nextInt(availableOrders.size()));
                            applyAddOrder(orderIdToAdd);
                            return true;
                        }
                    }
                    break;

                case 1: // Remover um pedido
                    Set<Integer> orders = solution.getOrders();
                    if (!orders.isEmpty()) {
                        List<Integer> selectedOrders = new ArrayList<>(orders);
                        int orderIdToRemove = selectedOrders.get(random.nextInt(selectedOrders.size()));
                        applyRemoveOrder(orderIdToRemove);
                        return true;
                    }
                    break;

                case 2: // Adicionar um corredor
                    int maxAisleId = solution.getInstance().getCorredores().stream()
                        .mapToInt(c -> c.getId()).max().orElse(-1);
                    if (maxAisleId != -1 && solution.getAisles().size() < solution.getInstance().getCorredores().size()) {
                        // Seleciona um corredor que não está na solução
                        List<Integer> availableAisles = solution.getInstance().getCorredores().stream()
                            .map(c -> c.getId())
                            .filter(id -> !solution.getAisles().contains(id))
                            .collect(Collectors.toList());

                        if (!availableAisles.isEmpty()) {
                            int aisleIdToAdd = availableAisles.get(random.nextInt(availableAisles.size()));
                            applyAddAisle(aisleIdToAdd);
                            return true;
                        }
                    }
                    break;

                case 3: // Remover um corredor
                    Set<Integer> aisles = solution.getAisles();
                    if (!aisles.isEmpty()) {
                        List<Integer> selectedAisles = new ArrayList<>(aisles);
                        int aisleIdToRemove = selectedAisles.get(random.nextInt(selectedAisles.size()));
                        applyRemoveAisle(aisleIdToRemove);
                        return true;
                    }
                    break;
            }

            // Se chegamos aqui, a perturbação falhou, tente outro tipo
            changeType = (changeType + 1) % 4;
        }

        return false; // Todas as tentativas falharam
    }

    /**
     * Aplica incrementalmente a adição de um pedido atualizando a estrutura de dados.
     */
    public void applyAddOrder(int orderId) {
        Set<Integer> orders = solution.getOrders();
        if (orders.contains(orderId)) return;

        // Calcula o delta de custo antes de aplicar a mudança
        IncrementalEvaluator evaluator = new IncrementalEvaluator(solution);
        double delta = evaluator.calculateAddOrderDelta(orderId);

        // Aplica a mudança
        Set<Integer> newOrders = new HashSet<>(orders);
        newOrders.add(orderId);
        solution.setOrders(newOrders);

        // Atualiza o custo da solução
        solution.setCurrentCost(solution.cost() + delta);
    }

    /**
     * Aplica incrementalmente a remoção de um pedido atualizando a estrutura de dados.
     */
    public void applyRemoveOrder(int orderId) {
        Set<Integer> orders = solution.getOrders();
        if (!orders.contains(orderId)) return;

        // Calcula o delta de custo antes de aplicar a mudança
        IncrementalEvaluator evaluator = new IncrementalEvaluator(solution);
        double delta = evaluator.calculateRemoveOrderDelta(orderId);

        // Aplica a mudança
        Set<Integer> newOrders = new HashSet<>(orders);
        newOrders.remove(orderId);
        solution.setOrders(newOrders);

        // Atualiza o custo da solução
        solution.setCurrentCost(solution.cost() + delta);
    }

    /**
     * Aplica incrementalmente a adição de um corredor atualizando a estrutura de dados.
     */
    public void applyAddAisle(int aisleId) {
        Set<Integer> aisles = solution.getAisles();
        if (aisles.contains(aisleId)) return;

        // Calcula o delta de custo antes de aplicar a mudança
        IncrementalEvaluator evaluator = new IncrementalEvaluator(solution);
        double delta = evaluator.calculateAddAisleDelta(aisleId);

        // Aplica a mudança
        Set<Integer> newAisles = new HashSet<>(aisles);
        newAisles.add(aisleId);
        solution.setAisles(newAisles);

        // Atualiza a cobertura para os pedidos afetados
        Map<Integer, Set<Integer>> aisleToOrders = solution.getAisleToOrders();
        if (aisleToOrders.containsKey(aisleId)) {
            ChallengeInstance instance = solution.getInstance();
            Corredor corredor = instance.getCorredores().stream()
                .filter(c -> c.getId() == aisleId)
                .findFirst().orElse(null);

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

        // Atualiza o custo da solução
        solution.setCurrentCost(solution.cost() + delta);
    }

    /**
     * Aplica incrementalmente a remoção de um corredor atualizando a estrutura de dados.
     */
    public void applyRemoveAisle(int aisleId) {
        Set<Integer> aisles = solution.getAisles();
        if (!aisles.contains(aisleId)) return;

        // Calcula o delta de custo antes de aplicar a mudança
        IncrementalEvaluator evaluator = new IncrementalEvaluator(solution);
        double delta = evaluator.calculateRemoveAisleDelta(aisleId);

        // Aplica a mudança
        Set<Integer> newAisles = new HashSet<>(aisles);
        newAisles.remove(aisleId);
        solution.setAisles(newAisles);

        // Atualiza a cobertura para os pedidos afetados
        Map<Integer, Set<Integer>> aisleToOrders = solution.getAisleToOrders();
        if (aisleToOrders.containsKey(aisleId)) {
            ChallengeInstance instance = solution.getInstance();
            Corredor corredor = instance.getCorredores().stream()
                .filter(c -> c.getId() == aisleId)
                .findFirst().orElse(null);

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

        // Atualiza o custo da solução
        solution.setCurrentCost(solution.cost() + delta);
    }

    /**
     * Implementação de movimento avançado: swap múltiplo de corredores.
     * Troca um conjunto de corredores da solução por outro conjunto fora da solução.
     */
    public boolean multipleAisleSwap(int count) {
        ChallengeInstance instance = solution.getInstance();
        Set<Integer> aisles = solution.getAisles();

        // Verifica se há corredores suficientes dentro e fora da solução
        if (aisles.size() < count || instance.getCorredores().size() - aisles.size() < count) {
            return false;
        }

        // Seleciona aleatoriamente 'count' corredores da solução
        List<Integer> selectedAisles = new ArrayList<>(aisles);
        Collections.shuffle(selectedAisles);
        List<Integer> aislesToRemove = selectedAisles.subList(0, count);

        // Seleciona aleatoriamente 'count' corredores fora da solução
        List<Integer> availableAisles = instance.getCorredores().stream()
            .map(c -> c.getId())
            .filter(id -> !aisles.contains(id))
            .collect(Collectors.toList());
        Collections.shuffle(availableAisles);
        List<Integer> aislesToAdd = availableAisles.subList(0, count);

        // Calcula o delta de custo incremental
        double delta = 0.0;
        IncrementalEvaluator evaluator = new IncrementalEvaluator(solution);

        // Remove os corredores antigos
        for (Integer aisleId : aislesToRemove) {
            applyRemoveAisle(aisleId);
        }

        // Adiciona os novos corredores
        for (Integer aisleId : aislesToAdd) {
            applyAddAisle(aisleId);
        }

        return true;
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

        // Como não estamos tratando ordem no conjunto de corredores, esse movimento
        // só faz sentido se tivermos um componente de roteamento, que não é o caso
        // na implementação atual

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
}
