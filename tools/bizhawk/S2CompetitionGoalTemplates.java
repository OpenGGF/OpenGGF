// Purpose: export ROM-backed AniPLC observation templates and finish positions; no input authoring.
// Inputs: verified S2 REV01 ROM. Origin: lane P final native evidence gate, 2026-11-08.
import com.openggf.data.*;
import com.openggf.level.animation.*;
import com.openggf.game.sonic2.*;
import com.openggf.game.sonic2.competition.*;
import java.nio.file.*;
import java.util.*;
class S2CompetitionGoalTemplates {
 public static void main(String[] args) throws Exception {
  if(!PhysicalImage.probe(Path.of(args[0])).fingerprint().sha1Hex().equalsIgnoreCase("8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9"))
   throw new IllegalArgumentException("S2 World REV01 required");
  try(var rom=new Rom()) {
   if(!rom.open(args[0]))throw new IllegalStateException("ROM");
   var reader=RomByteReader.fromRom(rom);
   var scripts=AniPlcParser.parseScripts(reader,Sonic2RomAddresses.address(reader,0x40160));
   var out=new StringBuilder("return {\n");
   for(var s:scripts) {
    out.append("{address=").append(s.destinationTileIndex()*32).append(",frames={");
    for(int id:s.frameTileIdsForTesting()) out.append("\"").append(HexFormat.of().formatHex(s.copyFramePayload(id))).append("\",");
    out.append("}},\n");
   }
   Files.writeString(Path.of(args[1]),out.append("}\n").toString(),StandardOpenOption.CREATE_NEW);
   for(var p:Sonic2CompetitionLevelProfile.loadAll(rom))System.out.println("zone="+p.romZone()+" act="+p.act()+" bounds="+p.bounds()+" signposts="+p.signposts());
  }
 }
}
