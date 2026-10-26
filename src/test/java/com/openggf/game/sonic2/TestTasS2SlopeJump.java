package com.openggf.game.sonic2;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.kis2.Kis2Rules;
import com.openggf.level.*;
import com.openggf.physics.*;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.*;
import com.openggf.tests.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 3557S OOZ1 / 5373S CP2: sensed downhill slope adds horizontal jump impulse. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestTasS2SlopeJump {
    private static class LockOnKnuckles extends Knuckles {
        LockOnKnuckles() { super("knuckles", (short) 0, (short) 0); setGameRulesForTest(Kis2Rules.RULES); applyExternalPhysicsProfile(com.openggf.game.sonic2.kis2.Kis2Physics.KNUCKLES); }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sensedDownhillJumpAddsSpeedThatNeutralAndFlatLaunchDoNot(boolean lockOn) {
        var slope = launch(lockOn, true, true);
        var neutral = launch(lockOn, true, false);
        var flat = launch(lockOn, false, true);
        assertTrue(slope.vx() > neutral.vx(), "the jump itself adds horizontal speed");
        assertTrue(slope.vx() > flat.vx(), "flat jump has no horizontal impulse");
        assertTrue(slope.vy() < 0); assertTrue(flat.vy() < slope.vy());
    }
    private record Exit(int vx, int vy) { }
    private static Exit launch(boolean lockOn, boolean sloped, boolean jump) {
        TestEnvironment.resetAll();
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(mock(Level.class));
        byte[] full = new byte[16]; Arrays.fill(full, (byte)16);
        byte[] ramp = new byte[16];
        for (int i = 0; i < 16; i++) ramp[i] = (byte)(16 - i);
        var fullTile = new SolidTile(1, full, full.clone(), (byte)0);
        var slopeTile = new SolidTile(2, ramp, ramp.clone(), (byte)0x20);
        var solid = new ChunkDesc(0xF001); var slope = new ChunkDesc(0xF002);
        // Authored continuous 45-degree down-right plane y=x; flat control y=256.
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean())).thenAnswer(call -> {
            int x = call.getArgument(1), y = call.getArgument(2);
            int floorTileY = sloped ? (x & ~15) : 256;
            return y < floorTileY ? null : sloped && y < floorTileY + 16 ? slope : solid;
        });
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt())).thenAnswer(c -> c.getArgument(0) == slope ? slopeTile : fullTile);
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt(), anyBoolean())).thenAnswer(c -> c.getArgument(0) == slope ? slopeTile : fullTile);
        GroundSensor.setLevelManager(level);
        var terrain = new TerrainCollisionManager(); var collision = new CollisionSystem(terrain);
        var mode = TestEnvironment.activeGameplayMode();
        mode.attachLevelManagers(mode.getWaterSystem(), mode.getParallaxManager(), terrain, collision, mode.getSpriteManager(), level);
        AbstractPlayableSprite player = lockOn ? new LockOnKnuckles() : new Sonic("sonic", (short)0, (short)0);
        player.setCentreX((short)264); player.setCentreY((short)200);
        player.setAir(true); player.setYSpeed((short)0x100);
        var camera = GameServices.camera(); camera.setMaxX((short)0x2000); camera.setMaxY((short)0x2000);
        var movement = new PlayableSpriteMovement(player, collision, GameServices.gameState());
        for (int i = 0; player.getAir() && i < 40; i++)
            movement.handleMovement(false,false,false,false,false,false,false,false);
        assertFalse(player.getAir(), "must land through actual terrain sensors");
        assertEquals(sloped ? 0x20 : 0, player.getAngle() & 255, "the launch angle must come from terrain");
        assertSame(level, player.currentLevelManager());
        // Valid incoming momentum is declared; neither the jump nor its added velocity is seeded.
        player.setGSpeed((short)0x500);
        player.setXSpeed((short)(sloped ? 0x389 : 0x500));
        player.setYSpeed((short)(sloped ? 0x389 : 0));
        int beforeX = player.getXSpeed(), beforeY = player.getYSpeed();
        player.setJumpInputPressed(jump);
        movement.handleMovement(false,false,false,false,jump,false,false,false);
        assertEquals(jump, player.getAir());
        if (jump) {
            // Independent native CalcSine $E0 = (sin=-181, cos=181); Sonic_Jump
            // (s2.asm:37345-37377). KiS2 Sonic_Jump (c336fede s2.asm:39958-40005)
            // uses $600 rather than $680 outside the demo mode.
            int impulse = lockOn ? 0x600 : 0x680;
            assertEquals(impulse, player.getJump(), "the actual character physics profile must be selected");
            // The jump-launch pass skips the later gravity owner (Sonic_MdNormal).
            assertEquals(beforeX + (sloped ? 181 * impulse >> 8 : 0), player.getXSpeed());
            assertEquals(beforeY + (sloped ? -181 * impulse >> 8 : -impulse), player.getYSpeed());
            assertTrue(player.isJumping());
        }
        verify(level, atLeastOnce()).getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean());
        return new Exit(player.getXSpeed(), player.getYSpeed());
    }
}
