package com.openggf.game.sonic3k.bluespheres;

import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.TileLoadRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The perfect screen's difficulty objects: Difficulty_Level_Sprites_Display
 * (sonic3k.asm:102101-102110) allocates one Difficulty_Sprites object
 * (sonic3k.asm:102115-102127) per difficulty step, and the screen's loop
 * runs SuperHyper_PalCycle (sonic3k.asm:4640-4774) for the Super Sonic step.
 *
 * <p>Each object's per-frame code is a {@link S3kBlueSpheresObject.Routine}
 * whose instance holds the SST scratch fields the ROM routine keeps
 * ({@code $2E}, {@code $30}, {@code $38}-{@code $40}, {@code subtype},
 * {@code parent3}, {@code child_dx}/{@code child_dy}). Positions are native
 * sprite coordinates; only the integer word is written where the ROM does a
 * {@code move.w} to {@code x_pos}/{@code y_pos}.
 */
final class S3kBlueSpheresShowcase {
    /** One Difficulty_Sprites allocation, for inspection. */
    record Spawn(int difficulty, String kind, S3kBlueSpheresObject object) { }

    private static final String[] KINDS = {
            "Sonic", "Tails", "Knuckles", "Eggman", "MechaSonic", "Ship",
            "SpawnChickens", "SpawnSquirrels", "SpawnRabbits", "MasterEmerald", "SKLogo", "SuperSonic" };

    /** make_art_tile(tile, palette, priority). */
    private static int artTile(int tile, int palette, int priority) {
        return (priority << 15) | (palette << 13) | tile;
    }

    private static final int DIFFICULTY = S3kBlueSpheresAssets.TILE_DIFFICULTY;
    /** {@code move.w #1,(Player_mode).w} in loc_4DC1A: Sonic alone. */
    private static final int PLAYER_MODE = 1;
    /**
     * {@code Graphics_flags} bit 7 (English system). The engine models an
     * overseas console, as the title screen and ending TM marks do, so the
     * SK logo's "&amp; Knuckles" trademark child survives loc_5FB1A's test.
     */
    private static final boolean OVERSEAS = true;

    private final S3kBlueSpheresAssets assets;
    private final S3kBlueSpheresSession session;
    private final S3kBlueSpheresPalette palette;
    private final List<Spawn> spawns = new ArrayList<>();
    private Pattern[] vram = S3kBlueSpheresAssets.emptyVram();
    private S3kBlueSpheresObject[] slots;
    private int firstDynamic;

    // Global RAM the objects share.
    private int superSonicKnuxFlag;
    /** {@code Super_palette_status}: 0 off, 1 fading, {@code $FF} done. */
    private int superPaletteStatus;
    private int paletteTimer;
    /** {@code Slotted_object_bits}, cleared by loc_4DC1A's {@code clearRAM _unkFA80,$80}. */
    private int slottedObjectBits;
    /** {@code Player_prev_frame}; see {@link #sonicLoadPlc}. */
    private int playerPrevFrame = -1;
    /**
     * {@code RNG_seed}: nothing on the Blue Spheres screens seeds it, so its
     * value is the console's history; the port starts from zero, which
     * Random_Number replaces with its {@code $2A6D365B} reset seed.
     */
    private int rngSeed;

    // ROM mappings and DPLCs, loaded on first use.
    private List<SpriteMappingFrame> mapSonic, mapSuperSonic, mapKnuckles, mapTails, mapEggman, mapShip,
            mapMecha, mapAnimals1, mapAnimals2, mapAnimals5, mapMasterEmerald, mapSkBanner, mapAndKnuckles;
    private List<SpriteDplcFrame> dplcSonic, dplcSuperSonic, dplcKnuckles;

    S3kBlueSpheresShowcase(S3kBlueSpheresAssets assets, S3kBlueSpheresSession session,
                           S3kBlueSpheresPalette palette) {
        this.assets = Objects.requireNonNull(assets);
        this.session = Objects.requireNonNull(session);
        this.palette = Objects.requireNonNull(palette);
    }

