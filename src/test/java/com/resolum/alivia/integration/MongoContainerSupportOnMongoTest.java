package com.resolum.alivia.integration;

import com.mongodb.client.MongoClients;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The shared MongoDB starts and behaves as the next tests need it to: it stores and reads a document and it refuses a
 * duplicate on a unique index (the base of the idempotent event ingestion of Tracking).
 */
@EnabledIf(value = "com.resolum.alivia.integration.MongoContainerSupport#dockerAvailable",
        disabledReason = "Docker is not available, so there is no MongoDB to test against")
class MongoContainerSupportOnMongoTest {

    @Test
    void storesAndReadsADocumentAndRefusesADuplicateOnAUniqueIndex() {
        try (var client = MongoClients.create(MongoContainerSupport.connectionString())) {
            var events = client.getDatabase("support_smoke").getCollection("events");
            events.drop();
            events.createIndex(Indexes.ascending("edgeId", "eventId"), new IndexOptions().unique(true));

            events.insertOne(new Document("edgeId", 1L).append("eventId", "e1").append("type", "LOW_BATTERY"));

            assertEquals("LOW_BATTERY", events.find(new Document("eventId", "e1")).first().getString("type"));
            assertThrows(com.mongodb.MongoWriteException.class,
                    () -> events.insertOne(new Document("edgeId", 1L).append("eventId", "e1")));
            // Same event id from another edge is another event.
            events.insertOne(new Document("edgeId", 2L).append("eventId", "e1"));
            assertEquals(2, events.countDocuments());
        }
    }
}
