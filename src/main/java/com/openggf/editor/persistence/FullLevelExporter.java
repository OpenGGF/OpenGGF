package com.openggf.editor.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.game.ModKeySyntax;
import com.openggf.game.GameId;
import com.openggf.game.modzone.*;
import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import com.openggf.level.*;
import com.openggf.level.objects.ObjectSpawn;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/** Complete staged exports and in-memory playtest data using the same selected-host contract. */
public final class FullLevelExporter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(FullLevelExporter.class.getName());
    private final LevelExportStagingValidator stagingValidator;

    public FullLevelExporter(LevelExportStagingValidator stagingValidator) {
        this.stagingValidator = Objects.requireNonNull(stagingValidator, "stagingValidator");
    }

    public ExportResult export(MutableLevel level, ExportRequest request) throws IOException {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(request, "request");
        prepareHostData(level, request);
        Path target = request.outputDirectory().toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "export target parent");
        Files.createDirectories(parent);
        Path reservation = target.resolveSibling(target.getFileName() + ".export-lock");
        try { Files.createFile(reservation); }
        catch (FileAlreadyExistsException collision) {
            throw new IOException("Export target is already reserved: " + target, collision);
        }
        try {
            if (Files.exists(target)) throw new IOException("Export target already exists: " + target);
            Path staging = Files.createTempDirectory(parent, target.getFileName() + ".tmp-");
            try {
                writeDirectory(level, request, staging);
                stagingValidator.validate(staging);
                // Deliberately non-atomic and without REPLACE_EXISTING: the provider contract must
                // reject an external process that creates the target after our precheck.
                Files.move(staging, target);
                return new ExportResult(target, exportedObjects(level).size(), level.getRings().size());
            } catch (IOException | RuntimeException failure) {
                IOException cleanup = deleteTree(staging);
                if (cleanup != null) failure.addSuppressed(cleanup);
                throw failure;
            }
        } finally {
            try { Files.deleteIfExists(reservation); }
            catch (IOException cleanup) { LOG.warning("Failed to remove export reservation " + reservation + ": " + cleanup.getMessage()); }
        }
    }

    /** Rejects every lossy or out-of-contract value before a staging directory is created. */
    private void preflight(MutableLevel level, ExportRequest request) throws IOException {
        if (level.getChunksPerBlockSide() != 8 && level.getChunksPerBlockSide() != 16)
            throw new IllegalArgumentException("v1 requires an 8x8 or 16x16 block grid");
        if (level.getMap().getLayerCount() < 1 || level.getMap().getLayerCount() > 2)
            throw new IllegalArgumentException("v1 supports one or two map layers");
        if (level.getPatternCount() < 1 || level.getPatternCount() > 2048
                || level.getChunkCount() < 1 || level.getChunkCount() > 1024
                || level.getBlockCount() < 1 || level.getBlockCount() > 256
                || level.getSolidTileCount() < 1 || level.getSolidTileCount() > 256
                || level.getPaletteCount() < 1 || level.getPaletteCount() > 4)
            throw new IllegalArgumentException("level asset count exceeds ModLevelDefinition v1 limits");
        if (level.getMap().getWidth() < 1 || level.getMap().getWidth() > 4096
                || level.getMap().getHeight() < 1 || level.getMap().getHeight() > 4096
                || (long) level.getMap().getWidth() * level.getMap().getHeight() > 4_194_304L)
            throw new IllegalArgumentException("map dimensions must be positive");
        if (level.getMinX() < 0 || level.getMaxX() > 0xFFFF
                || level.getMinY() < Short.MIN_VALUE || level.getMaxY() > Short.MAX_VALUE
                || level.getMinX() > level.getMaxX() || level.getMinY() > level.getMaxY()
                || request.startX() < level.getMinX() || request.startX() > level.getMaxX()
                || request.startY() < level.getMinY() || request.startY() > level.getMaxY())
            throw new IllegalArgumentException("start must lie inside ordered level bounds");
        for (int i=0;i<level.getSolidTileCount();i++) {
            SolidTile profile=level.getSolidTile(i);
            if(profile==null || profile.heights==null || profile.widths==null
                    || profile.heights.length!=SolidTile.TILE_SIZE_IN_ROM
                    || profile.widths.length!=SolidTile.TILE_SIZE_IN_ROM)
                throw new IllegalArgumentException("collision profiles require exactly 16 heights and widths");
        }
        for (int p=0;p<level.getPatternCount();p++) for(int y=0;y<8;y++) for(int x=0;x<8;x++) {
            int pixel = Byte.toUnsignedInt(level.getPattern(p).getPixel(x,y));
            if (pixel > 15) throw new IllegalArgumentException("pattern pixels must be exact 4-bit indices");
        }
        for (int i=0;i<level.getChunkCount();i++) {
            int[] state=level.getChunk(i).saveState();
            for(int j=0;j<4;j++) {
                int word=state[j];
                if(word<0||word>0xFFFF) throw new IllegalArgumentException("chunk descriptor is not unsigned 16-bit");
                if ((word & 0x7ff) >= level.getPatternCount()) throw new IllegalArgumentException("chunk references missing pattern");
            }
            for(int j=4;j<6;j++) if(state[j]<0||state[j]>0xFFFF||state[j]>=level.getSolidTileCount())
                throw new IllegalArgumentException("chunk collision index is invalid");
        }
        for(int i=0;i<level.getBlockCount();i++) for(int word:level.getBlock(i).saveState()) {
            if(word<0||word>0xFFFF) throw new IllegalArgumentException("block descriptor is not unsigned 16-bit");
            if ((word & 0x3ff) >= level.getChunkCount()) throw new IllegalArgumentException("block references missing chunk");
        }
        for(int layer=0;layer<level.getMap().getLayerCount();layer++)
            for(int y=0;y<level.getMap().getHeight();y++) for(int x=0;x<level.getMap().getWidth();x++) {
                int value=Byte.toUnsignedInt(level.getMap().getValue(layer,x,y));
                if(value<0||value>=level.getBlockCount()) throw new IllegalArgumentException("map references missing block");
            }
        for(int p=0;p<level.getPaletteCount();p++) for(int i=0;i<16;i++) {
            Palette.Color c=level.getPalette(p).getColor(i);
            requireGenesisColor(c.r); requireGenesisColor(c.g); requireGenesisColor(c.b);
        }
        java.util.List<ObjectSpawn> objects = exportedObjects(level);
        if (objects.size() > 65_535 || level.getRings().size() > 65_535)
            throw new IllegalArgumentException("spawn count exceeds ModLevelDefinition v1 limits");
        Set<Integer> objectIds = new HashSet<>();
        for (ObjectSpawn spawn : objects) {
            if (spawn.layoutIndex() < 0 || !objectIds.add(spawn.layoutIndex())
                    || spawn.x() < 0 || spawn.x() > 0xFFFE || spawn.y() < 0 || spawn.y() > 0x0FFF
                    || spawn.objectId() < 0 || spawn.objectId() > 0xFF
                    || spawn.subtype() < 0 || spawn.subtype() > 0xFF
                    || spawn.renderFlags() < 0 || spawn.renderFlags() > 3
                    || spawn.rawYWord() != nativeRawY(spawn, request.gameId()))
                throw new IllegalArgumentException("object spawn does not match the selected native placement encoding");
            if (spawn.objectKey() != null) ModKeySyntax.requireDisplayKey(spawn.objectKey());
        }
        Set<Integer> ringIds = new HashSet<>();
        for (var ring : level.getRings()) {
            if (ring.placementId() < 0 || !ringIds.add(ring.placementId())
                    || ring.x() < 0 || ring.x() > 0xFFFF || ring.y() < 0 || ring.y() > 0xFFFF)
                throw new IllegalArgumentException("ring spawn is outside ModLevelDefinition v1 ranges");
        }
        byte[] metadataBytes = JSON.writeValueAsBytes(metadata(level, request));
        if (metadataBytes.length > (1 << 20) || request.zoneName().length() > 65_536
                || request.zoneName().getBytes(StandardCharsets.UTF_8).length > (1 << 20))
            throw new IllegalArgumentException("level metadata exceeds ModLevelDefinition v1 limits");
        com.openggf.mods.code.ModLevelDefinitionParser.validateMetadataJson(metadataBytes);
    }

    /** Creates exactly the canonical data exported to disk, without touching the filesystem. */
    public ModZoneLevelData prepareHostData(MutableLevel level, ExportRequest request) throws IOException {
        Objects.requireNonNull(level); Objects.requireNonNull(request);
        preflight(level,request);
        if(request.gameId()==GameId.S1) {
            for(int y=0;y<level.getMap().getHeight();y++) for(int x=0;x<level.getMap().getWidth();x++) {
                int block=Byte.toUnsignedInt(level.getMap().getValue(0,x,y));
                if(level.resolveCollisionBlockIndex(block,x,y)!=block)
                    throw new IllegalArgumentException("Sonic 1 native loop flag at map cell ("+x+","+y+") cannot be represented by a flat authored level; clear or replace the native template feature first");
            }
        }
        int count=level.getChunkCount(); int[] primary=new int[count],secondary=new int[count];
        for(int i=0;i<count;i++) { primary[i]=level.getChunk(i).getSolidTileIndex(); secondary[i]=level.getChunk(i).getSolidTileAltIndex(); }
        boolean s3k=request.gameId()==GameId.S3K;
        byte[][] lines=s3k ? new byte[0][] : new byte[4][32];
        if(!s3k) {
            byte[] paletteBytes=palettes(level);
            for(int i=0;i<level.getPaletteCount();i++)
                lines[i]=Arrays.copyOfRange(paletteBytes,12+i*32,12+(i+1)*32);
        }
        ModZoneLevelData data=new ModZoneLevelData(s3k?2:1,request.zoneIndex(),level.getChunksPerBlockSide(),
                level.getMap().getWidth(),level.getMap().getHeight(),level.getMinX(),level.getMaxX(),level.getMinY(),level.getMaxY(),
                raw(patterns(level),12),raw(chunks(level),12),raw(blocks(level),12),raw(map(level,0),16),
                level.getMap().getLayerCount()>1?raw(map(level,1),16):null,
                raw(profiles(level,true),12),raw(profiles(level,false),12),raw(angles(level),12),primary,secondary,
                lines,s3k?Optional.of(new ModZoneHostMetadata(request.objectZoneSet())):Optional.empty(),
                s3k?paletteClaims(level):List.of(),canonicalObjects(level),level.getRings(),
                level.getPatternCount(),count,level.getBlockCount());
        if(request.gameId()!=null) {
            ModZoneAdapter adapter=switch(request.gameId()) {
                case S1 -> new Sonic1GameModule().getModZoneAdapter();
                case S2 -> new Sonic2GameModule().getModZoneAdapter();
                case S3K -> new Sonic3kGameModule().getModZoneAdapter();
                case STANDALONE -> throw new IllegalArgumentException("Standalone exports use the generic v1 contract, not a stock game host");
            };
            adapter.validate("editor",data);
        }
        stagingValidator.validateHostData(data);
        return data;
    }

    private static byte[] raw(byte[] bytes,int header) { return Arrays.copyOfRange(bytes,header,bytes.length); }

    private static int nativeRawY(ObjectSpawn spawn, GameId game) {
        return game==GameId.S1 ? spawn.y() | (spawn.renderFlags()<<14)
                : spawn.y() | (spawn.renderFlags()<<13) | (spawn.respawnTracked()?0x8000:0);
    }

    private static List<ObjectSpawn> canonicalObjects(MutableLevel level) {
        var codec=new CommonObjectPlacementEncoding();
        return exportedObjects(level).stream().map(spawn -> codec.move(spawn,spawn.x(),spawn.y())).toList();
    }

    /** Claims only colors reachable from the authored layouts; unused ROM library art has no owner. */
    private static List<ModPaletteClaim> paletteClaims(MutableLevel level) {
        boolean[][] used=new boolean[4][16];
        for(int layer=0;layer<level.getMap().getLayerCount();layer++)
            for(int y=0;y<level.getMap().getHeight();y++) for(int x=0;x<level.getMap().getWidth();x++) {
                Block block=level.getBlock(Byte.toUnsignedInt(level.getMap().getValue(layer,x,y)));
                for(int cy=0;cy<block.getGridSide();cy++) for(int cx=0;cx<block.getGridSide();cx++) {
                    Chunk chunk=level.getChunk(block.getChunkDesc(cx,cy).getChunkIndex());
                    for(int py=0;py<2;py++) for(int px=0;px<2;px++) {
                        PatternDesc desc=chunk.getPatternDesc(px,py);
                        Pattern pattern=level.getPattern(desc.getPatternIndex());
                        for(int iy=0;iy<8;iy++) for(int ix=0;ix<8;ix++) {
                            int color=Byte.toUnsignedInt(pattern.getPixel(ix,iy));
                            if(color==0) used[2][0]=true;
                            else if(desc.getPaletteIndex()==0) throw new IllegalArgumentException(
                                    "S3K reachable art uses host-owned character palette line 0, color "+color+"; choose a creator palette line before playtest/export");
                            else used[desc.getPaletteIndex()][color]=true;
                        }
                    }
                }
            }
        List<ModPaletteClaim> claims=new ArrayList<>();
        for(int line=1;line<4;line++) for(int color=0;color<16;color++) if(used[line][color]) {
            if(line>=level.getPaletteCount()) throw new IllegalArgumentException("Missing reachable palette line "+line);
            Palette.Color c=level.getPalette(line).getColor(color);
            int r=(Byte.toUnsignedInt(c.r)*7+127)/255,g=(Byte.toUnsignedInt(c.g)*7+127)/255,b=(Byte.toUnsignedInt(c.b)*7+127)/255;
            claims.add(new ModPaletteClaim(line,color,(b<<9)|(g<<5)|(r<<1)));
        }
        return List.copyOf(claims);
    }

    private static void requireGenesisColor(byte component) {
        int value=Byte.toUnsignedInt(component), quantized=(value*7+127)/255;
        if ((quantized*255+3)/7 != value) throw new IllegalArgumentException("palette color is not exactly Genesis-representable");
    }

    private void writeDirectory(MutableLevel level, ExportRequest request, Path root) throws IOException {
        write(root, "patterns.bin", patterns(level));
        write(root, "chunks.bin", chunks(level));
        write(root, "blocks.bin", blocks(level));
        write(root, "fg-map.bin", map(level, 0));
        if (level.getMap().getLayerCount() > 1) write(root, "bg-map.bin", map(level, 1));
        write(root, "solid-heights.bin", profiles(level, true));
        write(root, "solid-widths.bin", profiles(level, false));
        write(root, "solid-angles.bin", angles(level));
        write(root, "collision-primary.bin", collisions(level, false));
        write(root, "collision-secondary.bin", collisions(level, true));
        if (request.gameId()!=GameId.S3K) write(root, "palettes.bin", palettes(level));
        Files.write(root.resolve("level.json"), JSON.writeValueAsBytes(metadata(level, request)));
    }

    private ObjectNode metadata(MutableLevel level, ExportRequest r) {
        ObjectNode root = JSON.createObjectNode();
        root.put("formatVersion", r.gameId()==GameId.S3K ? 2 : 1); root.put("zoneName", r.zoneName());
        root.put("zoneIndex", r.zoneIndex()); root.put("levelIndex", r.levelIndex());
        root.put("blockGridSide", level.getChunksPerBlockSide());
        root.put("width", level.getMap().getWidth()); root.put("height", level.getMap().getHeight());
        ObjectNode bounds = root.putObject("bounds"); bounds.put("minX", level.getMinX()); bounds.put("maxX", level.getMaxX());
        bounds.put("minY", level.getMinY()); bounds.put("maxY", level.getMaxY());
        ObjectNode start = root.putObject("start"); start.put("x", r.startX()); start.put("y", r.startY());
        ObjectNode music = root.putObject("music");
        if (r.music() instanceof ExportMusic.Stock stock) music.put("stockId", stock.id());
        else { ExportMusic.Track track = (ExportMusic.Track) r.music(); ObjectNode key = music.putObject("trackKey");
            key.put("modId", track.owner()); key.put("name", track.localName()); }
        ObjectNode assets = root.putObject("assets");
        assets.put("patterns", "patterns.bin"); assets.put("chunks", "chunks.bin"); assets.put("blocks", "blocks.bin");
        assets.put("foregroundMap", "fg-map.bin"); if (level.getMap().getLayerCount() > 1) assets.put("backgroundMap", "bg-map.bin");
        assets.put("solidHeights", "solid-heights.bin"); assets.put("solidWidths", "solid-widths.bin");
        assets.put("solidAngles", "solid-angles.bin"); assets.put("collisionPrimary", "collision-primary.bin");
        assets.put("collisionSecondary", "collision-secondary.bin");
        if(r.gameId()!=GameId.S3K) assets.put("palettes", "palettes.bin");
        else {
            root.putObject("hostMetadata").putObject("s3k").put("objectZoneSet", r.objectZoneSet().name());
            ArrayNode claims=root.putArray("paletteClaims");
            for(ModPaletteClaim claim:paletteClaims(level)) {
                ObjectNode node=claims.addObject(); node.put("line",claim.line());
                node.put("color",claim.color()); node.put("sega",claim.segaColor());
            }
        }
        ArrayNode objects = root.putArray("objects");
        for (ObjectSpawn spawn : canonicalObjects(level)) {
            ObjectNode node = objects.addObject(); node.put("placementId", spawn.layoutIndex()); node.put("x", spawn.x()); node.put("y", spawn.y());
            if (spawn.objectKey() == null) node.put("stockObjectId", spawn.objectId()); else node.put("objectKey", spawn.objectKey());
            node.put("subtype", spawn.subtype()); node.put("renderFlags", spawn.renderFlags());
            node.put("respawnTracked", spawn.respawnTracked()); node.put("rawYWord", spawn.rawYWord());
        }
        ArrayNode rings = root.putArray("rings"); level.getRings().forEach(ring -> {
            ObjectNode node = rings.addObject(); node.put("placementId", ring.placementId()); node.put("x", ring.x()); node.put("y", ring.y()); });
        return root;
    }

    /**
     * Ring backing objects are a stock-ROM storage detail. The canonical export owns rings as
     * first-class entries, so backing objects represented by the level's explicit mapping are omitted.
     */
    private static java.util.List<ObjectSpawn> exportedObjects(MutableLevel level) {
        Set<ObjectSpawn> ringBacking = new HashSet<>(level.ringObjectPlacementMapping().keySet());
        return level.getObjects().stream().filter(spawn -> !ringBacking.contains(spawn)).toList();
    }

    private byte[] patterns(MutableLevel level) throws IOException { return binary(out -> {
        out.writeBytes("GPTN"); out.writeShort(1); out.writeShort(32); out.writeInt(level.getPatternCount());
        for (int p=0;p<level.getPatternCount();p++) for(int y=0;y<8;y++) for(int x=0;x<8;x+=2)
            out.writeByte(((level.getPattern(p).getPixel(x,y)&15)<<4)|(level.getPattern(p).getPixel(x+1,y)&15)); }); }
    private byte[] chunks(MutableLevel level) throws IOException { return binary(out -> {
        out.writeBytes("GCHK"); out.writeShort(1); out.writeShort(8); out.writeInt(level.getChunkCount());
        for(int i=0;i<level.getChunkCount();i++){ int[] s=level.getChunk(i).saveState(); for(int j=0;j<4;j++) out.writeShort(s[j]); }}); }
    private byte[] blocks(MutableLevel level) throws IOException { return binary(out -> {
        int side=level.getChunksPerBlockSide(); out.writeBytes("GBLK"); out.writeShort(1); out.writeByte(side); out.writeByte(0); out.writeInt(level.getBlockCount());
        for(int i=0;i<level.getBlockCount();i++) for(int v:level.getBlock(i).saveState()) out.writeShort(v); }); }
    private byte[] map(MutableLevel level,int layer) throws IOException { return binary(out -> {
        int w=level.getMap().getWidth(),h=level.getMap().getHeight(); out.writeBytes("GMAP"); out.writeShort(1); out.writeShort(w); out.writeShort(h); out.writeShort(1); out.writeInt(w*h);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) out.writeByte(level.getMap().getValue(layer,x,y)); }); }
    private byte[] profiles(MutableLevel level,boolean heights) throws IOException { return binary(out -> {
        out.writeBytes(heights?"GSHG":"GSWD"); out.writeShort(1); out.writeShort(16); out.writeInt(level.getSolidTileCount());
        for(int i=0;i<level.getSolidTileCount();i++) out.write(heights?level.getSolidTile(i).heights:level.getSolidTile(i).widths); }); }
    private byte[] angles(MutableLevel level) throws IOException { return binary(out -> { out.writeBytes("GSAN"); out.writeShort(1); out.writeShort(1); out.writeInt(level.getSolidTileCount()); for(int i=0;i<level.getSolidTileCount();i++) out.writeByte(level.getSolidTile(i).getAngle()); }); }
    private byte[] collisions(MutableLevel level,boolean secondary) throws IOException { return binary(out -> { out.writeBytes("GCOL"); out.writeShort(1); out.writeByte(secondary?1:0); out.writeByte(2); out.writeInt(level.getChunkCount()); for(int i=0;i<level.getChunkCount();i++) out.writeShort(secondary?level.getChunk(i).getSolidTileAltIndex():level.getChunk(i).getSolidTileIndex()); }); }
    private byte[] palettes(MutableLevel level) throws IOException { return binary(out -> { int count=Math.min(4,level.getPaletteCount()); out.writeBytes("GPAL"); out.writeShort(1); out.writeShort(count); out.writeShort(16); out.writeShort(0); for(int p=0;p<count;p++) for(int i=0;i<16;i++){ Palette.Color c=level.getPalette(p).getColor(i); int r=((Byte.toUnsignedInt(c.r)*7+127)/255),g=((Byte.toUnsignedInt(c.g)*7+127)/255),b=((Byte.toUnsignedInt(c.b)*7+127)/255); out.writeShort((b<<9)|(g<<5)|(r<<1)); }}); }
    private void write(Path root,String name,byte[] bytes)throws IOException{Files.write(root.resolve(name),bytes);}
    private byte[] binary(IoWriter writer)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){writer.write(out);}return bytes.toByteArray();}
    private IOException deleteTree(Path root){
        IOException failure=null;
        try(var paths=Files.walk(root)){
            for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) try{Files.deleteIfExists(path);}
            catch(IOException error){if(failure==null)failure=new IOException("Failed to clean export staging " + root);failure.addSuppressed(error);}
        }catch(IOException error){failure=error;}
        return failure;
    }

    public record ExportRequest(Path outputDirectory, String zoneName, int zoneIndex, int levelIndex,
                                int startX, int startY, ExportMusic music,
                                GameId gameId, ModObjectZoneSet objectZoneSet) {
        /** Legacy generic v1 export; new editor projects always select an explicit host. */
        public ExportRequest(Path outputDirectory,String zoneName,int zoneIndex,int levelIndex,
                             int startX,int startY,ExportMusic music) {
            this(outputDirectory,zoneName,zoneIndex,levelIndex,startX,startY,music,null,ModObjectZoneSet.S3KL);
        }
        public ExportRequest {
            Objects.requireNonNull(outputDirectory); Objects.requireNonNull(zoneName); Objects.requireNonNull(music);
            if (gameId == GameId.S3K) Objects.requireNonNull(objectZoneSet, "S3K object family");
            if(zoneName.isBlank() || zoneIndex<0x40 || levelIndex<0x400)
                throw new IllegalArgumentException("nonblank name and reserved mod ids required");
        }
    }
    public sealed interface ExportMusic permits ExportMusic.Stock,ExportMusic.Track{
        record Stock(int id)implements ExportMusic{public Stock{if(id<0||id>0xFFFF)throw new IllegalArgumentException("stock music id must be unsigned 16-bit");}}
        record Track(String owner,String localName)implements ExportMusic{public Track{ModKeySyntax.requireOwnedKey(owner,localName);}}
    }
    public record ExportResult(Path directory,int objectCount,int ringCount){}
    @FunctionalInterface private interface IoWriter{void write(DataOutputStream out)throws IOException;}
}
