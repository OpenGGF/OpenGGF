package com.openggf.game.soniccd;

import com.openggf.data.RomByteReader;
import com.openggf.game.common.CommonSpriteDataLoader;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sonic's art from the loaded stage file. Mappings use Sonic 1's format (byte piece
 * count, five-byte pieces) and animations its script format; DPLC frames differ from
 * Sonic 1 by a word entry count ({@code move.w (a2)+,d1}, LoadSonicDynPLC,
 * Level/_Objects/Sonic/Main.asm:3069-3073). Art is uncompressed.
 */
final class CdPlayerArt {
    /** ObjSonic_Init: move.w #$780,oTile(a0) (Level/_Objects/Sonic/Main.asm:147). */
    static final int ART_TILE = 0x780;

    /**
     * {@code PlayerShrunkAnimationMap} (src/objects/player.asm:2802-2860): the script each animation ID reads while
     * {@code miniSonic} is set.
     */
    private static final int[] SHRUNK_ANIMATION_MAP = {
            0x21, 0x18, 0x23, 0x23, 0x27, 0x1F, 0x26, 0x28, 0x20, 0x09, 0x0A, 0x0B, 0x0C, 0x24, 0x0E, 0x0F,
            0x28, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x25, 0x25, 0x1C, 0x1D, 0x1E, 0x1F,
            0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x30, 0x2C, 0x2D, 0x2E, 0x2F,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x39, 0x00};
    /** The shrunk walk, run, roll and push scripts: animation table entries $21, $22, $23 and $27
     *  (anims/player.asm:39-45). */
    private static final int WALK_SHRUNK = 0x21;
    private static final int RUN_SHRUNK = 0x22;
    private static final int ROLL_SHRUNK = 0x23;
    private static final int PUSH_SHRUNK = 0x27;

    private CdPlayerArt() { }

    private static boolean miniPlayer(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        var stage = com.openggf.game.soniccd.player.CdPlayerStage.of(player);
        return stage != null && stage.miniPlayer(player);
    }

    static SpriteArtSet load(CdStageImage image, CdStageCatalogue.Entry entry) throws IOException {
        RomByteReader reader = image.reader();
        List<SpriteMappingFrame> mappings = CommonSpriteDataLoader.loadVer1MappingFrames(reader, entry.playerMappings());
        List<SpriteDplcFrame> dplcs = loadWordCountDplcs(reader, entry.playerDplc());
        int tiles = 0;
        for (SpriteDplcFrame frame : dplcs) {
            for (TileLoadRequest request : frame.requests()) tiles = Math.max(tiles, request.startTile() + request.count());
        }
        Pattern[] art = CommonSpriteDataLoader.loadArtTiles(reader, entry.playerArt(),
                Math.min(tiles * Pattern.PATTERN_SIZE_IN_ROM, image.size() - entry.playerArt()));
        int animationCount = reader.readU16BE(entry.playerAnimations()) / 2;
        SpriteAnimationSet animations = CommonSpriteDataLoader.loadAnimationSet(reader, entry.playerAnimations(), animationCount);
        // Ani_Sonic IDs $00-$1B follow Sonic 1's order (Level/_Objects/Sonic/Data/Animations.asm:1-29).
        var profile = new ScriptedVelocityAnimationProfile()
                .setIdleAnimId(CdSonicAnimation.IDLE)
                .setWalkAnimId(CdSonicAnimation.WALK)
                .setRunAnimId(CdSonicAnimation.RUN)
                .setRunFramesUseWalkAnimationId(true)
                .setRollAnimId(CdSonicAnimation.ROLL)
                .setRoll2AnimId(CdSonicAnimation.ROLL_FAST)
                .setPushAnimId(CdSonicAnimation.PUSH)
                .setPushUsesWalkSpecialHandler(true)
                // $FC scripts (SonAni_Hang2, the rotating pole's) show the frame the stage routine
                // indexes (PlayerAnimateStaticFrame, src/objects/player.asm:2732-2737).
                .setStaticFrameScriptFlags(true)
                // miniSonic: PlayerShrunkAnimationMap and the shrunk walk/run, roll and push scripts
                // (src/objects/player.asm:2575-2581, 2660-2666, 2717-2721, 2740-2860).
                .setMiniForm(new ScriptedVelocityAnimationProfile.MiniForm(CdPlayerArt::miniPlayer,
                        id -> id >= 0 && id < SHRUNK_ANIMATION_MAP.length ? SHRUNK_ANIMATION_MAP[id] : id,
                        WALK_SHRUNK, RUN_SHRUNK, ROLL_SHRUNK, PUSH_SHRUNK))
                .setDuckAnimId(CdSonicAnimation.DUCK)
                .setLookUpAnimId(CdSonicAnimation.LOOK_UP)
                .setSpringAnimId(CdSonicAnimation.SPRING)
                .setDeathAnimId(CdSonicAnimation.DEATH)
                .setDrownAnimId(CdSonicAnimation.DROWN)
                .setHurtAnimId(CdSonicAnimation.HURT)
                .setSkidAnimId(CdSonicAnimation.SKID)
                .setSlideAnimId(CdSonicAnimation.SLIDE)
                .setAirAnimId(CdSonicAnimation.WALK)
                .setBalanceAnimId(CdSonicAnimation.BALANCE)
                // .BalanceAniBackwards writes SonAni_Balance2 when facing away from the
                // edge (Sonic/Main.asm:929-931); balance state 3 is "facing away".
                .setBalance3AnimId(com.openggf.game.soniccd.player.CdAnimationIds.BALANCE_BACKWARDS)
                // On a 3D ramp: SonAni_Run3D and SonAni_Roll3D (Sonic/Main.asm:2761-2770,
                // 2819-2830; Animations.asm:55-56).
                .setPseudo3dRampRunAnimId(com.openggf.game.soniccd.player.CdAnimationIds.RUN_3D)
                .setPseudo3dRampRollAnimId(com.openggf.game.soniccd.player.CdAnimationIds.ROLL_3D)
                .setWalkSpeedThreshold(0x40)
                .setRunSpeedThreshold(0x600)
                // ObjSonic_Animate selects SonAni_Peelout at |ground speed| >= $A00, still
                // under raw anim 0, with Run's add.b d0,d0 angle stride (Sonic/Main.asm:2772-2786).
                .setHighSpeedWalkRunAnimId(com.openggf.game.soniccd.player.CdAnimationIds.PEELOUT)
                .setHighSpeedWalkRunThreshold(0xA00)
                .setHighSpeedSlopeFrameStride(2)
                .setFallbackFrame(0);
        return new SpriteArtSet(art, mappings, dplcs, 0, ART_TILE, 1,
                CommonSpriteDataLoader.resolveBankSize(dplcs, mappings), profile, animations);
    }

    private static List<SpriteDplcFrame> loadWordCountDplcs(RomByteReader reader, int table) {
        int frames = reader.readU16BE(table) / 2;
        List<SpriteDplcFrame> result = new ArrayList<>(frames);
        for (int i = 0; i < frames; i++) {
            int at = table + reader.readU16BE(table + i * 2);
            int count = reader.readU16BE(at);
            at += 2;
            List<TileLoadRequest> requests = new ArrayList<>(count);
            for (int r = 0; r < count; r++, at += 2) {
                int word = reader.readU16BE(at);
                requests.add(new TileLoadRequest(word & 0x0FFF, ((word >> 12) & 0xF) + 1));
            }
            result.add(new SpriteDplcFrame(requests));
        }
        return result;
    }
}
