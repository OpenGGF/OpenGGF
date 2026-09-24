package com.openggf.tests;

import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezLightningObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** ROM animation/dispatch boundaries for SKL $52; no fixture-fitted timing. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezLightningHeadless {
    @AfterEach void reset() {
        AudioManager.getInstance().setRequestObserver(null);
        AbstractObjectInstance.resetCameraBoundsForTests();
        SessionManager.clear();
    }

    @Test
    void twoTickFlashFramesAndSignedSubtypeWaitGateOnlyFrameThree() throws Exception {
        boot();
        var reader = TestEnvironment.objectServices().romReader();
        assertArrayEquals(new byte[] {0, 2, 1, 1, 2, 3, 4, (byte) 0xFC}, reader.slice(0x47926, 8),
                "the actual Animate_Sprite table and its single script");
        int[] flash = {1, 1, 2, 2, 3, 3, 4, 4, 0};
        for (int subtype : new int[] {0, 1, 0x24, 0xFF}) {
            var object = create(subtype);
            int period = 9 + subtype;
            for (int frame = 0; frame < period * 3; frame++) {
                object.update(frame, null);
                int phase = frame % period;
                int expected = phase < flash.length ? flash[phase] : 0;
                assertEquals(expected, object.mappingFrameForTest(), "subtype=" + subtype + " frame=" + frame);
                assertEquals(0x9F, object.getCollisionFlags());
                assertEquals(expected == 3, object.publishesTouchResponseListEntryThisFrame());
                assertEquals(phase >= 8, object.waitingForTest());
            }
            assertFalse(object.requiresRenderFlagForTouch(), "Sprite_CheckDeleteTouch3 builds the list directly");
            assertFalse(object.usesCurrentTouchResponseState(), "the player reads the preceding published pointer list");
        }
    }

    @Test
    void playerTouchConsumesPoseThreeOnTheFollowingPlayerPassAndReplays() {
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .startPosition((short) 0x730, (short) 0x700).startPositionIsCentre().build();
        fixture.sprite().setRingCount(7);
        fixture.sprite().setAir(true);
        var object = new S3kDezLightningObjectInstance(new ObjectSpawn(
                fixture.sprite().getCentreX(), fixture.sprite().getCentreY(), 0x52, 0x24, 0, false, 100));
        object.setServices(TestEnvironment.objectServices());
        GameServices.level().getObjectManager().addDynamicObject(object);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        // Capture after the first dispatch: the pending collision list is empty.
        fixture.stepIdleFrames(1);
        assertEquals(1, object.mappingFrameForTest());
        assertFalse(fixture.sprite().isHurt());
        var before = registry.capture();
        List<String> first = contactFrames(fixture, 8);
        assertTrue(first.stream().anyMatch(row -> row.contains("hurt=true")), "the flash must hurt through the real player loop");
        assertFalse(fixture.sprite().getDead());
        registry.restore(before);
        assertEquals(first, contactFrames(fixture, 8), "pending list, damage and motion restore together");
        registry.restore(before);
        for (int i = 0; i < 8 && contactObject().mappingFrameForTest() != 3; i++) fixture.stepIdleFrames(1);
        assertEquals(3, contactObject().mappingFrameForTest());
        assertFalse(fixture.sprite().isHurt(), "pose 3 has just published its first damaging pointer");
        var liveList = registry.capture();
        List<String> afterLiveList = contactFrames(fixture, 4);
        assertTrue(afterLiveList.getFirst().contains("hurt=true"));
        registry.restore(liveList);
        assertEquals(afterLiveList, contactFrames(fixture, 4), "rewind preserves a pending damaging pointer");
    }

    @Test
    void placedLightningStillHurtsALightningShieldBeforeTheShockFloor() {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0)
                .startPosition((short) 0x780, (short) 0x780).startPositionIsCentre().build();
        fixture.sprite().giveShield(com.openggf.game.ShieldType.LIGHTNING);
        fixture.sprite().setRingCount(7);
        // Obj_DEZLightning writes collision_flags=$9F but never sets the
        // shield_reaction bit5 that Obj_InvisibleShockBlock sets separately.
        for (int i=0; i<12 && !fixture.sprite().isHurt(); i++) fixture.stepIdleFrames(1);
        assertTrue(fixture.sprite().isHurt());
        assertFalse(fixture.sprite().hasShield());
        assertTrue(fixture.sprite().getCentreY() < 0x7D2 - 32,
                "damage precedes solid contact with the separately immune floor");
    }

    private List<String> contactFrames(HeadlessTestFixture fixture, int count) {
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var object = contactObject();
            int publishedPose = object.mappingFrameForTest();
            boolean alreadyHurt = fixture.sprite().isHurt();
            fixture.stepIdleFrames(1);
            var p = fixture.sprite();
            if (!alreadyHurt) assertEquals(publishedPose == 3, p.isHurt(),
                    "player consumes the pose published on the preceding object pass, pose=" + publishedPose);
            rows.add(p.getCentreX() + "," + p.getCentreY() + ",hurt=" + p.isHurt()
                    + ",rings=" + p.getRingCount());
        }
        return rows;
    }

    private S3kDezLightningObjectInstance contactObject() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof S3kDezLightningObjectInstance && o.getSpawn().subtype() == 0x24)
                .map(o -> (S3kDezLightningObjectInstance) o).findFirst().orElseThrow();
    }

    @Test
    void soundReadsTheCarriedRenderFlagEvenDuringTheEmptyWaitPose() {
        boot();
        List<Integer> sounds = new ArrayList<>();
        AudioManager.getInstance().setRequestObserver((kind, id) -> sounds.add(id));
        var object = create(0);
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0x800);
        object.update(0, null);
        assertTrue(sounds.isEmpty(), "initial render_flags=$04 has no on-screen bit");
        object.refreshPostCameraRenderState();
        for (int frame = 1; frame <= 9; frame++) {
            object.update(frame, null);
            object.refreshPostCameraRenderState();
        }
        assertEquals(List.of(0x79), sounds, "loc_478BE sounds at restart after a visible blank frame");
        AbstractObjectInstance.updateCameraBounds(0x1000, 0, 0x1140, 224, 0x800);
        object.refreshPostCameraRenderState();
        for (int frame = 10; frame <= 18; frame++) object.update(frame, null);
        assertEquals(List.of(0x79), sounds, "off-screen restart stays silent");
    }

    @Test
    void flashAndCooldownRestoreAndReplay() {
        boot();
        var object = create(0x24);
        GameServices.level().getObjectManager().addDynamicObject(object);
        for (int frame = 0; frame < 5; frame++) object.update(frame, null);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var before = registry.capture();
        List<String> first = advance(object, 70);
        registry.restore(before);
        var restored = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o instanceof S3kDezLightningObjectInstance && o.getX() == 100)
                .map(o -> (S3kDezLightningObjectInstance) o).findFirst().orElseThrow();
        assertEquals(3, restored.mappingFrameForTest());
        assertEquals(first, advance(restored, 70));
    }

    @Test
    void bothActsBindTheFiveFrameRomMappingToDezMisc() throws Exception {
        boot();
        var frames = S3kSpriteDataLoader.loadMappingFrames(TestEnvironment.objectServices().romReader(), 0x4792E, 5);
        assertArrayEquals(new int[] {0, 1, 1, 2, 2},
                frames.stream().mapToInt(f -> f.pieces().size()).toArray());
        for (int act = 0; act < 2; act++) {
            var entry = Sonic3kPlcArtRegistry.getPlan(11, act).levelArt().stream()
                    .filter(e -> e.key().equals(Sonic3kObjectArtKeys.DEZ_LIGHTNING)).findFirst().orElseThrow();
            assertEquals(0x4792E, entry.mappingAddr());
            assertEquals(0x379, entry.artTileBase()); // ArtTile_DEZMisc=$34D plus $2C (sonic3k.lst:1946)
            assertEquals(0, entry.palette());
            assertEquals(5, entry.mappingFrameCount());
        }
    }

    private List<String> advance(S3kDezLightningObjectInstance object, int frames) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            object.update(i, null);
            result.add(object.mappingFrameForTest() + "," + object.cooldownForTest() + "," + object.getCollisionFlags());
        }
        return result;
    }

    private S3kDezLightningObjectInstance create(int subtype) {
        var object = new S3kDezLightningObjectInstance(new ObjectSpawn(100, 100, 0x52, subtype, 0, false, 100));
        object.setServices(TestEnvironment.objectServices());
        return object;
    }

    private void boot() {
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ, 0).build();
    }
}
