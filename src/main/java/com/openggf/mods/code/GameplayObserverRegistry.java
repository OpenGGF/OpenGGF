package com.openggf.mods.code;

import com.openggf.game.GameplayObserver;
import com.openggf.game.rewind.RewindSnapshottable;
import java.util.*;

/** Verified observer graph, accumulated across service decorations; never creator-reported owners. */
public final class GameplayObserverRegistry implements com.openggf.game.session.GameplayObserverSnapshots {
    public record Entry(String owner, GameplayObserver observer, RewindSnapshottable<?> adapter) { }
    private final List<Entry> entries;
    public GameplayObserverRegistry() {
        if(com.openggf.util.EngineCallerAccess.callerOutside(GameplayObserverRegistry.class).getClassLoader()!=GameplayObserverRegistry.class.getClassLoader())
            throw new SecurityException("Observer ownership belongs to the engine");
        entries=List.of();
    }
    private GameplayObserverRegistry(List<Entry> entries) { this.entries=List.copyOf(entries); }
    public List<Entry> entries() { return entries; }
    @Override public List<RewindSnapshottable<?>> adapters() {
        return entries.stream().map(Entry::adapter).toList();
    }
    GameplayObserverRegistry append(String owner,GameplayObserver observer) {
        if(!(observer instanceof RewindSnapshottable<?> adapter)) throw new IllegalArgumentException("Gameplay observers require captured state");
        if(!com.openggf.game.rewind.RewindAdapterOwnership.isBoundToOwner(adapter,owner))
            throw new IllegalArgumentException("Gameplay observer must retain verified owner binding");
        var values=new ArrayList<>(entries); values.add(new Entry(owner,observer,adapter));
        return new GameplayObserverRegistry(values);
    }
}
