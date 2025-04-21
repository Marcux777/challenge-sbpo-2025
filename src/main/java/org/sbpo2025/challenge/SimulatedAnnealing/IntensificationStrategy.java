package org.sbpo2025.challenge.SimulatedAnnealing;

/**
 * Interface genérica para estratégias de intensificação usadas pelo ASASolver.
 * Define métodos para busca local focada, path relinking e intensificação memética.
 */
public interface IntensificationStrategy<SolutionType> {

    /**
     * Atualiza o arquivo de elite com uma nova solução.
     *
     * @param solution A solução candidata para o arquivo de elite
     */
    void updateEliteArchive(SolutionType solution);

    /**
     * Aplica busca local focada em uma solução.
     *
     * @param solution A solução a ser melhorada
     * @param useBestImprovement Se verdadeiro, usa estratégia de melhor melhoria; caso contrário, primeira melhoria
     * @return A solução melhorada após busca local
     */
    SolutionType applyFocusedLocalSearch(SolutionType solution, boolean useBestImprovement);

    /**
     * Aplica path relinking entre soluções de elite.
     *
     * @return Uma nova solução gerada via path relinking ou null se não for possível
     */
    SolutionType applyElitePathRelinking();

    /**
     * Aplica intensificação memética quando a busca estagna.
     *
     * @return Uma nova solução gerada via intensificação memética ou null se não for possível
     */
    SolutionType applyMemeticIntensification();

    /**
     * Verifica se existem soluções de elite disponíveis.
     *
     * @return true se o arquivo de elite contém soluções, false caso contrário
     */
    boolean hasEliteSolutions();

    /**
     * Retorna o número de soluções no arquivo de elite.
     *
     * @return Quantidade de soluções de elite
     */
    int getEliteCount();

    /**
     * Imprime estatísticas sobre as estratégias de intensificação.
     */
    void printStatistics();
}
