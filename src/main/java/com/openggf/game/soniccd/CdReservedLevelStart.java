package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.CdAssetBytes;
/** Exact Main263-278 reserved-slot stores. Values grant no load/publication authority. */
final class CdReservedLevelStart {
 static CdAssetBytes hud(CdAssetBytes before){byte[] h=bytes(before);for(int whole:new int[]{2,3,5})h[(whole-1)*64]=0x1C;h[2*64+0x28]=1;h[4*64+0x29]=1;return CdAssetBytes.copyOf(h);}
 static CdAssetBytes timeIcon(CdAssetBytes before){byte[] h=bytes(before);h[30*64]=0x19;h[30*64+0x28]=10;return CdAssetBytes.copyOf(h);}
 static CdAssetBytes title(CdAssetBytes before){byte[] h=bytes(before);h[3*64]=0x3C;return CdAssetBytes.copyOf(h);}
 private static byte[] bytes(CdAssetBytes b){if(b.length()!=1984)throw new IllegalArgumentException("31 full reserved slots required");return b.copy();}
 private CdReservedLevelStart(){}
}
