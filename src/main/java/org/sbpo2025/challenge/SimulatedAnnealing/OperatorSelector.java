package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

    // Estatísticas
    private Map<Operator<S>, Integer> usageCounts;
    private Map<Operator<S>, Integer> successCounts;
    private Map<Operator<S>, Double> totalRewards;

    // Parâmetros de configuração
    private double explorationFactor = 0.1;  // Fator para o termo de exploração do UCB
    private int totalApplications = 0;       // Total de aplicações de operadores

    // Estratégia de seleção
    public enum SelectionStrategy {
        ROULETTE_WHEEL,   // Roleta (proporcional ao score)
        UCB1,             // Upper Confidence Bound
        EPSILON_GREEDY    // ε-greedy (seleciona o melhor com probabilidade 1-ε)
    }

    private SelectionStrategy strategy = SelectionStrategy.UCB1;

    /**
     * Construtor padrão.
     *
     * @param operators Lista de operadores disponíveis
     */
    public OperatorSelector(List<Operator<S>> operators) {
        this.operators = new ArrayList<>(operators);
        this.random = new Random();

        // Inicializa estatísticas
        this.usageCounts = new HashMap<>();
        this.successCounts = new HashMap<>();
        this.totalRewards = new HashMap<>();

        // Inicializa contadores para cada operador
        for (Operator<S> op : operators) {
            usageCounts.put(op, 0);
            successCounts.put(op, 0);
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
                return selectRouletteWheel();
        }
    }

    /**
     * Implementação da seleção por roleta (proporcional ao score).
     */
    private Operator<S> selectRouletteWheel() {
        double sum = operators.stream().mapToDouble(Operator::getScore).sum();
        double r = random.nextDouble() * sum;
        double acc = 0;

        for (Operator<S> op : operators) {
            acc += op.getScore();
            if (r < acc) {
                return op;
            }
        }

        // Fallback para caso de erro numérico
        return operators.get(random.nextInt(operators.size()));
    }

    /**
     * Implementação da seleção UCB1 (Upper Confidence Bound).
     */
    private Operator<S> selectUCB() {
        Operator<S> selected = null;
        double maxUcb = Double.NEGATIVE_INFINITY;

        for (Operator<S> op : operators) {
            int usages = usageCounts.get(op);
            if (usages == 0) {
                return op; // Sempre experimenta operadores não utilizados primeiro
            }

            double exploitation = op.getScore();
            double exploration = Math.sqrt(explorationFactor * Math.log(totalApplications) / usages);
            double ucbValue = exploitation + exploration;

            if (ucbValue > maxUcb) {
                maxUcb = ucbValue;
                selected = op;
            }
        }

        return selected;
    }

    /**
     * Implementação da seleção ε-greedy.
     */
    private Operator<S> selectEpsilonGreedy() {
        // Com probabilidade epsilon, escolhe aleatoriamente (exploração)
        if (random.nextDouble() < explorationFactor) {
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

        return best;
    }

    /**
     * Fornece feedback sobre o resultado da aplicação de um operador.
     *
     * @param operator O operador que foi aplicado
     * @param delta O delta de custo obtido (negativo para melhorias)
     * @param accepted Se a solução foi aceita no Simulated Annealing
     */
    public void feedback(Operator<S> operator, double delta, boolean accepted) {
        totalApplications++;

        // Atualiza contadores
        usageCounts.put(operator, usageCounts.get(operator) + 1);

        // Atribui crédito se houve melhoria ou a solução foi aceita
        if (delta < 0 || accepted) {
            successCounts.put(operator, successCounts.get(operator) + 1);

            // Quanto maior a melhoria (delta mais negativo), maior a recompensa
            double reward = delta < 0 ? -delta : 0.01;
            operator.credit(reward);
            totalRewards.put(operator, totalRewards.get(operator) + reward);
        }
    }

    /**
     * Atualiza as probabilidades de seleção de todos os operadores.
     */
    public void updateWeights() {
        double total = operators.stream().mapToDouble(Operator::getScore).sum();

        // Caso todos os scores sejam zero, distribui igualmente
        if (total <= 0) {
            double equalProb = 1.0 / operators.size();
            for (Operator<S> op : operators) {
                op.setSelectionProbability(equalProb);
            }
            return;
        }

        // Atualiza as probabilidades proporcionalmente aos scores
        for (Operator<S> op : operators) {
            double newP = op.getScore() / total;
            op.setSelectionProbability(newP);
        }
    }

    /**
     * Exibe estatísticas sobre o uso dos operadores.
     */
    public void printStatistics() {
        System.out.println("\n=== Estatísticas de Operadores ===");
        System.out.println("Operador | Uso | Sucesso | Taxa | Recompensa | Probab.");
        System.out.println("-------------------------------------------------");

        for (Operator<S> op : operators) {
            int uses = usageCounts.get(op);
            int successes = successCounts.get(op);
            double rate = uses > 0 ? (double) successes / uses : 0;
            double reward = totalRewards.get(op);

            System.out.printf("%-8s | %3d | %7d | %.2f | %9.2f | %.4f%n",
                    op.getName(), uses, successes, rate, reward, op.getSelectionProbability());
        }
        System.out.println("-------------------------------------------------");
    }

    /**
     * Define a estratégia de seleção.
     *
     * @param strategy A estratégia a ser usada
     */
    public void setStrategy(SelectionStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Define o fator de exploração (usado em UCB e ε-greedy).
     *
     * @param factor Fator de exploração (0.0-1.0)
     */
    public void setExplorationFactor(double factor) {
        this.explorationFactor = factor;
    }
}
