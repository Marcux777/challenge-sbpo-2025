package org.sbpo2025.challenge.model;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Representa uma instância completa do desafio SBPO 2025.
 */
public class ChallengeInstance {
    private int numPedidos;      // o = número de pedidos
    private int numItens;        // i = número de itens distintos
    private int numCorredores;   // a = número de corredores
    private List<Pedido> pedidos;
    private List<Corredor> corredores;
    private int limiteMinimoWave;  // LB = limite inferior da wave
    private int limiteMaximoWave;  // UB = limite superior da wave
    private String filename; // Adicionado para guardar o nome do arquivo

    public ChallengeInstance() {
        this.pedidos = new ArrayList<>();
        this.corredores = new ArrayList<>();
    }

    /**
     * Construtor que cria uma instância a partir de dados internos para uso pelo solver
     * @param filename Nome do arquivo (opcional)
     * @param orders Lista de pedidos como mapas de item para quantidade
     * @param aisles Lista de corredores como mapas de item para quantidade
     * @param numItems Número total de itens diferentes
     * @param waveSizeLB Limite inferior da wave
     * @param waveSizeUB Limite superior da wave
     */
    public ChallengeInstance(String filename, List<Map<Integer, Integer>> orders,
            List<Map<Integer, Integer>> aisles, int numItems, int waveSizeLB, int waveSizeUB) {
        this.filename = filename;
        this.numPedidos = orders.size();
        this.numCorredores = aisles.size();
        this.numItens = numItems;
        this.limiteMinimoWave = waveSizeLB;
        this.limiteMaximoWave = waveSizeUB;

        this.pedidos = new ArrayList<>();
        this.corredores = new ArrayList<>();

        // Converte os mapas de pedidos para objetos Pedido
        for (int i = 0; i < orders.size(); i++) {
            Pedido pedido = new Pedido(i);
            Map<Integer, Integer> orderItems = orders.get(i);

            for (Map.Entry<Integer, Integer> entry : orderItems.entrySet()) {
                pedido.adicionarItem(entry.getKey(), entry.getValue());
            }

            this.pedidos.add(pedido);
        }

        // Converte os mapas de corredores para objetos Corredor
        for (int i = 0; i < aisles.size(); i++) {
            Corredor corredor = new Corredor(i);
            Map<Integer, Integer> aisleItems = aisles.get(i);

            for (Map.Entry<Integer, Integer> entry : aisleItems.entrySet()) {
                corredor.adicionarItem(entry.getKey(), entry.getValue());
            }

            this.corredores.add(corredor);
        }
    }

    /**
     * Carrega uma instância a partir do arquivo de entrada
     * @param caminhoArquivo Caminho do arquivo de entrada
     * @return Uma instância carregada com todos os dados
     * @throws IOException Se ocorrer erro na leitura do arquivo
     */
    public static ChallengeInstance carregarDeArquivo(String caminhoArquivo) throws IOException {
        ChallengeInstance instancia = new ChallengeInstance();
        instancia.filename = caminhoArquivo; // Guarda o nome do arquivo

        try (BufferedReader reader = new BufferedReader(new FileReader(caminhoArquivo))) {
            // Lê cabeçalho
            StringTokenizer st = new StringTokenizer(reader.readLine());
            instancia.numPedidos = Integer.parseInt(st.nextToken());
            instancia.numItens = Integer.parseInt(st.nextToken());
            instancia.numCorredores = Integer.parseInt(st.nextToken());

            // Lê os pedidos
            for (int p = 0; p < instancia.numPedidos; p++) {
                Pedido pedido = new Pedido(p);
                st = new StringTokenizer(reader.readLine());
                int k = Integer.parseInt(st.nextToken()); // número de pares item+unidades

                for (int i = 0; i < k; i++) {
                    int itemId = Integer.parseInt(st.nextToken());
                    int unidades = Integer.parseInt(st.nextToken());
                    pedido.adicionarItem(itemId, unidades);
                }

                instancia.pedidos.add(pedido);
            }

            // Lê os corredores
            for (int c = 0; c < instancia.numCorredores; c++) {
                Corredor corredor = new Corredor(c);
                st = new StringTokenizer(reader.readLine());
                int l = Integer.parseInt(st.nextToken()); // número de pares item+unidades

                for (int i = 0; i < l; i++) {
                    int itemId = Integer.parseInt(st.nextToken());
                    int unidades = Integer.parseInt(st.nextToken());
                    corredor.adicionarItem(itemId, unidades);
                }

                instancia.corredores.add(corredor);
            }

            // Lê limites da wave
            st = new StringTokenizer(reader.readLine());
            instancia.limiteMinimoWave = Integer.parseInt(st.nextToken()); // LB
            instancia.limiteMaximoWave = Integer.parseInt(st.nextToken()); // UB
        }

        return instancia;
    }

    // Getters
    public int getNumPedidos() {
        return numPedidos;
    }

    public int getNumItens() {
        return numItens;
    }

    public int getNumCorredores() {
        return numCorredores;
    }

    public List<Pedido> getPedidos() {
        return pedidos;
    }

    public List<Corredor> getCorredores() {
        return corredores;
    }

    public int getLimiteMinimoWave() {
        return limiteMinimoWave;
    }

    public int getLimiteMaximoWave() {
        return limiteMaximoWave;
    }

    // Getter para o nome do arquivo
    public String getFilename() {
        return filename;
    }

    /**
     * Verifica se um conjunto de pedidos respeita os limites de tamanho da wave
     * @param indicesPedidos Lista com os índices dos pedidos selecionados
     * @return true se o conjunto de pedidos respeita os limites, false caso contrário
     */
    public boolean waveDentroLimites(List<Integer> indicesPedidos) {
        int totalUnidades = 0;

        for (Integer indicePedido : indicesPedidos) {
            if (indicePedido >= 0 && indicePedido < pedidos.size()) {
                totalUnidades += pedidos.get(indicePedido).calcularTotalUnidades();
            }
        }

        return totalUnidades >= limiteMinimoWave && totalUnidades <= limiteMaximoWave;
    }

    /**
     * Calcula o número total de unidades em um conjunto de pedidos
     * @param indicesPedidos Lista com os índices dos pedidos selecionados
     * @return Soma das unidades de todos os itens nos pedidos selecionados
     */
    public int calcularTotalUnidadesWave(List<Integer> indicesPedidos) {
        int totalUnidades = 0;

        for (Integer indicePedido : indicesPedidos) {
            if (indicePedido >= 0 && indicePedido < pedidos.size()) {
                totalUnidades += pedidos.get(indicePedido).calcularTotalUnidades();
            }
        }

        return totalUnidades;
    }

    @Override
    public String toString() {
        return "ChallengeInstance{" +
                "numPedidos=" + numPedidos +
                ", numItens=" + numItens +
                ", numCorredores=" + numCorredores +
                ", limiteMinimoWave=" + limiteMinimoWave +
                ", limiteMaximoWave=" + limiteMaximoWave +
                ", pedidos=" + pedidos.size() +
                ", corredores=" + corredores.size() +
                '}';
    }
}
