package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.game.soniccd.objects.mmz.MmzDoor;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The boss intro, object $39 with subtype $FE ({@code HotaruObject} -> loc_20F67E, objects/r8/hotaru.asm:3-17,
 * 404-578), placed at ($C30, $160) on R83C/R83D. Never drawn and never unloaded. From player X $BD0 it sets
 * {@code boss_door_flag} and closes the arena behind the player with a door ($29, subtype 0) at ($CA8, $1D0); it raises
 * {@code camYCenter} 6 a frame until {@code boss_flags} becomes 8 at $CC; the lights go down ({@code byte_20F874});
 * then two Hotaru fireflies in turn ($39, at ($BE0, $190), then ($BE0, $188) and ($C80, $188) together); when both are
 * gone the lights come back (good future, {@code byte_20F884}; the bad future stays dark), the door opens and Eggman
 * ($3F) appears at ($F00, $13C).
 *
 * <p>The light tables write palette line 3: the good future colours $31-$32 and $3C-$3F from 6-word rows, the bad
 * future colours $34-$3F from 12-word rows ({@code sub_20F828}, scddisasm/src/objects/r8/hotaru.asm:589-599).
 */
final class MmzBossHotaru extends MmzBossPiece implements MmzDoor.Opener {
    static final int ID = 0x39;
    /** HotaruObject: cmpi.b #$FD / #$FE / #$FF,obj.subtype(a0) (:4-9). */
    static final int TRAIL = 0xFD, INTRO = 0xFE, SHOT = 0xFF;

    private MmzDoor door;
    private MmzBossFirefly first;
    private MmzBossFirefly second;
    private int fadeIndex;
    private int fadeDelay;

    MmzBossHotaru(ObjectSpawn spawn) {
        super(spawn, "MmzBossHotaru");
    }

    @Override String sheet() { return MmzBossArt.HOTARU; }

