package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Monitor item, object $1A ({@code ObjMonitorItem}, Monitor and Time Post/Main.asm:419-638):
 * rises from -$300 slowing by $18 a frame, then gives its item and stays 30 frames. Items by
 * {@code oAnim}: 0 1UP, 1 ten rings (1UP at 100 and 200), 2 shield, 3 invincibility, 4 speed
 * shoes (1320 frames each on USA, Monitor and Time Post/Main.asm:539-543,572-576), 5 time stop, 6 combine ring, 7 S (shield,
 * invincibility and shoes). Time stop and the combine ring set {@code timeStopTimer} and
 * {@code combineRing}, which have no consumer yet; those two items are not hosted.
 */
public final class CdMonitorItem extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** USA: move.w #1320,objPlayerSlot+oPlayerInvinc.w / oPlayerShoes (Monitor and Time Post/Main.asm:539-543,572-576). */
    static final int USA_POWER_FRAMES = 1320;

    private final int item;
    private int routine;
    private int ySub;
    private int yVel;
    private int deleteTimer;

    public CdMonitorItem(int x, int y, int item, int layer) {
        super(new ObjectSpawn(x, y, 0x1A, item, 0, false, 0), "MonitorItem");
        this.item = item;
        // ObjMonitorItem_Init: tile $85A8, low priority on layer 2; priority 3; oWidth 8 (Monitor and Time Post/Main.asm:440-448).
        this.tileWord = layer != 0 ? CdObjectArtProvider.MONITOR_ITEM_TILE & 0x7FFF : CdObjectArtProvider.MONITOR_ITEM_TILE;
        this.priority = 3;
        this.width = 8;
    }

    public CdMonitorItem(ObjectSpawn spawn) {
        this(spawn.x(), spawn.y(), spawn.subtype(), 0);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                routine = 2;
                yVel = -0x300;
                main(player);
            }
            case 2 -> main(player);
            default -> {
                // ObjMonitorItem_Delete: subq.w #1; bmi DeleteObject (Monitor and Time Post/Main.asm:624-637).
                if (--deleteTimer < 0) setDestroyed(true);
            }
        }
    }

    /** ObjMonitorItem_Main (Monitor and Time Post/Main.asm:465-482). */
    private void main(PlayableEntity player) {
        if (yVel < 0) {
            int position = (y << 8) | ySub;
            position += yVel;
            y = position >> 8;
            ySub = position & 0xFF;
            yVel += 0x18;
            return;
        }
        routine = 4;
        deleteTimer = 30 - 1;
        give(player instanceof AbstractPlayableSprite sprite ? sprite : null);
    }

    private void give(AbstractPlayableSprite player) {
        var game = services().gameState();
        var state = services().levelGamestate();
        switch (item) {
            case 0 -> { oneUp(game); com.openggf.game.soniccd.audio.CdSoundRequests.oneUp(services()); }
            case 1 -> {
                // .GiveRings: +10; bset #1/#2 of livesFlags at 100/200 gives a 1UP (Monitor and Time Post/Main.asm:502-513).
                if (state == null) return;
                state.addRings(10);
                int flags = state.getRingExtraLifeFlags();
                if (state.getRings() >= 100 && (flags & 2) == 0) {
                    state.setRingExtraLifeFlags(flags | 2);
                    oneUp(game);
                    com.openggf.game.soniccd.audio.CdSoundRequests.oneUp(services());
                } else if (state.getRings() >= 200 && (flags & 4) == 0) {
                    state.setRingExtraLifeFlags(flags | 4);
                    oneUp(game);
                    com.openggf.game.soniccd.audio.CdSoundRequests.oneUp(services());
                } else {
                    services().playSfx(com.openggf.audio.GameSound.RING); // .RingSound: FM_RING
                }
            }
            case 2 -> { if (player != null) player.giveShield(); services().playSfx(com.openggf.game.soniccd.audio.CdSoundRequests.SHIELD); }
            case 3 -> { if (player != null) player.giveInvincibility(USA_POWER_FRAMES); com.openggf.game.soniccd.audio.CdSoundRequests.powerUpMusic(services(), com.openggf.game.soniccd.audio.CdSoundRequests.INVINCIBILITY); }
            case 4 -> { if (player != null) player.giveSpeedShoes(USA_POWER_FRAMES); com.openggf.game.soniccd.audio.CdSoundRequests.powerUpMusic(services(), com.openggf.game.soniccd.audio.CdSoundRequests.SPEED_SHOES); }
            case 7 -> {
                // .CheckS: shield, invincibility, then speed shoes (Monitor and Time Post/Main.asm:615-618).
                if (player != null) {
                    player.giveShield();
                    player.giveInvincibility(USA_POWER_FRAMES);
                    player.giveSpeedShoes(USA_POWER_FRAMES);
                }
                com.openggf.game.soniccd.audio.CdSoundRequests.sMonitor(services()); // .CheckS: shield, invincibility, shoes
            }
            default -> { }
        }
    }

    private static void oneUp(com.openggf.game.GameStateManager game) {
        if (game != null) game.addLife();   // .Give1UP: addq.b #1,lives (Monitor and Time Post/Main.asm:490-494)
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // oMapFrame = oAnim over the item's derived mappings (Monitor and Time Post/Main.asm:451-459).
        drawFrame(CdObjectArtProvider.MONITOR_ITEM, item, false, false);
    }

    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
}
