package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.sprites.NativePositionOps;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Spikes, object $26 in the Level-dialect stages ({@code ObjSpikes}, Level/_Objects/Spikes/
 * Main.asm:7-84). The tile word comes from SetObjectTileID $A by secondary subtype. Upright
 * spikes are solid and hurt a player standing on them; Y-flipped spikes are solid with hurt
 * collision $83. Both run only while the player is within $20 pixels vertically (Spikes/Main.asm:37-45).
 *
 * <p>Differences from Sonic 1's Obj36: CD spikes do not hurt during post-hit invulnerability
 * ({@code tst.w oPlayerHurt}, Spikes/Main.asm:68-69) or a time warp (Spikes/Main.asm:59-60); there are no sideways spikes and
 * no subtype frames. The Y rewind before HurtPlayer is Sonic 1's (Spikes/Main.asm:70-75).
 */
public final class CdSpikes extends CdObject implements ReevaluatingSolidObject,
        TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    private boolean resolvedTile;

    public CdSpikes(ObjectSpawn spawn) {
        super(spawn, "Spikes");
        // ObjSpikes_Init: priority 3, oWidth $10, oYRadius 8; Y-flipped: oWidth $12 (scddisasm/src/Level/_Objects/Spikes/Main.asm:20-31).
        this.priority = 3;
        this.width = vFlip() ? 0x12 : 0x10;
        this.yRadius = 8;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!resolvedTile) {
            CdObjectArtProvider provider = artProvider();
            if (provider != null) {
                tileWord = provider.objectTileId(CdObjectArtProvider.TILE_ID_SPIKES, subtype2());
                resolvedTile = true;
            }
        }
        // ObjSpikes_Main: cmpi.w #$20,d0; bcc.s .Display on |player Y - Y| (scddisasm/src/Level/_Objects/Spikes/Main.asm:36-45).
        if (!(player instanceof AbstractPlayableSprite sprite)
                || Math.abs((short) (sprite.getCentreY() - y)) >= 0x20) return;
        if (vFlip()) {
            solidObject(ReevaluatingSolidObject.Entry.SOLID);  // Spikes/Main.asm:46-50
            return;
        }
        // .ChkStand: SolidObject; beq; btst #3,oFlags(a0); timeWarp; invincible (Spikes/Main.asm:54-61).
        if (!solidObject(ReevaluatingSolidObject.Entry.SOLID) || !stoodOn(sprite)
                || sprite.getInvincibleFrames() > 0) return;
        // cmpi.b #4,oRoutine; bcc; tst.w oPlayerHurt; bne (Spikes/Main.asm:66-69). Time warp is not hosted.
        if (sprite.isHurt() || sprite.getDead() || sprite.getInvulnerableFrames() > 0) return;
        // oY.l -= oYVel << 8 before HurtPlayer (Spikes/Main.asm:70-76).
        NativePositionOps.addYPos16_16(sprite, -(sprite.getYSpeed() << 8));
        boolean hadRings = sprite.getRingCount() > 0;
        if (hadRings && !sprite.hasShield()) services().spawnLostRings(sprite, vIntRunCount);
        sprite.applyHurtOrDeath(x, true, hadRings);
    }

    /** Y-flipped spikes: move.b #$83,oColType(a0) (Spikes/Main.asm:31). */
    @Override public int getCollisionFlags() { return vFlip() ? 0x83 : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!resolvedTile || tileWord < 0) return;
        drawFrame(CdObjectArtProvider.spikesKey(tileWord), 0, hFlip(), vFlip());
    }
}