    /**
     * PLC_SphereResults and the five Kosinski archives loc_4DC1A
     * decompresses back to back and DMAs to ArtTile_BlueSphere_Difficulty.
     * Each {@code ArtKosM_*+2} is the first module's plain Kosinski stream.
     * The DMA lands after the PLC, so ArtKosM_EggRoboHead ({@code +$EF},
     * {@code $20} tiles) replaces the head tiles of ArtNem_RobotnikShip: the
     * ship's pilot is Egg Robo. ArtKosM_EndingMasterEmerald lands at
     * {@code +$6E} = ArtTile_SSZMasterEmerald, where Obj_Difficulty_MasterEmerald
     * looks for it.
     */
    void loadArt(Pattern[] vram) throws IOException {
        this.vram = vram;
        int base = S3kBlueSpheresAssets.TILE_DIFFICULTY;
        S3kBlueSpheresAssets.load(vram, base + 0xEF, assets.nemesis(S3kBlueSpheresAssets.ART_NEM_ROBOTNIK_SHIP));
        S3kBlueSpheresAssets.load(vram, base + 0x141, assets.nemesis(S3kBlueSpheresAssets.ART_NEM_CHICKEN));
        S3kBlueSpheresAssets.load(vram, base + 0x14F, assets.nemesis(S3kBlueSpheresAssets.ART_NEM_SQUIRREL));
        S3kBlueSpheresAssets.load(vram, base + 0x161, assets.nemesis(S3kBlueSpheresAssets.ART_NEM_RABBIT));
        S3kBlueSpheresAssets.load(vram, S3kBlueSpheresAssets.TILE_TAILS,
                assets.nemesis(S3kBlueSpheresAssets.ART_NEM_BLUE_SPHERE_TAILS));
        int[] archives = {
                S3kBlueSpheresAssets.ART_KOSM_SK_POSE_BANNER,
                S3kBlueSpheresAssets.ART_KOSM_AND_KNUCKLES,
                S3kBlueSpheresAssets.ART_KOSM_ENDING_MASTER_EMERALD,
                S3kBlueSpheresAssets.ART_KOSM_ROBOTNIK_SMUG,
                S3kBlueSpheresAssets.ART_KOSM_EGG_ROBO_HEAD };
        java.io.ByteArrayOutputStream joined = new java.io.ByteArrayOutputStream();
        for (int archive : archives) joined.write(assets.kosinski(archive + 2));
        S3kBlueSpheresAssets.load(vram, base, joined.toByteArray());
    }

    /**
     * {@code clr.b Super_Sonic_Knux_flag}/{@code Super_palette_status} before
     * the loop (sonic3k.asm:101780-101781). Palette_timer, Palette_frame and
     * RNG_seed are left as they were.
     */
    void resetSuper() {
        superSonicKnuxFlag = 0;
        superPaletteStatus = 0;
        slottedObjectBits = 0;
    }

    /**
     * Difficulty_Level_Sprites_Display (sonic3k.asm:102101-102110) for
     * {@code difficulty} 1-12: AllocateObject, then the Difficulty_Sprites
     * entry at {@code difficulty-1}. Outside 1-12 the ROM indexes past the
     * table into code; the screen never reaches that, so the port refuses it.
     */
    void spawn(S3kBlueSpheresObject[] slots, int firstDynamic, int difficulty) {
        this.slots = slots;
        this.firstDynamic = firstDynamic;
        if (difficulty < 1 || difficulty > 12) {
            throw new IllegalArgumentException("Difficulty_Sprites has no entry for difficulty " + difficulty);
        }
        S3kBlueSpheresObject object = allocateObject();
        if (object == null) return;
        object.routine = switch (difficulty) {
            case 1 -> new SonicPose()::run;
            case 2 -> this::tailsInit;
            case 3 -> new KnucklesPose()::run;
            case 4 -> this::eggmanInit;
            case 5 -> new MechaSonic()::run;
            case 6 -> this::shipInit;
            case 7 -> self -> spawnAnimals(self, Animal.CHICKEN);
            case 8 -> self -> spawnAnimals(self, Animal.SQUIRREL);
            case 9 -> self -> spawnAnimals(self, Animal.RABBIT);
            case 10 -> this::masterEmeraldInit;
            case 11 -> this::skLogoInit;
            default -> this::superSonicInit;
        };
        spawns.add(new Spawn(difficulty, KINDS[difficulty - 1], object));
    }

    List<Spawn> spawns() {
        return Collections.unmodifiableList(spawns);
    }

    S3kBlueSpheresObject[] slots() {
        return slots;
    }

    boolean superSonic() {
        return superSonicKnuxFlag != 0;
    }

    int superPaletteStatus() {
        return superPaletteStatus;
    }

    /**
     * SuperHyper_PalCycle (sonic3k.asm:4640-4774) as this screen reaches it:
     * Player_mode 1 and Water_flag clear, so Sonic's fade-in and Super cycle
     * write Normal_palette colours 2-4 and nothing underwater. The revert
     * arm needs a status above 1, which nothing here sets.
     */
    void cyclePalette() {
        byte status = (byte) superPaletteStatus;
        if (status == 0) return;
        if (status < 0) {
            // SuperHyper_PalCycle_Normal -> _SuperSonic (Super_Sonic_Knux_flag is 1, not Hyper).
            paletteTimer = (paletteTimer - 1) & 0xFF;
            if ((byte) paletteTimer >= 0) return;
            paletteTimer = 6;
            int frame = session.paletteFrame;
            session.paletteFrame = (session.paletteFrame + 6) & 0xFFFF;
            // FixBugs = 0: blo, so the last frame ($36) is skipped.
            if (session.paletteFrame >= 0x36) session.paletteFrame = 0x24;
            applySuperSonicPalette(frame);
            return;
        }
        if (status != 1) throw new IllegalStateException("SuperHyper_PalCycle_Revert is not reached here");
        paletteTimer = (paletteTimer - 1) & 0xFF;
        if ((byte) paletteTimer >= 0) return;
        paletteTimer = 1;
        if (PLAYER_MODE >= 2) throw new IllegalStateException("Player_mode is 1 on this screen");
        // SuperHyper_PalCycle_FadeIn.
        int frame = session.paletteFrame;
        session.paletteFrame = (session.paletteFrame + 6) & 0xFFFF;
        if (session.paletteFrame >= 0x24) superPaletteStatus = 0xFF;
        applySuperSonicPalette(frame);
    }

