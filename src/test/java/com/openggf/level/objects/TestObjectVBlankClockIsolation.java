package com.openggf.level.objects;

import com.openggf.game.GameModule;
import com.openggf.game.LevelInitProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class TestObjectVBlankClockIsolation {
    @Test
    void traceBootstrapStyleObjectClockAdvancesCannotReleaseLevelLoadWork() {
        AtomicInteger serviced = new AtomicInteger();
        LevelInitProfile profile = mock(LevelInitProfile.class);
        doAnswer(ignored -> { serviced.incrementAndGet(); return null; })
                .when(profile).serviceLevelLoadVBlank();
        GameModule module = mock(GameModule.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        ObjectServices services = mock(ObjectServices.class);
        when(services.gameModule()).thenReturn(module);
        ObjectManager objects = new ObjectManager(List.of(), null, 0, null,
                null, null, null, services);

        for (int i = 0; i < 20; i++) objects.advanceVblaCounter();

        assertEquals(20, objects.getVblaCounter());
        assertEquals(0, serviced.get(),
                "object dispatch/bootstrap counters are not production V-int edges");
    }
}
