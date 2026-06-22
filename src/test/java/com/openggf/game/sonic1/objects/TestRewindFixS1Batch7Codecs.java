package com.openggf.game.sonic1.objects;

import com.openggf.level.objects.BreathingBubbleInstance;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Batch-7 S1 rewind classification test.
 *
 * <p>Unlike the earlier S1 batches, batch-7 adds <em>no</em> new S1 rewind
 * recreate codecs: both batch-7 S1 objects are intentionally <strong>accept-drop</strong>
 * cosmetic/dead-code cases (documented in {@code docs/KNOWN_DISCREPANCIES.md},
 * section "Batch-7 Rewind: Transient Cosmetic Children Not Rewound"), so this
 * test instead pins that decision: it asserts the two accept-drop classes do NOT
 * acquire a codec in {@link Sonic1ObjectRegistry} (unioned with the shared codecs),
 * which would silently change the documented behaviour.
 *
 * <ul>
 *   <li>{@link Sonic1TryAgainEggmanObjectInstance} — S1 TRY AGAIN / END ending Eggman
 *       (object 0x8B). Never instantiated in {@code src/main}: the live ending-screen
 *       Eggman is reimplemented inline inside
 *       {@code com.openggf.game.sonic1.credits.TryAgainEndManager}, so the instance class
 *       is orphaned/dead code and never enters the rewindable dynamic-object list. Its
 *       {@code #recreate} baseline key is a {@code RewindCoverageAnalyzer}
 *       over-approximation; it is not spawn-constructible (null spawn + live sibling
 *       {@code emeralds} ref), so it stays accept-drop.</li>
 *   <li>{@link BreathingBubbleInstance} — shared underwater drowning bubble spawned by
 *       {@code com.openggf.sprites.playable.DrowningController} (S1 LZ Obj0A + S2). A
 *       short-lived cosmetic particle re-emitted in-frame; not spawn-codec eligible
 *       (6 non-spawn ctor args incl. the RNG-gated {@code countdownNumber}), not
 *       parent/sibling-linked. Stays accept-drop, mirroring {@code Sonic1SplashObjectInstance}.</li>
 * </ul>
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay session.
 */
class TestRewindFixS1Batch7Codecs {

    private static Set<String> codecClassNames() {
        Set<String> names = new HashSet<>();
        List<DynamicObjectRewindCodec> codecs = new Sonic1ObjectRegistry().dynamicRewindCodecs();
        for (DynamicObjectRewindCodec codec : codecs) {
            names.add(codec.className());
        }
        for (DynamicObjectRewindCodec codec : ObjectRewindDynamicCodecs.sharedCodecs()) {
            names.add(codec.className());
        }
        return names;
    }

    @Test
    void batch7S1ObjectsStayAcceptDropWithNoCodec() {
        Set<String> names = codecClassNames();

        List<String> acceptDrop = List.of(
                Sonic1TryAgainEggmanObjectInstance.class.getName(),
                BreathingBubbleInstance.class.getName());

        for (String name : acceptDrop) {
            assertFalse(names.contains(name),
                    "batch-7 accept-drop class unexpectedly acquired a rewind codec: " + name
                            + " (see docs/KNOWN_DISCREPANCIES.md 'Batch-7 Rewind: Transient "
                            + "Cosmetic Children Not Rewound')");
        }
    }
}
