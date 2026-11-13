package micaandtalon;

import com.openggf.game.StageSpriteSet;
import com.openggf.io.ModAssetRoot;

import java.io.IOException;

/**
 * StageSpriteSet.S3K_COMPETITION miniature body mappings for MICA. Indexed images and per-selector
 * origins replace the native body while the host owns race mechanics and timing.
 */
final class MicaCompetitionFrames {
    static StageSpriteSet load(ModAssetRoot assets) throws IOException {
        return FrameTable.read(assets, "art/mica/competition/frames.txt")
                .banks()
                .get("S3K_COMPETITION");
    }
}
