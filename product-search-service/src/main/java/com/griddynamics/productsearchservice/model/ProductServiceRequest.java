package com.griddynamics.productsearchservice.model;

import lombok.Data;

@Data
public class ProductServiceRequest {
    private Integer size;
    private String queryText;
    private Integer page;

    public boolean isEmpty() {
        return queryText == null;
    }
}
