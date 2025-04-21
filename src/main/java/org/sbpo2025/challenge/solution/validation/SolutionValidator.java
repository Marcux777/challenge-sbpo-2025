package org.sbpo2025.challenge.solution.validation;

import java.util.*;
import java.util.stream.Collectors;
import java.util.BitSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.sbpo2025.challenge.model.*;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Responsável pela validação e reparo de soluções.
 * Verifica se uma solução é viável e fornece métodos para torná-la viável
 * quando necessário.
 */
public class SolutionValidator {

    private final ChallengeSolution solution;
    private final Map<Integer, Corredor> corredorById;
    private final Map<Integer, Pedido> pedidoById;
    private final int nItems;
    private final Map<Integer, BitSet> coverageBitSet;
    private final Map<Integer, BitSet> aisleCoverageBitSet;
    private final Map<Integer, BitSet> aisleToPairBits;

    public SolutionValidator(ChallengeSolution solution) {
        this.solution = solution;
        ChallengeInstance instance = solution.getInstance();
        this.corredorById = instance.getCorredores().stream()
            .collect(Collectors.toMap(Corredor::getId, c -> c));
        this.pedidoById = instance.getPedidos().stream()
            .collect(Collectors.toMap(Pedido::getId, p -> p));
        this.nItems = instance.getNumItens();
        this.coverageBitSet = new HashMap<>();
        this.aisleCoverageBitSet = new HashMap<>();
        this.aisleToPairBits = new HashMap<>();
        // Inicializa BitSet para cada pedido
        for (Pedido pedido : instance.getPedidos()) {
            BitSet bs = new BitSet(nItems);
            coverageBitSet.put(pedido.getId(), bs);
        }
        // Inicializa BitSet para cada corredor (itens disponíveis)
        for (Corredor c : instance.getCorredores()) {
            BitSet bs = new BitSet(nItems);
            for (ItemStock stock : c.getEstoque()) {
                if (stock.getUnidades() > 0) bs.set(stock.getItemId());
            }
            aisleCoverageBitSet.put(c.getId(), bs);
        }
        // aisleToPairBits: para cada corredor, BitSet de pares (orderId << 16 | itemId)
        for (Corredor corredor : corredorById.values()) {
            int aisleId = corredor.getId();
            BitSet pairBits = new BitSet();
            for (Pedido pedido : pedidoById.values()) {
                int orderId = pedido.getId();
                for (ItemRequest req : pedido.getItens()) {
                    for (ItemStock stock : corredor.getEstoque()) {
                        if (stock.getItemId() == req.getItemId() && stock.getUnidades() > 0) {
                            int key = (orderId << 16) | req.getItemId();
                            pairBits.set(key);
                        }
                    }
                }
            }
            aisleToPairBits.put(aisleId, pairBits);
        }
    }

