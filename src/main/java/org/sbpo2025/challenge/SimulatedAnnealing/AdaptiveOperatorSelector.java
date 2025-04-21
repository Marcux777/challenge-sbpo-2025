package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import org.sbpo2025.challenge.SimulatedAnnealing.operators.*;
import org.sbpo2025.challenge.solution.ChallengeSolution;
import org.sbpo2025.challenge.SimulatedAnnealing.SelectionStrategy;
import org.sbpo2025.challenge.SimulatedAnnealing.Ucb1Strategy;
import org.sbpo2025.challenge.SimulatedAnnealing.EpsilonGreedyStrategy;
import org.sbpo2025.challenge.SimulatedAnnealing.RouletteWheelStrategy;

/**
 * Implementação da Dinâmica Avançada de Operadores para ASA.
 * Esta classe gerencia um conjunto de operadores de vizinhança e
 * seleciona adaptativamente quais operadores usar com base em seu
 * desempenho recente.
 */
public class AdaptiveOperatorSelector implements ASASolver.Neighborhood<ChallengeSolution> {

    // Seletor de operadores
    private OperatorSelector<ChallengeSolution> selector;

    // Lista de operadores disponíveis
    private CopyOnWriteArrayList<Operator<ChallengeSolution>> operators;

    // Gerador de números aleatórios
    private Random random;

    // Configurações
    private int updateFrequency = 100;  // Atualiza probabilidades a cada N aplicações
    private int totalApplications = 0;

    /**
     * Construtor padrão que cria todos os operadores básicos.
     */
    public AdaptiveOperatorSelector() {
        this.random = new Random();
        this.operators = new CopyOnWriteArrayList<>();

        // Inicializa os operadores básicos
        initializeOperators();

        // Cria o seletor de operadores
        this.selector = new OperatorSelector<>(operators);

        // Configura a estratégia de seleção e fatores de exploração
        // Substituir enum antigo por uso de classes de estratégia
        // Exemplo de uso:
        // selector.setStrategy(new Ucb1Strategy<>(Math.sqrt(2.0)));
        selector.setUcbExplorationFactor(Math.sqrt(2.0)); // Valor padrão para UCB1
        selector.setEpsilonExplorationFactor(0.1); // Valor padrão para Epsilon-Greedy
    }

    /**
     * Inicializa todos os operadores disponíveis.
     */
    private void initializeOperators() {
        // Operadores básicos
        operators.add(new AddOrderOperator());
        operators.add(new RemoveOrderOperator());
        operators.add(new AddAisleOperator());
        operators.add(new RemoveAisleOperator());
        operators.add(new SwapAisleOperator());
        operators.add(new SwapOrderOperator());  // Adicionado o novo operador de troca de pedidos

        // Operadores avançados
        operators.add(new LNSOrderOperator(0.3));
        operators.add(new LNSAisleOperator(0.3));
        operators.add(new MultiSwapAisleOperator(2));
        operators.add(new TwoOptOperator());

        // Operador especializado com foco na função objetivo
        operators.add(new ObjectiveFocusedOperator(0.2));
    }

    @Override
    public boolean applyRandomMove(ChallengeSolution solution, Random random) {
        // Seleciona um operador adaptativamente
        Operator<ChallengeSolution> selectedOperator = selector.select();

        // Salva o custo antes de aplicar o operador
        double initialCost = solution.cost();

        try {
            // Aplica o operador selecionado
            double delta = selectedOperator.apply(solution);

            // Se houve melhoria ou piorou menos que um threshold, consideramos sucesso
            boolean accepted = delta < 0 ||
                              (delta > 0 && random.nextDouble() < Math.exp(-delta / (solution.cost() * 0.1)));

            // Fornece feedback ao seletor sobre o desempenho do operador
            selector.feedback(selectedOperator, delta, accepted);

            // Incrementa contador de aplicações
            totalApplications++;

            // Atualiza as probabilidades periodicamente
            if (totalApplications % updateFrequency == 0) {
                selector.updateWeights();
                selector.printStatistics();
            }

            // Retornamos verdadeiro se houve mudança na solução
            return initialCost != solution.cost();

        } catch (Exception e) {
            System.err.println("Erro ao aplicar operador " + selectedOperator.getName() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean applyPerturbation(ChallengeSolution solution, double perturbationStrength) {
        // Para perturbações mais fortes, usamos LNS com taxa de destruição maior
        LNSOrderOperator lnsOrder = new LNSOrderOperator(perturbationStrength);
        LNSAisleOperator lnsAisle = new LNSAisleOperator(perturbationStrength);

        // Aplica os operadores LNS consecutivamente
        boolean changed1 = lnsOrder.apply(solution) != 0;
        boolean changed2 = lnsAisle.apply(solution) != 0;

        // Repara a solução se necessário
        if (!solution.isViable()) {
            solution.repair();
        }

        return changed1 || changed2;
    }

    /**
     * Define a frequência de atualização das probabilidades de seleção.
     *
     * @param frequency Número de aplicações entre atualizações
     */
    public void setUpdateFrequency(int frequency) {
        if (frequency <= 0) {
            throw new IllegalArgumentException("Frequência deve ser positiva");
        }
        this.updateFrequency = frequency;
    }

    /**
     * Define a estratégia de seleção de operadores.
     *
     * @param strategy Estratégia de seleção
     */
    public void setSelectionStrategy(SelectionStrategy strategy) {
        this.selector.setStrategy(strategy);
    }

    /**
     * Define o fator de exploração para UCB1.
     *
     * @param factor Fator de exploração (e.g., sqrt(2.0))
     */
    public void setUcbExplorationFactor(double factor) {
        this.selector.setUcbExplorationFactor(factor);
    }

    /**
     * Define o fator de exploração (epsilon) para ε-greedy.
     *
     * @param factor Fator de exploração (0.0 a 1.0)
     */
    public void setEpsilonExplorationFactor(double factor) {
        this.selector.setEpsilonExplorationFactor(factor);
    }

    /**
     * Imprime estatísticas sobre o uso dos operadores.
     */
    public void printStatistics() {
        selector.printStatistics();
    }
}
