package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestCnzEggCapsuleInstance {

    @Test
    void uprightCapsulesUseRomSpriteAndPlanePriority() {
        var capsules = java.util.List.of(
                new CnzEggCapsuleInstance(new ObjectSpawn(0x4990, 0x02E0, 0x2A, 0, 0, false, 0)),
                new com.openggf.game.sonic3k.objects.bosses.MhzEndBossEggCapsuleInstance(0x4640, 0x0320),
                new SozEndBossEggCapsule(0x5360, 0x720));
        for (var capsule : capsules) {
            assertEquals(4, capsule.getPriorityBucket(), "ObjDat_EggCapsule priority $200");
            assertEquals(true, capsule.isHighPriority(),
                    "ObjDat_EggCapsule art bit 15 keeps body and button in front of ship scenery");
        }
    }

    @Test
    void standingTriggerChangesTheButtonFrameWithoutMovingItsSolidAnchor() {
        CnzEggCapsuleInstance capsule = new CnzEggCapsuleInstance(
                new ObjectSpawn(0x4990, 0x02E0, 0x2A, 0, 0, false, 0));
        int buttonY = capsule.getPieceY(1);

        capsule.onPieceContact(1, null,
                new SolidContact(true, false, false, true, false), 0);

        assertEquals(0x02BC, buttonY);
        assertEquals(buttonY, capsule.getPieceY(1),
                "loc_8672A changes mapping_frame but leaves child_dy unchanged");
    }
}
