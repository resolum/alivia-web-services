package com.resolum.alivia.assets.domain.model.queries;

public record GetDevIceByIdQuery(Long id) {
public GetDevIceByIdQuery{
    if (id==null||id<=0){

        throw new IllegalArgumentException("id cannot be null or the less 0");
    }
}
}
