package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.NativeCompetitionRuntimeFixture;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestSonic2TeleportObjectFieldSwap {
    private NativeCompetitionRuntimeFixture world;

    @BeforeEach void setUp() {
        TestEnvironment.resetAll();
        world = new NativeCompetitionRuntimeFixture();
    }

    @Test void pointPokeySwapsOnlyThirtyAndThirtyFourLongwords() throws Exception {
        var cage = new PointPokeyObjectInstance(spawn(0xD6), "Cage");
        seedScalars(cage);
        set(cage, "playerState", 1); set(cage, "secondPlayerState", 4);
        set(cage, "countdown", 0x1234); set(cage, "secondCountdown", 0x5678);
        set(cage, "playerOccupied", true); set(cage, "secondOccupied", false);
        set(cage, "capturedPlayerUsesRideState", false); set(cage, "secondUsesRideState", true);
        set(cage, "prizeOwnerNativeSlot", 1);
        ((int[]) get(cage, "activePrizeCount"))[0] = 0x1234;
        var before = snapshot(cage);
        var expected = exchanged(before, "playerState", "secondPlayerState", "countdown", "secondCountdown");
        cage.swapPlayerOwnership();
        assertEquals(expected, snapshot(cage),
                "swap_loop_objects exchanges $30/$34 only; $3E, standing/ride latches and payout words stay put");
        cage.swapPlayerOwnership();
        assertEquals(before, snapshot(cage));
    }

    @Test void forcedSpinSwapsOnlyThirtyFourAndThirtyFiveBytes() throws Exception {
        var spin = new ForcedSpinObjectInstance(spawn(0x84), "Pinball");
        seedScalars(spin);
        set(spin, "sonicPastTrigger", true); set(spin, "tailsPastTrigger", false);
        set(spin, "sonicStateOwner", world.p1); set(spin, "tailsStateOwner", world.p2);
        var before = snapshot(spin);
        spin.swapPlayerOwnership();
        assertEquals(exchanged(before, "sonicPastTrigger", "tailsPastTrigger"), snapshot(spin));
        spin.swapPlayerOwnership();
        assertEquals(before, snapshot(spin));
    }

    @Test void launcherSwapsOnlyThirtySixAndThirtySevenStateBytes() throws Exception {
        var spring = spring();
        seedScalars(spring);
        Map<Object, Object> states = states(spring);
        Object first = state(1, 3), second = state(2, 5), extension = state(1, 7);
        var extra = world.ordinaryCpu();
        states.put(world.p1, first); states.put(world.p2, second); states.put(extra, extension);
        var before = snapshot(spring);
        var expected = new LinkedHashMap<>(before);
        expected.put(key(LauncherSpringObjectInstance.class, "playerStates"),
                Map.of(world.p1, List.of(2, 3), world.p2, List.of(1, 5), extra, List.of(1, 7)));
        spring.swapPlayerOwnership();
        assertSame(first, states.get(world.p1), "actor-local cooldown owner is retained");
        assertSame(second, states.get(world.p2));
        assertSame(extension, states.get(extra));
        assertEquals(expected, snapshot(spring), "only the two native state bytes move, not cooldowns or shared compression");
        spring.swapPlayerOwnership();
        assertEquals(before, snapshot(spring));
    }

    @Test void launcherMissingBankMeansZeroStateWithoutMovingTheExistingCooldown() throws Exception {
        var spring = spring();
        var states = states(spring);
        var first = state(2, 9);
        states.put(world.p1, first);
        spring.swapPlayerOwnership();
        assertSame(first, states.get(world.p1));
        assertEquals(0, get(first, "state"));
        assertEquals(9, get(first, "launchCooldown"));
        assertEquals(2, get(states.get(world.p2), "state"));
        assertEquals(0, get(states.get(world.p2), "launchCooldown"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0xDC, 0xD3})
    void prizeActorPointersAreNotPartOfTheObjectSwapLoop(int id) throws Exception {
        AbstractObjectInstance prize = id == 0xDC
                ? new RingPrizeObjectInstance(256, 256, 256, 256, 1, new int[]{3})
                : new BombPrizeObjectInstance(256, 256, 256, 256, 1, new int[]{4});
        set(prize, "recipientNativeSlot", 1); set(prize, "parentObjectSlot", 0x60);
        var before = snapshot(prize);
        if (prize instanceof RingPrizeObjectInstance value) value.swapPlayerOwnership();
        else ((BombPrizeObjectInstance) prize).swapPlayerOwnership();
        assertEquals(before, snapshot(prize), "the ROM loop never rewrites ObjDC/ObjD3 actor or counter pointers");
    }

    private LauncherSpringObjectInstance spring() {
        var spring = new LauncherSpringObjectInstance(spawn(0x85), "Pressure");
        spring.setServices(new TestObjectServices().withWorldSession(world.world));
        return spring;
    }

    private static ObjectSpawn spawn(int id) { return new ObjectSpawn(256, 256, id, 1, 0, false, 0); }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> states(Object spring) throws Exception {
        return (Map<Object, Object>) get(spring, "playerStates");
    }

    private static Object state(int value, int cooldown) throws Exception {
        var type = Class.forName(LauncherSpringObjectInstance.class.getName() + "$PlayerState");
        var ctor = type.getDeclaredConstructor(); ctor.setAccessible(true);
        var state = ctor.newInstance(); set(state, "state", value); set(state, "launchCooldown", cooldown);
        return state;
    }

    private static void seedScalars(Object object) throws Exception {
        int value = 0x40;
        for (var field : object.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
            field.setAccessible(true);
            if (field.getType() == int.class) field.setInt(object, value++);
            if (field.getType() == boolean.class) field.setBoolean(object, (value++ & 1) != 0);
        }
    }

    private static Map<String, Object> exchanged(Map<String, Object> before, String... pairs) {
        var result = new LinkedHashMap<>(before);
        for (int i = 0; i < pairs.length; i += 2) {
            String firstName = pairs[i], secondName = pairs[i + 1];
            String first = before.keySet().stream().filter(k -> k.endsWith("#" + firstName)).findFirst().orElseThrow();
            String second = before.keySet().stream().filter(k -> k.endsWith("#" + secondName)).findFirst().orElseThrow();
            result.put(first, before.get(second)); result.put(second, before.get(first));
        }
        return result;
    }

    private static Map<String, Object> snapshot(Object object) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Class<?> type = object.getClass(); type != Object.class; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(object);
                if (value instanceof int[] array) value = java.util.Arrays.stream(array).boxed().toList();
                else if (value instanceof Map<?, ?> map) {
                    var copy = new LinkedHashMap<Object, Object>();
                    for (var entry : map.entrySet()) {
                        Object element = entry.getValue();
                        if (element != null && element.getClass().getSimpleName().equals("PlayerState"))
                            element = List.of(get(element, "state"), get(element, "launchCooldown"));
                        copy.put(entry.getKey(), element);
                    }
                    value = copy;
                }
                result.put(key(type, field.getName()), value);
            }
        }
        return result;
    }

    private static String key(Class<?> type, String field) { return type.getName() + "#" + field; }
    private static Field field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void set(Object object, String name, Object value) throws Exception { field(object, name).set(object, value); }
    private static Object get(Object object, String name) throws Exception { return field(object, name).get(object); }
}
