package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic2ObjectRegistry} (unioned with the shared codecs)
 * still exposes the dynamic rewind recreate codec for the batch-inner2 S2
 * inner-class hazard child that remains codec-backed.
 *
 * <p>These are {@code static} nested children of the DEZ Death Egg Robot and WFZ
 * boss, keyed by its JVM binary name ({@code Outer$Inner}). Its codec relinks
 * the live parent boss (recreated earlier in the restore loop, so present in
 * {@code getActiveObjects()}) and reflection-constructs the package-private nested
 * type; the in-flight differentiator scalars are already non-final, so the
 * generic field capturer reapplies them after recreate (no un-finaling required):
 * <ul>
 *   <li>{@code Sonic2DeathEggRobotInstance$BombChild} — fired bomb HURT hazard flying its
 *       own arc from an attack routine; not re-emitted by the body.</li>
 * </ul>
 *
 * <p><b>Intentionally absent:</b> {@code ArticulatedChild}, {@code HeadChild}, and
 * {@code JetChild} are construction-spawned (inside {@code initializeBossState() →
 * spawnChildren()}). Registering a codec for them would double their count on restore
 * (boss reconstruction re-spawns them, then the codec adds another copy). They are
 * re-established by boss reconstruction and must NOT have codecs.
 * See {@code TestBossChildNoDoubleSpawnParity} and {@code docs/KNOWN_DISCREPANCIES.md}.
 * The WFZ floating-platform, laser-wall, and platform-hurt children now restore
 * through graph-tested {@code RewindRecreatable} generic recreate.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip coverage is enforced by the rewind coverage
 * guard ({@code TestRewindCoverageGuard}).
 */
class TestRewindFixS2InnerBatch2Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = new Sonic2ObjectRegistry().dynamicRewindCodecs();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        for (DynamicObjectRewindCodec codec : ObjectRewindDynamicCodecs.sharedCodecs()) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void registersCodecForBatchInner2DeathEggRobotBombChild() {
        Set<String> names = codecClassNames();

        // ArticulatedChild, HeadChild, JetChild are intentionally NOT checked:
        // they are construction-spawned and must NOT have codecs.
        List<String> required = List.of(
                "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance"
                        + "$BombChild");

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }
}
