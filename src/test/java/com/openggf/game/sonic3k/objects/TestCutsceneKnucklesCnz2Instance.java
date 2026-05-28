package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TestCutsceneKnucklesCnz2Instance {
    @Test
    void registryRoutesCnzAct2CutsceneSubtypesToCnzHandlers() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        ObjectInstance first = registry.create(new ObjectSpawn(
                0x1D00, 0x0280, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 12, 0, false, 0));
        ObjectInstance second = registry.create(new ObjectSpawn(
                0x45C0, 0x0720, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 16, 0, false, 0));

        assertInstanceOf(CutsceneKnucklesCnz2AInstance.class, first,
                "Subtype $0C is CutsceneKnux_CNZ2A, not the AIZ2 fallback");
        assertInstanceOf(CutsceneKnucklesCnz2BInstance.class, second,
                "Subtype $10 is CutsceneKnux_CNZ2B, not the AIZ2 fallback");
    }

    @Test
    void cnzCutsceneObjectsStartAtTheirLayoutPositions() {
        CutsceneKnucklesCnz2AInstance first = new CutsceneKnucklesCnz2AInstance(
                new ObjectSpawn(0x1D00, 0x0280, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 12, 0, false, 0));
        CutsceneKnucklesCnz2BInstance second = new CutsceneKnucklesCnz2BInstance(
                new ObjectSpawn(0x45C0, 0x0720, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 16, 0, false, 0));

        assertEquals(0x1D00, first.getX());
        assertEquals(0x0280, first.getY());
        assertEquals(0x45C0, second.getX());
        assertEquals(0x0720, second.getY());
    }
}
