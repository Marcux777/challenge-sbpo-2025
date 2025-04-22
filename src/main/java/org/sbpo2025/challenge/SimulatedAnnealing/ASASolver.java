package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementação genérica do algoritmo Adaptive Simulated Annealing (ASA).
 * Esta classe encapsula a lógica principal do ASA que pode ser aplicada
 * a diferentes problemas de otimização.
 */
public class ASASolver<SolutionType> {
    private static final Logger log = LoggerFactory.getLogger(ASASolver.class);

    // Parâmetros do algoritmo ASA
    private int maxNoImprovementIterations;
    private int intensificationFrequency;
    private int pathRelinkingFrequency;
    private int eliteUpdateFrequency;
    private double temperatureScaleFactor;
    private long maxRuntime;

    // Estado interno
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

        double currentCost = evaluator.getCost(solution);
        double bestCost = currentCost;
        SolutionType bestSolution = evaluator.copy(solution);

        iteration = 0;
        noImprovementCount = 0;

        // Loop principal do ASA
        while (getRemainingTimeFunc.get() > 0 && noImprovementCount < maxNoImprovementIterations) {
            iteration++;

            // Aplica um movimento aleatório na vizinhança
            boolean moveAccepted = neighborhood.applyRandomMove(solution, ThreadLocalRandom.current());
            if (moveAccepted) {
                currentCost = evaluator.getCost(solution);
            }
            boolean intensificationImproved = false;

            // Atualiza o arquivo de elite periodicamente
            if (iteration % eliteUpdateFrequency == 0) {
                intensifier.updateEliteArchive(solution);
                intensifier.updateEliteArchive(bestSolution);
                if (log.isDebugEnabled()) {
                    log.debug("Iter {}: elite atualizado ({} soluções)", iteration, intensifier.getEliteCount());
                }
            }

            // Aplica busca local focada periodicamente ou quando a busca estagna
            if (iteration % intensificationFrequency == 0 || noImprovementCount > maxNoImprovementIterations / 2) {
                boolean useBestImprovement = noImprovementCount > maxNoImprovementIterations / 2;

                double beforeCost = currentCost;
                SolutionType backup = evaluator.copy(solution); // backup só se necessário
                intensifier.applyFocusedLocalSearch(solution, useBestImprovement); // in-place
                double afterCost = evaluator.getCost(solution);
                currentCost = afterCost;

                if (afterCost > beforeCost) {
                    evaluator.copyFrom(solution, backup);
                    currentCost = beforeCost;
                } else if (afterCost < beforeCost) {
                    if (log.isDebugEnabled()) {
                        log.debug("Iter {}: Busca Local Focada melhorou solução: {} -> {}", iteration, beforeCost, afterCost);
                    }
                    intensificationImproved = true;
                    noImprovementCount = 0;
                }
            }

            // Aplica path relinking periodicamente ou quando a busca estagna
            if ((iteration % pathRelinkingFrequency == 0 || noImprovementCount > maxNoImprovementIterations * 0.7)
                    && intensifier.getEliteCount() >= 2) {
                if (log.isDebugEnabled()) {
                    log.debug("Iter {}: Path Relinking entre soluções elite", iteration);
                }

                SolutionType prSolution = intensifier.applyElitePathRelinking();

                if (prSolution != null) {
                    double prCost = evaluator.getCost(prSolution);
                    if (prCost < currentCost) {
                        double oldCost = currentCost;
                        evaluator.copyFrom(solution, prSolution);
                        currentCost = prCost;
                        if (log.isDebugEnabled()) {
                            log.debug("Iter {}: Path Relinking melhorou a solução: {} -> {}", iteration, oldCost, currentCost);
                        }
                        intensificationImproved = true;
                        noImprovementCount = 0;
                    }
                }
            }

            // Aplica intensificação memética quando a estagnação é alta
            if (noImprovementCount > maxNoImprovementIterations * 0.8 && intensifier.hasEliteSolutions()) {
                if (log.isDebugEnabled()) {
                    log.debug("Iter {}: Intensificação Memética (estagnação alta: {})", iteration, noImprovementCount);
                }

                SolutionType memeticSolution = intensifier.applyMemeticIntensification();

                if (memeticSolution != null) {
                    double memeticCost = evaluator.getCost(memeticSolution);
                    if (memeticCost < currentCost) {
                        double oldCost = currentCost;
                        evaluator.copyFrom(solution, memeticSolution);
                        currentCost = memeticCost;
                        if (log.isDebugEnabled()) {
                            log.debug("Iter {}: Intensificação Memética melhorou a solução: {} -> {}", iteration, oldCost, currentCost);
                        }
                        intensificationImproved = true;
                        noImprovementCount = 0;
                    }
                }
            }

            // Verifica se encontrou uma nova melhor solução
            if (currentCost < bestCost && evaluator.isViable(solution)) {
                bestCost = currentCost;
                evaluator.copyFrom(bestSolution, solution);
                noImprovementCount = 0;
                if (log.isDebugEnabled()) {
                    log.debug("Iter {}: Nova melhor solução: {}", iteration, bestCost);
                }
            } else if (!moveAccepted && !intensificationImproved) {
                noImprovementCount++;

                // Aplica perturbação para escapar de ótimos locais
                if (noImprovementCount % 100 == 0) {
                    neighborhood.applyPerturbation(solution, 0.3);

                    if (!evaluator.isViable(solution)) {
                        evaluator.repair(solution);
                    }
                    currentCost = evaluator.getCost(solution);
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