    /** SuperHyper_PalCycle_SonicApply: three words of PalCycle_SuperSonic to Normal_palette+4. */
    private void applySuperSonicPalette(int offset) {
        int table = S3kBlueSpheresAssets.PAL_CYCLE_SUPER_SONIC + offset;
        for (int i = 0; i < 3; i++) palette.setNormalWord(2 + i, assets.word(table + i * 2));
    }

    // ---- Object allocation ----

    /**
     * AllocateObject (sonic3k.asm:37951-37967): the loop advances before its
     * first test, so the scan starts one slot past Dynamic_object_RAM and
     * covers 90 slots.
     */
    private S3kBlueSpheresObject allocateObject() {
        int end = Math.min(slots.length, firstDynamic + 91);
        for (int i = firstDynamic + 1; i < end; i++) {
            if (slots[i].routine == null) return fresh(slots[i]);
        }
        return null;
    }

    /**
     * AllocateObjectAfterCurrent: the first free slot after {@code current}.
     * Its look-up table bounds the count (sonic3k.asm:37968-37979); with the
     * few objects this screen holds, the bound never decides the result.
     */
    private S3kBlueSpheresObject allocateAfter(S3kBlueSpheresObject current) {
        int end = Math.min(slots.length, firstDynamic + 90);
        for (int i = indexOf(current) + 1; i < end; i++) {
            if (slots[i].routine == null) return fresh(slots[i]);
        }
        return null;
    }

    private static S3kBlueSpheresObject fresh(S3kBlueSpheresObject slot) {
        slot.clear();
        return slot;
    }

    private int indexOf(S3kBlueSpheresObject object) {
        for (int i = 0; i < slots.length; i++) if (slots[i] == object) return i;
        throw new IllegalStateException("object is not in the slot array");
    }

    /** Delete_Current_Sprite. */
    private static void delete(S3kBlueSpheresObject self) {
        self.clear();
    }

    private static void setXWord(S3kBlueSpheresObject self, int pixels) {
        self.x = ((pixels & 0xFFFF) << 16) | (self.x & 0xFFFF);
    }

    private static void setYWord(S3kBlueSpheresObject self, int pixels) {
        self.y = ((pixels & 0xFFFF) << 16) | (self.y & 0xFFFF);
    }

    private static int xWord(S3kBlueSpheresObject self) {
        return (self.x >>> 16) & 0xFFFF;
    }

    private static int yWord(S3kBlueSpheresObject self) {
        return (self.y >>> 16) & 0xFFFF;
    }

    // ---- Obj_Difficulty_Sonic (sonic3k.asm:102144-102176) ----

    private final class SonicPose {
        private boolean initialised;
        /** 0 = loc_4E202, 1 = loc_4E222, 2 = loc_4E23A. */
        private int entry;
        /** {@code $30(a0)}: the wait after the Super palette fade. */
        private int wait;

        void run(S3kBlueSpheresObject self) {
            if (!initialised) {
                initialised = true;
                self.mappings = mapSonic();
                self.artTile = artTile(S3kBlueSpheresAssets.TILE_PLAYER_1, 0, 1);
                self.setX(0x120);
                self.setY(0x120);
                self.xFlip = true;
                self.priority = 0x80;
                self.anim = 0x13;
            }
            if (entry == 0 && superSonicKnuxFlag != 0) {
                // loc_4E202: Super Sonic's mappings and transformation.
                self.mappings = mapSuperSonic();
                self.anim = 0x1F;
                entry = 1;
                wait = 0x14;
            }
            if (entry <= 1 && (byte) superPaletteStatus < 0) {
                // loc_4E222: once the palette fade is done, wait $14 more frames.
                wait = (short) (wait - 1);
                if (wait < 0) {
                    self.anim = 5;
                    entry = 2;
                }
            }
            animateSonic(self);
            sonicLoadPlc(self);
            self.draw();
        }
    }

    /** Obj_Difficulty_SuperSonic (sonic3k.asm:102178-102183). */
    private void superSonicInit(S3kBlueSpheresObject self) {
        superPaletteStatus = 1;
        paletteTimer = 0x0F;
        superSonicKnuxFlag = 1;
        delete(self);
    }

    /**
     * Animate_Sonic (sonic3k.asm:24773-24843) for this object. The plain
     * arm copies {@code status} bit 0 (clear here) over the render x-flip,
     * so the init's {@code bset #0,render_flags} never shows. AniSonic1F
     * ends {@code $FD,0}, so Super Sonic walks (anim 0) for a few frames
     * before loc_4E222 selects anim 5: loc_126A4's walk arm runs with this
     * object's cleared angle, flip, status and ground_vel.
     */
    private void animateSonic(S3kBlueSpheresObject self) {
        int table = superSonicKnuxFlag != 0 ? S3kBlueSpheresAssets.ANI_SUPER_SONIC : S3kBlueSpheresAssets.ANI_SONIC;
        resetOnNewAnim(self);
        int script = table + assets.signedWord(table + self.anim * 2);
        int delay = assets.u8(script);
        if ((delay & 0x80) != 0) {
            walkSonic(self, delay);
            return;
        }
        animatePlain(self, script, delay);
    }

