package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementação do mecanismo de seleção adaptativa de operadores (AOS).
 * Esta classe gerencia um conjunto de operadores e seleciona qual será
 * aplicado em cada iteração com base em seu desempenho recente.
 *
 * @param <S> Tipo da solução sobre a qual os operadores atuam
 */
public class OperatorSelector<S> {

    // Lista de operadores disponíveis
    private List<Operator<S>> operators;

    // Gerador de números aleatórios
    private Random random;

    // Estatísticas (Thread-Safe)
    private ConcurrentMap<Operator<S>, AtomicInteger> usageCounts;
    private ConcurrentMap<Operator<S>, AtomicInteger> successCounts;
    private ConcurrentMap<Operator<S>, Double> totalRewards; // Double is generally safe enough for concurrent updates if only added to

    // Parâmetros de configuração
    private double ucbExplorationFactor = Math.sqrt(2.0); // Fator para UCB1 (default sqrt(2))
    private double epsilonExplorationFactor = 0.1; // Fator para ε-greedy (default 0.1)
    private AtomicInteger totalApplications = new AtomicInteger(0); // Total de aplicações (Thread-Safe)

    // Estratégia de seleção
    public enum SelectionStrategy {
        ROULETTE_WHEEL,   // Roleta (proporcional ao score)
        UCB1,             // Upper Confidence Bound
        EPSILON_GREEDY    // ε-greedy (seleciona o melhor com probabilidade 1-ε)
    }

    private SelectionStrategy strategy = SelectionStrategy.UCB1;

    /**
     * Construtor com lista de operadores e semente aleatória padrão.
     *
     * @param operators Lista de operadores disponíveis
     */
    public OperatorSelector(List<Operator<S>> operators) {
        this(operators, new Random()); // Chama o construtor com Random padrão
    }

    /**
     * Construtor com lista de operadores e instância de Random customizada.
     *
     * @param operators Lista de operadores disponíveis
     * @param random Instância de Random para reprodutibilidade
     */
    public OperatorSelector(List<Operator<S>> operators, Random random) {
        this.operators = new ArrayList<>(operators);
        this.random = random;

        // Inicializa estatísticas (Thread-Safe)
        this.usageCounts = new ConcurrentHashMap<>();
        this.successCounts = new ConcurrentHashMap<>();
        this.totalRewards = new ConcurrentHashMap<>();

        // Inicializa contadores para cada operador
        for (Operator<S> op : operators) {
            usageCounts.put(op, new AtomicInteger(0));
            successCounts.put(op, new AtomicInteger(0));
            totalRewards.put(op, 0.0);
            // Inicialmente todos operadores têm probabilidade igual
            op.setSelectionProbability(1.0 / operators.size());
        }
    }

    /**
     * Seleciona um operador para aplicação com base na estratégia atual.
     *
     * @return O operador selecionado
     */
    public Operator<S> select() {
        switch (strategy) {
            case ROULETTE_WHEEL:
                return selectRouletteWheel();
            case UCB1:
                return selectUCB();
            case EPSILON_GREEDY:
                return selectEpsilonGreedy();
            default:
                // Fallback para Roleta se estratégia for inválida
                System.err.println("WARN: Estratégia de seleção inválida, usando Roleta.");
                return selectRouletteWheel();
        }
    }

    /**
     * Implementação da seleção por roleta (proporcional ao score).
     */
    private Operator<S> selectRouletteWheel() {
        // Garante que os scores sejam não-negativos para a roleta
        double minScore = operators.stream().mapToDouble(Operator::getScore).min().orElse(0.0);
        double offset = minScore < 0 ? -minScore : 0; // Desloca scores para serem >= 0

        double sum = operators.stream().mapToDouble(op -> op.getScore() + offset).sum();

        // Se a soma for zero (ou negativa por erro numérico), seleciona uniformemente
        if (sum <= 1e-9) {
            return operators.get(random.nextInt(operators.size()));
        }

        double r = random.nextDouble() * sum;
        double acc = 0;

        for (Operator<S> op : operators) {
            acc += (op.getScore() + offset);
            if (r <= acc) {
                return op;
            }
        }

        // Fallback para caso de erro numérico extremo
        return operators.get(random.nextInt(operators.size()));
    }

    /**
     * Implementação da seleção UCB1 (Upper Confidence Bound).
     */
    private Operator<S> selectUCB() {
        Operator<S> selected = null;
        double maxUcb = Double.NEGATIVE_INFINITY;
        // Evita log(0) ou log(1) - começa a contar a partir da segunda aplicação total
        double logTotalApplications = Math.log(Math.max(2, totalApplications.get()));

        // Primeiro, garante que cada operador seja usado pelo menos uma vez
        for (Operator<S> op : operators) {
            if (usageCounts.get(op).get() == 0) {
                return op; // Sempre experimenta operadores não utilizados primeiro
            }
        }

        // Se todos já foram usados, calcula UCB1
        for (Operator<S> op : operators) {
            int usages = usageCounts.get(op).get();
            // Calcula a recompensa média (exploitation term)
            // Usamos o score atual do operador que reflete a recompensa acumulada normalizada
            double exploitation = op.getScore();
            // Calcula o termo de exploração
            double exploration = ucbExplorationFactor * Math.sqrt(logTotalApplications / usages);
            double ucbValue = exploitation + exploration;

            if (ucbValue > maxUcb) {
                maxUcb = ucbValue;
                selected = op;
            }
        }

        // Fallback caso algo dê errado (ex: todos UCBs são -Infinito)
        return selected != null ? selected : operators.get(random.nextInt(operators.size()));
    }

