package com.openggf.game.rewind;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.sonic3k.objects.MhzMushroomCapObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Obj_MHZMushroomCap keeps the character's last airborne y_vel in $30/$32(a0) and the
 * standing bits in status(a0) (MHZMushroomCap_BounceCharacter, sonic3k.asm:82323-82357).
 * The engine holds both per player in identity-keyed collections. A capture taken while a
 * player stands on the cap must restore them: otherwise the recreated cap re-records the
 * landed y_vel of zero and launches at -$680 where live play launches at -$780 (MHZ1
 * fresh 320px route, frame 3903).
 */
class TestS3kMhzMushroomCapRewind {
    private static final SolidContact STANDING = new SolidContact(true, false, false, true, false);

    private TestablePlayableSprite player;

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x800, 0x500, 0);
        player = new TestablePlayableSprite("sonic", (short) 0x0300, (short) 0x0200);
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @ValueSource(ints = {0x0500, 0x0700, 0x0800})
    void standingRiderBouncesWithItsAirborneSpeedAfterRecreation(int landingSpeed) {
        ObjectManager objectManager = harness();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        MhzMushroomCapObjectInstance source = objectManager.createDynamicObject(
                () -> new MhzMushroomCapObjectInstance(new ObjectSpawn(
                        0x0300, 0x0220, 0x23, 0, 0, false, 7)));

        // Airborne pass: BounceCharacter stores y_vel while the standing bit is clear.
        // The cap reads only y_vel and its own standing bits, never the player's air flag.
        player.setYSpeed((short) landingSpeed);
        source.update(0, player);
        // Landing pass: SolidObjectTop sets the standing bit on an idle (frame 0) cap.
        land();
        source.onSolidContact(player, STANDING, 0);

        ObjectRefId id = objectId(objectManager, source);
        RewindRegistry registry = registryFor(objectManager);
        CompositeSnapshot standing = registry.capture();
        int[] live = bounce(source);
        assertEquals(-(expectedMagnitude(landingSpeed) + 0x20), live[1], "live bounce");

        objectManager.removeDynamicObject(source);
        land();
        registry.restore(standing);
        MhzMushroomCapObjectInstance restored =
                objectById(objectManager, MhzMushroomCapObjectInstance.class, id);
        assertNotSame(source, restored, "restore must recreate the cap");
        assertArrayEquals(live, bounce(restored),
                "restored cap must launch on the same frame with the captured y_vel");
    }

    /** byte thresholds from MHZMushroomCap_BounceCharacter (sonic3k.asm:82333-82341). */
    private static int expectedMagnitude(int previousYVelocity) {
        if (previousYVelocity < 0x660) return 0x660;
        if (previousYVelocity < 0x760) return 0x760;
        return 0x860;
    }

    private void land() {
        player.setYSpeed((short) 0);
    }

    /** Runs the cap's main routine then SolidObjectTop's contact until the launch. */
    private int[] bounce(MhzMushroomCapObjectInstance cap) {
        for (int frame = 1; frame <= 40; frame++) {
            cap.update(frame, player);
            cap.onSolidContact(player, STANDING, frame);
            if (player.getYSpeed() < 0) {
                return new int[]{frame, player.getYSpeed()};
            }
        }
        return fail("cap never launched the standing player");
    }

    private ObjectManager harness() {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 0x800; }
            @Override public short getHeight() { return 0x500; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
            @Override public AbstractPlayableSprite getFocusedSprite() { return player; }
        };
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
        }.withPlayerQuery(new ObjectPlayerQuery(() -> player, List::of));
        ObjectManager objectManager = new ObjectManager(
                List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = objectManager;
        objectManager.reset(0);
        return objectManager;
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        return rewindRegistry;
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        RewindIdentityTable table = objectManager.captureIdentityContext().requireIdentityTable();
        ObjectRefId id = table.idFor(object);
        assertNotNull(id, "ObjectManager capture identity table must register " + object.getClass());
        return id;
    }

    private static <T extends ObjectInstance> T objectById(
            ObjectManager objectManager, Class<T> type, ObjectRefId id) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass() == type && !object.isDestroyed())
                .map(type::cast)
                .sorted(Comparator.comparingInt(o -> ((AbstractObjectInstance) o).getSlotIndex()))
                .filter(object -> objectId(objectManager, object).equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing restored object " + id));
    }
}
