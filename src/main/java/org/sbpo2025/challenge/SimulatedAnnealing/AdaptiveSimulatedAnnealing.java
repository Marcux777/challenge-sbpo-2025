package org.sbpo2025.challenge.SimulatedAnnealing;

import org.sbpo2025.challenge.ChallengeSolver;
import org.sbpo2025.challenge.solution.ChallengeSolution;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;

/**
 * Implementação do Adaptive Simulated Annealing (ASA) baseado em Ingber (1993).
 * Adapta dinamicamente a temperatura e a amplitude da vizinhança.
 */
public class AdaptiveSimulatedAnnealing {
    // Parâmetros de controle
    private final int D;                  // dimensão do problema (número de parâmetros a otimizar)
    private final double[] c;             // coeficientes de resfriamento c_i por dimensão
    private final double rTarget;         // taxa de aceitação desejada (ex: 0.4)
    private final double gamma;           // fator de ajuste para T0 (ex: 0.05)
    private final double delta;           // margem para ajuste de T0 (ex: 0.05)
    private final int batchSize;          // iterações por janela de adaptação (ex: 100)

    // Estado interno
    private double[] T0;                  // T_{i0} atuais (podem ser ajustados)
    private double[] T;                   // temperaturas atuais T_i(k) por dimensão
    private long kGlobal = 0;             // contador global de iterações (para T(k))
    private int kReanneal = 0;            // contador de iterações desde o último re-annealing (para T(k))
    private ChallengeSolution best;       // melhor solução encontrada

    // Métricas de desempenho
    private long fullEvalCount = 0;       // contador de avaliações completas
    private long incEvalCount = 0;        // contador de avaliações incrementais
    private long fullEvalTime = 0;        // tempo total de avaliações completas (ns)
    private long incEvalTime = 0;         // tempo total de avaliações incrementais (ns)

    /**
     * Construtor da classe AdaptiveSimulatedAnnealing.
     *
     * @param D Dimensão do espaço de parâmetros.
     * @param initialT0 Temperaturas iniciais para cada dimensão.
     * @param c Coeficientes de resfriamento para cada dimensão.
     * @param rTarget Taxa de aceitação alvo.
     * @param gamma Fator de ajuste da temperatura inicial.
     * @param delta Margem da taxa de aceitação para ajuste.
     * @param batchSize Tamanho da janela para calcular a taxa de aceitação.
     */
    public AdaptiveSimulatedAnnealing(int D, double[] initialT0, double[] c,
                                      double rTarget, double gamma, double delta,
                                      int batchSize) {
        if (initialT0.length != D || c.length != D) {
            throw new IllegalArgumentException("Arrays initialT0 and c must have length D.");
        }
        this.D = D;
        this.T0 = initialT0.clone();      // T0 que será adaptado
        this.c = c.clone();
        this.rTarget = rTarget;
        this.gamma = gamma;
        this.delta = delta;
        this.batchSize = batchSize;
        this.T = new double[D];
    }

