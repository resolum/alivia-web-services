package com.resolum.alivia.nursing.interfaces.rest.transform;

import com.resolum.alivia.nursing.domain.model.commands.SaveAssistedPersonCareProfileCommand;
import com.resolum.alivia.nursing.domain.model.valueobjects.IdentityDocumentType;
import com.resolum.alivia.nursing.domain.model.valueobjects.KinshipToCaregiver;
import com.resolum.alivia.nursing.domain.model.valueobjects.MobilityLevel;
import com.resolum.alivia.nursing.interfaces.rest.resources.SaveAssistedPersonCareProfileResource;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;

/**
 * Builds a {@link SaveAssistedPersonCareProfileCommand} from its REST resource.
 */
public final class SaveAssistedPersonCareProfileCommandFromResourceAssembler {

    private SaveAssistedPersonCareProfileCommandFromResourceAssembler() {
    }

    /**
     * Converts the resource. Unknown enum names become an {@link IllegalArgumentException}, which
     * answers 400.
     *
     * @param requester the authenticated client
     * @param resource  the request body
     * @return the command
     */
    public static SaveAssistedPersonCareProfileCommand toCommandFromResource(
            UserId requester, SaveAssistedPersonCareProfileResource resource) {
        var documentType = resource.documentType() == null || resource.documentType().isBlank()
                ? null
                : parse(IdentityDocumentType.class, resource.documentType(), "document type");
        return new SaveAssistedPersonCareProfileCommand(
                requester,
                resource.householdId(),
                resource.firstName(),
                resource.lastName(),
                resource.birthDate(),
                documentType,
                resource.documentNumber(),
                parse(KinshipToCaregiver.class, resource.kinship(), "kinship"),
                resource.phoneNumber(),
                parse(MobilityLevel.class, resource.mobilityLevel(), "mobility level"),
                resource.emergencyContactName(),
                resource.emergencyContactPhone(),
                Boolean.TRUE.equals(resource.consentGiven()));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("The " + name + " is required.");
        }
        try {
            return Enum.valueOf(type, value.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + name + ": " + value.strip() + ".", exception);
        }
    }
}
