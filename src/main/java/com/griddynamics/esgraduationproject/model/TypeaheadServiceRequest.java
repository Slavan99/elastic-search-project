package com.griddynamics.esgraduationproject.model;

import lombok.Data;

@Data
public class TypeaheadServiceRequest {
    private Integer size;
    private String textQuery;
    private boolean considerItemCountInSorting;

    public boolean isGetAllRequest() {
        return textQuery == null;
    }
}
