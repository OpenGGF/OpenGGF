package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.badniks.SpikerBadnikInstance;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** LANDING_GUARD changes initial body detection only, never exit or projectile commitment. */
class TestWestSpikerDetectionContract {
    @ParameterizedTest @ValueSource(ints = {64,80,95})
    void nativeExitAndBothLauncherGatesStayAt64ForStockAndLandingGuard(int range) throws Exception {
        for (boolean guard : new boolean[]{false,true}) {
            Sonic player = player(1000 - range);
            var services = new StubObjectServices().withPlayerQuery(new ObjectPlayerQuery(() -> player, List::of));
            var body = new SpikerBadnikInstance(new ObjectSpawn(1000,500,0x9C,0,0,false,0), guard);
            body.setServices(services);
            state(body, "OPEN"); set(body,"spikesExtended",true);
            Method open = body.getClass().getDeclaredMethod("updateOpen", AbstractPlayableSprite.class);
            open.setAccessible(true); open.invoke(body,player);
            assertEquals("DESCEND", value(body,"state").toString());
            assertEquals(7,value(body,"stateTimer")); assertEquals(false,value(body,"spikesExtended"));
            for (boolean left : new boolean[]{false,true}) {
                set(body,"spikesExtended",true);
                Class<?> type = Class.forName(SpikerBadnikInstance.class.getName()+"$SpikerSideLauncherChild");
                Constructor<?> ctor = type.getDeclaredConstructor(SpikerBadnikInstance.class,boolean.class);
                ctor.setAccessible(true);
                AbstractObjectInstance launcher = (AbstractObjectInstance) ctor.newInstance(body,left);
                launcher.setServices(services); state(launcher,"ARMED");
                player.setCentreX((short)(launcher.getX()+(left?-range:range)));
                launcher.update(0,player);
                assertEquals("ARMED",value(launcher,"state").toString(), "64..95 cannot commit a projectile on either side");
            }
        }
    }

    @Test void landingGuardKeepsTheActualEightRiseMovesAndOriginalLauncherAnimationProjectileAndGravity() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0,0,320,224,0);
        Sonic player = player(0x100);
        List<ObjectInstance> children = new ArrayList<>(); List<Integer> sounds = new ArrayList<>();
        ObjectManager manager = mock(ObjectManager.class);
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public void playSfx(int sound) { sounds.add(sound); }
        };
        services.withPlayerQuery(new ObjectPlayerQuery(() -> player,List::of));
        doAnswer(call -> { AbstractObjectInstance child=call.getArgument(0); child.setServices(services); children.add(child); return null; })
                .when(manager).addDynamicObjectAfterCurrent(any());
        var body = new SpikerBadnikInstance(new ObjectSpawn(0x120,0x100,0x9C,0,0,false,0),true);
        body.setServices(services); body.update(0,player); body.update(1,player);
        assertEquals(3,children.size());
        for (int frame=2;frame<=10;frame++) body.update(frame,player);
        assertEquals("OPEN",value(body,"state").toString()); assertEquals(0xF8,body.getY());
        AbstractObjectInstance launcher=children.stream().map(AbstractObjectInstance.class::cast)
                .filter(child -> child.getName().equals("SpikerLeftLauncher")).findFirst().orElseThrow();
        launcher.update(11,player); launcher.update(12,player);
        assertEquals("ATTACK",value(launcher,"state").toString());
        for (int frame=13;frame<=28;frame++) launcher.update(frame,player);
        assertFalse(sounds.contains(Sonic3kSfx.PROJECTILE.id)); assertEquals(3,children.size());
        launcher.update(29,player);
        assertTrue(sounds.contains(Sonic3kSfx.PROJECTILE.id)); assertEquals(4,children.size());
        AbstractObjectInstance projectile=(AbstractObjectInstance)children.getLast();
        assertEquals(0x10C,projectile.getX()); int startY=projectile.getY();
        projectile.update(30,player);
        assertEquals(0x10A,projectile.getX()); assertEquals(startY-2,projectile.getY());
        assertEquals(-0x1E0,value(projectile,"yVelocity"));
    }
    @Test void initialDetectionOnlyPumpsThreeNativeWarningCyclesOutsideTheProjectileGate() throws Exception {
        AbstractObjectInstance.updateCameraBounds(0,0,320,224,0);
        for (boolean guard : new boolean[]{false,true}) {
            Sonic player = player(0xD0); // Body distance80, left-launcher distance60.
            List<AbstractObjectInstance> children = new ArrayList<>();
            ObjectManager manager = mock(ObjectManager.class);
            StubObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return manager; }
            };
            services.withPlayerQuery(new ObjectPlayerQuery(() -> player,List::of));
            doAnswer(call -> { AbstractObjectInstance child=call.getArgument(0); child.setServices(services); children.add(child); return null; })
                    .when(manager).addDynamicObjectAfterCurrent(any());
            var body = new SpikerBadnikInstance(new ObjectSpawn(0x120,0x100,0x9C,0,0,false,0),guard);
            body.setServices(services);
            Map<String,Integer> counts = new HashMap<>();
            int attackUpdates = 0, minY = 0x100;
            for (int frame=0;frame<56;frame++) {
                body.update(frame,player);
                for (var child : List.copyOf(children)) {
                    child.update(frame,player);
                    if (child.getName().contains("Launcher") && value(child,"state").toString().equals("ATTACK")) attackUpdates++;
                }
                if (frame>=2) counts.merge(value(body,"state").toString(),1,Integer::sum);
                minY = Math.min(minY,body.getY());
            }
            assertEquals(3,children.size(),"native children only; no projectile was spawned");
            assertEquals(0,attackUpdates,"initial warning range does not widen projectile pressure");
            assertEquals(0x100,body.getY());
            if (guard) {
                assertEquals(Map.of("DETECT",3,"RISE",24,"OPEN",3,"DESCEND",24),counts);
                assertEquals(0xF8,minY);
            } else {
                assertEquals(Map.of("DETECT",54),counts);
                assertEquals(0x100,minY);
            }
        }
    }
    private static Sonic player(int x) { Sonic player=new Sonic("sonic",(short)0,(short)0);player.setCentreX((short)x);player.setCentreY((short)0x100);return player; }
    private static Field field(Object o,String name) throws Exception { for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try {Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){} throw new NoSuchFieldException(name); }
    private static Object value(Object o,String name) throws Exception { return field(o,name).get(o); }
    private static void set(Object o,String name,Object value) throws Exception { field(o,name).set(o,value); }
    private static void state(Object o,String name) throws Exception { Field f=field(o,"state");for(Object e:f.getType().getEnumConstants())if(e.toString().equals(name)){f.set(o,e);return;}throw new AssertionError(name); }
}
