package com.openggf.level.objects;

import com.openggf.game.*;
import com.openggf.game.internal.DyingSolidPushPolicy;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.graphics.GLCommand;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
class TestRetailDyingSolidPush {
    @Test void revisionZeroDyingPassClearsItsRealObjectPushLatchAndReplaysAfterRestore() {
        for (boolean rev00 : new boolean[] {false, true}) {
            var f = new Fixture(rev00);
            f.push();
            f.player.setDead(true);
            f.player.setAir(true);
            f.player.setPushing(false); // KillCharacter clears only the player's bit.
            f.player.setAnimationId(0x18);
            var actor = f.player.captureRewindState();
            var contacts = f.manager.solidContacts().captureRewindState();
            f.step(1);
            assertEquals(!rev00, f.manager.solidContacts().hasPushingLatch(f.player));
            assertEquals(rev00 ? 0 : 0x18, f.player.getAnimationId());
            if (rev00) assertEquals(1, f.player.getAnimationManager().captureRewindState().lastAnimationId());
            assertTrue(f.player.getDead());
            var forward = f.manager.solidContacts().captureRewindState();
            f.player.restoreRewindState(actor);
            f.manager.solidContacts().restoreRewindState(contacts);
            f.step(1);
            assertEquals(forward, f.manager.solidContacts().captureRewindState());
            assertEquals(rev00 ? 0 : 0x18, f.player.getAnimationId());
        }
    }

    @Test void monitorTopOnlyAndSuppressedNativeOwnersKeepTheirOwnDeadPassRules() {
        for (int kind = 1; kind <= 3; kind++) {
            var f = new Fixture(true);
            f.push();
            f.solid.kind = kind;
            f.player.setDead(true);
            f.player.setAir(true);
            f.player.setAnimationId(0x18);
            f.step(1);
            assertTrue(f.manager.solidContacts().hasPushingLatch(f.player));
            assertEquals(0x18, f.player.getAnimationId());
        }
    }

    private static final class Fixture {
        final Solid solid = new Solid();
        final ObjectManager manager;
        final Actor player;
        Fixture(boolean rev00) {
            var module = new Sonic1GameModule() {
                @Override public <T> T getGameService(Class<T> type) {
                    return type == DyingSolidPushPolicy.class
                            ? (rev00 ? type.cast((DyingSolidPushPolicy) () -> true) : null)
                            : super.getGameService(type);
                }
            };
            TestEnvironment.configureGameModuleFixture(module);
            var services = new StubObjectServices() {
                @Override public GameModule gameModule() { return module; }
            };
            var registry = new ObjectRegistry() {
                public ObjectInstance create(ObjectSpawn spawn) { return solid; }
                public void reportCoverage(List<ObjectSpawn> spawns) { }
                public String getPrimaryName(int id) { return "full solid"; }
            };
            manager = new ObjectManager(List.of(), registry, 0, null, null, null, GameServices.camera(), services);
            manager.reset(0);
            manager.addDynamicObject(solid);
            player = new Actor();
            player.setWidth(20); player.setHeight(20); player.setAir(false);
            player.setXSpeed((short) 0x100);
            player.setCentreX((short) 85); player.setCentreY((short) 81);
        }
        void push() {
            step(0);
            assertTrue(player.getPushing());
            assertTrue(manager.solidContacts().hasPushingLatch(player));
            assertTrue(solid.isWithinSolidContactBounds());
        }
        void step(int frame) { manager.update(0, player, List.of(), frame, false, true, false); }
    }

    private static final class Actor extends AbstractPlayableSprite {
        Actor() { super("sonic", (short) 0, (short) 0); }
        @Override protected void defineSpeeds() { }
        @Override protected void createSensorLines() {
            groundSensors = new Sensor[0]; ceilingSensors = new Sensor[0]; pushSensors = new Sensor[0];
        }
        @Override public void draw() { }
    }

    private static final class Solid extends AbstractObjectInstance implements SolidObjectProvider {
        int kind;
        Solid() { super(new ObjectSpawn(100, 100, 1, 0, 0, false, 0), "full solid"); }
        @Override public boolean isPersistent() { return true; }
        @Override public boolean isTopSolidOnly() { return kind == 1; }
        @Override public boolean hasMonitorSolidity() { return kind == 2; }
        @Override public boolean defersAirborneRiderUnseatThisFrame(PlayableEntity player) { return kind == 3; }
        @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(16, 8, 8); }
        @Override public void update(int frame, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
