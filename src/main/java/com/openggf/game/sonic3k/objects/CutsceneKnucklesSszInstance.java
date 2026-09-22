package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
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
import com.openggf.physics.ObjectTerrainUtils;

import java.io.IOException;
import java.util.List;

/**
 * ROM {@code CutsceneKnux_SSZ} ({@code Obj_CutsceneKnuckles} subtype {@code $2C},
 * sonic3k.asm:133530-133740): Knuckles' arrival in Sky Sanctuary act 1.
 *
 * <p>{@code Obj_57E34} beams him in at X {@code $100} and drives his Y until the beam swing
 * completes ({@code _unkFAB8} bit 0). He then falls to the terrain, watches the Death Egg rise
 * ({@code _unkFAB8} bit 1, set by {@code loc_65A4A}), runs right in three hops and lands on the
 * button at X {@code $2A8}+, where routine {@code $12} ({@code loc_658F2}) sets
 * {@code Events_bg+$08} and plays {@code sfx_Switch}. That flag is what
 * {@code Obj_SSZCutsceneBridge} waits on. Routine {@code $14} ({@code loc_6594A}) waits for him
 * to leave the screen, then writes the pseudo-starpost ({@code Last_star_post_hit = 1},
 * {@code Saved_X/Y = $140,$C6C}, {@code Save_Level_Data}), restores palette line 1 and reloads
 * {@code PLC_Monitors} before deleting itself.
 *
 * <p>The art is DPLC'd from {@code ArtUnc_Knux} into {@code ArtTile_CutsceneKnux} ({@code $4DA},
 * inside the monitor art), so there is no PLC wait on entry; {@code $38} bit 6 swaps
 * {@code Map_Knuckles} for {@code Map_SSZKnucklesTired}.
 */
public final class CutsceneKnucklesSszInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code move.w #$100,x_pos(a1)}. */
    static final int SPAWN_X = 0x100;
    /** {@code ObjSlot_CutsceneKnux}: {@code move.w #$180,priority}, then {@code $80} at routine 0. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x80);
    private static final int PRIORITY_BUCKET_BUTTON = RenderPriority.fromS3kWord(0x200);

    /** Raw animation scripts inside {@code byte_6669A}-{@code byte_668C7}. */
    private static final int ANI_667C1 = 0x667C1;
    private static final int ANI_66719 = 0x66719;
    private static final int ANI_6671F = 0x6671F;
    private static final int ANI_66824 = 0x66824;

    /** {@code bset #6,$38(a0)}: use {@code Map_SSZKnucklesTired}. */
    private static final int FLAG_TIRED_ART = 6;
    /** {@code _unkFAB8} bit 1: {@code loc_65A4A} has taken the Death Egg above the camera. */
    static final int FLAG_DEATH_EGG_RISEN = 1;

    /** {@code loc_658BA}: {@code cmpi.w #$2A8,x_pos(a0)}. */
    static final int BUTTON_LEAP_X = 0x2A8;
    /** {@code loc_65976}: the pseudo-starpost {@code Obj_SSZCutsceneBridge} also writes. */
    static final int SAVED_X = 0x140;
    static final int SAVED_Y = 0xC6C;
    /** {@code PalPointers} index for {@code Pal_SSZ1}, restored on exit. */
    private static final int PAL_POINTERS_SSZ1_INDEX = 0x1E;
    /** {@code MoveSprite}'s {@code moveq #$38,d1}. */
    private static final int GRAVITY = 0x38;

    private transient S3kRawAnimation rawScripts;

    private int routine;
    private int x = SPAWN_X;
    private int y;
    private int yRadius = 0x13;
    private int timer;
    private int flags;
    private boolean flipX;
    private boolean tiredArt;
    private boolean initialized;
    private boolean beamDriven = true;
    private int priorityBucket = PRIORITY_BUCKET;
    /** {@code x_pos}/{@code y_pos} as the ROM's 16.16 longwords, and the 8.8 velocity words. */
    private int xPos;
    private int yPos;
    private int xVel;
    private int yVel;
    private final S3kRawAnimation.State anim = new S3kRawAnimation.State();

    private record RewindExtra(int routine, int x, int y, int yRadius, int timer, int flags,
                               boolean flipX, boolean tiredArt, boolean initialized,
                               boolean beamDriven, int priorityBucket,
                               int xPos, int yPos, int xVel, int yVel,
                               S3kRawAnimation.State.Value anim)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public CutsceneKnucklesSszInstance(ObjectSpawn spawn) {
        super(spawn, "CutsceneKnucklesSsz");
        this.y = spawn.y();
    }

    @Override
    public CutsceneKnucklesSszInstance recreateForRewind(RewindRecreateContext ctx) {
        return new CutsceneKnucklesSszInstance(ctx.spawn());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                routine, x, y, yRadius, timer, flags, flipX, tiredArt, initialized, beamDriven,
                priorityBucket, xPos, yPos, xVel, yVel, anim.captureRewindStateValue()));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            routine = extra.routine();
            x = extra.x();
            y = extra.y();
            yRadius = extra.yRadius();
            timer = extra.timer();
            flags = extra.flags();
            flipX = extra.flipX();
            tiredArt = extra.tiredArt();
            initialized = extra.initialized();
            beamDriven = extra.beamDriven();
            priorityBucket = extra.priorityBucket();
            xPos = extra.xPos();
            yPos = extra.yPos();
            xVel = extra.xVel();
            yVel = extra.yVel();
            anim.restoreRewindStateValue(extra.anim());
        }
    }

    /** {@code Obj_57E34} writing {@code y_pos(a1)} each frame while the beam swing runs. */
    void setBeamY(int value) {
        if (beamDriven) {
            y = value & 0xFFFF;
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
        switch (routine) {
            case 0x00 -> loc65730();
            case 0x02 -> loc6575E(state);
            case 0x04 -> loc65794();
            case 0x06 -> loc657CE();
            case 0x08 -> loc657FE(state);
            case 0x0A -> loc65826();
            case 0x0C -> loc6584C();
            case 0x0E -> loc65876();
            case 0x10 -> loc658BA();
            case 0x12 -> loc658F2(state);
            default -> loc6594A();
        }
    }

    /** {@code loc_65730}. */
    private void loc65730() {
        initialized = true;
        anim.mappingFrame = 0x16;
        // clr.b (_unkFAB8).w then move.w #$80,priority(a0).
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
        if (state != null) {
            state.clearCutsceneFlags();
        }
        priorityBucket = PRIORITY_BUCKET;
        loadCutscenePalette();
        // SetUp_ObjAttributesSlotted advances routine past the init entry.
        routine = 0x02;
        S3kRawAnimation.set(anim, ANI_667C1);
    }

    /** {@code loc_6575E}: wait for the beam, then start falling. */
    private void loc6575E(SszZoneRuntimeState state) {
        scripts().animateNoSst(anim, ANI_667C1, null);
        if (state == null || !state.cutsceneFlag(SszCutsceneKnucklesSpawnerObjectInstance.FLAG_BEAM_LANDED)) {
            return;
        }
        beamDriven = false;
        routine = 0x04;
        anim.mappingFrame = 0x56;
        yRadius = 0x33;
        xPos = x << 16;
        yPos = y << 16;
        xVel = 0;
        yVel = 0;
        // ArtKosM_SSZDeathEggSmall is queued here; the engine's standalone sheet needs no queue.
    }

    /** {@code loc_65794}: {@code MoveSprite_LightGravity} until the floor is reached. */
    private void loc65794() {
        // MoveSprite_LightGravity (sonic3k.asm:178357): gravity $20 rather than $38.
        moveSprite(0x20);
        int distance = floorDistance();
        if (distance >= 0) {
            return;
        }
        addY(distance);
        routine = 0x06;
        timer = 5;
        spawnChild(() -> new SszDeathEggSmallObjectInstance(
                new ObjectSpawn(SszDeathEggSmallObjectInstance.SPAWN_X,
                        SszDeathEggSmallObjectInstance.SPAWN_Y, 0, 0, 0, false, 0)));
    }

    /** {@code loc_657CE}. */
    private void loc657CE() {
        services().playSfx(Sonic3kSfx.DEATH_EGG_RISE_QUIET.id);
        timer--;
        if (timer >= 0) {
            return;
        }
        routine = 0x08;
        flipX = true;
        tiredArt = true;
        flags |= 1 << FLAG_TIRED_ART;
        anim.mappingFrame = 0;
        S3kRawAnimation.set(anim, ANI_66719);
    }

    /** {@code loc_657FE}. */
    private void loc657FE(SszZoneRuntimeState state) {
        services().playSfx(Sonic3kSfx.DEATH_EGG_RISE_QUIET.id);
        if (state != null && state.cutsceneFlag(FLAG_DEATH_EGG_RISEN)) {
            routine = 0x0A;
            anim.mappingFrame = 0;
            timer = 5;
            return;
        }
        scripts().animateMultiDelay(anim, null);
    }

    /** {@code loc_65826}. */
    private void loc65826() {
        timer--;
        if (timer >= 0) {
            return;
        }
        routine = 0x0C;
        flipX = false;
        tiredArt = false;
        flags &= ~(1 << FLAG_TIRED_ART);
        anim.mappingFrame = 0x56;
        timer = 5;
    }

    /** {@code loc_6584C}: the first hop right. */
    private void loc6584C() {
        timer--;
        if (timer >= 0) {
            return;
        }
        routine = 0x0E;
        yRadius = 0x13;
        xVel = 0x200;
        yVel = -0x300;
        S3kRawAnimation.set(anim, ANI_667C1);
    }

    /** {@code loc_65876}: land from the first hop. */
    private void loc65876() {
        scripts().animateCheckResult(anim, null);
        moveSprite(GRAVITY);
        if (yVel < 0) {
            return;
        }
        int distance = floorDistance();
        if (distance >= 0) {
            return;
        }
        addY(distance);
        routine = 0x10;
        xVel = 0x300;
        yVel = 0;
        flipX = false;
        S3kRawAnimation.set(anim, ANI_66824);
    }

    /** {@code loc_658BA}: run right to the button. */
    private void loc658BA() {
        scripts().animateCheckResult(anim, null);
        // MoveSprite2: no gravity.
        moveSprite(0);
        if ((x & 0xFFFF) < BUTTON_LEAP_X) {
            return;
        }
        routine = 0x12;
        yRadius = 0x1B;
        xVel = 0x480;
        yVel = -0x600;
        S3kRawAnimation.set(anim, ANI_667C1);
    }

    /** {@code loc_658F2}: land on the button and release the bridge. */
    private void loc658F2(SszZoneRuntimeState state) {
        scripts().animateCheckResult(anim, null);
        moveSprite(GRAVITY);
        if (yVel < 0) {
            return;
        }
        int distance = floorDistance();
        if (distance >= 0) {
            return;
        }
        addY(distance);
        routine = 0x14;
        priorityBucket = PRIORITY_BUCKET_BUTTON;
        flipX = false;
        tiredArt = true;
        flags |= 1 << FLAG_TIRED_ART;
        anim.mappingFrame = 1;
        if (state != null) {
            // st (Events_bg+$08).w — Obj_SSZCutsceneBridge's only trigger.
            state.setEventsBgByte(0x08, 0xFF);
        }
        services().playSfx(Sonic3kSfx.SWITCH.id);
        S3kRawAnimation.set(anim, ANI_6671F);
    }

    /**
     * {@code loc_6594A}: wait until Knuckles is off the left of the screen or far enough below
     * it, then write the pseudo-starpost and leave. {@code sub_65FDE}'s "player ducks next to
     * him" reaction is a presentation-only branch and is recorded as a gap.
     */
    private void loc6594A() {
        scripts().animateMultiDelay(anim, null);
        var camera = services().camera();
        int cameraX = camera.getX() & 0xFFFF;
        int cameraY = camera.getY() & 0xFFFF;
        boolean leftOfScreen = (short) (cameraX - 0x80) > (short) x;
        if (!leftOfScreen) {
            int below = (short) (y - cameraY + 0x80);
            if (below <= 0x200) {
                return;
            }
        }
        writePseudoStarPost();
        restoreLevelPaletteLine();
        com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
    }

    /**
     * {@code loc_65976}: {@code Last_star_post_hit = 1}, {@code Saved_X/Y = $140,$C6C},
     * {@code Save_Level_Data}. {@code Obj_SSZCutsceneBridge} writes the same values and also
     * clears {@code Saved_timer}; whichever runs first wins and the other repeats it.
     */
    private void writePseudoStarPost() {
        SszCheckpointOps.writeCutsceneStarPost(services(), SAVED_X, SAVED_Y, false);
    }

    private void loadCutscenePalette() {
        try {
            byte[] colours = services().romReader()
                    .slice(Sonic3kConstants.PAL_CUTSCENE_KNUX_ADDR, 32);
            S3kPaletteWriteSupport.applyContiguousPatch(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager(),
                    S3kPaletteOwners.HPZ_CUTSCENE_KNUCKLES,
                    S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE,
                    1, 0, colours);
        } catch (IOException | RuntimeException ignored) {
            // Partial object harnesses may not provide a ROM or palette surface.
        }
    }

    /** {@code loc_65998}: copy {@code Target_palette_line_2} back, i.e. reload {@code Pal_SSZ1}. */
    private void restoreLevelPaletteLine() {
        try {
            int entryAddr = Sonic3kConstants.PAL_POINTERS_ADDR
                    + PAL_POINTERS_SSZ1_INDEX * Sonic3kConstants.PAL_POINTER_ENTRY_SIZE;
            int sourceAddr = services().rom().read32BitAddr(entryAddr) & 0x00FFFFFF;
            byte[] line = services().rom().readBytes(sourceAddr, 32);
            S3kPaletteWriteSupport.applyLine(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager(),
                    S3kPaletteOwners.ZONE_EVENT_PALETTE_LOAD,
                    S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE,
                    1, line, true);
        } catch (IOException | RuntimeException ignored) {
            // As above.
        }
    }

    /** {@code MoveSprite_CustomGravity}: position is 16.16, velocity 8.8, gravity added after. */
    private void moveSprite(int gravity) {
        xPos += xVel << 8;
        int velocity = yVel;
        yVel = (short) (yVel + gravity);
        yPos += velocity << 8;
        x = (xPos >>> 16) & 0xFFFF;
        y = (yPos >>> 16) & 0xFFFF;
    }

    /** {@code add.w d1,y_pos(a0)} after {@code ObjCheckFloorDist}. */
    private void addY(int distance) {
        y = (y + distance) & 0xFFFF;
        yPos = (y << 16) | (yPos & 0xFFFF);
    }

    /** {@code ObjCheckFloorDist}: negative means the object has sunk into the floor. */
    private int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
    }

    private S3kRawAnimation scripts() {
        if (rawScripts == null) {
            try {
                rawScripts = S3kRawAnimation.load(services().romReader(),
                        Sonic3kConstants.CUTSCENE_KNUX_RAW_SCRIPTS_ADDR,
                        Sonic3kConstants.CUTSCENE_KNUX_RAW_SCRIPTS_SIZE);
            } catch (IOException ex) {
                throw new IllegalStateException("byte_6669A raw animation scripts", ex);
            }
        }
        return rawScripts;
    }

    public int routineForTest() { return routine; }
    public int mappingFrameForTest() { return anim.mappingFrame; }
    public boolean tiredArtForTest() { return tiredArt; }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return priorityBucket; }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x18; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized) {
            return;
        }
        String key = tiredArt
                ? Sonic3kObjectArtKeys.HPZ_CUTSCENE_KNUCKLES_TIRED
                : Sonic3kObjectArtKeys.HPZ_CUTSCENE_KNUCKLES;
        PatternSpriteRenderer renderer = getRenderer(key);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(anim.mappingFrame, x, y, flipX, false);
        }
    }
}
