package com.griddynamics.productsearchservice.service;

import com.griddynamics.productsearchservice.model.ProductServiceRequest;
import com.griddynamics.productsearchservice.model.ProductServiceResponse;
import com.griddynamics.productsearchservice.repository.ProductSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class ProductSearchServiceImpl implements ProductSearchService {

    @Autowired
    private ProductSearchRepository productRepository;

    @Override
    public ProductServiceResponse getServiceResponse(ProductServiceRequest request) {
        if (request.isEmpty() || request.getQueryText().length() == 0) {
            return new ProductServiceResponse(0L, new ArrayList<>());
        } else {
            if (request.getSize() == null) {
                request.setSize(10);
            }
            if (request.getPage() == null) {
                request.setPage(0);
            }
        }
        return productRepository.getProductsByQuery(request);
    }


}
