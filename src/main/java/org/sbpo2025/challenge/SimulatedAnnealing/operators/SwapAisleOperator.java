package org.sbpo2025.challenge.SimulatedAnnealing.operators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.sbpo2025.challenge.SimulatedAnnealing.BaseOperator;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Operador que troca um corredor da solução por outro corredor não presente.
 */
public class SwapAisleOperator extends BaseOperator<ChallengeSolution> {

    /**
     * Construtor padrão.
     */
    public SwapAisleOperator() {
        super("SwpAisle");
    }

    @Override
    public double apply(ChallengeSolution solution) {
        Set<Integer> currentAisles = solution.getAisles();

        // Se não há corredores na solução, retorna falha
        if (currentAisles.isEmpty()) {
            return 0;
        }

        // Coleta corredores disponíveis para adicionar
        List<Integer> availableAisles = solution.getInstance().getCorredores().stream()
            .map(c -> c.getId())
            .filter(id -> !currentAisles.contains(id))
            .collect(Collectors.toList());

        // Se não há corredores disponíveis, retorna falha
        if (availableAisles.isEmpty()) {
            return 0;
        }

        // Seleciona aleatoriamente um corredor para remover e um para adicionar
        List<Integer> aislesList = new ArrayList<>(currentAisles);
        int aisleToRemove = aislesList.get(random.nextInt(aislesList.size()));
        int aisleToAdd = availableAisles.get(random.nextInt(availableAisles.size()));

        // Calcula o impacto antes de aplicar a mudança
        double delta = solution.calculateSwapAisleDelta(aisleToRemove, aisleToAdd);

        // Aplica a mudança
        solution.applyRemoveAisle(aisleToRemove);
        solution.applyAddAisle(aisleToAdd);

        // Repara a solução se necessário
        if (!solution.isViable()) {
            solution.repair();
        }

        return delta;
    }
}
