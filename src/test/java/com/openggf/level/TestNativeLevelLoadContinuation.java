package com.openggf.level;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.internal.NativeFreshLevelLoadProfile;
import com.openggf.game.internal.NativeLevelLoadInputs;
import com.openggf.game.internal.NativeLevelLoadPhaseAdmission;
import com.openggf.game.internal.NativeLevelLoadPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real continuation execution, with no ROM timing authority or gameplay values supplied by the test. */
class TestNativeLevelLoadContinuation {
    private static final NativeLevelLoadInputs INPUTS = NativeLevelLoadInputs.fromRom(
            RomByteReader.fromBytes(new byte[]{1, 2, 3, 4}),
            List.of(new NativeLevelLoadInputs.RegionSelection(0, 4)), List.of(0));

    @Test void orderedZeroInterruptsResumeOnlyPreparedWorkAndPreserveOneShotPreparation() {
        var events = new ArrayList<String>();
        var nativeProfile = mock(NativeFreshLevelLoadProfile.class);
        var section = phase(NativeLevelLoadPhaseAdmission.Phase.INITIAL_OBJECTS,
                () -> events.add("seed"), () -> events.add("children"));
        var fixture = fixture(new NativeLevelLoadPlan(List.of(section)), nativeProfile);
        doAnswer(call -> { events.add("vint0"); return null; }).when(nativeProfile).serviceLoadZeroRoutineVInt();
        var decisions = new java.util.ArrayDeque<>(List.of(
                NativeLevelLoadPhaseAdmission.Decision.VINT_0, NativeLevelLoadPhaseAdmission.Decision.HOLD));
        fixture.attempt.execute(() -> fixture.module, 0, () -> 7, handle -> {
            assertEquals(7, handle.loadOrdinal()); assertEquals(1, handle.phaseOrdinal());
            return decisions.removeFirst();
        });
        assertFalse(fixture.attempt.isComplete());
        assertEquals(List.of("seed", "vint0"), events);
        var held = fixture.attempt.preparedPhase();
        fixture.attempt.resume(handle -> { assertSame(held, handle); return NativeLevelLoadPhaseAdmission.Decision.RESUME; });
        assertTrue(fixture.attempt.isComplete());
        fixture.attempt.resume(handle -> { fail("completed work must not be admitted again"); return null; });
        assertEquals(List.of("seed", "vint0", "children"), events);
        verify(nativeProfile).serviceLoadZeroRoutineVInt();
    }

    @Test void placementServicePrecedesObjectSeedAndObjectServiceDoesNotReseedIt() {
        var events = new ArrayList<String>();
        var clock = new AtomicInteger(100);
        var seed = new AtomicInteger(-1);
        var nativeProfile = mock(NativeFreshLevelLoadProfile.class);
        doAnswer(call -> { clock.incrementAndGet(); events.add("vint0"); return null; })
                .when(nativeProfile).serviceLoadZeroRoutineVInt();
        var fixture = fixture(new NativeLevelLoadPlan(List.of(
                phase(NativeLevelLoadPhaseAdmission.Phase.INITIAL_PLACEMENT,
                        () -> events.add("placement"), () -> { }),
                phase(NativeLevelLoadPhaseAdmission.Phase.INITIAL_OBJECTS,
                        () -> { seed.set(clock.get()); events.add("seed"); }, () -> events.add("children")))), nativeProfile);
        var decisions = new java.util.ArrayDeque<>(List.of(
                NativeLevelLoadPhaseAdmission.Decision.VINT_0, NativeLevelLoadPhaseAdmission.Decision.HOLD));
        fixture.attempt.execute(() -> fixture.module, 0, () -> 1, phase -> decisions.removeFirst());
        assertEquals(-1, seed.get(), "real object initialization must wait for placement release");
        assertEquals(List.of("placement", "vint0"), events);
        decisions.addAll(List.of(NativeLevelLoadPhaseAdmission.Decision.RESUME,
                NativeLevelLoadPhaseAdmission.Decision.VINT_0, NativeLevelLoadPhaseAdmission.Decision.RESUME));
        fixture.attempt.resume(phase -> decisions.removeFirst());
        assertEquals(101, seed.get(), "placement's native service must affect the live seed read");
        assertEquals(102, clock.get());
        assertEquals(List.of("placement", "vint0", "seed", "vint0", "children"), events);
        assertTrue(fixture.attempt.isComplete());
    }

    @Test void nativeWaitsDoNotCreatePhasesOrServiceInterrupts() {
        var ready = new AtomicBoolean(); var executed = new AtomicInteger();
        var nativeProfile = mock(NativeFreshLevelLoadProfile.class);
        var fixture = fixture(new NativeLevelLoadPlan(List.of(
                NativeLevelLoadPlan.Section.waitFor(ready::get),
                phase(NativeLevelLoadPhaseAdmission.Phase.LEVEL_DATA_DECODE, executed::incrementAndGet, () -> { }))), nativeProfile);
        fixture.attempt.execute(() -> fixture.module, 0, () -> 1, phase -> { fail("native wait is not a phase"); return null; });
        assertNull(fixture.attempt.preparedPhase()); assertEquals(0, executed.get());
        fixture.attempt.resume(phase -> { fail("native wait remains closed"); return null; });
        ready.set(true);
        fixture.attempt.resume(phase -> NativeLevelLoadPhaseAdmission.Decision.RESUME);
        assertEquals(1, executed.get()); assertTrue(fixture.attempt.isComplete());
        verifyNoInteractions(nativeProfile);
    }

