package com.jop.sincronizador.data.laudus.model.entity;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class OrderWP {

    private int id;
    private String number;
    private String status;
    private String date_created;
    private String date_modified;
    private String total;
    private String currency;
    private String customer_note;
    private Billing billing;
    private List<LineItem> line_items;

    @Getter
    @Setter
    public static class Billing {
        private String first_name;
        private String last_name;
        private String email;
        private String phone;
        private String address_1;
        private String city;
    }

    @Getter
    @Setter
    public static class LineItem {
        private int id;
        private String name;
        private String sku;
        private int quantity;
        private String total;
    }
}
