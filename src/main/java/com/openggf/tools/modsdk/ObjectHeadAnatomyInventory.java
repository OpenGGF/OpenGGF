package com.openggf.tools.modsdk;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.Sonic1ObjectArtProvider;
import com.openggf.game.sonic2.Sonic2ObjectArt;
import com.openggf.game.sonic3k.Sonic3kObjectArt;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.render.PlayerHeadProfile;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** CPU-only native object anatomy inventory. Origin: Big Head polish, 2026-10-11.
 * Original ROM decoders provide every mapping/pixel. Indexed colours are diagnostic;
 * no GL, display, input, audio device, gameplay stepping or admission override is used. */
final class ObjectHeadAnatomyInventory {
    private ObjectHeadAnatomyInventory() { }

    static int run(String[] args, PrintStream output) throws Exception {
        if (args.length != 3)
            throw new IllegalArgumentException("object-head-inventory <rom> <s1|s2|s3k> <out-directory>");
        Rom rom = new Rom();
        rom.open(args[0]);
        GameServices.rom().setRom(rom);
        var graphics = com.openggf.game.session.EngineServices.current().graphics();
        graphics.initHeadless();
        Path out = Path.of(args[2]);
        Files.createDirectories(out);
        Map<String, ObjectSpriteSheet> sheets = new LinkedHashMap<>();
        switch (args[1]) {
            case "s1" -> {
                var provider = new Sonic1ObjectArtProvider();
                for (int zone = 0; zone < 6; zone++) {
                    provider.loadArtForZone(zone);
                    for (String key : provider.getRendererKeys())
                        sheets.put("z" + zone + "-" + key, provider.getSheet(key));
                }
            }
            case "s2" -> sonic2(new Sonic2ObjectArt(rom, RomByteReader.fromRom(rom)), sheets);
            case "s3k" -> {
                var art = new Sonic3kObjectArt(null, RomByteReader.fromRom(rom));
                for (int zone = 0; zone < 14; zone++) for (int act = 0; act < 2; act++)
                    for (var entry : Sonic3kPlcArtRegistry.getPlan(zone, act).standaloneArt()) {
                        String key = "z" + zone + "a" + act + "-" + entry.key();
                        sheets.put(key, art.loadStandaloneSheet(rom, entry));
                    }
            }
            default -> throw new IllegalArgumentException("Unknown native game");
        }
        var identities = new java.util.HashSet<String>();
        for (var entry : sheets.entrySet()) {
            var sheet = entry.getValue();
            if (sheet == null) continue;
            String identity = PlayerHeadProfile.fingerprint(sheet);
            if (identity.isEmpty() || !identities.add(identity)) continue;
            var renderer = new PatternSpriteRenderer(sheet, graphics);
            renderer.ensurePatternsCached(graphics, 0x10000);
            StringBuilder json = new StringBuilder("{\"fingerprint\":\"")
                    .append(identity).append("\",\"palette\":[");
            // A fixed index palette makes transparent/edge ownership readable without
            // pretending these are a loaded zone's cycling native palette colours.
            for (int i = 0; i < 16; i++) {
                if (i > 0) json.append(',');
                json.append(0xFF000000 | (i * 71 & 255) << 16 | (i * 43 & 255) << 8 | (i * 109 & 255));
            }
            json.append("],\"frames\":[");
            for (int frame = 0; frame < sheet.getFrameCount(); frame++) {
                if (frame > 0) json.append(',');
                int index = frame;
                var prepared = SpritePresentationRenderer.prepare(graphics, 0, 0,
                        () -> renderer.drawFrameIndex(index, 0, 0, false, false));
                json.append("{\"index\":").append(frame).append(",\"pieces\":")
                        .append(sheet.getFrame(frame).pieces().size()).append(",\"tiles\":[");
                boolean comma = false;
                for (var tile : prepared.tiles()) {
                    if (comma) json.append(',');
                    comma = true;
                    var pattern = sheet.getPatterns()[tile.patternId() - 0x10000];
                    json.append("{\"x\":").append((int) tile.x()).append(",\"y\":").append((int) tile.y())
                            .append(",\"pixels\":\"");
                    for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                        json.append(Character.forDigit(pattern.getPixel(tile.hFlip() ? 7-x : x,
                                tile.vFlip() ? 7-y : y) & 15, 16));
                    json.append("\"}");
                }
                json.append("]}");
            }
            json.append("]}");
            String name = args[1] + "-" + entry.getKey();
            Files.writeString(out.resolve(name + ".json"), json);
            if(PlayerHeadProfile.resolve(sheet)!=null)
                writeReview(sheet,renderer,graphics,out.resolve(name+"-review.png"));
            output.println(name + ": " + sheet.getFrameCount() + " frames; " + identity);
        }
        return 0;
    }

