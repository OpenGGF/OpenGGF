package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_659CC} / {@code loc_65A30} / {@code loc_65A4A} (sonic3k.asm:133751-133812):
 * the small Death Egg that rises out of Sky Sanctuary while cutscene Knuckles watches.
 *
 * <p>{@code CutsceneKnux_SSZ} routine 4 creates it through {@code ChildObjDat_665F6}. It starts at
 * {@code ($200,$C68)} with {@code y_vel} reused as the 16.16 vertical accumulator seeded to the
 * same {@code $C68} and {@code $40 = -$40} as upward acceleration, and moves with
 * {@code MoveSprite_SSZBGAdjust}: the object tracks the halved camera X delta in {@code _unkFA84}
 * and subtracts {@code _unkEE9C >> 2} so it drifts with the background rather than the
 * foreground. After {@code $2E = $100} frames it starts firing missiles, and once it has risen
 * past {@code Camera_Y - $38} it sets {@code _unkFAB8} bit 1 — the flag Knuckles' routine 8 waits
 * on — and deletes itself.
 *
 * <p>Initialization reseeds {@code RNG_seed} from the full {@code V_int_run_count} and
 * applies the nonzero {@code Pal_KnuxSSZEnd} words; departure restores the original line.
 * Initial children retain the native allocation order; missile cadence uses the V-int clock.
 */
