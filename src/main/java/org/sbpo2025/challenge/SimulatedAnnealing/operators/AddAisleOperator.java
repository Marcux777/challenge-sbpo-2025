package org.sbpo2025.challenge.SimulatedAnnealing.operators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.sbpo2025.challenge.SimulatedAnnealing.BaseOperator;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Operador que adiciona um corredor aleatório à solução.
 */
public class AddAisleOperator extends BaseOperator<ChallengeSolution> {

    /**
     * Construtor padrão.
     */
    public AddAisleOperator() {
        super("AddAisle");
    }

    @Override
    public double apply(ChallengeSolution solution) {
        Set<Integer> currentAisles = solution.getAisles();

        // Coleta corredores disponíveis para adicionar
        List<Integer> availableAisles = solution.getInstance().getCorredores().stream()
            .map(c -> c.getId())
            .filter(id -> !currentAisles.contains(id))
            .collect(Collectors.toList());

        // Se não há corredores disponíveis, retorna falha
        if (availableAisles.isEmpty()) {
            return 0;
        }

        // Seleciona aleatoriamente um corredor para adicionar
        int aisleToAdd = availableAisles.get(random.nextInt(availableAisles.size()));

        // Calcula o impacto antes de aplicar a mudança
        double delta = solution.calculateAddAisleDelta(aisleToAdd);

        // Aplica a mudança
        solution.applyAddAisle(aisleToAdd);

        return delta;
    }
}
