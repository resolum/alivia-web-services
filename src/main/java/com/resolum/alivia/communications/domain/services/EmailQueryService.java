package com.resolum.alivia.communications.domain.services;

import com.resolum.alivia.communications.domain.model.aggregates.Email;
import com.resolum.alivia.communications.domain.model.queries.GetAllEmailsByStatusQuery;

import java.util.List;

public interface EmailQueryService {
    List<Email> handle(GetAllEmailsByStatusQuery query);
}
