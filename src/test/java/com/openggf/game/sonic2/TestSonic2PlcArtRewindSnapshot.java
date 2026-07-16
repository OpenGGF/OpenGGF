package com.openggf.game.sonic2;

import com.openggf.game.session.EngineServices;
import com.openggf.game.rewind.snapshot.PlcProgressSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for {@link Sonic2ObjectArtProvider}'s
 * {@link com.openggf.game.rewind.RewindSnapshottable} implementation (Track F.1).
 *
 * <p>Tests verify that the key and epoch capture are stable. The nested ROM test
 * also exercises a real runtime PLC allocation across restore/replay.
 */
class TestSonic2PlcArtRewindSnapshot {

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void keyIsS2PlcArt() {
        Sonic2ObjectArtProvider provider = new Sonic2ObjectArtProvider();
        assertEquals("s2-plc-art", provider.key());
    }

    @Test
    void initialEpochIsZero() {
        Sonic2ObjectArtProvider provider = new Sonic2ObjectArtProvider();
        PlcProgressSnapshot snap = provider.capture();
        assertEquals(0, snap.loadEpoch(),
                "Initial epoch should be 0 before any zone load");
    }

    @Test
    void captureReturnsSameEpochOnSecondCall() {
        Sonic2ObjectArtProvider provider = new Sonic2ObjectArtProvider();
        PlcProgressSnapshot snap1 = provider.capture();
        PlcProgressSnapshot snap2 = provider.capture();
        assertEquals(snap1.loadEpoch(), snap2.loadEpoch(),
                "Epoch must not change between captures without a zone load");
    }

    @Test
    void restoreReinstatesCapturedEpoch() {
        Sonic2ObjectArtProvider provider = new Sonic2ObjectArtProvider();
        provider.restore(new PlcProgressSnapshot(37));

        assertEquals(37, provider.capture().loadEpoch());
    }

    @Nested
    @RequiresRom(SonicGame.SONIC_2)
    class RuntimePlcReplay {
        @Test
        void reusesImmutableRendererAllocationAndEpoch() throws Exception {
            GraphicsManager graphics = GraphicsManager.getInstance();
            graphics.initHeadless();
            Sonic2ObjectArtProvider provider = new Sonic2ObjectArtProvider();
            provider.loadArtForZone(Sonic2ZoneConstants.ROM_ZONE_WFZ);
            provider.ensurePatternsCached(graphics, PatternAtlasRange.OBJECTS.base());
            PlcProgressSnapshot beforeRequest = provider.capture();

            assertTrue(provider.requestPlc(Sonic2Constants.PLC_TORNADO));
            provider.ensurePatternsCached(graphics, PatternAtlasRange.OBJECTS.base());
            PatternSpriteRenderer firstRenderer =
                    provider.getRenderer(Sonic2ObjectArtKeys.TORNADO_THRUSTER);
            int firstBase = firstRenderer.getPatternBase();
            int afterRequestEpoch = provider.capture().loadEpoch();

            provider.restore(beforeRequest);
            assertEquals(beforeRequest.loadEpoch(), provider.capture().loadEpoch());
            assertFalse(provider.requestPlc(Sonic2Constants.PLC_TORNADO),
                    "replay keeps immutable runtime sheets instead of allocating duplicates");
            provider.ensurePatternsCached(graphics, PatternAtlasRange.OBJECTS.base());

            assertSame(firstRenderer, provider.getRenderer(Sonic2ObjectArtKeys.TORNADO_THRUSTER));
            assertEquals(firstBase,
                    provider.getRenderer(Sonic2ObjectArtKeys.TORNADO_THRUSTER).getPatternBase());
            assertEquals(afterRequestEpoch, provider.capture().loadEpoch(),
                    "a replayed ROM PLC request advances from the restored epoch deterministically");
        }
    }

    @Test
    void snapshotRecordPreservesEpoch() {
        PlcProgressSnapshot snap = new PlcProgressSnapshot(42);
        assertEquals(42, snap.loadEpoch());
    }
}
