package com.resolum.alivia.assets.domain.model.queries;

public record GetAllEdgesByBusinessIdQuery(Long businessId) {
    public GetAllEdgesByBusinessIdQuery{
        if (businessId==null|| businessId<=0){
            throw new IllegalArgumentException("business id cannot be null or less then 0");
        }

    }
}
