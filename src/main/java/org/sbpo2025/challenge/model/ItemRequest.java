package org.sbpo2025.challenge.model;

/**
 * Representa um item solicitado em um pedido com sua quantidade.
 */
public class ItemRequest {
    private int itemId;
    private int unidades;

    public ItemRequest(int itemId, int unidades) {
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

    @Override
    public String toString() {
        return "ItemRequest{itemId=" + itemId + ", unidades=" + unidades + '}';
    }
}