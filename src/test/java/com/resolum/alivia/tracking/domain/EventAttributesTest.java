package com.resolum.alivia.tracking.domain;

import com.resolum.alivia.tracking.domain.exceptions.InvalidTrackingData;
import com.resolum.alivia.tracking.domain.model.valueobjects.EventAttributes;
import com.resolum.alivia.tracking.domain.model.valueobjects.RejectionReason;
import com.resolum.alivia.tracking.domain.model.valueobjects.TrackingPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The free map of an event is small, scalar and never a way to carry audio. */
class EventAttributesTest {

    private static final TrackingPolicy POLICY = TrackingPolicy.defaults();

    private static RejectionReason reasonOf(Map<String, Object> raw) {
        try {
            EventAttributes.of(raw, POLICY);
        } catch (InvalidTrackingData invalid) {
            return invalid.reason();
        }
        return null;
    }

    @Test
    void scalarValuesAreKeptAndNothingIsAnEmptyMap() {
        var attributes = EventAttributes.of(Map.of("state", "closed", "elapsedMs", 700, "ok", true, "ratio", 0.5), POLICY);

        assertThat(attributes.values()).hasSize(4).containsEntry("state", "closed");
        assertThat(EventAttributes.of(null, POLICY).isEmpty()).isTrue();
        assertThat(EventAttributes.of(Map.of(), POLICY).isEmpty()).isTrue();
        assertThat(EventAttributes.empty().values()).isEmpty();
    }

    @Test
    void theValuesCannotBeChangedAfterwards() {
        var source = new HashMap<String, Object>(Map.of("state", "closed"));
        var attributes = EventAttributes.of(source, POLICY);
        source.put("other", 1);

        assertThat(attributes.values()).containsOnlyKeys("state");
        assertThatThrownBy(() -> attributes.values().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nestedValuesNullsAndBadKeysAreRefused() {
        assertThat(reasonOf(Map.of("list", List.of(1)))).isEqualTo(RejectionReason.ATTRIBUTES_INVALID);
        assertThat(reasonOf(Map.of("map", Map.of("a", 1)))).isEqualTo(RejectionReason.ATTRIBUTES_INVALID);
        assertThat(reasonOf(new HashMap<>(Map.of("ok", "x")) {{ put("nothing", null); }}))
                .isEqualTo(RejectionReason.ATTRIBUTES_INVALID);
        assertThat(reasonOf(Map.of("has space", 1))).isEqualTo(RejectionReason.ATTRIBUTES_INVALID);
        assertThat(reasonOf(Map.of("a.b", 1))).isEqualTo(RejectionReason.ATTRIBUTES_INVALID);
        assertThat(reasonOf(Map.of("$set", 1))).isEqualTo(RejectionReason.ATTRIBUTES_INVALID);
        assertThat(reasonOf(Map.of("k".repeat(41), 1))).isEqualTo(RejectionReason.ATTRIBUTES_INVALID);
        assertThat(reasonOf(Map.of("text", "x".repeat(257)))).isEqualTo(RejectionReason.ATTRIBUTES_INVALID);
    }

    @Test
    void theNumberOfKeysAndTheSizeAreBounded() {
        var tooMany = new LinkedHashMap<String, Object>();
        for (int i = 0; i < 21; i++) tooMany.put("k" + i, 1);
        assertThat(reasonOf(tooMany)).isEqualTo(RejectionReason.ATTRIBUTES_INVALID);

        var twenty = new LinkedHashMap<String, Object>();
        for (int i = 0; i < 20; i++) twenty.put("k" + i, 1);
        assertThat(reasonOf(twenty)).isNull();

        var big = new LinkedHashMap<String, Object>();
        for (int i = 0; i < 9; i++) big.put("key" + i, "x".repeat(256));
        assertThat(reasonOf(big)).isEqualTo(RejectionReason.ATTRIBUTES_INVALID);
    }

    @Test
    void theNamesOfAudioAreForbiddenInAnyCase() {
        for (var name : new String[]{"audio", "Audio", "pcm", "wav", "samples", "Transcript"}) {
            assertThat(reasonOf(Map.of(name, "x"))).as(name).isEqualTo(RejectionReason.FORBIDDEN_ATTRIBUTE);
        }
    }

    @Test
    void thePolicyRejectsLimitsThatMakeNoSense() {
        assertThatThrownBy(() -> new TrackingPolicy(0, 1, 1, Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrackingPolicy(1, 1, 1, Duration.ofSeconds(-1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(TrackingPolicy.defaults().clockTolerance()).isEqualTo(Duration.ofMinutes(5));
        assertThat(TrackingPolicy.defaults().maxAttributeBytes()).isEqualTo(2048);
    }
}
