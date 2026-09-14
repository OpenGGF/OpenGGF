package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.mockito.Mockito.*;

/** Obj_FBZBentPipe's ORI preserves placement flags independently of mapping subtype. */
class TestFbzBentPipeRendering {
    @Test void eachPlacementOrientationReachesTheRomMappingRenderer() {
        for (int flags=0;flags<4;flags++) {
            PatternSpriteRenderer renderer=mock(PatternSpriteRenderer.class);
            ObjectRenderManager manager=mock(ObjectRenderManager.class);
            when(renderer.isReady()).thenReturn(true);
            when(manager.getRenderer(Sonic3kObjectArtKeys.FBZ_BENT_PIPE)).thenReturn(renderer);
            var pipe=new FbzBentPipeObjectInstance(new ObjectSpawn(0xCDA,0x317,0x76,0,flags,false,0x2317));
            pipe.setServices(new StubObjectServices() {
                @Override public ObjectRenderManager renderManager() { return manager; }
            });
            pipe.appendRenderCommands(new ArrayList<>());
            verify(renderer).drawFrameIndex(0,0xCDA,0x317,(flags&1)!=0,(flags&2)!=0);
        }
    }
}
