package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestNativeTeleportPlaneSides {
    @Test void onlyTheSelectedPlacementPlayerBytesTravelAndLegacyObservationStaysPut() {
        var one=mock(PlayableEntity.class);var two=mock(PlayableEntity.class);var other=mock(PlayableEntity.class);
        var spawn=new ObjectSpawn(100,200,3,0,0,false,0,0);
        var untouched=new ObjectSpawn(300,400,3,0,0,false,0,1);
        var controller=new ObjectPlaneSwitcherController(null,3,null);
        controller.restoreRewindState(new PlaneSwitcherSnapshot(List.of(
                new PlaneSwitcherEntry(spawn,42,true,List.of(new PlaneSwitcherPlayerSideEntry(one,0),
                        new PlaneSwitcherPlayerSideEntry(two,1),new PlaneSwitcherPlayerSideEntry(other,7))),
                new PlaneSwitcherEntry(untouched,12,true,List.of(new PlaneSwitcherPlayerSideEntry(one,1))))));
        var before=controller.captureRewindState();
        controller.swapNativePlayerSides(spawn,one,two);
        var after=controller.captureRewindState();
        var entry=after.entries().stream().filter(e->e.spawn().equals(spawn)).findFirst().orElseThrow();
        var sides=new java.util.IdentityHashMap<PlayableEntity,Integer>();
        entry.playerSides().forEach(e->sides.put(e.player(),e.sideState()));
        assertEquals(1,sides.get(one));assertEquals(0,sides.get(two));assertEquals(7,sides.get(other));
        assertEquals(42,entry.lastSideState());assertTrue(entry.hasLastSideState());
        assertEquals(before.entries().stream().filter(e->e.spawn().equals(untouched)).findFirst().orElseThrow(),
                after.entries().stream().filter(e->e.spawn().equals(untouched)).findFirst().orElseThrow());
        controller.swapNativePlayerSides(spawn,one,two);
        assertEquals(before,controller.captureRewindState());
    }
}
