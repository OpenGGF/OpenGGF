package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.schema.ZoneEventSchemaSidecar;
import com.openggf.game.rewind.snapshot.LevelEventSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;
import com.openggf.game.sonic3k.runtime.S3kScreenShake;
import com.openggf.physics.BackgroundPlaneCollisionProvider;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Framed HCZ component-state preservation and atomic rejection; no trace or ROM input. */
class TestHczComposedRewindState {
    private static final int MANAGER_BYTES = 31;
    private static final int HCZ_HEADER_BYTES = 12;
    private static final int SHAKE_BYTES = 12;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void closeFixture() {
        SessionManager.clear();
    }

    @Test
    void beforeFirstPublicationRoundTripsWithoutAnInstalledRuntime() {
        SessionManager.clear();
        assertFalse(GameServices.hasRuntime());
        var source = new Sonic3kHCZEvents(() -> 0);
        byte[] saved = source.captureHczState();
        assertTrue(Sonic3kHCZEvents.hasValidHczState(saved));
        var restored = new Sonic3kHCZEvents(() -> 0);
        restored.restoreHczState(saved);

        assertArrayEquals(saved, restored.captureHczState());
        assertFalse(restored.screenPublication().valid());
        assertEquals(BackgroundPlaneCollisionProvider.State.INACTIVE, restored.backgroundCollisionState());
        assertFalse(GameServices.hasRuntime(), "construction, validation and capture cannot open a session");
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 8, 12, 16})
    void completePublicationAndAllThreeShakeWordsRoundTrip(int stage) throws Exception {
        var source = populated(stage);
        byte[] saved = source.captureHczState();
        ByteBuffer framed = ByteBuffer.wrap(saved);
        assertEquals(0x48435A32, framed.getInt());
        assertEquals(1, framed.getInt());
        assertEquals(saved.length - HCZ_HEADER_BYTES - SHAKE_BYTES, framed.getInt());
        framed.position(saved.length - SHAKE_BYTES);
        assertEquals(12, framed.getInt(), "timed flag after two real setup calls");
        assertEquals(4, framed.getInt(), "prepared offset");
        assertEquals(-4, framed.getInt(), "last offset is independent of the prepared offset");
        assertTrue(Sonic3kHCZEvents.hasValidHczState(saved));

        var restored = populated(stage == 4 ? 16 : 4);
        restored.setWallOffsetFixed(-1);
        restored.setShakeTimer(3);
        restored.restoreHczState(saved);

        assertArrayEquals(saved, restored.captureHczState(), "complete payload equality, not a preview");
        assertEquals(0xF9FF8000, restored.getWallOffsetFixed(), "full fractional accumulator");
        assertEquals(-0x601, restored.getWallOffsetPixels());
        assertEquals(stage, restored.getAct2BgRoutine());
        assertEquals(12, restored.getScreenShakeFlag());
        var expectedPublication = switch (stage) {
            case 4 -> new Sonic3kHCZEvents.ScreenPublication(true, true,
                    0x680, 0x6FC, -0x181, 0x1FC, -4);
            case 8 -> new Sonic3kHCZEvents.ScreenPublication(true, true,
                    0x680, 0x704, -0x181, 0x1FC, 4);
            case 12, 16 -> new Sonic3kHCZEvents.ScreenPublication(true, false,
                    0x680, 0x6FC, -0x181, 0x1BC, -4);
            default -> throw new AssertionError("unexpected component stage " + stage);
        };
        assertEquals(expectedPublication, restored.screenPublication());
        assertEquals(stage == 4
                        ? new BackgroundPlaneCollisionProvider.State(true, 0x801, 0x500)
                        : BackgroundPlaneCollisionProvider.State.INACTIVE,
                restored.backgroundCollisionState());
        assertEquals(Boolean.TRUE, field(restored, "backgroundInitialized"));
        assertEquals(stage == 8 || stage == 12 ? 7 : 0, field(restored, "redrawRowCountdown"));
    }

    @ParameterizedTest
    @EnumSource(BadPayload.class)
    void malformedPayloadLeavesCompleteStandaloneStateUnchanged(BadPayload kind) throws Exception {
        var target = populated(8);
        byte[] before = target.captureHczState();
        byte[] malformed = malformed(kind);
        assertFalse(Sonic3kHCZEvents.hasValidHczState(malformed), kind.name());

        assertThrows(IllegalArgumentException.class, () -> target.restoreHczState(malformed));
        assertArrayEquals(before, target.captureHczState(), kind + " must change no scalar or shake word");
    }

    @ParameterizedTest
    @EnumSource(BadPayload.class)
    void malformedPayloadLeavesEveryManagerBaseAndExtraFieldUnchanged(BadPayload kind) throws Exception {
        var source = new Sonic3kLevelEventManager();
        source.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        source.setHczPendingPostTransitionCutscene(true);
        source.getHczEventsForTest().restoreHczState(populated(4).captureHczState());
        var sourceSnapshot = source.capture();
        short[] foreground = sourceSnapshot.eventDataFg();
        byte[] background = sourceSnapshot.eventDataBg();
        Arrays.fill(foreground, (short) 0x55AA);
        Arrays.fill(background, (byte) 0x5A);
        var incoming = new LevelEventSnapshot(Sonic3kZoneIds.ZONE_CNZ, 0,
                12, 16, 0x1234, 99, true, foreground, background,
                replaceHczPayload(sourceSnapshot.extra(), malformed(kind)));

        var target = new Sonic3kLevelEventManager();
        target.initLevel(Sonic3kZoneIds.ZONE_HCZ, 1);
        target.getHczEventsForTest().restoreHczState(populated(8).captureHczState());
        LevelEventSnapshot before = target.capture();
        assertDoesNotThrow(() -> target.restore(incoming));
        assertSnapshotEquals(before, target.capture(), kind.name());
    }

    private static Sonic3kHCZEvents populated(int stage) throws Exception {
        var events = new Sonic3kHCZEvents(() -> 0);
        events.init(1);
        events.setAct2BgRoutine(stage);
        events.setWallOffsetFixed(0xF9FF8000);
        events.setBgRoutine(8);
        events.setCarrierP1XFixed(0x12345678);
        events.setCarrierP2TargetX(0x4321);
        set(events, "publicationValid", true);
        set(events, "backgroundInitialized", true);
        set(events, "wallPlane", stage == 4 || stage == 8);
        // Held stage 8 reuses the prepared word and the preceding BG copies.
        // Stages C/10 use quarter-height normal BG with their applied shake.
        set(events, "appliedScreenShakeOffset", stage == 8 ? 4 : -4);
        set(events, "foregroundCopyX", 0x680);
        set(events, "foregroundCopyY", stage == 8 ? 0x704 : 0x6FC);
        set(events, "backgroundCopyX", -0x181);
        set(events, "backgroundCopyY", stage == 12 || stage == 16 ? 0x1BC : 0x1FC);
        set(events, "collisionActive", stage == 4);
        set(events, "collisionCameraDiffX", 0x801);
        set(events, "collisionCameraDiffY", 0x500);
        set(events, "redrawRowCountdown", stage == 8 || stage == 12 ? 7 : 0);
        var shake = (S3kScreenShake) field(events, "screenShake");
        shake.writeFlag(14);
        shake.setup(1, false);
        shake.setup(2, false);
        return events;
    }

    private static byte[] malformed(BadPayload kind) throws Exception {
        var source = populated(4);
        byte[] good = source.captureHczState();
        int scalars = ByteBuffer.wrap(good).getInt(8);
        byte[] changed = good.clone();
        switch (kind) {
            case TRUNCATED_HEADER -> { return Arrays.copyOf(good, HCZ_HEADER_BYTES - 1); }
            case TRUNCATED_SCALARS -> { return Arrays.copyOf(good, HCZ_HEADER_BYTES + scalars - 1); }
            case TRUNCATED_SHAKE -> { return Arrays.copyOf(good, good.length - 1); }
            case WRONG_MAGIC -> ByteBuffer.wrap(changed).putInt(0, 0);
            case WRONG_VERSION -> ByteBuffer.wrap(changed).putInt(4, 2);
            case WRONG_LENGTH -> ByteBuffer.wrap(changed).putInt(8, scalars + 1);
            case LEGACY_SCALARS -> { return ZoneEventSchemaSidecar.capture(source); }
            case BAD_STAGE -> {
                source.setAct2BgRoutine(5);
                return source.captureHczState();
            }
            case BAD_COUNTDOWN -> {
                set(source, "redrawRowCountdown", 16);
                return source.captureHczState();
            }
            case NEGATIVE_COUNTDOWN -> {
                set(source, "redrawRowCountdown", -2);
                return source.captureHczState();
            }
            case NONCANONICAL_BOOLEAN -> {
                set(source, "publicationValid", false);
                byte[] falseEncoding = source.captureHczState();
                int differences = 0;
                for (int index = HCZ_HEADER_BYTES; index < HCZ_HEADER_BYTES + scalars; index++) {
                    if (good[index] != falseEncoding[index]) {
                        assertEquals(1, good[index]);
                        assertEquals(0, falseEncoding[index]);
                        changed[index] = 2;
                        differences++;
                    }
                }
                assertEquals(1, differences, "only the declared boolean's encoding changed");
            }
            case WIDE_COPY_WORD -> {
                set(source, "foregroundCopyY", 0x8000);
                return source.captureHczState();
            }
            case WIDE_FLAG -> ByteBuffer.wrap(changed).putInt(good.length - 12, 0x10000);
            case BELOW_WORD_FLAG -> ByteBuffer.wrap(changed).putInt(good.length - 12, -0x8001);
            case WIDE_OFFSET -> ByteBuffer.wrap(changed).putInt(good.length - 8, 0x10000);
            case WIDE_LAST_OFFSET -> ByteBuffer.wrap(changed).putInt(good.length - 4, 0x10000);
        }
        return changed;
    }

    private static byte[] replaceHczPayload(byte[] extra, byte[] payload) {
        ByteBuffer original = ByteBuffer.wrap(extra);
        original.position(MANAGER_BYTES);
        assertEquals(0, original.get(), "this fixture has no AIZ handler");
        assertEquals(1, original.get(), "this fixture has an HCZ handler");
        int oldLength = original.getInt();
        int suffix = original.position() + oldLength;
        ByteBuffer changed = ByteBuffer.allocate(extra.length - oldLength + payload.length);
        changed.put(extra, 0, MANAGER_BYTES + 2);
        changed.putInt(payload.length).put(payload);
        changed.put(extra, suffix, extra.length - suffix);
        return changed.array();
    }

    private static void assertSnapshotEquals(LevelEventSnapshot expected,
                                             LevelEventSnapshot actual, String label) {
        assertAll(label,
                () -> assertEquals(expected.currentZone(), actual.currentZone()),
                () -> assertEquals(expected.currentAct(), actual.currentAct()),
                () -> assertEquals(expected.eventRoutineFg(), actual.eventRoutineFg()),
                () -> assertEquals(expected.eventRoutineBg(), actual.eventRoutineBg()),
                () -> assertEquals(expected.frameCounter(), actual.frameCounter()),
                () -> assertEquals(expected.timerFrames(), actual.timerFrames()),
                () -> assertEquals(expected.bossActive(), actual.bossActive()),
                () -> assertArrayEquals(expected.eventDataFg(), actual.eventDataFg()),
                () -> assertArrayEquals(expected.eventDataBg(), actual.eventDataBg()),
                () -> assertArrayEquals(expected.extra(), actual.extra()));
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = Sonic3kHCZEvents.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = Sonic3kHCZEvents.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private enum BadPayload {
        TRUNCATED_HEADER, TRUNCATED_SCALARS, TRUNCATED_SHAKE,
        WRONG_MAGIC, WRONG_VERSION, WRONG_LENGTH, LEGACY_SCALARS,
        BAD_STAGE, BAD_COUNTDOWN, NEGATIVE_COUNTDOWN, NONCANONICAL_BOOLEAN, WIDE_COPY_WORD,
        WIDE_FLAG, BELOW_WORD_FLAG, WIDE_OFFSET, WIDE_LAST_OFFSET
    }
}
