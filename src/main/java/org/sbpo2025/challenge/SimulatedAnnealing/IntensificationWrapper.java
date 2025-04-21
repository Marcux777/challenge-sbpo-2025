package org.sbpo2025.challenge.SimulatedAnnealing;

import org.sbpo2025.challenge.solution.ChallengeSolution;
import java.util.*;

/**
 * Wrapper para as técnicas de intensificação usadas na otimização.
 * Esta classe fornece uma interface simplificada para aplicar diversas
 * estratégias de intensificação a uma solução.
 */
public class IntensificationWrapper {

    // Gerenciador de técnicas de intensificação
    private IntensificationManager intensifier;

    // Configurações básicas
    private final int eliteSize;
    private final int tabuTenure;
    private final int maxTabuIterations;

    /**
     * Construtor simplificado.
     */
    public IntensificationWrapper() {
        this(5, 10, 100);
    }

    /**
     * Construtor com parâmetros configuráveis para intensificação.
     *
     * @param eliteSize Tamanho do arquivo de soluções elite
     * @param tabuTenure Tamanho da lista tabu
     * @param maxIterations Número máximo de iterações para busca tabu
     */
    public IntensificationWrapper(int eliteSize, int tabuTenure, int maxIterations) {
        this.eliteSize = eliteSize;
        this.tabuTenure = tabuTenure;
        this.maxTabuIterations = maxIterations;
        this.intensifier = new IntensificationManager(eliteSize, tabuTenure, maxIterations);
    }

    /**
     * Método que aplica uma sequência de técnicas de intensificação.
     *
     * @param solution Solução inicial a ser intensificada
     * @return Solução intensificada
     */
    public ChallengeSolution intensify(ChallengeSolution solution) {
        // Verifica se a solução é viável
        if (!solution.isViable()) {
            solution.repair();
        }

        // Registra a solução no arquivo elite
        intensifier.updateEliteArchive(solution);

        // Aplica Busca Local Focada com estratégia de best-improvement
        ChallengeSolution improved = intensifier.applyFocusedLocalSearch(solution, true);

        // Se já tiver pelo menos duas soluções no arquivo elite, aplica Path Relinking
        if (intensifier.getEliteCount() >= 2) {
            // Aplica Path Relinking entre solução atual e a melhor elite
            ChallengeSolution bestElite = intensifier.getBestEliteSolution();
            ChallengeSolution prSolution = intensifier.applyPathRelinking(improved, bestElite);

            // Usa a melhor das soluções
            if (prSolution.cost() < improved.cost()) {
                improved = prSolution;
            }

            // Aplica Path Relinking entre pares de soluções elite
            ChallengeSolution elitePr = intensifier.applyElitePathRelinking();
            if (elitePr != null && elitePr.cost() < improved.cost()) {
                improved = elitePr;
            }
        }

        // Aplica Intensificação Memética se houver soluções elite
        if (intensifier.hasEliteSolutions()) {
            ChallengeSolution memeticSolution = intensifier.applyMemeticIntensification();
            if (memeticSolution != null && memeticSolution.cost() < improved.cost()) {
                improved = memeticSolution;
            }
        }

        // Exibe estatísticas
        intensifier.printStatistics();

        return improved;
    }

    /**
     * Obtém o gerenciador de intensificação.
     *
     * @return O gerenciador de intensificação
     */
    public IntensificationManager getIntensificationManager() {
        return intensifier;
    }
}
