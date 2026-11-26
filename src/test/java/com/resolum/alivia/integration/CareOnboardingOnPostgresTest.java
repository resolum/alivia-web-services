package com.resolum.alivia.integration;

import com.resolum.alivia.iam.application.internal.outboundservices.tokens.TokenService;
import com.resolum.alivia.iam.domain.repositories.UserRepository;
import com.resolum.alivia.iam.interfaces.acl.IamContextFacade;
import com.resolum.alivia.shared.domain.model.valueobjects.EmailAddress;
import com.resolum.alivia.shared.infrastructure.storage.cloudinary.CloudinaryStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The first steps of a family in the application, with real accounts and the real security chain: from a new account to
 * a caregiver who reads the assisted person. A new account has no relative record, so this is what shows that it is
 * created with the first person, which a test of the controller with mocked services cannot show.
 */
class CareOnboardingOnPostgresTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Alivia#Onboarding2026";
    private static final String PERSONS = "/api/v1/assisted-persons";

    @Autowired
    private IamContextFacade iam;
    @Autowired
    private UserRepository accounts;
    @Autowired
    private TokenService tokenService;
    /** The only thing replaced: the profile photo is uploaded to an external service. */
    @MockitoBean
    private CloudinaryStorageService storage;

    private record Account(long userId, String token) {
    }

    @BeforeEach
    void photosAreStoredWithoutTheExternalService() {
        when(storage.upload(any(), any()))
                .thenReturn(Map.of("url", "https://example.test/photo.png", "publicId", "photo"));
    }

    @Test
    void aNewAccountRegistersItsFirstPersonsAndInvitesACaregiverWhoReadsThem() throws Exception {
        var family = signUp("onboarding-family@alivia.test", "71000001");
        long home = newHousehold(family, "onboarding-family@alivia.test");
        assertEquals(0, relativeRecords(family));

        var first = register(family, home, "72000001");
        assertEquals(201, first.status(), first.body());
        var second = register(family, home, "72000002");
        assertEquals(201, second.status(), second.body());

        // One record, created with the first person and reused by the second.
        assertEquals(1, relativeRecords(family));
        assertEquals(number(first.body(), "relativeId"), number(second.body(), "relativeId"));
        long person = number(first.body(), "id");
        var list = call("GET", PERSONS, family.token());
        assertEquals(200, list.status(), list.body());
        assertTrue(list.body().contains("72000001") && list.body().contains("72000002"), list.body());

        var invitation = call("POST", "/api/v1/invitations", family.token(),
                "{\"assistedPersonId\":" + person + ",\"inviteeEmail\":\"onboarding-caregiver@alivia.test\"}", null);
        assertEquals(201, invitation.status(), invitation.body());
        var accepted = call("POST", "/api/v1/invitations/" + invitation.body().trim() + "/accept", null,
                "{\"password\":\"" + PASSWORD + "\",\"firstName\":\"Carla\",\"lastName\":\"Prueba\","
                        + "\"phoneNumber\":\"51987654321\"}", null);
        assertEquals(200, accepted.status(), accepted.body());

        var caregiver = account("onboarding-caregiver@alivia.test");
        var read = call("GET", PERSONS + "/" + person, caregiver.token());
        assertEquals(200, read.status(), read.body());
        // A caregiver of one person has no access to the other one.
        assertEquals(403, call("GET", PERSONS + "/" + number(second.body(), "id"), caregiver.token()).status());
    }

    @Test
    void aNewAccountCannotUseTheHouseholdOrTheRelativeOfAnother() throws Exception {
        var owner = signUp("onboarding-owner@alivia.test", "71000002");
        long home = newHousehold(owner, "onboarding-owner@alivia.test");
        var registered = register(owner, home, "72000003");
        assertEquals(201, registered.status(), registered.body());
        var stranger = signUp("onboarding-stranger@alivia.test", "71000003");

        var otherHome = register(stranger, home, "72000004");
        var otherRelative = call("POST", PERSONS, stranger.token(),
                "{\"relativeId\":" + number(registered.body(), "relativeId") + ",\"firstName\":\"Ana\","
                        + "\"lastName\":\"Prueba\",\"dni\":\"72000005\",\"birthDate\":\"1945-01-01\"}", null);

        assertEquals(403, otherHome.status(), otherHome.body());
        assertEquals(403, otherRelative.status(), otherRelative.body());
        assertEquals(0, relativeRecords(stranger));
    }

    @Test
    void aRegistrationThatFailsLeavesNoRelativeRecordBehind() throws Exception {
        var first = signUp("onboarding-first@alivia.test", "71000004");
        var firstHome = newHousehold(first, "onboarding-first@alivia.test");
        assertEquals(201, register(first, firstHome, "72000006").status());
        var late = signUp("onboarding-late@alivia.test", "71000005");
        long lateHome = newHousehold(late, "onboarding-late@alivia.test");

        var repeatedDni = register(late, lateHome, "72000006");

        assertEquals(409, repeatedDni.status(), repeatedDni.body());
        assertEquals(0, relativeRecords(late));
    }

    @Test
    void theAdministratorIsNotTurnedIntoARelative() throws Exception {
        var response = call("POST", PERSONS, admin,
                "{\"firstName\":\"Ana\",\"lastName\":\"Prueba\",\"dni\":\"72000007\",\"birthDate\":\"1945-01-01\"}", null);

        assertEquals(403, response.status(), response.body());
        assertEquals(0, jdbc.queryForObject("select count(*) from relatives r join users u on u.id = r.user_id "
                + "where u.email = 'super-admin@alivia.test'", Integer.class));
    }

    /** An ordinary sign-up, with its email verified. */
    private Account signUp(String email, String dni) {
        long id = iam.signUpUser(email, PASSWORD, "Familia", "Prueba", "+51987654321", "CAREGIVER", 1L, null, dni,
                LocalDate.of(1980, 1, 1));
        var user = accounts.findById(id).orElseThrow();
        user.verifyEmail();
        accounts.save(user);
        return account(email);
    }

    private Account account(String email) {
        var user = accounts.findByEmail(new EmailAddress(email)).orElseThrow();
        return new Account(user.getId(), tokenService.generateAccessToken(user));
    }

    private long newHousehold(Account account, String email) throws Exception {
        var response = call("POST", "/api/v1/households", account.token(),
                "{\"alias\":\"Hogar\",\"phoneNumber\":\"51984123456\",\"contactEmail\":\"" + email + "\","
                        + "\"address\":\"Av. Prueba 1\",\"district\":\"San Borja\",\"city\":\"Lima\","
                        + "\"formattedAddress\":\"Av. Prueba 1, Lima\",\"latitude\":-12.0,\"longitude\":-77.0,"
                        + "\"placeId\":\"probe\"}", null);
        assertEquals(201, response.status(), response.body());
        return number(response.body(), "id");
    }

    private Response register(Account account, long household, String dni) throws Exception {
        return call("POST", PERSONS, account.token(),
                "{\"householdId\":" + household + ",\"firstName\":\"Ana\",\"lastName\":\"Prueba\",\"dni\":\"" + dni
                        + "\",\"phoneNumber\":\"51987654321\",\"birthDate\":\"1945-01-01\",\"consentGiven\":true}", null);
    }

    private int relativeRecords(Account account) {
        return jdbc.queryForObject("select count(*) from relatives where user_id = ?", Integer.class, account.userId());
    }
}
