package com.openggf.sprites.managers;

import com.openggf.game.GameServices;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Native word arithmetic through the real boundary owner; full frame witnesses live in the TAS tests. */
class TestNativeLevelBoundaryWords {
    @ParameterizedTest
    @EnumSource(value=SonicGame.class, names={"SONIC_1","SONIC_2","SONIC_3K"})
    void wordWrapFractionAndEqualityBoundariesReplay(SonicGame game) throws Exception {
        TestEnvironment.configureGameModuleFixture(game);
        var camera=GameServices.camera();
        camera.setMinX((short)0);camera.setMaxX((short)0x1000);camera.setLevelStarted(false);
        var player=new Sonic("sonic",(short)0,(short)0);
        var movement=new PlayableSpriteMovement(player);
        var boundary=PlayableSpriteMovement.class.getDeclaredMethod("doLevelBoundary");
        boundary.setAccessible(true);
        int right=game==SonicGame.SONIC_3K?0x1128:0x1168;
        // Native CMP.W after 32-bit fixed-point ADD/SWAP. Expected words are
        // independent examples, including carry from the fractional high byte.
        int[][] cases={
            {24,0,-0x3000,right,0,0},        // underflow is unsigned far right
            {0xFFFF,0,0x300,16,0,0},        // word overflow is unsigned far left
            {24,0,-0x100,24,0,-0x100},      // ordinary unclamped prediction
            {16,0,-1,16,0,0},              // fractional underflow below left
            {16,0x100,-1,16,0x100,-1},      // fraction cancels one velocity unit
            {right-1,0xFF00,1,game==SonicGame.SONIC_3K?right-1:right,
                game==SonicGame.SONIC_3K?0xFF00:0,game==SonicGame.SONIC_3K?1:0},
            {right,0x55AA,0,right,game==SonicGame.SONIC_3K?0x55AA:0,0},
            {right,0xFF00,1,right,0,0}
        };
        for(int[] c:cases){
            player.setCentreX((short)c[0]);player.setSubpixelRaw(c[1],0x4321);
            player.setXSpeed((short)c[2]);player.setGSpeed((short)0x400);
            var before=player.captureRewindState();
            for(int replay=0;replay<2;replay++){
                if(replay!=0)player.restoreRewindState(before);
                boundary.invoke(movement);
                assertEquals(c[3],player.getCentreX()&0xFFFF,"x at inlet "+c[0]+" fraction "+c[1]);
                assertEquals(c[4],player.getXSubpixelRaw());assertEquals(c[5],player.getXSpeed());
                boolean clamped=c[3]!=c[0]||c[4]!=c[1]||c[5]!=c[2];
                // Equality can clamp with already-zero velocity and fraction.
                if(c[0]==16&&c[1]==0&&c[2]==-1)clamped=true;
                assertEquals(clamped?0:0x400,player.getGSpeed());
                assertEquals(0x4321,player.getYSubpixelRaw(),"X clamp cannot consume Y fraction");
            }
        }
    }
}
