package com.openggf.game.rewind;

import com.openggf.game.sonic3k.objects.Lbz1RobotnikEventController;
import com.openggf.game.sonic3k.objects.LbzMinibossBoxInstance;
import com.openggf.game.sonic3k.objects.LbzMinibossBoxRig;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TestLbz1BoxRigRewind {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void restoresProgressedBoxIntoFreshOwnerAndBackAcrossCreation(boolean standalone) throws Exception {
        var source = owner(standalone);
        var before = source.captureRewindState();
        var rig = new LbzMinibossBoxRig(0x3C40, 0x936);
        rig.release();
        for (int i = 0; i < 35; i++) rig.update(LbzMinibossBoxRig.NO_CAMERA);
        setRig(source, rig);
        var captured = source.captureRewindState();
        var restored = owner(standalone);
        restored.restoreRewindState(captured);
        assertNotSame(rig, getRig(restored));
        assertEquals(rig.isReleased(), getRig(restored).isReleased());
            assertArrayEquals(rig.captureRewindStateValue().pieces(), getRig(restored).captureRewindStateValue().pieces());
        for (int i = 0; i < 180; i++) {
            rig.update(LbzMinibossBoxRig.NO_CAMERA);
            getRig(restored).update(LbzMinibossBoxRig.NO_CAMERA);
            assertEquals(rig.isReleased(), getRig(restored).isReleased());
            assertArrayEquals(rig.captureRewindStateValue().pieces(), getRig(restored).captureRewindStateValue().pieces());
        }
        restored.restoreRewindState(before);
        assertNull(getRig(restored));
        restored.restoreRewindState(captured);
        assertNotNull(getRig(restored));
    }

    private static AbstractObjectInstance owner(boolean standalone) {
        var spawn = new ObjectSpawn(0x3C40, 0x936, standalone ? 0xC4 : 0xC3, 0, 0, false, 0);
        return standalone ? new LbzMinibossBoxInstance(spawn) : new Lbz1RobotnikEventController(spawn);
    }

    private static Object holder(AbstractObjectInstance owner) throws Exception {
        var field = owner.getClass().getDeclaredField("boxRig");
        field.setAccessible(true);
        return field.get(owner);
    }

    private static LbzMinibossBoxRig getRig(AbstractObjectInstance owner) throws Exception {
        Object holder = holder(owner);
        if (holder == null || holder instanceof LbzMinibossBoxRig) return (LbzMinibossBoxRig) holder;
        var value = holder.getClass().getDeclaredField("value");
        value.setAccessible(true);
        return (LbzMinibossBoxRig) value.get(holder);
    }

    private static void setRig(AbstractObjectInstance owner, LbzMinibossBoxRig rig) throws Exception {
        var field = owner.getClass().getDeclaredField("boxRig");
        field.setAccessible(true);
        if (field.getType() == LbzMinibossBoxRig.class) field.set(owner, rig);
        else {
            var value = holder(owner).getClass().getDeclaredField("value");
            value.setAccessible(true);
            value.set(holder(owner), rig);
        }
    }
}
