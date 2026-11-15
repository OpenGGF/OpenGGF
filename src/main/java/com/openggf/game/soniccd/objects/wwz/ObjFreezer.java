package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.game.soniccd.zones.wwz.WwzPlayerProfile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.NativePositionOps;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Freezer, object $05 ({@code ObjFreezer}, Level/Wacky Workbench/Objects/Freezer/Main.asm:14-346): the
 * placed machine waits 120 frames and then allocates its nozzle (routine 4, 36 pixels below), which animates;
 * while the nozzle is on animation 1 a player who is not invincible, warping, hurt, dead, controlled or
 * giving up, and who is inside the box (radius + 16 by radius + 32), is frozen: control bits 0 and 6 set
 * (captured and hidden) and an ice block allocated on the player. The block falls ($38 a frame of gravity) with
 * the player's Y following it, lands (the break sound), and for 15 frames a jump press frees the player; when the
 * time runs out the player is hurt instead. Either way the block shatters into six pieces, which each
 * shatter into four more. The nozzle's {@code $FC} ends in routine 6, which re-arms the machine and deletes it.
 * Act 3's index leaves $05 to {@code NullObject}. All objects share ID 5 and one routine byte.
 */
public final class ObjFreezer extends WwzObject implements ObjectVisualFacing.NonDirectional {
    private static final int MAIN = 2, NOZZLE = 4, RESET = 6, ICE_BLOCK = 8, ICE_LANDED = 0xA, ICE_PIECE = 0xC;
    /** {@code move.b #120,oFreezerTime(a0)} (:39, :78). */
    private static final int REARM = 120;
    /** {@code cmpi.b #$2B,oAnim(a1)}: SonAni_GiveUp (:296). */
    private static final int GIVE_UP_ANIMATION = 0x2B;
    private static final int ICE_GRAVITY = 0x38;
    private static final int JUMP_SPEED = -0x680;

    private final String kind;
    private final boolean placed;               // the layout's machine
    private int machineSpawnX;                  // oFreezerParent of a nozzle: the machine, found by its layout spawn
    private int machineSpawnY;
    private int time;
    private int yVel;
    private int xVel;
    private int breakTime;
    private int piece;
    private CdAnimator animator;
    private boolean initialised;
    private int statusFlip;
    /** The ice block holds the player (control and hidden bits) from the freeze until the break. */
    private boolean holdsPlayer;

    public ObjFreezer(ObjectSpawn spawn) {
        super(spawn, "Freezer");
        this.kind = WwzObjectArt.FREEZER;
        this.statusFlip = spawn.renderFlags() & 3;
        this.placed = true;
    }

