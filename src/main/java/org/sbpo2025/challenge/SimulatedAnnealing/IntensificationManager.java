package org.sbpo2025.challenge.SimulatedAnnealing;

import org.sbpo2025.challenge.solution.ChallengeSolution;
import java.util.ArrayList;
import java.util.List;

/**
 * Gerenciador de técnicas de intensificação para o Simulated Annealing.
 * Coordena a aplicação de diferentes estratégias de busca local intensa
 * e mantém um arquivo de soluções elites para intensificação memética.
 */
public class IntensificationManager implements IntensificationStrategy<ChallengeSolution> {

    // Parâmetros para técnicas de intensificação
    private final int eliteSize; // Tamanho do arquivo elite
    private final int tabuTenure; // Tamanho da lista tabu
    private final int maxTabuIterations; // Máximo de iterações da busca tabu

    // Componentes das técnicas de intensificação
    private final MemeticHybrid memeticHybrid;

    // Contadores para estatísticas
    private int flsApplied = 0; // Contador de aplicações de Focused Local Search
    private int flsImproved = 0; // Contador de melhorias via Focused Local Search
    private int mhApplied = 0; // Contador de aplicações de Memetic Hybrid
    private int mhImproved = 0; // Contador de melhorias via Memetic Hybrid
    private int prApplied = 0; // Contador de aplicações de Path Relinking
    private int prImproved = 0; // Contador de melhorias via Path Relinking

    /**
     * Construtor com parâmetros padrão.
     */
    public IntensificationManager() {
        this(5, 10, 100);
    }

    /**
     * Construtor com parâmetros configuráveis.
     *
     * @param eliteSize Tamanho do arquivo elite
     * @param tabuTenure Tamanho da lista tabu
     * @param maxTabuIterations Máximo de iterações da busca tabu
     */
    public IntensificationManager(int eliteSize, int tabuTenure, int maxTabuIterations) {
        this.eliteSize = eliteSize;
        this.tabuTenure = tabuTenure;
        this.maxTabuIterations = maxTabuIterations;

        // Inicializa o componente Memetic Hybrid
        this.memeticHybrid = new MemeticHybrid(eliteSize, tabuTenure, maxTabuIterations);
    }

    /**
     * Aplica Busca Local Focada (Focused Local Search) à solução.
     * Implementação do método definido em IntensificationStrategy.
     *
     * @param solution A solução a ser intensificada
     * @param useBestImprovement true para usar best-improvement, false para first-improvement
     * @return A solução melhorada, ou a original se não houver melhoria
     */
    @Override
    public ChallengeSolution applyFocusedLocalSearch(ChallengeSolution solution, boolean useBestImprovement) {
        flsApplied++;

        // Seleciona o modo da busca local
        FocusedLocalSearch.Mode mode = useBestImprovement ?
            FocusedLocalSearch.Mode.BEST_IMPROVEMENT :
            FocusedLocalSearch.Mode.FIRST_IMPROVEMENT;

        // Guarda o custo original para comparação
        double originalCost = solution.cost();

        // Aplica a Busca Local Focada
        ChallengeSolution improved = FocusedLocalSearch.apply(solution, mode);

        // Verifica se houve melhoria
        if (improved.cost() < originalCost) {
            flsImproved++;
            return improved;
        }

        return solution;
    }

    /**
     * Atualiza o arquivo de soluções elite com uma nova solução.
     * Implementação do método definido em IntensificationStrategy.
     *
     * @param solution A solução a ser considerada para o arquivo elite
     */
    @Override
    public void updateEliteArchive(ChallengeSolution solution) {
        memeticHybrid.updateElite(solution);
    }

    /**
     * Aplica Intensificação Memética (Memetic Hybrid) usando o arquivo elite.
     * Implementação do método definido em IntensificationStrategy.
     *
     * @return A melhor solução refinada, ou null se o arquivo elite estiver vazio
     */
    @Override
    public ChallengeSolution applyMemeticIntensification() {
        mhApplied++;

        ChallengeSolution improved = memeticHybrid.intensify();
        if (improved != null) {
            mhImproved++;
        }

        return improved;
    }

    /**
     * Aplica Path Relinking entre duas soluções de boa qualidade.
     *
     * @param originSolution A solução de origem
     * @param guideSolution A solução guia
     * @return A melhor solução encontrada no caminho entre as duas soluções
     */
    public ChallengeSolution applyPathRelinking(ChallengeSolution originSolution, ChallengeSolution guideSolution) {
        prApplied++;

        double originCost = originSolution.cost();
        ChallengeSolution result = FocusedLocalSearch.applyPathRelinking(originSolution, guideSolution);

        // Verifica se houve melhoria
        if (result.cost() < originCost) {
            prImproved++;
        }

        return result;
    }

