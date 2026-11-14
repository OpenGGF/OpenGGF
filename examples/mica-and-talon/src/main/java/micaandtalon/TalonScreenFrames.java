package micaandtalon;

import com.openggf.game.CharacterPresentation.Host;
import com.openggf.game.PresentationFrameSet;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * PresentationFrameSet for TALON's continue and data-select portrait banks. Maps host-native pose
 * IDs to indexed images and explicit origins; screen flow stays host-owned.
 */
final class TalonScreenFrames {
    static PresentationFrameSet load(ModAssetRoot assets, Host host) throws IOException {
        String root = "art/talon/";
        String game = host.name().toLowerCase(Locale.ROOT);
        var banks =
                new LinkedHashMap<>(
                        FrameTable.read(assets, root + "continue/" + game + "-frames.txt")
                                .screenBanks());
        if (host == Host.S3K) {
            banks.putAll(FrameTable.read(assets, root + "portrait/s3k-frames.txt").screenBanks());
        }
        int[] rgb =
                PresentationImages.backing(assets, root + "hud-and-screens/s3k-hud.png", 0).rgb();
        return new PresentationFrameSet(banks, rgb);
    }
}
