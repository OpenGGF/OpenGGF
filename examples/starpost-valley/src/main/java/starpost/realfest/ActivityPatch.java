package starpost.realfest;

import com.openggf.data.Rom;
import com.openggf.game.*;
import com.openggf.game.patch.*;
import com.openggf.level.Level;
import java.io.IOException;
import java.util.*;
import starpost.realvalley.*;

/** Re-encode Green Hill at load; the jar contains no donor asset bytes. */
public final class ActivityPatch implements GamePatch {
    private final ActivitySession session;
    private final ActSeasons seasons;
    public ActivityPatch(ActivitySession session) { this(session,new ActSeasons()); }
    public ActivityPatch(ActivitySession session,ActSeasons seasons) { this.session=session; this.seasons=seasons; }
    public String id() { return "real-festivals"; }
    public String displayName() { return "Starpost Valley: festivals and lake"; }
    public String baseGameId() { return "s3k"; }
    public boolean activatesFor(GameplayLaunchRequest request) { return "s3k".equals(request.gameId()); }
    public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.S1); }
    public List<String> providedMainCharacters() { return List.of(); }
    public GameModule apply(GameModule base,PatchContext context) {
        return new DelegatingGameModule(base,"starpost-valley:real-festivals") {
            private S1Terrain terrain;
            public Level loadLevelOverride(int index) throws IOException {
                var contribution=getZoneRegistry().modZoneRuntimeContribution(index);
                if(contribution==null || !"starpost-valley".equals(contribution.ownerModId()) ||
                    !List.of("race","hunt","snowboard","lake").contains(contribution.localKey())) return super.loadLevelOverride(index);
                if(!session.active() || !session.kind().equals(contribution.localKey())) throw new IOException("Activity needs its retained scene session");
                if(terrain==null) try(var rom=Rom.fromReader(context.openLogicalRom(LogicalRom.S1),"Starpost activities S1")) {
                    var module=RomDetectionService.getInstance().detectAndCreateModule(rom).orElseThrow();
                    terrain=S1TerrainReader.read(module.createGame(rom).loadLevel(0x80));
                }
                var data=CourseLevel.build(contribution.levelData(),terrain,session);
                seasons.remember(contribution.localKey(),data.paletteClaims());
                return base().getModZoneAdapter().load("starpost-valley",data);
            }
        };
    }
}
