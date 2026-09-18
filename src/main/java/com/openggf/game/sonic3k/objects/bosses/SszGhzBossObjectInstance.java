package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.SwingMotion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ROM {@code Obj_SSZGHZBoss} (sonic3k.asm:162576-162942): the Green Hill recreation, the first of
 * Sky Sanctuary act 1's three rebuilt boss fights. It is not placed — {@code sub_575EA}'s
 * {@code loc_576E8} allocates it once the arena lock has settled the camera at {@code $7C0}.
 *
 * <p><b>Entry.</b> The init installs {@code Obj_Wait} for {@code $1F} frames with
 * {@code $34 = loc_7A294}, sets {@code Boss_flag}, fades the music out and allocates an
 * {@code Obj_Song_Fade_Transition} carrying {@code mus_EndBoss}. It places itself at
 * {@code (Camera_X + $110, Camera_Y - $40)}, clears {@code _unkFA88}, loads PLC {@code $7B},
 * queues {@code ArtKosM_SSZGHZMisc}, copies {@code Normal_palette_line_2} into
 * {@code Target_palette_line_2} and then runs {@code PalLoad_Line1} over
 * {@code Pal_SSZGHZMisc}. The save is what the defeat restores.
 *
 * <p><b>The six routines</b> ({@code off_7A2B4}). {@code SetUp_ObjAttributes} is what advances
 * {@code routine} — it ends {@code addq.b #2,routine(a0)} — which is why the table has entries the
 * text never writes explicitly.
 * <ol start="0">
 *   <li>{@code loc_7A2C0}: {@code ObjDat_SSZGHZBoss} ({@code Map_RobotnikShip}, priority
 *       {@code $200}, {@code $1C} by {@code $20}, frame {@code $A}, collision {@code $F}),
 *       {@code collision_property 8} — eight hits — {@code y_vel $100}, a {@code $67}-frame wait
 *       and the Mecha Sonic head child.</li>
 *   <li>Routine 2 ({@code loc_7A2F0}) simply falls for those {@code $67} frames.</li>
 *   <li>{@code loc_7A2FC} then sets routine 4, {@code x_vel -$100}, spawns
 *       {@code ChildObjDat_7A69E}'s emitter and arms the {@code Swing_UpAndDown} pair
 *       ({@code $3E = y_vel = $C0}, {@code $40 = $10}).</li>
 *   <li>Routine 4 ({@code loc_7A32C}) swings and moves left until {@code Camera_X + $A0} reaches
 *       the ship, then takes routine 6, sets {@code $38} bit 6 and drops the six-link ball and
 *       chain from {@code ChildObjDat_7A684}.</li>
 *   <li>Routine 6 ({@code loc_7A35E}) waits for the chain's own bit 2, then routine 8 with
 *       {@code x_vel -$100} and a {@code $1F} wait whose expiry ({@code loc_7A39A}) is routine
 *       {@code $A}.</li>
 *   <li>Routine 8 ({@code loc_7A388}) swings and moves; routine {@code $A} ({@code loc_7A3A8})
 *       waits for the ball's bit 3 and then turns the ship around — negated {@code x_vel},
 *       {@code bchg} on the X-flip, a {@code $3F} wait — and hands back to routine 8. Eight and
 *       {@code $A} are the fight's loop.</li>
 * </ol>
 *
 * <p><b>Hits and defeat</b> ({@code sub_7A5A0}). The hit machine only runs on frames where
 * {@code collision_flags} is zero, which is what the touch response leaves behind; with
 * {@code collision_property} still positive it arms a {@code $20}-frame window, plays
 * {@code sfx_BossHit} and flashes three specific {@code Normal_palette} entries from
 * {@code word_7A628}. At zero hits {@code loc_7A5EC} installs {@code Wait_FadeToLevelMusic} with
 * {@code $34 = loc_7A3CE}, spawns a boss explosion of subtype 4 and calls {@code BossDefeated}.
 * {@code loc_7A3CE} then turns the ship right with {@code x_vel $400}, and after its {@code $2E}
 * frames {@code loc_7A3F8} clears {@code Boss_flag}, sets {@code $38} bit 4, writes
 * {@code st (_unkFA89).w} — which is what deletes the head — and {@code st (Events_bg+$00).w},
 * the negative byte {@code sub_575EA} reads as "beaten" and the {@code $79:$AA} pad reads as its
 * release. Only then does it restore palette line 2 and reload PLC {@code $32}.
 */
public final class SszGhzBossObjectInstance extends AbstractBossInstance
        implements SpawnRewindRecreatable {

    /** {@code move.b #8,collision_property(a0)}. */
    private static final int HIT_COUNT = 8;
    /** {@code ObjDat_SSZGHZBoss}: {@code dc.b $1C,$20,$A,$F}. */
    private static final int COLLISION_SIZE = 0x0F;
    private static final int SHIP_MAPPING_FRAME = 0x0A;
    private static final int SHIP_HALF_WIDTH = 0x1C;
    private static final int SHIP_HALF_HEIGHT = 0x20;
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x200);
    /** {@code addi.w #$110,d0} / {@code subi.w #$40,d0}. */
    public static final int SPAWN_CAMERA_X_OFFSET = 0x110;
    public static final int SPAWN_CAMERA_Y_OFFSET = -0x40;
    /** {@code move.w #$1F,$2E(a0)} in the init. */
    private static final int ENTRY_WAIT_FRAMES = 0x1F;
    /** {@code move.w #$67,$2E(a0)} in {@code loc_7A2C0}. */
    private static final int FALL_FRAMES = 0x67;
    /** {@code move.w #$100,y_vel(a0)} / {@code move.w #-$100,x_vel(a0)}. */
    private static final int FALL_Y_VEL = 0x100;
    private static final int RUN_X_VEL = -0x100;
    /** {@code move.w #$C0,d0} / {@code move.w #$10,$40(a0)}. */
    private static final int SWING_MAX = 0xC0;
    private static final int SWING_ACCELERATION = 0x10;
    /** {@code addi.w #$A0,d0} in {@code loc_7A32C}. */
    private static final int CHAIN_TRIGGER_CAMERA_OFFSET = 0xA0;
    /** {@code move.w #$1F,$2E(a0)} / {@code move.w #$3F,$2E(a0)}. */
    private static final int TURN_WAIT_FRAMES = 0x1F;
    private static final int REVERSE_WAIT_FRAMES = 0x3F;
    /** {@code move.w #$400,x_vel(a0)} in {@code loc_7A3CE}. */
    private static final int ESCAPE_X_VEL = 0x400;
    /** {@code move.w #(2*60)-1,$2E(a0)} in {@code loc_85674}. */
    private static final int ESCAPE_FRAMES = (2 * 60) - 1;
    /** {@code ChildObjDat_7A684}: {@code dc.w 6-1}. */
    public static final int CHAIN_LINKS = 6;
    /**
     * {@code moveq #$7B,d0 / Load_PLC} and, at the defeat, {@code moveq #$32,d0}. The engine has
     * no runtime decompression queue for object art: {@code Sonic3kObjectArtProvider} registers a
     * zone's sheets at level load from {@code Sonic3kPlcArtRegistry}, which is where
     * {@code ArtKosM_SSZGHZMisc} and {@code ArtKosM_MechaSonicHead} live. The two ids are kept as
     * named constants because the tests cite them and because a future runtime queue has to know
     * which entries the fight owns.
     */
    public static final int FIGHT_PLC = 0x7B;
    public static final int LEVEL_PLC = 0x32;
    /** {@code PAL_POINTERS} index for {@code Pal_SSZ1}, as the cutscene restore uses it. */
    private static final int PAL_POINTERS_SSZ1_INDEX = 0x1E;

    private static final int ROUTINE_SETUP = 0;
    private static final int ROUTINE_FALLING = 2;
    private static final int ROUTINE_RUN_IN = 4;
    private static final int ROUTINE_WAIT_FOR_CHAIN = 6;
    private static final int ROUTINE_SWEEP = 8;
    private static final int ROUTINE_WAIT_FOR_BALL = 0x0A;

    /** {@code move.w #$3F,$2E(a0)} in {@code BossDefeated} (sonic3k.asm:180822). */
    private static final int DEFEAT_WAIT_FRAMES = 0x3F;
    /** {@code moveq #100,d0 / jsr (HUD_AddToScore)} in the same routine. */
    private static final int DEFEAT_SCORE = 100;
    /** {@code move.b #$20,$20(a0)} in {@code sub_7A5A0}. */
    private static final int HIT_WINDOW_FRAMES = 0x20;
    /**
     * {@code word_7A622}: the three {@code Normal_palette} <em>byte</em> offsets
     * {@code sub_7A614} patches, as colour indices into palette line 0 — the line
     * {@code make_art_tile(ArtTile_RobotnikShip,0,0)} draws the ship on.
     */
    private static final int FLASH_COLOR_A = 0x0E / 2;
    private static final int FLASH_COLOR_B = 0x1C / 2;
    private static final int FLASH_COLOR_C = 0x1E / 2;
    /** {@code word_7A628}: two overlapping three-word rows in one six-word table. */
    private static final int[] FLASH_TABLE = {0x0008, 0x0866, 0x0222, 0x0888, 0x0CCC, 0x0EEE};
    /**
     * {@code btst #0,$20(a0) / bne.s loc_7A5D4} leaves {@code d0} zero on the odd frames, so the
     * odd frames always write {@code word_7A628}'s first row.
     */
    private static final int FLASH_ROW_NORMAL = 0;
    /**
     * The even frames add a byte offset. {@code FixBugs = 0} ships {@code addi.w #2*2,d0}, which
     * lands one word short of the second row and writes {@code $222,$888,$CCC} — a row that
     * overlaps the normal one, so the flash is dark-to-mid. The {@code FixBugs} branch is
     * {@code 2*3}, which would write {@code $888,$CCC,$EEE} and give the intended bright flash.
     * Modelled as shipped, per runtime invariant 7.
     */
    private static final int FLASH_ROW_SHIPPED = (2 * 2) / 2;
    static final int FLASH_ROW_FIXED = (2 * 3) / 2;

    /** {@code $2E(a0)} and the {@code $34(a0)} continuation it runs at zero. */
    private int waitTimer = ENTRY_WAIT_FRAMES;
    private Continuation waitContinuation = Continuation.NONE;

    private enum Continuation { START_FIGHT, END_FALL, TURN_AROUND, ESCAPE_DONE, NONE }

    /**
     * The init block ({@code Obj_SSZGHZBoss} itself) is one execution that ends
     * {@code jmp (PalLoad_Line1)}; {@code Obj_Wait} does not run on it, because the
     * {@code move.l #Obj_Wait,(a0)} at the top only changes what the <em>next</em> frame runs.
     */
    /** {@code status} bit 7; see {@link #hasStatusBit7()}. */
    private boolean statusBit7;
    private boolean initExecuted;
    /**
     * {@code loc_7A294} is the tail of the frame the wait expires on, not a frame of its own:
     * {@code Obj_Wait}'s {@code bmi} reaches {@code loc_84892}, which is
     * {@code movea.l $34(a0),a1 / jmp (a1)}, and {@code loc_7A294} installs {@code loc_7A29C} and
     * returns from there. So the 32nd decrement and the handover share one execution, and the
     * routine table is first dispatched on the one after it. Modelled as a separate early return
     * because the decrement and the dispatch are separate frames either way.
     */
    private boolean dispatcherInstalled;

    /** 16.16 position and 8.8 velocities, as {@code MoveSprite2} reads them. */
    private int xPos;
    private int yPos;
    private int xVel;
    private int yVel;
    private int swingMax;
    private int swingAcceleration;
    private boolean swingDown;
    private boolean renderFlipped;

    /** {@code $38(a0)} bits 2, 3, 4 and 6. */
    private boolean chainReady;
    private boolean ballReachedFarSide;
    private boolean escaped;
    private boolean chainPhaseActive;

    private boolean entryApplied;
    private boolean paletteLoaded;
    private boolean escaping;
    /** False through {@code Wait_FadeToLevelMusic}, true once {@code loc_7A3CE} has run. */
    private boolean escapeRunning;

    private final List<SszGhzBossChainLinkChild> chain = new ArrayList<>();
    private SszMechaSonicHeadChild head;
    private SszGhzBossShieldChild shield;

    public SszGhzBossObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZGHZBoss");
        xPos = spawn.x() << 16;
        yPos = spawn.y() << 16;
    }

    private record RewindExtra(ObjectRefId headId, ObjectRefId shieldId, List<ObjectRefId> chainIds,
                               int waitTimer, int waitContinuation, int xPos, int yPos, int xVel,
                               int yVel, int swingMax, int swingAcceleration, boolean swingDown,
                               boolean renderFlipped, boolean chainReady, boolean ballReachedFarSide,
                               boolean escaped, boolean chainPhaseActive, boolean entryApplied,
                               boolean paletteLoaded, boolean escaping, boolean escapeRunning,
                               boolean initExecuted, boolean dispatcherInstalled,
                               boolean statusBit7,
                               boolean invulnerable, int invulnerabilityTimer)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        var table = context.identityTable();
        List<ObjectRefId> chainIds = new ArrayList<>();
        for (SszGhzBossChainLinkChild link : chain) {
            chainIds.add(table.map(t -> t.encodeObject(link)).orElse(null));
        }
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                table.map(t -> t.encodeObject(head)).orElse(null),
                table.map(t -> t.encodeObject(shield)).orElse(null),
                List.copyOf(chainIds),
                waitTimer, waitContinuation.ordinal(), xPos, yPos, xVel, yVel, swingMax,
                swingAcceleration, swingDown, renderFlipped, chainReady, ballReachedFarSide,
                escaped, chainPhaseActive, entryApplied, paletteLoaded, escaping, escapeRunning,
                initExecuted, dispatcherInstalled, statusBit7, state.invulnerable,
                state.invulnerabilityTimer));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (!(snapshot.objectSubclassExtra() instanceof RewindExtra extra)) {
            return;
        }
        waitTimer = extra.waitTimer();
        waitContinuation = Continuation.values()[extra.waitContinuation()];
        xPos = extra.xPos();
        yPos = extra.yPos();
        xVel = extra.xVel();
        yVel = extra.yVel();
        swingMax = extra.swingMax();
        swingAcceleration = extra.swingAcceleration();
        swingDown = extra.swingDown();
        renderFlipped = extra.renderFlipped();
        chainReady = extra.chainReady();
        ballReachedFarSide = extra.ballReachedFarSide();
        escaped = extra.escaped();
        chainPhaseActive = extra.chainPhaseActive();
        entryApplied = extra.entryApplied();
        paletteLoaded = extra.paletteLoaded();
        escaping = extra.escaping();
        escapeRunning = extra.escapeRunning();
        initExecuted = extra.initExecuted();
        dispatcherInstalled = extra.dispatcherInstalled();
        statusBit7 = extra.statusBit7();
        state.invulnerable = extra.invulnerable();
        state.invulnerabilityTimer = extra.invulnerabilityTimer();
        head = (SszMechaSonicHeadChild) resolve(context, extra.headId());
        shield = (SszGhzBossShieldChild) resolve(context, extra.shieldId());
        chain.clear();
        for (ObjectRefId id : extra.chainIds()) {
            SszGhzBossChainLinkChild link = (SszGhzBossChainLinkChild) resolve(context, id);
            if (link != null) {
                chain.add(link);
            }
        }
    }

    private static Object resolve(RewindCaptureContext context, ObjectRefId id) {
        return id == null ? null : context.requireIdentityTable().resolveObject(id, true);
    }

    @Override
    protected void initializeBossState() {
        state.routine = ROUTINE_SETUP;
        state.hitCount = HIT_COUNT;
    }

    @Override protected int getInitialHitCount() { return HIT_COUNT; }

    @Override protected int getCollisionSizeIndex() { return COLLISION_SIZE; }

    @Override protected void onHitTaken(int remainingHits) { }

    /**
     * {@code sub_7A5A0} is this object's own hit window: it arms {@code $20(a0)}, reads bit 0 of
     * it to pick a palette row and only then counts it down. The shared handler decrements before
     * the flash reads the counter and flashes one whole line, so this boss owns both.
     */
    @Override protected boolean usesBaseHitHandler() { return false; }

    /** {@code BossDefeated} is {@code moveq #100,d0} into {@code HUD_AddToScore}. */
    @Override protected int getDefeatScore() { return DEFEAT_SCORE; }

    /**
     * {@code loc_7A29C} reads {@code routine(a0)} at its head, {@code jsr}s the selected arm and
     * only then {@code bsr.w sub_7A5A0}, whose zero-hits branch ({@code loc_7A5EC}) installs
     * {@code Wait_FadeToLevelMusic} into {@code (a0)}. The dispatcher has already run this slot
     * for the frame, so that wait's first decrement belongs to the next object pass and the
     * killing frame ends with {@code $2E} still at {@code $3F} — the same shape
     * {@code HczMinibossInstance} models with this override.
     *
     * <p>Measured, comparison only: in the {@code s3k-sonic-tails-complete-emeralds} {@code hpz}
     * segment the ship takes {@code Wait_FadeToLevelMusic} on frame 4412 and {@code loc_7A3E6} on
     * 4476. Sixty-four executions is {@code $3F} decrements plus the one that goes negative, so
     * the killing frame decrements nothing.
     */
    @Override protected boolean defeatDeferralAppliesToThisBoss() { return true; }

    @Override protected boolean usesDefeatSequencer() { return false; }

    @Override protected int getBossHitSfxId() { return Sonic3kSfx.BOSS_HIT.id; }

    @Override protected int getBossExplosionSfxId() { return Sonic3kSfx.EXPLODE.id; }

    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }

    @Override public int getOnScreenHalfWidth() { return SHIP_HALF_WIDTH; }

    @Override public int getOnScreenHalfHeight() { return SHIP_HALF_HEIGHT; }


    /**
     * {@code loc_7A29C} dispatches the routine and falls into {@code Draw_And_Touch_Sprite} with
     * no {@code Obj_WaitOffscreen} anywhere in the chain, so nothing unloads this object when it
     * leaves the screen — which matters, because {@code loc_7A3E6}'s escape carries the ship well
     * past the locked arena's right edge before {@code loc_7A3F8} writes the beaten flag.
     */
    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override public int getX() { return (xPos >> 16) & 0xFFFF; }

    @Override public int getY() { return (yPos >> 16) & 0xFFFF; }

    @Override
    protected void updateBossLogic(int vIntRunCount, PlayableEntity player) {
        if (!initExecuted) {
            // Obj_SSZGHZBoss's own body. It ends jmp (PalLoad_Line1) and never reaches Obj_Wait:
            // move.l #Obj_Wait,(a0) only changes what the NEXT frame runs, so this execution
            // does not consume one of the $1F wait frames.
            applyEntryOnce();
            if (entryApplied) {
                initExecuted = true;
            }
            return;
        }
        if (escaping) {
            updateEscape();
            syncBossStatePosition();
            return;
        }
        if (!dispatcherInstalled) {
            // Obj_Wait: subq.w #1,$2E(a0) / bmi -> movea.l $34(a0),a1 / jmp (a1).
            if (--waitTimer >= 0) {
                return;
            }
            // loc_84892 tail-calls $34(a0) on the frame the wait goes negative, and loc_7A294
            // is move.l #loc_7A29C,(a0) / rts. Either way the table is first dispatched next
            // frame, not on this one.
            dispatcherInstalled = true;
            return;
        }
        // loc_7A29C: dispatch, then sub_7A5A0, then Draw_And_Touch_Sprite.
        switch (state.routine) {
            case ROUTINE_SETUP -> enterFall();
            case ROUTINE_FALLING -> updateFall();
            case ROUTINE_RUN_IN -> updateRunIn();
            case ROUTINE_WAIT_FOR_CHAIN -> updateWaitForChain();
            case ROUTINE_SWEEP -> updateSweep();
            case ROUTINE_WAIT_FOR_BALL -> updateWaitForBall();
            default -> { }
        }
        updateHitWindow();
        syncBossStatePosition();
    }

    /**
     * {@code sub_7A5A0}, which {@code loc_7A29C} calls after every routine dispatch.
     *
     * <p>The gate is {@code tst.b collision_flags(a0)}: the touch response zeroes
     * {@code collision_flags} on the frame a hit lands and stashes the original in {@code $25(a0)},
     * so "collision flags are zero" is exactly this engine's {@code state.invulnerable}. While the
     * window runs it patches three {@code Normal_palette} entries and counts {@code $20(a0)} down;
     * at zero it restores {@code collision_flags} from {@code $25(a0)}, which is the invulnerable
     * flag clearing here. The defeat branch is {@code loc_7A5EC}, which the base hit handler's
     * {@code triggerDefeat} already owns.
     */
    private void updateHitWindow() {
        if (state.defeated || !state.invulnerable) {
            return;
        }
        // btst #0,$20(a0): odd counter values take the branch that leaves d0 zero.
        int row = (state.invulnerabilityTimer & 1) != 0 ? FLASH_ROW_NORMAL : FLASH_ROW_SHIPPED;
        writeFlashRow(row);
        // subq.b #1,$20(a0) / bne.s locret_7A5EA.
        state.invulnerabilityTimer--;
        if (state.invulnerabilityTimer <= 0) {
            state.invulnerabilityTimer = 0;
            // move.b $25(a0),collision_flags(a0): the ship is hittable again.
            state.invulnerable = false;
        }
    }

    /** {@code sub_7A614}: {@code CopyWordData_3} of three words into {@code word_7A622}'s list. */
    private void writeFlashRow(int row) {
        var registry = services().paletteOwnershipRegistryOrNull();
        var level = services().currentLevel();
        var graphics = services().graphicsManager();
        S3kPaletteWriteSupport.applyContiguousPatch(registry, level, graphics,
                S3kPaletteOwners.SSZ_GHZ_BOSS_HIT_FLASH, S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                0, FLASH_COLOR_A, segaWords(FLASH_TABLE[row]));
        // Normal_palette+$1C and +$1E are adjacent, so the last two words are one patch.
        S3kPaletteWriteSupport.applyContiguousPatch(registry, level, graphics,
                S3kPaletteOwners.SSZ_GHZ_BOSS_HIT_FLASH, S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                0, FLASH_COLOR_B, segaWords(FLASH_TABLE[row + 1], FLASH_TABLE[row + 2]));
    }

    private static byte[] segaWords(int... words) {
        byte[] out = new byte[words.length * 2];
        for (int i = 0; i < words.length; i++) {
            out[i * 2] = (byte) (words[i] >> 8);
            out[i * 2 + 1] = (byte) words[i];
        }
        return out;
    }

    /**
     * {@code x_pos}/{@code y_pos} are the object's own SST words, and the shared boss base reads
     * {@code state.x}/{@code state.y} for the defeat explosion offsets, the debug overlay and the
     * dynamic spawn a rewind recreation is rebuilt from. This object moves through its own
     * {@code MoveSprite2} fields, so those two have to follow or all three read the spawn.
     */
    private void syncBossStatePosition() {
        state.x = getX();
        state.y = getY();
    }

    /** The init block: PLC, art queue, palette save and {@code PalLoad_Line1}. */
    private void applyEntryOnce() {
        if (entryApplied || tryServices() == null) {
            return;
        }
        entryApplied = true;
        // move.b #1,(Boss_flag).w in the init; loc_7A3F8's clr.b is the only thing that clears it.
        setBossFlag(true);
        loadFightPalette();
    }

    /** {@code loc_7A2C0}. */
    private void enterFall() {
        state.routine = ROUTINE_FALLING;
        yVel = FALL_Y_VEL;
        waitTimer = FALL_FRAMES;
        waitContinuation = Continuation.END_FALL;
        head = spawnChild(() -> new SszMechaSonicHeadChild(
                new ObjectSpawn(getX() + SszMechaSonicHeadChild.CHILD_DX,
                        getY() + SszMechaSonicHeadChild.CHILD_DY, 0, 0, 0, false, 0), this));
    }

    /** {@code loc_7A2F0}: {@code MoveSprite2} then {@code Obj_Wait}. */
    private void updateFall() {
        moveSprite();
        if (--waitTimer >= 0) {
            return;
        }
        endFall();
    }

    /** {@code loc_7A2FC}. */
    private void endFall() {
        state.routine = ROUTINE_RUN_IN;
        waitContinuation = Continuation.NONE;
        xVel = RUN_X_VEL;
        shield = spawnChild(() -> new SszGhzBossShieldChild(
                new ObjectSpawn(getX() + SszGhzBossShieldChild.CHILD_DX, getY(),
                        0, 0, 0, false, 0), this));
        swingMax = SWING_MAX;
        yVel = SWING_MAX;
        swingAcceleration = SWING_ACCELERATION;
        swingDown = false;
    }

    /** {@code loc_7A32C}. */
    private void updateRunIn() {
        swing();
        moveSprite();
        int trigger = (nativeFramedCameraX() + CHAIN_TRIGGER_CAMERA_OFFSET) & 0xFFFF;
        if (Integer.compareUnsigned(trigger, getX()) < 0) {
            return;
        }
        state.routine = ROUTINE_WAIT_FOR_CHAIN;
        chainPhaseActive = true;
        dropChain();
    }

    /** {@code CreateChild9_TreeList ChildObjDat_7A684}: six links, each parented to the last. */
    private void dropChain() {
        SszGhzBossChainLinkChild previous = null;
        for (int index = 0; index < CHAIN_LINKS; index++) {
            final SszGhzBossChainLinkChild parentLink = previous;
            final int subtype = index * 2;
            SszGhzBossChainLinkChild link = spawnChild(() -> new SszGhzBossChainLinkChild(
                    new ObjectSpawn(getX(), getY(), 0, subtype, 0, false, 0), this, parentLink));
            if (link == null) {
                break;
            }
            chain.add(link);
            previous = link;
        }
    }

    /** {@code loc_7A35E}. */
    private void updateWaitForChain() {
        if (!chainReady) {
            return;
        }
        state.routine = ROUTINE_SWEEP;
        chainPhaseActive = false;
        xVel = RUN_X_VEL;
        waitTimer = TURN_WAIT_FRAMES;
        waitContinuation = Continuation.TURN_AROUND;
    }

    /** {@code loc_7A388} and its {@code Obj_Wait} continuation {@code loc_7A39A}. */
    private void updateSweep() {
        swing();
        moveSprite();
        if (waitContinuation != Continuation.TURN_AROUND) {
            return;
        }
        if (--waitTimer >= 0) {
            return;
        }
        waitContinuation = Continuation.NONE;
        state.routine = ROUTINE_WAIT_FOR_BALL;
        chainPhaseActive = true;
    }

    /** {@code loc_7A3A8}: {@code bclr #3,$38(a0)} reads and clears in one step. */
    private void updateWaitForBall() {
        if (!ballReachedFarSide) {
            return;
        }
        ballReachedFarSide = false;
        state.routine = ROUTINE_SWEEP;
        chainPhaseActive = false;
        xVel = -xVel;
        renderFlipped = !renderFlipped;
        waitTimer = REVERSE_WAIT_FRAMES;
        waitContinuation = Continuation.TURN_AROUND;
    }

    /** {@code Swing_UpAndDown}. */
    private void swing() {
        SwingMotion.Result result =
                SwingMotion.update(swingAcceleration, yVel, swingMax, swingDown);
        yVel = result.velocity();
        swingDown = result.directionDown();
    }

    /** {@code MoveSprite2}: 16.16 position, 8.8 velocity, no gravity. */
    private void moveSprite() {
        xPos += xVel << 8;
        yPos += yVel << 8;
    }

    /**
     * {@code loc_7A5EC}. The killing hit does not start the run: it installs
     * {@code Wait_FadeToLevelMusic} with {@code $34 = loc_7A3CE} and leaves {@code $2E} holding
     * whatever the last routine put there, so the ship hangs where it died until that timer runs
     * out. Only then does {@code loc_85674} arm the {@code (2*60)-1} escape window, allocate
     * {@code Obj_Song_Fade_ToLevelMusic} and hand over to {@code loc_7A3CE}.
     */
    @Override
    protected void onDefeatStarted() {
        escaping = true;
        escapeRunning = false;
        // move.w #$3F,$2E(a0) in BossDefeated (sonic3k.asm:180822), which loc_7A5EC jumps to
        // after installing Wait_FadeToLevelMusic. The fade wait is this value, not whatever the
        // last routine happened to leave in $2E.
        waitTimer = DEFEAT_WAIT_FRAMES;
        // loc_7A5EC clears no $38 bit and deletes nothing. What breaks the children up is the
        // shared touch response: Touch_Enemy's .checkhurtenemy runs
        // "subq.b #1,boss_hitcount2(a1) / bne.s .bossnotdefeated / bset #7,status(a1)"
        // (sonic3k.asm:20922) on the killing hit, so the ship's status bit 7 goes up in the
        // collision pass. statusBit7 is that bit; the children read it through parent3.
        statusBit7 = true;
    }

    /**
     * {@code status} bit 7, set on the ship by {@code Touch_Enemy}'s {@code .checkhurtenemy}
     * when {@code boss_hitcount2} reaches zero (sonic3k.asm:20922) — not by anything inside
     * {@code Obj_SSZGHZBoss}. It is what {@code loc_7A568} and
     * {@code Child_Draw[Touch]_Sprite_FlickerMove} test on {@code parent3}: the emitter deletes
     * itself and every chain link converts to {@code Obj_FlickerMove} scatter debris.
     */
    public boolean hasStatusBit7() { return statusBit7; }

    /** {@code Wait_FadeToLevelMusic}, {@code loc_7A3CE}, {@code loc_7A3E6} and {@code loc_7A3F8}. */
    private void updateEscape() {
        if (--waitTimer >= 0) {
            if (escapeRunning) {
                moveSprite();
            }
            return;
        }
        if (!escapeRunning) {
            // loc_85674 then loc_7A3CE.
            escapeRunning = true;
            waitTimer = ESCAPE_FRAMES;
            renderFlipped = true;
            xVel = ESCAPE_X_VEL;
            yVel = 0;
            return;
        }
        markBeaten();
    }

    /** {@code loc_7A3F8}. */
    private void markBeaten() {
        escaped = true;
        // clr.b (Boss_flag).w is loc_7A3F8's first instruction, before the flag writes below.
        setBossFlag(false);
        SszZoneRuntimeState state = sszState();
        if (state != null) {
            // st (Events_bg+$00).w: the negative byte sub_575EA reads as "beaten" and the
            // $79:$AA gated pad reads as its release.
            state.setEventsBgByte(0x00, 0xFF);
        }
        restoreLevelPaletteLine();
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    void setChainReady() { chainReady = true; }

    boolean isChainReady() { return chainReady; }

    void setBallReachedFarSide() { ballReachedFarSide = true; }

    /** {@code $38} bit 6: while it is set the emitter child hides. */
    public boolean isChainPhaseActive() { return chainPhaseActive; }

    /** {@code st (_unkFA89).w}: what deletes the Mecha Sonic head. */
    public boolean headShouldDelete() { return escaped; }

    public boolean hasEscaped() { return escaped; }

    public boolean isRenderFlippedForTest() { return renderFlipped; }

    public int routineForTest() { return state.routine; }

    public boolean isEscapingForTest() { return escaping; }

    public int waitTimerForTest() { return waitTimer; }

    /** True once the {@code Obj_SSZGHZBoss} init body has had its own execution. */
    public boolean initExecutedForTest() { return initExecuted; }

    /** True once {@code loc_7A294} has installed {@code loc_7A29C}. */
    public boolean dispatcherInstalledForTest() { return dispatcherInstalled; }

    /** {@code $20(a0)}: the hit window {@code sub_7A5A0} counts down. */
    public int hitWindowForTest() { return state.invulnerable ? state.invulnerabilityTimer : 0; }

    public int hitsRemainingForTest() { return state.hitCount; }

    public List<SszGhzBossChainLinkChild> chainForTest() { return List.copyOf(chain); }

    /**
     * {@code Boss_flag}. {@code Sonic3kLevelEventManager.setBossFlag} routes only to AIZ and CNZ,
     * so calling it from here would be a no-op dressed up as a port; the write goes to
     * {@link SszZoneRuntimeState} instead, where the rewind capture carries it and a consumer can
     * be added when one exists. Nothing in SSZ reads it yet — recorded as a gap rather than hidden.
     */
    private void setBossFlag(boolean active) {
        SszZoneRuntimeState ssz = sszState();
        if (ssz != null) {
            ssz.setBossFlag(active);
        }
    }

    private SszZoneRuntimeState sszState() {
        return S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
    }

    private com.openggf.camera.Camera camera() {
        return services().camera();
    }

    /**
     * {@code Camera_X_pos} as the ROM's 320-pixel frame sees it.
     *
     * <p>{@code loc_7A244}'s {@code addi.w #$110,d0} and {@code loc_7A32C}'s {@code addi.w #$A0,d0}
     * are both offsets from the camera's left edge, and the arena lock does not fix that edge: it
     * writes {@code Camera_max_X_pos = $160}, and the engine's wider viewport then centres the
     * same focus point, so {@code camera.getX()} reads {@code $160} at 320 px and {@code $70} at
     * 800 px. Read raw, the ship would spawn at {@code $180} and drop its chain at {@code $110} on
     * a wide screen — a different arena from the cartridge's. Same treatment, same reason, as
     * {@code HczMinibossInstance.nativeFramedCameraX} and {@code Sonic3kSSZEvents}'s copy.
     */
    private int nativeFramedCameraX() {
        var camera = camera();
        int focusExcess = Math.max(0,
                com.openggf.camera.DeadzoneGeometry.rightEdge(camera.getWidth())
                        - com.openggf.camera.DeadzoneGeometry.rightEdge(320));
        return (camera.getX() + focusExcess) & 0xFFFF;
    }

    /** {@code PalLoad_Line1 Pal_SSZGHZMisc}. */
    private void loadFightPalette() {
        if (paletteLoaded) {
            return;
        }
        try {
            byte[] line = services().rom()
                    .readBytes(Sonic3kConstants.PAL_SSZ_GHZ_MISC_ADDR, 32);
            S3kPaletteWriteSupport.applyLine(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager(),
                    S3kPaletteOwners.SSZ_GHZ_BOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    1, line);
            paletteLoaded = true;
        } catch (IOException | RuntimeException ignored) {
            // Partial object harnesses may not provide a ROM or palette surface.
        }
    }

    /** {@code loc_7A414}: copy {@code Target_palette_line_2} back, i.e. reload {@code Pal_SSZ1}. */
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
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    1, line, true);
        } catch (IOException | RuntimeException ignored) {
            // As above.
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(SHIP_MAPPING_FRAME, getX(), getY(), renderFlipped, false, 0);
        }
    }
}