    /**
     * Implementação da seleção ε-greedy.
     */
    private Operator<S> selectEpsilonGreedy() {
        // Com probabilidade epsilon, escolhe aleatoriamente (exploração)
        if (random.nextDouble() < epsilonExplorationFactor) {
            return operators.get(random.nextInt(operators.size()));
        }

        // Caso contrário, escolhe o melhor operador (exploitação)
        Operator<S> best = null;
        double maxScore = Double.NEGATIVE_INFINITY;

        for (Operator<S> op : operators) {
            if (op.getScore() > maxScore) {
                maxScore = op.getScore();
                best = op;
            }
        }

        // Fallback se nenhum operador tiver score > -Infinito
        return best != null ? best : operators.get(random.nextInt(operators.size()));
    }

    /**
     * Fornece feedback sobre o resultado da aplicação de um operador.
     *
     * @param operator O operador que foi aplicado
     * @param delta O delta de custo obtido (negativo para melhorias)
     * @param accepted Se a solução foi aceita (p.ex., no Simulated Annealing)
     */
    public void feedback(Operator<S> operator, double delta, boolean accepted) {
        totalApplications.incrementAndGet();

        // Atualiza contadores
        usageCounts.get(operator).incrementAndGet();

        // Atribui crédito se houve melhoria ou a solução foi aceita
        if (delta < 0 || accepted) {
            successCounts.get(operator).incrementAndGet();

            // Recompensa simples: 1 para melhoria, 0.1 para aceitação de piora, 0 senão
            double reward = (delta < 0) ? 1.0 : (accepted ? 0.1 : 0.0);

            // Atualiza a recompensa total (de forma aditiva, geralmente seguro)
            totalRewards.compute(operator, (k, v) -> (v == null ? 0 : v) + reward);

            // Atualiza o score do operador chamando o método credit()
            // Este método já existe na interface Operator e é implementado em BaseOperator
            operator.credit(reward);

            // Remove a tentativa de definir o score diretamente, pois o método não existe na interface
            // double newScore = totalRewards.get(operator) / usageCounts.get(operator).get();
            // operator.setScore(newScore); // ERRO: Método indefinido para Operator<S>
        } else {
            // Opcional: Aplicar decaimento mesmo em falhas (passando recompensa 0)?
            // operator.credit(0.0);
        }
        // Nota: A atualização de pesos (probabilidades para Roleta) é feita separadamente
        // em updateWeights(), geralmente chamada periodicamente.
    }

    /**
     * Atualiza as probabilidades de seleção de todos os operadores (para Roleta).
     */
    public void updateWeights() {
        // Garante que os scores sejam não-negativos para a roleta
        double minScore = operators.stream().mapToDouble(Operator::getScore).min().orElse(0.0);
        double offset = minScore < 0 ? -minScore : 0; // Desloca scores para serem >= 0

        double total = operators.stream().mapToDouble(op -> op.getScore() + offset).sum();

        // Caso todos os scores (ajustados) sejam zero, distribui igualmente
        if (total <= 1e-9) {
            double equalProb = 1.0 / operators.size();
            for (Operator<S> op : operators) {
                op.setSelectionProbability(equalProb);
            }
            return;
        }

        // Atualiza as probabilidades proporcionalmente aos scores ajustados
        for (Operator<S> op : operators) {
            double adjustedScore = op.getScore() + offset;
            double newP = adjustedScore / total;
            op.setSelectionProbability(newP);
        }
    }

    /**
     * Exibe estatísticas sobre o uso dos operadores.
     */
    public void printStatistics() {
        System.out.println("\n=== Estatísticas de Operadores (Total Aplicações: " + totalApplications.get() + ") ===");
        System.out.printf("%-10s | %5s | %7s | %5s | %10s | %7s%n",
                          "Operador", "Uso", "Sucesso", "Taxa", "Recompensa", "Score");
        System.out.println("----------------------------------------------------------------");

        // Ordena por score para melhor visualização
        operators.stream()
            .sorted((o1, o2) -> Double.compare(o2.getScore(), o1.getScore())) // Decrescente
            .forEach(op -> {
                int uses = usageCounts.get(op).get();
                int successes = successCounts.get(op).get();
                double rate = uses > 0 ? (double) successes / uses : 0;
                double reward = totalRewards.getOrDefault(op, 0.0);
                double score = op.getScore(); // Score atual usado pela seleção

                System.out.printf("%-10s | %5d | %7d | %5.2f | %10.2f | %7.4f%n",
                        op.getName(), uses, successes, rate, reward, score);
            });
        System.out.println("----------------------------------------------------------------");
    }

    /**
     * Define a estratégia de seleção.
     *
     * @param strategy A estratégia a ser usada
     */
    public void setStrategy(SelectionStrategy strategy) {
        this.strategy = strategy;
        System.out.println("INFO: Estratégia de seleção alterada para " + strategy);
    }

    /**
     * Define o fator de exploração para UCB1.
     *
     * @param factor Fator de exploração (e.g., sqrt(2.0))
     */
    public void setUcbExplorationFactor(double factor) {
        if (factor < 0) throw new IllegalArgumentException("Fator de exploração UCB deve ser não-negativo.");
        this.ucbExplorationFactor = factor;
    }

    /**
     * Define o fator de exploração (epsilon) para ε-greedy.
     *
     * @param factor Fator de exploração (0.0 a 1.0)
     */
    public void setEpsilonExplorationFactor(double factor) {
        if (factor < 0 || factor > 1) throw new IllegalArgumentException("Fator de exploração Epsilon deve estar entre 0.0 e 1.0.");
        this.epsilonExplorationFactor = factor;
    }

    // Getters para os fatores, se necessário
    public double getUcbExplorationFactor() {
        return ucbExplorationFactor;
    }

    public double getEpsilonExplorationFactor() {
        return epsilonExplorationFactor;
    }
}
