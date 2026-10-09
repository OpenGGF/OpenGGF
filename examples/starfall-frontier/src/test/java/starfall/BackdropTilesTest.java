package starfall;

import com.openggf.mods.scene.SceneRomArt;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackdropTilesTest {
    private SceneRomArt rom(byte[] bytes) {
        return (SceneRomArt)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{SceneRomArt.class},
                (proxy,method,args)-> {
                    if(!method.getName().equals("read"))throw new AssertionError("Unexpected ROM operation");
                    int start=(int)args[0],length=(int)args[1];
                    if(start<0||length<0||start+length>bytes.length)throw new IllegalArgumentException("ROM bounds");
                    return Arrays.copyOfRange(bytes,start,start+length);
                });
    }
    @Test void nativeSignedFrameTablesUseTheirFirstSourceOffsetAndPreservePaletteAndTransparency() {
        byte[] bytes=new byte[0x29000];ByteBuffer table=ByteBuffer.wrap(bytes);table.position(0x28862);
        table.putShort((short)1); // two scripts: a negative duration table, followed by a positive one
        table.putInt(0xFF001000).putShort((short)(0x222*32)).put((byte)3).put((byte)2);
        table.put(new byte[]{3,7,5,9,1,2});
        table.putInt(0x09002000).putShort((short)(0x252*32)).put((byte)1).put((byte)1);
        table.put(new byte[]{1,0});
        Arrays.fill(bytes,0x1000+3*32,0x1000+5*32,(byte)0x21);
        bytes[0x1000+3*32]=0x20;
        Arrays.fill(bytes,0x2000+32,0x2000+64,(byte)0x33);
        int[] palette=new int[64];for(int n=0;n<64;n++)palette[n]=0xFF000000|n*0x030303;
        var tiles=BackdropTiles.firstFrame(rom(bytes),Biome.MARBLE_GARDEN,0,palette);
        for(int line=0;line<4;line++) {
            assertEquals(palette[line*16+2],tiles[line][0x222].pixel(0,0));
            assertEquals(0,tiles[line][0x222].pixel(1,0));
            assertEquals(palette[line*16+1],tiles[line][0x223].pixel(1,0));
            assertEquals(palette[line*16+3],tiles[line][0x252].pixel(0,0));
        }
        assertNull(tiles[0][0x221]);assertNull(tiles[0][0x224]);
    }
    @Test void outOfVramDestinationsAreRejectedBeforeReadingSourceArt() {
        byte[] bytes=new byte[0x29000];ByteBuffer table=ByteBuffer.wrap(bytes);table.position(0x28862);
        table.putShort((short)0).putInt(0x09001000).putShort((short)(0x7FF*32)).put((byte)1).put((byte)2);
        assertThrows(IllegalArgumentException.class,()->BackdropTiles.firstFrame(rom(bytes),Biome.MARBLE_GARDEN,0,new int[64]));
    }
}
