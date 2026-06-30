package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.physics.TrigLookupTable;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestAizGiantRideVineObjectInstance {

    @BeforeEach
    void setUp() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void reservesRomChildSlotsAfterParentSlot() {
        ObjectManager[] holder = new ObjectManager[1];
        StubObjectServices services = new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return holder[0];
            }
        }.withPlayerQuery(new ObjectPlayerQuery(() -> null, List::of));
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0x1D00);
        when(camera.getY()).thenReturn((short) 0x0300);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.isVerticalWrapEnabled()).thenReturn(false);
        ObjectManager manager = new ObjectManager(
                List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;

        ObjectSpawn vineSpawn = new ObjectSpawn(0x1DE0, 0x0360, 0x0C, 0x09, 0, false, 0);
        AizGiantRideVineObjectInstance vine = new AizGiantRideVineObjectInstance(vineSpawn);
        manager.addDynamicObjectAtSlot(vine, 28);
        manager.addDynamicObjectAtSlot(new MarkerObject(new ObjectSpawn(0x1DE0, 0x0360, 0, 0, 0, false, 0)), 29);

        manager.update(0x1D00, null, null, 1);

        assertEquals(40, manager.allocateSlotAfter(vine.getSlotIndex()),
                "ROM Obj_AIZGiantRideVine allocates first/segment/handle child SST slots after the parent");
    }

    @Test
    void grabLeavesFirstChildOnPassiveGlobalAnglePath() throws Exception {
        AizGiantRideVineObjectInstance vine = newVine();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x1E40);
        player.setCentreY((short) 0x0320);

        vine.update(1, player);

        assertFalse(activatedSwingStarted(vine),
                "Passive loc_2248A should remain active while no player is inside the handle grab box");

        player.setCentreX((short) 0x1E00);
        player.setCentreY((short) 0x0310);

        vine.update(2, player);

        assertFalse(activatedSwingStarted(vine),
                "sub_220C2's grab path writes the handle grab byte at $32/$33, but does not switch "
                        + "Obj0C's first child away from loc_2248A");
    }

    @Test
    void frameAfterGrabContinuesPassiveGlobalAngleSine() throws Exception {
        AizGiantRideVineObjectInstance vine = newVine();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x1E00);
        player.setCentreY((short) 0x0310);

        vine.update(1, player);
        assertFalse(activatedSwingStarted(vine));

        vine.update(2, player);

        int expectedAngle = (short) (TrigLookupTable.sinHex(0x03) * 0x2C);
        assertEquals(expectedAngle, firstSegmentAngle(vine),
                "The next segment pass should still use loc_2248A's AIZ_vine_angle sine path");
    }

    @Test
    void jumpReleaseDoesNotRewriteHandleModeOrActivateFirstChild() throws Exception {
        AizGiantRideVineObjectInstance vine = newVine();
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x1E00);
        player.setCentreY((short) 0x0310);

        vine.update(1, player);

        assertFalse(activatedSwingStarted(vine));
        int modeAfterGrab = handleMode(vine);

        player.setJumpInputPressed(true);
        vine.update(2, player);
        vine.updatePostPlayer(2, player);
        player.setJumpInputPressed(false);
        vine.update(3, player);

        assertFalse(activatedSwingStarted(vine),
                "Jump release should not synthesize a first-child loc_224BC transition");
        assertEquals(modeAfterGrab, handleMode(vine),
                "Jump release should not rewrite the handle routine mode");
    }

    private static AizGiantRideVineObjectInstance newVine() {
        ObjectSpawn spawn = new ObjectSpawn(0x1E00, 0x0300, 0x0C, 0x01, 0, false, 0);
        AizGiantRideVineObjectInstance vine = new AizGiantRideVineObjectInstance(spawn);
        vine.setServices(new StubObjectServices()
                .withPlayerQuery(new ObjectPlayerQuery(() -> null, List::of)));
        return vine;
    }

    private static boolean activatedSwingStarted(AizGiantRideVineObjectInstance vine) throws Exception {
        Field field = AizGiantRideVineObjectInstance.class.getDeclaredField("activatedSwingStarted");
        field.setAccessible(true);
        return field.getBoolean(vine);
    }

    private static int firstSegmentAngle(AizGiantRideVineObjectInstance vine) throws Exception {
        Field firstField = AizGiantRideVineObjectInstance.class.getDeclaredField("first");
        firstField.setAccessible(true);
        Object first = firstField.get(vine);
        Field angleField = first.getClass().getDeclaredField("angle");
        angleField.setAccessible(true);
        return angleField.getInt(first);
    }

    private static int handleMode(AizGiantRideVineObjectInstance vine) throws Exception {
        Field handleField = AizGiantRideVineObjectInstance.class.getDeclaredField("handle");
        handleField.setAccessible(true);
        Object handle = handleField.get(vine);
        Field modeField = handle.getClass().getDeclaredField("mode");
        modeField.setAccessible(true);
        return modeField.getInt(handle);
    }

    private static final class MarkerObject extends AbstractObjectInstance {
        private MarkerObject(ObjectSpawn spawn) {
            super(spawn, "Marker");
        }

        @Override
        public void update(int frameCounter, com.openggf.game.PlayableEntity player) {
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }
}
