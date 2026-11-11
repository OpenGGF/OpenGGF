package micaandtalon;
import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;
import java.io.IOException;
import java.util.*;
/** PresentationFrameSet for TALON's continue and data-select portrait banks. Maps host-native pose IDs to indexed images and explicit origins; screen flow stays host-owned. */
final class TalonScreenFrames {
    static PresentationFrameSet load(ModAssetRoot assets, CharacterPresentation.Host host) throws IOException {
        var banks = new LinkedHashMap<String, Map<Integer, PresentationFrameSet.Pose>>();
        if (host == CharacterPresentation.Host.S1) {
            add(banks, assets, "continue/floor", 1, "continue/s1-00.png", 20, 12);
            add(banks, assets, "continue/floor", 2, "continue/s1-01.png", 20, 12);
            add(banks, assets, "continue/floor", 3, "continue/s1-02.png", 20, 12);
            add(banks, assets, "continue/icon", 6, "continue/s1-03.png", 0, 0);
            add(banks, assets, "continue/icon", 7, "continue/s1-04.png", 0, 0);
            add(banks, assets, "continue/player", 60, "continue/s1-05.png", 15, 12);
            add(banks, assets, "continue/player", 61, "continue/s1-06.png", 20, 12);
            add(banks, assets, "continue/player", 83, "continue/s1-07.png", 27, 12);
            add(banks, assets, "continue/player", 62, "continue/s1-08.png", 24, 12);
            add(banks, assets, "continue/player", 84, "continue/s1-09.png", 10, 12);
            add(banks, assets, "continue/player", 6, "continue/s1-10.png", 18, 19);
            add(banks, assets, "continue/player", 7, "continue/s1-11.png", 12, 18);
            add(banks, assets, "continue/player", 8, "continue/s1-12.png", 14, 17);
            add(banks, assets, "continue/player", 9, "continue/s1-13.png", 18, 19);
            add(banks, assets, "continue/player", 10, "continue/s1-14.png", 18, 18);
            add(banks, assets, "continue/player", 11, "continue/s1-15.png", 13, 17);
            add(banks, assets, "continue/player", 30, "continue/s1-16.png", 20, 15);
            add(banks, assets, "continue/player", 31, "continue/s1-17.png", 21, 16);
            add(banks, assets, "continue/player", 32, "continue/s1-18.png", 20, 15);
            add(banks, assets, "continue/player", 33, "continue/s1-19.png", 22, 16);
        }
        if (host == CharacterPresentation.Host.S2) {
            add(banks, assets, "continue/nag", 2, "continue/s2-00.png", 14, 16);
            add(banks, assets, "continue/nag", 3, "continue/s2-01.png", 14, 16);
            add(banks, assets, "continue/icon", 4, "continue/s2-02.png", 1, 0);
            add(banks, assets, "continue/icon", 5, "continue/s2-03.png", 1, 0);
            add(banks, assets, "continue/player", 14, "continue/s2-04.png", 22, 20);
            add(banks, assets, "continue/player", 15, "continue/s2-05.png", 22, 16);
            add(banks, assets, "continue/player", 16, "continue/s2-06.png", 22, 16);
            add(banks, assets, "continue/player", 17, "continue/s2-07.png", 20, 16);
            add(banks, assets, "continue/player", 18, "continue/s2-08.png", 21, 20);
            add(banks, assets, "continue/player", 19, "continue/s2-09.png", 21, 16);
            add(banks, assets, "continue/player", 20, "continue/s2-10.png", 23, 16);
            add(banks, assets, "continue/player", 21, "continue/s2-11.png", 24, 16);
            add(banks, assets, "continue/player", 46, "continue/s2-12.png", 16, 16);
            add(banks, assets, "continue/player", 47, "continue/s2-13.png", 20, 16);
            add(banks, assets, "continue/player", 48, "continue/s2-14.png", 19, 16);
            add(banks, assets, "continue/player", 49, "continue/s2-15.png", 20, 16);
        }
        if (host == CharacterPresentation.Host.S3K) {
            add(banks, assets, "continue/team-idle", 5, "continue/s3k-00.png", 16, 14);
            add(banks, assets, "continue/team-idle", 6, "continue/s3k-01.png", 15, 13);
            add(banks, assets, "continue/icon", 2, "continue/s3k-02.png", 12, 12);
            add(banks, assets, "continue/icon", 0, "continue/s3k-menu-icon-00.png", 0, 12);
            add(banks, assets, "continue/icon", 3, "continue/s3k-03.png", 12, 12);
            add(banks, assets, "continue/icon", 1, "continue/s3k-menu-icon-01.png", 0, 12);
            add(banks, assets, "continue/player", 173, "continue/s3k-04.png", 9, 15);
            add(banks, assets, "continue/player", 33, "continue/s3k-05.png", 18, 16);
            add(banks, assets, "continue/player", 34, "continue/s3k-06.png", 20, 16);
            add(banks, assets, "continue/player", 35, "continue/s3k-07.png", 18, 16);
            add(banks, assets, "continue/player", 36, "continue/s3k-08.png", 18, 16);
        }
        if (host == CharacterPresentation.Host.S3K) {
            add(banks, assets, "portrait", 5, "portrait/talon-00.png", 19, 37);
        }
        return new PresentationFrameSet(banks, PresentationImages.backing(assets, "art/talon/hud-and-screens/s3k-hud.png", 0).rgb());
    }
    private static void add(Map<String, Map<Integer, PresentationFrameSet.Pose>> banks, ModAssetRoot assets,
            String bank, int id, String file, int x, int y) throws IOException {
        banks.computeIfAbsent(bank, ignored -> new LinkedHashMap<>()).put(id,
                new PresentationFrameSet.Pose(PresentationImages.read(assets, "art/talon/" + file), x, y));
    }
}
