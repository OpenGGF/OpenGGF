package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.rules.GameRules;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Component solid-loop coverage, not a cold route or native execution oracle. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszRotatingPlatformSolidContacts {
    private static final int X = 0x200;
    private static final int Y = 0xC00;
    private static final int CAMERA_X = 0x100;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @ParameterizedTest(name = "carrier={0}, nativeP2={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void ownerAcquiredPositiveControlKeepsRealRiderAcrossHeldCheckpoints(boolean carrier, boolean nativeP2) {
        Harness h = harness(carrier, nativeP2);
        h.land();
        assertFalse(h.player.isObjectControlled(), "the fresh standing pass only arms the owner");
        h.step();
        assertHeld(h, "owner acquisition");
        for (int i = 0; i < 8; i++) {
            h.step();
            assertHeld(h, "continued held checkpoint " + i);
        }
        assertEquals(nativeP2, h.player.isCpuControlled(), "native P2 remains the CPU slot");
        assertFalse(h.owner.isDestroyed());
        assertTrue(h.manager.getActiveObjects().stream().anyMatch(object -> object == h.owner));
    }

    @ParameterizedTest(name = "carrier={0}, nativeP2={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void heldButtonDoesNotReleaseButFreshLogicalJumpReleasesBeforeNextPose(boolean carrier, boolean nativeP2) {
        Harness h = harness(carrier, nativeP2);
        h.land();
        h.step();
        assertHeld(h, "owner acquisition");
        h.player.setLogicalInputState(false, false, false, false, true, false);
        h.step();
        assertHeld(h, "held A without a logical edge");
        h.player.setLogicalInputState(false, false, false, false, false, false);
        h.step();
        assertHeld(h, "released A");
        int releaseX = h.player.getCentreX();
        h.player.setLogicalInputState(false, false, false, false, true, true);
        h.step();
        assertNull(h.manager.getRidingObject(h.player));
        assertTrue(h.player.isOnObject(),
                "loc_45E90/loc_45FE4 set InAir without clearing the player OnObj bit");
        assertTrue(h.player.getAir());
        assertTrue(h.player.isJumping());
        assertTrue(h.player.getRolling());
        assertFalse(h.player.isObjectControlled());
        assertFalse(h.player.isObjectControlAllowsCpu());
        assertFalse(h.player.isObjectControlSuppressesMovement());
        assertEquals(-0x680, h.player.getYSpeed());
        assertEquals(releaseX, h.player.getCentreX(), "the jump branch precedes centering/swing");
        h.player.clearLogicalInputState();
        h.step();
        assertNull(h.manager.getRidingObject(h.player), "upward release must not reacquire the surface");
        assertTrue(h.player.getAir());
        assertFalse(h.player.isObjectControlled());
    }

    @ParameterizedTest(name = "carrier={0}, nativeP2={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void signedBitSevenStillRejectsNewContactBeforeOwnerArming(boolean carrier, boolean nativeP2) {
        Harness h = harness(carrier, nativeP2);
        ObjectControlState.nativeBit7FullControl().applyTo(h.player);
        h.step();
        assertNull(h.manager.getRidingObject(h.player));
        assertFalse(h.player.isOnObject());
        assertTrue(h.player.getAir());
        assertEquals(0x100, h.player.getYSpeed());
        assertTrue(h.player.isObjectControlled());
        assertFalse(h.player.isObjectControlAllowsCpu(), "the owner did not replace bit7 control with 3");
    }

    private static void assertHeld(Harness h, String phase) {
        assertSame(h.owner, h.manager.getRidingObject(h.player), phase);
        assertTrue(h.player.isOnObject(), phase);
        assertFalse(h.player.getAir(), phase);
        assertTrue(h.player.isObjectControlled(), phase);
        assertTrue(h.player.isObjectControlAllowsCpu(), phase);
        assertTrue(h.player.isObjectControlSuppressesMovement(), phase);
        assertEquals(0, h.player.getXSpeed(), phase);
        assertEquals(0, h.player.getYSpeed(), phase);
        assertEquals(0, h.player.getGSpeed(), phase);
    }

    private static Harness harness(boolean carrier, boolean nativeP2) {
        var player = player();
        var main = nativeP2 ? player() : player;
        player.setCpuControlled(nativeP2);
        var sidekicks = nativeP2 ? List.of(player) : List.<TestPlayableSprite>of();
        Camera camera = GameServices.camera();
        camera.setX((short) CAMERA_X);
        camera.setY((short) 0xB80);
        var solids = new DefaultSolidExecutionRegistry();
        ObjectManager[] holder = new ObjectManager[1];
        var services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            @Override public SolidExecutionRegistry solidExecutionRegistry() { return solids; }
        };
        services.withPlayerQuery(new ObjectPlayerQuery(() -> main, () -> sidekicks));
        var manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(CAMERA_X);
        var post = manager.createDynamicObject(() -> new SszRotatingPlatformObjectInstance(
                new ObjectSpawn(X, Y, 0x76, 1, 0, false, 0)));
        manager.update(CAMERA_X, main, sidekicks, 0, false, true, false);
        AbstractObjectInstance owner = carrier ? post.carrierForTest() : post;
        assertNotNull(owner, "the actual post must allocate its carrier through the manager");
        var params = ((SolidObjectProvider) owner).getSolidParams();
        player.setCentreX((short) (X + (carrier ? 0x30 : 4)));
        player.setCentreY((short) (owner.getY() - params.airHalfHeight() - player.getYRadius() + 7));
        player.setYSpeed((short) 0x100);
        player.setAir(true);
        player.setOnObject(false);
        return new Harness(manager, owner, player, main, sidekicks);
    }

    private static TestPlayableSprite player() {
        var player = new S3kPlayer();
        player.setCentreX((short) 0x380);
        player.setCentreY((short) 0xB00);
        player.setAir(true);
        return player;
    }

    private static final class S3kPlayer extends TestPlayableSprite {
        private S3kPlayer() {
            super(28, 38);
            setGameRulesForTest(GameRules.SONIC_3K);
        }
    }

    private static final class Harness {
        private final ObjectManager manager;
        private final AbstractObjectInstance owner;
        private final TestPlayableSprite player;
        private final TestPlayableSprite main;
        private final List<TestPlayableSprite> sidekicks;
        private int frame = 1;

        private Harness(ObjectManager manager, AbstractObjectInstance owner, TestPlayableSprite player,
                        TestPlayableSprite main, List<TestPlayableSprite> sidekicks) {
            this.manager = manager;
            this.owner = owner;
            this.player = player;
            this.main = main;
            this.sidekicks = sidekicks;
        }

        private void step() {
            manager.update(CAMERA_X, main, sidekicks, frame++, false, true, false);
        }

        private void land() {
            step();
            assertSame(owner, manager.getRidingObject(player), "fresh public solid landing");
            assertTrue(player.isOnObject());
            assertFalse(player.getAir());
        }
    }
}
