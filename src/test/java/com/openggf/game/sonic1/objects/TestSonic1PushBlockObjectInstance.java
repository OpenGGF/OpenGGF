package com.openggf.game.sonic1.objects;

import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidRoutineProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSonic1PushBlockObjectInstance {

    @Test
    public void rowSubtypeUsesObjectLevelHighPriority() {
        Sonic1PushBlockObjectInstance single = new Sonic1PushBlockObjectInstance(
                new ObjectSpawn(0x100, 0x100, Sonic1ObjectIds.PUSH_BLOCK, 0, 0, false, 0));
        Sonic1PushBlockObjectInstance row = new Sonic1PushBlockObjectInstance(
                new ObjectSpawn(0x100, 0x100, Sonic1ObjectIds.PUSH_BLOCK, 1, 0, false, 0));

        assertFalse(single.isHighPriority());
        assertTrue(row.isHighPriority());
    }

    @Test
    public void pushBlockSolidRoutineKeepsRightEdgeInclusive() {
        Sonic1PushBlockObjectInstance block = new Sonic1PushBlockObjectInstance(
                new ObjectSpawn(0x100, 0x100, Sonic1ObjectIds.PUSH_BLOCK, 0, 0, false, 0));

        SolidRoutineProfile profile = block.getSolidRoutineProfile();

        assertTrue(profile.inclusiveRightEdge(),
                "S1 Solid_ChkEnter uses BHI for the right-edge rejection, so exact edge contact is solid");
    }
}
