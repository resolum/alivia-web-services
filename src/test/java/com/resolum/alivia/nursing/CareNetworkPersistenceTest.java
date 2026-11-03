package com.resolum.alivia.nursing;

import com.resolum.alivia.nursing.domain.model.aggregates.CareMembership;
import com.resolum.alivia.nursing.domain.model.valueobjects.CareRole;
import com.resolum.alivia.nursing.domain.repositories.CareMembershipRepository;
import com.resolum.alivia.nursing.infrastructure.adapters.CareMembershipRepositoryImpl;
import com.resolum.alivia.profiles.application.acl.HouseholdAccessFacadeImpl;
import com.resolum.alivia.profiles.application.acl.HouseholdMembershipFacadeImpl;
import com.resolum.alivia.profiles.application.internal.commandservices.HouseholdMemberCommandServiceImpl;
import com.resolum.alivia.profiles.application.internal.queryservices.HouseholdAccessServiceImpl;
import com.resolum.alivia.profiles.application.internal.queryservices.HouseholdQueryServiceImpl;
import com.resolum.alivia.profiles.domain.model.aggregates.Household;
import com.resolum.alivia.profiles.domain.model.valueobjects.HouseholdResponsibility;
import com.resolum.alivia.profiles.domain.repositories.HouseholdRepository;
import com.resolum.alivia.profiles.infrastructure.adapters.HouseholdRepositoryImpl;
import com.resolum.alivia.profiles.interfaces.acl.HouseholdAccessFacade;
import com.resolum.alivia.profiles.interfaces.acl.HouseholdMembershipFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.Address;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.domain.model.valueobjects.PersonProfileId;
import com.resolum.alivia.shared.domain.model.valueobjects.PhoneNumber;
import com.resolum.alivia.shared.domain.model.valueobjects.UserId;
import com.resolum.alivia.shared.infrastructure.configuration.ClockConfiguration;
import com.resolum.alivia.shared.infrastructure.persistence.jpa.configuration.JpaAuditingConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence used by the care network, against a real (in-memory) database: the care membership
 * repository and the Profiles membership facade. The slice builds the schema with Hibernate, so the
 * unique constraint checked here is the one declared in the entity (V1 declares the same one).
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED) // each call commits on its own, like in production
@Import({CareMembershipRepositoryImpl.class, HouseholdRepositoryImpl.class, HouseholdAccessServiceImpl.class,
        HouseholdAccessFacadeImpl.class, HouseholdQueryServiceImpl.class, HouseholdMemberCommandServiceImpl.class,
        HouseholdMembershipFacadeImpl.class, ClockConfiguration.class, JpaAuditingConfiguration.class})
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:h2:mem:carenetwork;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false"})
@DisplayName("Care network persistence Tests")
class CareNetworkPersistenceTest {

    private static final Address ADDRESS = new Address(
            "Av. El Derby 250, Santiago de Surco, Lima", -12.109722, -76.974444, "place-1");

    @Autowired
    private CareMembershipRepository careMembershipRepository;
    @Autowired
    private HouseholdRepository householdRepository;
    @Autowired
    private HouseholdMembershipFacade membershipFacade;
    @Autowired
    private HouseholdAccessFacade accessFacade;

    @Test
    @DisplayName("Care memberships are saved, found by assisted person and rejected when duplicated")
    void careMembershipsAreSavedFoundAndUnique() {
        var caregiver = new UserId(30L);
        var assisted = new PersonProfileId(200L);
        assertThat(careMembershipRepository.existsByUserIdAndAssistedPersonProfileIdAndRole(
                caregiver, assisted, CareRole.CAREGIVER)).isFalse();

        var saved = careMembershipRepository.save(
                new CareMembership(caregiver, new PersonProfileId(300L), assisted, CareRole.CAREGIVER));
        careMembershipRepository.save(
                new CareMembership(new UserId(10L), new PersonProfileId(100L), assisted, CareRole.RELATIVE));

        assertThat(saved.getId()).isNotNull();
        assertThat(careMembershipRepository.existsByUserIdAndAssistedPersonProfileIdAndRole(
                caregiver, assisted, CareRole.CAREGIVER)).isTrue();
        assertThat(careMembershipRepository.existsByUserIdAndAssistedPersonProfileIdAndRole(
                caregiver, assisted, CareRole.RELATIVE)).isFalse();
        assertThat(careMembershipRepository.findActiveByAssistedPersonProfileIds(List.of(assisted))).hasSize(2);
        assertThat(careMembershipRepository.findActiveByAssistedPersonProfileIds(List.of())).isEmpty();
        // The service relies on the constraint to answer 409 for a concurrent duplicate.
        assertThatThrownBy(() -> careMembershipRepository.save(
                new CareMembership(caregiver, new PersonProfileId(300L), assisted, CareRole.CAREGIVER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("The membership facade lists active members, adds new ones and keeps revoked ones out of access")
    void membershipFacadeWorksAgainstTheDatabase() {
        var household = new Household("Casa", ADDRESS, new PhoneNumber("51987654321"),
                new EmailAddress("hogar@alivia.com"));
        household.addMember(new UserId(10L), HouseholdResponsibility.PRINCIPAL);
        household.addMember(new UserId(11L), HouseholdResponsibility.CAREGIVER).revoke();
        var id = householdRepository.save(household).getId();

        assertThat(membershipFacade.fetchActiveMemberUserIds(id)).isEqualTo(Optional.of(Set.of(10L)));
        assertThat(membershipFacade.addMember(id, 20L, "GUEST")).isTrue();
        assertThat(membershipFacade.addMember(id, 20L, "CAREGIVER")).isFalse();
        // A revoked member is "already a member" for Profiles, and still has no access.
        assertThat(membershipFacade.addMember(id, 11L, "CAREGIVER")).isFalse();
        assertThat(membershipFacade.fetchActiveMemberUserIds(id)).isEqualTo(Optional.of(Set.of(10L, 20L)));
        assertThat(accessFacade.canReadHousehold(id, 20L)).isTrue();
        assertThat(accessFacade.canManageHousehold(id, 20L)).isFalse();
        assertThat(accessFacade.canReadHousehold(id, 11L)).isFalse();
        assertThat(membershipFacade.fetchActiveMemberUserIds(987_654L)).isEmpty();
    }
}
