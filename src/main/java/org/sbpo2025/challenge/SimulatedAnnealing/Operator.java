package org.sbpo2025.challenge.SimulatedAnnealing;

/**
 * Interface para operadores de vizinhança com avaliação adaptativa.
 * Esta interface permite que operadores sejam avaliados e selecionados
 * dinamicamente com base em seu desempenho recente.
 *
 * @param <S> Tipo da solução sobre a qual o operador atua
 */
public interface Operator<S> {

    /**
     * Aplica o operador na solução dada.
     *
     * @param solution A solução atual
     * @return O delta de custo (positivo se piorou, negativo se melhorou)
     */
    double apply(S solution);

    /**
     * Acumula recompensa/crédito para este operador com base no seu desempenho.
     *
     * @param reward A recompensa a ser concedida (normalmente proporcional à melhoria)
     */
    void credit(double reward);

    /**
     * Obtém a pontuação atual deste operador para seleção adaptativa.
     *
     * @return O score de seleção atual
     */
    double getScore();

    /**
     * Define a probabilidade de seleção deste operador.
     *
     * @param probability Nova probabilidade de seleção
     */
    void setSelectionProbability(double probability);

    /**
     * Obtém a probabilidade de seleção atual.
     *
     * @return Probabilidade de seleção
     */
    double getSelectionProbability();

    /**
     * Obtém o nome do operador para exibição e logging.
     *
     * @return Nome do operador
     */
    String getName();

    /**
     * Retorna a soma acumulada das recompensas recebidas.
     */
    double getSumRewards();

    /**
     * Retorna o número de aplicações (feedbacks recebidos).
     */
    int getCountApplications();

    /**
     * Retorna a recompensa média acumulada (score normalizado para seleção).
     */
    double getMeanReward();
}
