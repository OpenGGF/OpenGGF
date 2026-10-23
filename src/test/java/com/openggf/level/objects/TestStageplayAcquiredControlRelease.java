package com.openggf.level.objects;

import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStageplayAcquiredControlRelease {
    @ParameterizedTest @ValueSource(strings = {
            "com.openggf.game.sonic1.objects.Sonic1EggPrisonObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1RingFlashObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1SignpostObjectInstance",
            "com.openggf.game.sonic1.objects.bosses.Sonic1FZBossInstance",
            "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance",
            "com.openggf.game.sonic3k.ending.EndingKnucklesFlightObjectInstance",
            "com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance",
            "com.openggf.game.sonic3k.objects.DdzEndBossObjectInstance",
            "com.openggf.game.sonic3k.objects.DdzFlightControllerObjectInstance",
            "com.openggf.game.sonic3k.objects.DezFinalBossController",
            "com.openggf.game.sonic3k.objects.DezMinibossTransport",
            "com.openggf.game.sonic3k.objects.DezMinibossTransport$P2Hold",
            "com.openggf.game.sonic3k.objects.FbzEndBossInstance",
            "com.openggf.game.sonic3k.objects.FbzEndEggCapsuleInstance",
            "com.openggf.game.sonic3k.objects.IczFreezerObjectInstance$CaptureCloud",
            "com.openggf.game.sonic3k.objects.Lbz1GroundLaunchIntroInstance",
            "com.openggf.game.sonic3k.objects.Lbz2RobotnikShipInstance",
            "com.openggf.game.sonic3k.objects.LrzAutoscrollObjectInstance",
            "com.openggf.game.sonic3k.objects.LevelIntroPlayerRunInstance",
            "com.openggf.game.sonic3k.objects.LrzBoulderCutsceneObjectInstance",
            "com.openggf.game.sonic3k.objects.LrzCutsceneBoulderObjectInstance",
            "com.openggf.game.sonic3k.objects.MgzEndBossKnuxInstance",
            "com.openggf.game.sonic3k.objects.PachinkoEnergyTrapObjectInstance",
            "com.openggf.game.sonic3k.objects.S3kSignpostInstance",
            "com.openggf.game.sonic3k.objects.S3kSlotBonusCageObjectInstance",
            "com.openggf.game.sonic3k.objects.SozEndBossChild",
            "com.openggf.game.sonic3k.objects.SozEndBossInstance",
            "com.openggf.game.sonic3k.objects.SozFallingIntroInstance",
            "com.openggf.game.sonic3k.objects.SozMinibossChild$Alignment",
            "com.openggf.game.sonic3k.objects.SozMinibossChild$P2Hold",
            "com.openggf.game.sonic3k.objects.bosses.CnzEndBossInstance",
            "com.openggf.game.sonic3k.objects.bosses.HczEndBossGeyserCutscene",
            "com.openggf.game.sonic3k.objects.bosses.IczEndBossInstance",
            "com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss1Instance",
            "com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance",
            "com.openggf.game.sonic3k.objects.bosses.LbzFinalBoss2Instance$GrabOwnerChild",
            "com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance",
            "com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance$MhzEndBossPlayerTwoCarryChild",
            "com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance$MhzEndBossSidekickLockChild",
            "com.openggf.game.sonic3k.objects.bosses.MhzEndBossInstance$MhzEndBossWalkoffPrepChild",
            "com.openggf.game.sonic3k.objects.bosses.SszMechaSonicActEndObjectInstance",
            "com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance"
    })
    void acquiredIdentityReleasesOnceAcrossRewindWithoutAdvancingTheNativeOwner(String name) throws Exception {
        var owner = mock(AbstractPlayableSprite.class);
        var unrelated = mock(AbstractPlayableSprite.class);
        var services = mock(ObjectServices.class, RETURNS_DEEP_STUBS);
        var object = create(name, services);
        object.setServices(services);
        clearInvocations(owner, unrelated);
        object.releasePlayersForVisualDirection();
        verifyNoInteractions(owner, unrelated);
        var field = object.getClass().getDeclaredField("visualControlOwners");
        field.setAccessible(true);
        var ownership = (PlayerControlOwners) field.get(object);
        ownership.acquire(owner);
        var identities = new RewindIdentityTable();
        identities.registerPlayer(owner, PlayerRefId.mainPlayer());
        int objectId = 0;
        for (Class<?> type = object.getClass(); type != AbstractObjectInstance.class; type = type.getSuperclass()) {
            for (var reference : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(reference.getModifiers())) continue;
                reference.setAccessible(true);
                if (reference.get(object) instanceof ObjectInstance linked)
                    identities.registerObject(linked,
                            com.openggf.game.rewind.identity.ObjectRefId.dynamic(objectId++, 0, objectId));
            }
        }
        var context = RewindCaptureContext.withIdentityTable(identities);
        var snapshot = object.captureRewindState(context);
        var nativeState = scalarState(object);
        object.releasePlayersForVisualDirection();
        assertEquals(nativeState, scalarState(object), "Release cannot reset native routine progress");
        assertFalse(mockingDetails(owner).getInvocations().isEmpty(), name);
        verify(owner, never()).setXSpeed(anyShort());
        verify(owner, never()).setYSpeed(anyShort());
        verifyNoInteractions(unrelated);
        assertTrue(ownership.isEmpty());
        clearInvocations(owner);
        object.releasePlayersForVisualDirection();
        verifyNoInteractions(owner);
        object.restoreRewindState(snapshot, context);
        assertEquals(List.of(owner), ownership.players(), "Rewind must restore the exact acquired identity");
        object.releasePlayersForVisualDirection();
        assertFalse(mockingDetails(owner).getInvocations().isEmpty(), name);
    }

    private static java.util.Map<java.lang.reflect.Field, Object> scalarState(Object target) throws Exception {
        var state = new java.util.HashMap<java.lang.reflect.Field, Object>();
        for (Class<?> type = target.getClass(); type != AbstractObjectInstance.class; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType().isPrimitive() || field.getType().isEnum()) {
                    field.setAccessible(true); state.put(field, field.get(target));
                }
            }
        }
        return state;
    }

    private static AbstractObjectInstance create(String name, ObjectServices services) throws Exception {
        var type = Class.forName(name);
        Constructor<?> constructor = Arrays.stream(type.getDeclaredConstructors())
                .min(Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        constructor.setAccessible(true);
        var args = Arrays.stream(constructor.getParameterTypes()).map(parameter -> {
            if (parameter == ObjectSpawn.class) return new ObjectSpawn(100, 100, 1, 0, 0, false, 100);
            if (parameter == int.class) return 0;
            if (parameter == short.class) return (short) 0;
            if (parameter == boolean.class) return false;
            return mock(parameter, RETURNS_DEEP_STUBS);
        }).toArray();
        return ObjectConstructionContext.with(services, -1, () -> {
            try { return (AbstractObjectInstance) constructor.newInstance(args); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(name, failure); }
        });
    }
}
