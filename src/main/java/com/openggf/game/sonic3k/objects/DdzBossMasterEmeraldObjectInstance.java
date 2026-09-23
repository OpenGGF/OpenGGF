package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM {@code loc_81CC6} / {@code loc_81D0E} / {@code loc_81D4A} (sonic3k.asm:173812-173858): the
 * Master Emerald carried by the phase-2 ship ({@code ObjDat3_83226}: priority {@code $280},
 * {@code ArtTile_BossMasterEmerald} palette 3). It sits at the ship part's {@code $1C}, bobbing with
 * the part's animation ({@code child_dy = -4 + (frame - $3A)}). With all seven Super Emeralds
 * ({@code Collected_emeralds_array} all 3) it runs the {@code word_8141E} palette rotation on
 * palette line 4 colours 9 and 11. After the boss's {@code $38} bit 4 (exit) it keeps level with
 * Player 1 when Player 1 passes it, following the wrap offset and the camera delta.
 */
final class DdzBossMasterEmeraldObjectInstance extends AbstractDdzObjectInstance {
    private static final int OFFSET_X = 0x1C;
    private static final int OFFSET_Y = -4;
    private static final String OWNER = "s3k.ddz.masterEmerald";
    private int x;
    private int y;
    private boolean followingPlayer;
    private boolean rotating;
    private boolean initialized;
    DdzBossMasterEmeraldObjectInstance(DdzEndBossShipPartObjectInstance part) {
        super(new ObjectSpawn(part == null ? 0 : part.getX() + OFFSET_X, part == null ? 0 : part.getY() + OFFSET_Y,
                0, 0, 0, false, 0), "DDZBossMasterEmerald", part);
        if (part != null) {
            x = (part.getX() + OFFSET_X) & 0xFFFF;
            y = (part.getY() + OFFSET_Y) & 0xFFFF;
        }
    }

    /** Rewind probe for {@code ObjectRewindDynamicCodecs}; mirrors {@link #recreateForRewind}. */
    private DdzBossMasterEmeraldObjectInstance(ObjectSpawn spawn) {
        this((DdzEndBossShipPartObjectInstance) null);
    }

    @Override
    public DdzBossMasterEmeraldObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzBossMasterEmeraldObjectInstance((DdzEndBossShipPartObjectInstance) null);
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity playerEntity) {
        if (!initialized) {
            initialized = true;
            var gameState = services().gameState();
            rotating = gameState != null && gameState.hasAllSuperEmeralds();
            if (rotating) palette().install(services(),
                    com.openggf.game.sonic3k.constants.Sonic3kConstants.PAL_DDZ_MASTER_EMERALD_SCRIPT_ADDR);
            return;
        }
        if (followingPlayer) {
            x = (x - DdzObjectSupport.wrapOffset(services())) & 0xFFFF;
            AbstractPlayableSprite player = DdzObjectSupport.player(services());
            if (player != null && (player.getCentreX() & 0xFFFF) >= x) {
                x = player.getCentreX() & 0xFFFF;
            }
            x = (x + DdzObjectSupport.cameraDelta(services())) & 0xFFFF;
            return;
        }
        if (!(parent instanceof DdzEndBossShipPartObjectInstance part) || part.isDestroyed()) {
            goDelete();
            return;
        }
        int bob = (part.mappingFrame() - 0x3A) & 0xFF;
        x = (part.getX() + OFFSET_X) & 0xFFFF;
        y = (part.getY() + (byte) (OFFSET_Y + bob)) & 0xFFFF;
        if (rotating) {
            runRotation();
        }
        DdzEndBossObjectInstance boss = part.boss();
        if (boss != null && boss.flag(4)) {
            followingPlayer = true;
        }
    }

    private com.openggf.game.sonic3k.runtime.S3kEmeraldPaletteState palette() {
        return ((com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState)services().zoneRuntimeState()).emeraldPalette();
    }

    private void runRotation() { palette().tick(services(), OWNER); }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0x280);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable() || !initialized) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MASTER_EMERALD);
        if (renderer != null) {
            renderer.drawFrameIndex(0, x, y, false, false, 3);
        }
    }


}