    /**
     * loc_126A4 (sonic3k.asm:24844-24967) with angle 0, no flip or tumble,
     * status 0 and ground_vel 0: render flips clear, the slow walk script
     * (AniSuperSonic00 below $800, AniSonic00 below $600) and a delay of
     * {@code ($800 - 0) >> 8 = 8}.
     */
    private void walkSonic(S3kBlueSpheresObject self, int delay) {
        if (delay != 0xFF) throw new IllegalStateException("only the walk arm is reachable, not " + delay);
        self.xFlip = false;
        int groundSpeed = 0;
        int base = superSonicKnuxFlag != 0 ? S3kBlueSpheresAssets.ANI_SUPER_SONIC : S3kBlueSpheresAssets.ANI_SONIC;
        int walk = base + assets.signedWord(base);
        int frame = assets.u8(walk + 1 + self.animFrame);
        if (frame == 0xFF) {
            self.animFrame = 0;
            frame = assets.u8(walk + 1);
        }
        self.frame = frame;
        self.animTimer = (self.animTimer - 1) & 0xFF;
        if ((self.animTimer & 0x80) != 0) {
            int timer = 0x800 - groundSpeed;
            if (timer < 0) timer = 0;
            self.animTimer = (timer >> 8) & 0xFF;
            self.animFrame = (self.animFrame + 1) & 0xFF;
        }
    }

    private static void resetOnNewAnim(S3kBlueSpheresObject self) {
        if (self.anim != self.prevAnim) {
            self.prevAnim = self.anim;
            self.animFrame = 0;
            self.animTimer = 0;
        }
    }

    /**
     * The delay-script arm shared by Animate_Sonic (loc_12634, sonic3k.asm:24789-24842)
     * and Animate_Knuckles (loc_17D58, sonic3k.asm:33080-33134): x-flip from status bit
     * 0, {@code $FF} restart, {@code $FE n} back n, {@code $FD n} new anim.
     */
    private void animatePlain(S3kBlueSpheresObject self, int script, int delay) {
        self.xFlip = false;
        self.animTimer = (self.animTimer - 1) & 0xFF;
        if ((self.animTimer & 0x80) == 0) return;
        self.animTimer = delay;
        int index = self.animFrame;
        int frame = assets.u8(script + 1 + index);
        if (frame >= 0xFC) {
            if (frame == 0xFF) {
                self.animFrame = 0;
                frame = assets.u8(script + 1);
            } else if (frame == 0xFE) {
                int back = assets.u8(script + 2 + index);
                self.animFrame = (self.animFrame - back) & 0xFF;
                frame = assets.u8(script + 1 + ((index - back) & 0xFF));
            } else {
                if (frame == 0xFD) self.anim = assets.u8(script + 2 + index);
                return;
            }
        }
        self.frame = frame;
        self.animFrame = (self.animFrame + 1) & 0xFF;
    }

    /**
     * Sonic_Load_PLC (sonic3k.asm:25251-25298): PLC_SuperSonic while Super,
     * ArtUnc_Sonic_Extra from frame {@code $DA}, into ArtTile_Player_1 when
     * the frame differs from {@code Player_prev_frame}. The ROM's
     * Player_prev_frame still holds the results pose's last frame
     * ({@code $B1}-{@code $B3}, AniSonic13's loop, or Knuckles' {@code $B2}-{@code $B4});
     * this pose opens on {@code $B0}, so starting from "none" loads the same.
     */
    private void sonicLoadPlc(S3kBlueSpheresObject self) {
        int frame = self.frame & 0xFF;
        if (frame == playerPrevFrame) return;
        playerPrevFrame = frame;
        List<SpriteDplcFrame> dplc = superSonicKnuxFlag != 0 ? dplcSuperSonic() : dplcSonic();
        int art = frame >= 0xDA ? S3kBlueSpheresAssets.ART_UNC_SONIC_EXTRA : S3kBlueSpheresAssets.ART_UNC_SONIC;
        playerDplc(dplc, frame, art, S3kBlueSpheresAssets.TILE_PLAYER_1);
    }

    private void playerDplc(List<SpriteDplcFrame> dplc, int frame, int art, int tile) {
        if (frame >= dplc.size()) return;
        for (TileLoadRequest request : dplc.get(frame).requests()) {
            S3kBlueSpheresAssets.load(vram, tile, assets.rom().slice(art + request.startTile() * Pattern.PATTERN_SIZE_IN_ROM,
                    request.count() * Pattern.PATTERN_SIZE_IN_ROM));
            tile += request.count();
        }
    }

    // ---- Obj_Difficulty_Tails (sonic3k.asm:102185-102197) ----

    private void tailsInit(S3kBlueSpheresObject self) {
        self.mappings = mapTails();
        self.artTile = artTile(S3kBlueSpheresAssets.TILE_TAILS, 1, 1);
        self.setX(0xFC);
        self.setY(0x11B);
        self.xFlip = true;
        self.priority = 0x80;
        self.routine = S3kBlueSpheresObject::draw;
        self.draw();
    }

