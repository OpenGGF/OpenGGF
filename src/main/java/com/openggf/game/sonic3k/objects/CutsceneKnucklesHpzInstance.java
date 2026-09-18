package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.HpzZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import static com.openggf.game.sonic3k.objects.HpzKnucklesCutsceneSupport.*;

/**
 * ROM {@code CutsceneKnux_HPZ} ({@code Obj_CutsceneKnuckles} subtype {@code $28},
 * sonic3k.asm:131259-132540): the Sonic/Tails Knuckles fight in Hidden Palace ({@code $1601}),
 * followed by Knuckles' part in the Master Emerald theft, the altar collapse and the ending.
 *
 * <p>The placed object copies itself into {@code Dynamic_object_RAM+object_size*45} (absolute SST
 * slot 48) and deletes itself, so the fight runs after the low slots the cutscene later
 * allocates with {@code AllocateObject}. The copy waits in {@code loc_63D1A} until {@code Check_CameraInRange} passes, then fades
 * the music ({@code sub_85D6A}), allocates the {@code loc_85CA4} music and camera lock and
 * becomes the 47-entry {@code off_63E1E} state machine. It deletes itself when Player 1 is
 * Knuckles.
 *
 * <p>Knuckles has no touch response. Every fight routine scans the {@code word_66152} box
 * ({@code loc_660BE}) and chooses a handler from a four-entry table by whether the player is
 * in front of him and level with him; a hit ({@code loc_6429E}) takes one of eight
 * {@code collision_property} points. The fight ends in {@code loc_64310}.
 */
public final class CutsceneKnucklesHpzInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code byte_6669A} scripts, read once per instance; ROM bytes, not state. */
    private transient S3kRawAnimation rawScripts;

    private S3kRawAnimation rawScripts() {
        if (rawScripts == null) {
            rawScripts = scripts(services());
        }
        return rawScripts;
    }

    private static final Logger LOG = Logger.getLogger(CutsceneKnucklesHpzInstance.class.getName());
    /** {@code word_63CEC}: camera Y range, X range, then the lock words for {@code sub_85D6A}. */
    private static final int CAMERA_MIN_Y = 0x180;
    private static final int CAMERA_MAX_Y = 0x480;
    private static final int CAMERA_MIN_X = 0xFE0;
    private static final int CAMERA_MAX_X = 0x11E0;
    static final int LOCK_Y = 0x380;
    static final int LOCK_X = 0x10E0;

    private static final int Y_RADIUS = 0x13;
    private static final int HIT_POINTS = 8;
    /** {@code loc_63D5C}: {@code move.w #$100,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x100);
    private static final int PAL_COLOUR_6 = 0x0088;

    /** Raw animation scripts (sonic3k.asm:135227-135372). */
    static final int ANI_66771 = 0x66771;
    static final int ANI_667AD = 0x667AD;
    static final int ANI_667B4 = 0x667B4;
    static final int ANI_667BC = 0x667BC;
    static final int ANI_667C1 = 0x667C1;
    static final int ANI_667CC = 0x667CC;
    static final int ANI_667D4 = 0x667D4;
    static final int ANI_667E0 = 0x667E0;
    static final int ANI_667E7 = 0x667E7;
    static final int ANI_667F5 = 0x667F5;
    static final int ANI_6680A = 0x6680A;
    static final int ANI_66824 = 0x66824;
    static final int ANI_6682F = 0x6682F;
    static final int ANI_6684F = 0x6684F;
    static final int ANI_6687B = 0x6687B;
    static final int ANI_66880 = 0x66880;
    static final int ANI_66885 = 0x66885;
    static final int ANI_668AF = 0x668AF;
    static final int ANI_6671F = 0x6671F;

    /** {@code $44(a0)}. */
    static final int SET_KNUCKLES = 0;
    static final int SET_GRAB = 2;
    static final int SET_TIRED = 4;

    /** {@code $34(a0)} callback labels. */
    private static final int CB_NONE = 0;
    private static final int CB_63EBE = 1;
    private static final int CB_6401C = 2;
    private static final int CB_643A4 = 3;
    private static final int CB_648EA = 4;
    private static final int CB_649B4 = 5;
    private static final int CB_649DE = 6;
    private static final int CB_64A8E = 7;
    private static final int CB_64AE2 = 8;

    /** Decision-table entries used by {@code loc_660BE}. */
    private static final int H_63F7A = 1;
    private static final int H_63FF8 = 2;
    private static final int H_6429E = 3;
    private static final int H_660A6 = 4;
    private static final int[] OFF_63F10 = {H_63F7A, H_63FF8, H_6429E, H_660A6};
    private static final int[] OFF_63F6A = {H_63F7A, H_660A6, H_6429E, H_660A6};
    private static final int[] OFF_63FE8 = {H_660A6, H_660A6, H_6429E, H_660A6};
    private static final int[] OFF_64056 = {H_660A6, H_660A6, H_660A6, H_660A6};
    /** {@code off_640CC}: unlike the jump's all-hurt table, a player behind or beside the glide hits Knuckles. */
    private static final int[] OFF_640CC = {H_660A6, H_660A6, H_6429E, H_660A6};
    private static final int[] OFF_64122 = {H_6429E, H_660A6, H_6429E, H_660A6};
    /** {@code word_66152}. */
    private static final int RANGE_OFFSET = -0x18;
    private static final int RANGE_SPAN = 0x30;
    private static final int[] WORD_646B0 = {-0x20, 0x40, -0x20, 0x40};
    private static final int[] WORD_647E4 = {-0x10, 0x60, 0, 0x10};

    /** Absolute SST slot of {@code Dynamic_object_RAM+object_size*45}. */
    private static final int COPY_SST_SLOT = 3 + 45;

    /** False for the placed object, which only runs {@code CutsceneKnux_HPZ}. */
    private boolean copied;
    private boolean initialized;
    private int routine;
    private int x;
    private int y;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;
    /** {@code render_flags} bit 0. */
    private boolean flipX;
    /** {@code art_tile} bit 15. */
    private boolean highPriority;
    private int mappingSet;
    private final S3kRawAnimation.State anim = new S3kRawAnimation.State();
    /** {@code $2E(a0)}. */
    private int timer;
    private int callback;
    /** {@code $38(a0)}. */
    private int flags;
    /** {@code $39(a0)}. */
    private int loopCount;
    /** {@code $40(a0)}. */
    private int acceleration;
    private int collisionProperty;
    /** {@code V_int_run_count} of the current update, for the lost-ring spawn in {@code HurtCharacter}. */
    private int frameVInt;


    public CutsceneKnucklesHpzInstance(ObjectSpawn spawn) {
        this(spawn, false);
    }

    private CutsceneKnucklesHpzInstance(ObjectSpawn spawn, boolean copied) {
        super(spawn, "CutsceneKnuxHPZ");
        this.copied = copied;
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public CutsceneKnucklesHpzInstance recreateForRewind(RewindRecreateContext ctx) {
        return new CutsceneKnucklesHpzInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (tryServices() == null) {
            return;
        }
        if (!copied) {
            cutsceneKnuxHpz();
            return;
        }
        if (!initialized) {
            updateInit();
            return;
        }
        HpzZoneRuntimeState hpz = hpz(services());
        if (hpz == null) {
            return;
        }
        dispatch(hpz, vIntRunCount);
        updateDynamicSpawn(x, y);
    }

    /** {@code CutsceneKnux_HPZ}: copy this SST into slot 45 of the dynamic object RAM. */
    private void cutsceneKnuxHpz() {
        var manager = services().objectManager();
        CutsceneKnucklesHpzInstance copy = new CutsceneKnucklesHpzInstance(spawn, true);
        if (manager != null) {
            if (manager.reserveDynamicSlot(COPY_SST_SLOT)) {
                ObjectLifetimeOps.addDynamicAtReservedSlot(manager, copy, COPY_SST_SLOT);
            } else {
                // The ROM overwrites whatever occupies the slot; the engine keeps the occupant
                // and takes the lowest free slot instead, which changes the fight's execution order.
                LOG.warning("CutsceneKnux_HPZ: SST slot " + COPY_SST_SLOT
                        + " is occupied; Knuckles runs from the lowest free slot instead");
                spawnFreeChild(() -> new CutsceneKnucklesHpzInstance(spawn, true));
            }
        }
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    /** {@code loc_63D1A}. */
    private void updateInit() {
        if (player1(services()) instanceof Knuckles) {
            // cmpi.b #2,(Player_1+character_id).w / beq.w CutsceneKnux_Delete
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        var camera = services().camera();
        int cameraX = camera.getX() & 0xFFFF;
        int cameraY = camera.getY() & 0xFFFF;
        // Check_CameraInRange: outside the window the object stays in Delete_Sprite_If_Not_In_Range.
        if (cameraY < CAMERA_MIN_Y || cameraY > CAMERA_MAX_Y
                || cameraX < CAMERA_MIN_X || cameraX > CAMERA_MAX_X) {
            // Delete_Sprite_If_Not_In_Range clears the copied respawn entry's bit 7.
            if (isCoarseXOutOfRange(x, cameraX, coarseXCullRange())) {
                ObjectLifetimeOps.destroyRespawnableOffscreen(this);
            }
            return;
        }
        HpzZoneRuntimeState hpz = hpz(services());
        if (hpz == null) {
            return;
        }
        initialized = true;
        // sub_85D6A: Boss_flag, cmd_FadeOut, $2E=2*60, stored camera bounds, _unkFAB0-_unkFAB6.
        if (services().levelEventProvider() instanceof AbstractLevelEventManager events) {
            events.setBossActive(true);
        }
        services().fadeOutMusic();
        timer = 2 * 60;
        hpz.storeCameraBounds(camera.getMinX(), camera.getMaxX(), camera.getMinY(),
                camera.getMaxYTarget());
        // Check_CameraInRange stores $27 bits 7 (camera below $380) and 6 (right of $10E0).
        spawnDynamicObjectLowestFreeSlot(new HpzKnucklesBossMusicObjectInstance(
                cameraY > LOCK_Y, cameraX > LOCK_X));
        // loc_63D5C
        mappingSet = SET_KNUCKLES;
        highPriority = false;
        flipX = true;
        anim.mappingFrame = 0xD8;
        collisionProperty = HIT_POINTS;
        anim.script = ANI_66771;
        loadCutscenePalette();
    }

    /** {@code Pal_CutsceneKnux} into line 2 with colour 6 forced to {@code $088}. */
    private void loadCutscenePalette() {
        byte[] colours;
        try {
            colours = services().romReader().slice(Sonic3kConstants.PAL_CUTSCENE_KNUX_ADDR, 32);
        } catch (IOException ex) {
            throw new IllegalStateException("Pal_CutsceneKnux", ex);
        }
        colours[12] = (byte) (PAL_COLOUR_6 >> 8);
        colours[13] = (byte) PAL_COLOUR_6;
        S3kPaletteWriteSupport.applyContiguousPatch(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager(),
                S3kPaletteOwners.HPZ_CUTSCENE_KNUCKLES,
                S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE,
                1, 0, colours);
    }

    private void dispatch(HpzZoneRuntimeState hpz, int vInt) {
        frameVInt = vInt;
        switch (routine) {
            case 0x00 -> loc63E7C(hpz);
            case 0x02 -> loc63EDA();
            case 0x04 -> loc63F3C();
            case 0x06 -> loc63FD8();
            case 0x08 -> animateCheckResult();
            case 0x0A -> loc6403A();
            case 0x0C -> loc64090();
            case 0x0E -> loc64102();
            case 0x10 -> loc6414C();
            case 0x12 -> loc64184(vInt);
            case 0x14 -> loc641EC();
            case 0x16 -> loc64258();
            case 0x18 -> loc642F0();
            case 0x1A -> animateRaw2();
            case 0x1C -> loc6438A(vInt);
            case 0x1E -> countdown(this::loc643F2);
            case 0x20 -> countdown(this::loc64450);
            case 0x22 -> loc64422(hpz);
            case 0x24 -> loc64460(hpz);
            case 0x26 -> loc6451E();
            case 0x28 -> countdown(this::loc645B0);
            case 0x2A -> loc64594();
            case 0x2C -> loc645D4();
            case 0x2E -> loc64610();
            case 0x30 -> countdown(() -> loc646B8(hpz));
            case 0x32 -> loc64692();
            case 0x34 -> loc646F6();
            case 0x36 -> loc64758();
            case 0x38 -> loc647C6();
            case 0x3A -> loc6480A(hpz);
            case 0x3C -> loc64846(vInt);
            case 0x3E -> loc6487C(hpz, vInt);
            case 0x40 -> animateRaw2();
            case 0x42 -> loc648E2(hpz);
            case 0x44 -> countdown(() -> loc64964(hpz));
            case 0x46 -> loc64952();
            case 0x48, 0x4A, 0x50 -> animateRaw2();
            case 0x4C -> countdown(this::loc64A1C);
            case 0x4E -> loc64A00();
            case 0x52 -> loc64A6E(hpz);
            case 0x54 -> loc64AC0(hpz);
            case 0x56 -> loc64B10(hpz);
            case 0x58 -> loc64B6A();
            case 0x5A -> loc64BB8(hpz);
            case 0x5C -> animateCheckResult();
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ fight

    /** {@code loc_63E7C}: idle loop; after the music lock, two passes of {@code byte_66771} start a charge. */
    private void loc63E7C(HpzZoneRuntimeState hpz) {
        int d2 = animateRaw2();
        if (d2 < 0 && hpz.knucklesCutsceneFlag(FLAG_CAMERA_READY)) {
            int count = (loopCount + 1) & 0xFF;
            if (count >= 2) {
                loc641C4();
                return;
            }
            loopCount = count;
        }
        Nearest near = findSonicTails();
        sub66094(near);
        if (near.dy >= 0x40 || near.dx >= 0x30) {
            return;
        }
        loc63F20();
    }

    /** {@code loc_63EBE}. */
    private void loc63EBE() {
        routine = 0x00;
        loopCount = 0;
        xVel = 0;
        yVel = 0;
        setAnimation(ANI_66771);
    }

    /** {@code loc_63EDA}: guard stance. */
    private void loc63EDA() {
        animateCheckResult();
        Nearest near = findSonicTails();
        sub66094(near);
        if (near.dy < 0x10 && near.dx < 0x20 && anim(near.player) != 2) {
            loc63FF8(near.player);
            return;
        }
        timer = (short) (timer - 1);
        if (timer < 0) {
            loc64066();
            return;
        }
        loc660BE(OFF_63F10);
    }

    /** {@code loc_63F20}. */
    private void loc63F20() {
        routine = 0x02;
        anim.mappingFrame = 0xDA;
        timer = 2 * 60 - 1;
        setAnimation(ANI_667AD);
    }

    /** {@code loc_63F3C}: counter-punch slide. */
    private void loc63F3C() {
        sub6615A();
        if (xVel != 0) {
            xVel = (short) (xVel + acceleration);
            moveSprite2();
        }
        // Animate_Raw2MultiDelay / bmi.w: the command path ends with clr.b, so N is never set.
        animateRaw2();
        loc660BE(OFF_63F6A);
    }

    /** {@code loc_63F7A}: punch a player standing in front of Knuckles and bounce them away. */
    private void loc63F7A(AbstractPlayableSprite target) {
        routine = 0x04;
        callback = CB_63EBE;
        int d3 = 0x2C0;
        int d4 = -0x20;
        if (!flipX) {
            d3 = -d3;
            d4 = -d4;
        }
        xVel = d3;
        acceleration = d4;
        if (target != null) {
            int d0 = target.getXSpeed();
            if (d0 >= -0x100 && d0 < 0x100) {
                d0 = d0 < 0 ? -0x100 : 0x100;
            }
            target.setXSpeed((short) -d0);
            target.setYSpeed((short) -target.getYSpeed());
            target.setGSpeed((short) -target.getGSpeed());
        }
        setAnimation(ANI_667B4);
    }

    /** {@code loc_63FD8}. */
    private void loc63FD8() {
        timer = (short) (timer - 1);
        if (timer < 0) {
            loc63EBE();
            return;
        }
        loc660BE(OFF_63FE8);
    }

    /** {@code loc_63FF8}: a player walks into Knuckles' guard. */
    private void loc63FF8(AbstractPlayableSprite target) {
        if (invulnerabilityTimer(target) != 0) {
            return;
        }
        routine = 0x06;
        anim.mappingFrame = 0xDE;
        timer = 7;
        loc660AE(target);
    }

    /** {@code loc_6401C}. */
    private void loc6401C() {
        routine = 0x0A;
        yVel = -0x600;
        services().playSfx(Sonic3kSfx.JUMP.id);
        setAnimation(ANI_667C1);
    }

    /** {@code loc_6403A}: jump. */
    private void loc6403A() {
        animateCheckResult();
        moveSprite();
        if (yVel >= 0) {
            loc640DC();
            return;
        }
        loc660BE(OFF_64056);
    }

    /** {@code loc_64066}. */
    private void loc64066() {
        routine = 0x08;
        sub66094(findSonicTails());
        xVel = 0;
        yVel = 0;
        callback = CB_6401C;
        setAnimation(ANI_667CC);
    }

    /** {@code loc_64090}: glide. */
    private void loc64090() {
        sub6615A();
        moveSprite2();
        if (xVel == 0) {
            loc64132();
            return;
        }
        Nearest near = findSonicTails();
        if (near.dx >= 0x30) {
            boolean facing = flipX;
            if (near.d0 != 0) {
                facing = !facing;
            }
            if (!facing) {
                loc64132();
                return;
            }
        }
        loc660BE(OFF_640CC);
    }

    /** {@code loc_640DC}. */
    private void loc640DC() {
        routine = 0x0C;
        anim.mappingFrame = 0xC0;
        xVel = flipX ? -0x400 : 0x400;
        yVel = 0x80;
    }

    /** {@code loc_64102}: drop after a glide. */
    private void loc64102() {
        animateRaw2();
        moveSprite();
        int d1 = floorDistance();
        if (d1 < 0) {
            loc64164(d1);
            return;
        }
        loc660BE(OFF_64122);
    }

    /** {@code loc_64132}. */
    private void loc64132() {
        routine = 0x0E;
        flipX = !flipX;
        xVel = 0;
        setAnimation(ANI_667D4);
    }

    /** {@code loc_6414C}. */
    private void loc6414C() {
        animateCheckResult();
        sub66094(findSonicTails());
        loc660BE(OFF_63F10);
    }

    /** {@code loc_64164}. */
    private void loc64164(int d1) {
        y = (y + d1) & 0xFFFF;
        routine = 0x10;
        yVel = 0;
        callback = CB_63EBE;
        setAnimation(ANI_667E0);
    }

    /** {@code loc_64184}: spin-dash charge. */
    private void loc64184(int vInt) {
        if ((vInt & 0xF) == 0) {
            services().playSfx(Sonic3kSfx.ROLL.id);
        }
        animateCheckResult();
        timer = (short) (timer - 1);
        if (timer < 0) {
            loc64222();
            return;
        }
        if (timer == 43) {
            flags &= ~1;
            spawnChild(() -> new HpzKnucklesDustObjectInstance(
                    new ObjectSpawn(x, y, 0, 0, 0, false, 0), this));
        }
        loc660BE(OFF_64056);
    }

    /** {@code loc_641C4}. */
    private void loc641C4() {
        routine = 0x12;
        sub66094(findSonicTails());
        xVel = 0;
        yVel = 0;
        timer = 60 - 1;
        setAnimation(ANI_667F5);
    }

    /** {@code loc_641EC}: spin dash. */
    private void loc641EC() {
        animateCheckResult();
        sub6615A();
        if (xVel == 0) {
            flipX = !flipX;
            y = (y - 4) & 0xFFFF;
            loc63EBE();
            return;
        }
        xVel = (short) (xVel + acceleration);
        moveSprite2();
        loc660BE(OFF_64056);
    }

    /** {@code loc_64222}. */
    private void loc64222() {
        routine = 0x14;
        flags |= 1;
        int d0 = 0x800;
        int d1 = -0x10;
        if (flipX) {
            d0 = -d0;
            d1 = -d1;
        }
        xVel = d0;
        acceleration = d1;
        y = (y + 4) & 0xFFFF;
        setAnimation(ANI_667C1);
    }

    /** {@code loc_64258}: knocked back after a hit. */
    private void loc64258() {
        animateCheckResult();
        sub6615A();
        moveSprite();
        if (yVel < 0) {
            return;
        }
        int d1 = floorDistance();
        if (d1 >= 0) {
            return;
        }
        y = (y + d1) & 0xFFFF;
        int d0 = (short) (((services().camera().getX() & 0xFFFF) + 0xA0 - x) & 0xFFFF);
        if (d0 < 0) {
            d0 = -d0;
        }
        if ((d0 & 0xFFFF) < 0x60) {
            loc641C4();
        } else {
            loc64066();
        }
    }

    /** {@code loc_6429E}: the player hits Knuckles. */
    private void loc6429E(AbstractPlayableSprite attacker) {
        flags |= 1;
        services().playSfx(Sonic3kSfx.FLOOR_THUMP.id);
        // sub.w x_pos(a0),d1 / bcs.s: move away from the attacker.
        int d3 = 0x300;
        if (x(attacker) >= x) {
            d3 = -d3;
        }
        xVel = d3;
        yVel = -0x300;
        collisionProperty = (collisionProperty - 1) & 0xFF;
        if (collisionProperty == 0) {
            loc64310();
            return;
        }
        routine = 0x16;
        mappingSet = SET_KNUCKLES;
        anim.mappingFrame = 0x8D;
        setAnimation(ANI_667BC);
    }

    /** {@code loc_642F0}: final knock-back. */
    private void loc642F0() {
        sub6615A();
        moveSprite();
        if (yVel < 0) {
            return;
        }
        int d1 = floorDistance();
        if (d1 < 0) {
            loc64356(d1);
        }
    }

    /** {@code loc_64310}: defeat. */
    private void loc64310() {
        routine = 0x18;
        // clr.b (Update_HUD_timer).w
        if (services().levelGamestate() != null) {
            services().levelGamestate().pauseTimer();
        }
        mappingSet = SET_GRAB;
        anim.mappingFrame = 2;
        flags |= 0x10;
        // move.b #mus_LRZ2,(Current_music+1).w and Obj_Song_Fade_Transition(mus_LRZ2)
        spawnDynamicObjectLowestFreeSlot(SongFadeTransitionInstance.transitionTo(Sonic3kMusic.LRZ2.id));
    }

    /** {@code loc_64356}. */
    private void loc64356(int d1) {
        y = (y + d1) & 0xFFFF;
        routine = 0x1A;
        callback = CB_643A4;
        services().playSfx(Sonic3kSfx.DOOR_MOVE.id);
        setAnimation(ANI_6680A);
        // Queue_Kos_Module ArtKosM_HPZKnuxDizzy: the standalone sheet is already loaded.
    }

    /** {@code loc_6438A}. */
    private void loc6438A(int vInt) {
        if ((vInt & 0x1F) == 0) {
            services().playSfx(Sonic3kSfx.COLLAPSE.id);
        }
        timer = (short) (timer - 1);
        if (timer < 0) {
            loc643C6();
        }
    }

    /** {@code loc_643A4}: the room shakes and sixteen explosions go off. */
    private void loc643A4() {
        routine = 0x1C;
        timer = 0x7F;
        HpzZoneRuntimeState hpz = hpz(services());
        if (hpz != null) {
            hpz.screenShake().writeFlag(-1);
        }
        // ChildObjDat_66602: $10 x loc_652A2.
        for (int i = 0; i < 0x10; i++) {
            int subtype = i * 2;
            spawnChild(() -> new HpzKnucklesCeilingExplosionObjectInstance(
                    new ObjectSpawn(x, y, 0, subtype, 0, false, 0)));
        }
    }

    /** {@code loc_643C6}: "!" over Knuckles. */
    private void loc643C6() {
        routine = 0x1E;
        anim.mappingFrame = 6;
        timer = 0xF;
        spawnChild(() -> new HpzKnucklesDizzyStarsObjectInstance(
                new ObjectSpawn(x, y, 0, 0xFF, 0, false, 0)));
    }

    /** {@code loc_643F2}. */
    private void loc643F2() {
        routine = 0x20;
        mappingSet = SET_KNUCKLES;
        anim.mappingFrame = 0x56;
        flipX = false;
        timer = 7;
        xVel = 0;
        yVel = 0;
    }

    /** {@code loc_64422}: Knuckles runs right, off the screen. */
    private void loc64422(HpzZoneRuntimeState hpz) {
        animateCheckResult();
        int d0 = (xVel + 0x10) & 0xFFFF;
        if (d0 < 0x400) {
            xVel = d0;
        }
        moveSprite2();
        if (((services().camera().getX() + 0x180) & 0xFFFF) < x) {
            loc64472(hpz);
        }
    }

    /** {@code loc_64450}. */
    private void loc64450() {
        routine = 0x22;
        setAnimation(ANI_66824);
    }

    // ------------------------------------------------------------------ emerald theft

    /** {@code loc_64460}: wait for the camera pan. */
    private void loc64460(HpzZoneRuntimeState hpz) {
        animateCheckResult();
        if (hpz.knucklesCutsceneFlag(FLAG_CAMERA_READY)) {
            loc6453C();
        }
    }

    /** {@code loc_64472}: Knuckles reappears beside the Master Emerald. */
    private void loc64472(HpzZoneRuntimeState hpz) {
        routine = 0x24;
        mappingSet = SET_GRAB;
        anim.mappingFrame = 0xA;
        setAnimation(ANI_6687B);
        highPriority = true;
        x = 0x1620;
        y = 0x3AC;
        hpz.clearKnucklesCutsceneFlags();
        hpz.screenShake().writeFlag(0);
        spawnDynamicObjectLowestFreeSlot(new HpzEmeraldTheftPlayerControlObjectInstance());
        spawnDynamicObjectLowestFreeSlot(new HpzRobotnikShipObjectInstance(
                new ObjectSpawn(HpzRobotnikShipObjectInstance.START_X,
                        HpzRobotnikShipObjectInstance.START_Y, 0, 0, 0, false, 0)));
        spawnDynamicObjectLowestFreeSlot(new S3kCameraGradualObjectInstance(
                S3kCameraGradualObjectInstance.INC_END_X));
        spawnDynamicObjectLowestFreeSlot(new S3kCameraGradualObjectInstance(
                S3kCameraGradualObjectInstance.DEC_START_Y));
        // move.w (Camera_stored_max_Y_pos).w,(Camera_target_max_Y_pos).w
        services().camera().setMaxYTarget((short) hpz.cameraStoredMaxY());
        spawnDynamicObjectLowestFreeSlot(new S3kCameraGradualObjectInstance(
                S3kCameraGradualObjectInstance.INC_END_Y));
        // Queue_Kos_Module ArtKosM_KnuxFinalBossCrane / Load_PLC_Raw PLC_KnuxHPZCutsceneShip:
        // the standalone sheets are already loaded.
    }

    /** {@code loc_6451E}. */
    private void loc6451E() {
        animateCheckResult();
        moveSprite();
        if (yVel < 0) {
            return;
        }
        int d1 = floorDistance();
        if (d1 < 0) {
            loc6457C(d1);
        }
    }

    /** {@code loc_6453C}. */
    private void loc6453C() {
        routine = 0x26;
        mappingSet = SET_KNUCKLES;
        anim.mappingFrame = 0x9A;
        xVel = 0xC0;
        yVel = -0x600;
        services().playSfx(Sonic3kSfx.JUMP.id);
        setAnimation(ANI_667C1);
    }

    /** {@code loc_6457C}. */
    private void loc6457C(int d1) {
        y = (y + d1) & 0xFFFF;
        routine = 0x28;
        anim.mappingFrame = 0xD6;
        timer = 8;
    }

    /** {@code loc_64594}. */
    private void loc64594() {
        animateCheckResult();
        moveSprite();
        if (yVel < 0) {
            return;
        }
        if (y >= 0x339) {
            loc645E0();
        }
    }

    /** {@code loc_645B0}. */
    private void loc645B0() {
        routine = 0x2A;
        xVel = -0xC0;
        yVel = -0x500;
        services().playSfx(Sonic3kSfx.JUMP.id);
        setAnimation(ANI_667C1);
    }

    /** {@code loc_645D4}. */
    private void loc645D4() {
        timer = (short) (timer - 1);
        if (timer < 0) {
            loc6463C();
            return;
        }
        animateCheckResult();
    }

    /** {@code loc_645E0}: Knuckles hugs the Master Emerald. */
    private void loc645E0() {
        routine = 0x2C;
        y = 0x339;
        mappingSet = SET_GRAB;
        anim.mappingFrame = 0xA;
        timer = 0x7F;
        setAnimation(ANI_66880);
    }

    /** {@code loc_64610}. */
    private void loc64610() {
        animateCheckResult();
        accelerateRightClamped();
        moveSprite2();
        if (x >= 0x1660) {
            loc64670();
        }
    }

    /** {@code loc_6463C}. */
    private void loc6463C() {
        routine = 0x2E;
        mappingSet = SET_KNUCKLES;
        anim.mappingFrame = 7;
        xVel = 0;
        yVel = 0;
        setAnimation(ANI_6682F);
    }

    /** {@code loc_64692}: Knuckles leaps at the ship. */
    private void loc64692() {
        animateCheckResult();
        moveSprite();
        HpzRobotnikShipObjectInstance ship = ship(services());
        if (ship != null && inTheirRange(x, y, ship.getX(), ship.getY(), WORD_646B0)) {
            loc6471A(ship);
        }
    }

    /** {@code loc_64670}. */
    private void loc64670() {
        routine = 0x30;
        mappingSet = SET_GRAB;
        anim.mappingFrame = 0xD;
        timer = 30 - 1;
    }

    /** {@code loc_646B8}. */
    private void loc646B8(HpzZoneRuntimeState hpz) {
        routine = 0x32;
        hpz.setKnucklesCutsceneFlag(FLAG_RELEASE_PLAYER);
        mappingSet = SET_KNUCKLES;
        anim.mappingFrame = 0x9A;
        xVel = 0x300;
        yVel = -0x500;
        services().playSfx(Sonic3kSfx.JUMP.id);
        setAnimation(ANI_667C1);
    }

    /** {@code loc_646F6}. */
    private void loc646F6() {
        animateCheckResult();
        moveSprite();
        if (yVel < 0) {
            return;
        }
        if (y >= 0x3AB) {
            loc6478E();
        }
    }

    /** {@code loc_6471A}: bounce off the ship ({@code bset #6,status(a1)} flashes it). */
    private void loc6471A(HpzRobotnikShipObjectInstance ship) {
        ship.markHitByKnuckles();
        routine = 0x34;
        mappingSet = SET_KNUCKLES;
        anim.mappingFrame = 0x9A;
        xVel = -0x200;
        yVel = -0x200;
        services().playSfx(Sonic3kSfx.JUMP.id);
        setAnimation(ANI_667C1);
    }

    /** {@code loc_64758}: run back at the emerald. */
    private void loc64758() {
        animateCheckResult();
        accelerateRightClamped();
        moveSprite2();
        HPZMasterEmeraldObjectInstance emerald = masterEmerald(services());
        if (emerald != null && ((emerald.getX() - x) & 0xFFFF) < 0x30) {
            loc647EC();
        }
    }

    /** {@code loc_6478E}. */
    private void loc6478E() {
        y = 0x3AB;
        routine = 0x36;
        highPriority = false;
        mappingSet = SET_KNUCKLES;
        anim.mappingFrame = 7;
        xVel = 0;
        yVel = 0;
        setAnimation(ANI_6682F);
    }

    /** {@code loc_647C6}. */
    private void loc647C6() {
        animateCheckResult();
        moveSprite();
        HPZMasterEmeraldObjectInstance emerald = masterEmerald(services());
        if (emerald != null && inTheirRange(x, y, emerald.getX(), emerald.getY(), WORD_647E4)) {
            loc6482A();
        }
    }

    /** {@code loc_647EC}. */
    private void loc647EC() {
        routine = 0x38;
        yVel = -0x600;
        services().playSfx(Sonic3kSfx.JUMP.id);
        setAnimation(ANI_667C1);
    }

    /** {@code loc_6480A}: hang on to the carried emerald. */
    private void loc6480A(HpzZoneRuntimeState hpz) {
        if (hpz.knucklesCutsceneFlag(FLAG_ZAP)) {
            routine = 0x3C;
            timer = 60 - 1;
        }
        loc64812();
    }

    /** {@code loc_64812}. */
    private void loc64812() {
        HPZMasterEmeraldObjectInstance emerald = masterEmerald(services());
        if (emerald == null) {
            return;
        }
        x = (emerald.getX() - 0xC) & 0xFFFF;
        y = emerald.getY() & 0xFFFF;
    }

    /** {@code loc_6482A}. */
    private void loc6482A() {
        routine = 0x3A;
        mappingSet = SET_GRAB;
        anim.mappingFrame = 0;
        loc64812();
    }

    /** {@code loc_64846}: electrocuted. */
    private void loc64846(int vInt) {
        timer = (short) (timer - 1);
        if (timer < 0) {
            loc6489E();
            return;
        }
        anim.mappingFrame = (vInt & 1) != 0 ? 1 : 0;
        if ((vInt & 3) != 0) {
            services().playSfx(Sonic3kSfx.GRAVITY_MACHINE.id);
        }
        loc64812();
    }

    /** {@code loc_6487C}. */
    private void loc6487C(HpzZoneRuntimeState hpz, int vInt) {
        anim.mappingFrame = (vInt & 1) != 0 ? 1 : 0;
        moveSpriteLightGravity();
        int d1 = floorDistance();
        if (d1 < 0) {
            loc648B8(hpz, d1);
        }
    }

    /** {@code loc_6489E}. */
    private void loc6489E() {
        routine = 0x3E;
        xVel = -0x100;
        yVel = -0x100;
    }

    /** {@code loc_648B8}. */
    private void loc648B8(HpzZoneRuntimeState hpz, int d1) {
        y = (y + d1) & 0xFFFF;
        routine = 0x40;
        hpz.setKnucklesCutsceneFlag(FLAG_KNUCKLES_LANDED);
        xVel = 0;
        yVel = 0;
        callback = CB_648EA;
        setAnimation(ANI_667E7);
    }

    /** {@code loc_648E2}. */
    private void loc648E2(HpzZoneRuntimeState hpz) {
        if (hpz.playerReachedEmeraldAltar()) {
            loc648FA(hpz);
        }
    }

    /** {@code loc_648FA}: the ship's escape blows the altar floor. */
    private void loc648FA(HpzZoneRuntimeState hpz) {
        routine = 0x44;
        timer = 0x10;
        var camera = services().camera();
        camera.setScrollLocked(false);
        camera.setMaxYTarget((short) 0x5C0);
        hpz.setCameraStoredMaxY(0x5C0);
        services().playSfx(Sonic3kSfx.BIG_RUMBLE.id);
        spawnDynamicObjectLowestFreeSlot(new S3kCameraGradualObjectInstance(
                S3kCameraGradualObjectInstance.INC_END_Y));
        // bsr.w loc_64930 then fall into it: two Child6_CreateBossExplosion subtype $14.
        for (int i = 0; i < 2; i++) {
            spawnAfterCurrentSibling(() -> new HpzBossExplosionSpawnerObjectInstance(
                    new ObjectSpawn(0x1880, 0x3D0, 0, 0x14, 0, false, 0)));
        }
    }

    /** {@code loc_64952}: fall through the broken floor. */
    private void loc64952() {
        moveSprite();
        int d1 = floorDistance();
        if (d1 < 0) {
            loc6498A(d1);
        }
    }

    /** {@code loc_64964}. */
    private void loc64964(HpzZoneRuntimeState hpz) {
        routine = 0x46;
        anim.mappingFrame = 2;
        hpz.requestForegroundCollapse();
        hpz.screenShake().writeFlag(0x14);
        spawnChild(() -> new HpzCollapseBlockObjectInstance(
                new ObjectSpawn(HpzCollapseBlockObjectInstance.X, HpzCollapseBlockObjectInstance.Y,
                        0, 0, 0, false, 0)));
    }

    /** {@code loc_6498A}. */
    private void loc6498A(int d1) {
        y = (y + d1) & 0xFFFF;
        routine = 0x48;
        xVel = 0;
        yVel = 0;
        callback = CB_649B4;
        setAnimation(ANI_6680A);
    }

    /** {@code loc_649B4}: dizzy. */
    private void loc649B4() {
        routine = 0x4A;
        callback = CB_649DE;
        setAnimation(ANI_6684F);
        spawnChild(() -> new HpzKnucklesDizzyStarsObjectInstance(
                new ObjectSpawn(x, y, 0, 0, 0, false, 0)));
    }

    /** {@code loc_649DE}. */
    private void loc649DE() {
        routine = 0x4C;
        mappingSet = SET_KNUCKLES;
        anim.mappingFrame = 0x56;
        timer = 7;
    }

    /** {@code loc_64A00}. */
    private void loc64A00() {
        animateCheckResult();
        xVel = (short) (xVel - 0xC);
        moveSprite2();
        if (x <= 0x1810) {
            loc64A3C();
        }
    }

    /** {@code loc_64A1C}. */
    private void loc64A1C() {
        routine = 0x4E;
        flipX = true;
        xVel = 0;
        setAnimation(ANI_6682F);
    }

    /** {@code loc_64A3C}: punch the block. */
    private void loc64A3C() {
        routine = 0x50;
        flipX = false;
        mappingSet = SET_GRAB;
        anim.mappingFrame = 0xE;
        callback = CB_64A8E;
        setAnimation(ANI_668AF);
    }

    /** {@code loc_64A6E}. */
    private void loc64A6E(HpzZoneRuntimeState hpz) {
        int result = animateRaw2();
        // beq.s: WAITING sets Z, and every command path ends with clr.b anim_frame(a0).
        if (result != S3kRawAnimation.ADVANCED || anim.animFrame != 6) {
            return;
        }
        hpz.markCollapseBlockBreak();
        services().playSfx(Sonic3kSfx.COLLAPSE.id);
    }

    /** {@code loc_64A8E}. */
    private void loc64A8E() {
        routine = 0x52;
        flipX = true;
        callback = CB_64AE2;
        mappingSet = SET_KNUCKLES;
        anim.mappingFrame = 0x56;
        setAnimation(ANI_66885);
    }

    /** {@code loc_64AC0}. */
    private void loc64AC0(HpzZoneRuntimeState hpz) {
        animateCheckResult();
        xVel = (short) (xVel - 0xC);
        moveSprite2();
        if (((services().camera().getX() - 0x40) & 0xFFFF) >= x) {
            loc64B22(hpz);
        }
    }

    /** {@code loc_64AE2}. */
    private void loc64AE2() {
        routine = 0x54;
        flipX = true;
        mappingSet = SET_KNUCKLES;
        anim.mappingFrame = 7;
        xVel = 0;
        setAnimation(ANI_6682F);
    }

    // ------------------------------------------------------------------ ending

    /** {@code loc_64B10}. */
    private void loc64B10(HpzZoneRuntimeState hpz) {
        animateMultiDelay();
        if (hpz.knucklesCutsceneFlag(FLAG_ENDING_JUMP)) {
            loc64B80();
        }
    }

    /** {@code loc_64B22}: tired Knuckles on the lower floor. */
    private void loc64B22(HpzZoneRuntimeState hpz) {
        routine = 0x56;
        mappingSet = SET_TIRED;
        anim.mappingFrame = 0;
        flipX = false;
        x = 0x161C;
        y = 0x62C;
        hpz.setCameraStoredMinX(0x1520);
        spawnDynamicObjectLowestFreeSlot(new S3kCameraGradualObjectInstance(
                S3kCameraGradualObjectInstance.DEC_START_X));
        setAnimation(ANI_6671F);
    }

    /** {@code loc_64B6A}. */
    private void loc64B6A() {
        animateCheckResult();
        moveSprite();
        if (x <= 0x15C0) {
            loc64BC6();
        }
    }

    /** {@code loc_64B80}. */
    private void loc64B80() {
        routine = 0x58;
        mappingSet = SET_KNUCKLES;
        anim.mappingFrame = 0x9A;
        xVel = -0x1E0;
        yVel = -0x600;
        services().playSfx(Sonic3kSfx.JUMP.id);
        setAnimation(ANI_667C1);
    }

    /** {@code loc_64BB8}. */
    private void loc64BB8(HpzZoneRuntimeState hpz) {
        if (hpz.knucklesCutsceneFlag(FLAG_BEAM_READY)) {
            loc64C00();
            return;
        }
        animateMultiDelay();
    }

    /** {@code loc_64BC6}: land on the teleporter. */
    private void loc64BC6() {
        routine = 0x5A;
        x = 0x15C0;
        y = 0x600;
        mappingSet = SET_TIRED;
        anim.mappingFrame = 0;
        flipX = false;
        setAnimation(ANI_6671F);
    }

    /** {@code loc_64C00}. */
    private void loc64C00() {
        routine = 0x5C;
        mappingSet = SET_KNUCKLES;
        anim.mappingFrame = 0x9A;
        setAnimation(ANI_667C1);
    }

    // ------------------------------------------------------------------ shared helpers

    private void countdown(Runnable expired) {
        timer = (short) (timer - 1);
        if (timer < 0) {
            expired.run();
        }
    }

    private void accelerateRightClamped() {
        int d0 = (xVel + 0xC) & 0xFFFF;
        if (d0 > 0x300) {
            d0 = 0x300;
        }
        xVel = d0;
    }

    private void setAnimation(int script) {
        S3kRawAnimation.set(anim, script);
    }

    private int animateCheckResult() {
        return rawScripts().animateCheckResult(anim, this::runCallback);
    }

    private int animateRaw2() {
        return rawScripts().animateRaw2MultiDelay(anim, this::runCallback);
    }

    private int animateMultiDelay() {
        return rawScripts().animateMultiDelay(anim, this::runCallback);
    }

    private void runCallback() {
        switch (callback) {
            case CB_63EBE -> loc63EBE();
            case CB_6401C -> loc6401C();
            case CB_643A4 -> loc643A4();
            case CB_648EA -> routine = 0x42;
            case CB_649B4 -> loc649B4();
            case CB_649DE -> loc649DE();
            case CB_64A8E -> loc64A8E();
            case CB_64AE2 -> loc64AE2();
            default -> {
            }
        }
    }

    /** {@code loc_660BE}: {@code Check_PlayerInRange} over {@code word_66152}, Player 1 first. */
    private void loc660BE(int[] table) {
        int left = (x + RANGE_OFFSET) & 0xFFFF;
        int right = (left + RANGE_SPAN) & 0xFFFF;
        int top = (y + RANGE_OFFSET) & 0xFFFF;
        int bottom = (top + RANGE_SPAN) & 0xFFFF;
        AbstractPlayableSprite p1 = player1(services());
        AbstractPlayableSprite p2 = player2(services());
        boolean p1InRange = p1 != null && inBox(p1, left, right, top, bottom);
        boolean p2InRange = p2 != null && inBox(p2, left, right, top, bottom);
        if (p1InRange) {
            sub660E2(p1, table);
        }
        if (p2InRange) {
            sub660E2(p2, table);
        }
    }

    private static boolean inBox(AbstractPlayableSprite player, int left, int right, int top, int bottom) {
        int px = x(player);
        int py = y(player);
        return px >= left && px < right && py >= top && py < bottom;
    }

    /** {@code sub_660E2}. */
    private void sub660E2(AbstractPlayableSprite target, int[] table) {
        if ((flags & 0x10) != 0) {
            return;
        }
        boolean behind = flipX;
        if (x(target) < x) {
            behind = !behind;
        }
        boolean invincibleNotRolling = anim(target) != 2 && statusInvincible(target);
        if (!behind) {
            int dy = (short) ((y(target) - y) & 0xFFFF);
            if (dy < 0) {
                dy = -dy;
            }
            if ((dy & 0xFFFF) < 0x10) {
                runHandler(invincibleNotRolling ? H_6429E : table[0], target);
                return;
            }
        }
        runHandler(invincibleNotRolling ? H_6429E : table[2], target);
    }

    private void runHandler(int handler, AbstractPlayableSprite target) {
        switch (handler) {
            case H_63F7A -> loc63F7A(target);
            case H_63FF8 -> loc63FF8(target);
            case H_6429E -> loc6429E(target);
            case H_660A6 -> loc660A6(target);
            default -> {
            }
        }
    }

    /** {@code loc_660A6}. */
    private void loc660A6(AbstractPlayableSprite target) {
        if (invulnerabilityTimer(target) != 0) {
            return;
        }
        loc660AE(target);
    }

    /**
     * {@code loc_660AE}: {@code btst #Status_InAir,status_secondary(a1)}. {@code Status_InAir}
     * is bit 1, which in {@code status_secondary} is {@code Status_Invincible}.
     */
    private void loc660AE(AbstractPlayableSprite target) {
        if (statusInvincible(target)) {
            return;
        }
        hurtDirectly(services(), target, x, frameVInt);
    }

    /** {@code sub_66094}: face the nearest player. */
    private void sub66094(Nearest near) {
        flipX = near.d0 == 0;
    }

    /** {@code sub_6615A}: stop at the camera edges. */
    private void sub6615A() {
        if (xVel == 0) {
            return;
        }
        int cameraX = services().camera().getX() & 0xFFFF;
        if ((short) xVel > 0) {
            if (((cameraX + 0x130) & 0xFFFF) <= x) {
                xVel = 0;
            }
        } else if (((cameraX + 0x10) & 0xFFFF) >= x) {
            xVel = 0;
        }
    }

    private record Nearest(AbstractPlayableSprite player, int d0, int dx, int dy) {}

    /** {@code Find_SonicTails}. */
    private Nearest findSonicTails() {
        AbstractPlayableSprite p1 = player1(services());
        AbstractPlayableSprite p2 = player2(services());
        int d0 = 0;
        int d2 = (short) ((x - x(p1)) & 0xFFFF);
        if (d2 < 0) {
            d2 = -d2;
            d0 = 2;
        }
        int d1 = 0;
        int d3 = (short) ((x - x(p2)) & 0xFFFF);
        if (d3 < 0) {
            d3 = -d3;
            d1 = 2;
        }
        AbstractPlayableSprite chosen = p1;
        if ((d2 & 0xFFFF) > (d3 & 0xFFFF)) {
            chosen = p2;
            d0 = d1;
            d2 = d3;
        }
        int dy = (short) ((y - y(chosen)) & 0xFFFF);
        if (dy < 0) {
            dy = -dy;
        }
        return new Nearest(chosen, d0, d2 & 0xFFFF, dy & 0xFFFF);
    }

    private int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(x, y, Y_RADIUS).distance();
    }

    private void moveSprite() {
        SubpixelMotion.State s = motion();
        SubpixelMotion.moveSprite(s, SubpixelMotion.S3K_GRAVITY);
        apply(s);
    }

    private void moveSprite2() {
        SubpixelMotion.State s = motion();
        SubpixelMotion.moveSprite2(s);
        apply(s);
    }

    /** {@code MoveSprite_LightGravity}: gravity {@code $20}. */
    private void moveSpriteLightGravity() {
        SubpixelMotion.State s = motion();
        SubpixelMotion.moveSprite(s, 0x20);
        apply(s);
    }

    private SubpixelMotion.State motion() {
        return new SubpixelMotion.State(x, y, xSub, ySub, (short) xVel, (short) yVel);
    }

    private void apply(SubpixelMotion.State s) {
        x = s.x & 0xFFFF;
        y = s.y & 0xFFFF;
        xSub = s.xSub;
        ySub = s.ySub;
        xVel = (short) s.xVel;
        yVel = (short) s.yVel;
    }

    // ------------------------------------------------------------------ teleporter links

    /** {@code loc_45B1C}: {@code subq.w #1,y_pos(a1)}. */
    void nativeAddY(int delta) {
        y = (y + delta) & 0xFFFF;
    }

    /** {@code loc_45B1C}: {@code add.w d1,x_pos(a1)}. */
    void nativeAddX(int delta) {
        x = (x + delta) & 0xFFFF;
    }

    /** {@code loc_45B6C}: {@code clr.l (a1)}. */
    void clearCodePointer() {
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    /** {@code $38(a0)} bit 0: the dust child deletes itself while it is set. */
    boolean dustSuppressed() {
        return (flags & 1) != 0;
    }

    public int routineForTest() { return routine; }
    public int collisionPropertyForTest() { return collisionProperty; }
    public int mappingSetForTest() { return mappingSet; }
    public int mappingFrameForTest() { return anim.mappingFrame; }
    public boolean initializedForTest() { return initialized; }
    public boolean flipXForTest() { return flipX; }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isPersistent() { return copied; }
    @Override public boolean isHighPriority() { return highPriority; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x18; }

    boolean renderFlipX() { return flipX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized) {
            return;
        }
        String key = switch (mappingSet) {
            case SET_GRAB -> Sonic3kObjectArtKeys.HPZ_CUTSCENE_KNUCKLES_GRAB;
            case SET_TIRED -> Sonic3kObjectArtKeys.HPZ_CUTSCENE_KNUCKLES_TIRED;
            default -> Sonic3kObjectArtKeys.HPZ_CUTSCENE_KNUCKLES;
        };
        PatternSpriteRenderer renderer = getRenderer(key);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(anim.mappingFrame, x, y, flipX, false);
        }
    }

}
