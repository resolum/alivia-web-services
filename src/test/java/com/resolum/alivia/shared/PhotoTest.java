package com.resolum.alivia.shared;

import  com.resolum.alivia.shared.domain.model.valueobjects.Photo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhotoTest {

    private static final String URL = "https://res.cloudinary.com/demo/image/upload/sample.jpg";
    private static final String PUBLIC_ID = "alivia/profiles/sample";

    @Test
    void createsValidPhoto() {
        var photo = new Photo(URL, PUBLIC_ID);

        assertEquals(URL, photo.photoUrl());
        assertEquals(PUBLIC_ID, photo.photoPublicId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsNullOrBlankUrl(String url) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Photo(url, PUBLIC_ID));

        assertEquals("Photo url cannot be null or blank.", ex.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsNullOrBlankPublicId(String publicId) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Photo(URL, publicId));

        assertEquals("Photo public id cannot be null or blank.", ex.getMessage());
    }

    @Test
    void equalityIsBasedOnBothComponents() {
        assertEquals(new Photo(URL, PUBLIC_ID), new Photo(URL, PUBLIC_ID));
        assertNotEquals(new Photo(URL, PUBLIC_ID), new Photo(URL, "otro"));
    }
}