    // ---- Obj_Difficulty_Knuckles (sonic3k.asm:102203-102225) ----

    private final class KnucklesPose {
        private boolean initialised;
        /** {@code $30(a0)}: the last frame copied, zero in the fresh slot. */
        private int loaded;

        void run(S3kBlueSpheresObject self) {
            if (!initialised) {
                initialised = true;
                self.mappings = mapKnuckles();
                self.artTile = artTile(S3kBlueSpheresAssets.TILE_PLAYER_2, 1, 1);
                self.setX(0x138);
                self.setY(0x118);
                self.priority = 0x80;
                self.anim = 0x24;
            }
            // loc_4E2F0: Animate_Knuckles has no Super table.
            resetOnNewAnim(self);
            int table = S3kBlueSpheresAssets.ANI_KNUCKLES;
            int script = table + assets.signedWord(table + self.anim * 2);
            int delay = assets.u8(script);
            if ((delay & 0x80) != 0) throw new IllegalStateException("AniKnuckles24 is a plain script");
            animatePlain(self, script, delay);
            int frame = self.frame & 0xFF;
            if (frame != loaded) {
                loaded = frame;
                // loc_18122 (sonic3k.asm:33279-33303) into ArtTile_Player_2.
                playerDplc(dplcKnuckles(), frame, S3kBlueSpheresAssets.ART_UNC_KNUCKLES,
                        S3kBlueSpheresAssets.TILE_PLAYER_2);
            }
            self.draw();
        }
    }

    // ---- Obj_Difficulty_Eggman (sonic3k.asm:126589-126606) ----

    private void eggmanInit(S3kBlueSpheresObject self) {
        self.mappings = mapEggman();
        self.artTile = artTile(DIFFICULTY + 0xB2, 1, 0);
        self.xFlip = true;
        self.priority = 0x280;
        self.setX(0x168);
        self.setY(0x108);
        self.routine = s -> {
            // loc_5FB90
            animateRawNoSst(s, S3kBlueSpheresAssets.ANI_RAW_EGGMAN);
            s.draw();
        };
    }

    /**
     * Animate_RawNoSST (sonic3k.asm:177432-177475). Only the {@code $FC}
     * restart command occurs in the scripts these objects use.
     */
    private void animateRawNoSst(S3kBlueSpheresObject self, int script) {
        self.animTimer = (self.animTimer - 1) & 0xFF;
        if ((self.animTimer & 0x80) == 0) return;
        int index = (self.animFrame + 1) & 0xFF;
        self.animFrame = index;
        int frame = assets.u8(script + 1 + index);
        if ((frame & 0x80) == 0) {
            self.animTimer = assets.u8(script);
            self.frame = frame;
            return;
        }
        if (frame != 0xFC) throw new IllegalStateException("Animate_RawNoSST command " + frame + " is not used here");
        // AnimateRaw_Restart, then clr.b anim_frame.
        self.frame = assets.u8(script + 1);
        self.animTimer = assets.u8(script);
        self.animFrame = 0;
    }

    // ---- Obj_Difficulty_MechaSonic (sonic3k.asm:166705-166729) ----

    private final class MechaSonic {
        private int routine;
        /** {@code $3A(a0)}: Perform_DPLC's last frame. */
        private int loaded;

        void run(S3kBlueSpheresObject self) {
            if (routine == 0) {
                // Difficulty_MechaSonic_Init: SetUp_ObjAttributesSlotted with ObjSlot_MechaSonic.
                if (!setUpSlotted(self)) return;
                routine = 2;
                self.artTile = artTile(S3kBlueSpheresAssets.TILE_MECHA_SONIC, 2, 0);
                self.xFlip = true;
                self.setX(0xD8);
                self.setY(0x104);
            }
            performDplc(self);
            self.draw();
        }

        /**
         * SetUp_ObjAttributesSlotted (sonic3k.asm:178941-178989) reading
         * ObjSlot_MechaSonic. With no free slot bit the ROM wipes the object
         * and returns past its caller.
         */
        private boolean setUpSlotted(S3kBlueSpheresObject self) {
            int table = S3kBlueSpheresAssets.OBJSLOT_MECHA_SONIC;
            int max = assets.word(table);
            int tile = assets.word(table + 2);
            int step = assets.word(table + 4);
            int bit = 0;
            int bits = slottedObjectBits;
            while ((bits & 1) != 0) {
                bits >>>= 1;
                bit++;
                tile += step;
                if (bit > max) {
                    delete(self);
                    return false;
                }
            }
            slottedObjectBits |= 1 << bit;
            self.artTile = tile & 0xFFFF;
            self.mappings = mapMecha();
            self.priority = assets.word(table + 12);
            self.frame = assets.u8(table + 16);
            loaded = 0xFF;
            return true;
        }

