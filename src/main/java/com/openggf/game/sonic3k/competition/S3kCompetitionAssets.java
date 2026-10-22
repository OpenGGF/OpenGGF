package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomChannel;
import com.openggf.data.compression.EnigmaReader;
import com.openggf.data.compression.KosinskiReader;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.competition.objects.CompetitionArenaObjectInstance.RockDebrisPiece;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.SpriteMappingPieces;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.util.DplcStaticFlattener;
import com.openggf.util.PatternDecompressor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ROM-only competition art, mappings, DPLCs, menus and palettes.
 *
 * <p>Owning native pointers: Competition_Menu/Competition_LevelSelect/
 * Competition_Results (sonic3k.asm:13472-13515,13731-13800,14714-14760),
 * SonicKnuckles2P_Load_PLC (25521-25562), Tails2P_Load_PLC (29978-30018),
 * PLC_42..46 (199831-199888). Full compressed source spans and mapping bytes
 * were compared with the locked-on ROM, not selected by a filename match.
 * The S3-half miniatures and menu assets are the pointers the locked-on
 * routines actually use. No disassembly file is consulted at runtime.
 */
public final class S3kCompetitionAssets {
    public enum MenuPhase { MODE, ARENA_SELECT, PLAYER_SELECT, RESULTS, TIME_ATTACK_RESULTS }
    public record SourceSpan(String name, int address, int byteCount, String sha256) { }

    private static final List<SourceSpan> SOURCES = List.of(
            span("misc1",0x396B9C,0x173,"0bbe980ab9594d929681c0bf769f8cfc0595d28638cba955524337504f827c98"),
            span("misc2",0x396D10,0x1F1,"4f2d56c7ae150cc00a6440b24aedb45399754f30e5b460518b9b7431717fb856"),
            span("misc3",0x396F02,0x4F6,"60ad015b8be1fe0057eefc3502238088cae138118b41d456a868935b0c24425d"),
            span("goal",0x3973F8,0x178,"9ca7f54cd40c2dc27cdc32de7064e2ce6f8c4278242e12b7c765d0b6b710e982"),
            span("time",0x397570,0x659,"88051277522dc720a7b6c9db089bcc99973e8a73b5630c5403f68ce06df59402"),
            span("laps",0x397BCA,0x4FA,"b2bfdd89174560ba3f05e0829f1080da6ca0065435d698b928b206f7144446bf"),
            span("position",0x3980C4,0x9A,"34225bb8144df5a4a789e44aeebd67a5d64102ae29578651d2bfe7f6124a92a6"),
            span("bpz",0x39815E,0x84A,"30bfc1795735036222037acbf1c949875df29d3e8092c7fbb401998635eb2920"),
            span("dpz",0x3989A8,0xD15,"5cf7c5c8aca50dd0666c2e4774ce321ef7d96bd076a47fd84b61727cce48affb"),
            span("cgz",0x3996BE,0x373,"3f2031637f5844b04af4044dbae45c329264b7cb49eca407f9f99f0f22c1a32c"),
            span("emz",0x399A32,0x291,"e47d480512268c518dce47624813ad72519291b2a94a528b1545548940ad765b"),
            span("mode",0x39F46E,0x410,"0ffb0984653c12605d517174b5f855137af9dcae6005bc024b97ca3afaa80f33"),
            span("arena-select",0x39F87E,0x1E40,"7e7c05d330a65f780c9c06060eb7e1376842ea5eb8eb944acd2d495f78afe524"),
            span("results",0x3A16BE,0x430,"00bab33cda2b42f26a482ce1063c05793ef01f241f5615c1cad701b3d6d9e887"),
            span("player-select",0x3A1B30,0x4F0,"a48cd92c517b3bab9f70101cf51bd95641a69007b4d87ede07f0b71b73354e28"),
            span("menu-background",0x39D4A4,0x1C60,"93b8a88fca7ec8f4bf127e3411fb7a8adb224c665c6ba5927bb99f730d93932d"),
            span("border-map",0x39F104,0x4C,"85c817bc49de2b6ea72a0f7c94a2ed9faf37c5d141a4424702ae3082fd080558"),
            span("image-map",0x39F24C,0x120,"b31e763d290b6d9d8caa63402371f05a81edc3f878c4ca0fe070499ffdf0b187"),
            span("name-map",0x39F36C,0x102,"53f7d178a61bc34bda36f38669627e379594210de918bbf5e7e3b33eecaed147"),
            span("background-map",0x39D2A2,0x202,"d3caed1829d1e2d50e729d5c5df593ecf94dd41bd7eb849737f42f1333359897"),
            span("results-led-map",0x20AE62,0x1A,"375ef31ee32f9579f686b178ef9f3fb50c8ecb9a63fd8f69aebad219c686443d"),
            span("dash-dust",0x34F5E2,0xBE,"5fe85ca4b850e240ccc8801af238e23219e302935be52820e3adc2e046315997"),
            span("ta-digits",0x5D788,0xE0,"ad3cbe88ec3a68bbc662ffe5b9f63bf7ad5c8e090d5f8a41e43adde0fec909fa"),
            span("ta-font",0x38D3E0,0x1A5,"ae3fe91497d6e07dba329c5f2657f42043827fb6ccda0007262878fac6f04566"),
            span("ta-icons-general",0x15BAC0,0x670,"a644821f33fa5d4840ba20cb551bfae32f775fba7942e189803ae4df5bf4aa5e"),
            span("ta-icons-tails-knuckles",0x15C130,0x1B0,"77be5d8b44d07baa210917cd6d70aa6544a19c1f95300cd6d88d295514fd4e21"),
            span("sonic-miniature",0x337780,0x30C0,"39dc882c0f81093ee8ca0d100f530deedf2e35eb65740d1447127e826f9826e2"),
            span("tails-miniature",0x33A840,0x2DA0,"76e5f6bff5e83ff97e8b7e2902f1005b8d44762c4d73912f3cc9c299f8954eef"),
            span("tails-tail-miniature",0x33D5E0,0x580,"ea5027019170a279736bfd67a22ee55b8d0bb56e31e83fe15e18f1949e00567e"),
            span("knuckles-miniature",0x33DB60,0x3480,"d419f03d9e71ca2d682bb8d9054d929ab1bc015f7d07a9b13e1ec32aed8518d4")
    );

