package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.SubCpuCommand;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.WorldGlobals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Selected native AddPoints value entry, including arbitrary raw d0.l values unavailable to KillEnemy.
 * Literal vectors from AddPoints (Level/_Objects/HUD and Points/Main.asm:157-180), scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Expectations use no production arithmetic/helper.
 * Reflection is limited to the new entry/result so the pre-extraction baseline compiles and fails
 * as a JUnit assertion for the missing API, rather than failing test compilation.
 */
class TestCdSelectedAddPoints {
    private record Vector(String name, WorldGlobals before, int points, WorldGlobals expected,
                          Optional<SubCpuCommand> command) {
        @Override public String toString() { return name; }
    }

    private static Stream<Vector> literalVectors() {
        return Stream.of(
                new Vector("zero increment still marks HUD score",
                        new WorldGlobals(0xA55A, 3, 0x5A, 1234, 5000, 0xA5), 0,
                        new WorldGlobals(0xA55A, 3, 0x5A, 1234, 5000, 1), Optional.empty()),
                new Vector("threshold equality awards one life",
                        new WorldGlobals(0xA55A, 3, 0x5A, 4990, 5000, 0xA5), 10,
                        new WorldGlobals(0xA55A, 4, 0x5B, 5000, 10000, 1), yes()),
                new Vector("crossing cap compares capped score with threshold",
                        new WorldGlobals(0xA55A, 3, 0x5A, 999990, 1000000, 0xA5), 100,
                        new WorldGlobals(0xA55A, 3, 0x5A, 999999, 1000000, 1), Optional.empty()),
                new Vector("cap equality still reaches threshold equality",
                        new WorldGlobals(0xA55A, 3, 0x5A, 999999, 999999, 0xA5), 0,
                        new WorldGlobals(0xA55A, 4, 0x5B, 999999, 1004999, 1), yes()),
                new Vector("ADD.L wraps through zero before cap",
                        new WorldGlobals(0xA55A, 3, 0x5A, 0xFFFFFFF0, 5000, 0xA5), 0x20,
                        new WorldGlobals(0xA55A, 3, 0x5A, 16, 5000, 1), Optional.empty()),
                new Vector("raw sign bits can sum to zero",
                        new WorldGlobals(0xA55A, 3, 0x5A, 0x80000000, 5000, 0xA5), 0x80000000,
                        new WorldGlobals(0xA55A, 3, 0x5A, 0, 5000, 1), Optional.empty()),
                new Vector("selected entry accepts points above a WORD",
                        new WorldGlobals(0xA55A, 3, 0x5A, 0, 1000000, 0xA5), 0x10000,
                        new WorldGlobals(0xA55A, 3, 0x5A, 65536, 1000000, 1), Optional.empty()),
                new Vector("positive overflow is unsigned above cap",
                        new WorldGlobals(0xA55A, 3, 0x5A, 1, 1000000, 0xA5), 0x7FFFFFFF,
                        new WorldGlobals(0xA55A, 3, 0x5A, 999999, 1000000, 1), Optional.empty()),
                new Vector("negative raw increment is unsigned above cap",
                        new WorldGlobals(0xA55A, 3, 0x5A, 100, 1000000, 0xA5), -200,
                        new WorldGlobals(0xA55A, 3, 0x5A, 999999, 1000000, 1), Optional.empty()),
                new Vector("unsigned high threshold cannot reach wrapping increment",
                        new WorldGlobals(0xA55A, 3, 0x5A, 9000, 0xFFFFFFF0, 0xA5), 1000,
                        new WorldGlobals(0xA55A, 3, 0x5A, 10000, 0xFFFFFFF0, 1), Optional.empty()),
                new Vector("one award even when raised threshold remains reached",
                        new WorldGlobals(0xA55A, 3, 0x5A, 9000, 0, 0xA5), 1000,
                        new WorldGlobals(0xA55A, 4, 0x5B, 10000, 5000, 1), yes()),
                new Vector("lives and HUD lives wrap independently as bytes",
                        new WorldGlobals(0xA55A, 255, 255, 4990, 5000, 0xA5), 10,
                        new WorldGlobals(0xA55A, 0, 0, 5000, 10000, 1), yes()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("literalVectors")
    void selectedEntryPreservesTheFullWorldAndLiteralNativeOutcome(Vector vector) {
        var before = vector.before();
        var result = selected(before, vector.points());
        assertEquals(vector.expected(), result.world());
        assertEquals(vector.command(), result.command());
        assertEquals(result, selected(before, vector.points()), "The selected value entry is repeatable");
    }

    @Test void selectedEntryRejectsANullWorld() {
        assertThrows(NullPointerException.class, () -> selected(null, 0));
    }

    private static Optional<SubCpuCommand> yes() { return Optional.of(SubCpuCommand.SCMD_YESSFX); }
    private record Selected(WorldGlobals world, Optional<SubCpuCommand> command) { }

    private static Selected selected(WorldGlobals before, int rawPoints) {
        try {
            var entry = CdPlayerTouchResponse.class.getMethod("addPoints", WorldGlobals.class, int.class);
            Object result = entry.invoke(null, before, rawPoints);
            var world = assertInstanceOf(WorldGlobals.class, result.getClass().getMethod("world").invoke(result));
            var command = assertInstanceOf(Optional.class, result.getClass().getMethod("command").invoke(result));
            var typed = command.isEmpty() ? Optional.<SubCpuCommand>empty()
                    : Optional.of(assertInstanceOf(SubCpuCommand.class, command.orElseThrow()));
            return new Selected(world, typed);
        } catch (InvocationTargetException thrown) {
            if (thrown.getCause() instanceof RuntimeException cause) throw cause;
            if (thrown.getCause() instanceof Error cause) throw cause;
            return fail("Selected AddPoints threw a checked exception", thrown.getCause());
        } catch (ReflectiveOperationException missing) {
            return fail("Missing public selected AddPoints(WorldGlobals, int) value entry/result", missing);
        }
    }
}