    /**
     * Executa o algoritmo ASA.
     *
     * @param sol Solução inicial. Será modificada para conter a melhor solução encontrada.
     * @param maxIter Número máximo de iterações.
     */
    public void run(ChallengeSolution sol, int maxIter) {
        best = sol.copy(); // Assume que ChallengeSolution tem um método copy()
        registerFullEvaluation(() -> best.evaluateCost()); // Garante que o custo inicial está calculado
        resetTemperatures(); // Inicializa T[i] = T0[i]
        kGlobal = 0;
        kReanneal = 0;
        int acceptedInBatch = 0;
        long proposedInBatch = 0; // Contador de movimentos propostos no batch

        for (int iter = 1; iter <= maxIter; ++iter, ++kGlobal, ++kReanneal) {
            // 1) Gera e avalia um vizinho usando operadores incrementais
            int operatorType = selectOperatorType();
            ChallengeSolution nb = sol.copy();
            boolean moveApplied = false;
            double deltaCost = 0;

            switch (operatorType) {
                case 0: // Adicionar/remover pedido
                    moveApplied = applyOrderMove(nb);
                    break;
                case 1: // Adicionar/remover corredor
                    moveApplied = applyAisleMove(nb);
                    break;
                case 2: // Trocar corredor
                    moveApplied = applyAisleSwapMove(nb);
                    break;
                case 3: // Perturbação multi-dimensional clássica do ASA
                    moveApplied = applyClassicAsaMove(nb);
                    break;
            }

            // Se nenhum movimento foi possível, pule esta iteração
            if (!moveApplied) {
                 updateTemperatures();
                 if (iter % batchSize == 0) {
                    adjustT0BasedOnAcceptance(acceptedInBatch, proposedInBatch);
                    acceptedInBatch = 0;
                    proposedInBatch = 0;
                 }
                 continue;
            }

            proposedInBatch++;
            deltaCost = nb.cost() - sol.cost();

            // 2) Critério de Metropolis multidimensional
            double avgT = averageTemperature();
            boolean accepted = false;

            if (deltaCost <= 0) { // Sempre aceita movimentos de melhoria
                accepted = true;
            } else if (avgT > 1e-9) { // Aceita movimentos de piora com probabilidade
                double acceptProb = Math.exp(-deltaCost / avgT);
                accepted = Math.random() < acceptProb;
            }

            if (accepted) {
                sol = nb; // Aceita a nova solução
                acceptedInBatch++;

                // 3) Atualiza melhor solução encontrada
                if (sol.cost() < best.cost() && sol.isViable()) {
                    best = sol.copy();
                    System.out.printf("Iter %d: Nova melhor solução com custo = %.4f (Re-annealing)%n", iter, best.cost());
                    // Re-annealing quando encontra melhoria
                    kReanneal = 0;
                    resetTemperatures();
                }
            }

            // 4) Atualiza T[i] pelo cronograma ASA
            updateTemperatures();

            // 5) A cada batchSize iterações, ajusta T0 baseado na taxa de aceitação
            if (iter % batchSize == 0) {
                 adjustT0BasedOnAcceptance(acceptedInBatch, proposedInBatch);
                 acceptedInBatch = 0;
                 proposedInBatch = 0;
            }

            // A cada 500 iterações, verifica viabilidade e faz avaliação completa
            // para evitar acúmulo de erros numéricos nos deltas incrementais
            if (iter % 500 == 0) {
                if (!sol.isViable()) {
                    System.out.println("Iter " + iter + ": Solução inviável detectada, aplicando reparo");
                    boolean repaired = sol.repair();

                    // Antes da modificação: usava variável sol diretamente na lambda
                    // Solução: criar uma cópia final da referência para a solução
                    final ChallengeSolution solFinal = sol;
                    registerFullEvaluation(() -> solFinal.evaluateCost());

                    if (repaired && sol.cost() < best.cost()) {
                        best = sol.copy();
                        System.out.printf("Iter %d: Nova melhor solução após reparo: %.4f%n", iter, best.cost());
                    }
                } else {
                    // Mesmo viável, reavalia completamente para evitar drift numérico
                    // Mesma correção: criar uma cópia final da referência
                    final ChallengeSolution solFinal = sol;
                    registerFullEvaluation(() -> solFinal.evaluateCost());
                }
            }

            // A cada 1000 iterações, aplica perturbação intensa para escapar de mínimos locais
            if (iter % 1000 == 0) {
                ChallengeSolution perturbedSol = sol.copy();
                perturbedSol.intensePerturbation(0.2);

                // Repara e avalia completamente após perturbação intensa
                if (!perturbedSol.isViable()) {
                    perturbedSol.repair();
                }
                registerFullEvaluation(() -> perturbedSol.evaluateCost());

                // Aceita a solução perturbada se for melhor
                if (perturbedSol.cost() < sol.cost()) {
                    sol = perturbedSol;

                    if (sol.cost() < best.cost()) {
                        best = sol.copy();
                        System.out.printf("Iter %d: Nova melhor solução após perturbação: %.4f%n", iter, best.cost());
                        kReanneal = 0;
                        resetTemperatures();
                    }
                }
            }

            // Exibe estatísticas a cada 5000 iterações
            if (iter % 5000 == 0 || iter == maxIter) {
                System.out.printf("Iter %d: Temp média = %.6f, Custo = %.4f, Best = %.4f%n",
                    iter, avgT, sol.cost(), best.cost());
                System.out.printf("  Avals completas: %d (%.3fms/eval) | Avals incrementais: %d (%.3fms/eval)%n",
                    fullEvalCount, fullEvalTime / 1_000_000.0 / Math.max(1, fullEvalCount),
                    incEvalCount, incEvalTime / 1_000_000.0 / Math.max(1, incEvalCount));
            }
        }

        // Ao final, copia a melhor solução encontrada para a solução de entrada/saída
        sol.copyFrom(best);
        System.out.printf("ASA finalizado. Melhor custo encontrado: %.4f%n", best.cost());
        System.out.printf("Estatísticas finais:%n");
        System.out.printf("  Avaliações completas: %d (%.3f ms/eval)%n",
            fullEvalCount, fullEvalTime / 1_000_000.0 / Math.max(1, fullEvalCount));
        System.out.printf("  Avaliações incrementais: %d (%.3f ms/eval)%n",
            incEvalCount, incEvalTime / 1_000_000.0 / Math.max(1, incEvalCount));
        System.out.printf("  Razão avaliações incrementais/completas: %.2f%n",
            (double)incEvalCount/Math.max(1, fullEvalCount));
        System.out.printf("  Speedup avaliação: %.2fx%n",
            (fullEvalTime/Math.max(1, fullEvalCount)) / (incEvalTime/Math.max(1, incEvalCount)));
    }