    /** An object another routine allocates with ID 5 (all fields but the ones set are cleared). */
    private ObjFreezer(int x, int y, int routine, String kind, int tile, int flip) {
        super(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0x05, 0, 0, false, 0), "FreezerPart");
        this.kind = kind;
        this.routine = routine;
        this.tileWord = tile;
        this.spriteFlip = flip & 3;
        this.statusFlip = 0;
        this.initialised = true;
        this.placed = false;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (WwzStage.nullObject(services(), 0x05)) {
            setDestroyed(true);
            return;
        }
        AbstractPlayableSprite sprite = sprite(player);
        switch (routine) {
            case 0 -> {
                init();
                main();
            }
            case MAIN -> main();
            case NOZZLE -> nozzle(sprite);
            case RESET -> reset();
            case ICE_BLOCK -> iceBlock(sprite);
            case ICE_LANDED -> iceLanded(sprite);
            case ICE_PIECE -> icePiece();
            default -> { }
        }
    }

    /** ObjFreezer_Init (:35-40). */
    private void init() {
        tileWord = WwzObjectArt.FREEZER_TILE;
        time = REARM;
        routine = MAIN;
        initialised = true;
    }

    /** ObjFreezer_Main (:44-65): the nozzle appears when the timer runs out. */
    private void main() {
        if (time == 0) return;
        time = (time - 1) & 0xFF;
        if (time != 0) return;
        var objects = services().objectManager();
        if (objects == null) return;
        ObjFreezer nozzle = new ObjFreezer(x, y + 36, NOZZLE, WwzObjectArt.FREEZER, tileWord, 0);
        nozzle.machineSpawnX = getSpawn().x();
        nozzle.machineSpawnY = getSpawn().y();
        nozzle.priority = 3;
        objects.addDynamicObject(nozzle);
    }

    /** ObjFreezer_Freezer (:69-72) and ObjFreezer_ChkSonicFreeze (:291-302). */
    private void nozzle(AbstractPlayableSprite player) {
        if (animator == null) animator = WwzObjectArt.animator(artProvider(), "freezerAnimations", 0);
        if (animator != null && animator.anim == 1 && player != null
                && player.getAnimationId() != GIVE_UP_ANIMATION && canFreeze(player)) {
            freeze(player);
        }
        if (animator == null) return;
        int result = animator.step();
        mapFrame = animator.mapFrame();
        spriteFlip = (statusFlip ^ animator.flipBits()) & 3;
        if (result == 2) routine += 2;                           // $FC: addq.b #2,oRoutine
    }

    /** ObjFreezer_CheckSonic (:306-344). */
    private boolean canFreeze(AbstractPlayableSprite player) {
        if (player.getInvincibleFrames() > 0) return false;      // tst.b invincible
        var tt = CdTimeTravel.of(services().gameModule());
        if (tt != null && tt.warping()) return false;            // tst.b timeWarp
        if (player.isHurt() || player.getDead()) return false;   // cmpi.b #4,oRoutine(a1) / bcc.s
        if (WwzPlayerProfile.playerControl(player) != 0) return false;   // tst.b oPlayerCtrl(a1)
        int reachX = player.getXRadius() + 16;
        int dx = (player.getCentreX() - x + reachX) & 0xFFFF;
        if ((dx & 0x8000) != 0 || dx >= reachX * 2) return false;
        int reachY = player.getYRadius() + 32;
        int dy = (player.getCentreY() - y + reachY) & 0xFFFF;
        return (dy & 0x8000) == 0 && dy < reachY * 2;
    }

    /** ObjFreezer_FreezeSonic (:265-286). */
    private void freeze(AbstractPlayableSprite player) {
        var objects = services().objectManager();
        if (objects == null) return;
        ObjFreezer ice = new ObjFreezer(player.getCentreX(), player.getCentreY(), ICE_BLOCK, WwzObjectArt.ICE,
                WwzObjectArt.ICE_TILE, 0);
        ice.width = 0x18;
        ice.yRadius = 0x18;
        ice.mapFrame = 8;
        player.setObjectControlled(true);                        // bset #0,oPlayerCtrl(a2)
        player.setObjectControlAllowsCpu(true);                  // bit 0 only: oPlayerCtrl's sign stays clear
        ice.holdsPlayer = true;
        WwzPlayerProfile.setHidden(player, true);                // bset #6,oPlayerCtrl(a2)
        objects.addDynamicObject(ice);
    }

    /** ObjFreezer_Reset (:76-79). */
    private void reset() {
        var objects = services().objectManager();
        if (objects != null) {
            for (ObjFreezer machine : objects.activeObjectsOfType(ObjFreezer.class)) {
                if (machine.placed && machine.getSpawn().x() == machineSpawnX && machine.getSpawn().y() == machineSpawnY) {
                    machine.time = REARM;
                }
            }
        }
        setDestroyed(true);
    }

    /** ObjFreezer_IceBlock (:83-105). */
    private void iceBlock(AbstractPlayableSprite player) {
        yVel = (short) (yVel + ICE_GRAVITY);
        addToY((short) yVel << 8);
        int floor = ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
        if (floor < 0) {
            services().playMusic(CdSubCpuAudio.commandId("SCMD_BREAKSFX"));
            breakTime = 0xF;
            y = (y + floor) & 0xFFFF;
            routine += 2;
        }
        followWithPlayer(player);
    }

    /** {@code .UpdateY}: {@code move.l oY(a0),oY(a1)} onto the frozen player. */
    private void followWithPlayer(AbstractPlayableSprite player) {
        if (player == null) return;
        NativePositionOps.writeYPosPreserveSubpixel(player, y);
        player.setSubpixelRaw(player.getXSubpixelRaw(), ySub);
    }

    /** ObjFreezer_IceLanded (:109-150). */
    private void iceLanded(AbstractPlayableSprite player) {
        AbstractPlayableSprite target = player;
        if (target == null) return;
        if (breakTime != 0) {
            breakTime--;
            if (!target.isJumpJustPressed()) return;             // andi.b #$70,d0 / beq.s .End
            target.setObjectControlled(false);                   // bclr #0,oPlayerCtrl(a1)
            WwzPlayerProfile.setHidden(target, false);           // bclr #6,oPlayerCtrl(a1)
            target.setYSpeed((short) JUMP_SPEED);
            ObjLauncher.startRollingJump(target);                // $E/7 radii, y + 5, rolling, anim 2
            services().playSfx(WwzSounds.JUMP);
        } else {
            target.setObjectControlled(false);
            WwzPlayerProfile.setHidden(target, false);
            WwzPlayerProfile.hurtPlayer(target, x);              // jsr HurtPlayer with a2 = this block
        }
        holdsPlayer = false;
        routine += 2;                                            // .Broken
        mapFrame = 0xA;
        breakTime = 20;
        piece = 2;
        breakIntoPieces(WwzObjectCatalogue.offset(artProvider().payload(), "freezerIceBlockPieces"), 6);
    }

    /** ObjFreezer_IcePiece (:155-178). */
    private void icePiece() {
        breakTime = (breakTime - 1) & 0xFF;
        if (breakTime != 0) {
            x = (x + (short) xVel) & 0xFFFF;                     // add.w d0,oX(a0)
            y = (y + (short) yVel) & 0xFFFF;
            return;
        }
        if (mapFrame != 0xB) {
            var provider = artProvider();
            int table = WwzObjectCatalogue.offset(provider.payload(), "freezerIcePieceTable");
            int offset = (short) provider.image().u16(table + piece * 2);
            breakIntoPieces(table + offset, 4);
        }
        setDestroyed(true);
    }

    /** ObjFreezer_IceBlockBreak / ObjFreezer_IcePieceBreak (:207-251): eight-byte records. */
    private void breakIntoPieces(int records, int count) {
        var provider = artProvider();
        var image = provider.image();
        var objects = services().objectManager();
        if (objects == null) return;
        for (int i = 0; i < count; i++) {
            int at = records + i * 8;
            ObjFreezer part = new ObjFreezer(x + (byte) image.u8(at), y + (byte) image.u8(at + 1), ICE_PIECE,
                    WwzObjectArt.ICE, WwzObjectArt.ICE_TILE, image.u8(at + 4));
            part.breakTime = image.u8(at + 2);
            part.mapFrame = image.u8(at + 3);
            part.xVel = (byte) image.u8(at + 5);
            part.yVel = (byte) image.u8(at + 6);
            part.piece = image.u8(at + 7);
            objects.addDynamicObject(part);
        }
    }

    /** Clears the control this object still holds (the player-only half of its own release). */
    @Override
    public void releasePlayersForVisualDirection() {
        if (!holdsPlayer) return;
        holdsPlayer = false;
        if (services().camera().getFocusedSprite() instanceof AbstractPlayableSprite sprite) {
            sprite.setObjectControlled(false);                  // bclr #0,oPlayerCtrl
            WwzPlayerProfile.setHidden(sprite, false);          // bclr #6,oPlayerCtrl
        }
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    protected String sheetKey() {
        return initialised ? WwzObjectArt.key(kind) : null;
    }

    public int time() { return time; }
}
