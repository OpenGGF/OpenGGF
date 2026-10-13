package com.openggf.game.session;

import com.openggf.LevelFrameContext;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameModule;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestWorldPolicyHistory {
    @Test void acceptedOutcomesBetweenNativeTicksReplayWithoutCreatorSelection() {
        var state=new CapturedChild();
        var module=mock(GameModule.class);
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> state);
        var world=new WorldSession(module);
        var level=mock(LevelManager.class);when(level.getWorldSession()).thenReturn(world);
        var frame=mock(LevelFrameContext.class);when(frame.levelManager()).thenReturn(level);
        // Three zero-tick presentations can admit/replace a decision before the next canonical row.
        state.value=3;
        WorldSessionPolicyAccess.beforeNativeTick(frame);
        assertEquals(1,state.creatorCalls);
        assertEquals(4,state.value);
        WorldSessionPolicyAccess.afterNativeTick(frame);
        var first=WorldSessionPolicyAccess.captureHistory(world);
        assertNotNull(first);assertNull(WorldSessionPolicyAccess.captureHistory(world));
        // A second native tick in the same presentation does not advance its presentation clock.
        WorldSessionPolicyAccess.beforeNativeTick(frame);
        WorldSessionPolicyAccess.afterNativeTick(frame);
        var second=WorldSessionPolicyAccess.captureHistory(world);
        assertEquals(2,state.creatorCalls);
        state.value=999;
        WorldSessionPolicyAccess.restoreHistoryBefore(world,first);
        assertEquals(4,state.value);
        WorldSessionPolicyAccess.beforeNativeTick(frame);
        assertEquals(2,state.creatorCalls,"retained decisions never rerun the creator");
        WorldSessionPolicyAccess.afterNativeTick(frame);
        WorldSessionPolicyAccess.restoreHistoryAfter(world,first);
        assertEquals(14,state.value);assertFalse(WorldSessionPolicyAccess.replaying(world));
        WorldSessionPolicyAccess.restoreHistoryBefore(world,second);
        assertEquals(15,state.value);
        WorldSessionPolicyAccess.beforeNativeTick(frame);WorldSessionPolicyAccess.afterNativeTick(frame);
        WorldSessionPolicyAccess.restoreHistoryAfter(world,second);
        assertEquals(25,state.value);assertEquals(2,state.creatorCalls);
    }
    @Test void zeroTickCleanupReplaysItsNativeDeletionWithoutRepeatingCreatorCallbacks() {
        var state=new CapturedChild() {
            @Override public void leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase phase) {
                if(value>0)nativeObjectAlive=false;
                value=0;nativeCleanupCalls++;
                if(!WorldSessionPolicyAccess.replaying(world))creatorBoundaryCalls++;
            }
        };
        var module=mock(GameModule.class);
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> { state.world=world;return state; });
        var world=new WorldSession(module);
        var level=mock(LevelManager.class);when(level.getWorldSession()).thenReturn(world);
        var frame=mock(LevelFrameContext.class);when(frame.levelManager()).thenReturn(level);
        state.value=5;state.nativeObjectAlive=true;
        // No native row records this presentation boundary's object removal.
        WorldSessionPolicyAccess.leaveOrdinaryGameplay(world,com.openggf.game.GameplayObservation.Phase.TRANSITION);
        assertFalse(state.nativeObjectAlive);
        WorldSessionPolicyAccess.beforeNativeTick(frame);WorldSessionPolicyAccess.afterNativeTick(frame);
        var recorded=WorldSessionPolicyAccess.captureHistory(world);
        state.nativeObjectAlive=true; // Native object snapshot restored to the prior canonical row.
        WorldSessionPolicyAccess.restoreHistoryBefore(world,recorded);
        assertFalse(state.nativeObjectAlive,"host cleanup replays against its captured pre-cleanup graph");
        assertEquals(2,state.nativeCleanupCalls);assertEquals(1,state.creatorBoundaryCalls);
        WorldSessionPolicyAccess.restoreHistoryAfter(world,recorded);
    }

    @Test void cleanupDispatchRetiresEveryCapturedPackEvenIfOneThrows() {
        var first=new CapturedChild(){@Override public void retire(){retired=true;throw new IllegalStateException("fault");}};
        var second=new CapturedChild();
        var composite=CompositeWorldSessionPolicyState.of(first,second);
        var captured=((RewindSnapshottable<?>)composite.rewindAdapter()).capture();
        assertNotNull(captured);
        assertThrows(IllegalStateException.class,composite::retire);
        assertTrue(first.retired);assertTrue(second.retired);
    }
    private static class CapturedChild implements WorldSessionPolicyState,RewindSnapshottable<Integer> {
        int value,creatorCalls,nativeCleanupCalls,creatorBoundaryCalls;
        boolean retired,nativeObjectAlive;WorldSession world;
        @Override public <T>T getService(Class<T>type){return null;}
        @Override public RewindSnapshottable<?> rewindAdapter(){return this;}
        @Override public String key(){return "child";}
        @Override public Integer capture(){return value;}
        @Override public void restore(Integer value){this.value=value;}
        @Override public void observeTick(LevelFrameContext context,LogicalInputSnapshot input){creatorCalls++;value++;}
        @Override public void afterNativeTick(LevelFrameContext context,LogicalInputSnapshot input){value+=10;}
        @Override public void beforeAssembly(LevelLoadCause cause){}
        @Override public void bindRoster(SpriteManager sprites){}
        @Override public void failedAssembly(LevelLoadCause cause){}
        @Override public void closeScreens(){}
        @Override public void retire(){retired=true;}
    }
}
