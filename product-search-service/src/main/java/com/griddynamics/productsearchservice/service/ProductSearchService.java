package com.griddynamics.productsearchservice.service;

import com.griddynamics.productsearchservice.model.ProductServiceRequest;
import com.griddynamics.productsearchservice.model.ProductServiceResponse;

public interface ProductSearchService {
    ProductServiceResponse getServiceResponse(ProductServiceRequest request);
}