        /**
         * Perform_DPLC (sonic3k.asm:178993-179028) with DPLCPtr_MechaSonic:
         * entries are {@code count-1}, then words of source tile in bits 4-15
         * and {@code tiles-1} in bits 0-3.
         */
        private void performDplc(S3kBlueSpheresObject self) {
            int frame = self.frame & 0xFF;
            if (frame == loaded) return;
            loaded = frame;
            int script = S3kBlueSpheresAssets.DPLC_MECHA_SONIC;
            script += assets.signedWord(script + frame * 2);
            int entries = assets.word(script);
            int tile = self.artTile & 0x7FF;
            for (int i = 0; i <= entries; i++) {
                int word = assets.word(script + 2 + i * 2);
                int count = (word & 0xF) + 1;
                S3kBlueSpheresAssets.load(vram, tile, assets.rom().slice(
                        S3kBlueSpheresAssets.ART_UNC_MECHA_SONIC + (word & 0xFFF0) * 2,
                        count * Pattern.PATTERN_SIZE_IN_ROM));
                tile += count;
            }
        }
    }

    // ---- Obj_Difficulty_Ship / _ShipHead (sonic3k.asm:136675-136708) ----

    /** Swing_Setup1 / sub_6001E state: {@code $3E} limit, {@code $40} step, {@code $38} bit 0. */
    private static final class Swing {
        int limit;
        int step;
        boolean down;

        Swing(S3kBlueSpheresObject self, int speed, int step) {
            this.limit = speed;
            this.step = step;
            self.yVel = speed;
        }

        /** Swing_UpAndDown (sonic3k.asm:177947-177980). */
        void upAndDown(S3kBlueSpheresObject self) {
            int d0 = step;
            int d1 = self.yVel;
            int d2 = limit;
            if (!down) {
                d0 = (short) -d0;
                d1 = (short) (d1 + d0);
                d2 = (short) -d2;
                if (d1 > d2) {
                    self.yVel = d1;
                    return;
                }
                down = true;
                d0 = (short) -d0;
                d2 = (short) -d2;
            }
            d1 = (short) (d1 + d0);
            if (d1 >= d2) {
                down = false;
                d0 = (short) -d0;
                d1 = (short) (d1 + d0);
            }
            self.yVel = d1;
        }
    }

    private void shipInit(S3kBlueSpheresObject self) {
        self.mappings = mapShip();
        self.artTile = artTile(DIFFICULTY + 0xEF, 1, 0);
        self.priority = 0x280;
        self.frame = 0x0A;
        self.setX(0x188);
        self.setY(0xC0);
        Swing swing = new Swing(self, 0xC0, 0x10);
        self.routine = s -> {
            // loc_680EC
            swing.upAndDown(s);
            s.moveSprite2();
            s.draw();
        };
        // CreateChild1_Normal with Child1_DifficultyShipHead.
        int table = S3kBlueSpheresAssets.CHILD1_DIFFICULTY_SHIP_HEAD;
        int count = assets.word(table) + 1;
        for (int i = 0; i < count; i++) {
            S3kBlueSpheresObject head = allocateAfter(self);
            if (head == null) return;
            int dx = (byte) assets.u8(table + 6 + i * 6);
            int dy = (byte) assets.u8(table + 7 + i * 6);
            head.mappings = self.mappings;
            head.artTile = self.artTile;
            setXWord(head, xWord(self) + dx);
            setYWord(head, yWord(self) + dy);
            head.routine = h -> shipHeadInit(h, self, dx, dy);
        }
    }

    private void shipHeadInit(S3kBlueSpheresObject self, S3kBlueSpheresObject parent, int dx, int dy) {
        self.priority = 0x280;
        self.routine = s -> {
            // loc_68118: Refresh_ChildPositionAdjusted, then the head's two frames.
            refreshChildPositionAdjusted(s, parent, dx, dy);
            animateRawNoSst(s, S3kBlueSpheresAssets.ANI_RAW_EGG_ROBO_HEAD);
            s.draw();
        };
    }

    /** Refresh_ChildPositionAdjusted (sonic3k.asm:177391-177422); no parent here is y-flipped. */
    private static void refreshChildPositionAdjusted(S3kBlueSpheresObject self, S3kBlueSpheresObject parent,
                                                     int dx, int dy) {
        self.xFlip = parent.xFlip;
        setXWord(self, xWord(parent) + (parent.xFlip ? -dx : dx));
        setYWord(self, yWord(parent) + dy);
    }

    // ---- Obj_Difficulty_SpawnChickens/Squirrels/Rabbits (sonic3k.asm:61437-61582) ----

    private enum Animal {
        CHICKEN(S3kBlueSpheresAssets.CHICKEN_POSITIONS, 0x141, false),
        SQUIRREL(S3kBlueSpheresAssets.SQUIRREL_POSITIONS, 0x14F, true),
        RABBIT(S3kBlueSpheresAssets.RABBIT_POSITIONS, 0x161, false);

        final int positions;
        final int tile;
        final boolean flipped;

        Animal(int positions, int tile, boolean flipped) {
            this.positions = positions;
            this.tile = tile;
            this.flipped = flipped;
        }
    }

    /**
     * CreateChild6_Simple with a {@code 4-1} count: four children after the
     * spawner, copying its (cleared) mappings, art and position, subtypes
     * 0, 2, 4, 6; then the spawner deletes itself.
     */
    private void spawnAnimals(S3kBlueSpheresObject self, Animal animal) {
        for (int subtype = 0; subtype < 8; subtype += 2) {
            S3kBlueSpheresObject child = allocateAfter(self);
            if (child == null) break;
            child.mappings = self.mappings;
            child.artTile = self.artTile;
            child.x = self.x & 0xFFFF0000;
            child.y = self.y & 0xFFFF0000;
            int childSubtype = subtype;
            child.routine = c -> animalInit(c, animal, childSubtype);
        }
        delete(self);
    }

