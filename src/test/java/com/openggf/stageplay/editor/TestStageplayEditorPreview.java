package com.openggf.stageplay.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestStageplayEditorPreview {
    @Test void widescreenFilmLetterboxesInsideNativeGameViewport() {
        assertArrayEquals(new int[] {20,52,320,180},StageplayEditorSession.fitViewport(new int[] {20,30,320,224},384,216));
    }

    @Test void nativeFilmPillarboxesInsideWidescreenViewportWithoutStretching() {
        int[] fit = StageplayEditorSession.fitViewport(new int[] {0,0,1280,720},320,224);
        assertArrayEquals(new int[] {125,0,1029,720},fit);
        assertEquals(320.0/224,fit[2]/(double)fit[3],.001);
    }

    @Test void hdFilmFitsSmallCanvasAtItsOwnAspect() {
        assertArrayEquals(new int[] {0,3,200,113},StageplayEditorSession.fitViewport(new int[] {0,0,200,120},1920,1080));
    }
}