    @Test void failedPreparationCannotRetryItsPartialWrites() {
        var writes = new AtomicInteger(); var failure = new IllegalStateException("decode failed");
        var fixture = fixture(new NativeLevelLoadPlan(List.of(phase(
                NativeLevelLoadPhaseAdmission.Phase.LEVEL_DATA_DECODE,
                () -> { writes.incrementAndGet(); throw failure; }, () -> fail("unreached successor")))),
                mock(NativeFreshLevelLoadProfile.class));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> fixture.attempt.execute(
                () -> fixture.module, 0, () -> 1, NativeLevelLoadPhaseAdmission.IMMEDIATE)));
        assertNull(fixture.attempt.preparedPhase());
        assertThrows(IllegalStateException.class, () -> fixture.attempt.resume(NativeLevelLoadPhaseAdmission.IMMEDIATE));
        assertEquals(1, writes.get());
    }

    @Test void successivePhasesCanCommitInOneCallWithPositiveOrderedIdentity() {
        var seen = new ArrayList<Integer>();
        var fixture = fixture(new NativeLevelLoadPlan(List.of(
                phase(NativeLevelLoadPhaseAdmission.Phase.DISPLAY_CLEAR, () -> { }, () -> { }),
                phase(NativeLevelLoadPhaseAdmission.Phase.LEVEL_SETUP, () -> { }, () -> { }))),
                mock(NativeFreshLevelLoadProfile.class));
        fixture.attempt.execute(() -> fixture.module, 0, () -> 1,
                phase -> { seen.add(phase.phaseOrdinal()); return NativeLevelLoadPhaseAdmission.Decision.RESUME; });
        assertEquals(List.of(1, 2), seen); assertTrue(fixture.attempt.isComplete());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = NativeLevelLoadPhaseAdmission.Phase.class,
            names = {"INITIAL_PLACEMENT", "INITIAL_OBJECTS", "INITIAL_PRESENTATION"})
    void callerReturningAtPreparedInitialGateStillRestoresPlayersBeforeChildren(
            NativeLevelLoadPhaseAdmission.Phase heldPhase) {
        var events = new ArrayList<String>();
        var fixture = fixture(new NativeLevelLoadPlan(List.of(phase(
                heldPhase, () -> events.add("players"), () -> events.add("children")))),
                mock(NativeFreshLevelLoadProfile.class));
        fixture.attempt.execute(() -> fixture.module, 0, () -> 1,
                phase -> NativeLevelLoadPhaseAdmission.Decision.HOLD);
        var manager = mock(LevelManager.class);
        manager.nativeLoads = new NativeLevelLoadController();
        manager.nativeLoads.pending = new NativeLevelLoadController.PendingLoad(
                fixture.attempt, new LevelLoadContext(), 0, LevelLoadMode.FULL, null);
        NativeLevelLoadAccess.afterPlayerAssembly(manager, () -> events.add("restore"));
        assertEquals(List.of("players", "restore"), events);
        fixture.attempt.resume(NativeLevelLoadPhaseAdmission.IMMEDIATE);
        assertEquals(List.of("players", "restore", "children"), events);
    }

    @Test void displayResumePrecedesTheFirstRealTitleInitializationScan() {
        var events = new ArrayList<String>();
        var fixture = fixture(new NativeLevelLoadPlan(List.of(
                phase(NativeLevelLoadPhaseAdmission.Phase.DISPLAY_CLEAR, () -> { }, () -> { }),
                NativeLevelLoadPlan.Section.waitFor(() -> false))), mock(NativeFreshLevelLoadProfile.class));
        fixture.attempt.execute(() -> fixture.module, 0, () -> 1,
                phase -> NativeLevelLoadPhaseAdmission.Decision.HOLD);
        var manager = mock(LevelManager.class);
        manager.nativeLoads = new NativeLevelLoadController();
        manager.nativeLoads.pending = new NativeLevelLoadController.PendingLoad(
                fixture.attempt, new LevelLoadContext(), 0, LevelLoadMode.FULL, null);
        NativeLevelLoadAccess.afterTitlePrefix(manager, () -> events.add("title-init"));
        assertTrue(NativeLevelLoadAccess.beginTitleScan(manager).parked());
        assertTrue(events.isEmpty(), "display resume cannot initialize the next title scan");
        assertFalse(NativeLevelLoadAccess.beginTitleScan(manager).parked());
        assertEquals(List.of("title-init"), events);
        NativeLevelLoadAccess.beginTitleScan(manager);
        assertEquals(List.of("title-init"), events, "initialization belongs to one scan");
    }

    private static NativeLevelLoadPlan.Section phase(NativeLevelLoadPhaseAdmission.Phase phase,
                                                     Runnable prepare, Runnable commit) {
        return new NativeLevelLoadPlan.Section(phase, () -> INPUTS, () -> true,
                List.of(new InitStep("prepare", "test real work", prepare)),
                List.of(new InitStep("commit", "test native successor", commit)));
    }

    private record Fixture(LevelLoadAttempt attempt, GameModule module) { }
    private static Fixture fixture(NativeLevelLoadPlan plan, NativeFreshLevelLoadProfile delegate) {
        var profile = mock(LevelInitProfile.class, withSettings().extraInterfaces(NativeFreshLevelLoadProfile.class));
        when(((NativeFreshLevelLoadProfile) profile).freshLevelLoadPlan(any())).thenReturn(plan);
        doAnswer(call -> { delegate.serviceLoadZeroRoutineVInt(); return null; })
                .when((NativeFreshLevelLoadProfile) profile).serviceLoadZeroRoutineVInt();
        var module = mock(GameModule.class); when(module.getLevelInitProfile()).thenReturn(profile);
        return new Fixture(new LevelLoadAttempt(null, null, LevelLoadMode.FULL, new LevelLoadContext()), module);
    }
}
