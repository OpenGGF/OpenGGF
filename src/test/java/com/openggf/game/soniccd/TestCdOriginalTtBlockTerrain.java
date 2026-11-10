package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Pure geometry/source fence only. Equal DATA does not issue a World/Frame invocation. */
class TestCdOriginalTtBlockTerrain {
 @Test void actualUnchangedTerrainPassesWhileChangedBlocksOrLayoutAndForeignReaderReject()throws Exception {
  var root=System.getProperty("soniccd.media.root","");var cue=Path.of(root,"USA","Sonic CD (US).cue");assumeTrue(!root.isBlank()&&Files.isRegularFile(cue),"Actual USA source");
  try(var disc=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
   var load=CdProgrammeData.open(disc,CdR4DataLayout.Programme.R41A).levelLoadingSource();var foreign=CdProgrammeData.open(disc,CdR4DataLayout.Programme.R41A).levelLoadingSource();var player=load.retainedPlayerSource();var terrain=player.terrain();var blocks=terrain.blocks();var layout=terrain.layout().ram();
   assertDoesNotThrow(()->CdWorld.requireOriginalTtBlockTerrain(player,load,blocks,layout));
   var dirty=blocks.copy();dirty[0]^=1;var changed=CdAssetBytes.copyOf(dirty);assertThrows(IllegalArgumentException.class,()->CdWorld.requireOriginalTtBlockTerrain(player,load,changed,layout));
   var raw=layout.copy();raw[0]^=1;var changedLayout=CdAssetBytes.copyOf(raw);assertThrows(IllegalArgumentException.class,()->CdWorld.requireOriginalTtBlockTerrain(player,load,blocks,changedLayout));assertThrows(IllegalArgumentException.class,()->CdWorld.requireOriginalTtBlockTerrain(player,foreign,blocks,layout));
  }
 }
}