    private final Rom rom;
    private final RomByteReader reader;
    private final Map<String,ObjectSpriteSheet> sheets = new LinkedHashMap<>();
    private final Map<String,SpriteAnimationSet> animations = new LinkedHashMap<>();
    private final Map<MenuPhase,Pattern[]> menuPatterns = new EnumMap<>(MenuPhase.class);
    private final Map<String,List<SpriteMappingFrame>> menuMappings = new LinkedHashMap<>();
    private final Map<String,int[]> rawMenuMaps = new LinkedHashMap<>();
    private final List<RockDebrisPiece> rockDebris;
    private final int[] borders;
    private final int[] images;
    private final int[] names;
    private final int[] resultsLed;

    public S3kCompetitionAssets(Rom rom) throws IOException {
        this.rom = Objects.requireNonNull(rom);
        reader = RomByteReader.fromRom(rom);
        Pattern[] common = blankBank();
        put(common,0x391,PatternDecompressor.nemesis(rom,0x396D10));
        put(common,0x3AD,PatternDecompressor.nemesis(rom,0x396B9C));
        put(common,0x3C6,PatternDecompressor.nemesis(rom,0x396F02));
        put(common,0x600,PatternDecompressor.nemesis(rom,0x397570));
        put(common,0x6BC,PatternDecompressor.nemesis(rom,0x3973F8));
        put(common,0x700,PatternDecompressor.nemesis(rom,0x397BCA));
        put(common,0x75E,PatternDecompressor.nemesis(rom,0x3980C4));
        put(common,0x7E0,PatternDecompressor.nemesis(rom,0x34F5E2));
        Pattern[] bpz = common.clone(), dpz = common.clone(), cgz = common.clone(), emz = common.clone();
        put(bpz,0x300,PatternDecompressor.nemesis(rom,0x39815E));
        put(dpz,0x280,PatternDecompressor.nemesis(rom,0x3989A8));
        put(cgz,0x300,PatternDecompressor.nemesis(rom,0x3996BE));
        put(emz,0x300,PatternDecompressor.nemesis(rom,0x399A32));

        add("competition_item",common,0x36AA6,32,0x3C6);
        add("competition_goal",common,0x36FFA,4,0x6BC);
        add("competition_lap",common,0x370D6,10,0x8700);
        add("competition_item_icon",common,0x37F6A,4,0x8750);
        add("competition_neon",common,0x375FA,19,0x8756);
        add("competition_position",common,0x380C6,5,0x875E);
        add("competition_timer",common,0x37E8E,1,0x8600);
        add("competition_dash_dust",common,0x1910C,8,0x7E0);
        addTimerDigits(common);
        add("competition_blade",cgz,0x35BBC,4,0x6300);
        add("competition_elephant",bpz,0x35D5A,2,0x300);
        add("competition_balloon",bpz,0x35E9A,10,0x6300);
        add("competition_bumper",bpz,0x2322EE,2,0x2300);
        add("competition_fan",cgz,0x22F620,3,0x6300);
        add("competition_sand_bar",dpz,0x3609A,30,0x4280);
        add("competition_sand_bar_wide",dpz,0x360D6,29,0x4280);
        add("competition_button",dpz,0x36350,2,0x4280);
        add("competition_floor_button",common,0x22BD7A,3,0x3AD);
        add("competition_dripper",emz,0x38252,4,0x6300);
        add("competition_dripper_static",emz,0x38252,4,0xC300);
        add("competition_dripper_drop",emz,0x38252,4,0x4300);
        add("competition_rock",emz,0x21DDEC,8,0xE300);
        add("competition_retracting_spring_base",common,0x23F4A,4,0x391);
        add("competition_retracting_spring_base_emz",emz,0x23F52,4,0x391);
        add("competition_spring",common,0x23904,7,0x391);
        add("competition_spring_horizontal",common,0x23904,7,0x3AD);
        add("competition_spring_yellow",common,0x23912,7,0x391);
        add("competition_spring_horizontal_yellow",common,0x23912,7,0x3AD);
        sheets.put("competition_retracting_spring",sheets.get("competition_spring"));

        miniature("competition_sonic",0x337780,0x30C0,0x29FC3A,0x29FDE4,44,0);
        miniature("competition_tails",0x33A840,0x2DA0,0x29FEE6,0x2A013E,46,0);
        miniature("competition_knuckles",0x33DB60,0x3480,0x2A02AA,0x2A0498,50,0x2000);
        miniature("competition_tails_tail",0x33D5E0,0x580,0x2A00A4,0x2A024C,16,0);
        loadAnimations();
        loadMenus();
        borders = enigma(0x39F104,0x829F);
        images = enigma(0x39F24C,0xE29F);
        names = enigma(0x39F36C,0xA29F);
        resultsLed = enigma(0x20AE62,0xA34A);
        int count = reader.readU16BE(0x202A6) + 1;
        List<RockDebrisPiece> debris = new ArrayList<>(count);
        for (int i=0;i<count;i++) debris.add(new RockDebrisPiece(
                (byte)reader.readU8(0x202A8+i*2),(byte)reader.readU8(0x202A9+i*2),
                (short)reader.readU16BE(0x20358+i*4),(short)reader.readU16BE(0x2035A+i*4)));
        rockDebris = List.copyOf(debris);
    }

