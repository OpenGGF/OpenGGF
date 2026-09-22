package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezStaircaseObjectInstance;
import com.openggf.game.sonic3k.objects.S3kDezConveyorBeltObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezStaircaseHeadless {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides(); SessionManager.clear(); }

    @Test
    void fourSectionsHaveSeparateLaterSlotsAndOriginalFlipSelectsParentWords() {
        boot();
        for (int flags = 0; flags < 4; flags++) {
            var root = create(1, flags, 0x100 + flags * 0x100);
            root.update(0, null);
            List<S3kDezStaircaseObjectInstance> family = family(root);
            assertEquals(4, family.size());
            for (int i = 0; i < 4; i++) {
                var part = family.get(i);
                if (i > 0) {
                    assertSame(root, part.parentForTest());
                    assertTrue(part.getSlotIndex() > root.getSlotIndex());
                    part.update(0, null);
                }
                assertEquals(root.getX() + i * 32, part.getX());
                int offsetIndex = (flags & 1) == 0 ? i : 3 - i;
                assertEquals(0x400 + (offsetIndex == 0 ? 1 : 0), part.getY());
                assertEquals(flags ^ ((flags & 2) != 0 ? 1 : 0), part.renderFlagsForTest());
            }
        }
    }

    @Test
    void allocationFailureKeepsTheAvailableSectionsAndAllUseTheOriginalRangeAnchor() {
        boot();
        var manager = GameServices.level().getObjectManager();
        var root = create(1, 0, 0x270);
        List<S3kDezConveyorBeltObjectInstance> fillers = new ArrayList<>();
        boolean full = false;
        for (int i = 0; i < 160; i++) {
            var filler = new S3kDezConveyorBeltObjectInstance(new ObjectSpawn(0,0,0x50,0,0,false,0));
            manager.addDynamicObject(filler);
            if (filler.isDestroyed()) { full = true; break; }
            fillers.add(filler);
        }
        assertTrue(full);
        for (int i = 0; i < 2; i++) manager.removeDynamicObject(fillers.removeLast());
        root.update(0, null);
        var family = family(root);
        assertEquals(3, family.size(), "parent plus two successful forward allocations");
        assertEquals(1, root.offsetForTest(0), "allocation failure still falls into the controller");
        for (var part : family) {
            assertFalse(part.isCustomOutOfRange(0), "all sections use the original anchor at the inclusive range edge");
            assertTrue(part.isCustomOutOfRange(-0x80));
        }
    }

    @Test
    void standingContactStartsThirtyTickDelayAndSidesDoNotTriggerIt() {
        var fixture = boot();
        var root = create(0, 0, 0x100);
        root.update(0, null);
        root.onSolidContact(fixture.sprite(), new SolidContact(false,true,false,false,false), 0);
        root.update(1, null);
        assertEquals(0, root.timerForTest());
        root.onSolidContact(fixture.sprite(), new SolidContact(true,false,false,true,false), 1);
        root.update(2, null);
        assertEquals(30, root.timerForTest());
        for (int i = 0; i < 29; i++) root.update(i, null);
        assertEquals(0, root.subtypeForTest());
        assertEquals(1, root.timerForTest());
        root.update(0, null);
        assertEquals(1, root.subtypeForTest());
        assertEquals(0, root.offsetForTest(0), "expiry changes the routine but does not extend yet");
        root.update(0, null);
        assertEquals(1, root.offsetForTest(0));
    }

    @Test
    void undersideTriggerShakesForSixtyTicksBeforeExtending() {
        var fixture = boot();
        var root = create(2, 0, 0x100);
        root.update(0, null);
        root.onSolidContact(fixture.sprite(), new SolidContact(true,false,false,true,false), 0);
        root.update(0, null);
        assertEquals(0, root.timerForTest());
        root.onSolidContact(fixture.sprite(), new SolidContact(false,false,true,false,false), 0);
        root.update(0, null);
        assertEquals(60, root.timerForTest());
        for (int remaining = 59; remaining > 0; remaining--) {
            root.update(0, null);
            int bit = (remaining >>> 2) & 1;
            assertArrayEquals(new int[] {bit,bit^1,bit,bit^1}, offsets(root));
        }
        root.update(0, null);
        assertEquals(3, root.subtypeForTest());
        assertArrayEquals(new int[] {0,1,0,1}, offsets(root), "expiry preserves the last jitter pose");
        root.update(0, null);
        assertArrayEquals(new int[] {1,0,0,0}, offsets(root));
    }

    @Test
    void upwardAndDownwardStepsKeepNativeFractionalRoundingAndStopAt128() {
        boot();
        var down = create(1, 0, 0x100);
        var up = create(5, 0, 0x200);
        down.update(0, null);
        up.update(0, null);
        assertArrayEquals(new int[] {1,0,0,0}, offsets(down));
        assertArrayEquals(new int[] {-1,-1,-1,-1}, offsets(up));
        assertEquals(1, up.renderFlagsForTest(), "upward subtype flips render art, not word order");
        for (int i = 0; i < 200; i++) { down.update(i, null); up.update(i, null); }
        assertArrayEquals(new int[] {128,96,64,32}, offsets(down));
        assertArrayEquals(new int[] {-128,-96,-64,-32}, offsets(up));
    }

    @Test
    void childContactAndParentWordGraphRestoreAcrossTheShakeAndExtension() {
        var fixture = boot();
        var root = create(6, 1, 0x100);
        root.update(0, null);
        var parts = family(root);
        assertEquals(4, parts.size());
        parts.get(2).onSolidContact(fixture.sprite(), new SolidContact(false,false,true,false,false), 0);
        for (int i = 0; i < 10; i++) advance(parts);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before = registry.capture();
        List<String> first = graphFrames(parts, 180);
        for (var part : parts) {
            part.setDestroyed(true);
            GameServices.level().getObjectManager().removeDynamicObject(part);
        }
        registry.restore(before);
        var restored = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof S3kDezStaircaseObjectInstance && o.getX() == 0x100)
                .map(o -> (S3kDezStaircaseObjectInstance) o).findFirst().orElseThrow();
        assertNotSame(root, restored, "force recreation of the complete graph, not in-place restore");
        var restoredParts = family(restored);
        assertEquals(4, restoredParts.size());
        for (var part : restoredParts) if (part.childForTest()) assertSame(restored, part.parentForTest());
        assertEquals(first, graphFrames(restoredParts, 180));
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 800})
    void placedStaircaseAcceptsLandingThenRisesAndReplays(int width) {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width == 320 ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.SUPER_32_9).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .startPosition((short) 0xED0, (short) 0x610).startPositionIsCentre().build();
        assertEquals(width, GameServices.camera().getWidth());
        fixture.sprite().setAir(true);
        fixture.sprite().setRingCount(7);
        fixture.stepIdleFrames(35);
        var root = placedRoot();
        assertTrue(root.timerForTest() > 0 || root.subtypeForTest() == 5, "actual landing triggers the staircase");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before = registry.capture();
        List<String> first = placedFrames(fixture, 180);
        assertEquals(0x5D0, root.getY(), "the first section rises the full $80");
        assertFalse(fixture.sprite().getDead());
        registry.restore(before);
        assertEquals(first, placedFrames(fixture, 180), "player, moving sections and nearby hazards replay");
    }

    @Test
    void invertedActTwoLandingActivatesTheFlippedPlacedStaircase() {
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 1)
                .startPosition((short) 0x950, (short) 0x7D0).startPositionIsCentre().build();
        // Declared component-entry state: earlier route switches own the flag.
        GameServices.gameState().setReverseGravityActive(true);
        fixture.sprite().setAir(true);
        fixture.sprite().setRingCount(7);
        fixture.stepIdleFrames(35);
        var root = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof S3kDezStaircaseObjectInstance && o.getX() == 0x950)
                .map(o -> (S3kDezStaircaseObjectInstance) o).findFirst().orElseThrow();
        assertTrue(root.timerForTest() > 0 || root.subtypeForTest() == 5,
                "inverted standing contact still publishes the standing return bit");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before = registry.capture();
        List<String> first = invertedFrames(fixture, 180);
        assertEquals(0x770, root.getY(), "original X-flip assigns the quarter-height word to the parent");
        assertFalse(fixture.sprite().getDead());
        registry.restore(before);
        assertEquals(first, invertedFrames(fixture, 180));
    }

    private List<String> invertedFrames(HeadlessTestFixture fixture, int count) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            fixture.stepIdleFrames(1);
            var p = fixture.sprite();
            rows.add(p.getCentreX() + "," + p.getCentreY() + "," + p.getYSpeed()
                    + ",standing=" + p.isOnObject() + ",reverse=" + GameServices.gameState().isReverseGravityActive());
        }
        return rows;
    }

    private S3kDezStaircaseObjectInstance placedRoot() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof S3kDezStaircaseObjectInstance && o.getX() == 0xED0)
                .map(o -> (S3kDezStaircaseObjectInstance) o).findFirst().orElseThrow();
    }
    private List<String> placedFrames(HeadlessTestFixture fixture, int count) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            fixture.stepIdleFrames(1);
            var p = fixture.sprite();
            rows.add(p.getCentreX() + "," + p.getCentreY() + "," + p.getYSpeed()
                    + ",hurt=" + p.isHurt() + ",rings=" + p.getRingCount() + ",stair=" + placedRoot().getY());
        }
        return rows;
    }

    @Test
    void staircaseBindsSingleBridgeMappingToItsOwnTileBank() throws Exception {
        boot();
        var frames = S3kSpriteDataLoader.loadMappingFrames(TestEnvironment.objectServices().romReader(), 0x46F7A, 1);
        assertEquals(1, frames.getFirst().pieces().size());
        var piece = frames.getFirst().pieces().getFirst();
        assertEquals(-16, piece.xOffset()); assertEquals(-16, piece.yOffset());
        assertEquals(4, piece.widthTiles()); assertEquals(4, piece.heightTiles());
        assertEquals(0, piece.tileIndex());
        for (int act = 0; act < 2; act++) {
            var entry = Sonic3kPlcArtRegistry.getPlan(11, act).levelArt().stream()
                    .filter(e -> e.key().equals(Sonic3kObjectArtKeys.DEZ_STAIRCASE)).findFirst().orElseThrow();
            assertEquals(0x46F7A, entry.mappingAddr());
            assertEquals(0x480, entry.artTileBase());
            assertEquals(1, entry.palette());
            assertEquals(1, entry.mappingFrameCount());
        }
    }

    private int[] offsets(S3kDezStaircaseObjectInstance root) {
        return new int[] {root.offsetForTest(0),root.offsetForTest(1),root.offsetForTest(2),root.offsetForTest(3)};
    }
    private List<String> graphFrames(List<S3kDezStaircaseObjectInstance> parts, int count) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            advance(parts);
            result.add(parts.stream().map(p -> Integer.toString(p.getY())).toList() + ":" + parts.getFirst().timerForTest());
        }
        return result;
    }
    private void advance(List<S3kDezStaircaseObjectInstance> parts) { for (var part : parts) part.update(0, null); }
    private List<S3kDezStaircaseObjectInstance> family(S3kDezStaircaseObjectInstance root) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o == root || o instanceof S3kDezStaircaseObjectInstance s && s.parentForTest() == root)
                .map(o -> (S3kDezStaircaseObjectInstance) o).sorted(Comparator.comparingInt(S3kDezStaircaseObjectInstance::getX)).toList();
    }
    private S3kDezStaircaseObjectInstance create(int subtype, int flags, int x) {
        var object = new S3kDezStaircaseObjectInstance(new ObjectSpawn(x, 0x400, 0x4F, subtype, flags, false, 0));
        GameServices.level().getObjectManager().addDynamicObject(object);
        return object;
    }
    private HeadlessTestFixture boot() {
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0).build();
    }
}
