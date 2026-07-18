package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused locked-on-ROM coverage for {@code Obj_CutsceneButton} subtypes 4 and 6. */
@RequiresRom(SonicGame.SONIC_3K)
class TestCnz2CutsceneButtonInstance {

    @AfterEach
    void tearDown() {
        CutsceneKnucklesCnz2AInstance.clearActiveInstanceForTests();
        CutsceneKnucklesCnz2BInstance.clearActiveInstanceForTests();
        SessionManager.clear();
    }

    @Test
    void subtype4PressesFromRomHalfOpenProximityRangeWithoutImpactHandshake() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();
        CutsceneKnucklesCnz2AInstance knuckles = new CutsceneKnucklesCnz2AInstance(
                new ObjectSpawn(0x1E00, 0x033C,
                        Sonic3kObjectIds.CUTSCENE_KNUCKLES, 12, 0, false, 0));
        CutsceneKnucklesCnz2AInstance.setActiveInstanceForTests(knuckles);
        Cnz2CutsceneButtonInstance button = new Cnz2CutsceneButtonInstance(
                new ObjectSpawn(0x1E00, 0x0338,
                        Sonic3kObjectIds.CUTSCENE_BUTTON, 4, 0, false, 0));
        button.setServices(TestEnvironment.objectServices());

        button.update(0, fixture.sprite());

        assertTrue(button.isPressedForTest(),
                "loc_65C04 uses Check_InMyRange alone; there is no second-landing handshake");
        assertEquals(0x0350,
                GameServices.water().getWaterLevelTarget(Sonic3kZoneIds.ZONE_CNZ, 1));
    }

    @Test
    void proximityBoxIncludesNegativeEdgeAndExcludesPositiveEdge() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();
        Cnz2CutsceneButtonInstance button = new Cnz2CutsceneButtonInstance(
                new ObjectSpawn(0x1E00, 0x0338,
                        Sonic3kObjectIds.CUTSCENE_BUTTON, 4, 0, false, 0));
        button.setServices(TestEnvironment.objectServices());
        CutsceneKnucklesCnz2AInstance.setActiveInstanceForTests(
                new CutsceneKnucklesCnz2AInstance(new ObjectSpawn(
                        0x1E30, 0x033C,
                        Sonic3kObjectIds.CUTSCENE_KNUCKLES, 12, 0, false, 0)));

        button.update(0, fixture.sprite());

        assertFalse(button.isPressedForTest(), "dx=$30 is outside word_65C48's half-open range");

        CutsceneKnucklesCnz2AInstance.setActiveInstanceForTests(
                new CutsceneKnucklesCnz2AInstance(new ObjectSpawn(
                        0x1DE8, 0x033C,
                        Sonic3kObjectIds.CUTSCENE_KNUCKLES, 12, 0, false, 0)));
        button.update(1, fixture.sprite());

        assertTrue(button.isPressedForTest(), "dx=-$18 is included by Check_InMyRange");
    }

    @Test
    void subtype6MutatesNativeTubeColumnThroughRuntimePipelineAndStaysPressed() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();
        GameServices.camera().setX((short) 0x45C0);
        GameServices.camera().setY((short) 0x0720);
        CutsceneKnucklesCnz2BInstance knuckles = new CutsceneKnucklesCnz2BInstance(
                new ObjectSpawn(0x4780, 0x072C,
                        Sonic3kObjectIds.CUTSCENE_KNUCKLES, 16, 0, false, 0));
        knuckles.setServices(TestEnvironment.objectServices());
        knuckles.update(0, fixture.sprite());
        Cnz2CutsceneButtonInstance button = new Cnz2CutsceneButtonInstance(
                new ObjectSpawn(0x4780, 0x0728,
                        Sonic3kObjectIds.CUTSCENE_BUTTON, 6, 0, false, 0));
        button.setServices(TestEnvironment.objectServices());

        button.update(0, fixture.sprite());

        int[] expected = {0x14, 0x0F, 0x0F, 0x88};
        for (int row = 0; row < expected.length; row++) {
            assertEquals(expected[row],
                    GameServices.level().getCurrentLevel().getMap().getValue(0, 0x8E, 14 + row) & 0xFF,
                    "loc_65CAC layer-0 layout byte mismatch at row " + (14 + row));
        }
        assertTrue(GameServices.zoneLayoutMutationPipeline().isEmpty(),
                "the button applies its atomic gameplay mutation immediately");
        long tubesAfterPress = liveTubeCount();
        assertTrue(tubesAfterPress >= 2);

        button.update(1, fixture.sprite());

        assertEquals(tubesAfterPress, liveTubeCount(),
                "the cleared respawn entry prevents the pressed action replaying on backtrack polls");
        assertTrue(button.isPersistent());
        assertTrue(button.isPressedForTest());

        Cnz2CutsceneButtonInstance freshLevelObject = new Cnz2CutsceneButtonInstance(
                new ObjectSpawn(0x4780, 0x0728,
                        Sonic3kObjectIds.CUTSCENE_BUTTON, 6, 0, false, 0));
        assertFalse(freshLevelObject.isPressedForTest(),
                "a full level restart creates fresh object RAM; ordinary backtracking keeps the live pressed object");
    }

    private static long liveTubeCount() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(CnzVacuumTubeInstance.class::isInstance)
                .filter(object -> !object.isDestroyed())
                .count();
    }
}
