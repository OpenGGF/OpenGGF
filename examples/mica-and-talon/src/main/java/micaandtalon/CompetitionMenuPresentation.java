package micaandtalon;

import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;
import java.io.IOException;
import java.util.Map;

/** CharacterPresentation.CompetitionPortrait for both characters. Supplies independent player and record images with native foot-plane origins; the host owns roster and results. */
final class CompetitionMenuPresentation {
    static Map<CharacterPresentation.CompetitionSurface,CharacterPresentation.CompetitionPortrait> load(
            ModAssetRoot assets, String character) throws IOException {
        var player=PresentationImages.backing(assets,"art/"+character+"/competition-menu/player.png",0);
        var record=PresentationImages.backing(assets,"art/"+character+"/competition-menu/record.png",0);
        return Map.of(CharacterPresentation.CompetitionSurface.S3K_PLAYER,
                new CharacterPresentation.CompetitionPortrait(new PresentationFrameSet.Pose(player.image(),
                        player.image().width()/2,player.image().height()),player.rgb()),
                CharacterPresentation.CompetitionSurface.S3K_RECORD,
                new CharacterPresentation.CompetitionPortrait(new PresentationFrameSet.Pose(record.image(),
                        character.equals("mica")?11:8,record.image().height()),record.rgb()));
    }
}
