package org.sbpo2025.challenge.SimulatedAnnealing.operators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.sbpo2025.challenge.SimulatedAnnealing.BaseOperator;
import org.sbpo2025.challenge.solution.ChallengeSolution;

/**
 * Implementação do operador Large Neighborhood Search (LNS) para corredores.
 * Este operador remove um conjunto significativo de corredores e os reconstrói
 * utilizando uma heurística gulosa.
 */
public class LNSAisleOperator extends BaseOperator<ChallengeSolution> {

    // Porcentagem de corredores a serem destruídos (removidos)
    private double destructionRate;

    /**
     * Construtor com taxa de destruição configurável.
     *
     * @param destructionRate Taxa de destruição (0.0-1.0)
     */
    public LNSAisleOperator(double destructionRate) {
        super("LNSAisle");
        this.destructionRate = destructionRate;
    }

    /**
     * Construtor padrão com taxa de destruição de 30%.
     */
    public LNSAisleOperator() {
        this(0.3);
    }

    @Override
    public double apply(ChallengeSolution solution) {
        Set<Integer> currentAisles = solution.getAisles();

        // Se não há corredores na solução, retorna falha
        if (currentAisles.isEmpty()) {
            return 0;
        }

        // Salva o custo inicial antes das modificações
        double initialCost = solution.cost();

        // Seleciona aleatoriamente um subconjunto de corredores para remover (destruição)
        List<Integer> aislesList = new ArrayList<>(currentAisles);
        int numToRemove = Math.max(1, (int)(aislesList.size() * destructionRate));
        Set<Integer> aislesToRemove = new HashSet<>();

        while (aislesToRemove.size() < numToRemove && !aislesList.isEmpty()) {
            int randomIndex = random.nextInt(aislesList.size());
            aislesToRemove.add(aislesList.get(randomIndex));
            aislesList.remove(randomIndex);
        }

        // Remove os corredores selecionados
        for (Integer aisleId : aislesToRemove) {
            solution.applyRemoveAisle(aisleId);
        }

        // Fase de reconstrução: adiciona corredores de volta na ordem de benefício
        List<Integer> availableAisles = solution.getInstance().getCorredores().stream()
            .map(c -> c.getId())
            .filter(id -> !solution.getAisles().contains(id))
            .collect(Collectors.toList());

        // Constrói uma lista de pares (corredorId, delta)
        List<AisleDelta> candidateAisles = new ArrayList<>();
        for (Integer aisleId : availableAisles) {
            double delta = solution.calculateAddAisleDelta(aisleId);
            candidateAisles.add(new AisleDelta(aisleId, delta));
        }

        // Ordena por delta (benefício) - valores mais negativos primeiro
        candidateAisles.sort((a, b) -> Double.compare(a.delta, b.delta));

        // Adiciona os melhores candidatos até um certo limite
        int numToAdd = numToRemove;  // Tentamos adicionar a mesma quantidade que removemos
        for (int i = 0; i < Math.min(numToAdd, candidateAisles.size()); i++) {
            solution.applyAddAisle(candidateAisles.get(i).aisleId);
        }

        // Se a solução ficar inviável, aplicamos reparo
        if (!solution.isViable()) {
            solution.repair();
        }

        // Retorna o delta global da operação
        double finalCost = solution.cost();
        return finalCost - initialCost;
    }

    /**
     * Define a taxa de destruição.
     *
     * @param rate Taxa de destruição (0.0-1.0)
     */
    public void setDestructionRate(double rate) {
        if (rate <= 0 || rate >= 1) {
            throw new IllegalArgumentException("Taxa de destruição deve estar entre 0 e 1");
        }
        this.destructionRate = rate;
    }

    /**
     * Classe auxiliar para armazenar pares de (corredorId, delta).
     */
    private static class AisleDelta {
        int aisleId;
        double delta;

        AisleDelta(int aisleId, double delta) {
            this.aisleId = aisleId;
            this.delta = delta;
        }
    }
}
