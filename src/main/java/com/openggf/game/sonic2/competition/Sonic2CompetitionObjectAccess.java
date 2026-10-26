package com.openggf.game.sonic2.competition;

import com.openggf.audio.GameMusic;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic2.constants.Sonic2AudioConstants;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.audio.Sonic2SmpsConstants;
import com.openggf.level.objects.ObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;

/** Internal native object consumers; ordinary campaign awards never pass through this port. */
public final class Sonic2CompetitionObjectAccess {
    private Sonic2CompetitionObjectAccess() { }

    public static Sonic2CompetitionSession session(ObjectServices services) {
        return services == null ? null : WorldSessionPolicyAccess.getService(
                services.worldSession(), Sonic2CompetitionSession.class);
    }

    public static int slot(ObjectServices services, AbstractPlayableSprite player) {
        var session = session(services);
        return session == null ? -1 : session.slotOf(player);
    }

    static Sonic2CompetitionObjectHost host(ObjectServices services) {
        var host = WorldSessionPolicyAccess.getService(services.worldSession(), Sonic2CompetitionObjectHost.class);
        if (host == null) throw new IllegalStateException("Native competition object host is unavailable");
        return host;
    }

    /** Obj2E_Raise dispatches only at the apex; named lives and all counters belong to the session. */
    static void awardMonitor(ObjectServices services, int slot, int content, int sourceX, int vIntRunCount) {
        var session = session(services);
        if (session == null) throw new IllegalStateException("Native monitor session is unavailable");
        var player = session.player(slot);
        if (content == 4 && !com.openggf.game.mutators.LevelMutatorPolicyAccess.ringsAllowed(services)) return;
        int lives = session.gameState(slot).getLives();
        session.awardMonitor(slot, content);
        switch (content) {
            case 0, 3 -> hurtFromMonitor(services, player, sourceX, vIntRunCount);
            case 1, 2 -> services.playMusic(GameMusic.EXTRA_LIFE);
            case 4 -> {
                // REV01 super_ring reaches ChkPlayer_1up instead of the ring mailbox at a threshold.
                if (session.gameState(slot).getLives() > lives) services.playMusic(GameMusic.EXTRA_LIFE);
                else services.playMusicMailboxNativeRequest(Sonic2AudioConstants.SFX_RING_RIGHT);
            }
            case 5 -> {
                player.giveSpeedShoes();
                services.playMusic(Sonic2SmpsConstants.CMD_SPEED_UP);
            }
            case 6 -> {
                player.giveShield();
                services.playMusicMailboxNativeRequest(Sonic2Sfx.SHIELD.id);
            }
            case 7 -> {
                if (!session.player(0).isSuperSonic()) {
                    player.giveInvincibility();
                    var drowning = player.getDrowningController();
                    if (session.gameState(0).getCurrentBossId() == 0
                            && (drowning == null || drowning.getRemainingAir() > 12))
                        services.playMusic(GameMusic.INVINCIBILITY);
                }
            }
            case 8 -> host(services).requestTeleport(session);
            default -> { }
        }
    }

    private static void hurtFromMonitor(ObjectServices services, AbstractPlayableSprite player,
                                        int sourceX, int vIntRunCount) {
        // Touch_ChkHurt2 (s2.asm:29494-29516) gates routine >=4 before undoing
        // this tick's Y step. HurtCharacter owns shield/rings/death and knockback.
        if (player.getInvulnerable() || player.getDead() || player.isDrowningPreDeath()) return;
        NativePositionOps.addYPos16_16(player, -(player.getYSpeed() << 8));
        boolean hadRings = player.getRingCount() > 0;
        if (hadRings && !player.hasShield() && !player.suppressesLostRingSpawnOnHurt())
            services.spawnLostRings(player, vIntRunCount);
        player.applyHurtOrDeath(sourceX, false, hadRings);
    }
}
