package org.sbpo2025.challenge.sautils;

import java.util.List;

public interface EliteManager<SolutionType> {
    void addElite(SolutionType solution, double cost);
    void updateElite(SolutionType solution, double cost);
    List<SolutionType> getEliteSolutions();
    int getEliteCount();
    void printEliteSummary();
}
