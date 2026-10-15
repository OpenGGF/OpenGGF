package com.openggf.tools;

import com.openggf.game.sonic3k.objects.CutsceneKnucklesSkIntroInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Real ROM placement and manager dispatch, independent of a long cold route. */
@RequiresRom(SonicGame.SONIC_3K)
class TestMhzKnucklesLockedOnIntroGate {
    @ParameterizedTest @ValueSource(ints={320,352,400,528,800})
    void lockedOnPlacementDeletesBeforeSeizingKnucklesAndReplaysItsManagerBoundary(int width) throws Exception {
        var settings=new GameplayCaptureSession.Settings(width,"knuckles","","off",null,null,null);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),7,0,settings);
            for(int i=0;i<5;i++){session.step(null);session.renderFrame();}
            var level=GameServices.level();var manager=level.getObjectManager();
            var placement=level.getCurrentLevel().getObjects().stream()
                    .filter(p->p.objectId()==Sonic3kObjectIds.CUTSCENE_KNUCKLES&&p.subtype()==0x30)
                    .findFirst().orElseThrow();
            // Verified full Object Pos/1.bin matches ROM at $1F2296; row270.
            assertEquals(0x5C3,placement.x());assertEquals(0x9F5,placement.y());
            // Admit the actual placement as a short component fixture. Its factory
            // owns the supported locked-on ROM prerequisite; no gameplay values
            // or standalone flag are injected into the runtime.
            var owner=manager.createDynamicObject(()->(CutsceneKnucklesSkIntroInstance)
                    new Sonic3kObjectRegistry().create(placement));
            assertNotNull(owner);
            var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var before=registry.capture();
            session.step(null);session.renderFrame();
            assertTrue(owner.isDestroyed(),"CutsceneKnux_SKIntro BEQ-deletes for SK_alone_flag=0");
            assertFalse(session.player().isControlLocked());
            assertFalse(GameServices.camera().getFrozen());
            assertTrue(GameServices.camera().isLevelStarted());
            assertTrue(manager.activeObjectsOfType(CutsceneKnucklesSkIntroInstance.class).isEmpty());
            var after=registry.capture();
            for(int cycle=0;cycle<2;cycle++) {
                registry.restore(before);same(before,registry.capture(),"before native mode gate");
                session.restoreInputHistory(null);
                session.step(null);session.renderFrame();
                same(after,registry.capture(),"native mode gate replay");
                assertFalse(session.player().isControlLocked());
                assertFalse(GameServices.camera().getFrozen());
            }
        }
    }
    private static void same(CompositeSnapshot a,CompositeSnapshot b,String label) {
        assertEquals(a.entries().keySet(),b.entries().keySet(),label);
        for(var key:a.entries().keySet())assertTrue(
                RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key)).isEmpty(),label+" "+key);
    }
}
