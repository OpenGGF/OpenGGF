package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.GameStateManager;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Obj89's pillar calls {@code Obj89_Pillar_SolidObject} at the head of Sub0 and
 * Sub2, before it raises or shakes (s2.asm:65451-65497), and the shipped
 * (FixBugs=0) Sub4 makes no solid call while it sinks (s2.asm:65601-65640).
 */
class TestArzBossPillarSolidCalls {

    @Test
    void raisingPillarResolvesBeforeItMoves() {
        RecordingPillar pillar = pillar(0x510);
        assertEquals(SolidExecutionMode.MANUAL_CHECKPOINT, pillar.solidExecutionMode());

        pillar.update(1, null);
        pillar.update(2, null);

        assertEquals(List.of(0x510, 0x50F), pillar.callYs,
                "each Sub0 frame resolves at the y it entered with, then raises one pixel");
        assertEquals(0x50E, pillar.getY());
    }

    @Test
    void idlePillarResolvesOnceAtTheTargetHeight() {
        RecordingPillar pillar = pillar(0x489);
        pillar.update(1, null);
        pillar.callYs.clear();

        pillar.update(2, null);

        assertEquals(List.of(0x488), pillar.callYs, "Sub2 resolves once per frame at its own y");
    }

    @Test
    void sinkingPillarNeverResolves() {
        TestBoss boss = new TestBoss();
        boss.defeated = true;
        RecordingPillar pillar = pillar(0x488, boss);

        for (int frame = 0; frame < 0x88 && !pillar.isDestroyed(); frame++) {
            pillar.update(frame, null);
        }

        assertTrue(pillar.isDestroyed(), "Sub4 deletes the pillar at y $510");
        assertTrue(pillar.callYs.isEmpty(), "shipped Sub4 makes no Obj89_Pillar_SolidObject call");
    }

    private static RecordingPillar pillar(int y) {
        return pillar(y, null);
    }

    private static RecordingPillar pillar(int y, Sonic2ARZBossInstance boss) {
        RecordingPillar pillar = new RecordingPillar(new ObjectSpawn(0x2B70, y, 0x89, 4, 1, false, 0), boss);
        pillar.setServices(new TestObjectServices().withGameState(new GameStateManager()));
        return pillar;
    }

    private static final class RecordingPillar extends ARZBossPillar {
        final List<Integer> callYs = new ArrayList<>();

        RecordingPillar(ObjectSpawn spawn, Sonic2ARZBossInstance boss) {
            super(spawn, boss);
        }

        @Override
        protected SolidCheckpointBatch checkpointAll() {
            callYs.add(getY());
            return new SolidCheckpointBatch(this, new IdentityHashMap<>());
        }
    }

    private static final class TestBoss extends Sonic2ARZBossInstance {
        boolean defeated;

        TestBoss() {
            super(new ObjectSpawn(0x2AE0, 0x488, 0x89, 0, 0, false, 0));
        }

        @Override
        public boolean isInDefeatSequence() {
            return defeated;
        }
    }
}
