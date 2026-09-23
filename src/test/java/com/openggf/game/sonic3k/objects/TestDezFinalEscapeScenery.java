package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalEscapeScenery {
    private HeadlessTestFixture boot() {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(23,0).build(); f.sprite().setDebugMode(true); return f;
    }
    @Test void debrisUsesNativeRngFieldsAndDrawsOnlyAfterInitialization() {
        for(int random:new int[]{0,0x03028003,0x020101FE}) {
            var f=boot(); f.camera().setX((short)0x500); f.camera().setY((short)0x200);
            var debris=DezFinalEscapeScenery.debris(); GameServices.level().getObjectManager().addDynamicObject(debris);
            var services=spy(TestEnvironment.objectServices()); var rng=mock(GameRng.class);
            when(rng.nextRaw()).thenReturn(random); doReturn(rng).when(services).rng(); debris.setServices(services);
            debris.update(0,null); assertFalse(debris.visible); assertFalse(debris.publishesTouchResponseListEntryThisFrame());
            assertEquals(0x520+(random&0x1FF),debris.getX()); assertEquals(0x1E0,debris.getY());
            assertEquals((short)random<0?6:0,debris.getPriorityBucket()); assertTrue(debris.isHighPriority());
            assertEquals((random&1)!=0,debris.flipX); assertEquals((random&2)!=0,debris.flipY);
            assertEquals(new int[]{0,1,2,1}[(random>>>16)&3],debris.frame);
            int speed=(((random>>>16)&0x300)+0x100)>>8;
            debris.update(1,null); assertEquals(0x1E0+speed,debris.getY()); assertTrue(debris.visible);
            debris.writeY(0x308-speed); debris.update(2,null); assertFalse(debris.isDestroyed(),"equality remains live");
            debris.update(3,null); assertTrue(debris.isDestroyed(),"strictly below viewport deletes immediately");
            verify(rng).nextRaw();
        }
    }
    @Test void craneStopsTrackingOnReleaseAndDrawsThroughItsDeferredDeleteCallback() {
        boot(); var manager=GameServices.level().getObjectManager();
        var root=new TestDezFinalHand.Root(new ObjectSpawn(0x500,0x80,0,0,0,false,0)); manager.addDynamicObject(root);
        var crane=DezFinalEscapeScenery.crane(root); manager.addDynamicObject(crane);
        crane.update(0,null); assertTrue(crane.visible); assertEquals(0xA3,crane.getY());
        root.flipY=true; crane.update(1,null); assertEquals(0x5D,crane.getY()); assertTrue(crane.flipY);
        ((DezFinalBossZoneRuntimeState)GameServices.zoneRuntimeState()).bossSignals(8);
        root.writeX(0x600); crane.update(2,null); assertEquals(0x500,crane.getX()); assertTrue(crane.visible);
        root.setDestroyed(true); var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        crane.update(3,null); assertFalse(crane.isDestroyed()); assertTrue(crane.visible); assertEquals(0x80,crane.status&0x80);
        crane.update(4,null); assertTrue(crane.isDestroyed()); registry.restore(saved);
        var restored=manager.activeObjectsOfType(DezFinalEscapeScenery.class).getFirst(); restored.update(3,null); restored.update(4,null);
        assertTrue(restored.isDestroyed());
    }
    @Test void nativeWhiteFadeSupportsDezAndDdzReloadWordsAndRestoresMidFade() {
        for(int reload:new int[]{3,7}) {
            boot(); var manager=GameServices.level().getObjectManager();
            var fade=new DdzWhiteFadeObjectInstance(DdzWhiteFadeObjectInstance.Mode.TO_WHITE_HOLD,reload); manager.addDynamicObject(fade);
            var services=spy(TestEnvironment.objectServices()); var palette=mock(PaletteOwnershipRegistry.class);
            doReturn(palette).when(services).paletteOwnershipRegistryOrNull(); fade.setServices(services);
            fade.update(0,null); assertFalse(fade.finished()); verify(palette).setPaletteRotationDisabled(true);
            var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
            for(int i=1;i<7*(reload+1);i++) { fade.update(i,null); assertFalse(fade.finished()); }
            fade.update(7*(reload+1),null); assertTrue(fade.finished()); assertFalse(fade.isDestroyed());
            verify(palette,times(32)).submit(any()); verify(palette).setPaletteRotationDisabled(false);
            fade.update(100,null); assertTrue(fade.isDestroyed()); registry.restore(saved);
            var restored=manager.activeObjectsOfType(DdzWhiteFadeObjectInstance.class).getFirst();
            for(int i=1;i<=7*(reload+1);i++) restored.update(i,null);
            assertTrue(restored.finished()); assertFalse(restored.isDestroyed());
        }
    }
}
