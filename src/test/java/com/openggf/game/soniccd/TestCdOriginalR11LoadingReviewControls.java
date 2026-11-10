package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.PatternAtlasRange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Original public source/selected boot and configured EGL; no synthetic Frame or invocation. */
@Isolated class TestCdOriginalR11LoadingReviewControls {
    @TempDir Path root;
    private final TestCdOriginalR11LoadingWorld fixture = new TestCdOriginalR11LoadingWorld();
    @BeforeEach void setup() { fixture.root = root; fixture.setup(); }
    @AfterEach void cleanup() { fixture.cleanup(); }

    @Test void engineAdmissionStaysInTheR11Programme() throws Exception {
        var source = TestCdOriginalBootPrefix.source();
        var attempt = SonicCdGameModule.prepareSelectedProfile(source, new SaveManager(root), 1,
                SonicCdGameModule.ProfileEntry.NEW_GAME, 0);
        var module = attempt.module().orElseThrow();
        try (var boot = TestCdOriginalR11LoadingWorld.boot()) {
            assertNotNull(boot.bootSonicCdSelectedProfile(source, attempt));
            assertTrue(module.originalR11EngineLoadingAssembled());
            assertSame(source, SessionManager.getCurrentWorldSession().getDataSource());
            var selected = module.selectedProfileState();
            assertEquals(CdSelectedProfileVisit.Programme.R11A, selected.programme().orElseThrow());
            assertEquals(1L, selected.calls().stream().filter(c -> c == CdSelectedProfileVisit.Call.RUN_R11_ENTRY).count());
            assertFalse(selected.calls().contains(CdSelectedProfileVisit.Call.RUN_R31_ENTRY));
            assertEquals(1L, selected.calls().stream().filter(c -> c == CdSelectedProfileVisit.Call.IPX_WRITE_READ).count());
            assertEquals(0, module.originalR11EntryFrame().vIntClock().deltaSinceAdmittedCut());
        }
    }

    @Test void repeatedPendingDrawsPreserveTheOriginalNativeCut() throws Exception {
        var source = TestCdOriginalBootPrefix.source();
        var attempt = SonicCdGameModule.prepareSelectedProfile(source, new SaveManager(root), 1,
                SonicCdGameModule.ProfileEntry.NEW_GAME, 0);
        var module = attempt.module().orElseThrow();
        try (var boot = TestCdOriginalR11LoadingWorld.boot()) {
            boot.bootSonicCdSelectedProfile(source, attempt);
            var frame = module.originalR11EntryFrame();
            var before = module.originalR11FirstLoad();
            var actor = frame.captureActor(); var shared = frame.captureShared();
            var services = module.programServicesState();
            module.gameplayFrameController().drawOverlay();
            module.gameplayFrameController().drawOverlay();
            assertFalse(frame.closed());
            assertSame(before, module.originalR11FirstLoad());
            assertSame(services, module.programServicesState());
            assertEquals(actor, frame.captureActor()); assertEquals(shared, frame.captureShared());
        }
    }

    @Test void rendererConstructionFailureRetiresTheOriginalFrame() throws Exception {
        var source = TestCdOriginalBootPrefix.source();
        var attempt = SonicCdGameModule.prepareSelectedProfile(source, new SaveManager(root), 1,
                SonicCdGameModule.ProfileEntry.NEW_GAME, 0);
        var module = attempt.module().orElseThrow();
        try (var boot = TestCdOriginalR11LoadingWorld.boot()) {
            boot.bootSonicCdSelectedProfile(source, attempt);
            var frame = module.originalR11EntryFrame();
            GameServices.graphics().getPatternAtlas().registerRange(PatternAtlasRange.OBJECTS);
            assertThrows(IllegalArgumentException.class, module.gameplayFrameController()::drawOverlay);
            assertTrue(frame.closed());
            assertThrows(IllegalStateException.class, module::originalR11FirstLoad);
        }
    }
}
