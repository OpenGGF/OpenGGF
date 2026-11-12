package com.openggf.game.sonic3k.bluespheres;

import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.TileLoadRequest;

import java.util.List;

/**
 * A player sprite drawn by a Blue Spheres screen object: Animate_Sonic or
 * Animate_Knuckles for a plain delay script, then Sonic_Load_PLC or
 * Knuckles_Load_PLC copying the frame's tiles to {@code ArtTile_Player_1}.
 */
final class S3kBlueSpheresPlayerPose {
    private final S3kBlueSpheresAssets assets;
    private final List<SpriteMappingFrame> mappings;
    private final List<SpriteDplcFrame> dplc;
    private final int art;
    private final int animations;
    private int loadedFrame = -1;

    private S3kBlueSpheresPlayerPose(S3kBlueSpheresAssets assets, int mappings, int dplc, int art, int animations) {
        this.assets = assets;
        this.mappings = assets.mappings(mappings);
        this.dplc = S3kSpriteDataLoader.loadDplcFrames(assets.rom(), dplc);
        this.art = art;
        this.animations = animations;
    }

    static S3kBlueSpheresPlayerPose sonic(S3kBlueSpheresAssets assets) {
        return new S3kBlueSpheresPlayerPose(assets, S3kBlueSpheresAssets.MAP_SONIC,
                S3kBlueSpheresAssets.DPLC_SONIC, S3kBlueSpheresAssets.ART_UNC_SONIC, S3kBlueSpheresAssets.ANI_SONIC);
    }

    static S3kBlueSpheresPlayerPose knuckles(S3kBlueSpheresAssets assets) {
        return new S3kBlueSpheresPlayerPose(assets, S3kBlueSpheresAssets.MAP_KNUCKLES,
                S3kBlueSpheresAssets.DPLC_KNUCKLES, S3kBlueSpheresAssets.ART_UNC_KNUCKLES,
                S3kBlueSpheresAssets.ANI_KNUCKLES);
    }

    List<SpriteMappingFrame> mappings() {
        return mappings;
    }

    /**
     * The non-movement arm of Animate_Sonic/Animate_Knuckles: a changed
     * {@code anim} restarts the script; every {@code delay+1} frames the next
     * frame byte shows; {@code $FF} restarts and {@code $FE n} steps back n.
     */
    void animate(S3kBlueSpheresObject self) {
        int script = animations + assets.signedWord(animations + self.anim * 2);
        if (self.anim != self.prevAnim) {
            self.prevAnim = self.anim;
            self.animFrame = 0;
            self.animTimer = 0;
        }
        self.animTimer = (self.animTimer - 1) & 0xFF;
        if ((self.animTimer & 0x80) == 0) return;
        self.animTimer = assets.u8(script);
        int index = self.animFrame;
        int frame = assets.u8(script + 1 + index);
        if (frame >= 0xFC) {
            if (frame == 0xFF) {
                self.animFrame = 0;
                index = 0;
            } else if (frame == 0xFE) {
                int back = assets.u8(script + 2 + index);
                self.animFrame = (self.animFrame - back) & 0xFF;
                index = (index - back) & 0xFF;
            } else {
                return;
            }
            frame = assets.u8(script + 1 + index);
        }
        self.frame = frame;
        self.animFrame = (self.animFrame + 1) & 0xFF;
    }

    /** Sonic_Load_PLC/Knuckles_Load_PLC: reload only when the frame changed. */
    void loadPlc(S3kBlueSpheresObject self, Pattern[] vram) {
        if (self.frame == loadedFrame || self.frame >= dplc.size()) return;
        loadedFrame = self.frame;
        int tile = S3kBlueSpheresAssets.TILE_PLAYER_1;
        for (TileLoadRequest request : dplc.get(self.frame).requests()) {
            byte[] data = assets.rom().slice(art + request.startTile() * Pattern.PATTERN_SIZE_IN_ROM,
                    request.count() * Pattern.PATTERN_SIZE_IN_ROM);
            S3kBlueSpheresAssets.load(vram, tile, data);
            tile += request.count();
        }
    }
}