    public static List<SourceSpan> sourceSpans() { return SOURCES; }
    public Map<String,ObjectSpriteSheet> sheets() { return Map.copyOf(sheets); }
    public ObjectSpriteSheet sheet(String key) {
        ObjectSpriteSheet sheet=sheets.get(key);
        if (sheet==null) throw new IllegalArgumentException("No native competition sheet: "+key);
        return sheet;
    }
    public ObjectSpriteSheet miniature(S3kCompetitionPlayer.Character character) {
        return sheet("competition_"+character.name().toLowerCase(java.util.Locale.ROOT));
    }
    public ObjectSpriteSheet tailsTail() { return sheet("competition_tails_tail"); }
    public SpriteAnimationSet animations(String key) { return animations.get(key); }
    public List<RockDebrisPiece> rockDebris() { return rockDebris; }
    public Pattern[] menuPatterns(MenuPhase phase) { return menuPatterns.get(phase); }
    public int[] menuBackground(MenuPhase phase) throws IOException {
        return enigma(0x39D2A2,phase==MenuPhase.MODE ? 0x6001 : 1);
    }
    public int[] arenaBorders() { return borders.clone(); }
    public int[] arenaImages() { return images.clone(); }
    public int[] arenaNames() { return names.clone(); }
    public int[] resultsLed() { return resultsLed.clone(); }
    public Map<String,int[]> rawMenuMaps() {
        Map<String,int[]> result=new LinkedHashMap<>();
        rawMenuMaps.forEach((key,value)->result.put(key,value.clone())); return Map.copyOf(result);
    }

