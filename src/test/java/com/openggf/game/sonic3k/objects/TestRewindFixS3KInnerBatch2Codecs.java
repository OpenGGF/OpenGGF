package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic3kObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-inner2 nested-class
 * child that was previously dropped on a held-rewind restore.
 *
 * <p>These are static/non-static nested children (MGZ miniboss spire/arm, gumball
 * exit trigger, MGZ stone chip, MHZ1/MHZ2 cutscene helpers, HCZ miniboss rocket
 * touch hitbox, ICZ ice-spike hurt child) keyed by their JVM binary name
 * ({@code Outer$Inner}). Each codec either relinks the live parent recreated
 * earlier in the restore or re-runs the child's constructor from the captured
 * spawn; non-spawn differentiator scalars were un-finaled so the generic field
 * capturer reapplies them after recreate.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay
 * session. Full session round-trip coverage is enforced by the rewind coverage
 * guard ({@code TestRewindCoverageGuard}).
 */
class TestRewindFixS3KInnerBatch2Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = new Sonic3kObjectRegistry().dynamicRewindCodecs();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        for (DynamicObjectRewindCodec codec : ObjectRewindDynamicCodecs.sharedCodecs()) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void registersCodecsForBatchInner2S3KChildren() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                "com.openggf.game.sonic3k.objects.MgzMinibossInstance$DrillArmChild",
                "com.openggf.game.sonic3k.objects.IczIceSpikesObjectInstance$SpikeHurtChild");

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }

    @Test
    void migratedInner2ChildrenUseGenericRecreateInsteadOfExplicitCodecs() throws Exception {
        Set<String> names = codecClassNames();

        List<String> genericRecreateClasses = List.of(
                "com.openggf.game.sonic3k.objects.MgzMinibossInstance$CeilingSpireChild",
                "com.openggf.game.sonic3k.objects.GumballMachineObjectInstance$ExitTriggerChild",
                "com.openggf.game.sonic3k.objects.MGZHeadTriggerObjectInstance$HeadTriggerStoneChipChild",
                "com.openggf.game.sonic3k.objects.Mhz1CutsceneKnucklesInstance$Mhz1CutscenePlayerTwoStopper",
                "com.openggf.game.sonic3k.objects.CutsceneKnucklesMhz2Instance$Mhz2KnucklesRouteSwitchChild",
                "com.openggf.game.sonic3k.objects.HczMinibossInstance$RocketTouchChild");

        for (String className : genericRecreateClasses) {
            assertFalse(names.contains(className),
                    className + " must restore through RewindRecreatable generic recreate");
            assertTrue(com.openggf.level.objects.RewindRecreatable.class.isAssignableFrom(Class.forName(className)),
                    className + " must opt into generic recreate");
        }
    }
}
