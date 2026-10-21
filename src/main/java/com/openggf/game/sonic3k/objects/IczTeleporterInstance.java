package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;

import java.util.List;

/**
 * Knuckles' ICZ1 arrival, {@code Obj_ICZTeleporter} ($53AD4-$53C04).
 * ScreenInit owns the initial player/camera words; this owner retries its beam allocation,
 * swings actual Player_1, then remains a solid pad until Camera_X_pos_copy reaches $3780.
 */
public final class IczTeleporterInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, SolidObjectProvider, TeleporterBeamOwner {
    public static final int PAD_X = 0x3640;
    public static final int PAD_Y = 0x0670;
    private static final String PALETTE_OWNER = "s3k.icz.teleporter";
    private static final SolidObjectParams SOLID = SolidObjectParams.of(0x23, 0x10, 1);

    private boolean initialized;
    private boolean released;
    private int playerBaseY;
    private TeleporterBeamObjectInstance beam;
    private final S3kGradualSwing swing = new S3kGradualSwing();

    public IczTeleporterInstance(ObjectSpawn spawn) {
        super(spawn, "ICZTeleporter");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity ignoredFocus) {
        // The assembly uses lea (Player_1),a1, never the camera's focused participant.
        AbstractPlayableSprite player = services().spriteManager().getMainPlayable();
        if (!initialized) {
            var child = spawnChild(() -> TeleporterBeamObjectInstance.iczArrivalBeam(
                    new ObjectSpawn(PAD_X, PAD_Y, 0, 0, 0, false, 0), this));
            // The shared construction helper can return an unregistered, destroyed
            // instance when the native SST scan failed. Only an actual allocated
            // child is AllocateObjectAfterCurrent success ($53AD4-$53ADE).
            if (child == null || child.getSlotIndex() < 0 || child.isDestroyed()) return;
            beam = child;
            initialized = true;
            playerBaseY = player.getCentreY() & 0xFFFF;
            var palette = services().paletteOwnershipRegistryOrNull();
            if (palette != null) {
                // Target_palette_line_2+$10 and +$14 only. The fade owns Normal_palette.
                palette.applyTargetPatch(PALETTE_OWNER, 1, 8, new byte[] {0x0E, (byte) 0xA2});
                palette.applyTargetPatch(PALETTE_OWNER, 1, 10, new byte[] {0x0E, (byte) 0x80});
            }
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
            player.setObjectMappingFrameControl(false); // Whole object_control byte = 1, including bit 1 clear.
            player.setAnimationId(Sonic3kAnimationIds.ROLL);
            PlayableSpriteInternalAccess.writeNativeRollingStatus(player, true);
            // Native init falls through to Main in this same object dispatch.
        }
        if (!released) {
            int offset = swing.step(0x20000, 0x800);
            NativePositionOps.writeYPosPreserveSubpixel(player, (playerBaseY + offset) & 0xFFFF);
            if (swing.rising()) return;
            ObjectControlState.none().applyTo(player);
            player.setObjectMappingFrameControl(false);
            player.setAnimationId(0);
            released = true;
            // Main falls through to CheckDel, including its solid call, on the release pass.
        }
        if ((services().camera().getXCopy() & 0xFFFF) >= 0x3780) {
            S3kPaletteWriteSupport.applyColors(services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(), services().graphicsManager(), PALETTE_OWNER,
                    100, 1, new int[] {8, 10}, new int[] {0x002C, 0});
            S3kPaletteWriteSupport.resolvePendingWritesNow(services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(), services().graphicsManager());
            ObjectLifetimeOps.deleteNoRespawn(this);
        }
    }

    @Override public void onBeamFinished() { beam = null; }
    @Override public int getX() { return initialized ? PAD_X : spawn.x(); }
    @Override public int getY() { return initialized ? PAD_Y : spawn.y(); }
    @Override public int getOutOfRangeReferenceX() { return getX(); }
    @Override public boolean isPersistent() { return true; }
    /** Obj_ICZTeleporter: priority $80, art word $2347 (palette 1, low priority). */
    @Override public int getPriorityBucket() { return initialized ? 1 : 0; }
    @Override public boolean isHighPriority() { return false; }
    @Override public SolidObjectParams getSolidParams() { return SOLID; }
    @Override public boolean isSolidFor(PlayableEntity player) { return released && !isDestroyed(); }
    @Override public boolean usesInclusiveRightEdge() { return true; }

    public boolean initialized() { return initialized; }
    public boolean released() { return released; }
    public TeleporterBeamObjectInstance beamForTest() { return beam; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.ICZ_PLATFORMS_INTRO);
        if (renderer != null) renderer.drawFrameIndex(0x1A, PAD_X, PAD_Y, false, false, 1);
    }

}