    /** Sprite owners may change native palette/high-priority bits while retaining the same map. */
    public ObjectSpriteSheet menuSheet(MenuPhase phase,String mappingKey,int nativeArtWord) {
        List<SpriteMappingFrame> frames=menuMappings.get(mappingKey);
        if (frames==null) throw new IllegalArgumentException("No native menu mappings: "+mappingKey);
        return new ObjectSpriteSheet(menuPatterns(phase),resolve(frames,nativeArtWord),0,1);
    }

    public Palette[] menuPalette(MenuPhase phase,S3kCompetitionSession.Arena selected) throws IOException {
        byte[] raw=new byte[128];
        if (phase==MenuPhase.MODE) {
            copyPalette(raw,0,0x209818,96); copyPalette(raw,96,0x39D282,32);
        } else {
            copyPalette(raw,0,0x39D282,32); copyPalette(raw,32,0x20A55E,96);
            if (phase==MenuPhase.ARENA_SELECT && selected!=null) {
                int menuIndex=S3kCompetitionSession.GRAND_PRIX_ORDER.indexOf(selected);
                copyPalette(raw,96,0x20A5BE+menuIndex*32,32);
            }
            if (phase==MenuPhase.RESULTS) copyPalette(raw,96,0x20AD84,32);
            if (phase==MenuPhase.TIME_ATTACK_RESULTS) copyPalette(raw,96,0x20B26C,32);
        }
        Palette[] result=new Palette[4];
        for (int i=0;i<4;i++) { result[i]=new Palette(); result[i].fromSegaFormat(Arrays.copyOfRange(raw,i*32,i*32+32)); }
        return result;
    }

    private void loadAnimations() {
        animations.put("competition_balloon",S3kSpriteDataLoader.loadAnimationSet(reader,0x35E74,4));
        SpriteAnimationSet sand=S3kSpriteDataLoader.loadAnimationSet(reader,0x36046,4);
        animations.put("competition_sand_bar",sand); animations.put("competition_sand_bar_wide",sand);
        SpriteAnimationSet spring=S3kSpriteDataLoader.loadAnimationSet(reader,0x23722,6);
        for (String key:List.of("competition_spring","competition_spring_horizontal","competition_spring_yellow",
                "competition_spring_horizontal_yellow","competition_retracting_spring")) animations.put(key,spring);
        animations.put("competition_item",S3kSpriteDataLoader.loadAnimationSet(reader,0x36A58,12));
        animations.put("competition_goal",S3kSpriteDataLoader.loadAnimationSet(reader,0x36FF2,1));
        animations.put("competition_neon",S3kSpriteDataLoader.loadAnimationSet(reader,0x3750E,8));
        animations.put("competition_sonic",signedAnimations(0x12F84,36));
        animations.put("competition_knuckles",signedAnimations(0x12FCC,36));
        animations.put("competition_tails",signedAnimations(0x15F60,33));
        animations.put("competition_dash_dust",S3kSpriteDataLoader.loadAnimationSet(reader,0x190F2,4));
    }

