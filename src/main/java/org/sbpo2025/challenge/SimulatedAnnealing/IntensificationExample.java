package org.sbpo2025.challenge.SimulatedAnnealing;

import org.sbpo2025.challenge.solution.ChallengeSolution;
import org.sbpo2025.challenge.model.*;
import java.util.*;

/**
 * Classe de demonstração do uso das técnicas de intensificação implementadas.
 * Mostra um pseudo-código de como integrar as técnicas em um algoritmo principal.
 */
public class IntensificationExample {

    /**
     * Exemplo de como integrar as técnicas de intensificação em um algoritmo.
     *
     * @param initialSolution Solução inicial para o processo de otimização
     * @param maxIterations Número máximo de iterações
     * @return A melhor solução encontrada após otimização
     */
    public static ChallengeSolution optimize(ChallengeSolution initialSolution, int maxIterations) {
        // 1. Inicialização
        ChallengeSolution currentSolution = initialSolution.copy();
        ChallengeSolution bestSolution = currentSolution.copy();
        Random random = new Random();

        // 2. Cria o gerenciador de intensificação
        IntensificationManager intensifier = new IntensificationManager(5, 10, 100);

        // 3. Parâmetros para controle de aplicação das técnicas
        int flsFrequency = 50;      // A cada quantas iterações aplicar Busca Local Focada
        int mhFrequency = 10;       // A cada quantas "ondas" aplicar Memetic Hybrid
        int waveSize = 100;         // Tamanho da "onda" em iterações
        double initialTemperature = 1.0;
        double coolingRate = 0.99;
        double temperature = initialTemperature;

        // 4. Loop principal de otimização
        for (int iter = 1; iter <= maxIterations; iter++) {
            // 4.1 Aplica movimentos convencionais
            ChallengeSolution neighbor = currentSolution.copy();

            // Aplica perturbação na solução (movimento aleatório)
            // Em um algoritmo real, você usaria operadores específicos do problema
            neighbor.perturb(0, 0.1);

            // Garante que a solução é viável
            if (!neighbor.isViable()) {
                neighbor.repair();
            }

            // 4.2 Decide se aceita o movimento (critério de Metropolis)
            double deltaCost = neighbor.cost() - currentSolution.cost();
            boolean accept = false;

            if (deltaCost <= 0) { // Aceita movimentos de melhoria
                accept = true;
            } else if (temperature > 1e-9) { // Aceita movimentos de piora com probabilidade
                double acceptProb = Math.exp(-deltaCost / temperature);
                accept = random.nextDouble() < acceptProb;
            }

            if (accept) {
                currentSolution = neighbor;
            }

            // 4.3 Atualiza a melhor solução encontrada
            if (currentSolution.cost() < bestSolution.cost() && currentSolution.isViable()) {
                bestSolution = currentSolution.copy();
                System.out.printf("Iter %d: Nova melhor solução com custo = %.4f%n",
                                 iter, bestSolution.cost());
            }

            // 4.4 INTENSIFICAÇÃO: Aplica técnicas de intensificação

            // A. Busca Local Focada - Periodicamente ou em temperaturas baixas
            if (iter % flsFrequency == 0 || temperature < 0.05) {
                // Decide qual estratégia usar: best ou first improvement
                boolean useBestImprovement = temperature < 0.01; // Em baixas temperaturas, usa best-improvement

                System.out.printf("Iter %d: Aplicando Busca Local Focada (modo=%s)%n",
                                 iter, useBestImprovement ? "BEST_IMPROVEMENT" : "FIRST_IMPROVEMENT");

                // Aplica a técnica e registra se houve melhoria
                ChallengeSolution before = currentSolution.copy();
                currentSolution = intensifier.applyFocusedLocalSearch(currentSolution, useBestImprovement);

                if (currentSolution.cost() < before.cost()) {
                    System.out.printf("Iter %d: FLS melhorou a solução: %.4f -> %.4f%n",
                                     iter, before.cost(), currentSolution.cost());
                }
            }

            // B. Atualização do arquivo elite - A cada 100 iterações
            if (iter % 100 == 0) {
                intensifier.updateEliteArchive(currentSolution);
                // Também adiciona a melhor solução global conhecida
                intensifier.updateEliteArchive(bestSolution);
            }

            // C. Path Relinking entre solução atual e melhores do arquivo elite
            // A cada 200 iterações ou em temperatura muito baixa
            if (iter % 200 == 0 || temperature < 0.01) {
                if (intensifier.hasEliteSolutions()) {
                    System.out.printf("Iter %d: Aplicando Path Relinking entre solução atual e melhor elite%n", iter);

                    // Obtém a melhor solução elite como guia
                    ChallengeSolution guideSolution = intensifier.getBestEliteSolution();

                    // Aplica Path Relinking entre a solução atual e a melhor elite
                    ChallengeSolution prSolution = intensifier.applyPathRelinking(currentSolution, guideSolution);

                    // Se encontrou uma solução melhor, adota-a
                    if (prSolution.cost() < currentSolution.cost()) {
                        System.out.printf("Iter %d: Path Relinking melhorou a solução: %.4f -> %.4f%n",
                                         iter, currentSolution.cost(), prSolution.cost());
                        currentSolution = prSolution;

                        // Se for melhor que a global, atualiza a melhor global também
                        if (prSolution.cost() < bestSolution.cost()) {
                            bestSolution = prSolution.copy();
                            System.out.printf("Iter %d: Nova melhor solução via PR: %.4f%n",
                                             iter, bestSolution.cost());
                        }
                    }
                }
            }

            // D. Path Relinking entre pares de soluções elite
            // A cada 500 iterações para explorar recombinações de soluções elite
            if (iter % 500 == 0 && intensifier.getEliteCount() >= 2) {
                System.out.printf("Iter %d: Aplicando Path Relinking entre soluções elite%n", iter);

                ChallengeSolution prSolution = intensifier.applyElitePathRelinking();

                // Se encontrou uma solução melhor que a atual, adota-a
                if (prSolution != null && prSolution.cost() < currentSolution.cost()) {
                    System.out.printf("Iter %d: Elite Path Relinking melhorou a solução: %.4f -> %.4f%n",
                                     iter, currentSolution.cost(), prSolution.cost());
                    currentSolution = prSolution;

                    // Se for melhor que a global, atualiza a melhor global também
                    if (prSolution.cost() < bestSolution.cost()) {
                        bestSolution = prSolution.copy();
                        System.out.printf("Iter %d: Nova melhor solução via Elite PR: %.4f%n",
                                         iter, bestSolution.cost());
                    }
                }
            }

            // E. Intensificação Memética - A cada N ondas
            if (iter % waveSize == 0) {
                int wave = iter / waveSize;

                if (wave % mhFrequency == 0 && intensifier.hasEliteSolutions()) {
                    System.out.printf("Iter %d: Aplicando Memetic Hybrid (arquivo elite: %d soluções)%n",
                                     iter, intensifier.getEliteCount());

                    // Aplica intensificação memética nas soluções elite
                    ChallengeSolution refinedSolution = intensifier.applyMemeticIntensification();

                    // Se encontrou uma solução melhor, adota-a
                    if (refinedSolution != null && refinedSolution.cost() < currentSolution.cost()) {
                        System.out.printf("Iter %d: Memetic Hybrid melhorou a solução: %.4f -> %.4f%n",
                                         iter, currentSolution.cost(), refinedSolution.cost());
                        currentSolution = refinedSolution;

                        // Se for melhor que a global, atualiza a melhor global também
                        if (refinedSolution.cost() < bestSolution.cost()) {
                            bestSolution = refinedSolution.copy();
                            System.out.printf("Iter %d: Nova melhor solução via MH: %.4f%n",
                                             iter, bestSolution.cost());
                        }
                    }
                }
            }

            // 4.5 Resfriamento da temperatura
            temperature *= coolingRate;

            // 4.6 Exibe estatísticas a cada 1000 iterações
            if (iter % 1000 == 0 || iter == maxIterations) {
                System.out.printf("Iter %d: T=%.6f, Atual=%.4f, Melhor=%.4f%n",
                                 iter, temperature, currentSolution.cost(), bestSolution.cost());
            }
        }

        // 5. Exibe estatísticas finais do processo de intensificação
        intensifier.printStatistics();

        return bestSolution;
    }

    /**
     * Método main para demonstração simples.
     * Em um cenário real, este método seria chamado pelo ChallengeSolver ou similar.
     */
    public static void main(String[] args) {
        System.out.println("Exemplo de uso das técnicas de intensificação");
        System.out.println("Este código é apenas um pseudo-código para demonstração");
        System.out.println("Para uso real, integre estas técnicas ao seu algoritmo principal");

        // Em um cenário real, você teria:
        // 1. ChallengeSolution initialSolution = createInitialSolution();
        // 2. ChallengeSolution optimizedSolution = optimize(initialSolution, 10000);
        // 3. System.out.println("Solução final: " + optimizedSolution.cost());
    }
}
