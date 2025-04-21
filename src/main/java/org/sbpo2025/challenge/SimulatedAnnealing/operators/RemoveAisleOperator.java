package org.sbpo2025.challenge.SimulatedAnnealing.operators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.sbpo2025.challenge.SimulatedAnnealing.BaseOperator;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Operador que remove um corredor aleatório da solução.
 */
public class RemoveAisleOperator extends BaseOperator<ChallengeSolution> {

    /**
     * Construtor padrão.
     */
    public RemoveAisleOperator() {
        super("RemAisle");
    }

    @Override
    public double apply(ChallengeSolution solution) {
        Set<Integer> currentAisles = solution.getAisles();

        // Se não há corredores para remover, retorna falha
        if (currentAisles.isEmpty()) {
            return 0;
        }

        // Seleciona aleatoriamente um corredor para remover
        List<Integer> aislesList = new ArrayList<>(currentAisles);
        int aisleToRemove = aislesList.get(random.nextInt(aislesList.size()));

        // Calcula o impacto antes de aplicar a mudança
        double delta = solution.calculateRemoveAisleDelta(aisleToRemove);

        // Aplica a mudança
        solution.applyRemoveAisle(aisleToRemove);

        // Repara a solução se necessário
        if (!solution.isViable()) {
            solution.repair();
        }

        return delta;
    }
}
