package com.resolum.alivia.assets.domain.model.queries;

public record GetDeviceByIdQuery(Long id) {
public GetDeviceByIdQuery{
    if (id==null||id<=0){

        throw new IllegalArgumentException("id cannot be null or the less 0");
    }
}
}
