package com.openggf.game.sonic2.objects.bosses;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2ArzBossPlcReadiness {
    private Sonic2PlcService plc;
    private Sonic2ARZBossInstance boss;
    private AbstractPlayableSprite player;

    @BeforeEach
    void setUp() {
        GameServices.module().createGame(TestEnvironment.currentRom());
        plc = GameServices.module().getGameService(Sonic2PlcService.class);
        boss = new Sonic2ARZBossInstance(new ObjectSpawn(0, 0, 0x89, 0, 0, false, 0));
        boss.setServices(new TestObjectServices() {
            @Override
            public <T> T gameService(Class<T> type) {
                return GameServices.module().getGameService(type);
            }
        }.withGameModule(GameServices.module()));
        player = Mockito.mock(AbstractPlayableSprite.class);
    }

    @Test
    void arzBossInitializationReturnsWhileAnyPlcEntryIsPending() throws Exception {
        plc.append(43);
        assertFalse(checkInitConditions(), "ARZ initialization must wait while the active/queued FIFO is nonempty");

        drain();
        assertTrue(checkInitConditions(), "the first empty object scan may initialize ARZ");
    }

    @Test
    void unrelatedEarlierEntryBlocksArzBossArtReadiness() throws Exception {
        plc.append(0);
        plc.append(43);
        assertFalse(checkInitConditions(), "an earlier unrelated cue must block ARZ just like its own boss cue");
    }

    private boolean checkInitConditions() throws Exception {
        Method method = Sonic2ARZBossInstance.class.getDeclaredMethod("checkInitConditions", AbstractPlayableSprite.class);
        method.setAccessible(true);
        return (boolean) method.invoke(boss, player);
    }

    private void drain() {
        while (plc.isBusy()) {
            plc.prepare();
            plc.serviceLevelVBlank();
        }
    }
}
