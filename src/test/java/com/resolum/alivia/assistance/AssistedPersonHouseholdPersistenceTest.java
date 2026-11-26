package com.resolum.alivia.assistance;

import com.resolum.alivia.assistance.domain.model.aggregates.AssistedPerson;
import com.resolum.alivia.assistance.domain.model.aggregates.Relative;
import com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories.AssistedPersonPersistenceRepository;
import com.resolum.alivia.assistance.infrastructure.persistence.jpa.repositories.RelativePersistenceRepository;
import com.resolum.alivia.shared.domain.model.valueobjects.BusinessId;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** Who lives in a household, as Tracking asks it to know who may read the home. */
@DataJpaTest
@Import({ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:assisted_households;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false"})
class AssistedPersonHouseholdPersistenceTest {

    @Autowired
    private AssistedPersonPersistenceRepository people;
    @Autowired
    private RelativePersistenceRepository relatives;

    private AssistedPerson person(Relative relative, long profile, Long household) {
        var person = new AssistedPerson(new PersonProfileId(profile), relative);
        person.updateCareDetails(household, null, null, null);
        return people.saveAndFlush(person);
    }

    @Test
    void findsEveryoneWhoLivesInTheHouseholdAndNoOneElse() {
        var relative = relatives.saveAndFlush(new Relative(new UserId(1L), new PersonProfileId(1L), new BusinessId(1L)));
        var first = person(relative, 101, 30L);
        var second = person(relative, 102, 30L);
        person(relative, 103, 31L);
        person(relative, 104, null);

        assertThat(people.findAllByHouseholdId(30L)).extracting(AssistedPerson::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(people.findAllByHouseholdId(99L)).isEmpty();
    }
}
