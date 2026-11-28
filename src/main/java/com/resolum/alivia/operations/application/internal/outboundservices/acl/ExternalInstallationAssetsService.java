package com.resolum.alivia.operations.application.internal.outboundservices.acl;

import com.resolum.alivia.assets.domain.exceptions.DeviceNotAvailable;
import com.resolum.alivia.assets.domain.exceptions.EdgeNotAvailable;
import com.resolum.alivia.assets.domain.model.valueobjects.AssetStatus;
import com.resolum.alivia.assets.interfaces.acl.AssetsContextFacade;
import com.resolum.alivia.assistance.interfaces.acl.AssistanceContextFacade;
import com.resolum.alivia.operations.domain.exceptions.InstallationAssetNotFound;
import com.resolum.alivia.operations.domain.exceptions.InstallationAssetTypeMismatch;
import com.resolum.alivia.operations.domain.exceptions.InstallationContextMissing;
import com.resolum.alivia.operations.domain.exceptions.RequiredInstallationEdgeMissing;
import com.resolum.alivia.operations.domain.model.aggregates.Appointment;
import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDevice;
import com.resolum.alivia.operations.domain.model.valueobjects.InstalledDeviceType;
import org.springframework.stereotype.Service;

/**
 * Keeps the inventory (Assets) in step with the devices a technician registers in an installation order: each MAC must
 * be an available asset of the business with the chosen type, the edge of its kind goes first, and the asset is placed
 * in the installation (edges) or assigned to a room of the household (devices). The installation is identified by the
 * order's id.
 */
@Service
public class ExternalInstallationAssetsService {
    private static final String AVAILABLE = "AVAILABLE";

    private final AssetsContextFacade assets;
    private final AssistanceContextFacade assistance;
    private final ExternalSubscriptionsService subscriptions;

    public ExternalInstallationAssetsService(AssetsContextFacade assets, AssistanceContextFacade assistance,
                                             ExternalSubscriptionsService subscriptions) {
        this.assets = assets;
        this.assistance = assistance;
        this.subscriptions = subscriptions;
    }

    /**
     * Validates the device against the inventory and places it in the installation of the order.
     *
     * @param room key of the room for a device, such as {@code habitacion-1}; ignored for edges
     */
    public void validateAndAssign(Appointment order, InstalledDevice device, String room) {
        var type = device.type();
        if (!type.isEdge() && type.requiredEdge() == null) {
            throw new IllegalArgumentException("Elige un tipo de dispositivo válido: edge, micrófono, puerta, ventana, cortina o luz.");
        }
        if (!type.isEdge() && (room == null || room.isBlank())) {
            throw new IllegalArgumentException("Elige la habitación donde queda el dispositivo.");
        }
        var context = subscriptions.fetchInstallationContext(order.getDraftId())
                .orElseThrow(() -> new InstallationContextMissing("La contratación no está vinculada al negocio y hogar de la instalación."));
        var householdId = assistance.fetchHouseholdIdOfAssistedPerson(context.assistedPersonId())
                .orElseThrow(() -> new InstallationContextMissing("La persona asistida no tiene un hogar asignado."));
        var asset = assets.fetchInventoryAssetByMacAddress(device.macAddress())
                .filter(found -> found.businessId().equals(context.businessId()))
                .orElseThrow(InstallationAssetNotFound::new);
        if (!asset.type().equals(type.name()) || asset.edge() != type.isEdge()) {
            throw new InstallationAssetTypeMismatch();
        }
        if (!AVAILABLE.equals(asset.status())) {
            var status = AssetStatus.valueOf(asset.status());
            if (asset.edge()) throw new EdgeNotAvailable(asset.id(), status);
            throw new DeviceNotAvailable(asset.id(), status);
        }

        assets.ensureInstallation(order.getId(), householdId, context.businessId());
        var required = type.requiredEdge();
        if (required != null && !assets.installationHasEdgeType(order.getId(), required.name())) {
            throw new RequiredInstallationEdgeMissing(required == InstalledDeviceType.MICROPHONE_EDGE
                    ? "Registra primero el Edge de control por voz."
                    : "Registra primero el Edge de actuadores.");
        }
        if (type.isEdge()) {
            assets.placeEdge(order.getId(), asset.id());
        } else {
            assets.assignDevice(order.getId(), asset.id(), room.strip(), device.alias(), order.getTechnicianId());
        }
    }

    /**
     * Undoes the placement of a device removed from the order: the edge leaves the installation and a device leaves the
     * household, so both are available again. Legacy types and MACs outside the inventory change nothing.
     */
    public void release(Appointment order, InstalledDevice device) {
        var type = device.type();
        if (!type.isEdge() && type.requiredEdge() == null) return;
        assets.fetchInventoryAssetByMacAddress(device.macAddress())
                .filter(asset -> asset.edge() == type.isEdge() && !AVAILABLE.equals(asset.status()))
                .ifPresent(asset -> {
                    if (asset.edge()) {
                        assets.removeEdge(order.getId(), asset.id());
                    } else {
                        assets.unassignDevice(asset.id(), "Quitado de la orden técnica " + order.getId(), order.getTechnicianId());
                    }
                });
    }
}
