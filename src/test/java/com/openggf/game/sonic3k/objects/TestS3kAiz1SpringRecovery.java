package com.openggf.game.sonic3k.objects;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.route.InputProgram;
import com.openggf.tests.route.SidekickAudit;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Local recovery obligation, independent of the unresolved complete-route assertion. */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "openggf.aiz1.recovery", matches = "true")
class TestS3kAiz1SpringRecovery {
    private static final int WINDOW = 300;

    @ParameterizedTest(name = "AIZ1 opposing spring width={0} donor={1}")
    @CsvSource({"640,off", "800,off", "320,s1"})
    void liveSpringJumpCrossesAndReplaysWhileWalkingIsRejected(int width, String donor) throws Exception {
        TestS3kAiz1CompatibilityRoutes.withConfiguration(width, donor, setup -> {
            var fixture = setup.fixture();
            var input = new Aiz1IntroProgram(setup.program(), setup.preLevelRows());
            int frame = 0;
            int previousMask = 0;
            while (!input.exhausted() && frame < InputProgram.frames(setup.program()) + 300) {
                previousMask = input.next(fixture.camera().isLevelStarted());
                InputProgram.step(fixture, previousMask);
                frame++;
                assertFalse(fixture.sprite().getDead(), "P1 died reaching recovery entry");
                assertEquals(0, GameServices.level().getCurrentAct());
            }
            assertTrue(input.exhausted(), "intro gate exhausted its bounded wait");
            var player = fixture.sprite();
            var springs = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(Sonic3kSpringObjectInstance.class::isInstance)
                    .map(Sonic3kSpringObjectInstance.class::cast)
                    // Obj_Spring's subtype dispatch: bits 4..6 == 1 selects horizontal.
                    .filter(TestS3kAiz1SpringRecovery::opposesRightwardTravel)
                    .filter(o -> o.getX() > (player.getCentreX() & 0xFFFF))
                    .filter(o -> Math.abs(o.getY() - (player.getCentreY() & 0xFFFF)) < 128)
                    .sorted(Comparator.comparingInt(Sonic3kSpringObjectInstance::getX)).toList();
            assertEquals(2, springs.size(), "expected two live opposing springs on this approach");
            var spring = springs.getFirst();
            var lastSpring = springs.getLast();
            int chainExit = lastSpring.getX() + lastSpring.getSolidParams().halfWidth();
            int springX = spring.getX();
            int springY = spring.getY();
            int halfWidth = spring.getSolidParams().halfWidth();
            // Authored approach margin, scaled from the live collision width; not ROM physics.
            int approach = 4 * halfWidth;
            int approachFrames = 0;
            while (((player.getCentreX() & 0xFFFF) <= springX - approach || player.getAir())
                    && approachFrames++ < 300) {
                InputProgram.step(fixture, 8);
                previousMask = 8;
                frame++;
                assertFalse(player.getDead());
            }
            assertFalse(player.getAir());
            assertTrue((player.getCentreX() & 0xFFFF) >= springX - approach);
            assertTrue((player.getCentreX() & 0xFFFF) < springX - halfWidth,
                    "approach already crossed the spring contact volume");
            assertTrue(Math.abs(springY - (player.getCentreY() & 0xFFFF)) < 64,
                    "spring is not on the live approach surface");
            var registry = fixture.gameplayMode().getRewindRegistry();
            var before = registry.capture();
            int crossedAt = -1;
            boolean rejectedLeft = false;
            // Obj_Spring_Strengths: -$1000 red / -$A00 yellow. Require the
            // characteristic launch at this spring, not merely a later reversal.
            int rebound = (spring.getSpawn().subtype() & 2) == 0 ? -0x1000 : -0xA00;
            for (int i = 0; i < WINDOW; i++) {
                int beforeX = player.getCentreX() & 0xFFFF;
                int beforeSpeed = player.getXSpeed();
                InputProgram.step(fixture, 8);
                rejectedLeft |= beforeSpeed >= 0 && player.getXSpeed() == rebound
                        && Math.abs(beforeX - springX) <= 2 * halfWidth;
                assertFalse(player.getDead());
                if ((player.getCentreX() & 0xFFFF) > springX + halfWidth) crossedAt = i;
            }
            assertTrue(rejectedLeft, "no-jump control did not encounter the opposing spring");
            assertEquals(-1, crossedAt, "walking alone already crossed; recovery premise is invalid");

            registry.restore(before);
            fixture.runner().primeInputState(new Bk2FrameInput(0, previousMask, 0, false, ""));
            TestS3kAiz1RouteRewind.assertSnapshotsMatch(before, registry.capture(),
                    "spring control restore", frame, WINDOW);
            List<Integer> masks = new ArrayList<>();
            boolean jumpHeld = false;
            crossedAt = -1;
            var audit = new SidekickAudit(0x100);
            audit.observe(fixture.sprite(), false, false, fixture.camera().getX() & 0xFFFF);
            for (int i = 0; i < WINDOW; i++) {
                // Re-evaluate the live approach after landing or an opposing bounce.
                // Hold a jump through flight; release for one grounded tick before
                // requesting another edge. No engine state is changed by this policy.
                var current = fixture.sprite();
                if (!current.getAir() && jumpHeld) jumpHeld = false;
                else if (!current.getAir() && GameServices.level().getObjectManager().getActiveObjects().stream()
                        .filter(Sonic3kSpringObjectInstance.class::isInstance)
                        .map(Sonic3kSpringObjectInstance.class::cast)
                        .anyMatch(o -> opposesRightwardTravel(o)
                                && o.getX() > (current.getCentreX() & 0xFFFF)
                                && o.getX() - (current.getCentreX() & 0xFFFF) < 4 * o.getSolidParams().halfWidth()
                                && Math.abs(o.getY() - (current.getCentreY() & 0xFFFF)) < 64)) jumpHeld = true;
                int mask = 8 | (jumpHeld ? 16 : 0);
                masks.add(mask);
                InputProgram.step(fixture, mask);
                observe(fixture, audit);
                if ((fixture.sprite().getCentreX() & 0xFFFF) > chainExit && crossedAt < 0) {
                    crossedAt = i;
                }
            }
            assertTrue(crossedAt >= 0, "live spring steering never crossed both collision volumes");
            assertTrue((fixture.sprite().getCentreX() & 0xFFFF) > chainExit,
                    "P1 fell back inside the spring chain: x=" + (fixture.sprite().getCentreX() & 0xFFFF));
            assertAudit(audit);
            var expected = registry.capture();
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(before);
                fixture.runner().primeInputState(new Bk2FrameInput(0, previousMask, 0, false, ""));
                TestS3kAiz1RouteRewind.assertSnapshotsMatch(before, registry.capture(),
                        "spring restore cycle " + cycle, frame, WINDOW);
                var replayAudit = new SidekickAudit(0x100);
                replayAudit.observe(fixture.sprite(), false, false, fixture.camera().getX() & 0xFFFF);
                for (int mask : masks) {
                    InputProgram.step(fixture, mask);
                    observe(fixture, replayAudit);
                }
                assertAudit(replayAudit);
                TestS3kAiz1RouteRewind.assertSnapshotsMatch(expected, registry.capture(),
                        "spring replay cycle " + cycle, frame, WINDOW);
            }
            System.out.printf("AIZSPRING width=%d donor=%s entry=%d spring=%04X,%04X chainExit=%04X crossed=%d replay=%d cycles=2%n",
                    width, donor, frame, springX, springY, chainExit, crossedAt + 1, WINDOW);
        });
    }

    private static boolean opposesRightwardTravel(Sonic3kSpringObjectInstance spring) {
        // Obj_Spring horizontal dispatch and X-flipped launch toward the left.
        return ((spring.getSpawn().subtype() >> 3) & 0xE) == 2
                && (spring.getSpawn().renderFlags() & 1) != 0;
    }

    private static void observe(HeadlessTestFixture fixture, SidekickAudit audit) {
        assertFalse(fixture.sprite().getDead(), "P1 died crossing the spring");
        assertEquals(0, GameServices.level().getCurrentAct(), "local window crossed a load");
        assertEquals(1, GameServices.sprites().getSidekicks().size());
        audit.observe(fixture.sprite(), false, false, fixture.camera().getX() & 0xFFFF);
    }

    private static void assertAudit(SidekickAudit audit) {
        assertTrue(audit.identityOrderPreserved());
        assertTrue(audit.controllerEveryFrame());
        assertTrue(audit.leaderChainEveryFrame());
        assertTrue(audit.respawnedAfterEveryDeath(), audit.deathEvidence());
    }
}
