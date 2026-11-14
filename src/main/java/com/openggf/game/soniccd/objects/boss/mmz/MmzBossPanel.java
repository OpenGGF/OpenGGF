package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * One of the four rotating panels, object $40 ({@code BossPanelObject}, objects/r8/boss.asm:2279-3001), allocated by
 * the Egg Mobile with its slot index ({@code var_38}, 0-3) and angle frame (0, 4, 8, $C; sub_20CF28). A panel sits at
 * the Egg Mobile plus the offset of its frame in the current table set ({@code off_20CC38}), the second pair scaled by
 * the Egg Mobile's radius, and takes that row's collision type ($B4-$BF, hurt). Its 16 frames draw four mappings with
 * the flips by quarter.
 *
 * <ul>
 *   <li>Routine 2 follows while Eggman's intro runs; routine 4 follows, turns one frame on the Egg Mobile's rotate
 *       bits ($BA on the first panel's frames), and reports the first panel's frame.</li>
 *   <li>Routine 6 (Eggman's panel drop): up to Y $E0, wait for all, then each in turn ((slot + 1) * $1E frames) drops
 *       at a column from {@code word_20C9D6} (the Egg Mobile's pattern) or at the player, or (one hit left) flies at
 *       the player from a corner; it lands on $1B8-$1D0 ($B4), lies $1E frames, flickers 60 and waits in routine 8,
 *       hidden, until the Egg Mobile's routine $C brings it back.</li>
 *   <li>Routine $A: the panel a hit picked falls off and is deleted below Y $1E0.</li>
 * </ul>
 * Each hit remaps the slot indexes of the panels left ({@code off_20C810}).
 */
final class MmzBossPanel extends MmzBossPiece {
    static final int ID = 0x40;
    /** var_2c bit 3: this panel is the one the hit drops. */
    static final int DETACHED = 0x08;
    /** sub_20CBCA: move.w #$BA,d0; jmp PlayFmSound (scddisasm/src/objects/r8/boss.asm:2899-2900). */
    static final int FM_TURN = 0xBA;

    MmzBossEggMobile eggMobile;
    /** var_38: the panel's slot index. */
    int slotIndex;
    /** The mapping table in {@code obj.sprite_data}: BossPanelSprites1-6. */
    int panelSet = 1;
    /** var_30 / var_32: the offset from the Egg Mobile; var_34 / x_speed: the 16.16 throw steps. */
    private int offX;
    private int offY;
    private long stepX;
    private long stepY;
    private boolean hidden;
    private int flip;

    MmzBossPanel(int x, int y, MmzBossEggMobile eggMobile, int slotIndex, int frame) {
        super(ID, x, y, "MmzBossPanel");
        this.eggMobile = eggMobile;
        this.slotIndex = slotIndex;
        this.mapFrame = frame;
    }

    private MmzBossPanel(ObjectSpawn spawn) {
        super(spawn, "MmzBossPanel");
    }

    @Override String sheet() {
        // After PLC 5 (Amy's art at the third hit) set 1's tiles are Amy's.
        return MmzBossArt.PANEL + panelSet + (panelSet == 1 && amyArt() ? "a" : "");
    }

    private boolean amyArt() {
        return eggMobile != null && eggMobile.eggman != null && eggMobile.eggman.hitsLeft <= 1;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sonic = player instanceof AbstractPlayableSprite s ? s : null;
        if (eggMobile != null && (byte) eggMobile.panelSelect >= 0) remap();
        hidden = dispatch(sonic);
        if (hidden || isDestroyed()) return;
        // BossPanelObject (:2292-2320): frames 0-3 plain, 4-7 Y-flipped, 8-$B both, $C-$F X-flipped.
        if (mapFrame < 4) flip = 0;
        else if (mapFrame < 8) flip = 2;
        else if (mapFrame < 0xC) flip = 3;
        else flip = 1;
    }

    /** The routine; true when it ended in {@code addq.l #4,sp} (no flips, no DrawObject). */
    private boolean dispatch(AbstractPlayableSprite player) {
        return switch (routine) {
            case 0 -> { init(); yield false; }
            case 2 -> { followIntro(); yield false; }
            case 4 -> attached();
            case 6 -> drop(player);
            case 8 -> waiting();
            case 0xA -> falling();
            default -> false;
        };
    }

    @Override protected boolean drawn() { return !hidden && routine != 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || !drawn()) return;
        String key = sheetKey();
        if (key != null) drawFrame(key, mapFrame, (flip & 1) != 0, (flip & 2) != 0);
    }

    /** sub_20C7C0 (:2426-2458): the hit's panel detaches; the others take their new slot index. */
    private void remap() {
        if (eggMobile.panelSelect == slotIndex) {
            set(DETACHED);
            return;
        }
        MmzBossEggman eggman = eggMobile.eggman;
        int hits = eggman == null ? 0 : eggman.hitsLeft;
        if (hits == 1) {
            slotIndex = 0;
            return;
        }
        CdStageImage image = image();
        int base = table("panelRemap");
        if (image == null || base < 0) return;
        int list = base + (short) image.u16(base + hits * 2);
        int row = list + (short) image.u16(list + eggMobile.panelSelect * 2);
        slotIndex = image.u8(row + slotIndex);
    }

    /** BossPanelObject_0_Routine0 (:2337-2348), calling routine 4 once. */
    private void init() {
        routine = 2;
        priority = 5;
        width = 0x20;
        yRadius = 0x20;
        tileWord = MmzBossArt.PANEL_TILE;
        panelSet = 1;
        attachedStep();
        setCollision(0, 0);
    }

    /** BossPanelObject_0_Routine2 (:2352-2364). */
    private void followIntro() {
        place();
        MmzBossEggman eggman = eggMobile == null ? null : eggMobile.eggman;
        if (eggman != null && eggman.routine() != 4) {
            routine = 4;
            return;
        }
        setCollision(0, 0);
    }

    /** BossPanelObject_0_Routine4 (:2368-2422); a detached panel re-dispatches into routine $A the same frame. */
    private boolean attached() {
        if (has(DETACHED)) {
            step = 0;
            routine = 0xA;
            return falling();
        }
        attachedStep();
        return false;
    }

    private void attachedStep() {
        if (eggMobile == null) return;
        if (eggMobile.routine() == 0xA && mapFrame == 0) {
            step = 0;
            routine = 6;
            eggMobile.panelsUp = (eggMobile.panelsUp + 1) & 0xFF;
            return;
        }
        panelSet = eggMobile.setIndex + 1;                        // BossPanelSprites entry var_34 (Sprites1-6)
        if (eggMobile.has(MmzBossEggMobile.ROTATE_DOWN)) turn(-1);
        else if (eggMobile.has(MmzBossEggMobile.ROTATE_UP)) turn(1);
        if (slotIndex == 0) eggMobile.panelFrame = mapFrame;
        place();
    }

    /** sub_20CBA2 / sub_20CBB4 / sub_20CBCA (:2869-2905). */
    private void turn(int delta) {
        mapFrame = (mapFrame + delta) & 0xF;
        if (slotIndex == 0 && (mapFrame & 3) != 2) fmSound(FM_TURN);
    }

    /** loc_20C79E (scddisasm/src/objects/r8/boss.asm:2414-2422) and sub_20CBEA (scddisasm/src/objects/r8/boss.asm:2909-2937). */
    private void place() {
        if (eggMobile == null) return;
        CdStageImage image = image();
        int base = table("panelOffsets");
        if (image != null && base >= 0) {
            int row = base + (short) image.u16(base + eggMobile.setIndex * 2) + mapFrame * 0xA;
            int d1 = (short) image.u16(row), d2 = (short) image.u16(row + 2);
            int d3 = (short) image.u16(row + 4), d4 = (short) image.u16(row + 6);
            int d5 = image.u16(row + 8);
            int scale = (short) eggMobile.radius;
            d3 = ((short) (d3 * scale)) >> 8;                     // muls.w d0,d3; asr.w #8,d3
            d4 = ((short) (d4 * scale)) >> 8;
            offX = (d1 + d3) & 0xFFFF;
            offY = (d2 + d4) & 0xFFFF;
            setCollision(d5 & 0xFF, 2);
        }
        x = (eggMobile.ox() + offX) & 0xFFFF;
        y = (eggMobile.oy() + offY) & 0xFFFF;
    }

    /** BossPanelObject_0_Routine6 (:2523-2761). */
    private boolean drop(AbstractPlayableSprite player) {
        MmzBossEggman eggman = eggMobile == null ? null : eggMobile.eggman;
        int hits = eggman == null ? 0 : eggman.hitsLeft;
        switch (step) {
            case 0 -> {
                step = 1;
                if (hits == 1) panelSet = 2;
                rise();
            }
            case 1 -> rise();
            case 2 -> {
                if (eggMobile != null && (byte) hits <= (byte) eggMobile.panelsDown) {
                    setTimerHigh(0);
                    step = 3;
                }
            }
            case 3 -> aim(player, hits);
            case 4 -> fall();
            case 5 -> {
                setTimerHigh(timerHigh() + 1);
                if (timerHigh() == 0x1E) {
                    priority = 5;
                    step = 6;
                    setCollision(0, 0);
                    setTimerHigh(0);
                }
            }
            case 6 -> {
                return lie(hits);
            }
            default -> { }
        }
        return false;
    }

    /** loc_20C892 / loc_20C8A4 (:2541-2552). */
    private void rise() {
        addYLong(-0x10000);
        if (yWord() > 0xE0) return;
        step = (step + 1) & 0xFF;
        if (eggMobile != null) eggMobile.panelsDown = (eggMobile.panelsDown + 1) & 0xFF;
    }

    /** loc_20C8C4 / loc_20C8DC (:2571-2648). */
    private void aim(AbstractPlayableSprite player, int hits) {
        setTimerHigh(timerHigh() + 1);
        if ((((slotIndex + 1) * 0x1E) & 0xFF) != timerHigh()) return;
        priority = 3;
        step = (step + 1) & 0xFF;
        setTimerHigh(0);
        panelSet = 6;
        mapFrame = 0;
        if ((byte) hits >= 3) {
            dropAtColumn();
            return;
        }
        panelSet = 2;
        if (hits == 2) {
            if (slotIndex == 0) {
                dropAtColumn();
                return;
            }
            x = player == null ? x : player.getCentreX() & 0xFFFF;   // loc_20C98C
            stepX = 0;
            stepY = 0x38000;
            return;
        }
        int px = player == null ? 0xF00 : (short) player.getCentreX();
        if (px >= 0xF00) {
            // loc_20C954: from the left corner.
            mapFrame = 0xD;
            y = (throwHeight(px - 0xE08) + 0x1E0) & 0xFFFF;
            x = 0xE38;
            stepX = 0x33BB9;
        } else {
            mapFrame = 3;
            y = (throwHeight(0xFF8 - px) + 0x1E0) & 0xFFFF;
            x = 0xFC8;
            stepX = -0x33BB9 & 0xFFFFFFFFL;
        }
        stepY = 0x156CF;
    }

    /** {@code muls.w #$95F6,d0; swap d0}: the product's high word. */
    private static int throwHeight(int distance) {
        return (short) (((short) distance * (short) 0x95F6) >> 16);
    }

    /** loc_20C9AA (:2637-2648): the column {@code word_20C9D6[pattern + slot]}. */
    private void dropAtColumn() {
        CdStageImage image = image();
        int base = table("panelDropXs");
        if (image != null && base >= 0 && eggMobile != null) {
            x = image.u16(base + eggMobile.dropPattern + slotIndex * 2);
        }
        stepX = 0;
        stepY = 0x38000;
    }

    /** loc_20C9F6 / loc_20CA2E (:2672-2703). */
    private void fall() {
        addXLong(stepX);
        addYLong(stepY);
        int floor = mapFrame != 0 ? 0x1D0 : panelSet == 6 ? 0x1C0 : 0x1B8;
        if (floor > yWord()) return;
        y = floor;
        step = (step + 1) & 0xFF;
        setTimerHigh(0);
        fmSound(MmzBossEggman.FM_LAND);
    }

    /** loc_20CA6C / loc_20CA86 (:2725-2761): 60 frames flickering, then hidden in routine 8 with its settle frame. */
    private boolean lie(int hits) {
        setTimerHigh(timerHigh() + 1);
        if (timerHigh() != 60) return (timerHigh() & 1) != 0;
        if (eggMobile != null) eggMobile.panelsUp = (eggMobile.panelsUp + 1) & 0xFF;
        setTimerHigh(0);
        step = 0;
        routine = 8;
        CdStageImage image = image();
        int base = table("panelSettleFrames");
        if (image != null && base >= 0 && eggMobile != null) {
            int list = base + (short) image.u16(base + hits * 2);
            if (slotIndex == 0) eggMobile.panelFrame = image.u8(list);
            mapFrame = image.u8(list + slotIndex);
            eggMobile.setIndex = 0;
        }
        return true;
    }

    /** BossPanelObject_0_Routine8 (:2794-2803): hidden until the Egg Mobile's routine $C; a detached one is deleted. */
    private boolean waiting() {
        if (has(DETACHED)) {
            deleteObject();
            return true;
        }
        if (eggMobile != null && eggMobile.has(MmzBossEggMobile.REATTACH)) routine = 4;
        return true;
    }

    /** BossPanelObject_0_RoutineA (:2807-2865): thrown up and out, spinning, deleted below Y $1E0; drawn every other frame. */
    private boolean falling() {
        if (step == 0) {
            setCollision(0, 0);
            stepX = 0x10000;
            stepY = -0x30000 & 0xFFFFFFFFL;
            if ((byte) mapFrame > 8) stepX = -0x10000 & 0xFFFFFFFFL;
            timer = 0;
            step = 1;
            panelSet = panelSet == 4 ? 5 : 3;
        }
        addXLong(stepX);
        addYLong(stepY);
        stepY = (stepY + 0x3000) & 0xFFFFFFFFL;
        if (yWord() >= 0x1E0) {
            deleteObject();
            return true;
        }
        setTimerHigh(timerHigh() + 1);
        if (timerHigh() == 4) {
            setTimerHigh(0);
            mapFrame = (mapFrame + 1) & 0xFF;
            if ((byte) mapFrame > 0xF) mapFrame = 0;
        }
        setTimerLow(timerLow() + 1);
        return (timerLow() & 1) != 0;
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new MmzBossPanel(spawn);
    }
}
