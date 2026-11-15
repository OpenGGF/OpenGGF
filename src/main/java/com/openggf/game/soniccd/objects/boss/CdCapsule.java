package com.openggf.game.soniccd.objects.boss;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.common.CdFlower;
import com.openggf.game.soniccd.objects.common.CdLevelFlags;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The flower capsule every act 3 links ({@code ObjCapsule}, Level/_Objects/Level End/Main.asm:105-380), placed
 * by the layout after the boss: priority 4, tile $2481, width $20, Y radius $18. A rolling player within
 * ({@code oXRadius} + 32, {@code oYRadius} + 28) of it breaks it: the HUD timer stops, the player rebounds at a
 * quarter of its speed, and for 120 frames an explosion appears every fourth frame at one of eight offsets. Then
 * the level palette is reloaded, seven seeds fly out (Y speed -$600, the seed speed table for X) and, 60 frames
 * later, {@code StartResults} runs as for the signpost: the results music, the forced Right, and the results
 * object $3A, whose last line hands the act's end to the program flow. A seed falls under gravity and becomes
 * flower $1F (subtype 1) in its slot on landing.
 *
 * <p>The capsule draws in routines 2 and 4 (the seeds in routine $A) and not once results start.
 */
public final class CdCapsule extends CdBossPart {
    public static final String SHEET = "cd.boss.capsule";
    /** ObjCapsule_Init: move.w #$2481,oTile(a0) (Level End/Main.asm:164). */
    public static final int TILE = 0x2481;
    /** ObjCapsule_Seed: move.b #$1F,oID(a0) (Level End/Main.asm:331). */
    private static final int FLOWER_ID = 0x1F;
    /** ObjCapsule_SpawnSeeds: moveq #7-1,d6 (Level End/Main.asm:279). */
    private static final int SEEDS = 7;

    /** The player whose pad {@code ctrlLocked} replaces with {@code playerCtrlHold} while results run. */
    private final com.openggf.level.objects.PlayerControlOwners heldPlayers = new com.openggf.level.objects.PlayerControlOwners();

    /** oLvlEndTimer (oVar2A). */
    private int timer;
    private int xVel;
    private int yVel;

    public CdCapsule(ObjectSpawn spawn) {
        super(spawn, "FlowerCapsule");
    }

    private CdCapsule(int x, int y, int xVel) {
        super(new ObjectSpawn(x, y, 0x15, 0, 0, false, 0), "FlowerCapsuleSeed");
        this.routine = 0xA;
        this.xVel = xVel;
        this.yVel = -0x600;
        this.anim = 1;
        this.prevAnim = -1;
        this.priority = 0;
        this.tileWord = TILE;
    }

    /** The capsule's sheet: its mappings over VRAM after the boss stage's PLC 4 ({@code vram}). */
    public static void registerArt(CdObjectArtProvider art, CdStageVram vram) {
        int table = CdBossCatalogue.offset(art.payload(), "capsuleMappings");
        if (table >= 0) art.register(SHEET, art.sheet(art.image().reader(), table, TILE, vram));
    }

