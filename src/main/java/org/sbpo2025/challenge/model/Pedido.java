package org.sbpo2025.challenge.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa um pedido contendo uma lista de itens solicitados.
 */
public class Pedido {
    private int id;
    private List<ItemRequest> itens;
    
    public Pedido(int id) {
        this.id = id;
        this.itens = new ArrayList<>();
    }
    
    public Pedido(int id, List<ItemRequest> itens) {
        this.id = id;
        this.itens = new ArrayList<>(itens);
    }
    
    public int getId() {
        return id;
    }
    
    public List<ItemRequest> getItens() {
        return itens;
    }
    
    public void adicionarItem(ItemRequest item) {
        itens.add(item);
    }
    
    public void adicionarItem(int itemId, int unidades) {
        itens.add(new ItemRequest(itemId, unidades));
    }
    
    /**
     * Calcula o total de unidades solicitadas neste pedido
     * @return Soma das unidades de todos os itens do pedido
     */
    public int calcularTotalUnidades() {
        return itens.stream()
            .mapToInt(ItemRequest::getUnidades)
            .sum();
    }
    
    @Override
    public String toString() {
        return "Pedido{id=" + id + ", itens=" + itens + '}';
    }
}