    /**
     * Seleciona aleatoriamente um tipo de operador a aplicar.
     * @return Índice do tipo de operador (0-3)
     */
    private int selectOperatorType() {
        // Probabilidades para cada tipo de operador
        // Favorece operadores incrementais especializados
        double[] probs = {0.35, 0.35, 0.2, 0.1}; // Ordem, Corredor, Swap, ASA clássico
        double r = Math.random();
        double cumProb = 0;

        for (int i = 0; i < probs.length; i++) {
            cumProb += probs[i];
            if (r < cumProb) return i;
        }

        return 0; // Fallback
    }

    /**
     * Aplica um movimento de adição/remoção de pedido com avaliação incremental.
     * @param sol A solução a ser modificada
     * @return true se o movimento foi aplicado
     */
    private boolean applyOrderMove(ChallengeSolution sol) {
        Random random = new Random();
        Set<Integer> orders = sol.getOrders();

        if (orders.isEmpty() || random.nextDouble() < 0.6) {
            // Adicionar um pedido (60% ou sem pedidos)
            List<Integer> allOrderIds = new ArrayList<>();
            for (int i = 0; i < sol.getInstance().getPedidos().size(); i++) {
                if (!orders.contains(i)) {
                    allOrderIds.add(i);
                }
            }

            if (allOrderIds.isEmpty()) {
                return false; // Não há pedidos para adicionar
            }

            int orderToAdd = allOrderIds.get(random.nextInt(allOrderIds.size()));

            // Avaliação incremental para adição de pedido
            long startTime = System.nanoTime();
            double delta = sol.calculateAddOrderDelta(orderToAdd);
            registerIncrementalEvaluation(System.nanoTime() - startTime);

            sol.applyAddOrder(orderToAdd);
            return true;
        } else {
            // Remover um pedido (40%)
            if (orders.isEmpty()) {
                return false;
            }

            List<Integer> orderIds = new ArrayList<>(orders);
            int orderToRemove = orderIds.get(random.nextInt(orderIds.size()));

            // Avaliação incremental para remoção de pedido
            long startTime = System.nanoTime();
            double delta = sol.calculateRemoveOrderDelta(orderToRemove);
            registerIncrementalEvaluation(System.nanoTime() - startTime);

            sol.applyRemoveOrder(orderToRemove);
            return true;
        }
    }

    /**
     * Aplica um movimento de adição/remoção de corredor com avaliação incremental.
     * @param sol A solução a ser modificada
     * @return true se o movimento foi aplicado
     */
    private boolean applyAisleMove(ChallengeSolution sol) {
        Random random = new Random();
        Set<Integer> aisles = sol.getAisles();

        if (aisles.isEmpty() || random.nextDouble() < 0.6) {
            // Adicionar um corredor (60% ou sem corredores)
            List<Integer> allAisleIds = new ArrayList<>();
            for (int i = 0; i < sol.getInstance().getCorredores().size(); i++) {
                if (!aisles.contains(i)) {
                    allAisleIds.add(i);
                }
            }

            if (allAisleIds.isEmpty()) {
                return false; // Não há corredores para adicionar
            }

            int aisleToAdd = allAisleIds.get(random.nextInt(allAisleIds.size()));

            // Avaliação incremental para adição de corredor
            long startTime = System.nanoTime();
            double delta = sol.calculateAddAisleDelta(aisleToAdd);
            registerIncrementalEvaluation(System.nanoTime() - startTime);

            sol.applyAddAisle(aisleToAdd);
            return true;
        } else {
            // Remover um corredor (40%)
            if (aisles.isEmpty()) {
                return false;
            }

            List<Integer> aisleIds = new ArrayList<>(aisles);
            int aisleToRemove = aisleIds.get(random.nextInt(aisleIds.size()));

            // Avaliação incremental para remoção de corredor
            long startTime = System.nanoTime();
            double delta = sol.calculateRemoveAisleDelta(aisleToRemove);
            registerIncrementalEvaluation(System.nanoTime() - startTime);

            sol.applyRemoveAisle(aisleToRemove);
            return true;
        }
    }

