package com.openggf.game.session;

import com.openggf.game.StockGameDataSources;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * World/context identity oracle, originating the 2026-10-18 CNZ2 Nemesis composition task.
 * One actual stock module is deliberately reused by two actual worlds. Both use
 * ordinary manager attachment and injected DefaultObjectServices, without a level load.
 * Explicit READINESS capsule append and title-loop prepare/service are declared SYNTHETIC
 * component ingress: no CNZ event, native gameplay service, tile publication,
 * full-world rewind, or natural route is exercised here.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kWorldNemesisOwnership {
    private static final int CAPSULE_RAW_PLC = 0x083D74;
    private GameplayModeContext standalone;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        try {
            SessionManager.clear();
        } finally {
            try {
                if (standalone != null) standalone.destroy();
            } finally {
                TestEnvironment.resetAll();
            }
        }
    }

    @Test
    void reusedModuleSeparatesWorldQueuesAndContextReopenKeepsActiveProgress() throws Exception {
        var rom = TestEnvironment.currentRom(); // Original ROM supplied by @RequiresRom only.
        var module = new Sonic3kGameModule();
        assertNotNull(module.createGame(rom), "one real module/game initialization, before either test world");
        var source = StockGameDataSources.pinned(rom, module);
        var engine = EngineServices.current();

        // A is an actual standalone world/context, not SessionManager's ambient world.
        // Keeping it alive permits a genuine cross-world mutation-isolation assertion.
        var worldA = new WorldSession(module, module, source, null);
        standalone = new GameplayModeContext(worldA);
        GameplaySessionFactory.attachManagers(standalone, engine);
        var servicesA = new DefaultObjectServices(standalone, engine);
        var plcA = requirePlc(servicesA);
        var initialA = plcA.capture();
        plcA.appendRaw(CAPSULE_RAW_PLC, Sonic3kLevelTitlePlcService.RecipePurpose.READINESS);
        plcA.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        plcA.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        var progressedA = plcA.capture();

        // B uses the actual public session factory and exactly the SAME module reference.
        var contextB = SessionManager.openGameplaySession(module, module, source, null,
                HardwareReadinessAdmissionPolicy.LIVE);
        var worldB = contextB.getWorldSession();
        GameplaySessionFactory.attachManagers(contextB, engine);
        var servicesB = new DefaultObjectServices(contextB, engine);
        var plcB = requirePlc(servicesB);
        var initialB = plcB.capture();
        plcB.appendRaw(CAPSULE_RAW_PLC, Sonic3kLevelTitlePlcService.RecipePurpose.READINESS);
        plcB.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        plcB.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        var progressedB = plcB.capture();
        var aAfterBService = plcA.capture();

        // Actual teardown/reopen on B's same world, with fresh disposable managers/services.
        var reopened = SessionManager.reopenGameplaySession(HardwareReadinessAdmissionPolicy.LIVE);
        GameplaySessionFactory.attachManagers(reopened, engine);
        var reopenedServices = new DefaultObjectServices(reopened, engine);
        var reopenedPlc = requirePlc(reopenedServices);
        var afterReopen = reopenedPlc.capture();
        reopenedPlc.appendRaw(CAPSULE_RAW_PLC, Sonic3kLevelTitlePlcService.RecipePurpose.READINESS);
        var afterReopenAppend = reopenedPlc.capture();
        var aAfterReopenAppend = plcA.capture();

        // Independent original-ROM descriptor/header oracle; these values are never installed.
        assertEquals(0, (short) rom.read16BitAddr(CAPSULE_RAW_PLC), "one raw descriptor");
        int art = rom.read32BitAddr(CAPSULE_RAW_PLC + 2) & 0x00FF_FFFF;
        int destination = rom.read16BitAddr(CAPSULE_RAW_PLC + 6) & 0xFFFF;
        int patterns = rom.read16BitAddr(art) & 0x7FFF;
        assertTrue(patterns > 6, "one title service leaves this original archive active");
        var queued = new NemesisPlcQueueSnapshot.Entry(art, destination / 32, patterns, patterns);
        var active = new NemesisPlcQueueSnapshot.Entry(art, destination / 32, patterns, patterns - 6);
        var empty = new NemesisPlcQueueSnapshot(null, List.of());
        var inFlight = new NemesisPlcQueueSnapshot(active, List.of());

        // Collect the independent questions only after every public lifecycle action ran.
        // A shared queue must not hide the reopen question behind an early identity assertion.
        assertAll("world-owned queue and context-owned manager reconstruction",
                () -> assertSame(module, worldA.getGameModule()),
                () -> assertSame(module, worldB.getGameModule()),
                () -> assertNotSame(worldA, worldB),
                () -> assertNotSame(plcA, plcB, "the exact reused module is not the mutable queue owner"),
                () -> assertSame(plcA, servicesA.gameService(PlcLifecycleService.class)),
                () -> assertSame(plcB, servicesB.gameService(PlcLifecycleService.class)),
                () -> assertEquals(empty, initialA),
                () -> assertEquals(inFlight, progressedA),
                () -> assertEquals(empty, initialB, "B cannot inherit A's active head or waiting descriptors"),
                () -> assertEquals(inFlight, progressedB),
                () -> assertEquals(progressedA, aAfterBService, "B append/service cannot advance A"),
                () -> assertSame(worldB, reopened.getWorldSession()),
                () -> assertNotSame(contextB, reopened),
                () -> assertSame(plcB, reopenedPlc, "rebuilding a context retains the world's queue owner"),
                () -> assertSame(reopenedPlc, reopenedServices.gameService(PlcLifecycleService.class)),
                () -> assertEquals(progressedB, afterReopen, "active progress survives the actual context teardown/reopen"),
                () -> assertEquals(new NemesisPlcQueueSnapshot(active, List.of(queued)), afterReopenAppend),
                () -> assertEquals(progressedA, aAfterReopenAppend, "reopened B still cannot mutate A"));
    }

    private static Sonic3kLevelTitlePlcService requirePlc(DefaultObjectServices services) {
        var plc = services.gameService(Sonic3kLevelTitlePlcService.class);
        assertNotNull(plc, "explicit world's injected services must expose the initialized ROM-backed owner");
        return plc;
    }
}
