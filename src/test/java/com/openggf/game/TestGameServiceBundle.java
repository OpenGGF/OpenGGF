package com.openggf.game;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.rewind.RewindSnapshottable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestGameServiceBundle {
    private static final class Mode implements GameplayFrameController, RewindSnapshottable<Integer> {
        int rows;
        public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) { rows++; return true; }
        public void afterTick(CourseControl course, boolean advanced) { }
        public String key() { return "forged-owner:ignored"; }
        public Integer capture() { return rows; }
        public void restore(Integer snapshot) { rows=snapshot; }
    }
    @Test void controllerAndAdapterUseOneGraphWhileFactoryApplicationsStayIndependent() {
        java.util.function.Supplier<GameServiceBundle> factory = () -> {
            var mode = new Mode();
            return GameServiceBundle.builder().frameController("mode",mode).service(Mode.class,mode).build();
        };
        var first=factory.get(); var second=factory.get();
        var controller=(GameplayFrameController)first.services().get(GameplayFrameController.class);
        var state=(Mode)first.services().get(Mode.class);
        assertSame(state,controller); assertSame(state,first.rewindAdapters().get("mode"));
        assertFalse(first.rewindAdapters().containsKey(state.key()),"Claimed adapter owner cannot name its registration");
        assertNotSame(state,second.services().get(Mode.class));
        controller.beforeTick(null,null);
        Integer captured=state.capture(); controller.beforeTick(null,null); state.restore(captured);
        assertEquals(1,state.rows); assertEquals(0,((Mode)second.services().get(Mode.class)).rows);
        assertThrows(UnsupportedOperationException.class,()->first.services().clear());
    }
    @Test void duplicateContractsAndKeysAreRejectedAndBuilderEditsDoNotChangeBuiltViews() {
        var builder=GameServiceBundle.builder().service(String.class,"first");
        var snapshot=builder.build(); builder.service(Integer.class,3);
        assertFalse(snapshot.services().containsKey(Integer.class));
        assertThrows(IllegalArgumentException.class,()->builder.dynamicService(String.class,()->"other"));
        var mode=new Mode(); var captured=GameServiceBundle.builder().capturedService("run",Mode.class,mode);
        assertThrows(IllegalArgumentException.class,()->captured.rewindAdapter("run",new Mode()));
        assertThrows(IllegalArgumentException.class,()->captured.rewindAdapter("another:run",new Mode()));
        var active=new java.util.concurrent.atomic.AtomicReference<String>();
        var dynamic=GameServiceBundle.builder().dynamicService(String.class,active::get).build();
        assertNull(dynamic.dynamicServices().get(String.class).get());
        active.set("arena"); assertEquals("arena",dynamic.dynamicServices().get(String.class).get());
    }
}
