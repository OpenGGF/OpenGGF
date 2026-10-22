package com.openggf.game.sonic3k.objects;

import com.openggf.game.GroundMode;
import com.openggf.game.rules.GameRules;
import com.openggf.level.objects.ObjectManager;
import java.util.List;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestFbzMovingSqueezeTraversal {

    @BeforeEach
    void exposeTheLiveButtonToTheNativeRenderGate() {
        AbstractObjectInstance.updateCameraBounds(0x1C80, 0x700, 0x1DC0, 0x7E0, 0);
    }

    @AfterEach
    void resetCameraBounds() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }


    @Test
    void exactLiveFlatButtonLatchIsLaunchFloorAuthority() {
        Sonic3kButtonObjectInstance button = new Sonic3kButtonObjectInstance(
                new ObjectSpawn(0x1D38, 0x077A, 0x33, 0x20,
                        0, false, 273));
        button.snapshotPreUpdatePosition();
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        int surfaceY = button.getY() + button.getSolidParams().offsetY()
                - button.getSolidParams().groundHalfHeight();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(button);
        when(player.getCentreX()).thenReturn((short) button.getX());
        when(player.getCentreY()).thenReturn((short) (surfaceY - 0x13 - 1));
        when(player.getStandYRadius()).thenReturn((short) 0x13);
        when(player.getYRadius()).thenReturn((short) 0x13);

        assertTrue(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player));
    }

    @Test
    void offscreenButtonLatchDoesNotSupplyLaunchFloorAuthority() {
        Sonic3kButtonObjectInstance button = liveButton();
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        int surfaceY = button.getY() + button.getSolidParams().offsetY()
                - button.getSolidParams().groundHalfHeight();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(button);
        when(player.getCentreX()).thenReturn((short) button.getX());
        when(player.getCentreY()).thenReturn((short) (surfaceY - 0x13 - 1));
        when(player.getYRadius()).thenReturn((short) 0x13);
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);

        assertFalse(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player),
                "loc_2C62C skips the whole solid routine while the button is offscreen");
    }

    @Test
    void rollingButtonLatchUsesLiveRadiusAndPreservedFeet() {
        Sonic3kButtonObjectInstance button = liveButton();
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        int surfaceY = button.getY() + button.getSolidParams().offsetY()
                - button.getSolidParams().groundHalfHeight();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(button);
        when(player.getCentreX()).thenReturn((short) button.getX());
        when(player.getCentreY()).thenReturn((short) (surfaceY - 0x0E - 1));
        when(player.getStandYRadius()).thenReturn((short) 0x13);
        when(player.getYRadius()).thenReturn((short) 0x0E);
        when(player.getRolling()).thenReturn(true);

        assertTrue(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player));
    }

    @Test
    void retainedButtonRideAtMathematicalSurfaceIsLaunchFloorAuthority() {
        Sonic3kButtonObjectInstance button = liveButton();
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        int surfaceY = button.getY() + button.getSolidParams().offsetY()
                - button.getSolidParams().groundHalfHeight();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(button);
        when(player.getCentreX()).thenReturn((short) button.getX());
        when(player.getCentreY()).thenReturn((short) (surfaceY - 0x0E));
        when(player.getStandYRadius()).thenReturn((short) 0x13);
        when(player.getYRadius()).thenReturn((short) 0x0E);
        when(player.getRolling()).thenReturn(true);

        assertTrue(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player));
    }

    @Test
    void buttonLatchTwoPixelsAboveSurfaceIsNotLaunchFloorAuthority() {
        Sonic3kButtonObjectInstance button = liveButton();
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        int surfaceY = button.getY() + button.getSolidParams().offsetY()
                - button.getSolidParams().groundHalfHeight();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(button);
        when(player.getCentreX()).thenReturn((short) button.getX());
        when(player.getCentreY()).thenReturn((short) (surfaceY - 0x0E - 2));
        when(player.getStandYRadius()).thenReturn((short) 0x13);
        when(player.getYRadius()).thenReturn((short) 0x0E);
        when(player.getRolling()).thenReturn(true);

        assertFalse(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player));
    }

    @Test
    void buttonLatchOnePixelBelowSurfaceIsNotLaunchFloorAuthority() {
        Sonic3kButtonObjectInstance button = liveButton();
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        int surfaceY = button.getY() + button.getSolidParams().offsetY()
                - button.getSolidParams().groundHalfHeight();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(button);
        when(player.getCentreX()).thenReturn((short) button.getX());
        when(player.getCentreY()).thenReturn((short) (surfaceY - 0x0E + 1));
        when(player.getStandYRadius()).thenReturn((short) 0x13);
        when(player.getYRadius()).thenReturn((short) 0x0E);
        when(player.getRolling()).thenReturn(true);

        assertFalse(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player));
    }

    @Test
    void arbitraryObjectLatchIsNotLaunchFloorAuthority() {
        AbstractPlayableSprite player = ordinaryFlatPlayer();
        when(player.isOnObject()).thenReturn(true);
        when(player.getLatchedSolidObjectInstance()).thenReturn(mock(ObjectInstance.class));

        assertFalse(FbzMovingSqueezeTraversal.hasLaunchFloorAuthority(player));
    }

    private static AbstractPlayableSprite ordinaryFlatPlayer() {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getGroundMode()).thenReturn(GroundMode.GROUND);
        when(player.getAngle()).thenReturn((byte) 0);
        return player;
    }

    private static Sonic3kButtonObjectInstance liveButton() {
        Sonic3kButtonObjectInstance button = new Sonic3kButtonObjectInstance(
                new ObjectSpawn(0x1D38, 0x077A, 0x33, 0x20,
                        0, false, 273));
        button.snapshotPreUpdatePosition();
        return button;
    }

    @Test
    void terrainApproachPairsOnlyOverlappingHorizontalSupport() {
        assertTrue(FbzMovingSqueezeTraversal.spansOverlap(100, 140, 120, 180));
        assertTrue(FbzMovingSqueezeTraversal.spansOverlap(100, 140, 140, 180));
        assertFalse(FbzMovingSqueezeTraversal.spansOverlap(100, 139, 140, 180));
    }

    @Test
    void terrainFeetAcquireOnlyAReachableCarSurfaceBelowThem() {
        assertFalse(FbzMovingSqueezeTraversal.canAcquireCarSurface(0x780, 0x77F, 0x23));
        assertTrue(FbzMovingSqueezeTraversal.canAcquireCarSurface(0x780, 0x780, 0x23));
        assertTrue(FbzMovingSqueezeTraversal.canAcquireCarSurface(0x780, 0x7A3, 0x23));
        assertFalse(FbzMovingSqueezeTraversal.canAcquireCarSurface(0x780, 0x7A4, 0x23));
    }

    @Test
    void clearanceRequiresActualCarAcquisitionAndOverlapTraversal() {
        assertFalse(new FbzMovingSqueezeTraversal.Projection(
                true, true, false, false, false,
                0x1D90, 0x30, 0x0800, 0x1E00).clears());
        assertFalse(new FbzMovingSqueezeTraversal.Projection(
                true, true, true, false, false,
                0x1D90, 0x30, 0x0800, 0x1E00).clears());
        assertTrue(new FbzMovingSqueezeTraversal.Projection(
                true, true, true, true, false,
                0x1D90, 0x30, 0x0800, 0x1E00).clears());
    }
    @Test
    void discoveryQueriesCarsOnceAndRetainsWinningProjectionsWithUnchangedTieRejection() {
        // A later nearer pair resolves an earlier tie; a tie behind the winner does not invalidate it.
        int[][] dangers={{70,70,60},{60,70,70},{70,70,80},{70,80,90}};
        int[] winners={2,0,-1,0};
        for(int scenario=0;scenario<dangers.length;scenario++) {
            var player=ordinaryFlatPlayer();
            when(player.getGameRules()).thenReturn(GameRules.SONIC_3K);
            when(player.getGSpeed()).thenReturn((short)0x318);
            var objects=mock(ObjectManager.class);
            var blocks=List.of(block(80,1),block(90,2),block(100,3));
            var car=new FbzElevatorObjectInstance.Car(80,100,80,-1); car.setSlotIndex(4);
            when(objects.activeObjectsOfType(Sonic3kInvisibleBlockObjectInstance.class)).thenReturn(blocks);
            when(objects.activeObjectsOfType(FbzElevatorObjectInstance.Car.class)).thenReturn(List.of(car));
            var nativeProjections=new FbzMovingSqueezeTraversal.Projection[3];
            var currentProjections=new FbzMovingSqueezeTraversal.Projection[3];
            for(int i=0;i<3;i++) {
                nativeProjections[i]=new FbzMovingSqueezeTraversal.Projection(
                        true,true,true,true,false,dangers[scenario][i],29,0x800,160);
                currentProjections[i]=new FbzMovingSqueezeTraversal.Projection(
                        false,false,true,true,true,dangers[scenario][i],28,0x318,-1);
            }
            // Selective answers avoid executing nested real static calls while setting up stubs.
            try(var geometry=mockStatic(FbzMovingSqueezeTraversal.class,invocation -> {
                if(invocation.getMethod().getName().equals("project")) {
                    FbzMovingSqueezeTraversal.Episode episode=invocation.getArgument(0);
                    int index=blocks.indexOf(episode.block());
                    return (int)invocation.getArgument(2)==0x800 ? nativeProjections[index] : currentProjections[index];
                }
                return invocation.callRealMethod();
            })) {
                var result=FbzMovingSqueezeTraversal.findProjectedEpisode(objects,player);
                if(winners[scenario]<0) assertTrue(result.isEmpty());
                else {
                    var winner=result.orElseThrow(); int index=winners[scenario];
                    assertSame(blocks.get(index),winner.episode().block());
                    assertSame(nativeProjections[index],winner.nativeFloor());
                    assertSame(currentProjections[index],winner.current());
                }
                verify(objects,times(1)).activeObjectsOfType(FbzElevatorObjectInstance.Car.class);
                for(var block:blocks) {
                    var episode=new FbzMovingSqueezeTraversal.Episode(block,car);
                    geometry.verify(() -> FbzMovingSqueezeTraversal.project(episode,player,0x800),times(1));
                    geometry.verify(() -> FbzMovingSqueezeTraversal.project(episode,player,0x318),times(1));
                }
                assertEquals(result.map(FbzMovingSqueezeTraversal.ProjectedEpisode::episode),
                        FbzMovingSqueezeTraversal.findEpisode(objects,player),"existing discovery API retains its selection");
            }
        }
    }

    @Test
    void discoveryDoesNotQueryCarsWhenEveryBlockIsBehindTheLaunchPoint() {
        var player=ordinaryFlatPlayer();
        when(player.getGSpeed()).thenReturn((short)0x318);
        when(player.getCentreX()).thenReturn((short)1000);
        var objects=mock(ObjectManager.class);
        when(objects.activeObjectsOfType(Sonic3kInvisibleBlockObjectInstance.class)).thenReturn(List.of(block(80,1)));
        assertTrue(FbzMovingSqueezeTraversal.findEpisode(objects,player).isEmpty());
        verify(objects,never()).activeObjectsOfType(FbzElevatorObjectInstance.Car.class);
    }

    private static Sonic3kInvisibleBlockObjectInstance block(int x,int slot) {
        var block=new Sonic3kInvisibleBlockObjectInstance(new ObjectSpawn(x,0,0x28,0,0,false,0));
        block.setSlotIndex(slot);
        block.snapshotPreUpdatePosition();
        return block;
    }

}
