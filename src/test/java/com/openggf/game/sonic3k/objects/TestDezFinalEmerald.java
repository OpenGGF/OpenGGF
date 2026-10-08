package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.game.sonic3k.runtime.*;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalEmerald {
    private HeadlessTestFixture boot() {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var f=HeadlessTestFixture.builder().withZoneAndAct(23,0).build(); f.sprite().setDebugMode(true); return f;
    }
    private DezFinalBossZoneRuntimeState state() { return (DezFinalBossZoneRuntimeState)GameServices.zoneRuntimeState(); }
    private TestDezFinalHand.Root root() {
        var root=new TestDezFinalHand.Root(new ObjectSpawn(0x500,0x80,0,0,0,false,0));
        GameServices.level().getObjectManager().addDynamicObject(root); return root;
    }
    @Test void bodyEmeraldTracksWhileHiddenAndRetiresOnControlFourRatherThanDefeat() {
        boot(); var root=root(); var emerald=new DezFinalEmerald(root,0,0,0);
        GameServices.level().getObjectManager().addDynamicObject(emerald); emerald.update(0,null);
        emerald.update(1,null); assertFalse(emerald.visible); assertEquals(0x558,emerald.getX()); assertEquals(0x88,emerald.getY());
        state().mouthStatus(1); root.status=0x80; emerald.update(2,null); assertTrue(emerald.visible);
        root.control=0x10; emerald.update(3,null); assertTrue(emerald.isDestroyed());
    }
    @Test void shipEmeraldDropsOneDispatchAfterSignalThenHoldsAtCfAndRestoresWithoutParent() {
        var f=boot(); var root=root(); var emerald=new DezFinalEmerald(root,2,0,0x3B);
        var manager=GameServices.level().getObjectManager(); manager.addDynamicObject(emerald);
        emerald.update(0,null); emerald.update(1,null); assertEquals(0xBB,emerald.getY()); assertEquals(0,emerald.frame);
        state().bossSignals(0x10); emerald.update(2,null); assertEquals(0,emerald.yVelocity);
        emerald.update(3,null); assertEquals(0x38,emerald.yVelocity);
        root.setDestroyed(true); f.stepIdleFrames(1);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        for(int i=0;i<30;i++) emerald.update(i,null); assertEquals(0xCF,emerald.getY());
        registry.restore(saved); var restored=manager.activeObjectsOfType(DezFinalEmerald.class).getFirst();
        for(int i=0;i<30;i++) restored.update(i,null); assertEquals(0xCF,restored.getY()); assertTrue(restored.visible);
    }
    @Test void bothRomTablesRepeatAfter94TicksAndPauseWithoutAdvancingTheirCapturedCursors() {
        boot();
        for(int table:new int[]{0x813AA,0x8141E}) {
            var services=spy(TestEnvironment.objectServices()); var registry=mock(PaletteOwnershipRegistry.class);
            doReturn(registry).when(services).paletteOwnershipRegistryOrNull(); List<PaletteWrite> writes=new ArrayList<>();
            doAnswer(call->{writes.add(call.getArgument(0));return null;}).when(registry).submit(any());
            var palette=new S3kEmeraldPaletteState(); palette.install(services,table);
            palette.tick(services,"test.emerald"); assertEquals(2,writes.size());
            assertEquals(table==0x813AA?2:3,writes.getFirst().lineIndex());
            assertEquals(9,writes.getFirst().startColor()); assertEquals(11,writes.get(1).startColor());
            assertArrayEquals(new byte[]{6,0x60},writes.getFirst().segaData());
            var before=java.nio.ByteBuffer.allocate(S3kEmeraldPaletteState.SNAPSHOT_BYTES); palette.captureTo(before);
            when(registry.isPaletteRotationDisabled()).thenReturn(true);
            for(int i=0;i<30;i++) palette.tick(services,"test.emerald");
            var after=java.nio.ByteBuffer.allocate(S3kEmeraldPaletteState.SNAPSHOT_BYTES); palette.captureTo(after);
            assertArrayEquals(before.array(),after.array()); when(registry.isPaletteRotationDisabled()).thenReturn(false);
            for(int i=1;i<94;i++) palette.tick(services,"test.emerald");
            assertEquals(22,writes.size()); palette.tick(services,"test.emerald"); assertEquals(24,writes.size());
            assertArrayEquals(writes.get(0).segaData(),writes.get(22).segaData());
            assertArrayEquals(writes.get(1).segaData(),writes.get(23).segaData());
        }
    }
    @Test void zoneSnapshotsRestorePaletteCursorAndInstallingANewOwnerResetsIt() {
        boot(); var services=TestEnvironment.objectServices();
        for(S3kZoneRuntimeState zone:List.of(state(),new DdzZoneRuntimeState(0,PlayerCharacter.SONIC_ALONE))) {
            var palette=zone instanceof DezFinalBossZoneRuntimeState dez?dez.emeraldPalette():((DdzZoneRuntimeState)zone).emeraldPalette();
            palette.install(services,0x813AA); palette.tick(services,"test.emerald"); var saved=zone.captureBytes();
            for(int i=0;i<20;i++) palette.tick(services,"test.emerald"); var advanced=zone.captureBytes();
            zone.restoreBytes(saved); for(int i=0;i<20;i++) palette.tick(services,"test.emerald");
            assertArrayEquals(advanced,zone.captureBytes()); palette.install(services,0x813AA); palette.tick(services,"test.emerald");
            assertArrayEquals(saved,zone.captureBytes());
        }
    }
    @Test void rotationRequiresAllSevenSuperEmeralds() {
        boot(); var root=root(); var emerald=new DezFinalEmerald(root,0,0,0);
        var services=spy(TestEnvironment.objectServices()); var registry=mock(PaletteOwnershipRegistry.class);
        doReturn(registry).when(services).paletteOwnershipRegistryOrNull();
        GameServices.level().getObjectManager().addDynamicObject(emerald); emerald.setServices(services);
        emerald.update(0,null); emerald.update(1,null); verify(registry,never()).submit(any());
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(3,3,3,3,3,3,3),true);
        var hyper=new DezFinalEmerald(root,0,0,0); GameServices.level().getObjectManager().addDynamicObject(hyper); hyper.setServices(services);
        hyper.update(0,null); verify(registry,never()).submit(any()); hyper.update(1,null); verify(registry,times(2)).submit(any());
    }
}
