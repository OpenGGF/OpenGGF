package com.openggf.tools.modsdk;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.render.PlayerHeadProfile;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** CPU-only ROM anatomy inventory. Origin: Big Head polish, 2026-10-11.
 * Inputs are an original ROM and native game; output carries mapping coordinates and indexed
 * source tiles for reproducible lasso review. No window, input device, audio or GL boot. */
final class HeadAnatomyInventory {
    private HeadAnatomyInventory() { }
    static int run(String[] args, PrintStream output) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("head-inventory <rom> <s1|s2|s3k> <out-directory>");
        Rom rom = new Rom(); rom.open(args[0]);
        PlayerSpriteArtProvider provider = switch (args[1]) {
            case "s1" -> new com.openggf.game.sonic1.Sonic1(rom);
            case "s2" -> new com.openggf.game.sonic2.Sonic2(rom);
            case "s3k" -> new com.openggf.game.sonic3k.Sonic3k(rom);
            default -> throw new IllegalArgumentException("Unknown native game");
        };
        Path out = Path.of(args[2]); Files.createDirectories(out);
        GraphicsManager graphics = com.openggf.game.session.EngineServices.current().graphics();
        graphics.initHeadless();
        for (String character : java.util.List.of("sonic", "tails", "knuckles", "super-sonic")) {
            if (character.equals("super-sonic") && !args[1].equals("s3k")) continue;
            var palette = provider.loadCharacterPalette(character.equals("super-sonic") ? "sonic" : character);
            if (palette == null) continue;
            var art = character.equals("super-sonic")
                    ? new com.openggf.game.sonic3k.Sonic3kPlayerArt(com.openggf.data.RomByteReader.fromRom(rom)).loadSuperSonicArtSet()
                    : provider.loadPlayerSpriteArt(character);
            if (art == null) continue;
            String name = args[1] + "-" + character;
            int[] colours = new int[64];
            for (int i=0;i<16;i++) {
                var c=palette.getColor(i);
                colours[i]=0xFF000000|(c.r&255)<<16|(c.g&255)<<8|c.b&255;
            }
            var renderer = new PlayerSpriteRenderer(art, graphics);
            StringBuilder json = new StringBuilder("{\"fingerprint\":\"")
                    .append(PlayerHeadProfile.fingerprint(art)).append("\",\"palette\":[");
            for (int i=0;i<16;i++) { if(i>0) json.append(','); json.append(colours[i]); }
            json.append("],\"frames\":[");
            for (int frame=0;frame<art.mappingFrames().size();frame++) {
                if(frame>0) json.append(',');
                int index=frame;
                var nativeFrame=SpritePresentationRenderer.prepare(graphics,0,0,
                        ()->renderer.drawFrame(index,0,0,false,false));
                var pieces = art.mappingFrames().get(frame).pieces();
                json.append("{\"index\":").append(frame).append(",\"pieces\":")
                        .append(pieces.size()).append(",\"mapping\":[");
                for (int piece = 0; piece < pieces.size(); piece++) {
                    if (piece > 0) json.append(',');
                    var mapping = pieces.get(piece);
                    json.append("{\"x\":").append(mapping.xOffset()).append(",\"y\":").append(mapping.yOffset())
                            .append(",\"w\":").append(mapping.widthTiles() * 8)
                            .append(",\"h\":").append(mapping.heightTiles() * 8).append('}');
                }
                json.append("],\"tiles\":[");
                boolean comma=false;
                int piece = 0, tileInPiece = 0;
                for(var tile:nativeFrame.tiles()) {
                    while (piece < pieces.size() - 1 && tileInPiece >= pieces.get(piece).widthTiles() * pieces.get(piece).heightTiles()) {
                        tileInPiece -= pieces.get(piece).widthTiles() * pieces.get(piece).heightTiles();
                        piece++;
                    }
                    if(comma) json.append(','); comma=true;
                    var pattern=SpritePresentationRenderer.pattern(nativeFrame.patternVersions().get(tile.patternId()));
                    json.append("{\"piece\":").append(piece).append(",\"x\":").append((int)tile.x()).append(",\"y\":").append((int)tile.y())
                            .append(",\"pixels\":\"");
                    for(int y=0;y<8;y++) for(int x=0;x<8;x++)
                        json.append(Character.forDigit(pattern.getPixel(tile.hFlip()?7-x:x,tile.vFlip()?7-y:y)&15,16));
                    json.append("\"}");
                    tileInPiece++;
                }
                json.append("]}");
            }
            json.append("],\"animations\":[");
            boolean animationComma=false;
            if (art.animationSet()!=null) for(var entry:new java.util.TreeMap<>(art.animationSet().getAllScripts()).entrySet()) {
                if(animationComma) json.append(','); animationComma=true;
                var script=entry.getValue();
                json.append("{\"id\":").append(entry.getKey()).append(",\"delay\":").append(script.delay())
                        .append(",\"frames\":").append(script.frames()).append('}');
                var frames=script.frames().stream().filter(f->f>=0&&f<art.mappingFrames().size()).toList();
                if(!frames.isEmpty()) PlayerHeadMaskReview.writeSequence(art,colours,
                        out.resolve(name+"-animation-"+String.format("%02X",entry.getKey())+".png"),frames,200,
                        new PrintStream(java.io.OutputStream.nullOutputStream()));
            }
            json.append("]}");
            Files.writeString(out.resolve(name+".json"),json);
            for(int first=0;first<art.mappingFrames().size();first+=16)
                PlayerHeadMaskReview.write(art,colours,out.resolve(name+"-"+String.format("%02X",first)+".png"),
                        first,16,200,new PrintStream(java.io.OutputStream.nullOutputStream()));
            output.println(name+": "+art.mappingFrames().size()+" frames; "+PlayerHeadProfile.fingerprint(art));
        }
        return 0;
    }
}
