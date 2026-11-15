package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.soniccd.zones.wwz.WwzPlayerProfile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Electric beams, object $21 ({@code ObjElectricBeams}, Level/Wacky Workbench/Objects/Electric Beams/Main.asm:
 * 16-320): the placed beams (subtype 0) are drawn frames; the object with a non-zero subtype is the
 * zone's beam timer, the handler, which Init allocates at the player when none exists
 * ({@code ObjElectricBeams_SpawnHandler}, :148-172). The handler follows the player, waits 360 frames in the
 * past, 480 in the present and 240 in the future (not at all in a good future, nor in act 1 below Y $400),
 * charges one of three beam colours for 90 frames (blinking its palette entry, the charge sound),
 * and then flashes it for 120 frames, setting {@code wwzBeamMode} to the colour plus one for the player's
 * beam check; the colour then advances (blue, pink, yellow). Palette entries are written as the routines do
 * ({@code palette+$64}..., :176-203, :214-264, :307-320): a beam entry per colour, and one background entry.
 */
public final class ObjElectricBeams extends WwzObject implements ObjectVisualFacing.NonDirectional {
    private static final int MAIN = 2, FLASH = 4;
    private static final String PALETTE_OWNER = "scd.wwz.beams";
    private static final int PALETTE_PRIORITY = 110;
    /** {@code ObjElectricBeams_ModeColors}: 0, 2, 4 (:206-210) added to {@code palette+$64}, which is line 3 colour 2. */
    private static final int BEAM_COLOR_LINE = 3, BEAM_COLOR_BASE = 2;
    /** {@code palette+$40} (line 2 colour 0) in the past, {@code palette+$7A} (line 3 colour 13) otherwise. */
    private static final int PAST_BACKGROUND_LINE = 2, PAST_BACKGROUND_COLOR = 0;
    private static final int BACKGROUND_LINE = 3, BACKGROUND_COLOR = 13;
    /** {@code move.w #$80,d0} (:312): the charge blink colour. */
    private static final int CHARGE_COLOR = 0x80;
    /** {@code move.w #360 / #480 / #240,d0} (:71-77). */
    private static final int[] PERIOD = {360, 480, 240};

    private final boolean handler;
    private int beamTime;
    private int chargeTime;
    private int charge;
    private int mode;
    private int flash;
    private int backgroundFlash;
    private boolean initialised;

    public ObjElectricBeams(ObjectSpawn spawn) {
        super(spawn, "ElectricBeams");
        this.handler = spawn.subtype() != 0;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        AbstractPlayableSprite sprite = sprite(player);
        switch (routine) {
            case 0 -> {
                init(sprite);
                main(sprite);
            }
            case MAIN -> main(sprite);
            case FLASH -> flash(sprite);
            default -> { }
        }
    }

    /** ObjElectricBeams_Init (:34-43). */
    private void init(AbstractPlayableSprite player) {
        routine = MAIN;
        priority = 4;
        tileWord = WwzObjectArt.BEAMS_TILE;
        yRadius = 16;
        width = 16;
        mapFrame = getSpawn().subtype();
        initialised = true;
        spawnHandler(player);
    }

    /** ObjElectricBeams_SpawnHandler (:148-172). */
    private void spawnHandler(AbstractPlayableSprite player) {
        var objects = services().objectManager();
        if (objects == null || player == null) return;
        for (ObjElectricBeams beams : objects.activeObjectsOfType(ObjElectricBeams.class)) {
            if (beams.handler) return;                                 // cmpi.b #$21,oID / tst.b oSubtype / bne.s .End
        }
        objects.addDynamicObject(new ObjElectricBeams(
                new ObjectSpawn(player.getCentreX() & 0xFFFF, player.getCentreY() & 0xFFFF, 0x21, 1, 0, false, 0)));
    }

    /** ObjElectricBeams_Main (:47-97). */
    private void main(AbstractPlayableSprite player) {
        if (!handler) return;                                          // tst.b oSubtype / beq.s .End
        stickToPlayer(player);
        if (beamTime == 0) {
            restorePalette();
            int zone = timeZone();
            if (zone == 2 && goodFuture()) return;                     // cmpi.b #2,timeZone / tst.b goodFuture
            if (act() == 0) {                                          // tst.b act
                var camera = services().camera();
                if (camera != null && (camera.getY() & 0xFFFF) >= 0x400) return;   // cmpi.w #$400,cameraY
            }
            beamTime = PERIOD[Math.min(zone, 2)];
            return;
        }
        beamTime = (beamTime - 1) & 0xFFFF;
        if (beamTime != 0) return;
        routine = FLASH;
        beamTime = 120;
        chargeTime = 90;
        charge = 0;
        if (renderFlagOnScreen()) services().playSfx(WwzSounds.B2);
        flash(player);
    }

