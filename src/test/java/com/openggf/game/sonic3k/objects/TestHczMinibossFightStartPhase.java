package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HCZMiniboss_StartFight only installs Obj_HCZ_MinibossLoop; the loop's routine 0
 * (HCZMinibossLoop_Init) runs on the next dispatch and sets y_vel/$2E without
 * moving, so the first MoveSprite2 is one dispatch later
 * (docs/skdisasm/sonic3k.asm:139291-139299, 139324-139339).
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestHczMinibossFightStartPhase {
    private static final int ROUTINE_DESCEND = 6;

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void fightStartSpendsOneLoopInitDispatchBeforeTheDescentMoves() throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0)
                .build();
        ObjectManager objectManager = GameServices.level().getObjectManager();
        HczMinibossInstance boss = installBoss(objectManager);
        armStartFightCallback(boss);
        PlayableEntity player = GameServices.camera().getFocusedSprite();
        int startY = state(boss, "y");

        // StartFight callback pass: Obj_Wait expires and only swaps the code pointer.
        step(boss, 0, player);
        assertEquals(startY, state(boss, "y"), "StartFight does not move the boss");
        assertTrue(state(boss, "routine") != ROUTINE_DESCEND,
                "the descent routine is not installed until HCZMinibossLoop_Init runs");
        // Obj_Wait drew nothing this pass, so the next touch scan cannot hit the boss.
        assertEquals(0, boss.getCollisionFlags());
        assertEquals(null, boss.getMultiTouchRegions());

        RewindRegistry registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot initPending = registry.capture();
        List<String> forward = initAndDescend(boss, player, startY);

        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(initPending);
            HczMinibossInstance restored = findBoss(objectManager);
            assertEquals(forward, initAndDescend(restored, player, startY),
                    "restored init-pending boundary replays the same Init and first move");
        }
    }

    @Test
    void vortexPullCapturesOnTheFirstPassAfterTheWindupEnds() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0).build();
        ObjectManager objectManager = GameServices.level().getObjectManager();
        HczMinibossInstance boss = installBoss(objectManager);
        // loc_6A5BC switched the water effect to routine 8 at the end of the
        // previous pass; loc_6A5D8 runs sub_6A9B8 on its first routine-8 pass
        // (docs/skdisasm/sonic3k.asm:139846-139858).
        set(field(boss, "state"), "routine", 20);
        set(boss, "vortexActive", true);
        set(boss, "waterEffectRoutine", 8);
        com.openggf.sprites.playable.AbstractPlayableSprite player =
                (com.openggf.sprites.playable.AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
        int waterX = ((Number) findMethod(boss.getClass(), "getWaterEffectX").invoke(boss)).intValue();
        int waterY = ((Number) findMethod(boss.getClass(), "getWaterEffectY").invoke(boss)).intValue();
        player.setCentreX((short) waterX);
        player.setCentreY((short) waterY);

        step(boss, 0, player);

        assertTrue(player.isObjectControlled(), "the first routine-8 pass captures the player");
    }

    @Test
    void vortexEndReleasesCapturedPlayersOnTheSamePass() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0).build();
        ObjectManager objectManager = GameServices.level().getObjectManager();
        HczMinibossInstance boss = installBoss(objectManager);
        set(field(boss, "state"), "routine", 20);
        set(boss, "vortexActive", true);
        set(boss, "waterEffectRoutine", 8);
        com.openggf.sprites.playable.AbstractPlayableSprite player =
                (com.openggf.sprites.playable.AbstractPlayableSprite) GameServices.camera().getFocusedSprite();
        int waterX = ((Number) findMethod(boss.getClass(), "getWaterEffectX").invoke(boss)).intValue();
        int waterY = ((Number) findMethod(boss.getClass(), "getWaterEffectY").invoke(boss)).intValue();
        player.setCentreX((short) waterX);
        player.setCentreY((short) waterY);
        step(boss, 0, player);
        assertTrue(player.isObjectControlled(), "precondition: the pull captured the player");

        // The parent's Obj_Wait callback clears $38 bit 2; on the same pass the
        // water effect pulls once more, sees the bit clear and releases through
        // loc_6A5FA -> loc_6A986/sub_6A996 (docs/skdisasm/sonic3k.asm:139865-139871).
        set(boss, "waitTimer", 0);
        Class<?> callbackType = Class.forName(HczMinibossInstance.class.getName() + "$WaitCallback");
        set(boss, "waitCallback", enumConstant(callbackType, "END_VORTEX"));
        step(boss, 1, player);

        assertTrue(!player.isObjectControlled(), "the vortex end releases on its own pass");
        assertTrue(player.getAir());
    }

    private static List<String> initAndDescend(HczMinibossInstance boss, PlayableEntity player, int startY)
            throws Exception {
        List<String> trail = new ArrayList<>();
        // HCZMinibossLoop_Init: y_vel=$100, $2E=$DF, routine 2; no MoveSprite2.
        step(boss, 1, player);
        assertEquals(startY, state(boss, "y"), "HCZMinibossLoop_Init does not move");
        assertEquals(ROUTINE_DESCEND, state(boss, "routine"));
        assertEquals(0xDF, intField(boss, "waitTimer"));
        assertEquals(0x100, state(boss, "yVel"));
        assertTrue(boss.getCollisionFlags() != 0, "the Init pass reaches Draw_And_Touch_Sprite");
        trail.add(state(boss, "y") + ":" + intField(boss, "waitTimer"));
        // First HCZMinibossLoop_MoveAndWait pass.
        step(boss, 2, player);
        assertEquals(startY + 1, state(boss, "y"), "the first descent move is one dispatch after Init");
        assertEquals(0xDE, intField(boss, "waitTimer"));
        trail.add(state(boss, "y") + ":" + intField(boss, "waitTimer"));
        return trail;
    }

    private static void armStartFightCallback(HczMinibossInstance boss) throws Exception {
        Object st = field(boss, "state");
        set(st, "routine", 4);
        set(boss, "waitTimer", 0);
        Class<?> callbackType = Class.forName(HczMinibossInstance.class.getName() + "$WaitCallback");
        set(boss, "waitCallback", enumConstant(callbackType, "START_FIGHT"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static void step(HczMinibossInstance boss, int vInt, PlayableEntity player) throws Exception {
        Method m = findMethod(boss.getClass(), "updateBossLogic", int.class, PlayableEntity.class);
        m.invoke(boss, vInt, player);
    }

    private static HczMinibossInstance installBoss(ObjectManager objectManager) throws Exception {
        ObjectSpawn spawn = new ObjectSpawn(0x3720, 0x0580, Sonic3kObjectIds.HCZ_MINIBOSS, 0, 0, false, 0, 0x99);
        Field servicesField = ObjectManager.class.getDeclaredField("objectServices");
        servicesField.setAccessible(true);
        ObjectServices services = (ObjectServices) servicesField.get(objectManager);
        int slot = objectManager.allocateDynamicSlot();
        HczMinibossInstance boss = ObjectConstructionContext.with(services, slot, () -> new HczMinibossInstance(spawn));
        boss.setServices(services);
        Method register = ObjectManager.class.getDeclaredMethod(
                "registerActiveObject", ObjectSpawn.class, ObjectInstance.class);
        register.setAccessible(true);
        register.invoke(objectManager, spawn, boss);
        Field dirty = ObjectManager.class.getDeclaredField("activeObjectsCacheDirty");
        dirty.setAccessible(true);
        dirty.setBoolean(objectManager, true);
        return boss;
    }

    private static HczMinibossInstance findBoss(ObjectManager objectManager) {
        return objectManager.getActiveObjects().stream()
                .filter(HczMinibossInstance.class::isInstance)
                .map(HczMinibossInstance.class::cast)
                .findFirst().orElseThrow();
    }

    private static int state(HczMinibossInstance boss, String name) throws Exception {
        return ((Number) field(field(boss, "state"), name)).intValue();
    }

    private static int intField(Object target, String name) throws Exception {
        return ((Number) field(target, name)).intValue();
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) throws Exception {
        for (Class<?> t = type; t != null; t = t.getSuperclass()) {
            try {
                Method m = t.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    private static Field findField(Class<?> type, String name) throws Exception {
        for (Class<?> t = type; t != null; t = t.getSuperclass()) {
            try {
                Field f = t.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object field(Object target, String name) throws Exception {
        return findField(target.getClass(), name).get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        findField(target.getClass(), name).set(target, value);
    }
}
