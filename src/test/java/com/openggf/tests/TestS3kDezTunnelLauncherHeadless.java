package com.openggf.tests;

import com.openggf.configuration.*;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.objects.S3kDezTunnelLauncherObjectInstance;
import com.openggf.game.sonic3k.objects.S3kDezTunnelLauncherObjectInstance.Controller;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezTunnelLauncherHeadless {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides(); SessionManager.clear(); }

    @Test void primaryCountdownLaunchesAt180AndResetsAfterAnother60Passes() {
        var f = boot(320, 0, 0x100, 0x400);
        var p = f.sprite();
        var launcher = create(0x1B80, 0x330, 0);
        NativePositionOps.writeXPosPreserveSubpixel(p, 0x1B80);
        NativePositionOps.writeYPosPreserveSubpixel(p, 0x330);
        p.setAir(false);
        launcher.update(0, p);
        assertTrue(p.isObjectControlled()); assertEquals(9, p.getAnimationId());
        assertEquals(59, launcher.timerForTest()); assertEquals(10, launcher.digitForTest());
        for (int i = 1; i < 179; i++) launcher.update(0, p);
        assertFalse(launcher.countdownForTest()); assertEquals(1, launcher.timerForTest());
        launcher.update(0, p);
        assertTrue(launcher.countdownForTest()); assertEquals(7, launcher.digitForTest());
        assertNotNull(controller());
        for (int i = 0; i < 59; i++) launcher.update(0, p);
        assertEquals(6, launcher.frameForTest()); assertTrue(launcher.countdownForTest());
        launcher.update(0, p);
        assertFalse(launcher.countdownForTest()); assertEquals(0, launcher.frameForTest());
    }

    @Test void allEightRomPathsReleaseAtTheirFinalWaypointIncludingSineRoutes() throws Exception {
        int[][] ends = {{0x20D4,0x1A8},{0x31D0,0x228},{0x204C,0x228},{0x204C,0x228},
                {0x2F44,0x628},{0x2AB0,0x968},{0x2A40,0x4E8},{0,0}};
        var rom = TestEnvironment.objectServices().romReader();
        // The last path's final point is checked directly against its separate
        // ROM data, while the long-loop endpoints above are disassembly facts.
        int last = rom.readU32BE(0x1E4058 + 7 * 4);
        int count = rom.readU16BE(last);
        ends[7][0] = rom.readU16BE(last + 2 + (count - 1) * 4);
        ends[7][1] = rom.readU16BE(last + 4 + (count - 1) * 4);
        for (int subtype = 0; subtype < 8; subtype++) {
            var f = boot(320, 0, 0x100, 0x400); var p = f.sprite();
            var launch = create(0x100, 0x400, subtype); p.setAir(false);
            for (int i = 0; i < 180; i++) launch.update(0, p);
            var c = controller(); assertNotNull(c);
            for (int i = 0; i < 10; i++) { c.update(0, p); assertEquals(2, c.routineForTest(0)); }
            c.update(0, p); assertEquals(4, c.routineForTest(0)); assertEquals(0x800, p.getGSpeed());
            assertEquals(0x96, p.getMappingFrame());
            var modes = new java.util.HashSet<Integer>();
            int steps = 0;
            while (c.routineForTest(0) != 0 && steps++ < 2000) {
                modes.add(c.routineForTest(0)); c.update(0, p);
            }
            assertTrue(steps < 2000, "path " + subtype + " did not release");
            assertEquals(ends[subtype][0], p.getCentreX(), "path " + subtype);
            assertEquals(ends[subtype][1], p.getCentreY(), "path " + subtype);
            assertFalse(p.isObjectControlled());
            if (subtype < 6) { assertTrue(modes.contains(6)); assertTrue(modes.contains(8)); }
            else assertTrue(modes.contains(subtype == 6 ? 12 : 10));
        }
    }

    @Test void firstCircleUsesNativeFractionWordsAndSignedDeltaVelocities() {
        var f = boot(320, 0, 0x100, 0x400); var p = f.sprite(); p.setAir(false);
        var launcher = create(0x100, 0x400, 0);
        for (int i = 0; i < 180; i++) launcher.update(0, p);
        var c = controller();
        for (int i = 0; i < 100 && c.routineForTest(0) != 6; i++) c.update(0, p);
        assertEquals(6, c.routineForTest(0));
        assertEquals(0x1C8C, p.getCentreX()); assertEquals(0x328, p.getCentreY());
        assertEquals(0x1C8C, p.getXSubpixelRaw()); assertEquals(0x2A8, p.getYSubpixelRaw());
        c.update(0, p);
        assertEquals(0x1C98, p.getCentreX()); assertEquals(0x327, p.getCentreY());
        assertEquals(0xC00, p.getXSpeed()); assertEquals(-0x100, p.getYSpeed());
        assertEquals(0x1C8C, p.getXSubpixelRaw()); assertEquals(0x2A8, p.getYSubpixelRaw());
    }

    @Test void secondaryAloneWaitsForPrimaryAndExtraFollowersAreNotNativeSlots() {
        boot(320, 0, 0x100, 0x400);
        var p1 = new TestPlayableSprite(); var p2 = new TestPlayableSprite();
        var extra = new TestPlayableSprite();
        for (var p : List.of(p2, extra)) {
            NativePositionOps.writeXPosPreserveSubpixel(p, 0x100);
            NativePositionOps.writeYPosPreserveSubpixel(p, 0x400);
            p.setAir(false);
        }
        var launcher = create(0x100, 0x400, 0);
        var services = new TestObjectServices() {
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> p1, () -> List.of(p2, extra)); }
        }.withDirectObjectManager(GameServices.level().getObjectManager())
                .withGameState(GameServices.gameState()).withCamera(GameServices.camera());
        launcher.setServices(services);
        for (int i = 0; i < 240; i++) launcher.update(0, null);
        assertTrue(p2.isObjectControlled()); assertFalse(extra.isObjectControlled());
        assertEquals(0, launcher.digitForTest()); assertFalse(launcher.countdownForTest());
        assertNull(controller());
        NativePositionOps.writeXPosPreserveSubpixel(p1, 0x100);
        NativePositionOps.writeYPosPreserveSubpixel(p1, 0x400); p1.setAir(false);
        for (int i = 0; i < 180; i++) launcher.update(0, null);
        assertTrue(launcher.countdownForTest()); assertNotNull(controller());
        assertEquals(2, controller().routineForTest(0)); assertEquals(2, controller().routineForTest(1));
    }

    @Test void fullPoolStillCountsDownButCannotReleaseCapturedPlayersWithoutController() {
        var f = boot(320, 0, 0x100, 0x400); f.sprite().setAir(false);
        var launcher = create(0x100, 0x400, 0);
        var manager = GameServices.level().getObjectManager(); boolean full = false;
        for (int i = 0; i < 160; i++) {
            var filler = new com.openggf.game.sonic3k.objects.S3kDezConveyorBeltObjectInstance(
                    new ObjectSpawn(0,0,0x50,0,0,false,0));
            manager.addDynamicObject(filler);
            if (filler.isDestroyed()) { full = true; break; }
        }
        assertTrue(full);
        for (int i = 0; i < 180; i++) launcher.update(0, f.sprite());
        assertTrue(launcher.countdownForTest()); assertNull(controller());
        for (int i = 0; i < 60; i++) launcher.update(0, f.sprite());
        assertFalse(launcher.countdownForTest()); assertTrue(f.sprite().isObjectControlled());
    }

    @Test void transportRingAnimationDeletesOnTheRomFcCommand() {
        boot(320, 0, 0x100, 0x400);
        var ring = new S3kDezTunnelLauncherObjectInstance.Ring(new ObjectSpawn(0x100,0x400,0x57,0,0,false,0));
        GameServices.level().getObjectManager().addDynamicObject(ring);
        for (int i = 0; i < 48; i++) { ring.update(0, null); assertFalse(ring.isDestroyed(), "frame " + i); }
        ring.update(0, null); assertTrue(ring.isDestroyed());
    }

    @Test void romMappingsHaveElevenLauncherAndFourteenRingFrames() throws Exception {
        var rom = TestEnvironment.objectServices().romReader();
        assertEquals(11, S3kSpriteDataLoader.loadMappingFrames(rom, 0x48424, 11).size());
        var rings = S3kSpriteDataLoader.loadMappingFrames(rom, 0x489CC, 14);
        assertEquals(14, rings.size()); assertEquals(4, rings.get(3).pieces().size());
    }

    @ParameterizedTest @ValueSource(ints={320,800})
    void placedCountdownAndTransportGraphRestoreAndReplay(int width) {
        var f = boot(width, 0, 0x1B80, 0x310); f.sprite().setAir(true); f.sprite().setRingCount(7);
        f.stepIdleFrames(60);
        assertTrue(f.sprite().isObjectControlled(), "placed launcher captured Sonic");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var waiting = registry.capture(); var countdownRows = frames(f, 150);
        registry.restore(waiting); assertEquals(countdownRows, frames(f, 150));
        var c = controller(); assertNotNull(c); assertTrue(c.routineForTest(0) >= 4);
        var moving = registry.capture(); var rows = frames(f, 220);
        c.setDestroyed(true); f.stepIdleFrames(1); registry.restore(moving);
        assertNotSame(c, controller()); assertEquals(rows, frames(f, 220));
        int releaseWait = 0;
        while (f.sprite().isObjectControlled() && releaseWait++ < 1800) f.stepIdleFrames(1);
        assertTrue(releaseWait < 1800, "placed transport releases normally");
        assertFalse(f.sprite().getDead());
        var released = registry.capture(); var exitRows = frames(f, 30);
        registry.restore(released); assertEquals(exitRows, frames(f, 30));
    }

    @Test void finishedTrailRetiresBeforePlayersWithoutLeavingARewindReference() {
        var f = boot(320, 0, 0x100, 0x400);
        f.sprite().setAir(false);
        var launch = create(0x100, 0x400, 7);
        for (int i = 0; i < 180; i++) launch.update(0, f.sprite());
        var c = controller();
        assertNotNull(c);
        f.stepIdleFrames(1);
        var trail = c.spawnerForTest();
        assertNotNull(trail);
        int passes = 0;
        while (!trail.isDestroyed() && passes++ < 2000) f.stepIdleFrames(1);
        assertTrue(trail.isDestroyed(), "trail completes its own ROM channel");
        assertNotEquals(0, c.routineForTest(0), "player setup trails the ring channel by ten passes");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        var forward = frames(f, 45);
        registry.restore(saved);
        assertEquals(forward, frames(f, 45));
        assertNull(c.spawnerForTest(), "completed trail channel must not retain a retired Java owner");
    }

    private List<String> frames(HeadlessTestFixture f, int count) {
        var rows = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            f.stepIdleFrames(1); var p = f.sprite(); var c = controller();
            long rings = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(o -> o instanceof S3kDezTunnelLauncherObjectInstance.Ring).count();
            rows.add(p.getCentreX()+","+p.getCentreY()+","+p.getXSubpixelRaw()+","+p.getYSubpixelRaw()
                    +","+p.getXSpeed()+","+p.getYSpeed()+","+p.isObjectControlled()+":"
                    +(c == null ? "none" : c.routineForTest(0)+","+c.remainingForTest(0))+":"+rings);
        }
        return rows;
    }
    private Controller controller() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof Controller).map(o -> (Controller)o).findFirst().orElse(null);
    }
    private S3kDezTunnelLauncherObjectInstance create(int x, int y, int subtype) {
        var o = new S3kDezTunnelLauncherObjectInstance(new ObjectSpawn(x,y,0x57,subtype,0,false,0));
        GameServices.level().getObjectManager().addDynamicObject(o); return o;
    }
    private HeadlessTestFixture boot(int width, int act, int x, int y) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width == 320 ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.SUPER_32_9).name());
        config.resolveDisplayAspect(); config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,width);
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder().withZoneAndAct(11,act).startPosition((short)x,(short)y)
                .startPositionIsCentre().build();
    }
}
