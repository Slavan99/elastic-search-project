package com.griddynamics.productsearchservice.rest;

import com.griddynamics.productsearchservice.model.ProductServiceRequest;
import com.griddynamics.productsearchservice.model.ProductServiceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.griddynamics.productsearchservice.service.ProductSearchService;

@RestController
@RequestMapping(value = "/v1/product")
public class ProductController {

    @Autowired
    private ProductSearchService productService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    public ProductServiceResponse getSearchServiceResponse(@RequestBody ProductServiceRequest request) {
        return productService.getServiceResponse(request);
    }
}