    /**
     * Atualiza a cobertura BitSet de todos os pedidos com base nos corredores selecionados.
     * Só atualiza bits novos, não limpa tudo.
     */
    private void updateCoverageBitSet() {
        int nThreads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        List<Pedido> pedidos = new ArrayList<>(pedidoById.values());
        int chunkSize = (pedidos.size() + nThreads - 1) / nThreads;
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < pedidos.size(); i += chunkSize) {
            int from = i;
            int to = Math.min(i + chunkSize, pedidos.size());
            futures.add(executor.submit(() -> {
                for (int j = from; j < to; j++) {
                    Pedido pedido = pedidos.get(j);
                    BitSet pedidoCoverage = coverageBitSet.get(pedido.getId());
                    BitSet temp = new BitSet(nItems);
                    for (Integer aisleId : solution.getAisles()) {
                        BitSet aisleBits = aisleCoverageBitSet.get(aisleId);
                        if (aisleBits != null) {
                            temp.or(aisleBits);
                        }
                    }
                    for (ItemRequest req : pedido.getItens()) {
                        int itemId = req.getItemId();
                        if (temp.get(itemId) && !pedidoCoverage.get(itemId)) {
                            synchronized (pedidoCoverage) {
                                pedidoCoverage.set(itemId);
                            }
                        }
                    }
                }
            }));
        }
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        executor.shutdown();
    }

    /**
     * Verifica a viabilidade da solução atual usando BitSet.
     * Uma solução é viável se todos os pedidos selecionados podem ser atendidos.
     *
     * @return true se a solução for viável, false caso contrário
     */
    public boolean isViable() {
        updateCoverageBitSet();
        Set<Integer> orders = solution.getOrders();
        for (Integer orderId : orders) {
            Pedido pedido = pedidoById.get(orderId);
            if (pedido == null) continue;
            BitSet bs = coverageBitSet.get(orderId);
            int covered = 0;
            for (ItemRequest req : pedido.getItens()) {
                if (bs.get(req.getItemId())) covered++;
            }
            if (covered < pedido.getItens().size()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Faz uma reparação da solução para torná-la viável usando BitSet.
     * Adiciona corredores necessários para atender pedidos não cobertos.
     *
     * @return true se a reparação foi bem-sucedida, false caso contrário
     */
    public boolean repair() {
        Set<Integer> orders = solution.getOrders();
        if (isViable()) {
            return true;
        }
        updateCoverageBitSet();
        // Universo: pares (orderId, itemId) não cobertos, codificados como int
        Set<Integer> universe = new HashSet<>();
        for (Integer orderId : orders) {
            Pedido pedido = pedidoById.get(orderId);
            if (pedido == null) continue;
            BitSet bs = coverageBitSet.get(orderId);
            for (ItemRequest req : pedido.getItens()) {
                if (!bs.get(req.getItemId())) {
                    int key = (orderId << 16) | req.getItemId();
                    universe.add(key);
                }
            }
        }
        if (universe.isEmpty()) return true;
        // Mapeia corredores para os pares que cobrem
        Map<Integer, Set<Integer>> aisleToPairs = new HashMap<>();
        for (Map.Entry<Integer, BitSet> entry : aisleToPairBits.entrySet()) {
            if (solution.getAisles().contains(entry.getKey())) continue;
            Set<Integer> pairs = new HashSet<>();
            BitSet bits = entry.getValue();
            for (int k = bits.nextSetBit(0); k >= 0; k = bits.nextSetBit(k+1)) {
                if (universe.contains(k)) pairs.add(k);
            }
            if (!pairs.isEmpty()) {
                aisleToPairs.put(entry.getKey(), pairs);
            }
        }
        // Guloso: sempre escolhe o corredor que cobre mais pares ainda não cobertos
        while (!universe.isEmpty() && !aisleToPairs.isEmpty()) {
            int bestAisle = -1;
            int maxCover = -1;
            for (Map.Entry<Integer, Set<Integer>> entry : aisleToPairs.entrySet()) {
                if (entry.getValue().size() > maxCover) {
                    bestAisle = entry.getKey();
                    maxCover = entry.getValue().size();
                }
            }
            if (maxCover == 0 || bestAisle == -1) break;
            solution.applyAddAisle(bestAisle);
            Set<Integer> covered = aisleToPairs.get(bestAisle);
            universe.removeAll(covered);
            // Atualiza apenas corredores que cobriam algum dos pares removidos
            Set<Integer> affectedAisles = new HashSet<>();
            for (Integer pair : covered) {
                for (Map.Entry<Integer, Set<Integer>> entry : aisleToPairs.entrySet()) {
                    if (entry.getValue().contains(pair)) {
                        affectedAisles.add(entry.getKey());
                    }
                }
            }
            for (Integer aisleId : affectedAisles) {
                Set<Integer> pairs = aisleToPairs.get(aisleId);
                if (pairs != null) {
                    pairs.removeAll(covered);
                }
            }
            aisleToPairs.remove(bestAisle);
        }
        // Poda de corredores redundantes
        for (Integer a : new HashSet<>(solution.getAisles())) {
            solution.applyRemoveAisle(a);
            if (!this.isViable()) {
                solution.applyAddAisle(a);
            }
        }
        return isViable();
    }

    private static BitSet bitSetFromSet(Set<Integer> set) {
        BitSet bs = new BitSet();
        for (Integer i : set) bs.set(i);
        return bs;
    }

    /**
     * Avalia a qualidade de cobertura da solução usando BitSet.
     * Retorna a porcentagem de pedidos que estão completamente cobertos.
     *
     * @return valor entre 0.0 e 1.0 representando a proporção de pedidos cobertos
     */
    public double coverageQuality() {
        updateCoverageBitSet();
        Set<Integer> orders = solution.getOrders();
        if (orders.isEmpty()) {
            return 0.0;
        }
        int fullyCovered = 0;
        for (Integer orderId : orders) {
            Pedido pedido = pedidoById.get(orderId);
            if (pedido == null) continue;
            BitSet bs = coverageBitSet.get(orderId);
            int covered = 0;
            for (ItemRequest req : pedido.getItens()) {
                if (bs.get(req.getItemId())) covered++;
            }
            if (covered == pedido.getItens().size()) {
                fullyCovered++;
            }
        }
        return (double) fullyCovered / orders.size();
    }

    /**
     * Remove os pedidos que não podem ser completamente atendidos
     * com os corredores selecionados atualmente (usando BitSet).
     *
     * @return número de pedidos removidos
     */
    public int removeUnfeasibleOrders() {
        updateCoverageBitSet();
        Set<Integer> orders = solution.getOrders();
        int removedCount = 0;
        List<Integer> ordersToRemove = new ArrayList<>();
        for (Integer orderId : orders) {
            Pedido pedido = pedidoById.get(orderId);
            if (pedido == null) {
                ordersToRemove.add(orderId);
                continue;
            }
            BitSet bs = coverageBitSet.get(orderId);
            int covered = 0;
            for (ItemRequest req : pedido.getItens()) {
                if (bs.get(req.getItemId())) covered++;
            }
            if (covered < pedido.getItens().size()) {
                ordersToRemove.add(orderId);
            }
        }
        for (Integer orderId : ordersToRemove) {
            solution.applyRemoveOrder(orderId);
            removedCount++;
        }
        return removedCount;
    }
}
