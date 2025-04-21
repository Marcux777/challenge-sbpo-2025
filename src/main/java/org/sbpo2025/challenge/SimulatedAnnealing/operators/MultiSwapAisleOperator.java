package org.sbpo2025.challenge.SimulatedAnnealing.operators;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.sbpo2025.challenge.SimulatedAnnealing.BaseOperator;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Operador baseado em VNS (Variable Neighborhood Search) que realiza múltiplas
 * trocas de corredores em uma única operação.
 */
public class MultiSwapAisleOperator extends BaseOperator<ChallengeSolution> {

    // Número de trocas a realizar
    private int swapCount;

    /**
     * Construtor com número configurável de trocas.
     *
     * @param swapCount Número de trocas a realizar
     */
    public MultiSwapAisleOperator(int swapCount) {
        super("MltSwap");
        this.swapCount = swapCount;
    }

    /**
     * Construtor padrão que realiza 2 trocas.
     */
    public MultiSwapAisleOperator() {
        this(2);
    }

    @Override
    public double apply(ChallengeSolution solution) {
        // Verifica se é possível realizar as trocas (há corredores suficientes)
        Set<Integer> currentAisles = solution.getAisles();
        if (currentAisles.size() < swapCount) {
            return 0;
        }

        // Lista de corredores disponíveis para adicionar
        List<Integer> availableAisles = solution.getInstance().getCorredores().stream()
            .map(c -> c.getId())
            .filter(id -> !currentAisles.contains(id))
            .collect(Collectors.toList());

        if (availableAisles.size() < swapCount) {
            return 0;
        }

        // Salva o custo inicial
        double initialCost = solution.cost();

        // Seleciona aleatoriamente 'swapCount' corredores para remover
        List<Integer> aislesList = new ArrayList<>(currentAisles);
        List<Integer> aislesToRemove = new ArrayList<>();

        for (int i = 0; i < swapCount; i++) {
            int randomIndex = random.nextInt(aislesList.size());
            aislesToRemove.add(aislesList.get(randomIndex));
            aislesList.remove(randomIndex);
        }

        // Seleciona aleatoriamente 'swapCount' corredores para adicionar
        List<Integer> aislesToAdd = new ArrayList<>();
        for (int i = 0; i < swapCount; i++) {
            int randomIndex = random.nextInt(availableAisles.size());
            aislesToAdd.add(availableAisles.get(randomIndex));
            availableAisles.remove(randomIndex);
        }

        // Aplica as trocas
        for (Integer aisleId : aislesToRemove) {
            solution.applyRemoveAisle(aisleId);
        }

        for (Integer aisleId : aislesToAdd) {
            solution.applyAddAisle(aisleId);
        }

        // Repara a solução se necessário
        if (!solution.isViable()) {
            solution.repair();
        }

        // Retorna o delta global
        double finalCost = solution.cost();
        return finalCost - initialCost;
    }

    /**
     * Define o número de trocas a realizar.
     *
     * @param count Número de trocas
     */
    public void setSwapCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Número de trocas deve ser positivo");
        }
        this.swapCount = count;
    }
}
