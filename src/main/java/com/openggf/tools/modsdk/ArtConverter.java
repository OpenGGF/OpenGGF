package com.openggf.tools.modsdk;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.openggf.level.Pattern;
import com.openggf.io.ModInputLimits;
import org.yaml.snakeyaml.LoaderOptions;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Converts exact-palette PNG sheets into the canonical GGFS v1 container. */
public final class ArtConverter {
    private static final int MAX_OFFENDING_SAMPLES=32;
    private static final int MAX_OFFENDING_COLORS=16;
    private final ModInputLimits limits;
    private final ObjectMapper yaml;
    private final Runnable beforePublish;
    public ArtConverter(){this(ModInputLimits.production(),()->{});}
    ArtConverter(ModInputLimits limits){this(limits,()->{});}
    ArtConverter(ModInputLimits limits,Runnable beforePublish){
        this.limits=Objects.requireNonNull(limits,"limits");
        this.beforePublish=Objects.requireNonNull(beforePublish);
        LoaderOptions options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(limits.maxYamlDepth());options.setCodePointLimit(limits.maxDocumentCodePoints());
        YAMLFactory factory=YAMLFactory.builder().loaderOptions(options).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(limits.maxYamlDepth())
                        .maxDocumentLength(limits.maxDocumentCodePoints()).maxStringLength(limits.maxStringChars())
                        .maxNumberLength(limits.maxNumericDigits()).build()).build();
        yaml=new ObjectMapper(factory).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public Result convert(Path imagePath, Path manifestPath, Path outputPath) throws IOException {
        Objects.requireNonNull(imagePath, "imagePath");
        Objects.requireNonNull(manifestPath, "manifestPath");
        Objects.requireNonNull(outputPath, "outputPath");
        if(Files.size(imagePath)>limits.maxAssetBytes())throw new IllegalArgumentException("PNG exceeds asset byte limit");
        int declaredWidth,declaredHeight;
        try(var input=ImageIO.createImageInputStream(imagePath.toFile())){
            if(input==null)throw new IllegalArgumentException("Input is not a readable PNG");
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Input is not a readable PNG");
            var reader=readers.next();try{reader.setInput(input,true,true);
                if(!"png".equalsIgnoreCase(reader.getFormatName()))throw new IllegalArgumentException("Input must be PNG");
                declaredWidth=reader.getWidth(0);declaredHeight=reader.getHeight(0);
            }finally{reader.dispose();}
        }
        validateDimensions(declaredWidth,declaredHeight);
        BufferedImage image = ImageIO.read(imagePath.toFile());
        if (image == null) throw new IllegalArgumentException("Input is not a readable PNG");
        validateDimensions(image.getWidth(),image.getHeight());
        byte[] manifestBytes=readBounded(manifestPath,limits.maxMetadataBytes(),"Art manifest exceeds metadata byte limit");
        Manifest manifest;
        try(var parser=yaml.createParser(manifestBytes)){
            manifest=yaml.readValue(parser,Manifest.class);
            if(parser.nextToken()!=null)throw invalid("trailing YAML document is not allowed");
        }
        validateManifest(manifest);
        int[] rgb = new int[16];
        int[] genesis = new int[16];
        Map<Integer, Integer> paletteIndex = new LinkedHashMap<>();
        for (int index = 0; index < 16; index++) {
            rgb[index] = parseRgb(manifest.palette().get(index));
            genesis[index] = toGenesis(rgb[index]);
            paletteIndex.putIfAbsent(rgb[index], index);
        }
        List<String> offending = new ArrayList<>();java.util.LinkedHashSet<Integer> offendingColors=new java.util.LinkedHashSet<>();
        long offendingCount=0;boolean moreColors=false;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int color = image.getRGB(x, y) & 0xFFFFFF;
            if (!paletteIndex.containsKey(color)) {offendingCount++;
                if(offending.size()<MAX_OFFENDING_SAMPLES)offending.add("(" + x + "," + y + ")=#%06X".formatted(color));
                if(offendingColors.size()<MAX_OFFENDING_COLORS)offendingColors.add(color);else if(!offendingColors.contains(color))moreColors=true;}
        }
        if (offendingCount!=0) {
            throw new IllegalArgumentException("PNG contains colors outside the declared 16-color line: "
                    + String.join(", ", offending)+(offendingCount>offending.size()
                    ? "; and "+(offendingCount-offending.size())+" more offending pixels":"")
                    + "; distinct colors sampled="+offendingColors.size()+(moreColors?"+":""));
        }

