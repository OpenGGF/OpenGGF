package com.openggf.game.sonic2.competition;

import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionTeleportSwap {
    @Test void nativeAwardSwapsActorsCamerasBanksAndLeavesProgressAndHistoryOutsideTheTable() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            var s=w.session; var p1=s.player(0); var p2=s.player(1);
            var progress1=s.playerState(0); var progress2=s.playerState(1);
            NativePositionOps.writeXPosResetSubpixel(p1,0x120); NativePositionOps.writeYPosResetSubpixel(p1,0x200);
            NativePositionOps.writeXPosResetSubpixel(p2,0x400); NativePositionOps.writeYPosResetSubpixel(p2,0x300);
            p1.setXSpeed((short)0x123); p2.setXSpeed((short)-0x456);
            p1.setYSpeed((short)0x789); p2.setYSpeed((short)0x321);
            p1.setGSpeed((short)0x234); p2.setGSpeed((short)-0x345);
            p1.setInvulnerableFrames(17); p2.setInvulnerableFrames(31);
            p1.giveSpeedShoes();
            PlayableSpriteInternalAccess.publishNativeSpeedWords(p1,0xC00,0x18,0x80);
            PlayableSpriteInternalAccess.publishNativeSpeedWords(p2,0x600,0xC,0x80);
            p1.getXHistory()[9]=1234; p2.getXHistory()[9]=2345;
            var stat1=p1.copyStatusHistory(); var stat2=p2.copyStatusHistory();
            s.camera(0).setX((short)0x100);s.camera(1).setX((short)0x300);
            s.camera(0).setY((short)0x200);s.camera(1).setY((short)0x400);
            p1.setHighPriority(true);p2.setHighPriority(false);
            s.gameState(0).addScore(100);s.gameState(1).addScore(200);
            var bank1=s.playerState(0).capture();var bank2=s.playerState(1).capture();
            var camera1=s.camera(0).capture();var camera2=s.camera(1).capture();
            var actor1 = captureActor(p1); var actor2 = captureActor(p2);
            w.host.requestTeleport(s);
            compareActorAndProbeEveryComparison(p1, actor2, 0x13);
            compareActorAndProbeEveryComparison(p2, actor1, 0x0F);
            compareCameraAndProbeEveryComparison(s.camera(0),p1,camera1,camera2,true);
            compareCameraAndProbeEveryComparison(s.camera(1),p2,camera2,camera1,false);
            assertSame(p1,s.player(0));assertSame(p2,s.player(1));
            assertSame(progress1,s.playerState(0));assertSame(progress2,s.playerState(1));
            assertEquals(NativeTeleportTestWorld.value(bank1),NativeTeleportTestWorld.value(s.playerState(0).capture()));
            assertEquals(NativeTeleportTestWorld.value(bank2),NativeTeleportTestWorld.value(s.playerState(1).capture()));
            assertEquals(0x400,p1.getCentreX());assertEquals(0x120,p2.getCentreX());
            assertEquals(0x300,p1.getCentreY());assertEquals(0x200,p2.getCentreY());
            assertEquals(-0x456,p1.getXSpeed());assertEquals(0x123,p2.getXSpeed());
            assertEquals(0x321,p1.getYSpeed());assertEquals(0x789,p2.getYSpeed());
            assertEquals(-0x345,p1.getGSpeed());assertEquals(0x234,p2.getGSpeed());
            assertEquals(31,p1.getInvulnerableFrames());assertEquals(17,p2.getInvulnerableFrames());
            assertFalse(p1.hasSpeedShoes());assertTrue(p2.hasSpeedShoes());
            assertEquals(0x600,PlayableSpriteInternalAccess.nativeMaximumSpeedWord(p1));
            assertEquals(0xC00,PlayableSpriteInternalAccess.nativeMaximumSpeedWord(p2));
            assertEquals(2345,p1.getXHistory()[9]);assertEquals(1234,p2.getXHistory()[9]);
            assertArrayEquals(stat1,p1.copyStatusHistory());assertArrayEquals(stat2,p2.copyStatusHistory());
            assertFalse(p1.isHighPriority());assertTrue(p2.isHighPriority());
            assertEquals(0,p1.getMappingFrame());assertEquals(0,p2.getMappingFrame());
            assertEquals(0x300,s.camera(0).getX());assertEquals(0x100,s.camera(1).getX());
            assertEquals(0x280,s.camera(0).getY());assertEquals(0x80,s.camera(1).getY());
            assertTrue(s.camera(0).isMaxYChanging());
            assertEquals(1,w.backgrounds.swaps);assertEquals(11,w.backgrounds.first.x());assertEquals(1,w.planeSwaps);
            assertEquals(64,s.teleportTimer());
        });
    }
    @Test void shoesTravelWithTheirWordBankButSuperFlagAndExpiryStayWithTheNativeSlot() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            var p1=w.session.player(0);var p2=w.session.player(1);
            p1.setSuperSonic(true);
            PlayableSpriteInternalAccess.publishNativeSpeedWords(p1,0xA00,0x30,0x100);
            p2.giveSpeedShoes();
            p2.currentTimerManager().getTimerForCode("SpeedShoes-"+p2.getCode()).setTicks(1);
            w.host.requestTeleport(w.session);
            assertTrue(p1.isSuperSonic());assertFalse(p2.isSuperSonic());
            assertEquals(0xC00,PlayableSpriteInternalAccess.nativeMaximumSpeedWord(p1));
            assertEquals(0xA00,PlayableSpriteInternalAccess.nativeMaximumSpeedWord(p2),
                    "shipped teleport leaves Sonic's effective Super words on ordinary Tails");
            p1.currentTimerManager().updateDisplayPhaseTimersFor(p1);
            assertFalse(p1.hasSpeedShoes());
            assertEquals(0xA00,PlayableSpriteInternalAccess.nativeMaximumSpeedWord(p1));
            assertEquals(0x30,PlayableSpriteInternalAccess.nativeAccelerationWord(p1));
            assertEquals(0x100,PlayableSpriteInternalAccess.nativeDecelerationWord(p1));
            p2.giveSpeedShoes();
            p2.currentTimerManager().getTimerForCode("SpeedShoes-"+p2.getCode()).setTicks(1);
            p2.currentTimerManager().updateDisplayPhaseTimersFor(p2);
            assertEquals(0x600,PlayableSpriteInternalAccess.nativeMaximumSpeedWord(p2));
            assertEquals(0xC,PlayableSpriteInternalAccess.nativeAccelerationWord(p2));
            assertEquals(0x80,PlayableSpriteInternalAccess.nativeDecelerationWord(p2));
        });
    }

    @Test void levelOnlyRangeStillRemembersTrackedNativePlacementsOnDeletion() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            var objects=w.host.context().getObjectManager();
            assertEquals(0x90,objects.getLastProcessSlotExclusive());
            var object=objects.getActiveObjects().stream()
                    .filter(o->o.getSpawn()!=null && o.getSpawn().respawnTracked() && !o.isDestroyed())
                    .filter(o->o instanceof com.openggf.level.objects.AbstractObjectInstance)
                    .map(o->(com.openggf.level.objects.AbstractObjectInstance)o).findFirst().orElseThrow();
            var spawn=w.host.context().getLevelManager().getLevel().getObjects().stream()
                    .filter(row->row.layoutIndex()==object.getSpawn().layoutIndex()).findFirst().orElseThrow();
            assertFalse(objects.isRemembered(spawn));
            object.setDestroyed(false);object.setDestroyed(true);w.tick();
            assertTrue(objects.isRemembered(spawn),"tracked deletion: "+object.getClass().getName()
                    +" spawn="+spawn+" destroyed="+object.isDestroyed()+" respawnable="+object.isDestroyedRespawnable());
        });
    }

    @Test void repeatedIdAndParentWritesPreserveExistingFixedEffectAnimationAndIdentity() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            w.host.requestTeleport(w.session);
            var p=w.session.player(0);
            var shield=(com.openggf.level.objects.ShieldObjectInstance)p.getShieldObject();
            var stars=(com.openggf.level.objects.InvincibilityStarsObjectInstance)p.getInvincibilityObject();
            shield.update(123,p);stars.update(123,p);
            var shieldState=NativeTeleportTestWorld.value(shield.captureRewindState());
            var starState=NativeTeleportTestWorld.value(stars.captureRewindState());
            w.host.requestTeleport(w.session);
            assertSame(shield,p.getShieldObject());assertSame(stars,p.getInvincibilityObject());
            assertEquals(shieldState,NativeTeleportTestWorld.value(shield.captureRewindState()));
            assertEquals(starState,NativeTeleportTestWorld.value(stars.captureRewindState()));
        });
    }

    @Test void rollingRadiiTravelWithoutStandingNormalization() throws Exception {
        NativeTeleportTestWorld.run(w -> {
            var one=w.session.player(0);var two=w.session.player(1);
            one.setRollingFlagPreserveRadii(true);two.setRollingFlagPreserveRadii(true);
            one.applyCustomRadii(7,14);two.applyCustomRadii(7,10);
            w.host.requestTeleport(w.session);
            assertTrue(one.getRolling());assertTrue(two.getRolling());
            assertEquals(10,one.getYRadius());assertEquals(14,two.getYRadius());
            assertEquals(7,one.getXRadius());assertEquals(7,two.getXRadius());
        });
    }

    private static Record captureActor(com.openggf.sprites.playable.AbstractPlayableSprite player) throws Exception {
        var method=com.openggf.sprites.playable.AbstractPlayableSprite.class.getDeclaredMethod("captureNativeTeleportState");
        method.setAccessible(true);return (Record)method.invoke(player);
    }
    private static void compareActorAndProbeEveryComparison(
            com.openggf.sprites.playable.AbstractPlayableSprite player, Record incoming, int standingRadius) throws Exception {
        var good=captureActor(player);var components=good.getClass().getRecordComponents();
        var types=java.util.Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
        var constructor=good.getClass().getDeclaredConstructor(types);constructor.setAccessible(true);
        var apply=com.openggf.sprites.playable.AbstractPlayableSprite.class.getDeclaredMethod(
                "applyNativeTeleportState",good.getClass());apply.setAccessible(true);
        Object[] values=new Object[components.length];
        for(int i=0;i<components.length;i++) {
            var accessor=components[i].getAccessor();accessor.setAccessible(true);
            values[i]=accessor.invoke(good);
            Object expected=accessor.invoke(incoming);
            if(components[i].getName().equals("mappingFrame"))expected=0;
            if(components[i].getName().equals("xRadius"))expected=(short)9;
            if(components[i].getName().equals("yRadius"))expected=(short)standingRadius;
            assertField(expected,values[i],components[i].getName());
        }
        // Corrupt each actual field individually after a real production swap, prove its
        // comparison rejects it, then restore. This exercises every comparison deliberately.
        for(int i=0;i<components.length;i++) {
            var component=components[i];var accessor=component.getAccessor();accessor.setAccessible(true);
            if(component.getName().equals("shoeTime")) {
                var timer=player.currentTimerManager().getTimerForCode("SpeedShoes-"+player.getCode());
                if(timer!=null) {
                    int ticks=timer.getTicks();timer.setTicks(ticks-1);
                    assertThrows(AssertionError.class,()->assertField(ticks,accessor.invoke(captureActor(player)),"shoeTime"));
                    timer.setTicks(ticks);
                }
                continue;
            }
            Object[] corrupt=values.clone();corrupt[i]=different(values[i],component.getType());
            apply.invoke(player,constructor.newInstance(corrupt));
            Object expected=values[i];
            assertThrows(AssertionError.class,()->assertField(expected,accessor.invoke(captureActor(player)),component.getName()),component.getName());
            apply.invoke(player,good);
        }
    }
    private static void compareCameraAndProbeEveryComparison(com.openggf.camera.Camera camera,
            com.openggf.sprites.playable.AbstractPlayableSprite player,
            com.openggf.game.rewind.snapshot.CameraSnapshot own,
            com.openggf.game.rewind.snapshot.CameraSnapshot incoming, boolean primary) throws Exception {
        var good=camera.capture();var components=good.getClass().getRecordComponents();
        var constructor=good.getClass().getDeclaredConstructor(java.util.Arrays.stream(components)
                .map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new));
        var transferred=java.util.Set.of("x","y","minX","minY","maxX","maxY",
                "renderCopyX","renderCopyY","horizScrollDelayFrames","yPosBias");
        Object[] values=new Object[components.length];
        for(int i=0;i<components.length;i++) {
            var c=components[i];var accessor=c.getAccessor();values[i]=accessor.invoke(good);
            Object expected=accessor.invoke(transferred.contains(c.getName())?incoming:own);
            if(c.getName().equals("y"))expected=(short)(incoming.y()-0x180);
            if(c.getName().equals("maxYChanging")&&primary)expected=true;
            assertField(expected,values[i],"camera."+c.getName());
        }
        for(int i=0;i<components.length;i++) {
            var c=components[i];Object[] corrupt=values.clone();corrupt[i]=different(values[i],c.getType());
            camera.restore(constructor.newInstance(corrupt),player);
            Object expected=values[i];
            assertThrows(AssertionError.class,()->assertField(expected,c.getAccessor().invoke(camera.capture()),c.getName()));
            camera.restore(good,player);
        }
    }

    private static Object different(Object value,Class<?> type) {
        if(type==boolean.class)return !(boolean)value;
        if(type==byte.class)return (byte)((byte)value+1);
        if(type==short.class)return (short)((short)value+1);
        if(type==int.class)return (int)value==0?1:(int)value-1;
        if(type==Integer.class)return value==null?2:null;
        if(type==short[].class){var copy=((short[])value).clone();copy[0]++;return copy;}
        if(type.isEnum())for(var entry:type.getEnumConstants())if(entry!=value)return entry;
        throw new AssertionError("No mutation for "+type);
    }
    private static void assertField(Object expected,Object actual,String field) {
        if(expected instanceof short[] array)assertArrayEquals(array,(short[])actual,field);
        else assertEquals(expected,actual,field);
    }

}
