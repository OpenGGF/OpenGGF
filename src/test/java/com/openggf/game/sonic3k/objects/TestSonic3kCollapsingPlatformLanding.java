package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rules.GameRules;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectPassTestSupport;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.sprites.playable.Knuckles;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** The component checkpoint after player movement, without terrain or replay imports. */
@org.junit.jupiter.api.extension.ExtendWith(com.openggf.tests.SingletonResetExtension.class)
@com.openggf.tests.FullReset
@org.junit.jupiter.api.parallel.Isolated
class TestSonic3kCollapsingPlatformLanding {
    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        com.openggf.tests.TestEnvironment.configureGameModuleFixture(
                new com.openggf.game.sonic3k.Sonic3kGameModule());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.openggf.game.session.SessionManager.clear();
    }

    @ParameterizedTest
    @CsvSource({"false,48,10,0,false", "false,48,10,1,true", "false,48,10,16,true",
            "false,48,10,17,false", "true,48,10,0,false", "true,48,10,1,true",
            "true,48,10,14,true", "true,48,10,16,true", "true,48,10,17,false",
            "true,-59,19,16,true", "false,59,19,16,true", "false,0,19,17,false"})
    void directSlopedTopWindowUsesFeetOverlapWithoutSideOrBottomClassification(
            boolean flipped, int dx, int radius, int overlap, boolean lands) {
        var player = new LandingKnuckles();
        var camera = mock(Camera.class);
        var solids = new DefaultSolidExecutionRegistry();
        var holder = new ObjectManager[1];
        var services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public SolidExecutionRegistry solidExecutionRegistry() { return solids; }
        };
        var manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                null, null, mock(GraphicsManager.class), camera, services);
        holder[0] = manager;
        manager.reset(0);
        var platform = new Sonic3kCollapsingPlatformObjectInstance(
                new ObjectSpawn(0x200, 0x300, 4, 0, flipped ? 1 : 0, false, 0));
        manager.addDynamicObject(platform);
        platform.snapshotPreUpdatePosition();
        int relativeX = dx + 0x3C;
        int sample = (flipped ? 0x78 - 1 - relativeX : relativeX) >> 1;
        int surface = platform.getY() - platform.getSlopeData()[sample];
        player.setCentreX((short) (platform.getX() + dx));
        player.setCentreY((short) (surface - radius - 4 + overlap));
        player.setAir(true);
        player.applyCustomRadii(10, radius);
        player.setDoubleJumpFlag(5);
        player.setAnimationId(0x20);
        player.setYSpeed((short) 0);
        int beforeY = player.getCentreY();

        SolidObjectPassTestSupport.resolveSolidCheckpoints(manager, player, true);

        assertEquals(lands, manager.isRidingObject(player, platform));
        assertEquals(!lands, player.getAir());
        if (lands) {
            assertEquals(surface - radius - 1, player.getCentreY(),
                    "loc_1E45A writes the landing centre before Player_TouchFloor restores the radius");
            assertEquals(0, player.getDoubleJumpFlag());
            assertEquals(0, player.getAnimationId());
            assertTrue(manager.hasObjectStandingBit(player, platform));
        } else {
            assertEquals(beforeY, player.getCentreY());
        }
    }

    private static final class LandingKnuckles extends Knuckles {
        LandingKnuckles() {
            super("knuckles-landing", (short) 0, (short) 0);
            setGameRulesForTest(GameRules.SONIC_3K);
        }
    }
}
