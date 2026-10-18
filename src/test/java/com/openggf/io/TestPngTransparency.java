package com.openggf.io;
import org.junit.jupiter.api.Test;
import java.io.*;import java.nio.*;import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class TestPngTransparency {
 @Test void truecolorAndGrayTransparencyKeysAreHonored()throws Exception {
  assertEquals(0x00010203,PngCodec.decode(png(2,new byte[]{1,2,3},new byte[]{0,1,0,2,0,3})).getRGB(0,0));
  assertEquals(0x00070707,PngCodec.decode(png(0,new byte[]{7},new byte[]{0,7})).getRGB(0,0));
  assertEquals(0xFF080808,PngCodec.decode(png(0,new byte[]{8},new byte[]{0,7})).getRGB(0,0));
 }
 private static byte[] png(int type,byte[] pixel,byte[] key)throws Exception {
  var out=new ByteArrayOutputStream();out.write(new byte[]{(byte)137,80,78,71,13,10,26,10});
  chunk(out,"IHDR",ByteBuffer.allocate(13).putInt(1).putInt(1).put((byte)8).put((byte)type).put(new byte[3]).array());
  chunk(out,"tRNS",key);var compressed=new ByteArrayOutputStream();
  try(var z=new DeflaterOutputStream(compressed)){z.write(0);z.write(pixel);}
  chunk(out,"IDAT",compressed.toByteArray());chunk(out,"IEND",new byte[0]);return out.toByteArray();
 }
 private static void chunk(ByteArrayOutputStream out,String type,byte[] data)throws Exception {
  byte[] name=type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);var crc=new CRC32();crc.update(name);crc.update(data);
  var d=new DataOutputStream(out);d.writeInt(data.length);d.write(name);d.write(data);d.writeInt((int)crc.getValue());
 }
}
