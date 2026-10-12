package starpost.realruins;

import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import com.openggf.game.RomDetectionService;
import com.openggf.game.patch.*;
import com.openggf.level.Level;
import java.io.IOException;
import java.util.*;
import starpost.realvalley.S1Terrain;
import starpost.realvalley.S1TerrainReader;

/** Regenerates the selected chamber on every load; ROM bytes never enter the jar. */
public final class RuinsPatch implements GamePatch {
    private final RuinsSession session;
    public RuinsPatch(RuinsSession session) { this.session=session; }
    public String id() { return "real-ruins"; }
    public String displayName() { return "Starpost Valley: Marble Ruins"; }
    public String baseGameId() { return "s3k"; }
    public boolean activatesFor(GameplayLaunchRequest request) { return "s3k".equals(request.gameId()); }
    public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.S1); }
    public List<String> providedMainCharacters() { return List.of(); }
    public GameModule apply(GameModule base, PatchContext context) {
        return new DelegatingGameModule(base, "starpost-valley:real-ruins") {
            private final Map<Integer,S1Terrain> terrain=new HashMap<>();
            public Level loadLevelOverride(int index) throws IOException {
                var contribution=getZoneRegistry().modZoneRuntimeContribution(index);
                if (contribution==null || !"starpost-valley".equals(contribution.ownerModId())
                        || !"ruins".equals(contribution.localKey())) return super.loadLevelOverride(index);
                if (!session.active()) throw new IOException("Ruins act requires its retained scene session");
                var chamber=session.regenerate();
                int sourceIndex=(chamber.zone==1?0x86:chamber.zone==3?0x83:0x8F)+chamber.act;
                S1Terrain source=terrain.get(sourceIndex);
                if (source==null) {
                    if (context==null) throw new IOException("Ruins requires the logical-ROM context");
                    try (var rom=Rom.fromReader(context.openLogicalRom(LogicalRom.S1),"Starpost Ruins S1")) {
                        var game=RomDetectionService.getInstance().detectAndCreateModule(rom).orElseThrow().createGame(rom);
                        source=S1TerrainReader.read(game.loadLevel(sourceIndex));
                        terrain.put(sourceIndex,source);
                    }
                }
                return base().getModZoneAdapter().load("starpost-valley",RuinsLevel.build(contribution.levelData(),source,chamber,session));
            }
        };
    }
}
