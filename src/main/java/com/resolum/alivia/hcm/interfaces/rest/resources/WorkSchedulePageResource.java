package com.resolum.alivia.hcm.interfaces.rest.resources;

import java.util.List;

public record WorkSchedulePageResource(List<WorkScheduleResource> content, int page, int size,
                                       long totalElements, int totalPages) {
}
