package org.sbpo2025.challenge;

import org.sbpo2025.challenge.model.ChallengeInstance;

import java.io.File;
import java.io.IOException;

/**
 * Classe para testar a leitura de instâncias do Desafio SBPO 2025.
 */
public class InstanceReader {

    public static void main(String[] args) {
        String datasetDir = "/workspaces/challenge-sbpo-2025/datasets";
        File dir = new File(datasetDir);
        
        if (dir.exists() && dir.isDirectory()) {
            // Verificar o grupo A
            processaGrupoInstancias(datasetDir + "/a");
            
            // Verificar o grupo B
            processaGrupoInstancias(datasetDir + "/b");
        } else {
            System.err.println("Diretório de datasets não encontrado: " + datasetDir);
        }
    }
    
    private static void processaGrupoInstancias(String diretorio) {
        File dir = new File(diretorio);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Diretório não encontrado: " + diretorio);
            return;
        }
        
        System.out.println("Processando instâncias do diretório: " + diretorio);
        
        File[] arquivos = dir.listFiles((d, name) -> name.endsWith(".txt"));
        if (arquivos != null) {
            for (File arquivo : arquivos) {
                try {
                    System.out.println("\nProcessando arquivo: " + arquivo.getName());
                    ChallengeInstance instancia = ChallengeInstance.carregarDeArquivo(arquivo.getPath());
                    System.out.println(instancia);
                    
                    // Mostrar informações resumidas sobre os pedidos
                    System.out.println("Total de pedidos: " + instancia.getNumPedidos());
                    System.out.println("Total de corredores: " + instancia.getNumCorredores());
                    System.out.println("Limites da wave: " + instancia.getLimiteMinimoWave() + " - " + instancia.getLimiteMaximoWave());
                    
                    // Mostrar detalhes do primeiro pedido, se houver
                    if (!instancia.getPedidos().isEmpty()) {
                        System.out.println("Detalhes do primeiro pedido: " + instancia.getPedidos().get(0));
                    }
                    
                    // Mostrar detalhes do primeiro corredor, se houver
                    if (!instancia.getCorredores().isEmpty()) {
                        System.out.println("Detalhes do primeiro corredor: " + instancia.getCorredores().get(0));
                    }
                } catch (IOException e) {
                    System.err.println("Erro ao processar arquivo " + arquivo.getPath() + ": " + e.getMessage());
                }
            }
        }
    }
}