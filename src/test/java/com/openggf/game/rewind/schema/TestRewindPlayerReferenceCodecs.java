package com.openggf.game.rewind.schema;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRewindPlayerReferenceCodecs {
    @BeforeEach
    void setUpRuntime() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    void clearRegistry() {
        RewindSchemaRegistry.clearForTest();
        SessionManager.clear();
    }

    @Test
    void acquiredPlayerSetIsMutableCapturedStateAndRestoresRecreatedIdentitiesInPlace() {
        var oldMain = player("old-main");
        var oldSidekick = player("old-sidekick");
        var fixture = new AcquiredPlayersFixture();
        fixture.owners.add(oldSidekick);
        fixture.owners.add(oldMain);
        fixture.owners.add(oldMain);
        var helper = fixture.owners;
        var blob = CompactFieldCapturer.capture(fixture, context(oldMain, oldSidekick));
        fixture.owners.clear();
        var newMain = player("new-main");
        var newSidekick = player("new-sidekick");
        CompactFieldCapturer.restore(fixture, blob, context(newMain, newSidekick));
        assertSame(helper, fixture.owners);
        assertEquals(List.of(newMain, newSidekick), fixture.owners.players());
        assertTrue(RewindSchemaRegistry.schemaFor(AcquiredPlayersFixture.class).capturedFields().stream()
                .anyMatch(field -> field.field().getName().equals("owners")));
        assertThrows(IllegalStateException.class, () -> CompactFieldCapturer.capture(fixture));
    }

    @Test
    void craneReleaseRestoresItsAcquiredPlayerThroughTheCentralReferencePolicy() {
        var oldMain = player("old-main");
        oldMain.setObjectControlled(true);
        var spawn = new com.openggf.level.objects.ObjectSpawn(100, 200, 0, 0, 0, false, 0);
        var original = new com.openggf.game.sonic3k.objects.SszCranePlayerRelease(spawn, oldMain);
        var snapshot = original.captureRewindState(context(oldMain, null));
        var newMain = player("new-main");
        newMain.setObjectControlled(true);
        newMain.setObjectMappingFrameControl(true);
        var restored = new com.openggf.game.sonic3k.objects.SszCranePlayerRelease(spawn);
        restored.restoreRewindState(snapshot, context(newMain, null));
        restored.releasePlayersForVisualDirection();
        assertFalse(newMain.isObjectControlled());
        assertFalse(newMain.isObjectMappingFrameControl());
        assertTrue(oldMain.isObjectControlled(), "Restoration must release the recreated identity, not the original sprite");
    }

    @Test
    void emptyAcquiredPlayerSetStillSnapshotsAndClearsWithoutAnIdentityTable() {
        var fixture = new AcquiredPlayersFixture();
        var blob = CompactFieldCapturer.capture(fixture);
        fixture.owners.add(player("new-main"));
        CompactFieldCapturer.restore(fixture, blob);
        assertTrue(fixture.owners.isEmpty());
    }

    private static final class AcquiredPlayersFixture {
        final com.openggf.level.objects.PlayerControlOwners owners = new com.openggf.level.objects.PlayerControlOwners();
    }

    @Test
    void nullPlayerReferencesRestoreWithoutAnIdentityTable() {
        var fixture = new PlayerReferenceFixture(null, null);
        var snapshot = CompactFieldCapturer.capture(fixture);
        fixture.sprite = player("later-owner");
        CompactFieldCapturer.restore(fixture, snapshot);
        assertNull(fixture.playable);
        assertNull(fixture.sprite);
    }

    @Test
    void restoresDirectPlayerReferencesThroughIdentityTable() {
        TestablePlayableSprite oldMain = player("old-main");
        TestablePlayableSprite oldSidekick = player("old-sidekick");
        PlayerReferenceFixture fixture = new PlayerReferenceFixture(oldMain, oldSidekick);

        RewindObjectStateBlob blob = CompactFieldCapturer.capture(fixture, context(oldMain, oldSidekick));

        TestablePlayableSprite newMain = player("new-main");
        TestablePlayableSprite newSidekick = player("new-sidekick");
        fixture.playable = null;
        fixture.sprite = null;
        CompactFieldCapturer.restore(fixture, blob, context(newMain, newSidekick));

        assertSame(newMain, fixture.playable);
        assertSame(newSidekick, fixture.sprite);
    }

    @Test
    void restoresPlayerReferenceCollectionsAndMapsThroughIdentityTable() {
        TestablePlayableSprite oldMain = player("old-main");
        TestablePlayableSprite oldSidekick = player("old-sidekick");
        PlayerCollectionFixture fixture = new PlayerCollectionFixture(oldMain, oldSidekick);

        RewindObjectStateBlob blob = CompactFieldCapturer.capture(fixture, context(oldMain, oldSidekick));

        TestablePlayableSprite newMain = player("new-main");
        TestablePlayableSprite newSidekick = player("new-sidekick");
        fixture.players.clear();
        fixture.states.clear();
        CompactFieldCapturer.restore(fixture, blob, context(newMain, newSidekick));

        assertEquals(List.of(newMain, newSidekick), new ArrayList<>(fixture.players));
        assertEquals(List.of(newMain, newSidekick), new ArrayList<>(fixture.states.keySet()));
        assertEquals(7, fixture.states.get(newMain));
        assertEquals(9, fixture.states.get(newSidekick));
    }

    @Test
    void identityPlayerMapsCaptureStableAssociationsAfterIdentityReplacement() {
        // Duplicate character labels still identify two distinct participant slots.
        TestablePlayableSprite oldMain = player("sonic");
        TestablePlayableSprite oldSidekick = player("sonic");
        IdentityPlayerMapFixture fixture = new IdentityPlayerMapFixture(oldMain, oldSidekick);
        var map = (ReorderedIdentityMap) fixture.states;
        RewindObjectStateBlob before = CompactFieldCapturer.capture(fixture, context(oldMain, oldSidekick));

        for (int cycle = 0; cycle < 2; cycle++) {
            TestablePlayableSprite newMain = player("sonic");
            TestablePlayableSprite newSidekick = player("sonic");
            // Model a different identity-hash bucket order without a probabilistic test.
            map.reverse = cycle == 0;
            var freshContext = context(newMain, newSidekick);
            CompactFieldCapturer.restore(fixture, before, freshContext);

            assertSame(map, fixture.states, "final identity maps restore in place");
            assertEquals(3, fixture.states.size());
            assertEquals(7, fixture.states.get(newMain));
            assertEquals(9, fixture.states.get(newSidekick));
            assertEquals(11, fixture.states.get(null));
            assertFalse(fixture.states.containsKey(oldMain));
            assertFalse(fixture.states.containsKey(oldSidekick));
            assertEquals(cycle == 0 ? List.of(11, 9, 7) : List.of(7, 9, 11),
                    fixture.states.entrySet().stream().map(Map.Entry::getValue).toList(),
                    "capture must not reorder the live map");
            RewindObjectStateBlob restored = CompactFieldCapturer.capture(fixture, freshContext);
            assertArrayEquals(before.scalarData(), restored.scalarData(),
                    "association encoding must survive replacement identities and bucket order");
            assertArrayEquals(before.opaqueValues(), restored.opaqueValues());
        }
    }

    @Test
    void orderedPlayerMapsRetainTheirDeclaredIterationOrder() {
        var oldMain = player("main");
        var oldSidekick = player("sidekick");
        var fixture = new PlayerCollectionFixture(oldMain, oldSidekick);
        fixture.states.clear();
        fixture.states.put(oldSidekick, 9);
        fixture.states.put(oldMain, 7);
        var before = CompactFieldCapturer.capture(fixture, context(oldMain, oldSidekick));
        var newMain = player("main");
        var newSidekick = player("sidekick");
        CompactFieldCapturer.restore(fixture, before, context(newMain, newSidekick));
        assertEquals(List.of(newSidekick, newMain), new ArrayList<>(fixture.states.keySet()));
        assertEquals(List.of(9, 7), new ArrayList<>(fixture.states.values()));
    }

    @Test
    void playerReferenceCaptureRequiresIdentityContext() {
        PlayerReferenceFixture fixture = new PlayerReferenceFixture(player("main"), null);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> CompactFieldCapturer.capture(fixture));

        assertTrue(failure.getMessage().contains("RewindIdentityTable"));
    }

    private static RewindCaptureContext context(AbstractPlayableSprite main, AbstractPlayableSprite sidekick) {
        RewindIdentityTable table = new RewindIdentityTable();
        table.registerPlayer(main, PlayerRefId.mainPlayer());
        if (sidekick != null) {
            table.registerPlayer(sidekick, PlayerRefId.sidekick(0));
        }
        return RewindCaptureContext.withIdentityTable(table);
    }

    private static TestablePlayableSprite player(String code) {
        return new TestablePlayableSprite(code, (short) 0, (short) 0);
    }

    private static final class IdentityPlayerMapFixture {
        final IdentityHashMap<AbstractPlayableSprite, Integer> states = new ReorderedIdentityMap();

        IdentityPlayerMapFixture(AbstractPlayableSprite main, AbstractPlayableSprite sidekick) {
            states.put(main, 7);
            states.put(sidekick, 9);
            states.put(null, 11);
        }
    }

    private static final class ReorderedIdentityMap extends IdentityHashMap<AbstractPlayableSprite, Integer> {
        boolean reverse;

        @Override
        public Set<Map.Entry<AbstractPlayableSprite, Integer>> entrySet() {
            var entries = new ArrayList<>(super.entrySet());
            entries.sort(java.util.Comparator.comparingInt(Map.Entry::getValue));
            if (reverse) {
                java.util.Collections.reverse(entries);
            }
            return new LinkedHashSet<>(entries);
        }
    }

    private static final class PlayerReferenceFixture {
        PlayableEntity playable;
        AbstractPlayableSprite sprite;

        private PlayerReferenceFixture(PlayableEntity playable, AbstractPlayableSprite sprite) {
            this.playable = playable;
            this.sprite = sprite;
        }
    }

    private static final class PlayerCollectionFixture {
        Set<AbstractPlayableSprite> players = new LinkedHashSet<>();
        Map<AbstractPlayableSprite, Integer> states = new LinkedHashMap<>();

        private PlayerCollectionFixture(AbstractPlayableSprite main, AbstractPlayableSprite sidekick) {
            players.add(main);
            players.add(sidekick);
            states.put(main, 7);
            states.put(sidekick, 9);
        }
    }

}
