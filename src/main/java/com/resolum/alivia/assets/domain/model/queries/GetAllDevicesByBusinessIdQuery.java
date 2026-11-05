package com.resolum.alivia.assets.domain.model.queries;

public record GetAllDevicesByBusinessIdQuery(Long businessId) {

    public GetAllDevicesByBusinessIdQuery{

        if (businessId==null||businessId<=0 ){
            throw new IllegalArgumentException("businesss id cannot be null o less 0");

        }
    }
}

