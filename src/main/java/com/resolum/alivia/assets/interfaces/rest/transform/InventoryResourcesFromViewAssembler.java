package com.resolum.alivia.assets.interfaces.rest.transform;

import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceInventoryItem;
import com.resolum.alivia.assets.domain.model.valueobjects.DeviceType;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeInventoryItem;
import com.resolum.alivia.assets.domain.model.valueobjects.EdgeType;
import com.resolum.alivia.assets.interfaces.rest.resources.DeviceInventoryItemResource;
import com.resolum.alivia.assets.interfaces.rest.resources.EdgeInventoryItemResource;
import com.resolum.alivia.assets.interfaces.rest.resources.InventoryPageResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.function.Function;

/** What the inventory lists read from the request and what they answer. */
public class InventoryResourcesFromViewAssembler {

    /** The most items a page may have. */
    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 20;

    public static DeviceInventoryItemResource toResource(DeviceInventoryItem item) {
        var device = item.device();
        return new DeviceInventoryItemResource(device.getId(), device.getBusinessId().businessId(),
                device.getMacAddress().macAddress(), device.getAlias(), device.getDeviceType().name(),
                device.getAssetStatus().name(), item.installationId());
    }

    public static EdgeInventoryItemResource toResource(EdgeInventoryItem item) {
        var edge = item.edge();
        return new EdgeInventoryItemResource(edge.getId(), edge.getBusinessId().businessId(),
                edge.getMacAddress().macAddress(), edge.getEdgeType().name(), edge.getAssetStatus().name(),
                item.installationId());
    }

    public static <S, T> InventoryPageResource<T> toPageResource(Page<S> page, Function<S, T> toResource) {
        return new InventoryPageResource<>(page.getContent().stream().map(toResource).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /**
     * A page of the list, always in the order of the ids: the client does not choose what to sort by, so an unknown
     * property can never reach the database.
     *
     * @throws IllegalArgumentException when the page is negative or the size is not between 1 and {@value #MAX_PAGE_SIZE}
     */
    public static Pageable pageable(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("The page cannot be negative.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("The size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(page, size, Sort.by("id"));
    }

    /** The status of a filter, ignoring case; {@code null} or blank means no filter. */
    public static AssetStatus status(String value) {
        return parse(value, AssetStatus::valueOf, "asset status");
    }

    public static DeviceType deviceType(String value) {
        return parse(value, DeviceType::valueOf, "device type");
    }

    public static EdgeType edgeType(String value) {
        return parse(value, EdgeType::valueOf, "edge type");
    }

    private static <E> E parse(String value, Function<String, E> valueOf, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf.apply(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            // Not the message of valueOf: it would show the name of an internal class to the caller.
            throw new IllegalArgumentException("Unknown " + what + ": " + value.strip() + ".");
        }
    }
}
