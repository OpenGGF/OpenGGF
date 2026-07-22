package com.openggf.sprites.playable;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.PerObjectRewindSnapshot.PlayableSubclassRewindExtra;
import com.openggf.level.objects.PerObjectRewindSnapshot.PlayerRewindExtra;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit test for the playable-subclass rewind capture hooks
 * ({@link AbstractPlayableSprite#captureSubclassRewindState()} /
 * {@link AbstractPlayableSprite#restoreSubclassRewindState(PlayableSubclassRewindExtra)})
 * introduced for mod-character rewind support.
 */
@Isolated
class TestPlayableSubclassRewind {

    @BeforeEach
    void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    /** Immutable payload standing in for a mod-character's extra rewind state. */
    private record ComboExtra(int comboCounter) implements PlayableSubclassRewindExtra {
    }

    /** Test-local subclass overriding the subclass rewind hooks with a record payload. */
    private static final class ComboSprite extends TestablePlayableSprite {
        int comboCounter;

        ComboSprite(String code, short x, short y) {
            super(code, x, y);
        }

        @Override
        protected PlayableSubclassRewindExtra captureSubclassRewindState() {
            return new ComboExtra(comboCounter);
        }

        @Override
        protected void restoreSubclassRewindState(PlayableSubclassRewindExtra extra) {
            comboCounter = extra == null ? 0 : ((ComboExtra) extra).comboCounter();
        }
    }

    /** Test-local subclass that records the end-to-end null-payload restore contract. */
    private static final class NullExtraSprite extends TestablePlayableSprite {
        int subclassCounter;
        boolean restoreHookInvoked;
        PlayableSubclassRewindExtra restoredExtra = new ComboExtra(-1);

        NullExtraSprite() {
            super("null-extra", (short) 0, (short) 0);
        }

        @Override
        protected PlayableSubclassRewindExtra captureSubclassRewindState() {
            return null;
        }

        @Override
        protected void restoreSubclassRewindState(PlayableSubclassRewindExtra extra) {
            restoreHookInvoked = true;
            restoredExtra = extra;
            if (extra == null) {
                subclassCounter = 0;
            }
        }
    }

    // (a) A subclass overriding the hooks round-trips its payload through
    // captureRewindState()/restoreRewindState(), both via the snapshot record
    // and via the live field after restore.
    @Test
    void subclassPayloadRoundTripsThroughCaptureAndRestore() {
        ComboSprite sprite = new ComboSprite("combo", (short) 0, (short) 0);
        sprite.comboCounter = 7;

        PerObjectRewindSnapshot snapshot = sprite.captureRewindState();
        PlayerRewindExtra extra = snapshot.playerExtra();
        assertEquals(new ComboExtra(7), extra.subclassExtra(),
                "captured snapshot must carry the subclass payload");

        sprite.comboCounter = 0;
        sprite.restoreRewindState(snapshot);

        assertEquals(7, sprite.comboCounter, "restore must hydrate the live subclass field");
    }

    // (b) A subclass that does NOT override the hooks round-trips with a null
    // subclassExtra and does not throw on capture or restore.
    @Test
    void subclassWithoutOverridesRoundTripsWithNullExtraAndNoThrow() {
        TestablePlayableSprite sprite = new TestablePlayableSprite("plain", (short) 0, (short) 0);

        PerObjectRewindSnapshot snapshot = sprite.captureRewindState();
        assertNull(snapshot.playerExtra().subclassExtra(),
                "default captureSubclassRewindState() must return null");

        assertDoesNotThrow(() -> sprite.restoreRewindState(snapshot),
                "restoreSubclassRewindState(null) must be tolerated (default no-op)");
    }

    // (c) The restore hook is always invoked, and receives null when the
    // snapshot carries no subclass payload.
    @Test
    void restoreHookReceivesNullWhenSnapshotCarriesNone() {
        NullExtraSprite sprite = new NullExtraSprite();
        sprite.subclassCounter = 42;

        PerObjectRewindSnapshot snapshot = sprite.captureRewindState();
        assertNull(snapshot.playerExtra().subclassExtra(),
                "capture must preserve the subclass's intentional null payload");

        sprite.subclassCounter = 99;
        sprite.restoreRewindState(snapshot);

        assertTrue(sprite.restoreHookInvoked,
                "public snapshot restore must invoke the subclass restore hook");
        assertNull(sprite.restoredExtra,
                "public snapshot restore must forward the captured null payload");
        assertEquals(0, sprite.subclassCounter,
                "the null payload must reset stale subclass state during public restore");
    }

    // (d) The current unpublished 0.7 candidate exposes only the record's canonical
    // constructor; provisional compatibility constructors must not return.
    @Test
    void playerRewindExtraExposesOnlyCanonicalZeroSevenConstructor() {
        var constructors = PlayerRewindExtra.class.getConstructors();

        assertEquals(1, constructors.length,
                "Mod API 0.7 must not retain provisional PlayerRewindExtra constructor shapes");
        assertEquals(PlayerRewindExtra.class.getRecordComponents().length,
                constructors[0].getParameterCount(),
                "the sole public constructor must be the record's canonical constructor");
    }
}
