package com.resolum.alivia.assistance;

import com.resolum.alivia.assistance.domain.model.aggregates.CareNote;
import com.resolum.alivia.assistance.domain.model.valueobjects.CareNoteCategory;
import com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories.CareNotePersistenceRepository;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** TS-17: a note is kept with its author and date, and the summary finds it by person and period. */
@DataJpaTest
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:notes;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false"})
class CareNotePersistenceTest {

    @Autowired
    private CareNotePersistenceRepository notes;

    @Test
    void aNoteKeepsItsAuthorAndDateAndIsFoundInItsPeriod() {
        var saved = notes.saveAndFlush(new CareNote(5L, 7L, CareNoteCategory.MOOD, "Durmió bien."));
        notes.saveAndFlush(new CareNote(6L, 7L, CareNoteCategory.MOOD, "Otra persona."));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getAuthorUserId()).isEqualTo(7L);
        // Auditing uses the shared clock, which is UTC.
        var now = LocalDateTime.now(java.time.Clock.systemUTC());
        assertThat(notes.findAllByAssistedPersonIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                5L, now.minusHours(1), now.plusHours(1))).extracting(CareNote::getContent).containsExactly("Durmió bien.");
        assertThat(notes.findAllByAssistedPersonIdOrderByCreatedAtDesc(5L)).hasSize(1);
    }
}
