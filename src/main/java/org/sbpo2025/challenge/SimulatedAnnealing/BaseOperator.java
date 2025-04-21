package org.sbpo2025.challenge.SimulatedAnnealing;

import java.util.Random;

/**
 * Implementação base para operadores adaptativos.
 * Esta classe implementa funcionalidades comuns a todos os operadores.
 *
 * @param <S> Tipo da solução sobre a qual o operador atua
 */
public abstract class BaseOperator<S> implements Operator<S> {

    // Nome do operador
    private final String name;

    // Score acumulado e probabilidade de seleção
    private double score;
    private double selectionProbability;

    // Parâmetros de decaimento do score (para dar mais peso a resultados recentes)
    private double decayFactor = 0.95;

    // Gerador de números aleatórios
    protected final Random random;

    /**
     * Construtor padrão.
     *
     * @param name Nome do operador
     */
    public BaseOperator(String name) {
        this.name = name;
        this.score = 1.0;  // Score inicial
        this.selectionProbability = 0.0;
        this.random = new Random();
    }

    @Override
    public void credit(double reward) {
        // Aplica decaimento ao score anterior e adiciona a nova recompensa
        score = score * decayFactor + reward;
    }

    @Override
    public double getScore() {
        return score;
    }

    @Override
    public void setSelectionProbability(double probability) {
        this.selectionProbability = probability;
    }

    @Override
    public double getSelectionProbability() {
        return selectionProbability;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Define o fator de decaimento para o score.
     *
     * @param factor Valor entre 0 e 1 (valores mais próximos de 1 resultam em decaimento mais lento)
     */
    public void setDecayFactor(double factor) {
        if (factor < 0 || factor > 1) {
            throw new IllegalArgumentException("Fator de decaimento deve estar entre 0 e 1");
        }
        this.decayFactor = factor;
    }
}
