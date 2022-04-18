package com.griddynamics.productsearchservice.repository;


import com.griddynamics.productsearchservice.model.ProductServiceRequest;
import com.griddynamics.productsearchservice.model.ProductServiceResponse;

public interface ProductSearchRepository {
    ProductServiceResponse getAllProducts(ProductServiceRequest request);
    ProductServiceResponse getProductsByQuery(ProductServiceRequest request);

}
