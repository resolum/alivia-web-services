package com.resolum.alivia.assets.interfaces.rest.resources;

import java.util.List;

/** A page of an inventory list: the items of the page and where it sits among all of them. */
public record InventoryPageResource<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
}
