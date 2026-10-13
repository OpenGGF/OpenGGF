package com.openggf.game.session;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.code.GameplayObserverRegistry;
import java.util.*;

/** Per-world accepted policy/creator-state sidecar; canonical input remains the live recorder's authority. */
final class WorldPolicyHistory {
    private Snapshot beforeTick;
    private boolean replaying;
    private final List<Boundary> boundaries=new ArrayList<>();
    record Snapshot(Object policies,List<Object> observers) { Snapshot { observers=List.copyOf(observers); } }
    record Boundary(Snapshot before,com.openggf.game.GameplayObservation.Phase phase) { }
    record Frame(Snapshot before,Snapshot after,List<Boundary> boundaries) { Frame { boundaries=List.copyOf(boundaries); } }
    boolean replaying() { return replaying; }
    void recordBefore(WorldSession world) { if(!replaying)beforeTick=capture(world); }
    Frame frame(WorldSession world) {
        if(beforeTick==null)return null;
        var frame=new Frame(beforeTick,capture(world),boundaries); beforeTick=null;boundaries.clear(); return frame;
    }
    void clear() { beforeTick=null;replaying=false;boundaries.clear(); }
    void beforeBoundary(WorldSession world,com.openggf.game.GameplayObservation.Phase phase) {
        if(replaying)return;
        if(boundaries.size()>=32)throw new IllegalStateException("Pending native boundary journal is full");
        boundaries.add(new Boundary(capture(world),phase));
    }
    void restoreBefore(WorldSession world,Frame frame) {
        replaying=true;
        try {
            // Presentation-only cleanup owns native object side effects too. Reapply its exact
            // captured owner graph before restoring accepted creator decisions; callbacks stay quiet.
            for(var boundary:frame.boundaries()) {
                restore(world,boundary.before());
                world.policies.leaveOrdinaryGameplay(boundary.phase());
            }
            restore(world,frame.before());
        } catch(RuntimeException failure) { replaying=false;throw failure; }
    }
    void restoreAfter(WorldSession world,Frame frame) { try { restore(world,frame.after()); } finally { replaying=false; } }
    void endReplay() { replaying=false; }
    private static Snapshot capture(WorldSession world) {
        var observers=world.getGameModule().getGameService(GameplayObserverRegistry.class);
        return new Snapshot(world.policies.rewindAdapter().capture(),observers==null?List.of():
                observers.entries().stream().map(e -> (Object)e.adapter().capture()).toList());
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void restore(WorldSession world,Snapshot snapshot) {
        ((RewindSnapshottable)world.policies.rewindAdapter()).restore(snapshot.policies());
        var registry=world.getGameModule().getGameService(GameplayObserverRegistry.class);
        var observers=registry==null?List.<GameplayObserverRegistry.Entry>of():registry.entries();
        if(observers.size()!=snapshot.observers().size())throw new IllegalStateException("Gameplay observer graph changed");
        for(int i=0;i<observers.size();i++)((RewindSnapshottable)observers.get(i).adapter()).restore(snapshot.observers().get(i));
    }
}
