package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** Native loc_68D8/loc_13A10 has one Obj_Tails story writer, independently of extension rosters. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kScriptedIntroCarryAdmission {
    private final CanonicalEntryMatrixSupport support = new CanonicalEntryMatrixSupport() {
        @Override TestS3kCanonicalEntryLifecycle.Act binding() {
            return new TestS3kCanonicalEntryLifecycle.Act("CNZ1", 3, 0);
        }
    };
    @BeforeEach void saveConfiguration() { support.saveConfiguration(); }
    @AfterEach void restoreConfiguration() { support.restoreConfiguration(); }

    @ParameterizedTest(name = "native intro carrier {0}/{1}/{2}")
    @CsvSource(value = {
            "3;off;tails", "3;off;tails,tails", "3;off;sonic,tails",
            "3;s1;sonic,sonic,sonic,sonic,sonic,sonic,sonic",
            "7;off;", "7;off;tails", "7;off;sonic,tails",
            "7;s1;sonic,sonic,sonic,sonic,sonic,sonic,sonic", "3;s2;tails", "7;s2;tails"}, delimiter = ';')
    void onlyTheCapableNativeStoryCarrierOwnsFreshPickupAndItsWholeWorldReplay(
            int zone, String donor, String followers) throws Exception {
        var owner = new CanonicalEntryMatrixSupport() {
            @Override TestS3kCanonicalEntryLifecycle.Act binding() {
                return new TestS3kCanonicalEntryLifecycle.Act("native-carry", zone, 0);
            }
        };
        owner.config = support.config;
        var row = new CanonicalEntryMatrixSupport.Row("sonic",
                com.openggf.configuration.WidescreenAspect.NATIVE_4_3, donor,
                followers == null ? "" : followers);
        var fixture = owner.boot(row);
        fixture.stepIdleFrames(2);
        var sprites = GameServices.sprites();
        var main = sprites.getMainPlayable();
        var all = sprites.getRegisteredSidekicks();
        String[] configured = row.followers().isEmpty() ? new String[0] : row.followers().split(",");
        var previous = main;
        for (int index = 0; index < configured.length; index++) {
            var participant = all.get(index);
            assertEquals(configured[index], participant.characterKey().persisted());
            assertFalse(SpriteManagerInternalAccess.usesNativeScriptedArt(sprites, participant));
            assertSame(previous, participant.getCpuController().getLeader());
            previous = participant;
        }
        var writers = all.stream().filter(player -> player.getTailsCarryController()
                .isCarryingMainCharacter()).toList();
        assertEquals(1, writers.size(), "one actual ROM story writer");
        var carrier = writers.getFirst();
        assertEquals(SecondaryAbility.FLY, carrier.getSecondaryAbility(),
                "loc_13A10 belongs to Obj_Tails, not an incapable configured CPU");
        assertSame(main, carrier.getCpuController().getLeader());
        boolean intrinsic = configured.length == 0 || !configured[0].equals("tails");
        assertEquals(configured.length + (intrinsic ? 1 : 0), all.size());
        assertEquals(intrinsic, SpriteManagerInternalAccess.usesNativeScriptedArt(sprites, carrier));
        if (intrinsic) assertTrue(carrier.getCpuController().isTransientCarrySidekick());
        else assertSame(all.getFirst(), carrier);
        // A real fresh carry is short and may release inside45 frames. Capture
        // the complete owner graph now and compare both full forward cycles.
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        fixture.stepIdleFrames(45);
        var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(before, registry.capture(), "fresh carry restore", 45);
            fixture.stepIdleFrames(45);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(after, registry.capture(), "fresh carry replay", 45);
        }
    }
    @ParameterizedTest(name = "incapable-first native carrier natural retirement/recreation {0}")
    @org.junit.jupiter.params.provider.ValueSource(ints = {3,7})
    void intrinsicCarrierNaturallyRetiresAndRecreatesWithoutRemovingConfiguredParticipants(int zone) {
        var owner = new CanonicalEntryMatrixSupport() {
            @Override TestS3kCanonicalEntryLifecycle.Act binding() {
                return new TestS3kCanonicalEntryLifecycle.Act("native-carry", zone, 0);
            }
        };
        owner.config = support.config;
        var row = new CanonicalEntryMatrixSupport.Row("sonic",
                com.openggf.configuration.WidescreenAspect.NATIVE_4_3, "s1",
                "sonic,sonic,sonic,sonic,sonic,sonic,sonic");
        var fixture = owner.boot(row);
        fixture.stepIdleFrames(2);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var alive = registry.capture();
        fixture.stepIdleFrames(45);
        var forward = registry.capture();
        int lives = GameServices.gameState().getLives();
        for (int frame = 0; frame < 800 && GameServices.sprites().getRegisteredSidekicks().stream()
                .anyMatch(player -> SpriteManagerInternalAccess.usesNativeScriptedArt(GameServices.sprites(), player)); frame++) {
            fixture.stepIdleFrames(1);
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertTrue(GameServices.gameState().getLives() >= lives);
        }
        assertEquals(7, GameServices.sprites().getRegisteredSidekicks().size(), "only intrinsic actor retires");
        assertTrue(GameServices.sprites().getRegisteredSidekicks().stream().noneMatch(player ->
                SpriteManagerInternalAccess.usesNativeScriptedArt(GameServices.sprites(), player)));
        var absent = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(alive);
            var restored = GameServices.sprites().getRegisteredSidekicks().getLast();
            assertEquals(SecondaryAbility.FLY, restored.getSecondaryAbility());
            assertTrue(SpriteManagerInternalAccess.usesNativeScriptedArt(GameServices.sprites(), restored));
            assertSame(GameServices.sprites().getMainPlayable(), restored.getCpuController().getLeader());
            TestS3kCanonicalEntryLifecycle.assertSameWorld(alive, registry.capture(), "retired native carrier restore", 45);
            fixture.stepIdleFrames(45);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(forward, registry.capture(), "recreated native carrier forward", 45);
            registry.restore(absent);
            assertEquals(7, GameServices.sprites().getRegisteredSidekicks().size());
            TestS3kCanonicalEntryLifecycle.assertSameWorld(absent, registry.capture(), "native carrier reverse absence", 45);
        }
    }

}
