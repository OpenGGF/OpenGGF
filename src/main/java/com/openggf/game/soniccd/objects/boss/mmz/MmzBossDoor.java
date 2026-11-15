package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * The boss door, object $3D ({@code BossDoorObject}, objects/r8/boss_door.asm:3-99), placed at ($F00, $180) on
 * R83C/R83D. Its init makes itself the left half (subtype 1, X-flipped, X - 8) and allocates the right half (X + 8,
 * {@code FindObjSlot}; with no free slot it despawns). Both wait for {@code boss_started} bit 7 (Eggman's intro,
 * boss.asm:344), clear {@code boss_door_flag} and slide apart 2 pixels a frame; at every 16-pixel column they
 * {@code PlaceBlock} block 0 over 12 rows from Y - $60, and despawn after 17 columns.
 *
 * <p>Not hosted: {@code PlaceBlock} (Level/Block Draw.asm:286) rewrites the stage's 16-pixel block entries in the
 * chunk RAM, a terrain write the engine reaches through {@code LevelMutationSurface}; the columns are counted and the
 * halves move, but the map is not changed (the boss lane reports it).
 */
final class MmzBossDoor extends MmzBossPiece {
    static final int ID = 0x3D;

    private int originX;
    private boolean left;
    private int columns;

    MmzBossDoor(ObjectSpawn spawn) {
        super(spawn, "MmzBossDoor");
    }

    private MmzBossDoor(int originX, int y) {
        super(ID, originX, y, "MmzBossDoor");
        this.originX = originX;
    }

    @Override String sheet() { return MmzBossArt.BOSS_DOOR; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> waitForBoss();
            case 4 -> slide();
            default -> { }
        }
    }

    /** BossDoorObject_0_Routine0 / sub_20B728 (scddisasm/src/objects/r8/boss_door.asm:20-49). */
    private void init() {
        originX = x;
        left = true;
        setup();
        MmzBossDoor right = new MmzBossDoor(originX, y);
        if (!spawn(right)) {
            deleteObject();                                       // jmp DespawnObject
            return;
        }
        right.setup();
    }

    /** sub_20B728: ID $3D, X = origin -/+ 8, routine + 2, priority 2, tile $8797. */
    private void setup() {
        x = (originX + (left ? -8 : 8)) & 0xFFFF;
        routine = (routine + 2) & 0xFF;
        priority = 2;
        tileWord = MmzBossArt.BOSS_DOOR_TILE;
        width = 8;
        yRadius = 0x60;
    }

    /** BossDoorObject_0_Routine2 (scddisasm/src/objects/r8/boss_door.asm:53-61). */
    private void waitForBoss() {
        if ((bossStarted() & 0x80) == 0) return;
        var signals = signals();
        if (signals != null) signals.setBossDoorFlag(false);
        routine = 4;
    }

    /** BossDoorObject_0_Routine4 (scddisasm/src/objects/r8/boss_door.asm:65-96). */
    private void slide() {
        int column = (x - 8) & 0xFFFF;
        if ((column & 0xF) == 0) {
            columns = (columns + 1) & 0xFF;
            if (columns >= 0x11) {
                deleteObject();                                   // jmp DespawnObject
                return;
            }
            // PlaceBlock of block 0 at (column, y - $60 + 16 * row), rows 0-11: not hosted (class comment).
        }
        x = (x + (left ? -2 : 2)) & 0xFFFF;
    }

    /** jmp CheckObjectDespawn after DrawObject (boss_door.asm:3-9). */
    @Override boolean checksDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || routine == 0) return;
        String key = sheetKey();
        if (key != null) drawFrame(key, mapFrame, left, false);
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new MmzBossDoor(spawn);
    }
}
