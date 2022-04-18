package com.griddynamics.productindexer.service;

import com.griddynamics.productindexer.repository.ProductIndexRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProductIndexServiceImpl implements ProductIndexService {

    @Autowired
    private ProductIndexRepository productIndexRepository;

    @Override
    public void recreateIndex() {
        productIndexRepository.recreateIndex();
    }
}
