package org.sbpo2025.challenge.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa um corredor contendo o estoque de diversos itens.
 */
public class Corredor {
    private int id;
    private List<ItemStock> estoque;
    private Map<Integer, ItemStock> estoqueMap;
    
    public Corredor(int id) {
        this.id = id;
        this.estoque = new ArrayList<>();
        this.estoqueMap = new HashMap<>();
    }
    
    public Corredor(int id, List<ItemStock> estoque) {
        this.id = id;
        this.estoque = new ArrayList<>(estoque);
        this.estoqueMap = new HashMap<>();
        
        // Preenche o mapa para acesso rápido aos itens por ID
        for (ItemStock item : estoque) {
            estoqueMap.put(item.getItemId(), item);
        }
    }
    
    public int getId() {
        return id;
    }
    
    public List<ItemStock> getEstoque() {
        return estoque;
    }
    
    /**
     * Adiciona um novo item ao estoque do corredor
     * @param item O item a ser adicionado
     */
    public void adicionarItem(ItemStock item) {
        estoque.add(item);
        estoqueMap.put(item.getItemId(), item);
    }
    
    /**
     * Adiciona um novo item ao estoque do corredor
     * @param itemId ID do item
     * @param unidades Quantidade disponível em estoque
     */
    public void adicionarItem(int itemId, int unidades) {
        ItemStock item = new ItemStock(itemId, unidades);
        adicionarItem(item);
    }
    
    /**
     * Verifica se o corredor possui estoque suficiente do item solicitado
     * @param itemId ID do item solicitado
     * @param quantidade Quantidade necessária
     * @return true se há estoque suficiente, false caso contrário
     */
    public boolean possuiEstoqueSuficiente(int itemId, int quantidade) {
        ItemStock item = estoqueMap.get(itemId);
        return item != null && item.temEstoqueSuficiente(quantidade);
    }
    
    /**
     * Reduz o estoque de um item específico pela quantidade informada
     * @param itemId ID do item a reduzir
     * @param quantidade Quantidade a ser reduzida
     * @return true se a operação foi bem sucedida, false caso contrário
     */
    public boolean reduzirEstoque(int itemId, int quantidade) {
        ItemStock item = estoqueMap.get(itemId);
        if (item != null) {
            return item.reduzirEstoque(quantidade);
        }
        return false;
    }
    
    /**
     * Verifica se o corredor pode atender completamente um pedido
     * @param pedido O pedido a ser verificado
     * @return true se todos os itens do pedido podem ser atendidos por este corredor, false caso contrário
     */
    public boolean podeAtenderPedido(Pedido pedido) {
        for (ItemRequest req : pedido.getItens()) {
            if (!possuiEstoqueSuficiente(req.getItemId(), req.getUnidades())) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public String toString() {
        return "Corredor{id=" + id + ", estoque=" + estoque + '}';
    }
}