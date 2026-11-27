package com.openggf.game.sonic2.titlecard;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2TitleCardManagerRewind {

    @Test
    void gameplaySessionRegistersLiveTitleManager() {
        startLevel();

        var keys = TestEnvironment.activeGameplayMode()
                .getRewindRegistry().capture().entries().keySet();

        assertTrue(keys.contains(TitleCardManager.REWIND_KEY));
    }

    @Test
    void rewindBeforeOmittedTailPublicationReplaysItsExitPlcs() {
        startLevel();
        Sonic2TitleCardManagerFixture fixture = new Sonic2TitleCardManagerFixture();

        fixture.title().beginOmittedPresentationExitTail(0, 0);
        CompositeSnapshot beforePublication = fixture.registry().capture();
        int epochBefore = fixture.provider().capture().loadEpoch();

        runUntilComplete(fixture.title());
        assertEquals(epochBefore + 1, fixture.provider().capture().loadEpoch());

        fixture.registry().restore(beforePublication);
        assertFalse(fixture.title().isComplete(),
                "restoring before the tail boundary must restore the live title owner");
        assertEquals(epochBefore, fixture.provider().capture().loadEpoch());

        runUntilComplete(fixture.title());
        assertEquals(epochBefore + 1, fixture.provider().capture().loadEpoch(),
                "replaying the restored title owner must publish its PLCs again");
    }

    @Test
    void gameplayCheckpointsShareUnchangedTitleCardPlaneTables() {
        HeadlessTestFixture fixture = startLevel();
        RewindRegistry registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        fixture.stepIdleFrames(10);
        TitleCardManager.Snapshot first = titleEntry(registry.capture());
        fixture.stepIdleFrames(10);
        TitleCardManager.Snapshot second = titleEntry(registry.capture());

        // Mid-act the Obj34 plane tables are unchanged; each checkpoint
        // used to hold its own boxed copy of both 64x32 tables (~50 KB retained).
        assertTrue(second.obj34().foreground1().stream().anyMatch(word -> word != 0),
                "the settled card leaves its plane image in place");
        assertSame(first.obj34().planes(), second.obj34().planes(),
                "unchanged title-card plane tables must be shared between checkpoints");
    }

    @Test
    void titleArtIsSharedBetweenCheckpointsAndCopiedBeforeLetterUploads() throws Exception {
        startLevel();
        Sonic2TitleCardManagerFixture fixture = new Sonic2TitleCardManagerFixture();
        fixture.title().initialize(0, 0);
        TitleCardManager.Snapshot first = titleEntry(fixture.registry().capture());
        fixture.title().update();
        TitleCardManager.Snapshot second = titleEntry(fixture.registry().capture());
        for (String component : List.of("combinedPatterns", "titleCard2RawPatterns", "titleCardBasePatterns")) {
            assertTrue(retainedArray(first, component) != null, component + " is loaded by initialize");
            assertSame(retainedArray(first, component), retainedArray(second, component),
                    component + " must be shared between checkpoints");
        }

        // Copy-on-write: loading another zone's letters must not write into a retained checkpoint.
        Object[] retained = (Object[]) retainedArray(second, "combinedPatterns");
        Object[] before = retained.clone();
        fixture.title().initialize(2, 0);
        assertArrayEquals(before, retained, "zone-letter upload must copy the VRAM table before writing");
        assertNotSame(retained, retainedArray(titleEntry(fixture.registry().capture()), "combinedPatterns"),
                "the new zone's letters live in a new table");
    }

    @Test
    void rewindDuringTheTitleCardRestoresItsPlanesAndParts() {
        startLevel();
        Sonic2TitleCardManagerFixture fixture = new Sonic2TitleCardManagerFixture();
        TitleCardManager title = fixture.title();
        title.initialize(0, 0);
        for (int frame = 0; frame < 12; frame++) title.update();
        assertFalse(title.isComplete(), "the card must still be sliding in");

        CompositeSnapshot midCard = fixture.registry().capture();
        Obj34.Snapshot midObj34 = titleEntry(midCard).obj34();
        List<Integer> midPlane = midObj34.foreground1();
        assertTrue(midPlane.stream().anyMatch(word -> word != 0), "the background fill has started");
        for (int frame = 0; frame < 6; frame++) title.update();
        Obj34.Snapshot expected = title.capture().obj34();
        assertNotEquals(midObj34, expected, "the card must advance between the two captures");
        for (int frame = 0; frame < 30; frame++) title.update();

        fixture.registry().restore(midCard);
        assertEquals(midObj34, title.capture().obj34(), "restore must reproduce the mid-card Obj34 state");
        assertSame(midObj34.planes(), title.capture().obj34().planes(),
                "a capture straight after restore shares the restored plane image");
        for (int frame = 0; frame < 6; frame++) title.update();

        Obj34.Snapshot replayed = title.capture().obj34();
        assertEquals(expected, replayed, "replaying from the mid-card checkpoint must reach the same card");
        assertNotSame(midObj34.planes(), replayed.planes(), "a later fill must take a new plane image");
        assertEquals(midPlane, midObj34.foreground1(), "fills after restore must not write into the checkpoint");
    }

    private static TitleCardManager.Snapshot titleEntry(CompositeSnapshot snapshot) {
        return (TitleCardManager.Snapshot) snapshot.entries().get(TitleCardManager.REWIND_KEY);
    }

    /** The record's accessors return defensive copies; read the retained component itself. */
    private static Object retainedArray(TitleCardManager.Snapshot snapshot, String component) throws Exception {
        var field = TitleCardManager.Snapshot.class.getDeclaredField(component);
        field.setAccessible(true);
        return field.get(snapshot);
    }

    private static void runUntilComplete(TitleCardManager title) {
        for (int frame = 0; frame < 100 && !title.isComplete(); frame++) {
            title.update();
        }
        assertTrue(title.isComplete(), "title-card omitted tail did not complete");
    }

    private static HeadlessTestFixture startLevel() {
        TestEnvironment.resetAll();
        SessionManager.clear();
        EngineServices.configure(
                EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();
    }

    private static final class Sonic2TitleCardManagerFixture {
        private final TitleCardManager title =
                (TitleCardManager) GameServices.module().getTitleCardProvider();
        private final Sonic2ObjectArtProvider provider =
                (Sonic2ObjectArtProvider) GameServices.module().getObjectArtProvider();
        private final RewindRegistry registry =
                TestEnvironment.activeGameplayMode().getRewindRegistry();

        TitleCardManager title() {
            return title;
        }

        Sonic2ObjectArtProvider provider() {
            return provider;
        }

        RewindRegistry registry() {
            return registry;
        }
    }
}