    @Override protected boolean drawn() { return false; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init(player);
            case 2 -> raiseCamera(player);
            case 4 -> lightsDown();
            case 6 -> waitForFirst();
            case 8 -> spawnSecondPair();
            case 0xA -> waitForBoth();
            case 0xC -> lightsUp();
            default -> { }
        }
    }

    /** HotaruObject_0_Routine0 (scddisasm/src/objects/r8/hotaru.asm:429-445). */
    private void init(PlayableEntity player) {
        priority = 1;
        tileWord = MmzBossArt.HOTARU_TILE;
        if (player == null || (player.getCentreX() & 0xFFFF) < 0xBD0) return;   // bcs: unsigned
        var signals = signals();
        if (signals != null) signals.setBossDoorFlag(true);
        MmzDoor newDoor = new MmzDoor(spawnAt(MmzDoor.ID, 0xCA8, 0x1D0, 0));
        if (!spawn(newDoor)) return;
        door = newDoor;
        newDoor.setOpener(this);
        routine = 2;
    }

    /** HotaruObject_0_Routine2 (hotaru.asm:450-466). */
    private void raiseCamera(PlayableEntity player) {
        if (player == null || (player.getCentreX() & 0xFFFF) < 0xBD0) return;
        Camera camera = services().camera();
        var signals = signals();
        if (camera == null) return;
        if (camera.getYPosBias() >= 0xCC && signals != null) signals.setBossFlags(8);
        camera.setYPosBias((short) (camera.getYPosBias() + 6));
        if (signals == null || signals.bossFlags() != 8) return;
        routine = 4;
        fadeIndex = 0;
        fadeDelay = 0;
    }

    /** HotaruObject_0_Routine4 (hotaru.asm:471-486): the lights go down, then the first firefly. */
    private void lightsDown() {
        fade("hotaruFadeIn");
        if (fadeIndex != 0x10) return;
        fadeIndex = 0;
        fadeDelay = 0;
        MmzBossFirefly firefly = new MmzBossFirefly(spawnAt(ID, 0xBE0, 0x190, 0));
        if (!spawn(firefly)) return;
        first = firefly;
        firefly.owner = this;
        fadeIndex = 0x3C;
        routine = 6;
    }

    /** HotaruObject_0_Routine6 (hotaru.asm:491-513): dark while it lives, $3C frames more, then the second pair. */
    private void waitForFirst() {
        if (alive(first)) return;
        writeRow(0);
        fadeIndex = (fadeIndex - 1) & 0xFF;
        if (fadeIndex != 0) return;
        MmzBossFirefly firefly = new MmzBossFirefly(spawnAt(ID, 0xBE0, 0x188, 1));
        if (!spawn(firefly)) return;
        first = firefly;
        firefly.owner = this;
        routine = 8;
        spawnSecondPair();
    }

    /** HotaruObject_0_Routine8 (hotaru.asm:515-526). */
    private void spawnSecondPair() {
        MmzBossFirefly firefly = new MmzBossFirefly(spawnAt(ID, 0xC80, 0x188, 0));
        if (!spawn(firefly)) return;
        second = firefly;
        firefly.owner = this;
        fadeIndex = 0;
        fadeDelay = 0;
        routine = 0xA;
    }

    /** HotaruObject_0_RoutineA (hotaru.asm:531-545). */
    private void waitForBoth() {
        if (alive(first)) return;
        if (!alive(second)) routine = 0xC;
    }

    /** {@code cmpi.b #$39,obj.id(a1)}: the linked slot still holds a Hotaru. */
    private static boolean alive(MmzBossFirefly firefly) {
        return firefly != null && !firefly.isDestroyed();
    }

    /** HotaruObject_0_RoutineC (hotaru.asm:550-578). */
    private void lightsUp() {
        if (goodFuture()) {
            fade("hotaruFadeOut");
            if (fadeIndex != 0x10) return;
        } else {
            writeRow(0);
        }
        if (door != null) door.requestOpen();               // st $3F(a1)
        MmzBossEggman eggman = new MmzBossEggman(0xF00, 0x13C);
        if (!spawn(eggman)) return;
        deleteObject();
    }

    /** A firefly left the object manager: its slot no longer holds a Hotaru. */
    void fireflyRemoved(MmzBossFirefly firefly) {
        if (first == firefly) first = null;
        if (second == firefly) second = null;
    }

    @Override
    public void doorRemoved(MmzDoor removed) {
        if (door == removed) door = null;
    }

    @Override
    protected void onRemovedFromObjectManager() {
        if (door != null) door.clearOpener();
        if (first != null) first.owner = null;
        if (second != null) second.owner = null;
        door = null;
        first = null;
        second = null;
    }

    /** {@code STAGE_GOOD_FUTURE}: R83C. */
    private boolean goodFuture() {
        var payload = payload();
        return payload != null && payload.stage().period() == com.openggf.game.soniccd.CdPeriod.GOOD_FUTURE;
    }

    /** sub_20F83C (scddisasm/src/objects/r8/hotaru.asm:603-624): the next {row, delay} pair every {@code delay + 1} frames, to index $10. */
    private void fade(String column) {
        if (fadeIndex == 0x10) return;
        if (fadeDelay != 0) {
            fadeDelay = (fadeDelay - 1) & 0xFF;
            return;
        }
        CdStageImage image = image();
        int base = table(column);
        if (image == null || base < 0) return;
        int row = image.u8(base + fadeIndex);
        fadeDelay = image.u8(base + fadeIndex + 1);
        writeRow(row);
        fadeIndex = (fadeIndex + 2) & 0xFF;
    }

    /** sub_20F81E / sub_20F828 (scddisasm/src/objects/r8/hotaru.asm:583-599): the light row at byte offset {@code row} of {@code word_20F894}. */
    void writeRow(int row) {
        writeLightRow(this, goodFuture(), row);
    }

    static void writeLightRow(MmzBossPiece writer, boolean goodFuture, int row) {
        CdStageImage image = writer.image();
        int base = writer.table("hotaruFadePalettes");
        if (image == null || base < 0) return;
        int at = base + row;
        if (goodFuture) {
            writer.writePaletteWords(0x31, new int[] {image.u16(at), image.u16(at + 2)});
            writer.writePaletteWords(0x3C, new int[] {image.u16(at + 4), image.u16(at + 6), image.u16(at + 8),
                    image.u16(at + 10)});
        } else {
            int[] words = new int[12];
            for (int i = 0; i < 12; i++) words[i] = image.u16(at + i * 2);
            writer.writePaletteWords(0x34, words);
        }
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new MmzBossHotaru(spawn);
    }
}
