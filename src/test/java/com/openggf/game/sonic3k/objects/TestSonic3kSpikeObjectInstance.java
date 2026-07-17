package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kSpikeObjectInstance {

    private static final class TestableSprite extends AbstractPlayableSprite {
        TestableSprite(String code) {
            super(code, (short) 0, (short) 0);
        }

        @Override public void draw() { }
        @Override public void defineSpeeds() { }
        @Override protected void createSensorLines() { }
    }

    @Test
    void spikesUseSolidObjectFullInclusiveRightEdge() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x1FF8, 0x0564, Sonic3kObjectIds.SPIKES, 0x00, 0, false, 0));

        assertTrue(spikes.usesInclusiveRightEdge(),
                "Obj_Spikes calls SolidObjectFull; SolidObject_cont rejects relX > width*2, not relX == width*2");
    }

    @Test
    void spikesUseStrictSolidObjectFullRideBoundaryWithoutStickyExtension() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x0870, 0x0290, Sonic3kObjectIds.SPIKES, 0x00, 0, false, 0));

        assertFalse(spikes.usesStickyContactBuffer(),
                "SolidObjectFull continued riding clears at its exact d1 bound; the engine's "
                        + "16px platform tolerance must not collapse the authored gap between FBZ2 spikes");
    }

    @Test
    void spikesUseSolidObjectFullAirborneStaleStandingBitReturn() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x01D0, 0x05F0, Sonic3kObjectIds.SPIKES, 0x00, 0, false, 0));

        assertTrue(spikes.airborneStaleStandingBitReturnsNoContact(null),
                "Obj_Spikes calls SolidObjectFull; an airborne player with this object's standing bit set "
                        + "must clear support and return before SolidObject_cont creates a fresh contact");
    }

    @Test
    void movingSpikesKeepSolidLatchOnLiveObjectSlot() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x0C06, 0x06D4, Sonic3kObjectIds.SPIKES, 0x01, 0, false, 0));

        assertTrue(spikes.usesInstanceSolidStateLatchKey(),
                "Obj_Spikes stores standing/pushing bits in status(a0), so retracting spikes must keep "
                        + "solid latch state on the live object slot while updateDynamicSpawn changes position");
    }

    @Test
    void spikesInitFrameDoesNotMoveOrRunSolidBodyUntilNextExecution() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x2000, 0x0145, Sonic3kObjectIds.SPIKES, 0x01, 0x02, false, 0));

        spikes.update(11818, null);

        assertEquals(0x0145, spikes.getY(),
                "Obj_Spikes init returns before sub_242B6 can apply vertical movement");
        assertFalse(spikes.isSolidFor(null),
                "Obj_Spikes init returns before loc_2413E can call SolidObjectFull");

        spikes.update(11819, null);

        assertEquals(0x014D, spikes.getY(),
                "First main routine execution applies sub_242B6 vertical movement");
        assertTrue(spikes.isSolidFor(null),
                "SolidObjectFull is available once the main routine body is reached");
    }

    @Test
    void movingSpikesUseSavedOriginForOffscreenLifecycle() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x1280, 0x09D0, Sonic3kObjectIds.SPIKES, 0x01, 0, false, 0));

        spikes.update(0, null);
        spikes.update(1, null);

        assertEquals(0x09D8, spikes.getY(),
                "First main routine execution should move the vertical spike from its placement Y.");
        assertEquals(0x1280, spikes.getOutOfRangeReferenceX(),
                "S3K Obj_Spikes uses saved $30(a0), not live position, for Sprite_OnScreen_Test2 "
                        + "(docs/skdisasm/sonic3k.asm:49038-49039,49071-49072,49102-49103).");
    }

    @Test
    void spikesSolidGateUsesRenderSpritesExclusiveBottomEdge() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x1170, 0x08B0, Sonic3kObjectIds.SPIKES, 0x30, 0, false, 0));
        spikes.snapshotPreUpdatePosition();

        AbstractObjectInstance.updateCameraBounds(0x10CE, 0x07C1, 0x10CE + 320, 0x07C1 + 224, 0);
        assertTrue(spikes.isWithinSolidContactBounds(),
                "One pixel inside Render_Sprites' bottom edge, spikes keep render_flags bit 7 set.");

        AbstractObjectInstance.updateCameraBounds(0x10CE, 0x07C0, 0x10CE + 320, 0x07C0 + 224, 0);
        assertFalse(spikes.isWithinSolidContactBounds(),
                "CNZ f21147: Render_Sprites rejects y_pos + height_pixels == camera_y + 224 + 2*height "
                        + "with bhs, so SolidObjectFull must see render_flags bit 7 clear next frame "
                        + "(sonic3k.asm:36358-36365, 41016-41018, 49011-49039).");
    }

    @Test
    void movingSpikesUsePreviousRenderPositionForSolidGate() {
        TestableSpike spikes = new TestableSpike(
                new ObjectSpawn(0x1170, 0x08B0, Sonic3kObjectIds.SPIKES, 0x31, 0, false, 0));
        AbstractObjectInstance.updateCameraBounds(0x10CE, 0x07C0,
                0x10CE + 320, 0x07C0 + 224, 0);
        spikes.snapshotPreUpdatePosition();
        spikes.setCurrentYForTest(0x08AF);

        assertFalse(spikes.isWithinSolidContactBounds(),
                "loc_1DF88 reads the previous Render_Sprites flag, so movement into the viewport "
                        + "cannot make SolidObjectFull active until the next object dispatch");
    }

    @Test
    void pushSpikesUseDelayedPlayerPushSnapshotAndPreserveXSubpixel() {
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x24C0, 0x0570, Sonic3kObjectIds.SPIKES, 0x03, 0, false, 0));
        TestableSprite player = new TestableSprite("sonic");
        player.setCentreX((short) 0x24A5);
        player.setCentreY((short) 0x056C);
        player.setSubpixelRaw(0xA300, 0x4B00);

        // Init returns before loc_24356/SolidObjectFull.
        spikes.update(0, player);

        // f13765 equivalent: the pre-solid snapshot sees Push clear, then this
        // frame's SolidObject sets both the object contact and live player Push.
        spikes.update(1, player);
        spikes.onSolidContact(player,
                new SolidContact(false, true, false, false, true, 0, true), 1);
        player.setPushing(true);

        // f13766: object push contact is live, but saved $3E Push is still clear.
        spikes.update(2, player);
        assertEquals(0x24C0, spikes.getX(),
                "loc_24356 must not move until the prior player-status snapshot also has Push");
        assertEquals(0x24A5, player.getCentreX() & 0xFFFF);
        assertEquals(0xA300, player.getXSubpixelRaw());
        spikes.onSolidContact(player,
                new SolidContact(false, true, false, false, true, 0, true), 2);

        // f13767: prior object contact and saved player Push now agree.
        spikes.update(3, player);
        assertEquals(0x24C1, spikes.getX());
        assertEquals(0x24A6, player.getCentreX() & 0xFFFF);
        assertEquals(0xA300, player.getXSubpixelRaw(),
                "ROM addq.w #1,x_pos(a1) preserves x_sub");
    }

    @Test
    void pushSpikesProcessNativeP1P2BeforeExtraWithSharedRateTimer() throws Exception {
        TestableSprite p1 = pushingPlayer("sonic", 0x24A5);
        TestableSprite p2 = pushingPlayer("tails_p2", 0x24A4);
        TestableSprite extra = pushingPlayer("knuckles_p3", 0x24A3);
        Sonic3kSpikeObjectInstance spikes = new Sonic3kSpikeObjectInstance(
                new ObjectSpawn(0x24C0, 0x0570, Sonic3kObjectIds.SPIKES, 0x03, 0, false, 0));

        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return 0x2400; }
            @Override public short getY() { return 0x0500; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public GraphicsManager graphicsManager() { return null; }
            @Override public Camera camera() { return camera; }
            @Override public List<PlayableEntity> sidekicks() { return List.of(p2, extra); }
            @Override public ObjectPlayerQuery playerQuery() {
                return new ObjectPlayerQuery(() -> p1, () -> List.of(p2, extra));
            }
        };
        ObjectManager manager = new ObjectManager(List.of(), registryFor(spikes), 0,
                null, null, null, camera, services);
        holder[0] = manager;
        manager.reset(0);
        manager.addDynamicObject(spikes);

        // Init frame returns before loc_24356.
        manager.update(0x2400, p1, List.of(p2, extra), 0, false, true, false);
        FbzParticipantStateTable table = (FbzParticipantStateTable)
                field(spikes, "pushParticipants").get(spikes);
        table.restoreRewindStateValue(new FbzParticipantStateTable.Snapshot(
                3, new int[][]{{1, 1, 1}, {1, 1, 1}}));
        field(spikes, "pushRateTimer").setInt(spikes, 0);

        manager.update(0x2400, p1, List.of(p2, extra), 1, false, true, false);

        assertEquals(0x24C1, spikes.getX(), "P1 consumes the immediate push slot first");
        assertEquals(0x24A6, p1.getCentreX() & 0xFFFF);
        assertEquals(0x24A4, p2.getCentreX() & 0xFFFF,
                "P2 runs second and only decrements the shared timer from 16 to 15");
        assertEquals(0x24A3, extra.getCentreX() & 0xFFFF,
                "extra sidekicks run only after both native participants");
        assertEquals(14, field(spikes, "pushRateTimer").getInt(spikes),
                "P1 reset to 16, then P2 and the labelled extra each decrement once");
    }

    private static final class TestableSpike extends Sonic3kSpikeObjectInstance {
        private TestableSpike(ObjectSpawn spawn) {
            super(spawn);
        }

        private void setCurrentYForTest(int y) {
            currentY = y;
        }
    }

    private static TestableSprite pushingPlayer(String code, int x) {
        TestableSprite player = new TestableSprite(code);
        player.setWidth(18);
        player.setHeight(38);
        player.setCentreX((short) x);
        player.setCentreY((short) 0x056C);
        player.setPushing(true);
        return player;
    }

    private static ObjectRegistry registryFor(ObjectInstance instance) {
        return new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) { return instance; }
            @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
            @Override public String getPrimaryName(int objectId) { return "Spikes"; }
        };
    }

    private static Field field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