    /** ObjElectricBeams_Flash (:101-136). */
    private void flash(AbstractPlayableSprite player) {
        stickToPlayer(player);
        if (chargeTime != 0) {
            chargePalette();
            chargeTime = (chargeTime - 1) & 0xFFFF;
            if (chargeTime != 0) return;
            restorePalette();                                          // .ChargedUp
        }
        if (player != null) WwzPlayerProfile.setBeamMode(player, mode + 1);   // move.b d0,wwzBeamMode
        flashPalette();
        beamTime = (beamTime - 1) & 0xFFFF;
        if (beamTime != 0) return;
        routine = MAIN;
        if (player != null) WwzPlayerProfile.setBeamMode(player, 0);   // clr.b wwzBeamMode
        mode++;
        if (mode >= 3) {
            mode = 0;
            flash = 0;
        }
    }

    /** ObjElectricBeams_StickToPlayer (:140-144). */
    private void stickToPlayer(AbstractPlayableSprite player) {
        if (player == null) return;
        x = player.getCentreX() & 0xFFFF;
        y = player.getCentreY() & 0xFFFF;
    }

    /** ObjElectricBeams_RestorePal (:176-203). */
    private void restorePalette() {
        int zone = timeZone();
        int beam;
        int background;
        if (zone == 0) {
            beam = 0x626;
            background = 0x646;
        } else if (zone == 1) {
            beam = 0x222;
            background = 0x680;
        } else {
            if (goodFuture()) return;                                  // tst.b goodFuture / beq.s / rts
            beam = 0x402;
            background = 0x246;
        }
        for (int i = 0; i < 3; i++) writeColor(BEAM_COLOR_LINE, BEAM_COLOR_BASE + i, beam);
        writeBackground(background);
    }

    /** ObjElectricBeams_ChargePal (:307-320): the beam entry blinks between $80 and 0. */
    private void chargePalette() {
        writeColor(BEAM_COLOR_LINE, BEAM_COLOR_BASE + mode, charge == 0 ? CHARGE_COLOR : 0);
        charge ^= 1;
    }

    /** ObjElectricBeams_FlashPal (:214-264): both tables end in -1 and wrap. */
    private void flashPalette() {
        var provider = artProvider();
        if (provider == null) return;
        var image = provider.image();
        int zone = Math.min(timeZone(), 2);
        String[] beamColumns = {"beamColorsPast", "beamColorsPresent", "beamColorsFuture"};
        String[] backgroundColumns = {"beamBackgroundPast", "beamBackgroundPresent", "beamBackgroundFuture"};
        int beams = WwzObjectCatalogue.offset(provider.payload(), beamColumns[zone]);
        int backgrounds = WwzObjectCatalogue.offset(provider.payload(), backgroundColumns[zone]);
        if (beams < 0 || backgrounds < 0) return;
        writeColor(BEAM_COLOR_LINE, BEAM_COLOR_BASE + mode, image.u16(beams + flash * 2));
        flash++;
        if (image.u16(beams + flash * 2) == 0xFFFF) flash = 0;
        int word = image.u16(backgrounds + backgroundFlash * 2);
        if (zone == 0) writeColor(PAST_BACKGROUND_LINE, PAST_BACKGROUND_COLOR, word);
        else writeColor(BACKGROUND_LINE, BACKGROUND_COLOR, word);
        backgroundFlash++;
        if (image.u16(backgrounds + backgroundFlash * 2) == 0xFFFF) backgroundFlash = 0;
    }

    private void writeBackground(int word) {
        if (timeZone() == 0) writeColor(PAST_BACKGROUND_LINE, PAST_BACKGROUND_COLOR, word);
        else writeColor(BACKGROUND_LINE, BACKGROUND_COLOR, word);
    }

    /**
     * {@code move.w d0,(a2)+}: the palette in RAM is written at once, not queued with the zone's palette cycle (which
     * resolves its own claims at its own point of the frame), so the colours are in place for this frame and a queued
     * write cannot outlive the stage.
     */
    private void writeColor(int line, int index, int segaWord) {
        var levels = services().levelManager();
        PaletteWriteSupport.applyColor(null, levels == null ? null : levels.getCurrentLevel(), GameServices.graphics(),
                PALETTE_OWNER, PALETTE_PRIORITY, line, index, segaWord);
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    protected String sheetKey() {
        return initialised ? WwzObjectArt.key(WwzObjectArt.BEAMS) : null;
    }

    public int beamMode() { return mode; }
    public boolean isHandler() { return handler; }
}
