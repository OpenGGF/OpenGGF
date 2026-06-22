package com.openggf.game.sonic2.objects;

import com.openggf.game.rewind.RewindRoundTripHarness;
import com.openggf.game.rewind.RewindRoundTripHarness.RoundTripSweepResult;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.SignpostSparkleObjectInstance;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Verifies the S2 batch-7 signpost sparkle moved from its old shared codec onto
 * generic recreate.
 *
 * <p>The sparkle has a null base spawn and stores its position in non-final
 * {@code worldX}/{@code worldY}. Generic recreate constructs a zero-position
 * placeholder through its {@code (int,int)} constructor and the compact scalar
 * restore reapplies the captured position.
 */
class TestRewindFixS2Batch7Codecs {

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
    void signpostSparkleNoLongerHasRegisteredCodec() {
        Set<String> names = codecClassNames();

        assertFalse(names.contains(SignpostSparkleObjectInstance.class.getName()),
                "signpost sparkle should restore through generic recreate, not a dynamic codec");
    }

    @Test
    void signpostSparkleRoundTripsThroughGenericRecreate() {
        RoundTripSweepResult result =
                RewindRoundTripHarness.probeClass(SignpostSparkleObjectInstance.class.getName());

        assertInstanceOf(RoundTripSweepResult.Passed.class, result,
                "signpost sparkle should round-trip through generic recreate");
    }
}
