package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Finite native value writers on the same actual B Frame; no Module authority is forged. */
class TestCdPastLoadFrameValues {
    private static CdR11bAssets assets;
    @BeforeAll static void originalSource() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);
        try(var paths=Files.list(Path.of(root).resolve("USA"))) {
            var cues=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());
            try(var source=CdProgramAssets.open(cues.getFirst(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {assets=CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));}
        }
    }
    @Test void flowerClearChangesExactlyMiniDebugBossAndTheExistingCounter() {
        var f=frame(0x80);var actor=f.captureActor();var shared=f.captureShared();var e=f.environment();var m=e.movement();var g=m.gates();var b=e.globals();
        f.clearLevelStartFlowersPlayerValues();
        var gates=new CdPlayerMovement.Gates(g.zoneAct(),g.timeZone(),0,g.waterSlide(),g.windTunnel(),g.speedShoes(),g.timeWarpDirection());
        var globals=new CdPlayerBody.Globals(b.levelFrames(),b.cameraXCentre(),b.timeAttackMode(),0,b.debugCheat(),b.timeWarpTimer(),b.invincible(),0);
        assertEquals(new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(),m.bounds(),gates,m.globals()),e.controls(),globals,e.dynamicSlots()),f.environment());
        assertSame(actor.state(),f.state());assertEquals(actor,f.captureActor());assertSame(shared.owner(),f.captureShared().owner());
        assertSame(shared.artState().cpuBuffer(),f.artState().cpuBuffer());assertSame(shared.artState().residentBank(),f.artState().residentBank());
        assertEquals(shared.artState().lastFrame(),f.artState().lastFrame());assertEquals(shared.artState().dirty(),f.artState().dirty());
        assertEquals(new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0),0),f.vIntClock());
    }
    @Test void levelSizeStoresOnlyItsBoundsAndTwoNativeCameraCentres() {
        var f=frame(0x80);var actor=f.captureActor();var shared=f.captureShared();var e=f.environment();var m=e.movement();var b=e.globals();var g=m.globals();
        var bounds=new CdPlayerMovement.Bounds(0x100,0x2897,0x310,m.bounds().bossFight());
        f.initializeLevelSizePlayerValues(bounds,0x60);
        var globals=new CdPlayerMovement.Globals(g.lookMode(),0x60,g.primaryAngle(),g.secondaryAngle(),g.boredTimer(),g.scoreChain(),g.debugAngle(),g.debugAngleShift(),g.debugQuadrant(),g.debugFloorDistance());
        var body=new CdPlayerBody.Globals(b.levelFrames(),160,b.timeAttackMode(),b.debugMode(),b.debugCheat(),b.timeWarpTimer(),b.invincible(),b.bossMusic());
        assertEquals(new CdPlayerBody.Environment(new CdPlayerMovement.Environment(m.physics(),bounds,m.gates(),globals),e.controls(),body,e.dynamicSlots()),f.environment());
        assertEquals(actor,f.captureActor());assertSame(shared.owner(),f.captureShared().owner());assertSame(shared.vIntClock(),f.vIntClock());
        assertSame(shared.artState().cpuBuffer(),f.artState().cpuBuffer());assertSame(shared.artState().residentBank(),f.artState().residentBank());
    }
    @Test void malformedLevelSizeValuesCannotChangeTheBossFlagOrAnyOwner() {
        var f=frame(0x80);var a=f.captureActor();var s=f.captureShared();
        assertThrows(NullPointerException.class,()->f.initializeLevelSizePlayerValues(null,0));
        assertThrows(IllegalArgumentException.class,()->f.initializeLevelSizePlayerValues(new CdPlayerMovement.Bounds(1,2,3,0x67),0));
        assertThrows(IllegalArgumentException.class,()->f.initializeLevelSizePlayerValues(new CdPlayerMovement.Bounds(1,2,3,0x66),0x10000));
        assertEquals(a,f.captureActor());assertEquals(s,f.captureShared());
    }
    @Test void pendingClosedAndForeignPeriodGuardsPrecedeEveryWrite() {
        var pending=frame(0x80);var state=pending.state();var env=pending.environment();var clock=pending.vIntClock();pending.prepareInput(8);
        assertThrows(IllegalStateException.class,pending::clearLevelStartFlowersPlayerValues);
        assertThrows(IllegalStateException.class,()->pending.initializeLevelSizePlayerValues(new CdPlayerMovement.Bounds(1,2,3,0x66),0));
        assertSame(state,pending.state());assertSame(env,pending.environment());assertSame(clock,pending.vIntClock());
        var closed=frame(0x80);closed.close();assertThrows(IllegalStateException.class,closed::clearLevelStartFlowersPlayerValues);
        assertThrows(IllegalStateException.class,()->closed.initializeLevelSizePlayerValues(new CdPlayerMovement.Bounds(1,2,3,0x66),0));
        var wrong=frame(0x83);var before=wrong.environment();assertThrows(UnsupportedOperationException.class,wrong::clearLevelStartFlowersPlayerValues);
        assertThrows(UnsupportedOperationException.class,()->wrong.initializeLevelSizePlayerValues(new CdPlayerMovement.Bounds(1,2,3,0x66),0));assertSame(before,wrong.environment());
    }
    private static CdPlayerFrameRuntime frame(int period) {
        var motion=new CdPlayerMotionState(0x12345678,0x34567890,0x123,-0x234,0x345,0,0x12,0x23,0x34,0x45,0x56,0,0,0x67,0x78,-0x123,0x89,0x9A,0xAB,0xBC);
        var state=new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(0,0,0,0,0),new CdPlayerBody.Timers(1,2,3,4),
                new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]),0x40),0xCD,new CdPlayerBody.ObjectLinks(0xDE,0xEF),0xF1);
        var movement=new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,12,0x80),new CdPlayerMovement.Bounds(0x111,0x222,0x333,0x66),
                new CdPlayerMovement.Gates(0,period,0xA5,0x32,0x43,0x54,0xFF),new CdPlayerMovement.Globals(0x55,0x666,0x77,0x88,0x999,0xAAA,0xBB,0xCC,0xDD,0xEE));
        var env=new CdPlayerBody.Environment(movement,new CdPlayerBody.Controls(0xF5,0xA4,0xB3,0xC2,0xD1),new CdPlayerBody.Globals(0x1234,0x2345,0x34,0x4567,0x5678,0x6789,0x78,0x89),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        byte[] cpu=new byte[0x300],resident=new byte[0x300];Arrays.fill(cpu,(byte)0xC5);Arrays.fill(resident,(byte)0xD6);
        return CdPlayerFrameRuntime.fromPastEntry(assets,state,env,new CdPlayerArtRuntime.State(cpu,resident,0x13,true),new CdPlayerFrameRuntime.VIntClock(OptionalLong.of(0x12345678L),0x345));
    }
}
