package com.openggf.mods.code;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.mode.GameplayFrameDisposition;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.runtime.OwnerBoundCallbacks;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestOwnerControllerNativeInput {
    private static final String OWNER = "controller-owner";

    private static final class Fixture {
        final ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        final List<Set<String>> disabled = new ArrayList<>();
        final List<Set<String>> persisted = new ArrayList<>();
        final ModFaultBoundary boundary = new ModFaultBoundary(Map.of("dependent", Set.of(OWNER)), findings,
                owners -> {
                    persisted.add(Set.copyOf(owners));
                    return new ModStateSaveResult.Saved();
                }, owners -> disabled.add(Set.copyOf(owners)));

        GameplayFrameController bind(GameplayFrameController controller) {
            return new OwnerBoundCallbacks(OWNER, boundary, 8, Map.of())
                    .bind(GameplayFrameController.class, controller);
        }
    }

    @Test void nullNativeInputQuarantinesItsVerifiedOwnerAndDependents() {
        var fixture = new Fixture();
        int[] calls = {0};
        var controller = fixture.bind(new GameplayFrameController() {
            public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) { return true; }
            public void afterTick(CourseControl course, boolean advanced) { }
            public LogicalInputSnapshot nativeInput(CourseControl course) {
                assertEquals(OWNER, OwnerCallbackScope.current());
                calls[0]++;
                return null;
            }
        });
        assertEquals(GameplayFrameDisposition.ADVANCE_CONTROLLED,
                controller.beforeFrame(null, LogicalInputSnapshot.neutral()));

        var failure = assertThrows(ModFaultBoundary.CallbackAborted.class, () -> controller.nativeInput(null));

        assertEquals(1, calls[0]);
        assertEquals(OWNER, failure.owner());
        assertInstanceOf(NullPointerException.class, failure.getCause());
        var quarantined = Set.of(OWNER, "dependent");
        assertEquals(quarantined, failure.disabledOwners());
        assertFalse(fixture.boundary.isOwnerAvailable(OWNER));
        assertFalse(fixture.boundary.isOwnerAvailable("dependent"));
        assertTrue(fixture.boundary.isOwnerAvailable("unrelated"));
        assertEquals(List.of(quarantined), fixture.disabled);
        assertEquals(List.of(quarantined), fixture.persisted);
        assertEquals("MOD_CALLBACK_FAILED", fixture.findings.findingsFor(OWNER).getFirst().code());
        assertTrue(fixture.findings.findingsFor("dependent").isEmpty());
        assertNull(OwnerCallbackScope.current());
    }

    @Test void validControllerNativeInputPassesThroughTheOwnerBoundary() {
        var fixture = new Fixture();
        var supplied = LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT, 0, 0, 0, false, false),
                PlayerInputState.neutral());
        int[] calls = {0};
        var controller = fixture.bind(new GameplayFrameController() {
            public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) { return true; }
            public void afterTick(CourseControl course, boolean advanced) { }
            public LogicalInputSnapshot nativeInput(CourseControl course) {
                assertEquals(OWNER, OwnerCallbackScope.current());
                calls[0]++;
                return supplied;
            }
        });

        assertEquals(GameplayFrameDisposition.ADVANCE_CONTROLLED,
                controller.beforeFrame(null, LogicalInputSnapshot.neutral()));
        assertSame(supplied, controller.nativeInput(null));
        assertEquals(1, calls[0]);
        assertTrue(fixture.boundary.isOwnerAvailable(OWNER));
        assertTrue(fixture.findings.snapshot().isEmpty());
        assertTrue(fixture.disabled.isEmpty());
        assertTrue(fixture.persisted.isEmpty());
        assertNull(OwnerCallbackScope.current());
    }

    @Test void legacyControllerStillDefaultsToNeutralInput() {
        var fixture = new Fixture();
        var live = LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT, 0, 0, 0, false, false),
                PlayerInputState.neutral());
        var controller = fixture.bind(new GameplayFrameController() {
            public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
                assertEquals(OWNER, OwnerCallbackScope.current());
                assertSame(live, input);
                return true;
            }
            public void afterTick(CourseControl course, boolean advanced) { }
        });

        assertEquals(GameplayFrameDisposition.ADVANCE_CONTROLLED, controller.beforeFrame(null, live));
        assertEquals(LogicalInputSnapshot.neutral(), controller.nativeInput(null));
        assertTrue(fixture.boundary.isOwnerAvailable(OWNER));
        assertTrue(fixture.findings.snapshot().isEmpty());
        assertNull(OwnerCallbackScope.current());
    }

    @Test void nativeInputDispositionDoesNotEagerlyQueryControllerInput() {
        var fixture = new Fixture();
        var live = LogicalInputSnapshot.neutral();
        var controller = fixture.bind(new GameplayFrameController() {
            public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
                fail("beforeFrame supplies the native-input decision");
                return false;
            }
            public GameplayFrameDisposition beforeFrame(CourseControl course, LogicalInputSnapshot input) {
                assertEquals(OWNER, OwnerCallbackScope.current());
                assertSame(live, input);
                return GameplayFrameDisposition.ADVANCE_NATIVE_INPUT;
            }
            public void afterTick(CourseControl course, boolean advanced) { }
            public LogicalInputSnapshot nativeInput(CourseControl course) {
                fail("native-input disposition must not query controller input");
                return null;
            }
        });

        assertEquals(GameplayFrameDisposition.ADVANCE_NATIVE_INPUT, controller.beforeFrame(null, live));
        assertTrue(fixture.boundary.isOwnerAvailable(OWNER));
        assertTrue(fixture.findings.snapshot().isEmpty());
        assertNull(OwnerCallbackScope.current());
    }
}
