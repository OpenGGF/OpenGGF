package com.openggf.game.sonic3k.objects;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.tests.route.InputProgram;
import com.openggf.tests.route.SidekickAudit;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Independent entry and rewind checks; no late-route success is required to reach these spots. */
@RequiresRom(SonicGame.SONIC_3K)
@Tag("slow-suite")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "openggf.aiz1.entry", matches = "true")
class TestS3kAiz1EntryMatrix {
    static Stream<Arguments> configurations() {
        return Stream.of(320, 400, 512, 640, 800)
                .flatMap(width -> Stream.of("off", "s1", "s2").map(donor -> Arguments.of(width, donor)));
    }

    @ParameterizedTest(name = "AIZ1 entry width={0} donor={1}")
    @MethodSource("configurations")
    void introReleasesInputAndEntryReplaysTwice(int width, String donor) throws Exception {
        TestS3kAiz1CompatibilityRoutes.withConfiguration(width, donor, setup -> {
            var fixture = setup.fixture();
            var input = new Aiz1IntroProgram(setup.program(), setup.preLevelRows());
            var audit = new SidekickAudit(0x100);
            int frame = 0;
            int firstMask = 0;
            // Budget only: completion is the live owner's release followed by recorded input.
            while (frame < 1800) {
                firstMask = input.next(fixture.camera().isLevelStarted());
                if (firstMask != 0) break;
                InputProgram.step(fixture, 0);
                frame++;
                audit.observe(fixture.sprite(), false, false, fixture.camera().getX() & 0xFFFF);
                assertFalse(fixture.sprite().getDead(), "P1 died during intro");
            }
            assertNotEquals(0, firstMask, "intro never admitted player input");
            assertTrue(fixture.camera().isLevelStarted());
            assertFalse(fixture.sprite().isObjectControlled());
            assertEquals(1, com.openggf.game.GameServices.sprites().getSidekicks().size());
            assertTrue(audit.identityOrderPreserved());
            assertTrue(audit.controllerEveryFrame());
            assertTrue(audit.leaderChainEveryFrame());
            assertTrue(audit.respawnedAfterEveryDeath(), audit.deathEvidence());

            var registry = fixture.gameplayMode().getRewindRegistry();
            var before = registry.capture();
            List<Integer> masks = new ArrayList<>();
            masks.add(firstMask);
            for (int i = 1; i < 30; i++) masks.add(input.next(fixture.camera().isLevelStarted()));
            stepAndAudit(fixture, masks, audit);
            assertFalse(fixture.sprite().getDead());
            assertTrue(fixture.sprite().getXSpeed() > 0, "released input must produce forward movement");
            var expected = registry.capture();
            for (int cycle = 0; cycle < 2; cycle++) {
                registry.restore(before);
                fixture.runner().primeInputState(new Bk2FrameInput(0, 0, 0, false, ""));
                TestS3kAiz1RouteRewind.assertSnapshotsMatch(before, registry.capture(),
                        "entry restore " + width + "/" + donor + " cycle=" + cycle, frame, 30);
                stepAndAudit(fixture, masks, new SidekickAudit(0x100));
                TestS3kAiz1RouteRewind.assertSnapshotsMatch(expected, registry.capture(),
                        "entry replay " + width + "/" + donor + " cycle=" + cycle, frame, 30);
            }
            System.out.printf("AIZENTRY width=%d donor=%s firstInputFrame=%d held=%d replay=30 cycles=2%n",
                    width, donor, frame + 1, input.heldFrames());
        });
    }

    private static void stepAndAudit(com.openggf.tests.HeadlessTestFixture fixture,
                                     List<Integer> masks, SidekickAudit audit) {
        audit.observe(fixture.sprite(), false, false, fixture.camera().getX() & 0xFFFF);
        for (int mask : masks) {
            InputProgram.step(fixture, mask);
            assertFalse(fixture.sprite().getDead(), "P1 died during entry replay");
            assertEquals(1, com.openggf.game.GameServices.sprites().getSidekicks().size());
            audit.observe(fixture.sprite(), false, false, fixture.camera().getX() & 0xFFFF);
        }
        assertTrue(audit.identityOrderPreserved());
        assertTrue(audit.controllerEveryFrame());
        assertTrue(audit.leaderChainEveryFrame());
        assertTrue(audit.respawnedAfterEveryDeath(), audit.deathEvidence());
    }
}
