package com.resolum.alivia.iam.interfaces.rest.transform;

import com.resolum.alivia.iam.domain.exceptions.SignUpValidationException;
import com.resolum.alivia.iam.domain.model.commands.SignUpCommand;
import com.resolum.alivia.iam.domain.model.valueobjects.AccessRole;
import com.resolum.alivia.iam.interfaces.rest.resources.SignUpResource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Assembles {@link SignUpCommand} instances from REST resources.
 */
public final class SignUpCommandFromResourceAssembler {

    private SignUpCommandFromResourceAssembler() {
    }

    /**
     * Creates a sign-up command from a request resource.
     *
     * @param resource the sign-up request resource
     * @return the sign-up command
     * @throws SignUpValidationException when the role is unknown or the photo is not a readable image
     */
    public static SignUpCommand toCommandFromResource(SignUpResource resource) {
        if (resource == null) {
            return null;
        }
        AccessRole role = parseRole(resource.role());
        var photo = resource.photo();
        boolean hasPhoto = photo != null && !photo.isEmpty();
        return new SignUpCommand(
                resource.email(),
                resource.password(),
                resource.firstName(),
                resource.lastName(),
                resource.phoneNumber(),
                Boolean.TRUE.equals(resource.acceptedTerms()),
                role,
                resource.businessId(),
                resource.familiarId(),
                resource.dni(),
                resource.birthDate(),
                hasPhoto ? imageBytes(photo) : null,
                hasPhoto ? photo.getOriginalFilename() : null
        );
    }

    private static AccessRole parseRole(String roleStr) {
        try {
            return com.resolum.alivia.iam.domain.services.RoleParser.parse(roleStr);
        } catch (IllegalArgumentException e) {
            throw new SignUpValidationException("role", "invalid_role");
        }
    }

    private static byte[] imageBytes(MultipartFile photo) {
        var contentType = photo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new SignUpValidationException("photo", "invalid_photo");
        }
        try {
            return photo.getBytes();
        } catch (IOException exception) {
            throw new SignUpValidationException("photo", "invalid_photo");
        }
    }
}
