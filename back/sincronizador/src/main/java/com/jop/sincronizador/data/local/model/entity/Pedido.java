package com.jop.sincronizador.data.local.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @Column(length = 36)
    private String uuid;

    @Column(length = 36)
    private String clienteUuid;

    @Column(length = 300)
    private String clienteName;

    @Column(columnDefinition = "TEXT")
    private String items; // JSON array

    private Double subtotal;
    private Double tax;
    private Double total;

    @Column(length = 20)
    private String status = "pending"; // pending | confirmed | cancelled

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 30)
    private String createdAt;

    @Column(length = 30)
    private String updatedAt;

    public Pedido() {}

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getClienteUuid() { return clienteUuid; }
    public void setClienteUuid(String clienteUuid) { this.clienteUuid = clienteUuid; }

    public String getClienteName() { return clienteName; }
    public void setClienteName(String clienteName) { this.clienteName = clienteName; }

    public String getItems() { return items; }
    public void setItems(String items) { this.items = items; }

    public Double getSubtotal() { return subtotal; }
    public void setSubtotal(Double subtotal) { this.subtotal = subtotal; }

    public Double getTax() { return tax; }
    public void setTax(Double tax) { this.tax = tax; }

    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
