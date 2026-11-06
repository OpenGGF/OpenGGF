package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.resources.CdLevelHud;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import java.util.List;
import java.util.Objects;

/**
 * Actual original-USA R12B finite raw writers; no decode, RAM tail default, current bank or service clock.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Main.asm:599-614; Powerup/Main.asm:157-186;
 * HUD and Points/Main.asm:186-717; Palmtree Panic/Data (Act 2 Past).asm:189-228.
 * The raw1024-byte copy crosses TimeStars992+DiagonalSpring32 and TimeOver960+GameOver64.
 * The later1152-byte upload retains128 actual caller CPU bytes through CdPowerupArtRuntime.
 */
public final class CdR12bDirectArtSources implements CdPowerupArtRuntime.RawSources {
    /** Fixed original file spans only. Canonical source length and consumed raw-copy length are distinct. */
    public enum View {
        LIFE_ICON(0x362D6,384,384,
                "0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a","0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a"),
        HUD_NUMBERS(0x36456,1024,1024,
                "843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42","843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42"),
        HUD_RESET_TILES(0xAE52,12,12,
                "3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c","3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c"),
        HUD_DIVISORS(0xAFB2,40,40,
                "7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea","7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea"),
        SHIELD_ART_INDEX(0x63D4,20,20,
                "e5f83ef559b1e3eaa1e15d9c73a6d771b41634426b2dce4ecabf6a6ebe222834","e5f83ef559b1e3eaa1e15d9c73a6d771b41634426b2dce4ecabf6a6ebe222834"),
        SHIELD_COPY(0x344E8,1152,1024,
                "43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e","cbca98fc186fec63ea75c2c0dd8da06ec651c02c37232c15e38cecb749d5b7d0"),
        INV_STARS_COPY(0x34968,1152,1024,
                "486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75","706736889a2cced446522a69a86853de41d4dfc9204c83ab0a7d2690d3036fcc"),
        TIME_STARS_COPY(0x34DE8,992,1024,
                "1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa","2094d9156ba35fdb865feb01bee3e64b809ad0297e3d46d7defcf79d94fb5919"),
        GAME_OVER_COPY(0x33D3C,1024,1024,
                "911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd","911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd"),
        TIME_OVER_COPY(0x3397C,960,1024,
                "a1c0e5a3a7afcfac0fd1b18b277bcde2f561d2453fd65fa1e7256a269fcf9241","8830d700eed4f092a9f4329993caf68940ac65bae7656e54a823031711e371f3");
        private final int offset,canonicalLength,length;
        private final String canonicalSha,sha;
        View(int offset,int canonicalLength,int length,String canonicalSha,String sha) {
            if(offset<0 || canonicalLength<=0 || length<=0 || offset>CdR12bProfile.FILE_BYTES-Math.max(canonicalLength,length)
                    || !canonicalSha.matches("[0-9a-f]{64}") || !sha.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid fixed R12B raw-source span");
            this.offset=offset;this.canonicalLength=canonicalLength;this.length=length;this.canonicalSha=canonicalSha;this.sha=sha;
        }
        public CdR12bProfile.FileRange canonicalRange(){return range(canonicalLength);}
        public CdR12bProfile.FileRange range(){return range(length);}
        private CdR12bProfile.FileRange range(int count){return new CdR12bProfile.FileRange(CdNativeFile.R12B,CdMmdImage.Domain.ORIGINAL_WORD_RAM,offset,count);}
        public String canonicalSha256(){return canonicalSha;}
        public String sha256(){return sha;}
    }
    private static final List<View> ORDER=List.of(View.SHIELD_COPY,View.INV_STARS_COPY,View.TIME_STARS_COPY,View.GAME_OVER_COPY,View.TIME_OVER_COPY);
    private final CdR12bTerrainAssets assets;
    private final CdR12bTerrainAssets.Identity identity;
    private final CdAssetBytes[] views=new CdAssetBytes[View.values().length];
    private CdLevelHud.Assets hud;
    private CdR12bDirectArtSources(CdR12bTerrainAssets assets) {
        this.assets=Objects.requireNonNull(assets,"actual R12B assets");identity=Objects.requireNonNull(assets.identity(),"identity");
        var profile=Objects.requireNonNull(assets.profile(),"profile");
        if(identity.variant()!=CdReleaseVariant.USA || identity.file()!=CdNativeFile.R12B
                || identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM || !identity.stage().equals(CdStagePayload.R12B.stage())
                || !identity.mmdSha256().equals(profile.expectedMmdSha256()))
            throw new IllegalArgumentException("Raw writers require actual original USA R12B");
    }
    public static CdR12bDirectArtSources fromAssets(CdR12bTerrainAssets assets){return Objects.requireNonNull(assets).directArtSources();}
    static CdR12bDirectArtSources bind(CdR12bTerrainAssets assets){return new CdR12bDirectArtSources(assets);}
    public CdR12bTerrainAssets.Identity identity(){return identity;}
    public synchronized CdAssetBytes view(View source) {
        Objects.requireNonNull(source);var value=views[source.ordinal()];if(value!=null)return value;
        value=Objects.requireNonNull(assets.directArtView(source),"retained original raw source");
        if(value.length()!=source.length || !source.sha256().equals(CdR11aProfile.sha256(value.copy())))
            throw new IllegalArgumentException("Original R12B raw source differs: "+source);
        views[source.ordinal()]=value;return value;
    }
    public CdAssetBytes index(){return view(View.SHIELD_ART_INDEX);}
    public View copySource(int nativeSlot){return ORDER.get(nativeSlot);}
    @Override public CdAssetBytes copy(int nativeSlot){var source=copySource(nativeSlot);requireIndex(index());return view(source);}
    public synchronized CdLevelHud.Assets hudAssets() {
        if(hud==null)hud=new CdLevelHud.Assets(view(View.HUD_NUMBERS),view(View.HUD_RESET_TILES),view(View.HUD_DIVISORS));
        return hud;
    }
    /** Pure format validation, not an owner or native-call factory. */
    static void requireIndex(CdAssetBytes value) {
        Objects.requireNonNull(value);if(value.length()!=20)throw new IllegalArgumentException("Exact five-pointer ShieldArtIndex required");
        for(int slot=0;slot<5;slot++)if(value.unsignedLong(slot*4)!=Integer.toUnsignedLong(ORDER.get(slot).range().nativeAddress()))
            throw new IllegalArgumentException("R12B original ShieldArtIndex pointer/order differs");
    }
}
