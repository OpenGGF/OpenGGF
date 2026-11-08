package com.openggf.game;

import com.openggf.io.ModAssetRoot;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.IndexColorModel;

/** Bounded indexed-PNG reader for character-owned packaged presentation assets. */
@ModApi
public final class PresentationImages {
    private PresentationImages() { }
    public static CharacterPresentation.Image read(ModAssetRoot root, String entry) throws IOException {
        return decode(root, entry).image();
    }
    public static CharacterPresentation.Backing backing(ModAssetRoot root, String entry, int paletteLine)
            throws IOException {
        var decoded = decode(root, entry);
        return new CharacterPresentation.Backing(decoded.image(), paletteLine, decoded.rgb());
    }
    private record Decoded(CharacterPresentation.Image image, int[] rgb) { }
    private static Decoded decode(ModAssetRoot root, String entry) throws IOException {
        byte[] data = root.readBounded(entry, Math.min(262144, root.limits().maxAssetBytes()));
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported presentation image: " + entry);
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || width > 256 || height < 1 || height > 256)
                    throw new IOException("Presentation image outside dimension budget: " + entry);
                var image = reader.read(0);
                if (!(image.getColorModel() instanceof IndexColorModel palette))
                    throw new IOException("Presentation image must be indexed: " + entry);
                byte[] pixels = new byte[width * height];
                for (int y=0;y<height;y++) for (int x=0;x<width;x++) {
                    int value=image.getRaster().getSample(x,y,0);
                    if (value>15) throw new IOException("Presentation index exceeds 15: " + entry);
                    pixels[y*width+x]=(byte)value;
                }
                int[] rgb=new int[16];
                for(int i=0;i<16;i++) rgb[i]=palette.getRGB(i)&0xffffff;
                return new Decoded(new CharacterPresentation.Image(width,height,pixels),rgb);
            } finally { reader.dispose(); }
        }
    }
}
