package com.openggf.control;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestActionReducer {
    private static PhysicalInputEvent key(long time, int key, float value) {
        return new PhysicalInputEvent(time,time,PhysicalInputEvent.Kind.KEY,-1,key,value);
    }
    @Test void shortTapProducesTimestampedPressAndReleaseEvenWhenFinalStateIsNeutral() {
        ActionMap map = new ActionMap().bind("jump",new PhysicalBinding('K',-1,65,1));
        ActionReducer reducer = new ActionReducer(map);
        reducer.reset(PhysicalInput.neutral());
        List<ActionFrame> frames = reducer.accept(new PhysicalInput(30,List.of(),List.of(),
                List.of(key(10,65,1),key(20,65,0)),0));
        assertTrue(frames.get(0).pressed("jump"));
        assertTrue(frames.get(0).held("jump"));
        assertFalse(frames.get(1).held("jump"));
        assertFalse(frames.getLast().pressed("jump"));
        assertEquals(10,frames.get(0).timestampNanos());
        assertEquals(20,frames.get(1).timestampNanos());
    }
    @Test void alternativeRisingEdgeSurvivesOtherHeldBindingAndResetConsumesEdges() {
        ActionMap map = new ActionMap().bind("jump",new PhysicalBinding('K',-1,65,1),new PhysicalBinding('K',-1,66,1));
        ActionReducer reducer = new ActionReducer(map);
        reducer.reset(new PhysicalInput(0,List.of(65),List.of(),List.of(),0));
        List<ActionFrame> frames = reducer.accept(new PhysicalInput(20,List.of(65,66),List.of(),List.of(key(10,66,1)),0));
        assertTrue(frames.getFirst().pressed("jump"));
        reducer.reset(new PhysicalInput(20,List.of(65,66),List.of(),List.of(),0));
        assertFalse(reducer.accept(new PhysicalInput(30,List.of(65,66),List.of(),List.of(),0)).getFirst().pressed("jump"));
    }
    @Test void remappingConsumesCurrentEdgesAndDoesNotInventAPress() {
        ActionMap map = new ActionMap().bind("accept",new PhysicalBinding('K',-1,65,1));
        ActionReducer reducer = new ActionReducer(map);
        reducer.reset(PhysicalInput.neutral());
        map.bind("accept",new PhysicalBinding('K',-1,66,1));
        List<ActionFrame> frames = reducer.accept(new PhysicalInput(20,List.of(66),List.of(),List.of(key(10,66,1)),2));
        assertEquals(1,frames.size());
        assertTrue(frames.getFirst().held("accept"));
        assertFalse(frames.getFirst().pressed("accept"));
        assertEquals(2,frames.getFirst().droppedEvents());
    }
    @Test void labelsCaptureAndEncodingPreserveBindingsAndIgnoreTriggerRelease() {
        PhysicalBinding binding = new PhysicalBinding('A',0,3,-1);
        assertEquals(binding,PhysicalBinding.decode(binding.encode()));
        assertEquals("PAD 0 AXIS 3-",binding.label());
        assertNull(PhysicalBinding.capture(new PhysicalInputEvent(1,1,PhysicalInputEvent.Kind.AXIS,0,4,-1),true,.7f));
        assertEquals(new PhysicalBinding('K',-1,65,1),PhysicalBinding.capture(key(1,65,1),false,.7f));
        assertThrows(IllegalArgumentException.class, () -> PhysicalBinding.decode(",0,1,1"));
        ActionMap map = new ActionMap().bind("accept",binding,new PhysicalBinding('K',-1,257,1));
        ActionMap restored = new ActionMap();
        restored.read(map.encode());
        assertEquals(map.bindings("accept"),restored.bindings("accept"));
    }
    @Test void disconnectedTriggerIsNeutralAndIdenticalAlternativesCountOnce() {
        ActionReducer reducer = new ActionReducer(new ActionMap().bind("fire",new PhysicalBinding('A',0,4,1),new PhysicalBinding('A',0,4,1)));
        reducer.reset(PhysicalInput.neutral());
        assertFalse(reducer.accept(PhysicalInput.neutral()).getFirst().held("fire"));
        PhysicalInputEvent axis = new PhysicalInputEvent(1,1,PhysicalInputEvent.Kind.AXIS,0,4,1);
        List<ActionFrame> frames = reducer.accept(new PhysicalInput(2,List.of(),List.of(),List.of(axis),0));
        assertEquals(java.util.Set.of("fire"),frames.getFirst().pressedActions());
        assertEquals(1f,frames.getFirst().amount("fire"));
    }
}