    private void loadMenus() throws IOException {
        Pattern[] background=PatternDecompressor.kosinski(rom,0x39D4A4);
        Pattern[] levels=PatternDecompressor.kosinski(rom,0x39F87E);
        Pattern[] mode=PatternDecompressor.kosinski(rom,0x39F46E);
        Pattern[] results=PatternDecompressor.kosinski(rom,0x3A16BE);
        Pattern[] players=PatternDecompressor.kosinski(rom,0x3A1B30);
        for (MenuPhase phase:MenuPhase.values()) {
            Pattern[] bank=blankBank(); put(bank,1,background); put(bank,0x29F,levels);
            if (phase==MenuPhase.MODE) put(bank,0x34A,mode);
            else { put(bank,0x5C9,players); if (phase==MenuPhase.RESULTS || phase==MenuPhase.TIME_ATTACK_RESULTS) put(bank,0x34A,results); }
            if (phase==MenuPhase.TIME_ATTACK_RESULTS) {
                put(bank,0x58D,PatternDecompressor.nemesis(rom,0x5D788));
                put(bank,0x5A1,PatternDecompressor.nemesis(rom,0x38D3E0));
                byte[] general=kosinskiBytes(0x15BAC0), tk=kosinskiBytes(0x15C130);
                byte[] combined=new byte[general.length+tk.length];
                System.arraycopy(general,0,combined,0,general.length); System.arraycopy(tk,0,combined,general.length,tk.length);
                if (combined.length<0xF40+27*32) throw new IOException("Native TA icon source span is truncated");
                put(bank,0x572,PatternDecompressor.fromBytes(Arrays.copyOfRange(combined,0xF40,0xF40+27*32)));
            }
            menuPatterns.put(phase,bank);
        }
        menuMappings.put("competition_menu_mode",S3kSpriteDataLoader.loadMappingFrames(reader,0x2098C0,7));
        menuMappings.put("competition_menu_select",S3kSpriteDataLoader.loadMappingFrames(reader,0x20A6E4,16));
        menuMappings.put("competition_menu_player",S3kSpriteDataLoader.loadMappingFrames(reader,0x3A1AEE,3));
        menuMappings.put("competition_results_character",S3kSpriteDataLoader.loadMappingFrames(reader,0x2F26A,59));
        sheets.put("competition_menu_mode",menuSheet(MenuPhase.MODE,"competition_menu_mode",0x34A));
        sheets.put("competition_menu_select",menuSheet(MenuPhase.ARENA_SELECT,"competition_menu_select",0x829F));
        sheets.put("competition_menu_player",menuSheet(MenuPhase.PLAYER_SELECT,"competition_menu_player",0x25C9));
        sheets.put("competition_results_character",menuSheet(MenuPhase.TIME_ATTACK_RESULTS,"competition_results_character",0x9FD5));
        int[][] raw={{0x20A130,0x34},{0x39F150,0xD8},{0x39F228,0x24},{0x20AE7C,0x28},
                {0x20AEA4,0x50},{0x20AEF4,0x14},{0x20AF08,8},{0x20AF10,8},{0x20AF18,8},
                {0x20B326,0x64},{0x20B38A,0x1C},{0x20B3A6,0x30},
                {0x20B3EA,0x34},{0x20B41E,0x34},{0x20B452,0x34},{0x20B486,0x34},{0x20B4BA,0x34}};
        String[] keys={"numbers","time_border","best_time","results_letters","results_dividers","results_total",
                "win","lose","tie","lap","records","ranks","azure_lake","balloon_park","chrome_gadget","desert_palace","endless_mine"};
        for (int i=0;i<raw.length;i++) rawMenuMaps.put(keys[i],words(rom.readBytes(raw[i][0],raw[i][1])));
    }

