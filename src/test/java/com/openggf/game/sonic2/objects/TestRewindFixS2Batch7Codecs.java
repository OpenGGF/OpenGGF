package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.DynamicObjectRewindCodec;
import com.openggf.level.objects.ObjectRewindDynamicCodecs;
import com.openggf.level.objects.SignpostSparkleObjectInstance;
import com.openggf.level.objects.boss.BossExplosionObjectInstance;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link Sonic2ObjectRegistry} (unioned with the shared codecs)
 * now exposes a dynamic rewind recreate codec for every batch-7 S2 object that
 * was previously dropped on a held-rewind restore.
 *
 * <p>Batch-7 adds:
 * <ul>
 *   <li>{@link BossExplosionObjectInstance} — shared boss-defeat explosion, registered
 *       per-game (this entry uses {@code Sonic2Sfx.BOSS_EXPLOSION}); a ~49-frame animated
 *       one-shot. Recreated via {@code exactSpawnCodec}; {@code sfxId} stays final because
 *       the codec passes the game's own constant and {@code initialized} is restored true.</li>
 *   <li>{@link SignpostSparkleObjectInstance} — shared S1+S2 signpost ring sparkle, recreated
 *       via the shared {@code exactSpawnCodec}. Its position lives in non-final
 *       {@code worldX}/{@code worldY} (base {@code spawn} is null), reapplied after recreate.</li>
 * </ul>
 *
 * <p>{@code com.openggf.level.objects.BoxObjectInstance} — the debug-box base class — is
 * intentionally accept-drop (it is never registered as a factory and is never spawned as its
 * own concrete type in gameplay), documented in {@code docs/KNOWN_DISCREPANCIES.md}; it is
 * deliberately NOT required to have a codec here.
 *
 * <p>Pure registry-content test: it constructs a registry and reads
 * {@code dynamicRewindCodecs()} without a ROM, OpenGL, or an active gameplay session. Full
 * session round-trip is handled by the rewind coverage guard.
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
    void registersCodecsForBatch7S2Objects() {
        Set<String> names = codecClassNames();

        List<String> required = List.of(
                BossExplosionObjectInstance.class.getName(),
                SignpostSparkleObjectInstance.class.getName());

        for (String name : required) {
            assertTrue(names.contains(name),
                    "missing rewind recreate codec for " + name);
        }
    }
}
