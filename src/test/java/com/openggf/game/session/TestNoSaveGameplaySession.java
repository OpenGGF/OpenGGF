package com.openggf.game.session;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.save.RuntimeSaveContext;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SaveSnapshotProvider;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Launch isolation is enforced before a challenge can replace a stock session. */
class TestNoSaveGameplaySession {
    @TempDir Path saves;
    private final GameDataSource source = mock(GameDataSource.class);
    private final GameModule stock = new Sonic2GameModule();

    @BeforeEach void setup() {
        TestEnvironment.resetAll();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        EngineServices.current().configuration().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        EngineServices.current().configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
    }

    @AfterEach void cleanup() {
        SessionManager.clear();
        EngineServices.current().configuration().clearSessionOverrides();
        TestEnvironment.resetAll();
    }

    private GameModule challenge() {
        return new DelegatingGameModule(stock, "test-challenge") {
            @Override public boolean requiresNoSaveSession() { return true; }
        };
    }

    private GameplayModeContext open(GameModule module, SaveSessionContext save) {
        return SessionManager.openGameplaySession(stock, module, source, save);
    }

    @Test void nullContextGetsAnExplicitLaunchLocalNoSaveTeamWithoutInheritingTheOldSlot() {
        var oldSave = SaveSessionContext.forSlot("s2", 2, new SelectedTeam("knuckles", List.of("tails")), 3, 1);
        var old = open(stock, oldSave);
        EngineServices.current().configuration().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        var launched = open(challenge(), null);
        var save = launched.getWorldSession().getSaveSessionContext();
        assertNotSame(old, launched);
        assertNotNull(save);
        assertTrue(save.activeSlot().isEmpty());
        assertEquals("s2", save.gameCode());
        assertEquals(new SelectedTeam("tails", List.of()), save.selectedTeam());
        assertEquals(0, save.startZone());
        assertEquals(0, save.startAct());
        assertEquals(2, oldSave.activeSlot().orElseThrow());
        assertEquals("knuckles", oldSave.selectedTeam().mainCharacter());
    }

    @Test void suppliedSlotIsRejectedBeforeClosingTheExistingWorld() {
        var slot = SaveSessionContext.forSlot("s2", 1, new SelectedTeam("sonic", List.of()), 0, 0);
        var old = open(stock, slot);
        var closes = new java.util.concurrent.atomic.AtomicInteger();
        old.setHardwareTimingReplayCloseHook(closes::incrementAndGet);
        assertThrows(IllegalArgumentException.class, () -> open(challenge(), slot));
        assertSame(old, SessionManager.getCurrentGameplayMode());
        assertSame(old.getWorldSession(), SessionManager.getCurrentWorldSession());
        assertEquals(0, closes.get());
        assertSame(slot, old.getWorldSession().getSaveSessionContext());
    }

    @Test void explicitNoSaveContextAndStockLaunchContextsRetainTheirIdentity() {
        var noSave = SaveSessionContext.noSave("s2", new SelectedTeam("sonic", List.of("tails")), 4, 1);
        assertSame(noSave, open(challenge(), noSave).getWorldSession().getSaveSessionContext());
        assertNull(open(stock, null).getWorldSession().getSaveSessionContext());
        var slot = SaveSessionContext.forSlot("s2", 2, new SelectedTeam("sonic", List.of()), 2, 1);
        assertSame(slot, open(stock, slot).getWorldSession().getSaveSessionContext());
    }

    @Test void policyCallbackFailurePreservesTheExistingWorldAndTheNextLaunchRecovers() {
        var old = open(stock, null);
        var failure = new IllegalStateException("rejected launch policy");
        GameModule failing = new DelegatingGameModule(stock, "failed-policy") {
            @Override public boolean requiresNoSaveSession() { throw failure; }
        };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> open(failing, null)));
        assertSame(old, SessionManager.getCurrentGameplayMode());
        assertNotNull(open(challenge(), null).getWorldSession().getSaveSessionContext());
    }

    @Test void repeatedChallengeSaveRequestsLeaveNativeSlotBytesUntouched() throws Exception {
        var manager = new SaveManager(saves);
        manager.writeSlot("s2", 1, Map.of("marker", "native-progress"));
        Path slot = saves.resolve("s2/slot1.json");
        byte[] original = Files.readAllBytes(slot);
        var snapshots = mock(SaveSnapshotProvider.class);
        for (int launch = 0; launch < 3; launch++) {
            var runtime = open(challenge(), null);
            var save = runtime.getWorldSession().getSaveSessionContext();
            var context = RuntimeSaveContext.forGameplayMode(runtime, save);
            for (var reason : SaveReason.values()) {
                save.requestSave(reason, context, snapshots, manager);
                save.requestSaveAsync(reason, context, snapshots, manager);
            }
            SessionManager.reopenGameplaySession(HardwareReadinessAdmissionPolicy.LIVE);
            assertSame(save, SessionManager.getCurrentWorldSession().getSaveSessionContext());
            SessionManager.closeGameplaySession();
            assertNull(SessionManager.getCurrentWorldSession());
            assertNull(SessionManager.getCurrentGameplayMode());
            assertArrayEquals(original, Files.readAllBytes(slot));
        }
        verifyNoInteractions(snapshots);
    }

    @Test void legacyOverloadAdmitsTheNoSavePolicyExactlyOnce() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        GameModule module = new DelegatingGameModule(stock, "count-policy") {
            @Override public boolean requiresNoSaveSession() { calls.incrementAndGet(); return true; }
        };
        var runtime = SessionManager.openGameplaySession(module);
        assertNotNull(runtime.getWorldSession().getSaveSessionContext());
        assertEquals(1, calls.get());
    }
}
