package com.openggf.game.sonic2.competition.state;

import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.level.objects.*;
import java.util.List;
import java.util.Objects;

/** Selects the ROM level-only processed range without changing the ordinary registry's allocation window. */
public final class Sonic2LevelOnlyObjectRegistry extends Sonic2ObjectRegistry implements ObjectManagerInternalAccess.AllocationSemantics {
    // RunObjects (s2.asm:29805-29822): every level/demo walks $90 slots, including $80-$8F.
    // Ordinary S2 currently executes explicit out-of-range effects through its fallback walk;
    // this native world needs the real processed range for fixed-address replacement and ordering.
    private static final ObjectSlotLayout LEVEL_ONLY_RANGE = new ObjectSlotLayout(16,112,144,false,true,false);
    private final ObjectRegistry delegate;
    public Sonic2LevelOnlyObjectRegistry(ObjectRegistry delegate) { this.delegate=Objects.requireNonNull(delegate); }
    @Override public ObjectSlotLayout allocationLayout() { return ObjectSlotLayout.SONIC_2; }
    @Override public ObjectSlotLayout objectSlotLayout() { return LEVEL_ONLY_RANGE; }
    @Override public ObjectWindowingStrategy objectWindowingStrategy() { return delegate.objectWindowingStrategy(); }
    @Override public ObjectInstance create(ObjectSpawn spawn) { return delegate.create(spawn); }
    @Override public void reportCoverage(List<ObjectSpawn> spawns) { delegate.reportCoverage(spawns); }
    @Override public String getPrimaryName(int id) { return delegate.getPrimaryName(id); }
    @Override public List<String> getAliases(int id) { return delegate.getAliases(id); }
    @Override public boolean hasObjectKey(String key) { return delegate.hasObjectKey(key); }
    @Override public List<String> browsableObjectKeys() { return delegate.browsableObjectKeys(); }
    @Override public void installFixedSstObjects(int zone,int act,FixedSstSlotSink slots) {
        delegate.installFixedSstObjects(zone,act,slots);
    }
}
