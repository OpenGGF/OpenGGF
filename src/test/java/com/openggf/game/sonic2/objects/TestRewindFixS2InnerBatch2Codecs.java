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
 * now exposes a dynamic rewind recreate codec for every batch-inner2 S2
 * inner-class hazard/solid child that was previously dropped on a held-rewind
 * restore (no codec matched their JVM binary name).
 *
 * <p>These are {@code static} nested children of the DEZ Death Egg Robot and WFZ
 * bosses, keyed by their JVM binary name ({@code Outer$Inner}). Each codec relinks
 * the live parent boss (recreated earlier in the restore loop, so present in
 * {@code getActiveObjects()}) and reflection-constructs the package-private nested
 * type; the in-flight differentiator scalars are all already non-final, so the
 * generic field capturer reapplies them after recreate (no un-finaling required):
 * <ul>
 *   <li>{@code Sonic2DeathEggRobotInstance$ArticulatedChild} — final-boss articulated
 *       part HURT hitbox. Once {@code falling}, it carries its own xVel/yVel/fallTimer
 *       trajectory and is no longer re-positioned by the body. Exact-class match so the
 *       {@code ForearmChild} subclass (final {@code isFront}) is not caught.</li>
 *   <li>{@code Sonic2DeathEggRobotInstance$BombChild} — fired bomb HURT hazard flying its
 *       own arc; not re-emitted by the body.</li>
 *   <li>{@code Sonic2DeathEggRobotInstance$HeadChild} — the only hittable part of the
 *       final boss; owns the non-skippable head state machine that releases the body.
 *       Codec relinks {@code parent.jet}/lifetime; head-local state is non-final.</li>
 *   <li>{@code Sonic2DeathEggRobotInstance$JetChild} — exhaust HURT hitbox; codec relinks
 *       the parent's {@code jet} back-reference.</li>
 *   <li>{@code Sonic2WFZBossInstance$WFZFloatingPlatform} — rideable top-solid platform
 *       flying its own descend/oscillation trajectory; not re-emitted by the releaser.</li>
 *   <li>{@code Sonic2WFZBossInstance$WFZLaserWall} — fixed solid laser-wall barrier; codec
 *       relinks whichever of the parent's {@code leftWall}/{@code rightWall} is null.</li>
 *   <li>{@code Sonic2WFZBossInstance$WFZPlatformHurt} — invisible HURT hitbox riding below a
 *       floating platform; codec relinks the live platform parent (restored first).</li>
 * </ul>
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
    void registersCodecsForBatchInner2S2Children() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance"
                        + "$ArticulatedChild",
                "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance"
                        + "$BombChild",
                "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance"
                        + "$HeadChild",
                "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance"
                        + "$JetChild",
                "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance"
                        + "$WFZFloatingPlatform",
                "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance"
                        + "$WFZLaserWall",
                "com.openggf.game.sonic2.objects.bosses.Sonic2WFZBossInstance"
                        + "$WFZPlatformHurt");

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }
}
