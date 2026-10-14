package com.openggf.game.session;

import com.openggf.game.rewind.RewindSnapshottable;
import java.util.*;

/** Per-world accepted policy/creator-state sidecar; canonical input remains the live recorder's authority. */
final class WorldPolicyHistory {
    private Snapshot beforeTick;
    private boolean replaying;
    private boolean armed;
    boolean nativeTickActive;
    private final List<Boundary> boundaries=new ArrayList<>();
    record Snapshot(Object policies,List<Object> observers) { Snapshot { observers=List.copyOf(observers); } }
    record Boundary(Snapshot before,com.openggf.game.GameplayObservation.Phase phase) { }
    record Frame(Snapshot before,Snapshot after,List<Boundary> boundaries) { Frame { boundaries=List.copyOf(boundaries); } }
    boolean replaying() { return replaying; }
    void recording(boolean enabled) { if(armed!=enabled)discardPending();armed=enabled; }
    void recordBefore(WorldSession world) { if(armed && !replaying)beforeTick=capture(world); }
    Frame frame(WorldSession world) {
        if(beforeTick==null)return null;
        var frame=new Frame(beforeTick,capture(world),boundaries); beforeTick=null;boundaries.clear(); return frame;
    }
    void discardPending() { beforeTick=null;boundaries.clear();nativeTickActive=false; }
    void clear() { discardPending();replaying=false; }
    void beforeBoundary(WorldSession world,com.openggf.game.GameplayObservation.Phase phase) {
        if(!armed || replaying)return;
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
        return new Snapshot(world.policies.rewindAdapter().capture(), adapters(world).stream()
                .map(adapter -> (Object) adapter.capture()).toList());
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void restore(WorldSession world,Snapshot snapshot) {
        ((RewindSnapshottable)world.policies.rewindAdapter()).restore(snapshot.policies());
        var observers = adapters(world);
        if(observers.size()!=snapshot.observers().size())throw new IllegalStateException("Gameplay observer graph changed");
        for(int i=0;i<observers.size();i++)((RewindSnapshottable)observers.get(i)).restore(snapshot.observers().get(i));
    }
    private static List<RewindSnapshottable<?>> adapters(WorldSession world) {
        var graph = world.getGameModule().getGameService(GameplayObserverSnapshots.class);
        if (graph == null) return List.of();
        if (graph.getClass().getClassLoader() != WorldPolicyHistory.class.getClassLoader())
            throw new SecurityException("Observer history belongs to the verified engine graph");
        return graph.adapters();
    }

}
