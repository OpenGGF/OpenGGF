package com.openggf.game.sonic3k;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent declared S3K fixed-countdown component, not a replay of HCZ row3475.
 * Native044 AirCountdown_ReduceAir writes anim=$17 when air underflows
 * (sonic3k.asm:33580-33606); loc_1857C reasserts it throughout the $78 pre-death
 * interval. The next Tails routine-$C pass animates through sub_15842.
 *
 * Public Blank animation and movement-suppressing object-control are explicit
 * inputs. ROM-backed HCZ geometry and ordinary fixture frames establish water
 * state and consume the actual default air supply. No air/timer/routine/slot,
 * clock, RNG, visibility, mapping or private state is assigned. No recorded
 * comparison state or callback is installed. These cases do not identify the
 * actual canonical engine writer, test a route, or claim whole-world rewind.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFixedCountdownDrownAnimationOwnership {
    private static final int MAX_COUNTDOWN_STEPS = 2_000; // fail-closed admission bound, not an expected clock
    private static final int MAX_FIRST_AIR_STEPS = 120;
    private static final short COMPONENT_X = 0x1440;
    private static final short COMPONENT_Y = 0x0800;

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void ordinaryFixedCountdownConsumesAirWithoutTakingTheDeclaredBlankAnimation() {
        var inlet = underwaterBlankComponent();
        var tails = inlet.tails();
        int remaining = tails.getDrowningController().getRemainingAir();
        for (int step = 0; step < MAX_FIRST_AIR_STEPS && remaining == inlet.countdownBaseline(); step++) {
            ordinaryFrame(inlet);
            remaining = tails.getDrowningController().getRemainingAir();
        }
        assertTrue(remaining >= 0 && remaining < inlet.countdownBaseline(),
                "prerequisite: a NEW decrement after the actual post-setup countdown baseline");
        assertFalse(tails.isDrowningPreDeath());
        assertFalse(tails.getDead());
        assertEquals(Sonic3kAnimationIds.BLANK.id(), tails.getAnimationId());
        assertEquals(inlet.blankMapping(), tails.getMappingFrame());
    }

    @Test
    void naturalAirUnderflowPublishesDrownBeforeTheNextOrdinaryAnimationPass() {
        var inlet = underwaterBlankComponent();
        reachNaturalDrowning(inlet);
        var tails = inlet.tails();
        int rawAtUnderflow = tails.getAnimationId();
        int mappingAtUnderflow = tails.getMappingFrame();
        // The just-completed frame ran the player animator BEFORE fixed objects.
        // Its mapping still belongs to the declared Blank input; raw anim must
        // already be the countdown owner's later write, independently of fallback.
        ordinaryFrame(inlet);
        int rawAfterNextPass = tails.getAnimationId();
        int mappingAfterNextPass = tails.getMappingFrame();
        assertAll("fixed owner raw-byte publication and later real ROM-script mapping",
                () -> assertEquals(Sonic3kAnimationIds.DROWN.id(), rawAtUnderflow),
                () -> assertEquals(inlet.blankMapping(), mappingAtUnderflow),
                () -> assertEquals(Sonic3kAnimationIds.DROWN.id(), rawAfterNextPass),
                () -> assertEquals(inlet.drownMapping(), mappingAfterNextPass));
    }

    @Test
    void activePreDeathFixedOwnerReassertsDrownAfterADeclaredLaterBlankWriter() {
        var inlet = underwaterBlankComponent();
        reachNaturalDrowning(inlet);
        var tails = inlet.tails();
        // A separate declared ordinary public writer, not an attribution of the
        // canonical writer. No timer reset, direct Drown write or forced mapping.
        tails.setAnimationId(Sonic3kAnimationIds.BLANK);
        assertEquals(Sonic3kAnimationIds.BLANK.id(), tails.getAnimationId());
        assertTrue(tails.isDrowningPreDeath());
        ordinaryFrame(inlet);
        int reassertedRaw = tails.getAnimationId();
        int mappingAfterBlankPass = tails.getMappingFrame();
        // This following player pass must use the actual Drown script that the
        // preceding fixed pass selected. Never require $9C in the overwrite pass.
        ordinaryFrame(inlet);
        int rawAfterFollowingPass = tails.getAnimationId();
        int mappingAfterFollowingPass = tails.getMappingFrame();
        assertAll("loc_1857C owns raw anim while pre-death remains active",
                () -> assertEquals(Sonic3kAnimationIds.DROWN.id(), reassertedRaw),
                () -> assertEquals(inlet.blankMapping(), mappingAfterBlankPass),
                () -> assertEquals(Sonic3kAnimationIds.DROWN.id(), rawAfterFollowingPass),
                () -> assertEquals(inlet.drownMapping(), mappingAfterFollowingPass));
    }

    // BEGIN R78 independent public-P1 companion (not exercised by R77).
    @Test
    void primaryFixedCountdownPublishesAndReassertsDrownUsingItsOwnRomScript() {
        var inlet = underwaterBlankComponent();
        var main = inlet.fixture().sprite();
        assertNotSame(inlet.tails(), main);
        assertSame(main, GameServices.sprites().getMainPlayable());
        assertFalse(main.isCpuControlled());
        assertTrue(main.isInWater());
        assertTrue(main.isObjectControlSuppressesMovement());
        assertFalse(main.isDrowningPreDeath());
        assertFalse(main.getDead());
        assertEquals(-1, main.getForcedAnimationId());
        assertNotNull(main.getDrowningController());
        assertNotNull(main.getAnimationSet());
        var blank = main.getAnimationSet().getScript(Sonic3kAnimationIds.BLANK.id());
        var drown = main.getAnimationSet().getScript(Sonic3kAnimationIds.DROWN.id());
        assertNotNull(blank);
        assertNotNull(drown);
        assertFalse(blank.frames().isEmpty());
        assertFalse(drown.frames().isEmpty());
        // These are the actual P1 ROM scripts, never Tails' $9C mapping oracle.
        int blankMapping = blank.frames().getFirst();
        int drownMapping = drown.frames().getFirst();
        main.setAnimationId(Sonic3kAnimationIds.BLANK);
        ordinaryFrame(inlet);
        assertEquals(Sonic3kAnimationIds.BLANK.id(), main.getAnimationId());
        assertEquals(blankMapping, main.getMappingFrame());
        assertFalse(main.isDrowningPreDeath());
        int baseline = main.getDrowningController().getRemainingAir();
        assertTrue(baseline > 0 && baseline <= 30);
        int steps = 0;
        while (!main.isDrowningPreDeath() && steps < MAX_COUNTDOWN_STEPS) {
            assertEquals(Sonic3kAnimationIds.BLANK.id(), main.getAnimationId(),
                    "prerequisite: declared P1 byte persists until natural underflow");
            ordinaryFrame(inlet);
            steps++;
        }
        assertTrue(main.isDrowningPreDeath(), "prerequisite: real P1 fixed countdown underflows");
        assertFalse(main.getDead());
        assertTrue(main.isInWater());
        assertTrue(main.isObjectControlSuppressesMovement());
        assertEquals(-1, main.getForcedAnimationId());
        assertEquals(0, main.getXSpeed());
        assertEquals(0, main.getYSpeed());
        assertEquals(0, main.getGSpeed());
        int rawAtUnderflow = main.getAnimationId();
        int mappingAtUnderflow = main.getMappingFrame();
        ordinaryFrame(inlet);
        int rawAfterNextPass = main.getAnimationId();
        int mappingAfterNextPass = main.getMappingFrame();
        main.setAnimationId(Sonic3kAnimationIds.BLANK);
        assertEquals(Sonic3kAnimationIds.BLANK.id(), main.getAnimationId());
        assertTrue(main.isDrowningPreDeath());
        ordinaryFrame(inlet);
        int reassertedRaw = main.getAnimationId();
        int mappingAfterBlankPass = main.getMappingFrame();
        ordinaryFrame(inlet);
        int rawAfterFollowingPass = main.getAnimationId();
        int mappingAfterFollowingPass = main.getMappingFrame();
        assertSame(main, GameServices.sprites().getMainPlayable());
        assertAll("native fixed P1 owner raw byte and subsequent P1 ROM mapping",
                () -> assertEquals(Sonic3kAnimationIds.DROWN.id(), rawAtUnderflow),
                () -> assertEquals(blankMapping, mappingAtUnderflow),
                () -> assertEquals(Sonic3kAnimationIds.DROWN.id(), rawAfterNextPass),
                () -> assertEquals(drownMapping, mappingAfterNextPass),
                () -> assertEquals(Sonic3kAnimationIds.DROWN.id(), reassertedRaw),
                () -> assertEquals(blankMapping, mappingAfterBlankPass),
                () -> assertEquals(Sonic3kAnimationIds.DROWN.id(), rawAfterFollowingPass),
                () -> assertEquals(drownMapping, mappingAfterFollowingPass));
    }
    // END R78 independent public-P1 companion.

    private static Inlet underwaterBlankComponent() {
        // Declared component roster/geometry, independent of any movie row.
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 1)
                .startPosition(COMPONENT_X, COMPONENT_Y)
                .startPositionIsCentre()
                .build();
        var main = fixture.sprite();
        var sidekicks = GameServices.sprites().getRegisteredSidekicks();
        assertEquals(1, sidekicks.size(), "real Sonic+Tails production roster");
        var tails = assertInstanceOf(Tails.class, sidekicks.getFirst());
        assertSame(main, GameServices.sprites().getMainPlayable());
        assertTrue(tails.isCpuControlled());
        assertSame(main, tails.getCpuController().getLeader());
        assertNotNull(tails.getAnimationSet());
        var blank = tails.getAnimationSet().getScript(Sonic3kAnimationIds.BLANK.id());
        var drown = tails.getAnimationSet().getScript(Sonic3kAnimationIds.DROWN.id());
        assertNotNull(blank);
        assertNotNull(drown);
        // Independent native AniTails1C/AniTails17 ROM-script oracle.
        assertEquals(0x77, blank.delay());
        assertEquals(List.of(0), blank.frames());
        assertEquals(0x2F, drown.delay());
        assertEquals(List.of(0x9C), drown.frames());
        assertNotNull(tails.getDrowningController());
        int constructorAir = tails.getDrowningController().getRemainingAir();
        assertEquals(30, constructorAir, "actual public constructor/init default BEFORE any ordinary bootstrap");
        assertFalse(tails.isDrowningDeath());
        assertFalse(tails.getDead());
        var cpu = tails.getCpuController();
        assertNotNull(cpu);
        // HCZ does not preserve the assembled spawn state. The real first INIT
        // dispatch performs placement and clears object-control, then returns
        // NORMAL. Let that production pass finish BEFORE installing the input.
        if (cpu.getState() == SidekickCpuController.State.INIT) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertSame(tails, GameServices.sprites().getRegisteredSidekicks().getFirst());
        assertSame(cpu, tails.getCpuController());
        assertEquals(SidekickCpuController.State.NORMAL, cpu.getState(),
                "prerequisite: ordinary CPU INIT completed without a seeded state or retry");
        assertFalse(tails.isDrowningDeath());
        assertFalse(tails.getDead());
        assertEquals(-1, tails.getForcedAnimationId(), "HCZ act1 component has no forced intro to disable");
        int water = GameServices.water().getGameplayWaterLevelY(Sonic3kZoneIds.ZONE_HCZ, 1);
        assertTrue(water > 0);
        assertTrue((main.getCentreY() & 0xFFFF) > water);
        assertTrue((tails.getCentreY() & 0xFFFF) > water,
                "declared actual P2 geometry lies below the loaded production waterline");
        // Holding both real participants avoids introducing a second moving
        // geometry/input owner while the countdown runs. These are public
        // declared object-control inputs, not a fake renderer or visibility bit.
        ObjectControlState.nativeBit7FullControl().applyTo(main);
        ObjectControlState.nativeBit7FullControl().applyTo(tails);
        tails.setAnimationId(Sonic3kAnimationIds.BLANK);
        assertEquals(-1, tails.getForcedAnimationId(), "no unrelated forced-animation owner in this component");
        fixture.stepFrame(false, false, false, false, false); // water/Blank player pass, then real fixed countdown
        assertSame(tails, GameServices.sprites().getRegisteredSidekicks().getFirst());
        assertTrue(tails.isInWater());
        assertFalse(tails.isDrowningPreDeath());
        assertTrue(tails.isObjectControlSuppressesMovement());
        assertEquals(-1, tails.getForcedAnimationId());
        assertEquals(Sonic3kAnimationIds.BLANK.id(), tails.getAnimationId());
        assertEquals(blank.frames().getFirst().intValue(), tails.getMappingFrame());
        // Bootstrap/first Blank frame may already have consumed air. This fresh
        // positive baseline belongs to the completed public setup; never reset it.
        int countdownBaseline = tails.getDrowningController().getRemainingAir();
        assertTrue(countdownBaseline > 0 && countdownBaseline <= constructorAir);
        return new Inlet(fixture, tails, countdownBaseline,
                blank.frames().getFirst(), drown.frames().getFirst());
    }

    private static void reachNaturalDrowning(Inlet inlet) {
        var tails = inlet.tails();
        int steps = 0;
        while (!tails.isDrowningPreDeath() && steps < MAX_COUNTDOWN_STEPS) {
            assertEquals(Sonic3kAnimationIds.BLANK.id(), tails.getAnimationId(),
                    "prerequisite: declared byte persists until the actual underflow owner acts");
            ordinaryFrame(inlet);
            steps++;
        }
        assertTrue(tails.isDrowningPreDeath(), "prerequisite: ordinary fixed countdown reaches natural underflow");
        assertFalse(tails.getDead());
        assertTrue(tails.isInWater());
        assertTrue(tails.isObjectControlSuppressesMovement());
        assertEquals(0, tails.getXSpeed());
        assertEquals(0, tails.getYSpeed());
        assertEquals(0, tails.getGSpeed());
    }

    private static void ordinaryFrame(Inlet inlet) {
        inlet.fixture().stepFrame(false, false, false, false, false);
        assertSame(inlet.tails(), GameServices.sprites().getRegisteredSidekicks().getFirst(),
                "prerequisite: the same real registered P2 remains the owner");
        assertTrue(inlet.tails().isInWater(), "prerequisite: the ordinary water path retains the declared inlet");
        assertTrue(inlet.tails().isObjectControlSuppressesMovement());
        assertEquals(-1, inlet.tails().getForcedAnimationId());
    }

    private record Inlet(HeadlessTestFixture fixture, AbstractPlayableSprite tails,
                         int countdownBaseline, int blankMapping, int drownMapping) {}
}
