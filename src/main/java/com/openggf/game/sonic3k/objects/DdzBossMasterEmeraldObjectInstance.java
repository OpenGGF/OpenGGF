package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
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
    /** {@code word_8141E} script 1: line 4 colour 9 (colour, frames). */
    private static final int[][] SCRIPT_9 = {
            {0x660, 15}, {0x680, 18}, {0x880, 7}, {0x6A2, 7}, {0xAC0, 5},
            {0xCE8, 5}, {0xAC0, 5}, {0x6A2, 7}, {0x880, 7}, {0x680, 18}};
    /** {@code word_8141E} script 2: line 4 colour 11. */
    private static final int[][] SCRIPT_11 = {
            {0x6A0, 15}, {0x8C0, 9}, {0xAC0, 9}, {0xCE0, 7}, {0xCE6, 7}, {0xCE8, 5},
            {0xEEC, 5}, {0xCE8, 5}, {0xCE6, 7}, {0xCE0, 7}, {0xAC0, 9}, {0x8C0, 9}};

    private int x;
    private int y;
    private boolean followingPlayer;
    private boolean rotating;
    private boolean initialized;
    /** {@code Palette_rotation_data}: next entry index and delay per script. */
    private int index9;
    private int delay9;
    private int index11;
    private int delay11;


    DdzBossMasterEmeraldObjectInstance(DdzEndBossShipPartObjectInstance part) {
        super(new ObjectSpawn(part == null ? 0 : part.getX() + OFFSET_X, part == null ? 0 : part.getY() + OFFSET_Y,
                0, 0, 0, false, 0), "DDZBossMasterEmerald", part);
        if (part != null) {
            x = (part.getX() + OFFSET_X) & 0xFFFF;
            y = (part.getY() + OFFSET_Y) & 0xFFFF;
        }
    }

    @Override
    public DdzBossMasterEmeraldObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzBossMasterEmeraldObjectInstance(null);
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

    /** {@code Run_PalRotationScript}: each entry writes its colour and waits its frames. */
    private void runRotation() {
        var registry = services().paletteOwnershipRegistryOrNull();
        if (registry != null && registry.isPaletteRotationDisabled()) {
            return;
        }
        delay9 = (delay9 - 1) & 0xFF;
        if ((byte) delay9 < 0) {
            int[] entry = SCRIPT_9[index9];
            write(9, entry[0]);
            delay9 = entry[1] - 1;
            index9 = (index9 + 1) % SCRIPT_9.length;
        }
        delay11 = (delay11 - 1) & 0xFF;
        if ((byte) delay11 < 0) {
            int[] entry = SCRIPT_11[index11];
            write(0xB, entry[0]);
            delay11 = entry[1] - 1;
            index11 = (index11 + 1) % SCRIPT_11.length;
        }
    }

    private void write(int colour, int word) {
        S3kPaletteWriteSupport.applyColors(services().paletteOwnershipRegistryOrNull(), services().currentLevel(),
                services().graphicsManager(), OWNER, S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, 3,
                new int[] {colour}, new int[] {word});
    }

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
