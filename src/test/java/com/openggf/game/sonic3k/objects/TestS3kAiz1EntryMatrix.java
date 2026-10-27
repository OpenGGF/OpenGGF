package com.openggf.game.sonic3k.objects;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.tests.route.InputProgram;
import com.openggf.tests.route.SidekickAudit;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.tests.SozAcceptanceConfigurations;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Independent entry and rewind checks; no late-route success is required to reach these spots. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAiz1EntryMatrix {
    static Stream<Arguments> configurations() {
        return Stream.of(WidescreenAspect.values())
                .flatMap(aspect -> Stream.of("off", "s1", "s2").map(donor -> Arguments.of(aspect, donor)));
    }

    static Stream<Arguments> legacyConfigurations() {
        return Stream.of(512, 640).flatMap(width -> Stream.of("off", "s1", "s2")
                .map(donor -> Arguments.of(width, donor)));
    }

    @ParameterizedTest(name = "AIZ1 production preset entry {0} donor={1}")
    @MethodSource("configurations")
    @Tag("exhaustive-audit")
    void introReleasesInputAndEntryReplaysTwice(WidescreenAspect aspect, String donor) throws Exception {
        verifyPresetEntry(aspect, donor);
    }

    @ParameterizedTest(name = "AIZ1 representative preset entry {0} donor={1}")
    @CsvSource({"NATIVE_4_3,off", "WIDE_16_10,s1", "WIDE_16_9,s2", "ULTRA_21_9,off", "SUPER_32_9,s1"})
    void representativePresetsReleaseInputAndReplayTwice(WidescreenAspect aspect, String donor) throws Exception {
        verifyPresetEntry(aspect, donor);
    }

    private static void verifyPresetEntry(WidescreenAspect aspect, String donor) throws Exception {
        TestS3kAiz1CompatibilityRoutes.withConfiguration(aspect, donor,
                setup -> verifyEntry(setup, aspect.pixelWidth(), donor, aspect));
    }

    @ParameterizedTest(name = "AIZ1 legacy custom geometry {0}px donor={1}")
    @MethodSource("legacyConfigurations")
    @Tag("exhaustive-audit")
    void legacyCustomWidthRetainsIntroAndEntryReplay(int width, String donor) throws Exception {
        verifyLegacyEntry(width, donor);
    }

    @ParameterizedTest(name = "AIZ1 representative legacy geometry {0}px donor={1}")
    @CsvSource({"512,s1", "640,s2"})
    void representativeLegacyWidthsRetainIntroAndEntryReplay(int width, String donor) throws Exception {
        verifyLegacyEntry(width, donor);
    }

    private static void verifyLegacyEntry(int width, String donor) throws Exception {
        TestS3kAiz1CompatibilityRoutes.withConfiguration(width, donor,
                setup -> verifyEntry(setup, width, donor, null));
    }

    private static void verifyEntry(TestS3kAiz1RoutePilot.RouteSetup setup, int width, String donor,
                                WidescreenAspect aspect) throws Exception {
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
            if (aspect != null) {
                SozAcceptanceConfigurations.assertDisplay(aspect);
                SozAcceptanceConfigurations.assertUsableTeam(donor);
                assertSame(com.openggf.game.GameServices.sprites().getMainPlayable(),
                        fixture.camera().getFocusedSprite(), "immediate restore retains current main focus");
            }
            stepAndAudit(fixture, masks, new SidekickAudit(0x100));
            TestS3kAiz1RouteRewind.assertSnapshotsMatch(expected, registry.capture(),
                    "entry replay " + width + "/" + donor + " cycle=" + cycle, frame, 30);
            if (aspect != null) assertSame(com.openggf.game.GameServices.sprites().getMainPlayable(),
                    fixture.camera().getFocusedSprite(), "each replay retains current main focus");
        }
        if (aspect != null) {
            SozAcceptanceConfigurations.assertDisplay(aspect);
            SozAcceptanceConfigurations.assertUsableTeam(donor);
            assertSame(com.openggf.game.GameServices.sprites().getMainPlayable(),
                    fixture.camera().getFocusedSprite(), "released current main owns camera focus");
        }
        System.out.printf("AIZENTRY width=%d donor=%s firstInputFrame=%d held=%d replay=30 cycles=2%n",
                width, donor, frame + 1, input.heldFrames());
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
