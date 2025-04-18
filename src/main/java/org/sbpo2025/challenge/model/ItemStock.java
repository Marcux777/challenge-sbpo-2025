package org.sbpo2025.challenge.model;

/**
 * Representa um item disponível em um corredor com sua quantidade em estoque.
 */
public class ItemStock {
    private int itemId;
    private int unidades;

    public ItemStock(int itemId, int unidades) {
        this.itemId = itemId;
        this.unidades = unidades;
    }

    public int getItemId() {
        return itemId;
    }

    public int getUnidades() {
        return unidades;
    }

    public void setUnidades(int unidades) {
        this.unidades = unidades;
    }
    
    /**
     * Verifica se há estoque suficiente para atender uma solicitação
     * @param quantidade Quantidade solicitada
     * @return true se há estoque suficiente, false caso contrário
     */
    public boolean temEstoqueSuficiente(int quantidade) {
        return unidades >= quantidade;
    }
    
    /**
     * Reduz o estoque pela quantidade informada
     * @param quantidade Quantidade a reduzir
     * @return true se a operação foi bem sucedida, false caso contrário
     */
    public boolean reduzirEstoque(int quantidade) {
        if (temEstoqueSuficiente(quantidade)) {
            unidades -= quantidade;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "ItemStock{itemId=" + itemId + ", unidades=" + unidades + '}';
    }
}