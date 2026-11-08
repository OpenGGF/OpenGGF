package com.openggf.game.sonic2.kis2;

import com.openggf.game.internal.BadnikChainPolicy;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestKis2ChainServiceLookup {
    @Test void inheritedWordLookupDoesNotProbeRomsOrInitializeSpecialStages() throws Exception {
        var base=new Sonic2GameModule();
        var context=mock(PatchContext.class);
        var module=new Kis2GameModule(base,context);
        var chain=module.getGameService(BadnikChainPolicy.class);
        assertSame(base.getGameService(BadnikChainPolicy.class),chain);
        verifyNoInteractions(context);
        for(String name:new String[]{"specialStageProvider","kis2Image","physicsProvider","game"}) {
            var field=Kis2GameModule.class.getDeclaredField(name);field.setAccessible(true);
            assertNull(field.get(module),name+" remains uninitialized");
        }
        var probe=Kis2GameModule.class.getDeclaredField("kis2ImageProbed");probe.setAccessible(true);
        assertFalse(probe.getBoolean(module));
        assertEquals(1,module.rewindAdapters().stream().filter(adapter->adapter==chain).count());
    }
}
