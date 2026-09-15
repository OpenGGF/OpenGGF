package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestCoconutsInitialization {
    private CoconutsBadnikInstance create() {
        return new CoconutsBadnikInstance(new ObjectSpawn(0x300, 0x200, 0x9D, 0, 0, false, 0));
    }

    private PerObjectRewindSnapshot.CoconutsRewindExtra state(CoconutsBadnikInstance object) {
        return (PerObjectRewindSnapshot.CoconutsRewindExtra)
                object.captureRewindState().badnikSubclassExtra();
    }

    @Test
    void initReturnsBeforeIdleAndRewindPreservesThatPass() {
        var object = create();
        var beforeInit = object.captureRewindState();
        object.update(0, null);
        assertEquals(0x10, state(object).timer(), "Obj9D_Init writes $10 and returns");
        var afterInit = object.captureRewindState();
        object.update(1, null);
        assertEquals(0x0F, state(object).timer());

        var restored = create();
        restored.restoreRewindState(beforeInit);
        restored.update(0, null);
        assertEquals(afterInit, restored.captureRewindState());
        restored.restoreRewindState(afterInit);
        restored.update(1, null);
        assertEquals(object.captureRewindState(), restored.captureRewindState());
    }

    @Test
    void enteringThrowRangeOnIdleExpiryThrowsBeforeStartingClimb() {
        var object = create();
        var player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) (0x300 - 0x60));
        object.update(0, player); // Obj9D_Init only.
        for (int i = 0; i < 0x10; i++) {
            object.update(i + 1, player);
        }
        when(player.getCentreX()).thenReturn((short) (0x300 - 0x5F));
        object.update(17, player);
        assertEquals(0x20, state(object).attackTimer(), "Obj9D_Idle checks range before timer expiry");
        assertEquals(8, state(object).timer());
        assertEquals(0, state(object).climbTableIndex());
        assertEquals(0x200, object.getY());
    }
}
