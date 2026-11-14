package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.common.CdSpikes;
import com.openggf.game.soniccd.objects.mmz.MmzLaser;
import com.openggf.level.objects.ObjectFactory;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Object IDs resolved per stage from the catalogues, ROM-free: a zone routine that only some acts index does not
 * shadow the others ($1D: R82x's laser, r8/object_index_2.asm:32; R83x's bumper, r8/object_index_3.asm:32), and
 * the Level dialect's ObjSpikes sits only at the ID each stage's ObjectIndex gives it (common {@code spikesId}).
 */
class TestCdObjectRegistryStageScope {
    private final SonicCdObjectRegistry registry = new SonicCdObjectRegistry();

    private Object created(ObjectFactory factory, int id) {
        return factory == null ? null : factory.create(new ObjectSpawn(0x100, 0x100, id, 0, 0, false, 0), registry);
    }

    @Test
    void theMetallicMadnessLaserIsScopedToTheStagesThatIndexIt() {
        for (CdStagePayload laserStage : new CdStagePayload[]{CdStagePayload.R82A, CdStagePayload.R82D}) {
            assertInstanceOf(MmzLaser.class, created(registry.zoneFactory(laserStage, 0x1D), 0x1D), laserStage.name());
        }
        // R83x indexes the bumper at $1D; the zone fragment's one $1D factory picks the bumper there (TestMmzObjects3
        // creates it through a loaded stage), and R81x indexes nothing at $1D.
        for (CdStagePayload bumperStage : new CdStagePayload[]{CdStagePayload.R83C, CdStagePayload.R83D}) {
            assertNotNull(registry.zoneFactory(bumperStage, 0x1D), bumperStage + ": $1D resolves in the zone fragment");
        }
        for (CdStagePayload none : new CdStagePayload[]{CdStagePayload.R81A, CdStagePayload.R81D}) {
            assertNull(registry.zoneFactory(none, 0x1D), none + ": $1D is a null object");
        }
        // An unscoped zone registration still applies in every act.
        assertNotNull(registry.zoneFactory(CdStagePayload.R83C, 0x22));
        assertNotNull(registry.zoneFactory(CdStagePayload.R82A, 0x22));
    }

    @Test
    void spikesArePlacedOnlyWhereTheStageIndexesThem() {
        assertInstanceOf(CdSpikes.class, created(registry.stageFactory(CdStagePayload.R11A, 0x26), 0x26));
        assertInstanceOf(CdSpikes.class, created(registry.stageFactory(CdStagePayload.R13D, 0x26), 0x26));
        // $26 is the door in Collision Chaos, the elevator in Tidal Tempest, the jump spike ball in Stardust Speedway.
        for (CdStagePayload other : new CdStagePayload[]{CdStagePayload.R31A, CdStagePayload.R41A, CdStagePayload.R71A}) {
            assertFalse(created(registry.stageFactory(other, 0x26), 0x26) instanceof CdSpikes, other.name());
        }
    }
}
