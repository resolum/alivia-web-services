package com.resolum.alivia.assistance.interfaces.rest.resources;

/** @param category GENERAL, HEALTH, MEDICATION, FEEDING, MOOD or INCIDENT */
public record CreateCareNoteResource(String content, String category) {
}
