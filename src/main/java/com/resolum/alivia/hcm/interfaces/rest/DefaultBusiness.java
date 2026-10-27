package com.resolum.alivia.hcm.interfaces.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Business used by HCM requests that do not name one. Today Alivia is the only business; once the
 * session carries the user's business, requests send it and this default is no longer used.
 */
@Component
public class DefaultBusiness {
    private final Long id;

    public DefaultBusiness(@Value("${alivia.hcm.default-business-id}") Long id) {
        this.id = id;
    }

    public Long resolve(Long requested) {
        return requested != null ? requested : id;
    }
}
