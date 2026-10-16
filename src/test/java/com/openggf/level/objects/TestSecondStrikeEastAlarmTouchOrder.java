package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.difficulty.LbzAlarmRecipe;
import com.openggf.game.sonic3k.objects.LbzAlarmObjectInstance;
import com.openggf.game.sonic3k.objects.badniks.Flybot767BadnikInstance;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real touch-list ordering witness: an alarm earlier in the list than a hurt
 * object. STOP_AFTER_FIRST_OVERLAP means a published alarm region can end the
 * player's pass before the later object; a blocked BOUNDED alarm publishes none.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSecondStrikeEastAlarmTouchOrder {
    private static final int X = 400, Y = 200;

    @AfterEach void cleanup() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        SessionManager.clear();
    }

    /** Minimal hurt object ($80 hurt category, size $17) registered after the alarm. */
    public static final class Hazard extends AbstractObjectInstance implements TouchResponseProvider {
        public Hazard(ObjectSpawn spawn) { super(spawn, "east-test-hazard"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public int getCollisionFlags() { return 0x80 | 0x17; }
        @Override public int getCollisionProperty() { return 0; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class Rig {
        final HeadlessTestFixture fixture;
        final ObjectManager manager;
        final Sonic sonic;
        final TestObjectServices services;

        Rig() throws Exception {
            fixture = HeadlessTestFixture.builder().withZoneAndAct(6, 0).withSkippedZoneIntro().build();
            sonic = new Sonic("sonic", (short) X, (short) Y);
            var camera = mock(Camera.class);
            when(camera.getX()).thenReturn((short) 0);
            when(camera.getY()).thenReturn((short) 0);
            when(camera.getWidth()).thenReturn((short) 900);
            when(camera.getHeight()).thenReturn((short) 500);
            when(camera.getFocusedSprite()).thenReturn(sonic);
            var module = new Sonic3kGameModule();
            var holder = new ObjectManager[1];
            services = new TestObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
            }.withCamera(camera).withDebugOverlay(mock(DebugOverlayManager.class))
                    .withGameState(new GameStateManager()).withGameModule(module)
                    .withLevelManager(GameServices.level()).withSidekicks(List.of());
            var table = new TouchResponseTable(
                    com.openggf.data.RomByteReader.fromRom(TestEnvironment.currentRom()),
                    Sonic3kConstants.TOUCH_SIZES_ADDR, Sonic3kConstants.TOUCH_SIZES_COUNT);
            manager = new ObjectManager(List.of(), module.createObjectRegistry(), 0, null, table,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = manager;
            services.withDirectObjectManager(manager);
            AbstractObjectInstance.updateCameraBounds(0, 0, 900, 500, 0);
            NativePositionOps.writeXPosPreserveSubpixel(sonic, X);
            NativePositionOps.writeYPosPreserveSubpixel(sonic, Y);
        }

        /** Adds alarm first, hazard after, optional live escort; returns whether the hazard hurt Sonic. */
        boolean hazardHurts(LbzAlarmRecipe recipe, boolean escortLive, boolean clearEscortFirst) {
            var alarm = new LbzAlarmObjectInstance(
                    new ObjectSpawn(X, Y, Sonic3kObjectIds.LBZ_ALARM, 0, 0, false, Y), recipe);
            manager.addDynamicObject(alarm);
            var hazard = new Hazard(new ObjectSpawn(X, Y, 0x7F, 0, 0, false, Y));
            manager.addDynamicObject(hazard);
            Flybot767BadnikInstance escort = null;
            if (escortLive) {
                escort = new Flybot767BadnikInstance(
                        new ObjectSpawn(X + 0x120, Y - 0x60, Sonic3kObjectIds.FLYBOT_767, 0, 0, false, Y)).asBoundedEscort();
                manager.addDynamicObject(escort);
            }
            if (clearEscortFirst && escort != null) {
                manager.removeDynamicObject(escort);
            }
            sonic.setRingCount(5);
            manager.update(0, sonic, List.of(), 1, false);
            manager.snapshotTouchResponseState();
            manager.runTouchResponsesForPlayer(sonic, 2);
            return sonic.isHurt() || sonic.getInvulnerable();
        }
    }

    @Test void stockAlarmEarlierInTheListShadowsTheHazardControl() throws Exception {
        var rig = new Rig();
        assertFalse(rig.hazardHurts(LbzAlarmRecipe.STOCK, false, false),
                "control: a published STOCK alarm region ends the player's touch pass first");
    }

    @Test void boundedAlarmWithoutALiveEscortBehavesLikeStock() throws Exception {
        var rig = new Rig();
        assertFalse(rig.hazardHurts(LbzAlarmRecipe.BOUNDED, false, false));
    }

    @Test void blockedBoundedAlarmPublishesNothingSoTheLaterHazardIsReached() throws Exception {
        var rig = new Rig();
        assertTrue(rig.hazardHurts(LbzAlarmRecipe.BOUNDED, true, false),
                "a blocked BOUNDED alarm must not shadow later objects in the touch pass");
    }

    @Test void clearedEscortRearmsTheAlarmAndRestoresTheStockOrdering() throws Exception {
        var rig = new Rig();
        assertFalse(rig.hazardHurts(LbzAlarmRecipe.BOUNDED, true, true),
                "once the escort is gone the alarm publishes its region again");
    }

    @Test void blockedAlarmFlagsAreZeroOnlyForBoundedAndOnlyWhileAnEscortIsLive() throws Exception {
        var rig = new Rig();
        var bounded = new LbzAlarmObjectInstance(
                new ObjectSpawn(X, Y, Sonic3kObjectIds.LBZ_ALARM, 0, 0, false, Y), LbzAlarmRecipe.BOUNDED);
        var stock = new LbzAlarmObjectInstance(
                new ObjectSpawn(X + 8, Y, Sonic3kObjectIds.LBZ_ALARM, 0, 0, false, Y), LbzAlarmRecipe.STOCK);
        rig.manager.addDynamicObject(bounded);
        rig.manager.addDynamicObject(stock);
        assertEquals(0xD7, bounded.getCollisionFlags());
        var escort = new Flybot767BadnikInstance(
                new ObjectSpawn(X + 0x120, Y - 0x60, Sonic3kObjectIds.FLYBOT_767, 0, 0, false, Y)).asBoundedEscort();
        rig.manager.addDynamicObject(escort);
        assertEquals(0, bounded.getCollisionFlags());
        assertEquals(0xD7, stock.getCollisionFlags(), "STOCK is never blocked");
        rig.manager.removeDynamicObject(escort);
        assertEquals(0xD7, bounded.getCollisionFlags());
    }
}