        List<Pattern> patterns = new ArrayList<>();
        List<BakedSheetReader.Frame> frames = new ArrayList<>();
        int totalPieces=0;
        if(manifest.frames().size()>limits.maxSheetFrames())throw invalid("frame count exceeds limit");
        for (Frame frame : manifest.frames()) {
            totalPieces=Math.addExact(totalPieces,frame.pieces().size());
            if(totalPieces>limits.maxSheetPieces())throw invalid("piece count exceeds limit");
            if (frame.delay() < 0 || frame.delay() > 0xFFFF) throw invalid("frame delay must fit u16");
            List<SpriteMappingPiece> pieces = new ArrayList<>();
            for (Piece piece : frame.pieces()) {
                validatePiece(piece, image);
                int tileIndex = patterns.size();
                for (int tileY = 0; tileY < piece.heightPixels(); tileY += 8) {
                    for (int tileX = 0; tileX < piece.widthPixels(); tileX += 8) {
                        Pattern pattern = new Pattern();
                        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                            int color = image.getRGB(piece.sourceX() + tileX + x,
                                    piece.sourceY() + tileY + y) & 0xFFFFFF;
                            pattern.setPixel(x, y, paletteIndex.get(color).byteValue());
                        }
                        patterns.add(pattern);
                        if(patterns.size()>limits.maxSheetPatterns())throw invalid("pattern count exceeds limit");
                    }
                }
                pieces.add(new SpriteMappingPiece(piece.xOffset(), piece.yOffset(),
                        piece.widthPixels() / 8, piece.heightPixels() / 8, tileIndex,
                        piece.hFlip(), piece.vFlip(), piece.paletteIndex(), piece.priority()));
            }
            frames.add(new BakedSheetReader.Frame(frame.delay(), new SpriteMappingFrame(pieces)));
        }
        BakedSheetReader.BakedSheet sheet = new BakedSheetReader.BakedSheet(
                patterns.toArray(Pattern[]::new), frames,
                Optional.of(new BakedSheetReader.Palette(manifest.paletteLine(), genesis)));
        byte[] bytes = BakedSheetWriter.write(sheet);
        Path output = outputPath.toAbsolutePath().normalize();
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        if(Files.exists(output))throw new IOException("Art output already exists: "+output);
        Path parent=Objects.requireNonNull(output.getParent(),"art output parent");Files.createDirectories(parent);
        Path staging=Files.createTempFile(parent,".ggfmod-art-",".ggfs");boolean published=false;
        try{Files.write(staging,bytes);BakedSheetReader.read(Files.readAllBytes(staging),limits);
            NoClobberPublisher.publish(staging,output,beforePublish);published=true;
            return new Result(output,patterns.size(),frames.size());
        }finally{if(!published)Files.deleteIfExists(staging);}
    }

    private void validateDimensions(int width,int height){
        if(width<=0||height<=0||width%8!=0||height%8!=0)throw new IllegalArgumentException("PNG dimensions must each be a positive multiple of 8");
        if(width>limits.maxImageWidth()||height>limits.maxImageHeight()||(long)width*height>limits.maxImagePixels())
            throw new IllegalArgumentException("PNG dimensions exceed image limits");
    }

    private static byte[] readBounded(Path path,long cap,String message)throws IOException{
        try(var input=Files.newInputStream(path)){
            byte[] bytes=input.readNBytes(Math.toIntExact(Math.min(Integer.MAX_VALUE,cap+1)));
            if(bytes.length>cap)throw new IllegalArgumentException(message);return bytes;
        }
    }

    private static void validateManifest(Manifest manifest) {
        if (manifest == null || manifest.formatVersion() != 1) throw invalid("formatVersion must be 1");
        if (manifest.paletteLine() < 0 || manifest.paletteLine() > 3) throw invalid("paletteLine must be 0..3");
        if (manifest.palette() == null || manifest.palette().size() != 16)
            throw invalid("palette must contain exactly 16 colors");
        if (manifest.frames() == null) throw invalid("frames are required");
    }

    private static void validatePiece(Piece piece, BufferedImage image) {
        if (piece == null) throw invalid("piece is required");
        if (piece.sourceX() < 0 || piece.sourceY() < 0 || piece.sourceX() % 8 != 0 || piece.sourceY() % 8 != 0
                || piece.widthPixels() <= 0 || piece.heightPixels() <= 0
                || piece.widthPixels() % 8 != 0 || piece.heightPixels() % 8 != 0) {
            throw invalid("piece source and dimensions must be positive 8-aligned pixel boxes");
        }
        if ((long) piece.sourceX() + piece.widthPixels() > image.getWidth()
                || (long) piece.sourceY() + piece.heightPixels() > image.getHeight()) {
            throw invalid("piece box lies outside image");
        }
        if (piece.paletteIndex() < 0 || piece.paletteIndex() > 3) throw invalid("paletteIndex must be 0..3");
    }

    private static int parseRgb(String value) {
        if (value == null || !value.matches("#[0-9A-Fa-f]{6}")) throw invalid("palette colors must be #RRGGBB");
        return Integer.parseInt(value.substring(1), 16);
    }

    private static int toGenesis(int rgb) {
        int r = (rgb >>> 16) & 0xFF, g = (rgb >>> 8) & 0xFF, b = rgb & 0xFF;
        int r3 = exactChannel(r), g3 = exactChannel(g), b3 = exactChannel(b);
        return (b3 << 9) | (g3 << 5) | (r3 << 1);
    }

    private static int exactChannel(int value) {
        int quantized = (value * 7 + 127) / 255;
        if ((quantized * 255 + 3) / 7 != value) throw invalid("palette color is not exactly Genesis-representable");
        return quantized;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid art manifest: " + message);
    }

    public record Result(Path output, int patternCount, int frameCount) {}
    public record Manifest(int formatVersion, int paletteLine, List<String> palette, List<Frame> frames) {}
    public record Frame(int delay, List<Piece> pieces) {
        public Frame { pieces = List.copyOf(Objects.requireNonNull(pieces, "pieces")); }
    }
    public record Piece(int sourceX, int sourceY, int widthPixels, int heightPixels,
                        int xOffset, int yOffset, boolean hFlip, boolean vFlip,
                        int paletteIndex, boolean priority) {}
}
