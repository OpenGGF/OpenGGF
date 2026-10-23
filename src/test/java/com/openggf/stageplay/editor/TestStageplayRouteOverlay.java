package com.openggf.stageplay.editor;

import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.route.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestStageplayRouteOverlay {
    record Stroke(float x1, float y1, float x2, float y2, int rgb, float width) { }
    static class Painter implements StageplayRouteOverlay.Painter {
        final List<Stroke> strokes = new ArrayList<>();
        public void line(float x1, float y1, float x2, float y2, int rgb, float width) {
            strokes.add(new Stroke(x1,y1,x2,y2,rgb,width));
        }
    }
    private final StageplayDocument scene = StageplayDocument.empty("s2",0,0,0,0,320,224);
    private final StageplayRouteRequest request = new StageplayRouteRequest("hero",0,200,160,120);
    private final List<StageplayRoutePoint> path = List.of(new StageplayRoutePoint(0,10,100,true),
            new StageplayRoutePoint(1,15,100,true),new StageplayRoutePoint(2,20,90,false),
            new StageplayRoutePoint(3,30,80,false),new StageplayRoutePoint(4,40,90,false),
            new StageplayRoutePoint(5,45,100,true));

    private StageplayRouteResult result(boolean success, List<StageplayRoutePoint> points) {
        return new StageplayRouteResult(success ? StageplayRouteResult.Status.VERIFIED : StageplayRouteResult.Status.SEARCH_EXHAUSTED,
                success ? "Verified" : "BLOCKED: wall", "0".repeat(64),request,success ? scene : null,
                List.of(),List.of(),points,1,6,success);
    }
    private Painter paint(StageplayRouteOverlay overlay, StageplayTimeline.CameraPose camera) {
        var painter = new Painter();
        overlay.paint(painter,camera,320,224,20,30,640,448,Double.NaN,Double.NaN);
        return painter;
    }
    @Test void followsOnlyConsecutiveObservedSamplesAndNeverConnectsTheGoal() {
        var overlay = new StageplayRouteOverlay(result(true,path),false);
        var painter = paint(overlay,new StageplayTimeline.CameraPose(0,0,1));
        assertEquals(path,overlay.path());
        assertTrue(painter.strokes.contains(new Stroke(40,230,50,230,StageplayRouteOverlay.GROUND,2)));
        assertTrue(painter.strokes.contains(new Stroke(50,230,60,210,StageplayRouteOverlay.AIR,2)));
        assertFalse(painter.strokes.stream().anyMatch(s -> s.x1()==110 && s.x2()==420),"Never connect last sample to requested goal");
        var missing = paint(new StageplayRouteOverlay(result(true,List.of(path.getFirst(),path.getLast())),false),
                new StageplayTimeline.CameraPose(0,0,1));
        assertFalse(missing.strokes.contains(new Stroke(40,230,110,230,StageplayRouteOverlay.AIR,2)),"Unsampled ticks cannot become a chord");
    }
    @Test void failureCrossIsAtLastObservedPointWithExactReasonAndSeparateDestination() {
        var painter = paint(new StageplayRouteOverlay(result(false,path),false),new StageplayTimeline.CameraPose(0,0,1));
        assertTrue(painter.strokes.contains(new Stroke(105,225,115,235,StageplayRouteOverlay.FAILURE,2)));
        var overlay = new StageplayRouteOverlay(result(false,path),false);
        assertEquals("Route incomplete", overlay.title());
        assertEquals("No route found in this search. blocked: wall", overlay.reason());
        assertTrue(painter.strokes.contains(new Stroke(420,344,426,350,StageplayRouteOverlay.FAILURE,2)));
    }
    @Test void chromeExplainsEveryResultWithoutProtocolEnums() {
        for (var status : StageplayRouteResult.Status.values()) {
            boolean success = status == StageplayRouteResult.Status.VERIFIED || status == StageplayRouteResult.Status.VISUAL_TERRAIN;
            var result = new StageplayRouteResult(status, "BLOCKED_SWEEP: no floor", "0".repeat(64), request,
                    success ? scene : null, List.of(), List.of(), path, 1, 6, status == StageplayRouteResult.Status.VERIFIED);
            var overlay = new StageplayRouteOverlay(result, false);
            assertEquals(success, overlay.successful());
            assertFalse((overlay.title() + overlay.reason() + overlay.statusText()).matches("(?s).*[A-Z]{2,}_[A-Z_]+.*"));
            if (status == StageplayRouteResult.Status.BUDGET_EXCEEDED) {
                assertTrue(overlay.reason().contains("Reachability is still unknown"));
                assertEquals("Route search limit reached.", overlay.statusText());
            }
        }
        assertEquals("Route applied", new StageplayRouteOverlay(result(true,path),true).title());
    }

    @Test void boundedFailuresKeepThePlannersMotionAdviceInEditorChrome() {
        var failure=new StageplayRouteResult(StageplayRouteResult.Status.BUDGET_EXCEEDED,
                "Speed-dependent terrain may need Run or Spindash", "0".repeat(64),request,null,
                List.of(),List.of(),path,120,2000,false);
        var overlay=new StageplayRouteOverlay(failure,false);
        assertTrue(overlay.reason().contains("Run or Spindash"));
        assertTrue(overlay.reason().contains("Reachability is still unknown"));
    }

    @Test void emptyUnsupportedResultHasNoInventedStartOrFailureLocation() {
        var painter = paint(new StageplayRouteOverlay(result(false,List.of()),false),new StageplayTimeline.CameraPose(0,0,1));
        assertEquals(8,painter.strokes.size(),"Only goal diamond and its outline, no simulated movement");
    }
    @Test void panZoomAndViewportFitUseTheSameTransformAndKeepStrokeWidths() {
        var painter = paint(new StageplayRouteOverlay(result(true,path),true),new StageplayTimeline.CameraPose(5,50,2));
        assertTrue(painter.strokes.contains(new Stroke(40,230,60,230,StageplayRouteOverlay.GROUND,2)));
        assertEquals(40,StageplayRouteOverlay.screenX(10,new StageplayTimeline.CameraPose(5,50,2),320,20,640));
        assertEquals(230,StageplayRouteOverlay.screenY(100,new StageplayTimeline.CameraPose(5,50,2),224,30,448));
    }
    @Test void paintingIsDeterministicAndCannotChangeSerializedFilm() throws Exception {
        var before = StageplayStore.encode(scene);
        var overlay = new StageplayRouteOverlay(result(true,path),true);
        var camera = new StageplayTimeline.CameraPose(0,0,1);
        assertEquals(paint(overlay,camera).strokes,paint(overlay,camera).strokes);
        assertArrayEquals(before,StageplayStore.encode(scene));
        var json = new String(before,java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(json.contains("PROPOSED")); assertFalse(json.contains("APPLIED"));
        assertThrows(IllegalArgumentException.class,() -> new StageplayRouteOverlay(result(false,path),true));
    }
}
