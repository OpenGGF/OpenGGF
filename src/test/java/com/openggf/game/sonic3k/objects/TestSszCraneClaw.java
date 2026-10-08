package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszCraneClaw {
    public static final class Ship extends AbstractObjectInstance implements SpawnRewindRecreatable, SszCranePose {
        public Ship(ObjectSpawn spawn) { super(spawn, "TestSszCraneShip"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public boolean craneFlipped() { return false; }
        @Override public boolean isPersistent() { return true; }
    }

    private HeadlessTestFixture fixture;

    private SszCraneClaw create() {
        fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1).build();
        fixture.sprite().setCentreX((short) 0x100);
        fixture.sprite().setCentreY((short) 0x460);
        var ship = new Ship(new ObjectSpawn(0x100, 0x400, 0, 0, 0, false, 0));
        var claw = new SszCraneClaw(new ObjectSpawn(0x100, 0x423, 0, 0, 0, false, 0), ship);
        var manager = GameServices.level().getObjectManager();
        ship.setServices(TestEnvironment.objectServices());
        claw.setServices(TestEnvironment.objectServices());
        manager.addDynamicObject(ship);
        manager.addDynamicObject(claw);
        claw.update(0, null);
        state().setCutsceneFlag(0);
        return claw;
    }

    private SszZoneRuntimeState state() { return (SszZoneRuntimeState) GameServices.zoneRuntimeState(); }

    @Test void lowersByOneAndGrabsBeforeRaisingAndCarryingOnTheFollowingUpdate() {
        var claw = create();
        assertEquals(2, GameServices.level().getObjectManager().activeObjectsOfType(SszCraneClawPart.class).size());
        assertEquals(0, claw.getPriorityBucket());
        for (int update = 1; update <= 45; update++) {
            claw.update(update, null);
            assertEquals(0x423 + update, claw.getY());
            assertFalse(state().cutsceneFlag(2));
        }
        claw.update(46, null);
        assertTrue(state().cutsceneFlag(1));
        assertTrue(state().cutsceneFlag(2));
        assertEquals(0x460, fixture.sprite().getCentreY(), "grab does not yet write carried position");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int update = 0; update < 45; update++) claw.update(update, null);
        assertFalse(state().cutsceneFlag(3), "zero height still owes the underflow update");
        claw.update(46, null);
        assertTrue(state().cutsceneFlag(3));
        assertEquals(0x423, claw.getY());
        assertEquals(0x439, fixture.sprite().getCentreY());
        registry.restore(saved);
        var restored = GameServices.level().getObjectManager().activeObjectsOfType(SszCraneClaw.class).getFirst();
        for (int update = 0; update < 46; update++) restored.update(update, null);
        assertTrue(state().cutsceneFlag(3));
        assertEquals(0x423, restored.getY(), "recreated parent link supplies the original ship position");
        var player = TestEnvironment.objectServices().spriteManager().getMainPlayable();
        assertEquals(0x439, player.getCentreY());
        state().setCutsceneFlag(5);
        player.setCentreY((short) 0x480);
        restored.update(47, null);
        assertEquals(0x480, player.getCentreY(), "release flag ends crane position ownership");
    }

    @Test void rightGrabBoundaryIsExclusiveAndLeftBoundaryIsInclusive() {
        var claw = create();
        fixture.sprite().setCentreX((short) 0x10C);
        for (int update = 0; update < 47; update++) claw.update(update, null);
        assertTrue(state().cutsceneFlag(1));
        assertFalse(state().cutsceneFlag(2));
        fixture.sprite().setCentreX((short) 0xF4);
        claw.update(48, null);
        assertTrue(state().cutsceneFlag(2));
        assertEquals(2, claw.mappingFrame());
    }
}
