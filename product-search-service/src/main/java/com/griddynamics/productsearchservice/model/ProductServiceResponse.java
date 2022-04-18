package com.griddynamics.productsearchservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProductServiceResponse {
    private Long totalHits;
    @JsonInclude
    private List<Map<String, Object>> products;
    private Map<String, List<Map<String, Object>>> facets = new HashMap<>();

    public ProductServiceResponse() {
    }

    public ProductServiceResponse(Long totalHits, List<Map<String, Object>> products) {
        this.totalHits = totalHits;
        this.products = products;
    }

}