public final class SszDeathEggSmallObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code move.w #$200,x_pos(a0)} / {@code move.w #$C68,d0}. */
    static final int SPAWN_X = 0x200;
    static final int SPAWN_Y = 0xC68;
    /**
     * {@code move.w #-$40,$40(a0)}: {@code MoveSprite_SSZBGAdjust} adds {@code $40 << 8} to the
     * longword at {@code y_vel} every frame, so this is a constant -0.25 px per frame rise, not an
     * acceleration. The longword's high word is the Y the object draws at.
     */
    private static final int RISE_PER_FRAME = -0x40;
    /** {@code move.w #$100,$2E(a0)}. */
    private static final int RISE_FRAMES = 0x100;
    /** {@code loc_65A4A}: {@code subi.w #$38,d0}. */
    private static final int CAMERA_MARGIN = 0x38;
    /** {@code ObjDat3_664AA}: {@code dc.w $380}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x380);

    private int x = SPAWN_X;
    private int y = SPAWN_Y;
    /** {@code y_vel(a0)} as the ROM's 16.16 longword. */
    private int riseAccumulator = SPAWN_Y << 16;
    private int timer = RISE_FRAMES;
    private boolean firing;
    private boolean initialized;
    private byte[] savedPalette = new byte[0];

    private record RewindExtra(int x, int y, int riseAccumulator, int timer, boolean firing,
                               boolean initialized, byte[] savedPalette)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszDeathEggSmallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SszDeathEggSmall");
    }

    @Override
    public SszDeathEggSmallObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SszDeathEggSmallObjectInstance(ctx.spawn());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(x, y, riseAccumulator, timer, firing, initialized, savedPalette.clone()));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            x = extra.x();
            y = extra.y();
            riseAccumulator = extra.riseAccumulator();
            timer = extra.timer();
            firing = extra.firing();
            initialized = extra.initialized();
            savedPalette = extra.savedPalette().clone();
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) {
            initialized = true;
            // loc_659CC copies the full longword, not the low-word level clock.
            services().rng().setSeed(Integer.toUnsignedLong(vIntRunCount));
            spawnInitialChildren();
            installCutscenePalette();
        }
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
        if (firing && (vIntRunCount & 0x1F) == 0) {
            // loc_65A4A calls sub_66054 before BG movement. CreateChild6_Simple
            // copies the pre-movement coordinates and consumes RNG in the child.
            services().playSfx(com.openggf.game.sonic3k.audio.Sonic3kSfx.MISSILE_SHOOT.id);
            spawnChild(() -> new SszDeathEggMissile(new ObjectSpawn(x, y, 0, 0, 0, false, 0)));
        }
        moveSpriteSszBgAdjust(state);
        if (!firing) {
            timer--;
            if (timer < 0) {
                firing = true;
            }
            return;
        }
        int cameraY = services().camera().getY() & 0xFFFF;
        // CMP.W / BLS compares unsigned words, including subtraction wraparound.
        if (((cameraY - CAMERA_MARGIN) & 0xFFFF) <= y) {
            return;
        }
        if (state != null) {
            state.setCutsceneFlag(CutsceneKnucklesSszInstance.FLAG_DEATH_EGG_RISEN);
        }
        com.openggf.game.sonic3k.S3kPaletteWriteSupport.applyLine(
                services().paletteOwnershipRegistryOrNull(), services().currentLevel(), services().graphicsManager(),
                com.openggf.game.sonic3k.S3kPaletteOwners.SSZ_DEATH_EGG_CUTSCENE,
                com.openggf.game.sonic3k.S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE,
                3, savedPalette, true);
        com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
    }

    private void spawnInitialChildren() {
        // ChildObjDat_665C4 is an ordered CreateChild1_Normal batch; stop at
        // the first failed allocation. Child subtypes advance by two.
        int[] dx = {0, 0, -0x20, -0x10, 8, 0x10, 0x28};
        int[] dy = {0, -0x33, 0x1D, 0x1D, 0x1D, 0x1D, 0x1D};
        for (int i = 0; i < dx.length; i++) {
            final int index = i;
            if (spawnChild(() -> new SszDeathEggChild(new ObjectSpawn(
                    (x + dx[index]) & 0xFFFF, (y + dy[index]) & 0xFFFF,
                    0, index * 2, 0, false, 0), getSlotIndex())) == null) break;
        }
    }

    private void installCutscenePalette() {
        // loc_65A14 backs Normal_palette_line_4 into Target_palette_line_4.
        // Keep this cutscene-owned backup with its recreatable owner: deletion's
        // loc_65A80 restores these exact words, not a freshly loaded level palette.
        savedPalette = new byte[32];
        var palette = services().currentLevel().getPalette(3);
        for (int i = 0; i < 16; i++) {
            int word = com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(palette.getColor(i));
            savedPalette[i * 2] = (byte) (word >>> 8);
            savedPalette[i * 2 + 1] = (byte) word;
        }
        try {
            byte[] patch = services().romReader().slice(0x669B2, 32); // Pal_KnuxSSZEnd
            for (int i = 0; i < 16; i++) {
                // loc_65A24 skips zero words; they mean preserve, not black.
                if ((patch[i * 2] | patch[i * 2 + 1]) == 0) continue;
                com.openggf.game.sonic3k.S3kPaletteWriteSupport.applyContiguousPatch(
                        services().paletteOwnershipRegistryOrNull(), services().currentLevel(),
                        services().graphicsManager(),
                        com.openggf.game.sonic3k.S3kPaletteOwners.SSZ_DEATH_EGG_CUTSCENE,
                        com.openggf.game.sonic3k.S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE,
                        3, i, new byte[] {patch[i * 2], patch[i * 2 + 1]});
            }
            com.openggf.game.sonic3k.S3kPaletteWriteSupport.resolvePendingWritesNow(
                    services().paletteOwnershipRegistryOrNull(), services().currentLevel(), services().graphicsManager());
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** {@code MoveSprite_SSZBGAdjust} (sonic3k.asm:134355-134371). */
    private void moveSpriteSszBgAdjust(SszZoneRuntimeState state) {
        int cameraDelta = state == null ? 0 : state.backgroundCameraDelta();
        x = (x + cameraDelta) & 0xFFFF;
        riseAccumulator += RISE_PER_FRAME << 8;
        int integer = riseAccumulator >> 16;
        int oscillator = state == null ? 0 : state.cloudOscillator();
        y = (integer - (oscillator >> 2)) & 0xFFFF;
    }

    int timerForTest() { return timer; }
    boolean firingForTest() { return firing; }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x3C; }
    @Override public int getOnScreenHalfHeight() { return 0x30; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_DEATH_EGG_SMALL);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, x, y, false, false);
        }
    }
}
