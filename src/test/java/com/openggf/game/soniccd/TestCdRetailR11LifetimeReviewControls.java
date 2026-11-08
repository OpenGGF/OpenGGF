package com.openggf.game.soniccd;

import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.save.SaveManager;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Real selected-source/slot/session controls at the closed pre-LevelStart cut.
 * No DEV entry, synthetic Frame/snapshot, source substitution or ordinary service. */
@Isolated
class TestCdRetailR11LifetimeReviewControls {
    @TempDir Path root;
    @BeforeEach void setup() { TestEnvironment.resetAll(); SessionManager.clear(); }
    @AfterEach void cleanup() { SessionManager.clear(); TestEnvironment.resetAll(); }

    @Test void cancellingTheFirstWorldBeforeEntryConsumesTheOriginalAttempt() throws Exception {
        var source = TestCdOriginalBootPrefix.source();
        var attempt = SonicCdGameModule.prepareSelectedProfile(source, new SaveManager(root), 1,
                SonicCdGameModule.ProfileEntry.NEW_GAME, 0);
        var module = attempt.module().orElseThrow();
        var world = attempt.openSession().getWorldSession();
        var slot = root.resolve("scd/slot1.json");
        boolean existed = Files.exists(slot);
        var disk = existed ? Files.readAllBytes(slot) : new byte[0];
        var services = module.programServicesState();
        SessionManager.clear();
        assertNull(SessionManager.getCurrentWorldSession());
        assertThrows(IllegalStateException.class, attempt::openSession);
        assertNull(SessionManager.getCurrentWorldSession());
        assertEquals(existed, Files.exists(slot));
        if (existed) assertArrayEquals(disk, Files.readAllBytes(slot));
        assertSame(services, module.programServicesState());
        assertNotNull(world);
    }

    @Test void fullRestoreRejectsPendingInputWithoutDiscardingItOrClosingTheFrame() throws Exception {
        var entry = enter();
        var snapshot = entry.registry.capture();
        var services = entry.module.programServicesState();
        entry.frame.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> entry.registry.restore(snapshot));
        assertThrows(IllegalStateException.class, entry.frame::captureActor);
        assertFalse(entry.frame.closed());
        entry.frame.clearPendingInput();
        assertDoesNotThrow(() -> entry.registry.restore(snapshot));
        assertSame(entry.frame, entry.module.originalR11EntryFrame());
        assertSame(services, entry.module.programServicesState());
    }

    @Test void courseRestoreRejectsPendingInputWithoutDiscardingItOrClosingTheFrame() throws Exception {
        var entry = enter();
        var snapshot = entry.registry.captureCourse();
        var services = entry.module.programServicesState();
        entry.frame.prepareInput(0);
        assertThrows(IllegalStateException.class, () -> entry.registry.restoreCourse(snapshot));
        assertThrows(IllegalStateException.class, entry.frame::captureShared);
        assertFalse(entry.frame.closed());
        entry.frame.clearPendingInput();
        assertDoesNotThrow(() -> entry.registry.restoreCourse(snapshot));
        assertSame(entry.frame, entry.module.originalR11EntryFrame());
        assertSame(services, entry.module.programServicesState());
    }

    @Test void foreignThreadCannotObserveCaptureOrRestoreTheOriginalEntry() throws Exception {
        var entry = enter();
        var full = entry.registry.capture();
        var course = entry.registry.captureCourse();
        var failure = new AtomicReference<Throwable>();
        var worker = new Thread(() -> {
            try {
                assertAll(
                        () -> assertThrows(IllegalStateException.class, entry.module::originalR11EntryFrame),
                        () -> assertThrows(IllegalStateException.class, entry.registry::capture),
                        () -> assertThrows(IllegalStateException.class, () -> entry.registry.restore(full)),
                        () -> assertThrows(IllegalStateException.class, () -> entry.registry.restoreCourse(course)));
            } catch (Throwable original) { failure.set(original); }
        }, "retail-r11-foreign-thread-control");
        worker.start();
        worker.join();
        assertNull(failure.get(), () -> String.valueOf(failure.get()));
        assertFalse(entry.frame.closed());
        assertDoesNotThrow(() -> entry.registry.restore(full));
        assertDoesNotThrow(() -> entry.registry.restoreCourse(course));
        assertSame(entry.frame, entry.module.originalR11EntryFrame());
    }

    @Test void reopeningTheGameplayContextDoesNotMigrateTheOriginalEntryOrRegistry() throws Exception {
        var entry = enter();
        var full = entry.registry.capture();
        var course = entry.registry.captureCourse();
        var originalMode = SessionManager.getCurrentGameplayMode();
        var replacement = SessionManager.reopenGameplaySession(HardwareReadinessAdmissionPolicy.LIVE);
        assertNotSame(originalMode, replacement);
        assertSame(entry.world, replacement.getWorldSession());
        assertAll(
                () -> assertThrows(IllegalStateException.class, entry.module::originalR11EntryFrame),
                () -> assertThrows(IllegalStateException.class, entry.registry::capture),
                () -> assertThrows(IllegalStateException.class, () -> entry.registry.restore(full)),
                () -> assertThrows(IllegalStateException.class, () -> entry.registry.restoreCourse(course)));
    }

    private Entry enter() throws Exception {
        var source = TestCdOriginalBootPrefix.source();
        var attempt = SonicCdGameModule.prepareSelectedProfile(source, new SaveManager(root), 1,
                SonicCdGameModule.ProfileEntry.NEW_GAME, 0);
        var module = attempt.module().orElseThrow();
        var world = attempt.openSession().getWorldSession();
        var boundary = assertThrows(CdSelectedProfileVisit.Unowned.class, () -> module.createGame(source));
        assertEquals(CdSelectedProfileVisit.Boundary.R11_LEVEL_START_LOADING, boundary.boundary());
        var frame = module.originalR11EntryFrame();
        var registry = new RewindRegistry();
        WorldSessionPolicyAccess.registerRewind(world, registry);
        return new Entry(module, world, frame, registry);
    }

    private record Entry(SonicCdGameModule module, WorldSession world,
                         CdPlayerFrameRuntime frame, RewindRegistry registry) { }
}
