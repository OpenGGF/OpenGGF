package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * State every Wacky Workbench boss object shares (objects/r6/boss.asm:1-2103): {@code oXVel}/{@code oYVel}, the
 * per-frame accelerations the boss keeps in {@code var_30}/{@code var_32} ({@link #integrateVelocity}, BossIntegrateVelocity,
 * :1594-1612), the {@code var_2a} frame counter, and scalar mirrors of the level RAM in {@link WwzBossRam} so the generic
 * rewind capture restores it.
 */
abstract class WwzBossPiece extends CdBossPart {
    int xVel;
    int yVel;
    int accelX;
    int accelY;
    int timer;
    // WwzBossRam mirrors, written by every RAM write.
    private int ramFlags;
    private int ramFight;
    private boolean ramMusic;
    private boolean ramKilled;
    /** The mirrors hold a RAM write (a part spawned after the last write has none and does not restore the RAM). */
    private boolean ramMirrored;

    WwzBossPiece(int id, int x, int y, String name) {
        super(id, x, y, name);
    }

    WwzBossPiece(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    /** The stage's boss RAM, bound to the zone player's signals. */
    final WwzBossRam ram() {
        var art = artProvider();
        WwzBossRam ram = art == null ? new WwzBossRam() : art.zoneArt(WwzBossRam.class, WwzBossRam::new);
        ram.bind(services().gameModule(), cdLevel(), services().levelManager());
        return ram;
    }

    /** The art provider, for the level events' read of the boss RAM. */
    final com.openggf.game.soniccd.objects.art.CdObjectArtProvider artProviderForEvents() {
        return artProvider();
    }

    final void mirror(WwzBossRam ram) {
        ramFlags = ram.flags();
        ramFight = ram.fight();
        ramMusic = ram.music();
        ramKilled = ram.playerKilled();
        ramMirrored = true;
    }

    @Override
    protected void afterGenericRewindStateRestored(com.openggf.game.rewind.schema.RewindCaptureContext context) {
        var art = artProvider();
        if (art != null && ramMirrored) {
            art.zoneArt(WwzBossRam.class, WwzBossRam::new).restore(ramFlags, ramFight, ramMusic, ramKilled);
        }
    }

    /**
     * {@code move.b d,boss_started}: {@code bossFight} also drops the player's 56-pixel right extension while it is
     * set ({@code PlayerCheckBounds}, objects/r6/player.asm:1956-1964: {@code tst.b boss_started; bne}); the shared
     * boundary reads that gate from the screen-lock flag, so the boss drives it with the value.
     */
    final void setFight(int value) {
        ram().setFight(objects(), value);
        var state = services().gameState();
        if (state != null) state.setScreenLocked((value & 0xFF) != 0);
    }

    final com.openggf.level.objects.ObjectManager objects() {
        return services().objectManager();
    }

    /** BossIntegrateVelocity (objects/r6/boss.asm:1608-1624): the accelerations, then BossIntegratePosition. */
    final void integrateVelocity() {
        xVel = (short) (xVel + accelX);
        yVel = (short) (yVel + accelY);
        integratePosition();
    }

    /** BossIntegratePosition (objects/r6/boss.asm:1614-1624): {@code ext.l; lsl.l #8; add.l} to X and Y. */
    final void integratePosition() {
        addXLong((long) (short) xVel << 8);
        addYLong((long) (short) yVel << 8);
    }

    /** {@code SineCosine}: d0 = sine of the byte angle. */
    static int sine(int angle) { return com.openggf.physics.TrigLookupTable.sinHex(angle & 0xFF); }

    static AbstractPlayableSprite sonic(PlayableEntity player) {
        return player instanceof AbstractPlayableSprite s ? s : null;
    }

    /**
     * BossHurtPlayer (objects/r6/boss.asm:1366-1381): unless the player's {@code oFlags} bit 7 is set,
     * {@code CheckHurtPlayer}, the entry after the warp and invincibility tests (Wacky Workbench/Player Object
     * Collision.asm; src/r6/player_object_collide.asm:221-226), so only the hurt timer protects ({@code FixBugs = 0}: an
     * invincible player is hurt too); on the death animation ($18) bit 7 is set. Returns d0 = -1.
     */
    final void hurtPlayer(AbstractPlayableSprite player) {
        if (player == null || ram().playerKilled()) return;
        if (player.getInvulnerableFrames() == 0 && !player.isHurt() && !player.getDead()) {
            com.openggf.game.soniccd.zones.wwz.WwzPlayerProfile.hurtPlayer(player, x);
        }
        if (player.getDead()) {                                     // cmpi.b #$18,obj.anim_id(a1) ; bne (:1374-1375)
            ram().setPlayerKilled(objects());
            com.openggf.game.soniccd.player.CdPlayerObjectFlags.setBit7(player);   // bset #7,obj.flags(a1)
        }
    }

    @Override
    protected String sheetKey() {
        return artProvider() == null ? null : WwzBossArt.key(sheet());
    }

    /** The part's sheet kind ({@link WwzBossArt}). */
    abstract String sheet();
}
