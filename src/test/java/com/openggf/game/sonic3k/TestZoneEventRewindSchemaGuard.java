package com.openggf.game.sonic3k;

import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.schema.RewindClassSchema;
import com.openggf.game.rewind.schema.RewindSchemaRegistry;
import com.openggf.game.rewind.schema.ZoneEventSchemaSidecar;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards schema-converted zone-event handlers: every field is either
 * captured or explicitly @RewindTransient, and the fields the legacy
 * hand-written sidecar serialized remain covered.
 */
public class TestZoneEventRewindSchemaGuard {

    private static final List<Class<?>> CONVERTED_HANDLERS = List.of(Sonic3kAIZEvents.class);
    private static final Set<String> AIZ_ALLOWED_TRANSIENT_FIELDS = Set.of("bootstrap");

    /**
     * FIELD names (not getter names) the legacy writeAizState byte layout serialized.
     */
    private static final Set<String> AIZ_LEGACY_FIELDS = Set.of(
            "introSpawned",
            "introMinXLocked",
            "introSidekickMarkerReleased",
            "introNormalRefreshPending",
            "paletteSwapped",
            "boundariesUnlocked",
            "fireMinXLockReached",
            "minibossSpawned",
            "eventsFg4",
            "eventsFg5",
            "bossFlag",
            "battleshipAutoScrollActive",
            "battleshipSpawned",
            "endBossSpawned",
            "battleshipTerrainLoaded",
            "act2TransitionRequested",
            "fireTransitionMutationRequested",
            "postFireHazeActive",
            "fireOverlayTilesLoaded",
            "act2WaitFireDrawActive",
            "appliedTreeRevealChunkCopiesMask",
            "aiz2ResizeRoutine",
            "battleshipWrapX",
            "screenShakeTimer",
            "levelRepeatOffset",
            "battleshipBgYOffset",
            "battleshipSmoothScrollX",
            "battleshipPostScrollCameraX",
            "screenShakeOffsetY",
            "fireBgCopyFixed",
            "fireRiseSpeed",
            "fireWavePhase",
            "fireTransitionFrames",
            "firePhaseFrames",
            "fireOverlayTileCount",
            "fireSequencePhase"
    );

    @Test
    public void convertedHandlersHaveNoUnsupportedFields() {
        for (Class<?> handler : CONVERTED_HANDLERS) {
            RewindClassSchema schema = RewindSchemaRegistry.schemaFor(handler);
            assertTrue(schema.unsupportedFields().isEmpty(),
                    handler.getSimpleName() + " has unsupported rewind fields: "
                            + schema.unsupportedFields());
        }
    }

    @Test
    public void aizSchemaCoversAllLegacySidecarFields() {
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(Sonic3kAIZEvents.class);
        Set<String> captured = schema.capturedFields().stream()
                .map(plan -> plan.field().getName())
                .collect(Collectors.toSet());
        Set<String> missing = AIZ_LEGACY_FIELDS.stream()
                .filter(name -> !captured.contains(name))
                .collect(Collectors.toSet());
        assertTrue(missing.isEmpty(),
                "schema capture lost legacy sidecar fields: " + missing);
    }

    @Test
    public void aizTransientFieldInventoryIsExplicit() {
        Set<String> transientFields = Arrays.stream(Sonic3kAIZEvents.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> Modifier.isTransient(field.getModifiers())
                        || field.isAnnotationPresent(RewindTransient.class))
                .map(field -> field.getName())
                .collect(Collectors.toSet());
        assertEquals(AIZ_ALLOWED_TRANSIENT_FIELDS, transientFields,
                "AIZ transient field inventory changed; classify new fields as captured or structural");
    }

    @Test
    public void sidecarRestoreRejectsNullBytes() {
        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        assertThrows(NullPointerException.class, () -> ZoneEventSchemaSidecar.restore(events, null));
    }

    @Test
    public void sidecarRestoreRejectsShortPayloadWithoutPartialMutation() {
        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        byte[] full = ZoneEventSchemaSidecar.capture(events);
        byte[] shortPayload = Arrays.copyOf(full, full.length - 1);

        events.setIntroSpawned(true);

        assertThrows(IllegalArgumentException.class,
                () -> ZoneEventSchemaSidecar.restore(events, shortPayload));
        assertTrue(events.isIntroSpawned(),
                "length rejection must happen before restore mutates AIZ fields");
    }

    @Test
    public void sidecarRestoreRejectsLongPayloadWithoutPartialMutation() {
        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        byte[] full = ZoneEventSchemaSidecar.capture(events);
        byte[] longPayload = Arrays.copyOf(full, full.length + 1);

        events.setIntroSpawned(true);

        assertThrows(IllegalArgumentException.class,
                () -> ZoneEventSchemaSidecar.restore(events, longPayload));
        assertTrue(events.isIntroSpawned(),
                "length rejection must happen before restore mutates AIZ fields");
    }
}