    private void animalInit(S3kBlueSpheresObject self, Animal animal, int subtype) {
        self.mappings = switch (animal) {
            case CHICKEN -> mapAnimals1();
            case SQUIRREL -> mapAnimals2();
            case RABBIT -> mapAnimals5();
        };
        self.artTile = artTile(DIFFICULTY + animal.tile, 1, 0);
        if (animal.flipped) self.xFlip = true;
        // sub_2CE76: (x, y) by subtype, $2E = subtype*4, priority $200.
        int entry = animal.positions + subtype * 2;
        setXWord(self, assets.word(entry));
        setYWord(self, assets.word(entry + 2));
        int[] delay = {subtype * 4};
        self.priority = 0x200;
        if (animal == Animal.CHICKEN) {
            // loc_2CD3C, then sub_6001E's swing set-up.
            Swing swing = new Swing(self, 0x80, 8);
            self.routine = s -> {
                // loc_2CD6E: flap in place until $2E runs out, then loc_2CD84 also swings.
                delay[0] = (short) (delay[0] - 1);
                if (delay[0] < 0) {
                    s.routine = c -> {
                        flap(c);
                        swing.upAndDown(c);
                        c.moveSprite2();
                        c.draw();
                    };
                }
                flap(s);
                s.draw();
            };
        } else {
            // loc_2CDB0 / loc_2CE42: $3A keeps the ground y.
            int ground = yWord(self);
            self.routine = s -> hop(s, ground);
        }
    }

    /** sub_2CEA2: toggle frames 0/1 every third frame. */
    private static void flap(S3kBlueSpheresObject self) {
        self.animTimer = (self.animTimer - 1) & 0xFF;
        if ((self.animTimer & 0x80) == 0) return;
        self.animTimer = 2;
        self.frame = (self.frame + 1) & 1;
    }

    /** loc_2CDEA: fall with gravity, and at the ground hop again at a random speed. */
    private void hop(S3kBlueSpheresObject self, int ground) {
        moveSprite(self);
        self.frame = 1;
        if ((short) self.yVel >= 0) {
            self.frame = 0;
            if (yWord(self) >= ground) {
                setYWord(self, ground);
                int random = randomNumber();
                self.yVel = (short) -((random & 0x1FF) + 0x100);
            }
        }
        self.draw();
    }

    /** MoveSprite (sonic3k.asm:36072-36082): the old y speed moves, then {@code $38} gravity. */
    private static void moveSprite(S3kBlueSpheresObject self) {
        int yVel = (short) self.yVel;
        self.yVel = (short) (yVel + 0x38);
        self.x += ((short) self.xVel) << 8;
        self.y += yVel << 8;
    }

    /** Random_Number (sonic3k.asm:3024-3042); returns d0's low word. */
    private int randomNumber() {
        int d1 = rngSeed;
        if ((d1 & 0xFFFF) == 0) d1 = 0x2A6D365B;
        int d0 = d1;
        d1 <<= 2;
        d1 += d0;
        d1 <<= 3;
        d1 += d0;
        int low = d1 & 0xFFFF;
        int sum = (low + (d1 >>> 16)) & 0xFFFF;
        rngSeed = (sum << 16) | low;
        return sum;
    }

    // ---- Obj_Difficulty_MasterEmerald (sonic3k.asm:166732-166761) ----

    private void masterEmeraldInit(S3kBlueSpheresObject self) {
        self.mappings = mapMasterEmerald();
        self.artTile = artTile(S3kBlueSpheresAssets.TILE_SSZ_MASTER_EMERALD, 3, 0);
        self.priority = 0x280;
        self.frame = 2;
        self.setX(0x120);
        // Super_emerald_count: the Blue Spheres extra never collects Super
        // Emeralds and nothing in it writes the counter, so it keeps its
        // cold-boot zero and loc_7D042's palette script is skipped.
        setYWord(self, assets.word(S3kBlueSpheresAssets.MASTER_EMERALD_Y));
        // cmpi.b #7,(Super_emerald_count).w: never 7 here, so loc_7D050 (Draw_Sprite only).
        self.routine = S3kBlueSpheresObject::draw;
    }

    // ---- Obj_Difficulty_SKLogo (sonic3k.asm:126553-126587) ----

    private void skLogoInit(S3kBlueSpheresObject self) {
        self.mappings = mapSkBanner();
        self.artTile = artTile(DIFFICULTY, 3, 0);
        self.priority = 0x280;
        self.routine = S3kBlueSpheresObject::draw;
        self.setX(0x120);
        self.setY(0xA0);
        S3kBlueSpheresObject tm = allocateObject();
        if (tm == null) return;
        tm.routine = t -> trademarkInit(t, self);
    }

