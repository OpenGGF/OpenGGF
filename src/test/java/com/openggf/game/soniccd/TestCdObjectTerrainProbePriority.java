package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.game.soniccd.objects.ssz.SszStageLayer;
import com.openggf.game.soniccd.objects.wwz.ObjSemi;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestCdObjectTerrainProbePriority {
    private static final int WWZ_LOOP_X = 0xA80;
    private static final int WWZ_LOOP_Y = 0x280;

    @TempDir Path tempDir;

    @BeforeEach
    @AfterEach
    void reset() {
        TestEnvironment.resetAll();
    }

    @Test
    void rightWallReadsPlainChunkOnTheSecondBlock() throws Exception {
        Probe object = boot(CdStagePayload.R81B, 0x92B0);
        int before = object.tile();
        // CheckBlockXEmpty (common/block_collide.asm:419-424) enters CheckBlockX2 (common/block_collide.asm:491-494).
        // R81B column $16 is blank here. The second read crosses into plain chunk $19.
        object.wall(0x16F4, 0x80, false);
        assertEquals(List.of(0x92B0, 0x12B0), List.of(before, object.tile()));
    }

    @Test
    void leftWallAppliesTheSszLayerPriorityRule() throws Exception {
        Probe object = boot(CdStagePayload.R71A, 0x12B0);
        int before = object.tile();
        // GetBlockZone5AnimatedTiles (common/block_collide.asm:107-127). R71A loop chunk $01.
        object.wall(0x280, 0x80, true);
        int firstLayer = object.tile();
        SszStageLayer.of((CdLevel) GameServices.level().getCurrentLevel()).toggle();
        object.wall(0x280, 0x80, true);
        assertEquals(List.of(0x12B0, 0x92B0, 0x12B0), List.of(before, firstLayer, object.tile()));
    }

    @Test
    void ceilingProbeClearsWwzTilePriority() throws Exception {
        Probe object = boot(CdStagePayload.R61A, 0xA4A8);
        int before = object.tile();
        // ObjGetCeilDist (Level/Collision Check.asm:318-338) enters FindLevelFloor above the object.
        object.ceiling(WWZ_LOOP_X, WWZ_LOOP_Y + 0x10);
        assertEquals(List.of(0xA4A8, 0x24A8, true),
                List.of(before, object.tile(), object.terrainProbeLoopPath()));
    }

    @Test
    void floorProbeRetainsTheWwzPathThroughRewindUntilAPlainRead() throws Exception {
        Probe object = boot(CdStagePayload.R61A, 0xA4A8);
        // GetLevelBlock (Level/Collision Floor.asm:495-531). Re-entering $14 only clears priority
        // after a plain read has cleared the object's path bit.
        object.floor(WWZ_LOOP_X, WWZ_LOOP_Y);
        int entered = object.tile();
        var onLoop = object.captureRewindState();

        object.setTerrainProbePriority(true);
        object.floor(0x180, 0x180);
        object.floor(WWZ_LOOP_X, WWZ_LOOP_Y);
        int reentered = object.tile();

        object.floor(0x180, 0x180);
        object.restoreRewindState(onLoop);
        object.setTerrainProbePriority(true);
        object.floor(WWZ_LOOP_X, WWZ_LOOP_Y);
        assertEquals(List.of(0x24A8, 0x24A8, 0xA4A8), List.of(entered, reentered, object.tile()));
    }

    @Test
    void semiBombClearsPriorityOnItsFirstTurn() throws Exception {
        boot(CdStagePayload.R61A);
        // ObjSemiBomb_Init falls through into ObjSemiBomb_Fall (Level/Wacky Workbench/Objects/Semi/Main.asm:232-249).
        var parent = new ObjSemi(new ObjectSpawn(WWZ_LOOP_X, 0x266, 0x32, 0, 0, false, 0));
        var type = Class.forName(ObjSemi.class.getName() + "$ObjSemiBomb");
        var constructor = type.getDeclaredConstructor(ObjSemi.class);
        constructor.setAccessible(true);
        var bomb = (CdObject) constructor.newInstance(parent);
        GameServices.level().getObjectManager().addDynamicObject(bomb);
        bomb.update(1, null);
        assertEquals(List.of(false, true), List.of(bomb.isHighPriority(), bomb.terrainProbeLoopPath()));
    }

    private Probe boot(CdStagePayload payload, int tileWord) throws Exception {
        boot(payload);
        var probe = new Probe(tileWord);
        GameServices.level().getObjectManager().addDynamicObject(probe);
        return probe;
    }

    private void boot(CdStagePayload payload) throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.boot(tempDir, disc, payload);
        }
    }

    private static final class Probe extends CdObject {
        Probe(int tileWord) {
            super(new ObjectSpawn(0, 0, 0x21, 0, 0, false, 0), "Terrain priority probe");
            this.tileWord = tileWord;
        }

        int tile() { return tileWord; }
        void floor(int x, int y) { checkFloor(x, y, 0); }
        void wall(int x, int y, boolean left) { checkWall(x, y, left); }
        void ceiling(int x, int y) { checkCeiling(x, y, 0x10); }

        @Override public void update(int frame, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