    private void miniature(String key,int art,int bytes,int map,int dplc,int count,int paletteWord) throws IOException {
        Pattern[] patterns=PatternDecompressor.uncompressed(reader,art,bytes);
        var maps=S3kSpriteDataLoader.loadMappingFrames(reader,map,count);
        var requests=S3kSpriteDataLoader.loadDplcFrames(reader,dplc,count);
        var flattened=DplcStaticFlattener.applyDplcRemap(maps,requests);
        sheets.put(key,new ObjectSpriteSheet(patterns,resolve(flattened,paletteWord),0,1));
    }
    private SpriteAnimationSet signedAnimations(int base,int count) {
        SpriteAnimationSet set=new SpriteAnimationSet();
        for (int i=0;i<count;i++) set.addScript(i,S3kSpriteDataLoader.parseAnimationScript(reader,
                base+(short)reader.readU16BE(base+i*2)));
        return set;
    }
    private void add(String key,Pattern[] bank,int map,int count,int artWord) {
        sheets.put(key,new ObjectSpriteSheet(bank,resolve(S3kSpriteDataLoader.loadMappingFrames(reader,map,count),artWord),0,1));
    }
    private void addTimerDigits(Pattern[] bank) {
        List<SpriteMappingFrame> frames=new ArrayList<>(10);
        for (int i=0;i<10;i++) {
            int address=0x37E2A+i*4, size=reader.readU8(address+1);
            SpriteMappingPiece piece=new SpriteMappingPiece(0,(byte)reader.readU8(address),
                    (size>>2&3)+1,(size&3)+1,reader.readU16BE(address+2),false,false,0,false);
            frames.add(new SpriteMappingFrame(List.of(SpriteMappingPieces.withTileWord(piece,
                    (SpriteMappingPieces.toTileWord(piece)+0x8600)&0xFFFF))));
        }
        sheets.put("competition_timer_digit",new ObjectSpriteSheet(bank,List.copyOf(frames),0,1));
    }
    private static List<SpriteMappingFrame> resolve(List<SpriteMappingFrame> frames,int artWord) {
        return frames.stream().map(frame->new SpriteMappingFrame(frame.pieces().stream()
                .map(piece->SpriteMappingPieces.withTileWord(piece,
                        (SpriteMappingPieces.toTileWord(piece)+artWord)&0xFFFF)).toList())).toList();
    }
    private void copyPalette(byte[] destination,int at,int source,int count) throws IOException {
        System.arraycopy(rom.readBytes(source,count),0,destination,at,count);
    }
    private int[] enigma(int address,int artWord) throws IOException {
        try (var channel=RomChannel.at(rom,address)) { return words(EnigmaReader.decompress(channel,artWord)); }
    }
    private byte[] kosinskiBytes(int address) throws IOException {
        try (var channel=RomChannel.at(rom,address)) { return KosinskiReader.decompress(channel); }
    }
    private static int[] words(byte[] raw) {
        int[] words=new int[raw.length/2];
        for (int i=0;i<words.length;i++) words[i]=(raw[i*2]&255)<<8 | raw[i*2+1]&255;
        return words;
    }
    private static Pattern[] blankBank() {
        Pattern[] bank=new Pattern[0x800]; Arrays.fill(bank,new Pattern()); return bank;
    }
    private static void put(Pattern[] bank,int tile,Pattern[] data) throws IOException {
        if (tile<0 || tile+data.length>bank.length) throw new IOException("Competition art exceeds native VRAM");
        System.arraycopy(data,0,bank,tile,data.length);
    }
    private static SourceSpan span(String name,int address,int bytes,String hash) { return new SourceSpan(name,address,bytes,hash); }
}
