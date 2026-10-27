package com.resolum.alivia.hcm.interfaces.rest.resources;

import java.util.List;

public record ContractPageResource(List<ContractResource> content, int page, int size,
                                   long totalElements, int totalPages) {
}
