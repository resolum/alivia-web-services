package com.resolum.alivia.assets;

import com.resolum.alivia.assets.interfaces.rest.transform.TableEntityTag;
import com.resolum.alivia.assets.interfaces.rest.transform.TableEntityTag.Relation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The entity tag of a table: installation and version, compared strictly. */
class TableEntityTagTest {

    private static Relation relation(String header) {
        return TableEntityTag.relation(header, 3L, 12L);
    }

    @Test
    void theTagIsTheInstallationAndTheVersionQuoted() {
        assertEquals("\"3.12\"", TableEntityTag.of(3L, 12L));
        assertEquals("\"900004.0\"", TableEntityTag.of(900004L, 0L));
    }

    @Test
    void theSameInstallationAndVersionIsUpToDateInAnyOfTheAcceptedForms() {
        for (var header : new String[]{"\"3.12\"", "W/\"3.12\"", " \"3.1\" , \"3.12\" ", "\"8.12\", W/\"3.12\"", "*"}) {
            assertEquals(Relation.UP_TO_DATE, relation(header), header);
        }
    }

    @Test
    void anOlderVersionOrAnotherInstallationIsDifferent() {
        for (var header : new String[]{"\"3.11\"", "\"3.0\"", "\"8.12\"", "\"8.99\"", "\"8.1\", \"9.1\""}) {
            assertEquals(Relation.DIFFERENT, relation(header), header);
        }
    }

    @Test
    void aHigherVersionOfThisInstallationIsAhead() {
        for (var header : new String[]{"\"3.13\"", "W/\"3.40\"", "\"8.12\", \"3.13\""}) {
            assertEquals(Relation.AHEAD, relation(header), header);
        }
    }

    @Test
    void theExactTagWinsOverAnAheadOne() {
        assertEquals(Relation.UP_TO_DATE, relation("\"3.40\", \"3.12\""));
    }

    @Test
    void anythingThatIsNotATagOfThisFormMatchesNothing() {
        for (var header : new String[]{null, "", "   ", "garbage", "12", "\"12\"", "\"3\"", "\"3.\"", "\".12\"", "\"3.12.1\"",
                "\"3.012\"", "\"03.12\"", "\"-3.12\"", "\"3.-12\"", "\"3.+12\"", "\"3. 12\"", "\"3.1 2\"", "\"a.b\"", "\"3.12",
                "3.12\"", "\"\"", "\"3.99999999999999999999\"", "\"3.\u0661\u0662\"", "W/", ","}) {
            assertEquals(Relation.DIFFERENT, relation(header), String.valueOf(header));
        }
    }
}
