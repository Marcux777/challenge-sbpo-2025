package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.commons.lang3.time.StopWatch;

/**
 * Implementação genérica do algoritmo Adaptive Simulated Annealing (ASA).
 * Esta classe encapsula a lógica principal do ASA que pode ser aplicada
 * a diferentes problemas de otimização.
 */
public class ASASolver<SolutionType> {
    // Parâmetros do algoritmo ASA
    private int maxNoImprovementIterations;
    private int intensificationFrequency;
    private int pathRelinkingFrequency;
    private int eliteUpdateFrequency;
    private double temperatureScaleFactor;
    private long maxRuntime;

    // Estado interno
    private final Random random;
    private int noImprovementCount;
    private int iteration;

    // Interfaces funcionais para conectar o algoritmo ao problema específico
    private final Supplier<Long> getRemainingTimeFunc;
    private final Neighborhood<SolutionType> neighborhood;
    private final IntensificationStrategy<SolutionType> intensifier;
    private final SolutionEvaluator<SolutionType> evaluator;

    /**
     * Interface para operações de vizinhança na solução.
     */
    public interface Neighborhood<S> {
        /**
         * Aplica um movimento aleatório na solução.
         * @param solution A solução atual
         * @param random Gerador de números aleatórios
         * @return true se o movimento foi aplicado, false caso contrário
         */
        boolean applyRandomMove(S solution, Random random);

        /**
         * Aplica uma perturbação intensa na solução quando a busca estagna.
         * @param solution A solução atual
         * @param perturbationStrength A intensidade da perturbação (0.0-1.0)
         * @return true se a perturbação foi aplicada com sucesso
         */
        boolean applyPerturbation(S solution, double perturbationStrength);
    }

    /**
     * Interface para avaliação de soluções.
     */
    public interface SolutionEvaluator<S> {
        /**
         * Obtém o custo da solução atual.
         * @param solution A solução
         * @return O valor da função objetivo
         */
        double getCost(S solution);

        /**
         * Verifica se a solução é viável.
         * @param solution A solução
         * @return true se a solução é viável, false caso contrário
         */
        boolean isViable(S solution);

        /**
         * Cria uma cópia da solução.
         * @param solution A solução a ser copiada
         * @return Uma cópia independente da solução
         */
        S copy(S solution);

        /**
         * Copia o conteúdo de uma solução para outra.
         * @param target A solução alvo que receberá o conteúdo
         * @param source A solução fonte
         */
        void copyFrom(S target, S source);

        /**
         * Tenta reparar uma solução inviável.
         * @param solution A solução a ser reparada
         * @return true se a solução foi reparada com sucesso
         */
        boolean repair(S solution);
    }

    /**
     * Construtor para o ASASolver.
     *
     * @param neighborhood Interface para operações de vizinhança
     * @param evaluator Interface para avaliação de soluções
     * @param intensifier Estratégia de intensificação
     * @param getRemainingTimeFunc Função que retorna o tempo restante em segundos
     * @param maxRuntime Tempo máximo de execução em milissegundos
     * @param maxNoImprovementIterations Número máximo de iterações sem melhoria
     */
    public ASASolver(
            Neighborhood<SolutionType> neighborhood,
            SolutionEvaluator<SolutionType> evaluator,
            IntensificationStrategy<SolutionType> intensifier,
            Supplier<Long> getRemainingTimeFunc,
            long maxRuntime,
            int maxNoImprovementIterations) {

        this.neighborhood = neighborhood;
        this.evaluator = evaluator;
        this.intensifier = intensifier;
        this.getRemainingTimeFunc = getRemainingTimeFunc;
        this.maxRuntime = maxRuntime;
        this.maxNoImprovementIterations = maxNoImprovementIterations;

        this.random = new Random();
        this.noImprovementCount = 0;
        this.iteration = 0;

        // Valores padrão para os parâmetros do ASA
        this.intensificationFrequency = 200;
        this.pathRelinkingFrequency = 500;
        this.eliteUpdateFrequency = 50;
        this.temperatureScaleFactor = 0.1;
    }

    /**
     * Configura os parâmetros do algoritmo ASA.
     *
     * @param intensificationFrequency Frequência de aplicação da busca local
     * @param pathRelinkingFrequency Frequência de aplicação do path relinking
     * @param eliteUpdateFrequency Frequência de atualização do arquivo de elite
     * @param temperatureScaleFactor Fator de escala da temperatura
     */
    public void setParameters(
            int intensificationFrequency,
            int pathRelinkingFrequency,
            int eliteUpdateFrequency,
            double temperatureScaleFactor) {

        this.intensificationFrequency = intensificationFrequency;
        this.pathRelinkingFrequency = pathRelinkingFrequency;
        this.eliteUpdateFrequency = eliteUpdateFrequency;
        this.temperatureScaleFactor = temperatureScaleFactor;
    }

