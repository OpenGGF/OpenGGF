package com.openggf.game.sonic2.competition;

import com.openggf.game.sonic2.competition.state.Sonic2TeleportPorts;
import com.openggf.game.sonic2.objects.*;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Raw field-scope probe; the provider route test separately uses no private-state mutation. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionObjectTeleport {
    @Test void actualTransactionCallsOnlyLiveNativeFieldsInSlotOrder() throws Exception {
        var bridge=new Sonic2TeleportObjectFieldsAccess();
        var order=new ArrayList<Integer>();
        var fields=new Sonic2TeleportPorts.ObjectFields() {
            public boolean operational() { return bridge.operational(); }
            public Sonic2TeleportPorts.ObjectPlayerFields fields(AbstractObjectInstance object) {
                var nativeFields=bridge.fields(object);
                return () -> { order.add(object.getSlotIndex()); nativeFields.swapPlayers(); };
            }
        };
        NativeTeleportTestWorld.run(fields,world -> {
            var objects=world.host.context().getObjectManager();
            var slots=new ArrayList<Integer>();
            for(int slot=16;slot<56 && slots.size()<3;slot++)
                if(NativeSstSlotAccess.currentOwner(objects,slot)==null) slots.add(slot);
            assertEquals(3,slots.size());
            var spin=new ForcedSpinObjectInstance(spawn(0x84),"Pinball");
            var cage=new PointPokeyObjectInstance(spawn(0xD6),"Cage");
            var spring=new LauncherSpringObjectInstance(spawn(0x85),"Pressure");
            // Reverse insertion order must not replace the ROM's increasing SST walk.
            objects.addDynamicObjectAtSlot(spring,slots.get(2));
            objects.addDynamicObjectAtSlot(cage,slots.get(1));
            objects.addDynamicObjectAtSlot(spin,slots.get(0));
            set(spin,"sonicPastTrigger",true);set(spin,"tailsPastTrigger",false);
            set(cage,"playerState",1);set(cage,"secondPlayerState",4);
            set(cage,"countdown",0x1234);set(cage,"secondCountdown",0x5678);
            set(cage,"prizeOwnerNativeSlot",0);set(cage,"playerOccupied",true);set(cage,"secondOccupied",false);
            set(cage,"animationTimer",77);
            var first=springState(1,7);var second=springState(2,11);
            var states=states(spring);states.put(world.session.player(0),first);states.put(world.session.player(1),second);
            world.qualifyTeleport();
            assertEquals(slots,order,"swap_loop_objects must execute in native slot order");
            assertAll(
                () -> assertEquals(false,get(spin,"sonicPastTrigger")),
                () -> assertEquals(true,get(spin,"tailsPastTrigger")),
                () -> assertEquals(4,get(cage,"playerState")),
                () -> assertEquals(1,get(cage,"secondPlayerState")),
                () -> assertEquals(0x5678,get(cage,"countdown")),
                () -> assertEquals(0x1234,get(cage,"secondCountdown")),
                () -> assertEquals(0,get(cage,"prizeOwnerNativeSlot")),
                () -> assertEquals(true,get(cage,"playerOccupied")),
                () -> assertEquals(false,get(cage,"secondOccupied")),
                () -> assertEquals(77,get(cage,"animationTimer")),
                () -> assertSame(first,states.get(world.session.player(0))),
                () -> assertSame(second,states.get(world.session.player(1))),
                () -> assertEquals(2,get(first,"state")),
                () -> assertEquals(1,get(second,"state")),
                () -> assertEquals(7,get(first,"launchCooldown")),
                () -> assertEquals(11,get(second,"launchCooldown")));
            assertThrows(IllegalArgumentException.class,()->bridge.fields(
                    new RingPrizeObjectInstance(256,256,256,256,1,new int[]{1})));
        });
    }
    private static ObjectSpawn spawn(int id) { return new ObjectSpawn(256,256,id,1,0,false,0); }
    private static void set(Object owner,String name,Object value) throws Exception {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
    }
    private static Object get(Object owner,String name) throws Exception {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    @SuppressWarnings("unchecked") private static Map<Object,Object> states(Object owner) throws Exception {
        return (Map<Object,Object>)get(owner,"playerStates");
    }
    private static Object springState(int state,int cooldown) throws Exception {
        var type=Class.forName(LauncherSpringObjectInstance.class.getName()+"$PlayerState");
        var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);var value=ctor.newInstance();
        set(value,"state",state);set(value,"launchCooldown",cooldown);return value;
    }
}
