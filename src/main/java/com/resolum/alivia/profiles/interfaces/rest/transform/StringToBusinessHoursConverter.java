package com.resolum.alivia.profiles.interfaces.rest.transform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolum.alivia.profiles.domain.model.valueobjects.BusinessHours;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Spring Converter to automatically bind JSON strings submitted in multipart/form-data
 * into {@link BusinessHours} objects.
 */
@Component
public class StringToBusinessHoursConverter implements Converter<String, BusinessHours> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Override
    public BusinessHours convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(source, BusinessHours.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid business hours format", e);
        }
    }
}