    /**
     * Aplica um movimento de troca de corredor com avaliação incremental.
     * @param sol A solução a ser modificada
     * @return true se o movimento foi aplicado
     */
    private boolean applyAisleSwapMove(ChallengeSolution sol) {
        Random random = new Random();
        Set<Integer> aisles = sol.getAisles();

        if (aisles.isEmpty()) {
            return false;
        }

        List<Integer> allAisleIds = new ArrayList<>();
        for (int i = 0; i < sol.getInstance().getCorredores().size(); i++) {
            if (!aisles.contains(i)) {
                allAisleIds.add(i);
            }
        }

        if (allAisleIds.isEmpty()) {
            return false; // Não há corredores para trocar
        }

        // Seleciona aleatoriamente um corredor para remover e outro para adicionar
        List<Integer> currentAisleIds = new ArrayList<>(aisles);
        int aisleToRemove = currentAisleIds.get(random.nextInt(currentAisleIds.size()));
        int aisleToAdd = allAisleIds.get(random.nextInt(allAisleIds.size()));

        // Avaliação incremental para remoção+adição de corredor
        long startTime = System.nanoTime();

        // Calcula delta de remover corredor atual
        double removeAisleDelta = sol.calculateRemoveAisleDelta(aisleToRemove);

        // Simula a remoção para calcular corretamente o delta de adição
        sol.applyRemoveAisle(aisleToRemove);
        double addAisleDelta = sol.calculateAddAisleDelta(aisleToAdd);

        // Realiza a adição do novo corredor
        sol.applyAddAisle(aisleToAdd);

        // Registra a avaliação incremental
        registerIncrementalEvaluation(System.nanoTime() - startTime);

        return true;
    }

    /**
     * Aplica o movimento clássico de perturbação multidimensional do ASA.
     * @param sol A solução a ser modificada
     * @return true se o movimento foi aplicado
     */
    private boolean applyClassicAsaMove(ChallengeSolution sol) {
        boolean perturbed = false;

        // Aplica perturbação em cada dimensão com temperatura atual
        for (int i = 0; i < D; ++i) {
            double u = Math.random();
            double dx = T[i] * (2 * u - 1); // dx ∈ [-Ti, Ti]

            if (sol.perturb(i, dx)) {
                perturbed = true;
            }
        }

        if (perturbed) {
            // Após perturbação clássica, usa avaliação completa
            registerFullEvaluation(() -> sol.evaluateCost());
            return true;
        }

        return false;
    }

    /**
     * Reinicia as temperaturas atuais T[i] para os valores T0[i] (atuais).
     */
    private void resetTemperatures() {
        System.arraycopy(T0, 0, T, 0, D);
        // Garante que T não seja zero inicialmente se T0 for zero
        for(int i=0; i<D; ++i) {
            if (T[i] < 1e-9) T[i] = 1e-9; // Evita divisão por zero ou T muito baixo no início
        }
    }

     /**
     * Atualiza as temperaturas T[i] de acordo com o cronograma ASA.
     * T_i(k) = T_{i0} * exp(-c_i * k_{reanneal}^{1/D})
     */
    private void updateTemperatures() {
        double kPow = Math.pow(Math.max(1, kReanneal), 1.0 / D); // Usa kReanneal, max(1,...) para evitar k=0
        for (int i = 0; i < D; ++i) {
            T[i] = T0[i] * Math.exp(-c[i] * kPow);
             // Adiciona um piso para a temperatura para evitar que chegue a zero muito rápido
             T[i] = Math.max(T[i], 1e-9);
        }
    }

    /**
     * Calcula a temperatura média atual (pode ser substituída por outra métrica).
     * @return A média das temperaturas T[i].
     */
    private double averageTemperature() {
        double sum = 0;
        for (double t : T) {
            sum += t;
        }
        // Evita divisão por zero se D=0 (embora D deva ser > 0)
        return (D > 0) ? Math.max(sum / D, 1e-9) : 1e-9; // Adiciona piso para evitar T=0
    }

