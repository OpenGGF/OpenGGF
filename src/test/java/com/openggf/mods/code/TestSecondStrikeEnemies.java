package com.openggf.mods.code;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ModRewindRecreatable;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TerrainSurface;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.PoweredScreenAttackable;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Production creator enemy source: committed attacks, native touch, bounded children and rewind. */
class TestSecondStrikeEnemies {
    @TempDir static Path temporary;
    static URLClassLoader loader;

    @BeforeAll static void compileCreatorSource() throws Exception {
        loader = SecondStrikeSourceFixture.compile(temporary.resolve("classes"), SecondStrikeSourceFixture.ENEMIES);
    }

    @AfterAll static void closeCreatorLoader() throws Exception {
        if (loader != null) loader.close();
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    @Test void guardCommitsDirectionThenExposesAnHonestPunishWindow() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            fixture.playerX = 320;
            guard.update(1, fixture.player);
            assertEquals(0x8b, touch(guard).getCollisionFlags(), "visible armed guard is a native HURT object");
            fixture.playerX = 500; // Dodge past it during its tell.
            for (int i = 0; i < 36; i++) guard.update(i, fixture.player);
            guard.update(40, fixture.player);
            assertTrue(guard.getX() < 400, "the committed dash must not retarget the dodge");
            for (int i = 0; i < 24; i++) guard.update(i, fixture.player);
            assertEquals(0x0b, touch(guard).getCollisionFlags(), "braking restores ordinary attackable enemy collision");
            int brakeX = guard.getX();
            for (int i = 0; i < 56; i++) guard.update(i, fixture.player);
            assertEquals(brakeX, guard.getX());
            assertEquals(0x0b, touch(guard).getCollisionFlags(), "punish window remains open while moving through");
        }
    }

    @Test void nearestTargetFiltersAliveAndRoleRangeBeforeComparingNativeParticipants() throws Exception {
        for (String role : List.of("SpotterBadnik", "GuardBadnik", "ArtilleryBadnik")) {
            for (boolean swapped : List.of(false, true)) for (boolean dead : List.of(false, true)) {
                try (var fixture = new Fixture()) {
                    var hidden = mock(PlayableEntity.class);
                    when(hidden.getCentreX()).thenReturn((short)420); when(hidden.getCentreY()).thenReturn((short)(dead ? 200 : 500));
                    when(hidden.getDead()).thenReturn(dead);
                    var eligible = mock(PlayableEntity.class);
                    when(eligible.getCentreX()).thenReturn((short)460); when(eligible.getCentreY()).thenReturn((short)200);
                    fixture.services.withPlayerQuery(new ObjectPlayerQuery(() -> swapped ? eligible : hidden,
                            () -> List.of(swapped ? hidden : eligible)));
                    var enemy = fixture.enemy(role, role.equals("SpotterBadnik") ? "spotter" : role.equals("GuardBadnik") ? "guard" : "artillery", 400, 200);
                    enemy.update(0, swapped ? eligible : hidden);
                    var target = enemy.getClass().getSuperclass().getDeclaredField("targetX"); target.setAccessible(true);
                    assertEquals(460, target.getInt(enemy), role + " must select the eligible participant before nearest-X ranking");
                    when(eligible.getCentreX()).thenReturn((short)300); enemy.update(1, eligible);
                    assertEquals(460, target.getInt(enemy), "a dodge cannot change the committed target");
                }
            }
        }
    }

    @Test void guardStopsAtNativeLedgeInsteadOfCrossingTheGap() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            fixture.playerX = 320;
            guard.update(0, fixture.player);
            for (int i = 0; i < 36; i++) guard.update(i, fixture.player);
            fixture.floorPresent = false;
            guard.update(40, fixture.player);
            assertEquals(400, guard.getX());
            assertEquals(0x0b, touch(guard).getCollisionFlags());
        }
    }

    @Test void chargeTravelsOneHundredFiftyPercentFurtherAndReturnsWithoutOvershooting() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            guard.update(0, fixture.player);
            for (int tick = 0; tick < 36; tick++) guard.update(tick, fixture.player);
            for (int stride = 0; stride < 22; stride++) {
                int previousX = guard.getX();
                guard.update(stride, fixture.player);
                assertEquals(-10, guard.getX() - previousX);
            }
            guard.update(23, fixture.player);
            assertEquals(175, guard.getX(), "225px is 150% further than the previous actual90px");
            assertEquals(0x0b, touch(guard).getCollisionFlags());
            for (int tick = 0; tick < 72; tick++) {
                guard.update(tick, fixture.player);
                assertEquals(175, guard.getX(), "the complete exposed brake stays stationary");
            }
            for (int tick = 0; tick < 112; tick++) {
                int previousX = guard.getX();
                guard.update(tick, fixture.player);
                assertEquals(2, guard.getX() - previousX);
            }
            guard.update(113, fixture.player);
            assertEquals(400, guard.getX(), "the final return stride is one pixel, with no teleport");
            assertEquals(0x0b, touch(guard).getCollisionFlags());
        }
    }

    @Test void reinforcedEnemiesRequireSeparatedHitsAndRewardOnlyTheFinalContact() throws Exception {
        for (String[] definition : List.of(new String[]{"SpotterBadnik", "spotter", "2"},
                new String[]{"ArtilleryBadnik", "artillery", "2"},
                new String[]{"GuardBadnik", "guard", "3"})) {
            try (var fixture = new Fixture()) {
                var enemy = fixture.enemy(definition[0], definition[1], 400, 200);
                int hits = Integer.parseInt(definition[2]);
                for (int hit = 0; hit < hits; hit++) {
                    assertEquals(hits - hit - 1, touch(enemy).getCollisionProperty(),
                            "native rebound on nonfatal hits, ordinary kill bounce on final hit");
                    attack(enemy, fixture.player);
                    if (hit < hits - 1) {
                        assertFalse(enemy.isDestroyed());
                        assertEquals(0, touch(enemy).getCollisionFlags(), "hit flash has no hurt authority");
                        attack(enemy, fixture.player); // Same-frame second player / rolling overlap.
                        assertEquals(hits - hit - 2, touch(enemy).getCollisionProperty());
                        verifyNoInteractions(fixture.score);
                        assertEquals(0, fixture.explosions());
                        fixture.playerX = 800;
                        for (int tick = 0; tick < 24; tick++) enemy.update(tick, fixture.player);
                        fixture.playerX = 320;
                    }
                }
                assertTrue(enemy.isDestroyed());
                assertEquals(1, fixture.explosions());
                verify(fixture.score, times(1)).addScore(100);
                attack(enemy, fixture.player);
                assertEquals(1, fixture.explosions(), "destroyed enemy cannot repeat its final rewards");
            }
        }
    }

    @Test void prolongedRollingOverlapCannotConsumeASecondHitButCollisionRecoversOnTime() throws Exception {
        try (var fixture = new Fixture()) {
            var enemy = fixture.enemy("ArtilleryBadnik", "artillery", 400, 200);
            fixture.playerX = 400;
            attack(enemy, fixture.player);
            for (int tick = 0; tick < 60; tick++) enemy.update(tick, fixture.player);
            attack(enemy, fixture.player);
            assertFalse(enemy.isDestroyed(), "a distinct separated contact is required");
            assertEquals(0x23, touch(enemy).getCollisionFlags(),
                    "body contact continuity must not extend the fixed24-update collision flash");
            fixture.playerX = 800;
            enemy.update(61, fixture.player);
            assertEquals(0x23, touch(enemy).getCollisionFlags());
            attack(enemy, fixture.player);
            assertTrue(enemy.isDestroyed());
        }
    }

    @Test void directPoweredCallbackUsesMultiHitHealthWhileKeepingNativeHurtCollision() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            guard.update(0, fixture.player);
            assertEquals(0x8b, touch(guard).getCollisionFlags(), "the native armed tell still hurts on contact");
            ((PoweredScreenAttackable) guard).onPoweredScreenAttack(fixture.player);
            assertFalse(guard.isDestroyed());
            assertEquals(1, touch(guard).getCollisionProperty(), "only DASH is immune to health damage");
            assertEquals(0, fixture.explosions());
            verifyNoInteractions(fixture.score);
        }
    }

    @Test void fastCommittedDashRejectsTouchAndPoweredScreenAttacks() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            guard.update(0, fixture.player);
            for (int tick = 0; tick < 35; tick++) guard.update(tick, fixture.player);
            assertEquals(400, guard.getX(), "all 36 tell updates remain stationary");
            guard.update(36, fixture.player);
            guard.update(37, fixture.player);
            assertEquals(390, guard.getX(), "charge is 10 pixels/update instead of 4");
            attack(guard, fixture.player);
            ((PoweredScreenAttackable) guard).onPoweredScreenAttack(fixture.player);
            assertFalse(guard.isDestroyed());
            assertEquals(2, touch(guard).getCollisionProperty(), "invulnerable dash cannot lose HP");
            for (int tick = 0; tick < 24; tick++) guard.update(tick, fixture.player);
            assertEquals(225, Math.abs(guard.getX() - 400));
            assertEquals(0x0b, touch(guard).getCollisionFlags());
            assertEquals(0, fixture.explosions());
            verifyNoInteractions(fixture.score);
        }
    }

    @Test void fastDashChecksIntermediateFloorInsteadOfSkippingOverANarrowGap() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            guard.update(0, fixture.player);
            for (int tick = 0; tick < 36; tick++) guard.update(tick, fixture.player);
            fixture.floorGapX = 393;
            guard.update(37, fixture.player);
            assertEquals(400, guard.getX());
            assertEquals(0x0b, touch(guard).getCollisionFlags());
        }
    }

    @Test void guardReturnsSlowlyAfterTheFullBrakeThenChargesTheSameDirectionAgain() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            fixture.playerX = 260;
            finishGuardCharge(guard, fixture);
            assertEquals(175, guard.getX());
            for (int tick = 0; tick < 72; tick++) {
                guard.update(tick, fixture.player);
                assertEquals(175, guard.getX(), "all72 exposed brake updates remain stationary");
                assertEquals(0x0b, touch(guard).getCollisionFlags());
            }
            for (int tick = 0; tick < 112; tick++) {
                int previousX = guard.getX();
                guard.update(tick, fixture.player);
                assertEquals(2, guard.getX() - previousX, "return is a walk, not a teleport or charge");
                assertEquals(0x0b, touch(guard).getCollisionFlags(), "return stays vulnerable");
            }
            guard.update(113, fixture.player);
            assertEquals(400, guard.getX());
            assertEquals(1, fixture.sounds.stream().filter(id -> id == 0x43).count());
            guard.update(0, fixture.player);
            for (int tick = 0; tick < 36; tick++) guard.update(tick, fixture.player);
            guard.update(37, fixture.player);
            assertEquals(390, guard.getX(), "a second same-direction charge has real10px travel");
            assertEquals(2, fixture.sounds.stream().filter(id -> id == 0x43).count());
        }
    }

    @Test void returnRemainsAttackableAndItsMidWalkScalarStateReplays() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            fixture.playerX = 260;
            finishGuardCharge(guard, fixture);
            for (int tick = 0; tick < 84; tick++) guard.update(tick, fixture.player);
            assertEquals(199, guard.getX());
            assertReplay(guard, fixture, 8);
            assertEquals(215, guard.getX());
            assertEquals(0x0b, touch(guard).getCollisionFlags());
            attack(guard, fixture.player);
            assertFalse(guard.isDestroyed());
            assertEquals(1, touch(guard).getCollisionProperty(), "return admits an ordinary nonfatal hit");
            assertEquals(0, fixture.explosions());
            verifyNoInteractions(fixture.score);
        }
    }

    @Test void guardReturnStopsAtAnIntermediateGapAndCannotFlashABlockedFlameDash() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            fixture.playerX = 260;
            finishGuardCharge(guard, fixture);
            for (int tick = 0; tick < 72; tick++) guard.update(tick, fixture.player);
            fixture.floorGapX = 176; // One-pixel hole before a clear2px return endpoint.
            for (int tick = 0; tick < 70; tick++) {
                guard.update(tick, fixture.player);
                assertEquals(175, guard.getX(), "a rejected stride cannot teleport toward the post");
                assertEquals(0x0b, touch(guard).getCollisionFlags(), "blocked return must not re-arm");
            }
            assertEquals(1, fixture.sounds.stream().filter(id -> id == 0x43).count(),
                    "a range-blocked charge must not enter flame DASH");
        }
    }

    @Test void guardCancelsFlameEntryWhenFloorDisappearsDuringTheTell() throws Exception {
        try (var fixture = new Fixture()) {
            var guard = fixture.enemy("GuardBadnik", "guard", 400, 200);
            guard.update(0, fixture.player);
            fixture.floorPresent = false;
            for (int tick = 0; tick < 36; tick++) guard.update(tick, fixture.player);
            assertEquals(400, guard.getX());
            assertEquals(0x0b, touch(guard).getCollisionFlags());
            assertFalse(fixture.sounds.contains(0x43), "flame dash entry requires real native travel");
        }
    }

    private static void finishGuardCharge(AbstractObjectInstance guard, Fixture fixture) {
        guard.update(0, fixture.player);
        for (int tick = 0; tick < 36; tick++) guard.update(tick, fixture.player);
        for (int tick = 0; tick < 23; tick++) guard.update(tick, fixture.player);
    }

    @Test void oneFloorValidatedCompanionIsNonrecursiveAndRetiresWithAnUnloadedParent() throws Exception {
        try (var fixture = new Fixture()) {
            AbstractObjectInstance.updateCameraBounds(7400, 400, 8000, 900, 0);
            var gunner = fixture.enemy("ArtilleryBadnik", "artillery", 0x1dc0, 0x27c);
            fixture.playerX = 0x1e00; fixture.playerY = 0x27c;
            gunner.update(0, fixture.player);
            assertEquals(1, fixture.guards().size());
            var ally = fixture.guards().getFirst();
            for (int tick = 0; tick < 20; tick++) {
                gunner.update(tick, fixture.player);
                ally.update(tick, fixture.player);
            }
            assertEquals(1, fixture.guards().size(), "companions cannot produce more companions");
            assertReplay(ally, fixture, 4);
            gunner.setDestroyed(true);
            assertEquals(0, touch(ally).getCollisionFlags(), "parent retirement removes same-pass hurt authority");
            ally.update(22, fixture.player);
            assertTrue(ally.isDestroyed());
            assertEquals(0, fixture.explosions(), "lifetime cleanup gives no extra reward");
        }
    }

    @Test void parentCombatDefeatPreservesAllyHealthAndRespawnCannotDuplicateIt() throws Exception {
        try (var fixture = new Fixture()) {
            AbstractObjectInstance.updateCameraBounds(7400, 400, 8000, 900, 0);
            var parent = fixture.enemy("ArtilleryBadnik", "artillery", 0x1dc0, 0x27c);
            fixture.playerX = 0x1e00; fixture.playerY = 0x27c;
            parent.update(0, fixture.player);
            var ally = fixture.guards().getFirst();
            attack(parent, fixture.player);
            fixture.playerX = 7900;
            for (int tick = 0; tick < 24; tick++) parent.update(tick, fixture.player);
            attack(parent, fixture.player);
            assertTrue(parent.isDestroyed());
            assertFalse(ally.isDestroyed());
            assertEquals(2, touch(ally).getCollisionProperty(), "combat does not give a free ally kill/hit");
            ally.update(25, fixture.player);
            assertFalse(ally.isDestroyed());
            assertReplay(ally, fixture, 4);
            var respawn = fixture.enemy("ArtilleryBadnik", "artillery", 0x1dc0, 0x27c);
            respawn.update(26, fixture.player);
            assertEquals(1, fixture.guards().size(), "one live ally per native parent anchor across respawn");
            AbstractObjectInstance.updateCameraBounds(0, 0, 900, 500, 0);
            ally.update(27, fixture.player);
            assertTrue(ally.isDestroyed(), "native range retirement is silent even after combat release");
            assertEquals(1, fixture.explosions(), "only defeated artillery received rewards");
            verify(fixture.score, times(1)).addScore(100);
        }
    }

    @Test void missingCompanionFloorDoesNotInventAnEnemyPlatform() throws Exception {
        try (var fixture = new Fixture()) {
            AbstractObjectInstance.updateCameraBounds(7400, 400, 8000, 900, 0);
            fixture.floorPresent = false;
            var gunner = fixture.enemy("ArtilleryBadnik", "artillery", 0x1dc0, 0x27c);
            for (int tick = 0; tick < 20; tick++) gunner.update(tick, fixture.player);
            assertTrue(fixture.guards().isEmpty());
        }
    }

    @Test void hurtHealthCooldownAndSeparationRecreateAndReplay() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.playerX = 400;
            for (String[] definition : List.of(new String[]{"SpotterBadnik", "spotter"},
                    new String[]{"GuardBadnik", "guard"}, new String[]{"ArtilleryBadnik", "artillery"})) {
                var enemy = fixture.enemy(definition[0], definition[1], 400, 200);
                attack(enemy, fixture.player);
                var recreated = assertReplay(enemy, fixture, 30);
                assertFalse(enemy.isDestroyed());
                assertNotEquals(0, touch(enemy).getCollisionFlags(), "rewound held contact cannot prolong hit flash");
                int property = touch(enemy).getCollisionProperty();
                attack(enemy, fixture.player);
                attack(recreated, fixture.player);
                assertEquals(property, touch(enemy).getCollisionProperty(), "restored uninterrupted contact cannot take another hit");
                assertEquals(property, touch(recreated).getCollisionProperty(), "the recreated actor keeps the actual held attacker");
            }
        }
    }

    @Test void heldContactFollowsActorIdentityAcrossReorderAndDropsReplacedActors() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.playerX = 800;
            var held = contactPlayer();
            var fresh = contactPlayer();
            var roster = new ArrayList<PlayableEntity>(List.of(held, fresh));
            fixture.services.withPlayerQuery(new ObjectPlayerQuery(() -> fixture.player, () -> roster));
            var enemy = fixture.enemy("ArtilleryBadnik", "artillery", 400, 200);
            attack(enemy, held);
            for (int tick = 0; tick < 24; tick++) enemy.update(tick, fixture.player);
            java.util.Collections.swap(roster, 0, 1);
            enemy.update(24, fixture.player);
            attack(enemy, held);
            assertFalse(enemy.isDestroyed(), "the held actor remains held after changing roster index");
            attack(enemy, fresh);
            assertTrue(enemy.isDestroyed(), "a fresh actor must not inherit the prior occupant's latch");

            var replacementTarget = fixture.enemy("ArtilleryBadnik", "artillery", 400, 200);
            attack(replacementTarget, held);
            for (int tick = 0; tick < 24; tick++) replacementTarget.update(tick, fixture.player);
            roster.remove(held);
            var replacement = contactPlayer();
            roster.add(replacement);
            replacementTarget.update(24, fixture.player);
            var latches = replacementTarget.getClass().getSuperclass().getDeclaredField("heldAttackers");
            latches.setAccessible(true);
            assertEquals(0, ((PlayableEntity[]) latches.get(replacementTarget)).length,
                    "removal clears the exact old actor even when its replacement has identical coordinates");
            attack(replacementTarget, replacement);
            assertTrue(replacementTarget.isDestroyed());
        }
    }

    private static PlayableEntity contactPlayer() {
        var player = mock(PlayableEntity.class);
        when(player.getCentreX()).thenReturn((short) 400);
        when(player.getCentreY()).thenReturn((short) 200);
        when(player.getYRadius()).thenReturn((short) 19);
        when(player.incrementBadnikChain()).thenReturn(100);
        return player;
    }

    @Test void raisedCoconutTellCanBeInterruptedBeforeAnyDefenderHearsIt() throws Exception {
        try (var fixture = new Fixture()) {
            var caller = fixture.enemy("SpotterBadnik", "spotter", 540, 200);
            var gunner = fixture.enemy("ArtilleryBadnik", "artillery", 400, 200);
            fixture.playerX = 700;
            caller.update(0, fixture.player);
            for (int tick = 0; tick < 12; tick++) {
                caller.update(tick, fixture.player);
                gunner.update(tick, fixture.player);
            }
            assertTrue((boolean) caller.getClass().getMethod("isTelling").invoke(caller));
            attack(caller, fixture.player);
            for (int tick = 0; tick < 60; tick++) {
                caller.update(tick, fixture.player);
                gunner.update(tick, fixture.player);
            }
            assertTrue(fixture.shots().isEmpty(), "interrupting the raised tell prevents a remote commitment");
            assertEquals(List.of(0x6e), fixture.sounds, "no alert or gunner charging cue was committed");
        }
    }

    @Test void aVisibleCallerCannotWakeACompletelyHiddenDefender() throws Exception {
        var visibility = AbstractObjectInstance.class.getDeclaredMethod("isOnScreen", int.class);
        visibility.setAccessible(true);
        for (var aspect : com.openggf.configuration.WidescreenAspect.values()) {
            int width = aspect.pixelWidth();
            try (var fixture = new Fixture()) {
                AbstractObjectInstance.updateCameraBounds(0, 0, width, 224, 0);
                fixture.playerX = width - 140; fixture.playerY = 120;
                var caller = fixture.enemy("SpotterBadnik", "spotter", width - 60, 120);
                var guard = fixture.enemy("GuardBadnik", "guard", width + 32, 120);
                assertTrue(guard.getX() - guard.getOnScreenHalfWidth() > width,
                        "the entire declared body is beyond the actual preset's right edge: " + aspect);
                assertTrue((boolean) visibility.invoke(guard, 32), "positive control for the old centre-only margin");
                assertFalse((boolean) visibility.invoke(guard, 0));
                for (int tick = 0; tick < 25; tick++) caller.update(tick, fixture.player);
                assertTrue((boolean) caller.getClass().getMethod("isCalling").invoke(caller));
                guard.update(25, fixture.player);
                assertFalse((boolean) guard.getClass().getMethod("armed").invoke(guard),
                        "a32px activation margin must not hide the whole charge tell: " + aspect);
                assertEquals(List.of(0x51), fixture.sounds);
                AbstractObjectInstance.updateCameraBounds(64, 0, width + 64, 224, 0);
                assertTrue(guard.getX() + guard.getOnScreenHalfWidth() <= width + 64);
                guard.update(26, fixture.player);
                assertTrue((boolean) guard.getClass().getMethod("armed").invoke(guard),
                        "the same floor and heard alert work with the whole body visible: " + aspect);
                assertEquals(List.of(0x51, 0x48), fixture.sounds);
            }
        }
    }

    @Test void aPreEntryCompanionKeepsItsLifetimeWithoutStartingAHiddenCharge() throws Exception {
        for (var aspect : com.openggf.configuration.WidescreenAspect.values()) {
            try (var fixture = new Fixture()) {
                int parentX = 0x1dc0, y = 0x27c;
                int left = parentX + 16 - aspect.pixelWidth();
                AbstractObjectInstance.updateCameraBounds(left, y - 112, parentX + 16, y + 112, 0);
                fixture.playerX = parentX - 80; fixture.playerY = y;
                var parent = fixture.enemy("ArtilleryBadnik", "artillery", parentX, y);
                parent.update(0, fixture.player);
                var ally = fixture.guards().getFirst();
                var identity = fixture.services.objectQuery().identityOf(ally).orElseThrow();
                assertTrue(ally.getX() - ally.getOnScreenHalfWidth() > parentX + 16);
                ally.update(1, fixture.player);
                assertFalse(ally.isDestroyed(), "existing32px companion lifetime includes this pre-entry ally: " + aspect);
                assertFalse((boolean) ally.getClass().getMethod("armed").invoke(ally));
                assertEquals(List.of(0x53), fixture.sounds);
                AbstractObjectInstance.updateCameraBounds(left + 64, y - 112, parentX + 80, y + 112, 0);
                ally.update(2, fixture.player);
                assertFalse(ally.isDestroyed());
                assertEquals(identity, fixture.services.objectQuery().identityOf(ally).orElseThrow());
                assertTrue((boolean) ally.getClass().getMethod("armed").invoke(ally),
                        "visible entry admits the complete tell with the original ally identity: " + aspect);
                assertEquals(List.of(0x53, 0x48), fixture.sounds);
                int tellX = ally.getX();
                for (int tick = 0; tick < 35; tick++) {
                    ally.update(tick, fixture.player);
                    assertEquals(tellX, ally.getX(), "the visible36-update tell stays stationary");
                }
                assertFalse(fixture.sounds.contains(0x43));
                ally.update(35, fixture.player);
                assertEquals(tellX, ally.getX());
                ally.update(36, fixture.player);
                assertEquals(tellX - 10, ally.getX(), "the first dash movement follows the full visible tell");
            }
        }
    }

    @Test void spotterUsesOneNativeCoconutCueAtEachCallEntry() throws Exception {
        try (var fixture = new Fixture()) {
            var spotter = fixture.enemy("SpotterBadnik", "spotter", 400, 200);
            spotter.update(0, fixture.player);
            assertTrue(fixture.sounds.isEmpty(), "raised coconut is interruptible before the alert");
            for (int tick = 0; tick < 23; tick++) spotter.update(tick, fixture.player);
            assertTrue(fixture.sounds.isEmpty());
            spotter.update(24, fixture.player);
            assertEquals(List.of(0x51), fixture.sounds);
            for (int tick = 0; tick < 168; tick++) spotter.update(tick, fixture.player);
            assertEquals(List.of(0x51), fixture.sounds, "call and rest cannot retrigger the cue");
            spotter.update(169, fixture.player);
            for (int tick = 0; tick < 24; tick++) spotter.update(tick, fixture.player);
            assertEquals(List.of(0x51, 0x51), fixture.sounds);
        }
    }

    @Test void callerEnablesCooperationAndDestroyingItRemovesTheSignal() throws Exception {
        try (var fixture = new Fixture()) {
            var caller = fixture.enemy("SpotterBadnik", "spotter", 540, 200);
            var gunner = fixture.enemy("ArtilleryBadnik", "artillery", 400, 200);
            fixture.playerX = 700; // Outside the gunner's own detection but visible to its caller.
            caller.update(0, fixture.player);
            gunner.update(0, fixture.player);
            assertTrue(fixture.sounds.isEmpty(), "a raised tell is not yet an alert");
            for (int i = 0; i < 24; i++) caller.update(i, fixture.player);
            gunner.update(24, fixture.player);
            var acknowledgement = gunner.getClass().getMethod("acknowledgingAlert");
            acknowledgement.setAccessible(true);
            assertTrue((boolean) acknowledgement.invoke(gunner));
            assertEquals(List.of(0x51, 0x53), fixture.sounds, "the visual acknowledgement adds no ambient sound");
            attack(caller, fixture.player); // Stop the caller after the defender committed.
            for (int i = 0; i < 12; i++) gunner.update(i, fixture.player);
            assertFalse((boolean) acknowledgement.invoke(gunner), "one bounded12-update acknowledgement");
            for (int i = 0; i < 24; i++) gunner.update(i, fixture.player);
            assertEquals(10, fixture.shots().size(), "the heard alert commits a volley that survives interruption of the caller");

            var other = fixture.enemy("ArtilleryBadnik", "artillery", 400, 200);
            caller.setDestroyed(true);
            for (int i = 0; i < 48; i++) other.update(i, fixture.player);
            assertEquals(10, fixture.shots().size(), "destroyed caller cannot keep a hidden alert alive");
            assertEquals(0, touch(caller).getCollisionFlags());
        }
    }

    @Test void gunnerLocksBothFansBeforeTellAndCapsItsLiveChildren() throws Exception {
        try (var fixture = new Fixture()) {
            var gunner = fixture.enemy("ArtilleryBadnik", "artillery", 400, 200);
            fixture.playerX = 320;
            gunner.update(0, fixture.player);
            fixture.playerX = 500;
            fixture.playerY = 100;
            for (int i = 0; i < 36; i++) gunner.update(i, fixture.player);
            for (int i = 0; i < 12; i++) gunner.update(i, fixture.player);
            assertEquals(20, fixture.shots().size());
            int firstX = velocity(fixture.shots().getFirst(), "horizontalVelocity");
            int firstY = velocity(fixture.shots().getFirst(), "verticalVelocity");
            assertTrue(firstX < 0, "volley stays aimed left after the player dodges right");
            var firstFan = fixture.shots().subList(0, 10); var secondFan = fixture.shots().subList(10, 20);
            for (int lane = 0; lane < 10; lane++) {
                assertEquals(velocity(firstFan.get(lane), "horizontalVelocity"), velocity(secondFan.get(lane), "horizontalVelocity"));
                assertEquals(velocity(firstFan.get(lane), "verticalVelocity"), velocity(secondFan.get(lane), "verticalVelocity"));
            }
            assertEquals(-0x80, firstY);
            for (int i = 0; i < 200; i++) gunner.update(i, fixture.player);
            assertEquals(20, fixture.shots().size(), "unexpired shots cannot accumulate behind the player");
            assertEquals(0x23, touch(gunner).getCollisionFlags(), "the gunner's body remains punishable throughout");
        }
    }

    @Test void shieldDeflectionRemovesHurtAuthorityAndShotLifetimeIsBounded() throws Exception {
        try (var fixture = new Fixture()) {
            var shot = fixture.enemy("CommittedShot", "shot", 400, 200);
            assertEquals(0x98, touch(shot).getCollisionFlags());
            assertEquals(8, touch(shot).getShieldReactionFlags());
            assertTrue(touch(shot).onShieldDeflect(fixture.player));
            assertEquals(0, touch(shot).getCollisionFlags());
            assertFalse(touch(shot).onShieldDeflect(fixture.player));
            for (int i = 0; i < 120; i++) shot.update(i, fixture.player);
            assertTrue(shot.isDestroyed());
        }
    }

    @Test void downwardShotExpiresOnNativeFloorAndOffscreenShotsCannotPersist() throws Exception {
        try (var fixture = new Fixture()) {
            var type = loader.loadClass("secondstrike.enemies.CommittedShot");
            var shot = (AbstractObjectInstance) type.getConstructor(ObjectSpawn.class, int.class,
                    int.class).newInstance(spawn("shot", 400, 200), 640, 128);
            shot.setServices(fixture.services);
            for (int i = 0; i < 5; i++) shot.update(i, fixture.player);
            assertTrue(shot.isDestroyed(), "native floor contact removes low projectile hurt authority");
            assertEquals(0, touch(shot).getCollisionFlags());

            var outside = fixture.enemy("CommittedShot", "shot", 1000, 200);
            outside.update(0, fixture.player);
            assertTrue(outside.isDestroyed(), "offscreen projectiles must clean up without a 120-frame wait");
        }
    }

    @Test void attackTellAndProjectileFractionRecreateAndReplayExactly() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.playerX = 320;
            for (String[] definition : List.of(new String[]{"SpotterBadnik", "spotter"},
                    new String[]{"GuardBadnik", "guard"}, new String[]{"ArtilleryBadnik", "artillery"})) {
                var original = fixture.enemy(definition[0], definition[1], 400, 200);
                original.update(0, fixture.player);
                for (int i = 0; i < 12; i++) original.update(i, fixture.player);
                assertReplay(original, fixture, 8);
            }
            var shotType = loader.loadClass("secondstrike.enemies.CommittedShot");
            var shot = (AbstractObjectInstance) shotType.getConstructor(ObjectSpawn.class, int.class,
                    int.class).newInstance(spawn("shot", 400, 200), -640, -123);
            shot.setServices(fixture.services);
            for (int i = 0; i < 3; i++) shot.update(i, fixture.player);
            assertReplay(shot, fixture, 16);
        }
    }

    private static AbstractObjectInstance assertReplay(AbstractObjectInstance original, Fixture fixture, int steps) {
        var identities = new RewindIdentityTable();
        var players = fixture.services.playerQuery().playersFor(
                com.openggf.level.objects.ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
        for (int index = 0; index < players.size(); index++) {
            identities.registerPlayer(players.get(index), index == 0 ? PlayerRefId.mainPlayer() : PlayerRefId.sidekick(index - 1));
        }
        var context = RewindCaptureContext.withIdentityTable(identities);
        assertTrue(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(original.getClass()),
                "the default schema must admit every captured field, including fresh player-reference arrays");
        var snapshot = original.captureRewindState(context);
        assertNotNull(snapshot.compactGenericState());
        var recreated = ((ModRewindRecreatable) original).recreateForRewind(
                new RewindRecreateContext(original.getSpawn(), snapshot, fixture.services));
        recreated.setServices(fixture.services);
        recreated.restoreRewindState(snapshot, context);
        assertEquals(snapshot, recreated.captureRewindState(context), "recreate must restore all captured phase/target/fraction state");
        for (int i = 0; i < steps; i++) {
            original.update(i, fixture.player);
            recreated.update(i, fixture.player);
            assertEquals(original.captureRewindState(context), recreated.captureRewindState(context),
                    "restored state must produce the same forward frames");
        }
        return recreated;
    }

    private static TouchResponseProvider touch(AbstractObjectInstance instance) { return (TouchResponseProvider) instance; }
    private static void attack(AbstractObjectInstance enemy, PlayableEntity player) {
        ((TouchResponseAttackable) enemy).onPlayerAttack(player, null);
    }
    private static int velocity(AbstractObjectInstance instance, String method) throws Exception {
        return (int) instance.getClass().getMethod(method).invoke(instance);
    }
    private static ObjectSpawn spawn(String key, int x, int y) {
        return new ObjectSpawn(x, y, 0, 0, 0, false, y, -1, "second-strike", "second-strike:" + key);
    }

    private static final class Fixture implements AutoCloseable {
        int playerX = 320, playerY = 200;
        boolean floorPresent = true;
        int floorGapX = Integer.MIN_VALUE;
        final GameStateManager score = mock(GameStateManager.class);
        final List<Integer> sounds = new ArrayList<>();
        final PlayableEntity player = (PlayableEntity) Proxy.newProxyInstance(
                TestSecondStrikeEnemies.class.getClassLoader(), new Class<?>[]{PlayableEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCentreX" -> (short) playerX;
                    case "getCentreY" -> (short) playerY;
                    case "getDead" -> false;
                    case "getYRadius" -> (short) 19;
                    case "incrementBadnikChain" -> 100;
                    default -> throw new AssertionError("Unexpected player access: " + method.getName());
                });
        final StubObjectServices services = new StubObjectServices() {
            @Override public TerrainSurface floorSurface(int x, int y, TerrainSurface.Layer layer, TerrainSurface.Path path) {
                return new TerrainSurface(floorPresent && x != floorGapX, y, 0, 0);
            }
            @Override public GameStateManager gameState() { return score; }
            @Override public void playSfx(int soundId) { sounds.add(soundId); }
        }.withIsolatedObjectManager().withPlayerQuery(new ObjectPlayerQuery(() -> player, List::of));

        Fixture() { AbstractObjectInstance.updateCameraBounds(0, 0, 900, 500, 0); }

        AbstractObjectInstance enemy(String type, String key, int x, int y) throws Exception {
            var instance = (AbstractObjectInstance) loader.loadClass("secondstrike.enemies." + type)
                    .getConstructor(ObjectSpawn.class).newInstance(spawn(key, x, y));
            services.objectManager().addDynamicObject(instance);
            instance.setServices(services);
            return instance;
        }

        List<AbstractObjectInstance> shots() {
            return services.objectManager().getActiveObjects().stream()
                    .filter(o -> o.getClass().getSimpleName().equals("CommittedShot"))
                    .map(AbstractObjectInstance.class::cast).toList();
        }
        List<AbstractObjectInstance> guards() {
            return services.objectManager().getActiveObjects().stream()
                    .filter(o -> o.getClass().getSimpleName().equals("GuardBadnik") && !o.isDestroyed())
                    .map(AbstractObjectInstance.class::cast).toList();
        }
        long explosions() {
            return services.objectManager().getActiveObjects().stream()
                    .filter(o -> o.getClass().getSimpleName().equals("ExplosionObjectInstance")).count();
        }

        @Override public void close() { AbstractObjectInstance.resetCameraBoundsForTests(); }
    }
}
