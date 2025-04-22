package org.sbpo2025.challenge.sautils;

public class ASAStatistics {
    private int totalIterations = 0;
    private int improvements = 0;
    private int intensifications = 0;
    private int pathRelinkings = 0;
    private int memeticIntensifications = 0;

    public void incIteration() { totalIterations++; }
    public void incImprovement() { improvements++; }
    public void incIntensification() { intensifications++; }
    public void incPathRelinking() { pathRelinkings++; }
    public void incMemetic() { memeticIntensifications++; }

    public int getTotalIterations() { return totalIterations; }
    public int getImprovements() { return improvements; }
    public int getIntensifications() { return intensifications; }
    public int getPathRelinkings() { return pathRelinkings; }
    public int getMemeticIntensifications() { return memeticIntensifications; }

    public void printSummary() {
        System.out.println("--- Estatísticas ASA ---");
        System.out.printf("Iterações: %d\n", totalIterations);
        System.out.printf("Melhorias: %d\n", improvements);
        System.out.printf("Intensificações: %d\n", intensifications);
        System.out.printf("Path Relinking: %d\n", pathRelinkings);
        System.out.printf("Memética: %d\n", memeticIntensifications);
    }
}
