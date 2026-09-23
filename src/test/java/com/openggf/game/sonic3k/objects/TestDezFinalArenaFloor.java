package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestDezFinalArenaFloor {
    private HeadlessTestFixture boot() {
        com.openggf.game.session.SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(23, 0).build();
        fixture.sprite().setDebugMode(true);
        GameServices.camera().setX((short) 0x80); GameServices.camera().setXCopy((short) 0x80);
        return fixture;
    }
    private DezFinalBossZoneRuntimeState state() {
        return (DezFinalBossZoneRuntimeState) GameServices.zoneRuntimeState();
    }
    @Test void laserCopiesExactlyFourRomTilesOnlyWhenItsFrameChanges() throws Exception {
        var fixture = boot(); var services = TestEnvironment.objectServices();
        assertEquals(0xFF00, state().uploadedLaserOffset());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved = registry.capture();
        for (int frame : new int[]{0, 0x20, 0xFFFF}) {
            state().laserOffset(frame);
            com.openggf.game.sonic3k.events.DezFinalLaserArt.update(services, state());
            assertFalse(services.zoneLayoutMutationPipeline().isEmpty());
            fixture.stepIdleFrames(1);
            assertTrue(services.zoneLayoutMutationPipeline().isEmpty());
            byte[] expected = services.rom().readBytes(0x15A674 + (((frame + 2) << 2) & 0xFFFF), 0x80);
            for (int i = 0; i < expected.length; i++) {
                var pattern = GameServices.level().getCurrentLevel().getPattern(0x208 + i / 32);
                int pixel = (i % 32) * 2;
                assertEquals((expected[i] >>> 4) & 15, pattern.getPixel(pixel % 8, pixel / 8));
                assertEquals(expected[i] & 15, pattern.getPixel((pixel + 1) % 8, pixel / 8));
            }
            com.openggf.game.sonic3k.events.DezFinalLaserArt.update(services, state());
            assertTrue(services.zoneLayoutMutationPipeline().isEmpty(), "unchanged frame submits no upload");
        }
        registry.restore(saved); assertEquals(0xFF00, state().uploadedLaserOffset());
        com.openggf.game.sonic3k.events.DezFinalLaserArt.update(services, state());
        assertFalse(services.zoneLayoutMutationPipeline().isEmpty(), "restored gate allows the original first upload");
    }

    @Test void fourFallingBlockFramesUseFinalArenaLevelTilesOnly() throws Exception {
        boot(); var services = TestEnvironment.objectServices();
        var frames = com.openggf.game.sonic3k.S3kSpriteDataLoader.loadMappingFrames(services.romReader(), 0x5A9AC);
        assertEquals(4, frames.size());
        var plan = com.openggf.game.sonic3k.Sonic3kPlcArtRegistry.getPlan(23, 0);
        var entry = plan.levelArt().stream().filter(e -> e.key().equals(
                com.openggf.game.sonic3k.Sonic3kObjectArtKeys.DEZ_FINAL_ARENA_BLOCK)).findFirst().orElseThrow();
        assertEquals(1, entry.artTileBase()); assertEquals(0x5A9AC, entry.mappingAddr());
        for (var frame : frames) for (var piece : frame.pieces())
            for (int tile = 0; tile < piece.widthTiles() * piece.heightTiles(); tile++)
                assertNotNull(GameServices.level().getCurrentLevel().getPattern(1 + piece.tileIndex() + tile));
        assertTrue(com.openggf.game.sonic3k.Sonic3kPlcArtRegistry.getPlan(23, 1).levelArt().stream()
                .noneMatch(e -> e.key().equals(entry.key())), "sanctuary keeps its own art plan");
    }

    @Test void movingFloorConsumesBreaksAndFailedAllocationStillAdvancesFrontier() {
        for (int free = 0; free <= 1; free++) {
            boot(); var manager = GameServices.level().getObjectManager();
            var floor = DezFinalArenaFloor.moving(); manager.addDynamicObject(floor);
            floor.update(0, null); manager.reserveAllButNFreeSlots(free);
            state().breakRequest(0x80); floor.update(1, null);
            assertEquals(0, state().breakRequest()); assertEquals(0xA0, state().breakFrontier());
            assertEquals(0x150, floor.getX());
            assertEquals(free == 0 ? 0 : 0x80, state().redrawRequest());
            assertEquals(free, manager.activeObjectsOfType(DezFinalArenaFloor.class).stream()
                    .filter(o -> o.modeForTest() == DezFinalArenaFloor.FALLING).count());
            floor.update(2, null);
            assertEquals(0xA0, state().breakFrontier(), "consumed request never retries");
        }
    }
    @Test void movingSupportAdvancesOneColumnPerPassAndRejectsOldBreakRequests() {
        boot(); var floor = DezFinalArenaFloor.moving();
        GameServices.level().getObjectManager().addDynamicObject(floor); floor.update(0, null);
        assertEquals(0x130, floor.getX());
        GameServices.camera().setXCopy((short) 0xC0); floor.update(1, null);
        assertEquals(0x150, floor.getX(), "ROM adds one $20 step, not the camera delta");
        state().breakFrontier(0x100); state().breakRequest(0xE0); floor.update(2, null);
        assertEquals(0, state().breakRequest()); assertEquals(0x100, state().breakFrontier());
        assertEquals(0, state().redrawRequest());
        GameServices.camera().setXCopy((short) 0xA0); floor.update(3, null);
        assertEquals(0x130, floor.getX());
    }
    @Test void entrySupportRetiresWhenTheBossWindowChanges() {
        boot(); var entry = DezFinalArenaFloor.entry();
        GameServices.level().getObjectManager().addDynamicObject(entry); entry.update(0, null);
        assertEquals(0x40, entry.getX()); assertEquals(0xF0, entry.getY()); assertFalse(entry.isDestroyed());
        state().windowBase(0x2C0); entry.update(1, null); assertTrue(entry.isDestroyed());
    }
    @Test void realPlayerLandsOnEntrySupportAtTheScriptedEntryHeight() {
        var fixture = boot(); fixture.sprite().setDebugMode(false);
        var sprite = fixture.sprite();
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(sprite, 0x40);
        com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(sprite, 0xCC);
        sprite.setAir(true); sprite.setYSpeed((short) 0x100);
        GameServices.camera().setScrollLocked(true);
        GameServices.level().getObjectManager().addDynamicObject(DezFinalArenaFloor.entry());
        // loc_7FD9E puts the controlled entry player at $CD. Start one pixel above
        // that support: the uninitialized boss layout above it still has terrain.
        fixture.stepIdleFrames(30);
        assertFalse(sprite.getDead(), "dead at " + sprite.getCentreX() + "," + sprite.getCentreY());
        assertFalse(sprite.getAir(), "airborne at " + sprite.getCentreX() + "," + sprite.getCentreY() + " yvel=" + sprite.getYSpeed());
        assertTrue(sprite.isOnObject(), "the arena object, not layout terrain, owns support");
        assertEquals(0xCD, sprite.getCentreY());
    }
    @Test void openingCollapsePublishesNineteenColumnsEverySixteenPassesAndClearsShake() {
        boot(); state().screenShake().writeFlag(-1);
        var worker = DezFinalArenaBreakup.opening(); GameServices.level().getObjectManager().addDynamicObject(worker);
        int published = 0;
        for (int pass = 0; pass <= 18 * 16; pass++) {
            state().breakRequest(0); worker.update(pass, null);
            if (pass % 16 == 0) assertEquals(0x2D0 + 0x20 * published++, state().breakRequest());
            else assertEquals(0, state().breakRequest());
        }
        assertEquals(19, published); assertTrue(worker.isDestroyed()); assertEquals(0, state().screenShake().flag());
    }
    @Test void chaseCollapseUsesBossHeightAndTimerUntilTheCameraTakesOver() {
        boot(); var worker = DezFinalArenaBreakup.chase(0x400);
        GameServices.level().getObjectManager().addDynamicObject(worker);
        state().bossPosition(0x370, 0x110); worker.update(0, null); assertEquals(0, state().breakRequest());
        state().bossPosition(0x370, 0x10F); worker.update(1, null); assertEquals(0x400, state().breakRequest());
        state().breakRequest(0); state().bossPosition(0x390, 0x10F);
        for (int i = 0; i < 13; i++) worker.update(i + 2, null);
        assertEquals(0, state().breakRequest()); worker.update(15, null); assertEquals(0x420, state().breakRequest());
        state().windowBase(0); state().breakRequest(0); GameServices.camera().setXCopy((short) 0x3A3);
        worker.update(16, null); assertEquals(0, state().breakRequest());
        GameServices.camera().setXCopy((short) 0x3A4); worker.update(17, null); assertEquals(0x440, state().breakRequest());
    }

    @Test void fallingBlockIntegratesOldVelocityAndRecreatesWithItsFraction() {
        var fixture = boot(); var manager = GameServices.level().getObjectManager();
        var floor = DezFinalArenaFloor.moving(); manager.addDynamicObject(floor);
        floor.update(0, null); state().breakRequest(0x80); floor.update(1, null);
        var falling = manager.activeObjectsOfType(DezFinalArenaFloor.class).stream()
                .filter(o -> o.modeForTest() == DezFinalArenaFloor.FALLING).findFirst().orElseThrow();
        falling.update(0, null); assertEquals(0xF0, falling.getY()); assertEquals(0x1A, falling.velocityForTest());
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved = registry.capture();
        for (int i = 0; i < 20; i++) falling.update(i, null);
        int y = falling.getY(), velocity = falling.velocityForTest();
        falling.setDestroyed(true); fixture.stepIdleFrames(1); registry.restore(saved);
        var restored = manager.activeObjectsOfType(DezFinalArenaFloor.class).stream()
                .filter(o -> o.modeForTest() == DezFinalArenaFloor.FALLING).findFirst().orElseThrow();
        assertNotSame(falling, restored);
        for (int i = 0; i < 20; i++) restored.update(i, null);
        assertEquals(y, restored.getY()); assertEquals(velocity, restored.velocityForTest());
    }
}