    /**
     * loc_5FB1A: deleted on Japanese consoles; otherwise Map_ANDKnuckles
     * frame 2 (the TM) placed once by loc_5FFD4 at byte_5F520[subtype 0],
     * then Child_Draw_Sprite (the logo is never deleted here).
     */
    private void trademarkInit(S3kBlueSpheresObject self, S3kBlueSpheresObject parent) {
        if (!OVERSEAS) {
            delete(self);
            return;
        }
        self.mappings = mapAndKnuckles();
        self.artTile = artTile(DIFFICULTY + 0x48, 3, 0);
        self.priority = 0x280;
        self.frame = 2;
        self.routine = S3kBlueSpheresObject::draw;
        int dx = (byte) assets.u8(S3kBlueSpheresAssets.TM_OFFSETS);
        int dy = (byte) assets.u8(S3kBlueSpheresAssets.TM_OFFSETS + 1);
        setXWord(self, xWord(parent) + dx);
        setYWord(self, yWord(parent) + dy);
    }

    // ---- ROM data ----

    private List<SpriteMappingFrame> mapSonic() {
        if (mapSonic == null) mapSonic = assets.mappings(S3kBlueSpheresAssets.MAP_SONIC);
        return mapSonic;
    }

    private List<SpriteMappingFrame> mapSuperSonic() {
        if (mapSuperSonic == null) mapSuperSonic = assets.mappings(S3kBlueSpheresAssets.MAP_SUPER_SONIC);
        return mapSuperSonic;
    }

    private List<SpriteMappingFrame> mapKnuckles() {
        if (mapKnuckles == null) mapKnuckles = assets.mappings(S3kBlueSpheresAssets.MAP_KNUCKLES);
        return mapKnuckles;
    }

    private List<SpriteMappingFrame> mapTails() {
        if (mapTails == null) mapTails = assets.mappings(S3kBlueSpheresAssets.MAP_BS_TAILS_POSE);
        return mapTails;
    }

    private List<SpriteMappingFrame> mapEggman() {
        if (mapEggman == null) mapEggman = assets.mappings(S3kBlueSpheresAssets.MAP_ENDING_EYECATCH_EGGMAN);
        return mapEggman;
    }

    private List<SpriteMappingFrame> mapShip() {
        if (mapShip == null) mapShip = assets.mappings(S3kBlueSpheresAssets.MAP_ROBOTNIK_SHIP);
        return mapShip;
    }

    private List<SpriteMappingFrame> mapMecha() {
        if (mapMecha == null) mapMecha = assets.mappings(S3kBlueSpheresAssets.MAP_MECHA_SONIC);
        return mapMecha;
    }

    private List<SpriteMappingFrame> mapAnimals1() {
        if (mapAnimals1 == null) mapAnimals1 = assets.mappings(S3kBlueSpheresAssets.MAP_ANIMALS_1);
        return mapAnimals1;
    }

    private List<SpriteMappingFrame> mapAnimals2() {
        if (mapAnimals2 == null) mapAnimals2 = assets.mappings(S3kBlueSpheresAssets.MAP_ANIMALS_2);
        return mapAnimals2;
    }

    private List<SpriteMappingFrame> mapAnimals5() {
        if (mapAnimals5 == null) mapAnimals5 = assets.mappings(S3kBlueSpheresAssets.MAP_ANIMALS_5);
        return mapAnimals5;
    }

    private List<SpriteMappingFrame> mapMasterEmerald() {
        if (mapMasterEmerald == null) mapMasterEmerald = assets.mappings(S3kBlueSpheresAssets.MAP_SSZ_MASTER_EMERALD);
        return mapMasterEmerald;
    }

    private List<SpriteMappingFrame> mapSkBanner() {
        if (mapSkBanner == null) mapSkBanner = assets.mappings(S3kBlueSpheresAssets.MAP_SK_POSE_BANNER);
        return mapSkBanner;
    }

    private List<SpriteMappingFrame> mapAndKnuckles() {
        if (mapAndKnuckles == null) mapAndKnuckles = assets.mappings(S3kBlueSpheresAssets.MAP_AND_KNUCKLES);
        return mapAndKnuckles;
    }

    /** Map_Sonic's own offset table has 251 entries; the tables after it are not frames. */
    private static final int SONIC_FRAMES = 251;

    private List<SpriteDplcFrame> dplcSonic() {
        if (dplcSonic == null) {
            dplcSonic = S3kSpriteDataLoader.loadDplcFrames(assets.rom(), S3kBlueSpheresAssets.DPLC_SONIC, SONIC_FRAMES);
        }
        return dplcSonic;
    }

    private List<SpriteDplcFrame> dplcSuperSonic() {
        if (dplcSuperSonic == null) {
            dplcSuperSonic = S3kSpriteDataLoader.loadDplcFrames(assets.rom(), S3kBlueSpheresAssets.DPLC_SUPER_SONIC,
                    SONIC_FRAMES);
        }
        return dplcSuperSonic;
    }

    private List<SpriteDplcFrame> dplcKnuckles() {
        if (dplcKnuckles == null) {
            dplcKnuckles = S3kSpriteDataLoader.loadDplcFrames(assets.rom(), S3kBlueSpheresAssets.DPLC_KNUCKLES);
        }
        return dplcKnuckles;
    }
}
