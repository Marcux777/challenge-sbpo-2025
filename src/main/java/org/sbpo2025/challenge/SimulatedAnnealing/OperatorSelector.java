package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementação do mecanismo de seleção adaptativa de operadores (AOS).
 * Esta classe gerencia um conjunto de operadores e seleciona qual será
 * aplicado em cada iteração com base em seu desempenho recente.
 *
 * @param <S> Tipo da solução sobre a qual os operadores atuam
 */
public class OperatorSelector<S> {

    // Lista de operadores disponíveis (imutável após construção)
    private final List<Operator<S>> operators;

    // Gerador de números aleatórios (não mais usado diretamente)
    private final Random random;

    // Estatísticas por índice (Thread-Safe)
    private final AtomicInteger[] usageCounts;
    private final AtomicInteger[] successCounts;
    private final DoubleAdder[] totalRewards;

    // Mapeamento de operadores para índices
    private final Map<Operator<S>, Integer> indexMap;

    // Parâmetros de configuração
    private double ucbExplorationFactor = Math.sqrt(2.0); // Fator para UCB1 (default sqrt(2))
    private double epsilonExplorationFactor = 0.1; // Fator para ε-greedy (default 0.1)
    private final LongAdder totalApplications = new LongAdder(); // Total de aplicações (Thread-Safe)
    private final ReentrantReadWriteLock weightsLock = new ReentrantReadWriteLock();

    // Estratégia de seleção
    private SelectionStrategy<S> strategyImpl;
    private Stats stats;

    // Classe de estatísticas para passar para as estratégias
    public class Stats {
        public final AtomicInteger[] usageCounts;
        public final AtomicInteger[] successCounts;
        public final DoubleAdder[] totalRewards;
        public final LongAdder totalApplications;

        public Stats(AtomicInteger[] usageCounts, AtomicInteger[] successCounts, DoubleAdder[] totalRewards, LongAdder totalApplications) {
            this.usageCounts = usageCounts;
            this.successCounts = successCounts;
            this.totalRewards = totalRewards;
            this.totalApplications = totalApplications;
        }
    }

    /**
     * Construtor com lista de operadores e semente aleatória padrão.
     *
     * @param operators Lista de operadores disponíveis
     */
    public OperatorSelector(List<Operator<S>> operators) {
        this(operators, new Random());
    }

    /**
     * Construtor com lista de operadores e instância de Random customizada.
     *
     * @param operators Lista de operadores disponíveis
     * @param random Instância de Random para reprodutibilidade
     */
    public OperatorSelector(List<Operator<S>> operators, Random random) {
        if (operators == null || operators.isEmpty())
            throw new IllegalArgumentException("Deve haver ao menos um operador");
        this.operators = List.copyOf(operators); // Imutável
        this.random = random; // Mantido para compatibilidade, mas não usado
        int n = operators.size();
        this.usageCounts = new AtomicInteger[n];
        this.successCounts = new AtomicInteger[n];
        this.totalRewards = new DoubleAdder[n];
        this.indexMap = new HashMap<>();
        for (int i = 0; i < n; i++) {
            usageCounts[i] = new AtomicInteger(0);
            successCounts[i] = new AtomicInteger(0);
            totalRewards[i] = new DoubleAdder();
            this.indexMap.put(this.operators.get(i), i);
            operators.get(i).setSelectionProbability(1.0 / n);
        }
        this.stats = new Stats(usageCounts, successCounts, totalRewards, totalApplications);
    }

    /**
     * Seleciona um operador para aplicação com base na estratégia atual.
     *
     * @return O operador selecionado
     */
    public Operator<S> select() {
        return strategyImpl.select(operators, stats);
    }

    /**
     * Fornece feedback sobre o resultado da aplicação de um operador.
     *
     * @param operator O operador que foi aplicado
     * @param delta O delta de custo obtido (negativo para melhorias)
     * @param accepted Se a solução foi aceita (p.ex., no Simulated Annealing)
     */
    public void feedback(Operator<S> operator, double delta, boolean accepted) {
        Integer idx = indexMap.get(operator);
        if (idx == null) return;
        totalApplications.increment();
        usageCounts[idx].incrementAndGet();
        if (delta < 0 || accepted) {
            successCounts[idx].incrementAndGet();
            double reward = (delta < 0) ? 1.0 : (accepted ? 0.1 : 0.0);
            totalRewards[idx].add(reward);
            operator.credit(reward);
        }
    }

