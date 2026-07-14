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

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    // snapshot carries no subclass payload -- exercised directly here, and
    // indirectly by the pre-Task-3-constructor test below.
    @Test
    void restoreHookReceivesNullWhenSnapshotCarriesNone() {
        ComboSprite sprite = new ComboSprite("combo", (short) 0, (short) 0);
        sprite.comboCounter = 42;

        sprite.restoreSubclassRewindState(null);

        assertEquals(0, sprite.comboCounter,
                "subclass must reset its state when the restore hook receives null");
    }

    // (d) The previous canonical PlayerRewindExtra constructor (pre-Task-3,
    // with no subclassExtra parameter) still compiles/links and always yields
    // subclassExtra() == null.
    @Test
    void preservedOldCanonicalConstructorCompilesAndYieldsNullSubclassExtra() throws Exception {
        ComboSprite sprite = new ComboSprite("combo", (short) 0, (short) 0);
        sprite.comboCounter = 5;
        PlayerRewindExtra current = sprite.captureRewindState().playerExtra();
        assertEquals(new ComboExtra(5), current.subclassExtra());

        RecordComponent[] components = PlayerRewindExtra.class.getRecordComponents();
        Object[] oldShapeArgs = Arrays.stream(components)
                .filter(component -> !component.getName().equals("subclassExtra"))
                .map(component -> {
                    try {
                        return component.getAccessor().invoke(current);
                    } catch (ReflectiveOperationException ex) {
                        throw new IllegalStateException(ex);
                    }
                })
                .toArray();

        Constructor<?> oldCanonical = Arrays.stream(PlayerRewindExtra.class.getConstructors())
                .filter(ctor -> ctor.getParameterCount() == oldShapeArgs.length)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "expected the preserved pre-Task-3 canonical PlayerRewindExtra constructor"));

        PlayerRewindExtra legacyShapeExtra = (PlayerRewindExtra) oldCanonical.newInstance(oldShapeArgs);
        assertNull(legacyShapeExtra.subclassExtra(),
                "pre-Task-3 constructor shape has no subclassExtra parameter and must yield null");

        // And the restore hook must receive that null, resetting subclass state.
        PerObjectRewindSnapshot legacySnapshot = sprite.captureRewindState().withPlayerExtra(legacyShapeExtra);
        sprite.restoreRewindState(legacySnapshot);
        assertEquals(0, sprite.comboCounter,
                "restoring a pre-Task-3-shaped snapshot must null out (reset) subclass state");
    }
}