    /**
     * Aplica Path Relinking entre múltiplas soluções elite.
     * Realiza Path Relinking entre pares selecionados de soluções do arquivo elite.
     * Implementação do método definido em IntensificationStrategy.
     *
     * @return A melhor solução encontrada após aplicar Path Relinking
     */
    @Override
    public ChallengeSolution applyElitePathRelinking() {
        if (memeticHybrid.getEliteCount() < 2) {
            return null; // Precisa de pelo menos duas soluções para Path Relinking
        }

        // Obtém soluções do arquivo elite
        List<ChallengeSolution> eliteSolutions = getEliteSolutions();

        // Seleciona a melhor solução como referência
        ChallengeSolution bestSolution = eliteSolutions.get(0).copy();
        double bestCost = bestSolution.cost();

        // Aplica Path Relinking entre pares de soluções elite
        for (int i = 0; i < eliteSolutions.size() - 1; i++) {
            for (int j = i + 1; j < eliteSolutions.size(); j++) {
                // Path Relinking nos dois sentidos
                ChallengeSolution pr1 = applyPathRelinking(eliteSolutions.get(i), eliteSolutions.get(j));
                ChallengeSolution pr2 = applyPathRelinking(eliteSolutions.get(j), eliteSolutions.get(i));

                // Atualiza a melhor solução encontrada
                if (pr1.cost() < bestCost) {
                    bestSolution = pr1;
                    bestCost = pr1.cost();
                }

                if (pr2.cost() < bestCost) {
                    bestSolution = pr2;
                    bestCost = pr2.cost();
                }
            }
        }

        // Adiciona a melhor solução encontrada ao arquivo elite
        memeticHybrid.updateElite(bestSolution);

        return bestSolution;
    }

    /**
     * Obtém a lista de soluções do arquivo elite.
     *
     * @return Lista das soluções elite
     */
    public List<ChallengeSolution> getEliteSolutions() {
        List<ChallengeSolution> solutions = new ArrayList<>();

        for (int i = 0; i < memeticHybrid.getEliteCount(); i++) {
            ChallengeSolution elite = memeticHybrid.getEliteSolution(i);
            if (elite != null) {
                solutions.add(elite);
            }
        }

        return solutions;
    }

    /**
     * Verifica se o arquivo elite contém soluções.
     * Implementação do método definido em IntensificationStrategy.
     *
     * @return true se o arquivo elite não estiver vazio
     */
    @Override
    public boolean hasEliteSolutions() {
        return !memeticHybrid.isEmpty();
    }

    /**
     * Obtém o número de soluções no arquivo elite.
     * Implementação do método definido em IntensificationStrategy.
     *
     * @return Contagem de soluções elite
     */
    @Override
    public int getEliteCount() {
        return memeticHybrid.getEliteCount();
    }

    /**
     * Obtém a melhor solução do arquivo elite.
     *
     * @return A melhor solução do arquivo elite, ou null se vazio
     */
    public ChallengeSolution getBestEliteSolution() {
        return memeticHybrid.getBestElite();
    }

    /**
     * Imprime estatísticas de uso das técnicas de intensificação.
     * Implementação do método definido em IntensificationStrategy.
     */
    @Override
    public void printStatistics() {
        System.out.println("Estatísticas de Intensificação:");
        System.out.printf("  Busca Local Focada: %d aplicações, %d melhorias (%.1f%%)%n",
                        flsApplied, flsImproved,
                        flsApplied > 0 ? 100.0 * flsImproved / flsApplied : 0.0);
        System.out.printf("  Path Relinking: %d aplicações, %d melhorias (%.1f%%)%n",
                        prApplied, prImproved,
                        prApplied > 0 ? 100.0 * prImproved / prApplied : 0.0);
        System.out.printf("  Intensificação Memética: %d aplicações, %d melhorias (%.1f%%)%n",
                        mhApplied, mhImproved,
                        mhApplied > 0 ? 100.0 * mhImproved / mhApplied : 0.0);
        System.out.printf("  Tamanho do arquivo elite: %d/%d%n",
                        memeticHybrid.getEliteCount(), eliteSize);
    }
}
