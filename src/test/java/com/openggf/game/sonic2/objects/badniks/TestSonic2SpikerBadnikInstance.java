package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestSonic2SpikerBadnikInstance {

    @Test
    void throwCheckUsesClosestNativePlayer() throws Exception {
        SpikerBadnikInstance spiker = new SpikerBadnikInstance(
                new ObjectSpawn(0x0100, 0x0100, 0x92, 0, 0, false, 0));
        AbstractPlayableSprite main = mock(AbstractPlayableSprite.class);
        when(main.getCentreX()).thenReturn((short) 0x0180);
        when(main.getCentreY()).thenReturn((short) 0x0100);

        AbstractPlayableSprite sidekick = mock(AbstractPlayableSprite.class);
        when(sidekick.getCentreX()).thenReturn((short) 0x0100);
        when(sidekick.getCentreY()).thenReturn((short) 0x0100);
        spiker.setServices(new TestObjectServices().withSidekicks(List.of(sidekick)));

        spiker.update(0, main);

        // Obj92 calls Obj_GetOrientationToPlayer, which selects the closer of
        // MainCharacter and Sidekick by X distance (docs/s2disasm/s2.asm:
        // 72812-72831) before the +/-$20 horizontal throw window is tested.
        assertEquals("THROW_PREP", stateName(spiker));
    }

    private static String stateName(SpikerBadnikInstance spiker) throws Exception {
        Field field = SpikerBadnikInstance.class.getDeclaredField("state");
        field.setAccessible(true);
        return String.valueOf(field.get(spiker));
    }
}