    private static void writeReview(ObjectSpriteSheet sheet,PatternSpriteRenderer renderer,
                                    com.openggf.graphics.GraphicsManager graphics,Path out) throws Exception {
        int cell=256,columns=Math.min(4,sheet.getFrameCount()),rows=(sheet.getFrameCount()+columns-1)/columns;
        var image=com.openggf.io.PixelImage.blank(columns*cell*2,rows*(cell+12));
        int[] palette=new int[64];
        for(int i=0;i<64;i++) palette[i]=0xFF000000|((i&15)*71&255)<<16|((i&15)*43&255)<<8|((i&15)*109&255);
        for(int f=0;f<sheet.getFrameCount();f++) {
            int frame=f,x=f%columns*cell*2,y=f/columns*(cell+12)+12;
            var stock=SpritePresentationRenderer.prepare(graphics,0,0,
                    ()->renderer.drawFrameIndex(frame,cell/2,cell/2,false,false));
            var head=SpritePresentationRenderer.prepare(graphics,0,0,
                    ()->com.openggf.graphics.SpritePresentation.withSubject(graphics,
                            new com.openggf.graphics.SpritePresentation.Subject("object-review",
                                    com.openggf.graphics.SpritePresentation.Part.OBJECT_BODY,false,200),
                            ()->renderer.drawFrameIndex(frame,cell/2,cell/2,false,false)));
            SpriteSheetDump.digits(image,f,x+2,y-10);
            PlayerHeadMaskReview.paint(image,freezeStaticArt(stock,sheet),palette,x,y,cell,t->true);
            PlayerHeadMaskReview.paint(image,freezeStaticArt(head,sheet),palette,x+cell,y,cell,t->true);
        }
        com.openggf.io.PngCodec.write(out,image);
    }
    private static com.openggf.graphics.SpritePresentation.Frame freezeStaticArt(
            com.openggf.graphics.SpritePresentation.Frame frame,ObjectSpriteSheet sheet) {
        var versions=new java.util.HashMap<>(frame.patternVersions());
        for(var tile:frame.tiles()) {
            int index=tile.patternId()-0x10000;
            if(index>=0&&index<sheet.getPatterns().length)
                versions.putIfAbsent(tile.patternId(),SpritePresentationRenderer.version(sheet.getPatterns()[index]));
        }
        return new com.openggf.graphics.SpritePresentation.Frame(frame.tiles(),frame.primitives(),versions);
    }

    private static void sonic2(Sonic2ObjectArt art, Map<String, ObjectSpriteSheet> sheets) {
        sheets.put("buzzer", art.loadBuzzerSheet());
        sheets.put("masher", art.loadMasherSheet());
        sheets.put("coconuts", art.loadCoconutsSheet());
        sheets.put("chopchop", art.loadChopChopSheet());
        sheets.put("whisp", art.loadWhispSheet());
        sheets.put("grounder", art.loadGrounderSheet());
        sheets.put("spiker", art.loadSpikerSheet());
        sheets.put("sol", art.loadSolSheet());
        sheets.put("rexon", art.loadRexonSheet());
        sheets.put("crawl", art.loadCrawlSheet());
        sheets.put("nebula", art.loadNebulaSheet());
        sheets.put("turtloid", art.loadTurtloidSheet());
        sheets.put("balkiry", art.loadBalkirySheet());
        sheets.put("clucker", art.loadCluckerSheet());
        sheets.put("octus", art.loadOctusSheet());
        sheets.put("aquis", art.loadAquisSheet());
        sheets.put("shellcracker", art.loadShellcrackerSheet());
        sheets.put("slicer", art.loadSlicerSheet());
        sheets.put("spiny", art.loadSpinySheet());
        sheets.put("grabber", art.loadGrabberSheet());
        sheets.put("boss-ehz", art.loadEHZBossSheet());
        sheets.put("boss-cpz", art.loadCPZBossEggpodSheet());
        sheets.put("boss-arz", art.loadARZBossMainSheet());
        sheets.put("boss-cnz", art.loadCNZBossSheet());
        sheets.put("boss-htz", art.loadHTZBossSheet());
        sheets.put("boss-mcz", art.loadMCZBossSheet());
        sheets.put("boss-mtz", art.loadMTZBossSheet());
        sheets.put("boss-ooz", art.loadOOZBossSheet());
        sheets.put("boss-silver-sonic", art.loadSilverSonicSheet());
        sheets.put("boss-dez", art.loadDEZBossSheet());
        sheets.put("boss-eggman", art.loadDEZEggmanSheet());
        sheets.put("boss-wfz", art.loadWFZBossSheet());
        sheets.put("boss-wfz-robotnik", art.loadWFZRobotnikSheet());
    }
}
