package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.badniks.BlasterBadnikInstance;
import com.openggf.game.sonic3k.objects.badniks.TechnoSqueekBadnikInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.rings.LostRingObjectInstance;

import java.util.List;

/** Locked-on placed loot prison {@code Obj_FBZEggPrison} ($CF). */
public final class FbzEggPrisonInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable {
    private static final int[] ANIMAL_X = {0, 16, -16, 28, -28};
    private static final int[] RING_X = {-8, 8, 16, -16, 24, -24};
    private static final int[] BLASTER_X = {-24, 24};
    private static final int[] TECHNO_X = {-8, 8};
    private static final int[] FRAGMENT_X = {0, -16, 16, -24, 24};

    public record ReleaseCounts(int animals, int rings, int blasters, int technoSqueeks) { }

    private boolean initialized;
    private boolean buttonSpawnAttempted;
    private boolean triggered;
    private boolean opened;
    private boolean releaseAttempted;
    private int mappingFrame;
    private int routineEntries;

    public FbzEggPrisonInstance(ObjectSpawn spawn) { super(spawn, "FBZEggPrison"); }

    @Override public void update(int frameCounter, PlayableEntity player) {
        routineEntries++;
        if (!initialized) {
            initialized = true;
            FbzEggPrisonButtonInstance button = spawnButtonPrefixOnce();
            boolean remembered = rememberedBroken();
            if (remembered) {
                if (button != null) button.restoreRememberedBrokenState();
                restoreRememberedBrokenState();
            }
            return;
        }
        if (triggered && !opened) openPrefixOnce();
        coarseXCull(spawn.x(), 0x280);
    }

    private FbzEggPrisonButtonInstance spawnButtonPrefixOnce() {
        if (buttonSpawnAttempted) return null;
        buttonSpawnAttempted = true;
        return spawnAfterCurrentSibling(() -> new FbzEggPrisonButtonInstance(
                new ObjectSpawn(spawn.x(), spawn.y() - 0x24, 0, 0, 0, false, 0), this));
    }

    private void restoreRememberedBrokenState() {
        triggered = true;
        opened = true;
        mappingFrame = 1;
    }

    private boolean rememberedBroken() {
        if (tryServices() == null || services().objectManager() == null) return false;
        return services().objectManager().isSpawnStateBitSet(spawn, 0);
    }

    void triggerFromButton(PlayableEntity player) {
        if (opened) return;
        triggered = true;
    }

    private void openPrefixOnce() {
        opened = true;
        mappingFrame = 1;
        if (tryServices() != null && services().objectManager() != null) {
            services().objectManager().setSpawnStateBit(spawn, 0);
        }
        spawnContentsPrefixOnce();
        spawnChild(() -> new FbzEggPrisonExplosionController(spawn.x(), spawn.y()));
        spawnFragmentsPrefix();
    }

    private void spawnContentsPrefixOnce() {
        if (releaseAttempted) return;
        releaseAttempted = true;
        switch (spawn.subtype()) {
            case 0 -> {
                for (int i = 0; i < ANIMAL_X.length; i++) {
                    int subtype = i << 1;
                    int x = spawn.x() + ANIMAL_X[i];
                    spawnChild(() -> new FbzEggPrisonAnimalInstance(
                            new ObjectSpawn(x, spawn.y() - 4, 0, subtype, 0, false, 0)));
                }
            }
            case 1 -> {
                services().playSfx(Sonic3kSfx.RING_LOSS.id);
                int[][] velocity = {{0x100,-0x100},{-0x200,-0x200},{0x200,-0x200},
                        {-0x300,-0x200},{0x300,-0x200},{-0x200,-0x200}};
                for (int i = 0; i < RING_X.length; i++) {
                    int x = spawn.x() + RING_X[i];
                    int vx = velocity[i][0], vy = velocity[i][1];
                    LostRingObjectInstance ring = spawnChild(() -> LostRingObjectInstance.spawn(x, spawn.y() - 4,
                            vx, vy, 0, 0xFF, services().ringManager().getSpillAnimationState()));
                    if (ring.getSlotIndex() >= 0 && !ring.isDestroyed()) {
                        // loc_89D44 initializes and draws; Obj_Bouncing_Ring physics starts next SST entry.
                        ring.deferFirstPhysicsUpdate();
                        services().ringManager().getSpillAnimationState().reset();
                        ring.setSpillPhaseOffset(ring.getSlotIndex());
                    }
                }
            }
            case 2 -> {
                for (int i = 0; i < BLASTER_X.length; i++) {
                    int x = spawn.x() + BLASTER_X[i];
                    boolean facingRight = i != 0;
                    int subtype = i << 1;
                    spawnChild(() -> BlasterBadnikInstance.falling(
                            new ObjectSpawn(x, spawn.y() - 4, 0xA8, subtype, 0, false, 0), facingRight));
                }
                for (int i = 0; i < TECHNO_X.length; i++) {
                    int x = spawn.x() + TECHNO_X[i];
                    boolean launchLeft = i == 0;
                    int subtype = i << 1;
                    spawnChild(() -> TechnoSqueekBadnikInstance.falling(
                            new ObjectSpawn(x, spawn.y() - 4, 0xA9, subtype, 0, false, 0), launchLeft));
                }
            }
            default -> throw new IllegalArgumentException("Placed FBZ prison subtype: " + spawn.subtype());
        }
    }

    private void spawnFragmentsPrefix() {
        for (int i = 0; i < FRAGMENT_X.length; i++) {
            int x = spawn.x() + FRAGMENT_X[i];
            int subtype = i << 1;
            spawnChild(() -> new FbzEggPrisonFragmentInstance(
                    new ObjectSpawn(x, spawn.y() - 8, 0, subtype, 0, false, 0)));
        }
    }

    @Override public SolidObjectParams getSolidParams() { return new SolidObjectParams(0x2B, 0x18, 0x18); }
    @Override public SolidRoutineProfile getSolidRoutineProfile() {
        // sub_89D9C tail-calls S3K SolidObjectFull. Its unsigned BHI width
        // check accepts relX == d1*2, retaining the pushing bits while Sonic
        // rests on the exact outer edge.
        return SolidRoutineProfile.fullSolid(false, true, false);
    }
    @Override public boolean isSolidFor(PlayableEntity player) { return routineEntries >= 2; }
    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return spawn.y(); }
    @Override public int getOnScreenHalfWidth() { return 0x20; }
    @Override public int getOnScreenHalfHeight() { return 0x28; }
    @Override public int getPriorityBucket() { return 4; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_EGG_CAPSULE);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }

    public static int[] animalOffsets() { return ANIMAL_X.clone(); }
    public static int[] ringOffsets() { return RING_X.clone(); }
    public static int[] blasterOffsets() { return BLASTER_X.clone(); }
    public static int[] technoSqueekOffsets() { return TECHNO_X.clone(); }
    public static int[] fragmentOffsets() { return FRAGMENT_X.clone(); }
    public static ReleaseCounts releaseCounts(int subtype) {
        return switch (subtype) {
            case 0 -> new ReleaseCounts(5, 0, 0, 0);
            case 1 -> new ReleaseCounts(0, 6, 0, 0);
            case 2 -> new ReleaseCounts(0, 0, 2, 2);
            default -> throw new IllegalArgumentException("Placed FBZ prison subtype: " + subtype);
        };
    }
    int mappingFrameForTest() { return mappingFrame; }
    boolean releaseAttemptedForTest() { return releaseAttempted; }
    int routineEntriesForTest() { return routineEntries; }
}
