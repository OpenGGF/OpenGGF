package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot.Entry;
import com.openggf.game.sonic3k.Sonic3kLevelTitlePlcService;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.Lbz1RobotnikEventController;
import com.openggf.game.sonic3k.objects.LbzMinibossBoxInstance;
import com.openggf.game.sonic3k.objects.LbzMinibossBoxKnuxInstance;
import com.openggf.game.sonic3k.objects.LbzMinibossBoxRig;
import com.openggf.level.objects.MultiBucketRenderable;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLbz1BoxRestorePlc {
    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sonicSubtypeCAppendsMonitorsSpikesSpringsThenBubbles(boolean carriedBox) throws Exception {
        var fixture = inlet("sonic");
        var objects = GameServices.level().getObjectManager();
        var plc = queue();
        plc.appendRaw(0x083D74, Sonic3kLevelTitlePlcService.RecipePurpose.EVENT_READINESS);
        var before = plc.capture();

        int x = 0x3E80;
        MultiBucketRenderable owner;
        if (carriedBox) {
            var robotnik = objects.createDynamicObject(() -> new Lbz1RobotnikEventController(
                    spawn(x, 0x160, Sonic3kObjectIds.LBZ1_ROBOTNIK)));
            robotnik.forceMinibossBoxReleaseForTest(x, 0x160);
            owner = robotnik;
        } else {
            var box = objects.createDynamicObject(() -> new LbzMinibossBoxInstance(
                    spawn(x, 0x160, Sonic3kObjectIds.LBZ_MINIBOSS_BOX)));
            fixture.sprite().setCentreX((short) x);
            owner = box;
        }
        fixture.camera().setX((short) 0x3E80);
        for (int frame = 0; frame < 0x120; frame++) {
            // Robotnik's already released rig ticks before the player-dependent ship routine.
            owner.update(frame, carriedBox ? null : fixture.sprite());
        }
        assertLingering(owner);
        assertEquals(before, plc.capture(), "No request while the panels remain in range");

        // At this camera position the two right-hand panels are out of range.
        // Subtype $C is on the left and remains exactly at the inclusive $280 limit.
        fixture.camera().setX((short) 0x3C00);
        owner.update(0, null);
        assertLingering(owner);
        assertEquals(before, plc.capture(), "Non-$C retirement leaves the FIFO alone");

        var expected = appended(before, List.of(
                entry(0x190F4A, 0x9880), entry(0x1927FE, 0x9280), entry(0x191B46, 0x8B80)));
        fixture.camera().setX((short) 0x4000);
        owner.update(0, null);
        assertEquals(expected, plc.capture(), "Subtype $C appends both original lists in order");
        assertNoLingering(owner);
        owner.update(0, null);
        assertEquals(expected, plc.capture(), "An already retired panel cannot resubmit");
    }

    @Test
    void knucklesAppendsOnlyBubblesForEachPanelAndReplaysAfterRewind() throws Exception {
        var fixture = inlet("knuckles");
        var objects = GameServices.level().getObjectManager();
        var plc = queue();
        plc.appendRaw(0x083D74, Sonic3kLevelTitlePlcService.RecipePurpose.EVENT_READINESS);
        var before = plc.capture();

        var box = objects.createDynamicObject(() -> new LbzMinibossBoxKnuxInstance(
                spawn(0x3C40, 0x936, Sonic3kObjectIds.LBZ_MINIBOSS_BOX_KNUX)));
        fixture.camera().setX((short) 0x3BC0);
        fixture.camera().setY((short) 0x936);
        for (int frame = 0; frame < 0x180; frame++) {
            box.update(frame, fixture.sprite());
        }
        assertLingering(box);
        assertEquals(before, plc.capture(), "Neither rig submits while its panels remain in range");

        var registry = fixture.gameplayMode().getRewindRegistry();
        var inRange = registry.capture();
        var expected = appended(before, Collections.nCopies(8, entry(0x191B46, 0x8B80)));
        fixture.camera().setX((short) 0x3D80);
        box.update(0, fixture.sprite());
        assertEquals(expected, plc.capture(), "Knuckles takes the one-entry list for all four panels per rig");
        assertNoLingering(box);
        box.update(0, fixture.sprite());
        assertEquals(expected, plc.capture(), "Retired panels stay retired on subsequent ticks");

        registry.restore(inRange);
        assertEquals(before, plc.capture(), "Rewind restores the FIFO before retirement");
        var restored = objects.activeObjectsOfType(LbzMinibossBoxKnuxInstance.class).getFirst();
        assertLingering(restored);
        fixture.camera().setX((short) 0x3D80);
        restored.update(0, fixture.sprite());
        assertEquals(expected, plc.capture(), "Replaying the cull reproduces the full FIFO");
    }

    private static HeadlessTestFixture inlet(String characterCode) {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, characterCode);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        return HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 0).build();
    }

    private static Sonic3kLevelTitlePlcService queue() {
        return GameServices.level().getObjectManager().getObjectServices()
                .gameService(Sonic3kLevelTitlePlcService.class);
    }

    private static ObjectSpawn spawn(int x, int y, int id) {
        return new ObjectSpawn(x, y, id, 0, 0, false, 0);
    }

    private static void assertLingering(MultiBucketRenderable owner) {
        assertTrue(hasLingeringPanels(owner), "Positive control: the late panel bucket is active");
    }

    private static void assertNoLingering(MultiBucketRenderable owner) {
        assertFalse(hasLingeringPanels(owner), "All late panels retired");
    }

    private static boolean hasLingeringPanels(MultiBucketRenderable owner) {
        for (int bucket : owner.extraRenderBuckets()) {
            if (bucket == LbzMinibossBoxRig.LINGER_PRIORITY_BUCKET) {
                return true;
            }
        }
        return false;
    }

    private static Entry entry(int source, int destinationBytes) throws Exception {
        var rom = GameServices.level().getObjectManager().getObjectServices().romReader();
        int patterns = rom.readU16BE(source) & 0x7FFF;
        return new Entry(source, destinationBytes / 32, patterns, patterns);
    }

    private static NemesisPlcQueueSnapshot appended(NemesisPlcQueueSnapshot before, List<Entry> extra) {
        var entries = new ArrayList<>(before.queuedEntries());
        entries.addAll(extra);
        return new NemesisPlcQueueSnapshot(before.activeEntry(), entries);
    }
}