    public int capsuleRoutine() { return routine; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                // ObjCapsule_Init (Level End/Main.asm:159-167), falling into ObjCapsule_Main.
                routine = 2;
                priority = 4;
                tileWord = TILE;
                width = 0x20;
                yRadius = 0x18;
                main(player);
            }
            case 2 -> main(player);
            case 4 -> explode();
            case 6 -> startResultsWhenDue();
            case 0xA -> seed();
            default -> { }                                       // ResultsActive: rts (Level End/Main.asm:905-907)
        }
        if (routine == 8) holdPlayer(player);
    }

    /**
     * {@code bset #0,ctrlLocked; move.w #$808,playerCtrlHold}: while {@code ctrlLocked} is set ObjSonic takes
     * {@code playerCtrlHold} instead of the pad (Level/_Objects/Sonic/Main.asm:297-299), so the act ends running
     * right whatever is pressed. The shared player has no reader for the flag, so the capsule, which set it, holds
     * the player's input the way the other games' capsules do (forced input under a control lock).
     */
    private void holdPlayer(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sonic)) return;
        CdLevelFlags flags = flags();
        if (!flags.ctrlLocked) {
            releasePlayersForVisualDirection();
            return;
        }
        int hold = (flags.playerCtrlHold >> 8) & 0xFF;
        int mask = hold & 0x0F;                                   // up, down, left, right
        if ((hold & 0x70) != 0) mask |= AbstractPlayableSprite.INPUT_JUMP;   // A, B or C
        AbstractPlayableSprite held = heldPlayers.acquire(sonic);
        held.setControlLocked(true);
        held.setForcedInputMask(mask);
    }

    @Override
    public void releasePlayersForVisualDirection() {
        for (var player : heldPlayers.players()) {
            player.setControlLocked(false);
            player.clearForcedInputMask();
            player.clearLogicalInputState();
        }
        heldPlayers.clear();
    }

    /** ObjCapsule (Level End/Main.asm:105-122): drawn after routine 0 when a seed or before routine 6. */
    @Override protected boolean drawn() { return routine != 0 && (routine == 0xA || routine < 6); }

    @Override protected String sheetKey() { return artProvider() == null ? null : SHEET; }

    private int animations() {
        var level = cdLevel();
        return level == null ? -1 : CdBossCatalogue.offset(level.payload(), "capsuleAnimations");
    }

    /** ObjCapsule_Main (Level End/Main.asm:173-210). */
    private void main(PlayableEntity player) {
        animate(animations());
        if (!(player instanceof AbstractPlayableSprite sonic) || !hit(sonic)) return;
        var state = services().levelGamestate();
        if (state != null) state.pauseTimer();                  // clr.b updateHUDTime
        mapFrame = 2;
        timer = 120;
        routine = 4;
        int reach = sonic.getXRadius() + 32;
        int dx = (short) (sonic.getCentreX() - x + reach);
        if (dx < 0 || dx >= reach * 2) {
            sonic.setXSpeed((short) (-sonic.getXSpeed() >> 2));        // .BounceX
        } else {
            sonic.setYSpeed((short) (-sonic.getYSpeed() >> 2));        // .BounceY
        }
    }

    /** ObjCapsule_CheckHit (Level End/Main.asm:347-380): the player in a ball, inside the reach. */
    private boolean hit(AbstractPlayableSprite sonic) {
        if (!sonic.getRolling()) return false;
        int rx = sonic.getXRadius() + 32;
        int dx = ((sonic.getCentreX() - x + rx) & 0xFFFF);
        if ((dx & 0x8000) != 0 || dx >= rx * 2) return false;
        int ry = sonic.getYRadius() + 28;
        int dy = ((sonic.getCentreY() - y + ry) & 0xFFFF);
        return (dy & 0x8000) == 0 && dy < ry * 2;
    }

    /** ObjCapsule_Explode (Level End/Main.asm:217-253). */
    private void explode() {
        timer = (timer - 1) & 0xFF;
        if ((timer & 0x80) != 0) {
            spawnSeeds();
            routine = 6;
            timer = 60;
            return;
        }
        if ((timer & 3) != 0) return;
        CdStageImage image = stageImage();
        var level = cdLevel();
        if (image == null || level == null) return;
        int at = CdBossCatalogue.offset(level.payload(), "capsuleExplosionOffsets") + ((timer >> 2) & 7) * 2;
        explode(x + (byte) image.u8(at), y + (byte) image.u8(at + 1));
    }

    /** ObjCapsule_SpawnSeeds (Level End/Main.asm:272-301): the level palette again, then seven seeds. */
    private void spawnSeeds() {
        CdStageImage image = stageImage();
        var level = cdLevel();
        if (image == null || level == null) return;
        var entry = CdStageCatalogue.entry(level.payload());
        loadPalette(image.u8(entry.levelDataIndex() + 15));     // move.b LevelPaletteID,d0
        int speeds = CdBossCatalogue.offset(level.payload(), "capsuleSeedSpeeds");
        for (int i = 0; i < SEEDS; i++) {
            if (!spawn(new CdCapsule(x, y, (short) image.u16(speeds + i * 2)))) return;
        }
    }

    /** StartResults (Level End/Main.asm:816-866) through the shared tail ({@link #startResults()}). */
    private void startResultsWhenDue() {
        timer = (timer - 1) & 0xFF;
        if (timer != 0) return;
        startResults();
        timer = 180;
        routine = 8;
    }

    /** ObjCapsule_Seed (Level End/Main.asm:322-335): ObjMoveGrv, ObjGetFloorDist, then flower $1F subtype 1. */
    private void seed() {
        animate(animations());
        long px = xLong() + ((long) (short) xVel << 8);
        long py = yLong() + ((long) (short) yVel << 8);
        yVel = (short) (yVel + 0x38);                           // ObjMoveGrv .CheckGravity (Object Functions.asm:42-80)
        if (yVel >= 0x1000) yVel = 0x1000;                      // .NoGravity: the fall-speed cap
        setXLong(px & 0xFFFFFFFFL);
        setYLong(py & 0xFFFFFFFFL);
        int floor = com.openggf.physics.ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
        if (floor >= 0) return;
        var objects = services().objectManager();
        int slot = ObjectLifetimeOps.detachSlotForTransfer(this);
        setDestroyed(true);
        if (objects != null) {
            ObjectLifetimeOps.addReplacementAtTransferredSlot(objects,
                    new CdFlower(new ObjectSpawn(x, y, FLOWER_ID, 1, 0, false, 0)), slot);
        }
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new CdCapsule(spawn);
    }
}
