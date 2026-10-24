package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.objects.Sonic1AnimalsObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1WaterfallSoundObjectInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BallHogBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1BombBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1CrabmeatBadnikInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1MotobugBadnikInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance;
import com.openggf.game.sonic3k.objects.badniks.ToxomisterBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.FirewormBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.FirewormHeadInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesAiz2Instance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesHcz2Instance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.objects.boss.BossStateContext;
import com.openggf.level.objects.ObjectVisualFacing.Basis;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Function;

import static com.openggf.level.objects.ObjectVisualFacing.Basis.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class TestObjectVisualFacing {
    @Test void dayOffMotobugUsesLogicalFacingRatherThanRawUnflippedArt() throws Exception {
        // Day Off used flipX=false while moving right. Native Moto_Main starts
        // facing left with these same source flags; a director must mirror it.
        var moto = new Sonic1MotobugBadnikInstance(spawn(0));
        assertEquals(LEFT, ObjectVisualFacing.resolve(moto));
        assertEquals(RIGHT, ObjectVisualFacing.resolve(new Sonic1MotobugBadnikInstance(spawn(1))));

        field(AbstractBadnikInstance.class, "xVelocity").setInt(moto, 0x100);
        field(AbstractBadnikInstance.class, "animFrame").setInt(moto, 5);
        assertEquals(LEFT, ObjectVisualFacing.resolve(moto), "Motion and selected pose do not normalize art facing");
        field(AbstractBadnikInstance.class, "facingLeft").setBoolean(moto, false);
        assertEquals(RIGHT, ObjectVisualFacing.resolve(moto), "Live direction changes must supersede placement flags");

        // Crabmeat's misleading legacy field is also its draw flip. Its walk
        // routine chooses POSITIVE velocity when facingLeft is true.
        assertEquals(RIGHT, ObjectVisualFacing.resolve(new Sonic1CrabmeatBadnikInstance(spawn(0))));
        assertEquals(LEFT, ObjectVisualFacing.resolve(new Sonic1CrabmeatBadnikInstance(spawn(1))));
    }

    @Test void independentOwnersDoNotDependOnTheSharedBadnikBase() {
        assertEquals(RIGHT, ObjectVisualFacing.resolve(new Sonic1BallHogBadnikInstance(spawn(0))));
        assertEquals(LEFT, ObjectVisualFacing.resolve(new Sonic1BallHogBadnikInstance(spawn(1))));
        assertEquals(RIGHT, ObjectVisualFacing.resolve(new Sonic1BombBadnikInstance(spawn(0))));
        assertEquals(LEFT, ObjectVisualFacing.resolve(new Sonic1BombBadnikInstance(spawn(1))));
        assertEquals(LEFT, ObjectVisualFacing.resolve(new ToxomisterBadnikInstance(spawn(0))));
        assertEquals(RIGHT, ObjectVisualFacing.resolve(new ToxomisterBadnikInstance(spawn(1))));
    }

    @Test void animalZeroAndIndependentS1FlipFollowTheirActualNativeOwners() throws Exception {
        var animal = new AnimalObjectInstance(spawn(0), mock(ObjectServices.class));
        Field velocity = field(AnimalObjectInstance.class, "xVelocity");
        for (int value : new int[]{-0x100, 0, 0x100}) {
            velocity.setInt(animal, value);
            assertEquals(value < 0 ? LEFT : RIGHT, ObjectVisualFacing.resolve(animal));
        }
        var s1 = new Sonic1AnimalsObjectInstance(spawn(0));
        field(Sonic1AnimalsObjectInstance.class, "xVelocity").setInt(s1, -0x100);
        Field flip = field(Sonic1AnimalsObjectInstance.class, "hFlip");
        flip.setBoolean(s1, false);
        assertEquals(RIGHT, ObjectVisualFacing.resolve(s1), "Ending animals can look against their velocity");
        flip.setBoolean(s1, true);
        assertEquals(LEFT, ObjectVisualFacing.resolve(s1));
    }

    @Test void bossSpecificDirectionDoesNotAssumeBossStateRenderFlags() throws Exception {
        var boss = mock(Sonic2WFZBossInstance.class, CALLS_REAL_METHODS);
        var state = new BossStateContext(0, 0, 8);
        state.renderFlags = 1;
        field(AbstractBossInstance.class, "state").set(boss, state);
        field(Sonic2WFZBossInstance.class, "facingLeft").setBoolean(boss, true);
        assertEquals(LEFT, ObjectVisualFacing.resolve(boss));
        state.renderFlags = 0;
        field(Sonic2WFZBossInstance.class, "facingLeft").setBoolean(boss, false);
        assertEquals(RIGHT, ObjectVisualFacing.resolve(boss));
    }

    @Test void cutsceneKnucklesLegacyFieldNamesDoNotInvertNativeLogicalDirection() throws Exception {
        var aiz = new CutsceneKnucklesAiz2Instance(spawn(0));
        var hcz = new CutsceneKnucklesHcz2Instance(spawn(0));
        for (ObjectInstance actor : List.of(aiz, hcz)) {
            Field flip = field(actor.getClass(), "facingRight");
            // HCZ's native walk is currentX -= WALK_SPEED with this bit SET.
            // LBZ2 loc_62932 independently documents bit CLEAR = face RIGHT.
            flip.setBoolean(actor, true);
            assertEquals(LEFT, ObjectVisualFacing.resolve(actor));
            flip.setBoolean(actor, false);
            assertEquals(RIGHT, ObjectVisualFacing.resolve(actor));
        }
    }

    @Test void firewormContainerDoesNotHideItsIndependentlyFacingHead() throws Exception {
        var spawner = new FirewormBadnikInstance(spawn(1));
        var head = new FirewormHeadInstance(spawn(0));
        assertInstanceOf(ObjectVisualFacing.Container.class, spawner);
        assertEquals(NON_DIRECTIONAL, ObjectVisualFacing.resolve(spawner));
        assertEquals(LEFT, ObjectVisualFacing.resolve(head));
        field(AbstractBadnikInstance.class, "facingLeft").setBoolean(head, false);
        assertEquals(RIGHT, ObjectVisualFacing.resolve(head));
        field(FirewormBadnikInstance.class, "spawned").setBoolean(spawner, true);
        assertEquals(NON_DIRECTIONAL, ObjectVisualFacing.resolve(spawner),
                "The invisible container never acquires its independently positioned head's body direction");
    }

    @Test void freeConstructorMappingsAreOwnedIndividuallyAndPreserveOtherSourceBits() {
        List<Function<ObjectSpawn, ObjectInstance>> constructors = List.of(
                Sonic1MotobugBadnikInstance::new, Sonic1BallHogBadnikInstance::new,
                Sonic1BombBadnikInstance::new, ToxomisterBadnikInstance::new);
        for (var constructor : constructors) {
            for (int sourceFlags : new int[]{0, 1, 2, 3}) {
                var source = constructor.apply(spawn(sourceFlags));
                for (Basis requested : java.util.List.of(Basis.LEFT, Basis.RIGHT)) {
                    int flags = ObjectVisualFacing.initialPlacementFlags(source, requested, sourceFlags);
                    assertEquals(sourceFlags & ~1, flags & ~1);
                    assertEquals(requested == Basis.LEFT ? LEFT : RIGHT,
                            ObjectVisualFacing.resolve(constructor.apply(spawn(flags))));
                }
            }
        }
        assertEquals(2, ObjectVisualFacing.initialPlacementFlags(
                new Sonic1MotobugBadnikInstance(spawn(0)), Basis.LEFT, 2));
        assertEquals(3, ObjectVisualFacing.initialPlacementFlags(
                new Sonic1BallHogBadnikInstance(spawn(0)), Basis.LEFT, 2));
    }

    @Test void unknownIsAnActionableErrorAndNeutralRequiresAnExplicitNativeDeclaration() {
        for (int flags : new int[]{0, 1}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> ObjectVisualFacing.resolve(new UndeclaredObject(spawn(flags))));
            assertTrue(error.getMessage().contains(UndeclaredObject.class.getName()));
            assertTrue(error.getMessage().contains("ObjectVisualFacing.Source.nativeFacing()"));
        }
        assertFalse(ObjectVisualFacing.Source.class.isAssignableFrom(AbstractBadnikInstance.class));
        assertFalse(ObjectVisualFacing.Source.class.isAssignableFrom(AbstractBossInstance.class));
        var sound = new Sonic1WaterfallSoundObjectInstance(spawn(1));
        assertEquals(NON_DIRECTIONAL, ObjectVisualFacing.resolve(sound));
        assertEquals(3, ObjectVisualFacing.initialPlacementFlags(sound, Basis.LEFT, 3));
        assertEquals(3, ObjectVisualFacing.initialPlacementFlags(sound, Basis.RIGHT, 3));

        var error = assertThrows(IllegalArgumentException.class, () -> ObjectVisualFacing.initialPlacementFlags(
                new Sonic1CrabmeatBadnikInstance(spawn(0)), Basis.RIGHT, 0));
        assertTrue(error.getMessage().contains("ObjectVisualFacing.PlacementSource.initialPlacementFlags"));
    }

    private static ObjectSpawn spawn(int flags) { return new ObjectSpawn(100, 200, 0x40, 0, flags, false, 0); }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class UndeclaredObject extends AbstractObjectInstance {
        UndeclaredObject(ObjectSpawn spawn) { super(spawn, "Undeclared"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
