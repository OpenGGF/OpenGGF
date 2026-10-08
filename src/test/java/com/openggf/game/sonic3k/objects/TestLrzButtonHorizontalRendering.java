package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;

import static org.mockito.Mockito.*;

class TestLrzButtonHorizontalRendering {
    /** Obj_LRZButtonHorizontal ORs #4 into render_flags, retaining both placement flips. */
    @ParameterizedTest
    @CsvSource({"0,0", "0,1", "0,2", "0,3", "1,0", "1,1", "1,2", "1,3"})
    void placementFlipsSurvivePressReleaseAndRecreation(int act, int flags) {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        ObjectRenderManager manager = mock(ObjectRenderManager.class);
        when(renderer.isReady()).thenReturn(true);
        when(manager.getRenderer(act == 0 ? Sonic3kObjectArtKeys.LRZ_BUTTON_HORIZONTAL
                : Sonic3kObjectArtKeys.LRZ2_BUTTON_HORIZONTAL)).thenReturn(renderer);
        var services = new StubObjectServices() {
            @Override public int currentAct() { return act; }
            @Override public ObjectRenderManager renderManager() { return manager; }
        };
        var spawn = new ObjectSpawn(0x10C2, 0x400, 0x1C, 0, flags, false, 0);
        var button = ObjectConstructionContext.construct(services,
                () -> new LrzButtonHorizontalObjectInstance(spawn));
        for (int incarnation = 0; incarnation < 2; incarnation++) {
            button.setServices(services);
            for (int frame : new int[]{0, 1, 0}) {
                if (frame == 1) {
                    button.onSolidContact(null, new SolidContact(false, true, false, false, true), 0);
                }
                button.update(0, null);
                button.appendRenderCommands(new ArrayList<>());
                verify(renderer).drawFrameIndex(frame, 0x10C2, 0x400,
                        (flags & 1) != 0, (flags & 2) != 0);
                clearInvocations(renderer);
            }
            button = button.recreateForRewind(new RewindRecreateContext(spawn, null, services));
        }
    }
}
