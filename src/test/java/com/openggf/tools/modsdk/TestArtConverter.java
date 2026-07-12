package com.openggf.tools.modsdk;

import com.openggf.level.objects.BakedSheetReader;
import com.openggf.io.ModInputLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestArtConverter {
    @TempDir Path temp;

    @Test
    void convertsExactPalettePngAndOrderedPieceToCanonicalSheet() throws Exception {
        Path png = temp.resolve("art.png");
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            image.setRGB(x, y, (x + y) % 2 == 0 ? 0xff000000 : 0xffffffff);
        }
        ImageIO.write(image, "png", png.toFile());
        Path yaml = temp.resolve("sheet.yaml");
        Files.writeString(yaml, manifest(0, 0, 8, 8));
        Path output = temp.resolve("art.ggfsheet");

        ArtConverter.Result result = new ArtConverter().convert(png, yaml, output);
        BakedSheetReader.BakedSheet sheet = BakedSheetReader.read(Files.readAllBytes(output));

        assertEquals(1, result.patternCount());
        assertEquals(1, sheet.frames().size());
        assertEquals(0, sheet.patterns()[0].getPixel(0, 0));
        assertEquals(1, sheet.patterns()[0].getPixel(1, 0));
        assertEquals(0, sheet.frames().getFirst().mapping().pieces().getFirst().tileIndex());
        assertEquals(0x0EEE, sheet.palette().orElseThrow().colors()[1]);
    }

    @Test
    void rejectsNonAlignedImageOutOfBoundsPieceAndReportsEveryOffPalettePixel() throws Exception {
        Path yaml = temp.resolve("sheet.yaml");
        Files.writeString(yaml, manifest(0, 0, 8, 8));
        Path odd = temp.resolve("odd.png");
        ImageIO.write(new BufferedImage(9, 8, BufferedImage.TYPE_INT_ARGB), "png", odd.toFile());
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new ArtConverter().convert(odd, yaml, temp.resolve("odd.ggfsheet")))
                .getMessage().contains("multiple of 8"));

        BufferedImage valid = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Path png = temp.resolve("valid.png"); ImageIO.write(valid, "png", png.toFile());
        Files.writeString(yaml, manifest(8, 0, 8, 8));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new ArtConverter().convert(png, yaml, temp.resolve("oob.ggfsheet")))
                .getMessage().contains("outside image"));

        valid.setRGB(0, 0, 0xff123456); valid.setRGB(1, 0, 0xff654321); ImageIO.write(valid, "png", png.toFile());
        Files.writeString(yaml, manifest(0, 0, 8, 8));
        String message = assertThrows(IllegalArgumentException.class,
                () -> new ArtConverter().convert(png, yaml, temp.resolve("bad.ggfsheet"))).getMessage();
        assertTrue(message.contains("(0,0)=#123456"), message);
        assertTrue(message.contains("(1,0)=#654321"), message);
    }

    @Test void enforcesInjectedMetadataAndPredecodeImageLimitsAndUnknownFields() throws Exception {
        Path png=temp.resolve("wide.png");ImageIO.write(new BufferedImage(16,8,BufferedImage.TYPE_INT_ARGB),"png",png.toFile());
        Path yaml=temp.resolve("limits.yaml");Files.writeString(yaml,manifest(0,0,8,8));
        assertTrue(assertThrows(Exception.class,()->new ArtConverter(ModInputLimits.loweringBuilder()
                .maxImageWidth(8).build()).convert(png,yaml,temp.resolve("x"))).getMessage().contains("image limits"));
        assertTrue(assertThrows(Exception.class,()->new ArtConverter(ModInputLimits.loweringBuilder()
                .maxMetadataBytes(32).build()).convert(png,yaml,temp.resolve("y"))).getMessage().contains("metadata byte limit"));
        Path valid=temp.resolve("eight.png");ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB),"png",valid.toFile());
        Files.writeString(yaml,manifest(0,0,8,8)+"unknownField: true\n");
        assertThrows(Exception.class,()->new ArtConverter().convert(valid,yaml,temp.resolve("z")));
    }

    @Test void capsOffPaletteDiagnosticsAndNeverOverwritesOrLeavesPartialOutput() throws Exception {
        BufferedImage image=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)image.setRGB(x,y,0xff000000|((y*8+x+1)*0x010203));
        Path png=temp.resolve("many.png");ImageIO.write(image,"png",png.toFile());Path yaml=temp.resolve("many.yaml");
        Files.writeString(yaml,manifest(0,0,8,8));Path output=temp.resolve("many.ggfs");
        String message=assertThrows(Exception.class,()->new ArtConverter().convert(png,yaml,output)).getMessage();
        assertTrue(message.contains("more offending pixels"),message);assertTrue(message.length()<2500);assertFalse(Files.exists(output));

        BufferedImage valid=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);Path validPng=temp.resolve("ok.png");
        ImageIO.write(valid,"png",validPng.toFile());Files.writeString(output,"keep");
        assertThrows(Exception.class,()->new ArtConverter().convert(validPng,yaml,output));
        assertEquals("keep",Files.readString(output));
    }

    @Test void targetCreatedAtPublicationBoundaryIsNeverReplaced() throws Exception {
        BufferedImage valid=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);Path png=temp.resolve("race.png");
        ImageIO.write(valid,"png",png.toFile());Path yaml=temp.resolve("race.yaml");Files.writeString(yaml,manifest(0,0,8,8));
        Path output=temp.resolve("race.ggfs");ArtConverter converter=new ArtConverter(ModInputLimits.production(),()->{
            try{Files.writeString(output,"competitor");}catch(Exception error){throw new RuntimeException(error);}});
        assertThrows(Exception.class,()->converter.convert(png,yaml,output));assertEquals("competitor",Files.readString(output));
        try(var files=Files.list(temp)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".ggfmod-art-")));}
    }

    private static String manifest(int sourceX, int sourceY, int width, int height) {
        return """
                formatVersion: 1
                paletteLine: 0
                palette: ["#000000", "#FFFFFF", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000", "#000000"]
                frames:
                  - delay: 5
                    pieces:
                      - sourceX: %d
                        sourceY: %d
                        widthPixels: %d
                        heightPixels: %d
                        xOffset: -4
                        yOffset: -4
                        hFlip: false
                        vFlip: false
                        paletteIndex: 0
                        priority: false
                """.formatted(sourceX, sourceY, width, height);
    }
}