     /**
     * Ajusta as temperaturas iniciais T0[i] com base na taxa de aceitação observada.
     * @param accepted Contagem de movimentos aceitos no último batch.
     * @param proposed Contagem de movimentos propostos no último batch.
     */
    private void adjustT0BasedOnAcceptance(int accepted, long proposed) {
        if (proposed == 0) return; // Evita divisão por zero se nenhum movimento foi proposto

        double currentRate = (double) accepted / proposed;
        double adjustmentFactor = 0.0; // 0 significa sem ajuste

        if (currentRate > rTarget + delta) {
            // Taxa muito alta -> aumentar T0 para explorar mais (aceitar menos movimentos ruins)
            // No guia original, aumentar T0 aumenta a amplitude do passo, o que pode DIMINUIR a taxa
            // de aceitação se os passos ficarem muito grandes. Vamos seguir o guia: aumenta T0.
             adjustmentFactor = gamma; // Fator positivo para aumentar T0
             System.out.printf("Fim do batch (k=%d): Taxa de aceitação %.3f > %.3f. Aumentando T0 em %.1f%%%n", kGlobal, currentRate, rTarget + delta, gamma * 100);
        } else if (currentRate < rTarget - delta) {
            // Taxa muito baixa -> diminuir T0 para explorar menos (aceitar mais movimentos)
            // Diminuir T0 diminui a amplitude do passo, o que pode AUMENTAR a taxa de aceitação.
             adjustmentFactor = -gamma; // Fator negativo para diminuir T0
             System.out.printf("Fim do batch (k=%d): Taxa de aceitação %.3f < %.3f. Diminuindo T0 em %.1f%%%n", kGlobal, currentRate, rTarget - delta, gamma * 100);
        }

        if (Math.abs(adjustmentFactor) > 1e-9) { // Se houve ajuste
            adjustT0(adjustmentFactor);
            // Após ajustar T0, é bom resetar as temperaturas T atuais para refletir a mudança
            // e reiniciar a contagem kReanneal para que o novo T0 tenha efeito imediato no schedule.
            resetTemperatures(); // Reseta T[i] para os novos T0[i]
            kReanneal = 0;      // Reinicia o contador do schedule
        } else {
            System.out.printf("Fim do batch (k=%d): Taxa de aceitação %.3f dentro do alvo [%.3f, %.3f]. Sem ajuste de T0.%n", kGlobal, currentRate, rTarget - delta, rTarget + delta);
        }
    }


    /**
     * Aplica o ajuste multiplicativo aos T0[i].
     * T0[i] *= (1 + factorSign * gamma)
     * @param factorSign Sinal do ajuste (+gamma ou -gamma).
     */
    private void adjustT0(double factorSign) {
        for (int i = 0; i < D; ++i) {
            T0[i] *= (1 + factorSign);
            // Adiciona um piso para T0 também, para não ficar negativo ou zero
            T0[i] = Math.max(T0[i], 1e-7);
        }
    }

    /**
     * Registra uma avaliação incremental de custo.
     * @param nanoseconds O tempo levado para calcular o delta de custo em nanossegundos.
     */
    public void registerIncrementalEvaluation(long nanoseconds) {
        incEvalCount++;
        incEvalTime += nanoseconds;
    }

    /**
     * Registra uma avaliação completa de custo.
     * @param evaluation Runnable que executa a avaliação.
     */
    public void registerFullEvaluation(Runnable evaluation) {
        long startTime = System.nanoTime();
        evaluation.run();
        long evalTime = System.nanoTime() - startTime;
        fullEvalCount++;
        fullEvalTime += evalTime;
    }

    // --- Getters para possível monitoramento externo ---

    public double[] getCurrentTemperatures() {
        return T.clone();
    }

    public double[] getCurrentT0() {
        return T0.clone();
    }

    public ChallengeSolution getBestSolution() {
        return best;
    }

    public long getCurrentIteration() {
        return kGlobal;
    }

    public long getIncrementalEvaluationCount() {
        return incEvalCount;
    }

    public long getFullEvaluationCount() {
        return fullEvalCount;
    }

    public double getIncrementalEvaluationTime() {
        return incEvalTime / 1_000_000.0;
    }

    public double getFullEvaluationTime() {
        return fullEvalTime / 1_000_000.0;
    }
}
