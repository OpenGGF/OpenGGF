package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kPlayerArt;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.TailsTailsController;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.io.IOException;

/**
 * Host presentation of the dedicated Slot sprite, Obj_Sonic_RotatingSlotBonus.
 *
 * <p>loc_4B9E8 (sonic3k.asm:98718-98742) installs host {@code Map_Sonic},
 * {@code Map_Tails} or {@code Map_Knuckles} by {@code Player_mode} with
 * {@code ArtTile_Player_1} ({@code ArtTile_Player_2} for Tails alone, which also
 * gets a fresh {@code Obj_Tails_Tail} whose {@code $30} is this sprite). These are
 * host ROM resources whatever donor the ordinary player is configured with; the
 * ordinary player's own renderer and controllers are untouched and return with it.
 *
 * <p>Every frame the shared tail loc_4B97C (98670-98680) runs Animate and then
 * {@code Perform_Player_DPLC} (25260), whose {@code character_id} selects
 * {@code Sonic_Load_PLC2} (25215: {@code Player_prev_frame}, PLC_Sonic, D000),
 * {@code Tails_Load_PLC2} (29635: {@code Player_prev_frame_P2}, PLC_Tails, D400) or
 * {@code Knuckles_Load_PLC2} (33233: {@code Player_prev_frame}, PLC_Knuckles, D000).
 * Each routine stores the new frame before its empty-cue check. The goal write
 * {@code move.w #60,$38(a0)} (98971) zeroes {@code character_id}, so the rest of the
 * fade submits Sonic cues to D000: Knuckles' retained Map_Knuckles then draws Sonic
 * tiles, while Tails' D400 body keeps its last resident tiles. Level's
 * {@code clearRAM Tails_CPU_interact,$100} (7621) zeroes all previous-frame bytes
 * ($F766, $F7DE) on entry.
 */
final class S3kSlotPlayerArt {
    static final int SONIC = 0;
    static final int TAILS = 1;
    static final int KNUCKLES = 2;

    private final int bodyCharacter;
    private final SpriteArtSet bodyArt;
    private final SpriteArtSet sonicArt;
    private final PlayerSpriteRenderer body;
    private final TailsTailsController tail;
    private int characterId;
    private int playerPrevFrame;
    private int playerPrevFrameP2;

    record RewindState(int characterId, int playerPrevFrame, int playerPrevFrameP2,
            PlayerResidentBanks.Snapshot body, PlayerResidentBanks.Snapshot tail) { }

    private S3kSlotPlayerArt(int bodyCharacter, SpriteArtSet bodyArt, SpriteArtSet sonicArt,
            SpriteArtSet tailArt, AbstractPlayableSprite slotPlayer) {
        this.bodyCharacter = bodyCharacter;
        this.bodyArt = bodyArt;
        this.sonicArt = sonicArt;
        this.characterId = bodyCharacter;
        var graphics = GameServices.graphics();
        body = new PlayerSpriteRenderer(bodyArt, graphics);
        PlayerResidentBanks.claim(body);
        tail = tailArt == null ? null
                : new TailsTailsController(slotPlayer, new PlayerSpriteRenderer(tailArt, graphics), true);
    }

    /** Loads host art from the active ROM, failing closed when it is not available. */
    static S3kSlotPlayerArt load(int character, AbstractPlayableSprite slotPlayer) {
        try {
            var art = new Sonic3kPlayerArt(RomByteReader.fromRom(GameServices.rom().getRom()));
            SpriteArtSet body = switch (character) {
                case TAILS -> art.loadTails();
                case KNUCKLES -> art.loadKnuckles();
                default -> art.loadSonic();
            };
            if (body == null || body.mappingFrames().isEmpty()) {
                throw new IllegalStateException("Slot sprite requires host player mappings");
            }
            return new S3kSlotPlayerArt(character, body, art.loadSonic(),
                    character == TAILS ? art.loadTailsTail() : null, slotPlayer);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Slot sprite requires host ROM player art", failure);
        }
    }

    void bind(AbstractPlayableSprite slotPlayer) {
        slotPlayer.setSpriteRenderer(body);
        slotPlayer.setAnimationFrameCount(bodyArt.mappingFrames().size());
        slotPlayer.setAnimationProfile(bodyArt.animationProfile());
        slotPlayer.setAnimationSet(bodyArt.animationSet());
        slotPlayer.setTailsTailsController(tail);
    }

    /** loc_4BC1E's {@code move.w #60,$38(a0)}: character_id becomes Sonic's 0. */
    void writeGoalCharacterId() {
        characterId = SONIC;
    }

    /** Perform_Player_DPLC for the mapping frame Animate just published. */
    void performPlayerDplc(int mappingFrame) {
        int frame = mappingFrame & 0xFF;
        switch (characterId) {
            case SONIC -> {
                if (frame == playerPrevFrame) return;
                playerPrevFrame = frame;
                // Sonic's D000 is the displayed bank for Sonic and Knuckles only.
                if (bodyCharacter != TAILS) load(sonicArt, frame);
            }
            case TAILS -> {
                if (frame == playerPrevFrameP2) return;
                playerPrevFrameP2 = frame;
                load(bodyArt, frame);
            }
            default -> {
                if (frame == playerPrevFrame) return;
                playerPrevFrame = frame;
                load(bodyArt, frame);
            }
        }
    }

    private void load(SpriteArtSet source, int frame) {
        if (frame >= source.dplcFrames().size()) return;
        SpriteDplcFrame cues = source.dplcFrames().get(frame);
        PlayerResidentBanks.load(body, cues, source.artTiles());
    }

    RewindState capture() {
        return new RewindState(characterId, playerPrevFrame, playerPrevFrameP2,
                PlayerResidentBanks.capture(body),
                tail == null ? null : PlayerResidentBanks.capture(tail.getRenderer()));
    }

    /** Restores VRAM and DPLC RAM exactly; no animation tick or cue submission. */
    void restore(RewindState state) {
        characterId = state.characterId();
        playerPrevFrame = state.playerPrevFrame();
        playerPrevFrameP2 = state.playerPrevFrameP2();
        PlayerResidentBanks.restore(body, state.body());
        if (tail != null) PlayerResidentBanks.restore(tail.getRenderer(), state.tail());
    }

    static int characterIdFor(AbstractPlayableSprite player) {
        if (player instanceof com.openggf.sprites.playable.Tails) return TAILS;
        if (player instanceof com.openggf.sprites.playable.Knuckles) return KNUCKLES;
        return SONIC;
    }
}