    /**
     * Atualiza as probabilidades de seleção de todos os operadores (para Roleta).
     */
    public void updateWeights() {
        weightsLock.writeLock().lock();
        try {
            double minScore = operators.stream().mapToDouble(Operator::getMeanReward).min().orElse(0.0);
            double offset = minScore < 0 ? -minScore : 0;
            double total = operators.stream().mapToDouble(op -> op.getMeanReward() + offset).sum();
            if (total <= 1e-9) {
                double equalProb = 1.0 / operators.size();
                for (Operator<S> op : operators) {
                    op.setSelectionProbability(equalProb);
                }
                return;
            }
            for (Operator<S> op : operators) {
                double adjustedScore = op.getMeanReward() + offset;
                double newP = adjustedScore / total;
                op.setSelectionProbability(newP);
            }
        } finally {
            weightsLock.writeLock().unlock();
        }
    }

    /**
     * Exibe estatísticas sobre o uso dos operadores.
     */
    public void printStatistics() {
        weightsLock.readLock().lock();
        try {
            System.out.println("\n=== Estatísticas de Operadores (Total Aplicações: " + totalApplications.sum() + ") ===");
            System.out.printf("%-10s | %5s | %7s | %5s | %10s | %7s | %7s | %7s%n",
                              "Operador", "Uso", "Sucesso", "Taxa", "Recompensa", "Score", "Média", "N Aplic");
            System.out.println("-------------------------------------------------------------------------------");
            operators.stream()
                .sorted((o1, o2) -> Double.compare(o2.getMeanReward(), o1.getMeanReward()))
                .forEach(op -> {
                    Integer idx = indexMap.get(op);
                    int uses = usageCounts[idx].get();
                    int successes = successCounts[idx].get();
                    double rate = uses > 0 ? (double) successes / uses : 0;
                    double reward = totalRewards[idx].sum();
                    double score = op.getScore();
                    double mean = op.getMeanReward();
                    int nApp = op.getCountApplications();
                    System.out.printf("%-10s | %5d | %7d | %5.2f | %10.2f | %7.4f | %7.4f | %7d%n",
                            op.getName(), uses, successes, rate, reward, score, mean, nApp);
                });
            System.out.println("-------------------------------------------------------------------------------");
        } finally {
            weightsLock.readLock().unlock();
        }
    }

    /**
     * Define a estratégia de seleção.
     *
     * @param strategy A estratégia a ser usada
     */
    public void setStrategy(SelectionStrategy strategy) {
        this.strategyImpl = strategy;
        System.out.println("INFO: Estratégia de seleção alterada para " + strategy.getClass().getSimpleName());
    }

    /**
     * Define o fator de exploração para UCB1.
     *
     * @param factor Fator de exploração (e.g., sqrt(2.0))
     */
    public void setUcbExplorationFactor(double factor) {
        if (factor < 0) throw new IllegalArgumentException("Fator de exploração UCB deve ser não-negativo.");
        this.ucbExplorationFactor = factor;
        if (strategyImpl instanceof Ucb1Strategy) {
            this.strategyImpl = new Ucb1Strategy<>(factor);
        }
    }

    /**
     * Define o fator de exploração (epsilon) para ε-greedy.
     *
     * @param factor Fator de exploração (0.0 a 1.0)
     */
    public void setEpsilonExplorationFactor(double factor) {
        if (factor < 0 || factor > 1) throw new IllegalArgumentException("Fator de exploração Epsilon deve estar entre 0.0 e 1.0.");
        this.epsilonExplorationFactor = factor;
        if (strategyImpl instanceof EpsilonGreedyStrategy) {
            this.strategyImpl = new EpsilonGreedyStrategy<>(factor);
        }
    }

    // Getters para os fatores, se necessário
    public double getUcbExplorationFactor() {
        return ucbExplorationFactor;
    }

    public double getEpsilonExplorationFactor() {
        return epsilonExplorationFactor;
    }
}