    /**
     * Executa o algoritmo ASA a partir de uma solução inicial.
     *
     * @param initialSolution A solução inicial
     * @return A melhor solução encontrada
     */
    public SolutionType solve(SolutionType initialSolution) {
        SolutionType solution = evaluator.copy(initialSolution);

        // Repara a solução inicial se necessário
        if (!evaluator.isViable(solution)) {
            evaluator.repair(solution);
        }

        double initialCost = evaluator.getCost(solution);
        double bestCost = initialCost;
        SolutionType bestSolution = evaluator.copy(solution);

        iteration = 0;
        noImprovementCount = 0;

        // Loop principal do ASA
        while (getRemainingTimeFunc.get() > 0 && noImprovementCount < maxNoImprovementIterations) {
            iteration++;

            // Aplica um movimento aleatório na vizinhança
            boolean moveAccepted = neighborhood.applyRandomMove(solution, random);
            boolean intensificationImproved = false;

            // Atualiza o arquivo de elite periodicamente
            if (iteration % eliteUpdateFrequency == 0) {
                intensifier.updateEliteArchive(solution);
                intensifier.updateEliteArchive(bestSolution);

                System.out.printf("Iteração %d: Arquivo elite atualizado (%d soluções)%n",
                        iteration, intensifier.getEliteCount());
            }

            // Aplica busca local focada periodicamente ou quando a busca estagna
            if (iteration % intensificationFrequency == 0 || noImprovementCount > maxNoImprovementIterations / 2) {
                boolean useBestImprovement = noImprovementCount > maxNoImprovementIterations / 2;

                System.out.printf("Iteração %d: Aplicando Busca Local Focada (modo=%s)%n",
                        iteration, useBestImprovement ? "BEST_IMPROVEMENT" : "FIRST_IMPROVEMENT");

                SolutionType before = evaluator.copy(solution);
                solution = intensifier.applyFocusedLocalSearch(solution, useBestImprovement);

                if (evaluator.getCost(solution) > evaluator.getCost(before)) {
                    solution = before;
                } else if (evaluator.getCost(solution) < evaluator.getCost(before)) {
                    System.out.printf("Iteração %d: Busca Local Focada melhorou solução: %.4f -> %.4f%n",
                            iteration, evaluator.getCost(before), evaluator.getCost(solution));
                    intensificationImproved = true;
                    noImprovementCount = 0;
                }
            }

            // Aplica path relinking periodicamente ou quando a busca estagna
            if ((iteration % pathRelinkingFrequency == 0 || noImprovementCount > maxNoImprovementIterations * 0.7)
                    && intensifier.getEliteCount() >= 2) {
                System.out.printf("Iteração %d: Aplicando Path Relinking entre soluções elite%n", iteration);

                SolutionType prSolution = intensifier.applyElitePathRelinking();

                if (prSolution != null && evaluator.getCost(prSolution) < evaluator.getCost(solution)) {
                    double oldCost = evaluator.getCost(solution);
                    solution = evaluator.copy(prSolution);
                    System.out.printf("Iteração %d: Path Relinking melhorou a solução: %.4f -> %.4f%n",
                            iteration, oldCost, evaluator.getCost(solution));

                    intensificationImproved = true;
                    noImprovementCount = 0;
                }
            }

            // Aplica intensificação memética quando a estagnação é alta
            if (noImprovementCount > maxNoImprovementIterations * 0.8 && intensifier.hasEliteSolutions()) {
                System.out.printf("Iteração %d: Aplicando Intensificação Memética (estagnação alta: %d)%n",
                        iteration, noImprovementCount);

                SolutionType memeticSolution = intensifier.applyMemeticIntensification();

                if (memeticSolution != null && evaluator.getCost(memeticSolution) < evaluator.getCost(solution)) {
                    double oldCost = evaluator.getCost(solution);
                    solution = evaluator.copy(memeticSolution);
                    System.out.printf("Iteração %d: Intensificação Memética melhorou a solução: %.4f -> %.4f%n",
                            iteration, oldCost, evaluator.getCost(solution));

                    intensificationImproved = true;
                    noImprovementCount = 0;
                }
            }

            // Verifica se encontrou uma nova melhor solução
            if (evaluator.getCost(solution) < bestCost && evaluator.isViable(solution)) {
                bestCost = evaluator.getCost(solution);
                evaluator.copyFrom(bestSolution, solution);
                noImprovementCount = 0;

                System.out.printf("Nova melhor solução: %.2f%n", bestCost);
            } else if (!moveAccepted && !intensificationImproved) {
                noImprovementCount++;

                // Aplica perturbação para escapar de ótimos locais
                if (noImprovementCount % 100 == 0) {
                    neighborhood.applyPerturbation(solution, 0.3);

                    if (!evaluator.isViable(solution)) {
                        evaluator.repair(solution);
                    }
                }
            }
        }

        // Imprime estatísticas finais do intensificador
        intensifier.printStatistics();

        return bestSolution;
    }

    /**
     * Retorna o número da iteração atual.
     */
    public int getCurrentIteration() {
        return iteration;
    }

    /**
     * Retorna o número de iterações sem melhoria.
     */
    public int getNoImprovementCount() {
        return noImprovementCount;
    }
}
