package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdHeader;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import java.util.List;

/**
 * Candidate declarative original-USA R32A (Collision Chaos Act 2 Present) layout.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: R32A Legacy.asm,
 * R3 Legacy Act 2 Shared.asm, r3/data_2a.asm, scroll_2a.asm and stage_object_maps_2.asm.
 * These are source facts, not an authenticated asset owner, parser or runtime admission.
 * The closed shared programme reader consumes these facts; no per-act aggregate loader is cloned here.
 * Runtime bytes must continue to enter through CdProgramAssets; the tests use its existing
 * pipeline and the existing finite Nemesis, Sonic visual and placement parsers directly.
 */
public final class CdR32aDataLayout {
    private CdR32aDataLayout() { }
    public static final CdReleaseVariant VARIANT = CdReleaseVariant.USA;
    public static final CdNativeFile FILE = CdNativeFile.R32A;
    public static final CdStageKey STAGE = CdStagePayload.R32A.stage();
    public static final CdMmdImage.Domain DOMAIN = CdMmdImage.Domain.ORIGINAL_WORD_RAM;
    public static final int FILE_BYTES = 262144;
    public static final String MMD_SHA256 = "fe2c1c959104fdd0c73c4980ab221b7f99907050e7896b9e9256d73a7847dfc3";
    public static final CdMmdHeader HEADER = new CdMmdHeader(0, 0, 0, 0, 0x200100, 0x20010C, 0x200112);
    public static final int ORDINARY_ZONE_ACT_WORD = 0x101;
    public static final int ORDINARY_PERIOD = 1;
    public static final CdR11bLoadPlan.Bounds BOUNDS = new CdR11bLoadPlan.Bounds(4, 0, 0x2697, 0, 0x510, 0x60);
    public static final CdR11bLoadPlan.InitialCentre INITIAL_CENTRE = new CdR11bLoadPlan.InitialCentre(0x20, 0x1AC);
    public static final CdR11bLoadPlan.LevelIndex LEVEL_INDEX = new CdR11bLoadPlan.LevelIndex(
            new CdR11bLoadPlan.TaggedPointer(0x0323A2F4L), new CdR11bLoadPlan.TaggedPointer(0x0223937EL),
            0x210000, CdAssetBytes.copyOf(new byte[] {0, (byte) 0x81}), CdAssetBytes.copyOf(new byte[] {5, 5}));

    /** Internal immutable data consumption; no Body, Frame, Host or World admission. */
    static CdStagePlayerSource readPlayerSource(CdProgramAssets actual)throws java.io.IOException {
        return CdStagePlayerSource.fromProgramme(CdProgrammeReader.open(actual,STAGE));
    }

    /** Fixed raw source declarations only; no public arbitrary-address read method. */
    public enum Asset {
        LEVEL_CHUNKS(0x10000, 54784, "b4f47030cb62fa200f85a4ea53cdd5d2bded5ab13aa9927c30af1c1857bb97f4"),
        COL_ANGLE_MAP(0x36C76, 256, "c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd"),
        COL_HEIGHT_MAP(0x36D76, 4096, "103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d"),
        COL_WIDTH_MAP(0x37D76, 4096, "90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4"),
        LEVEL_COLLISION(0x38D76, 700, "4255dd7fec26d277d162ef252851654dec30dff0a0dfe401d444111c69a13295"),
        LAYOUT_FG(0x3907A, 242, "5ccc03ba525aff4a3ae1fae5c4b6f50f874036bfedef9becbafa44f841f0a7cb"),
        LAYOUT_BG(0x3916C, 24, "5cdad5fa793d62ece4d08f0eba0ed90e0531fc779e414e23df1c7585cc98ea37"),
        ART_SONIC(0x20000, 57120, "a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276"),
        MAP_SPR_SONIC(0x2DF20, 2443, "4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965"),
        SONIC_MAPPING_READ_VIEW(0x2DF20, 2444, "9836b535d15ee481a9a663bf651377791569f3ea9d91c6cd657ddc83cca3ae77"),
        DPLC_SONIC(0x2E8AC, 1338, "313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae"),
        ANI_SONIC(0x58FC, 578, "96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9"),
        MINI_ANIMATIONS(0x58AC, 80, "3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8"),
        SINE_TABLE(0x79A, 640, "bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9"),
        ARC_TAN_TABLE(0xA80, 258, "7d0ff986365e0596080368cab93935822e1f39220f50071db5dd075c0cabe66f"),
        TOUCH_DIMENSIONS(0x753C, 126, "5337f6beae28fe2b8458277610f92d358020a78e947d68b09470b38829e46575"),
        ENEMY_POINTS(0x7332, 8, "75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5"),
        CAM_BOUNDS(0x287C, 12, "e7db14d0e3e1b2d863064334453b1970799d4a88b352129107ca688ae3c61d0d"),
        LEVEL_START_LOC(0x2940, 4, "8b2b2f07036cd4f9a9ebf24194c3db813017ff6e2495f4aff7368b0de7978aec"),
        SPEC_CHUNKS(0x2944, 4, "365475d471c68745e3881c3743e1669911103607f18d88d9db7292e29177c0bf"),
        PALETTE_CYCLES(0x200, 100, "f1c83c9d9f2454d129b2811a0dc138994130ad7754a59cf6935a90ab071a3f72"),
        PAL_SONIC(0x65C, 32, "8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a"),
        PAL_LEVEL(0x67C, 96, "2af2050f1eb57a5183cb90be48b6e5e7ffbbf7cd3cc5764d466eaf64a185bd15"),
        PAL_LEVEL_END(0x6DC, 96, "4277057e64bf28760727361b2b5ecda2795d57e05c49511236c4532e1b03e8c4"),
        PALETTE_TABLE(0x52C, 48, "8dd2f06d81432e838ae90f04765214f0229b3d2e44b401a6f0feac170007fdd4"),
        PAL_S1_TITLE(0x55C, 128, "65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759"),
        PAL_S1_LEVEL_SELECT(0x5DC, 128, "809f6fb01f6ee63d1dfdb230c00cc255f7a6585d23e16a48e04cd4b9dc3fb16d"),
        SECTIONS(0xC7CC, 12, "ca54d8c2a85ed1fd804eb14559898b76c7bfee6e595368bc7807b57e5549c102"),
        BG_SECTIONS(0x34F6, 50, "a21448fdc0be189d3b285084fb55114514e079714adc780f9aeed2b45e24ec6f"),
        BG_POINTERS(0x3528, 16, "2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1"),
        SCROLL_REPEAT_COUNTS(0x2BF8, 14, "404bf9fc64e36849a7d88094cd9ecbe230d3461ec067395de32e8e85f2c27e15"),
        SCROLL_WATER_BOUNDS(0x2C06, 6, "677bb2193c2749569097a8311af9320d7269d37ad55c06dac13626006a2d5104"),
        WOBBLE(0x3CE4C, 256, "2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267"),
        LEVEL_COL_INDEX(0x19C2, 32, "a72f4e4868a51dddcbaa558ba39fcf8aecf7d1838010a0c689774eda727fc8bd"),
        LEVEL_DATA_INDEX(0xF16A, 16, "fd89d4a398c4e8c534ee212b91625952d3388524d16437e89a8b39560cdf9df0"),
        PLC_LISTS(0xF17A, 348, "f20655b2bd3cc897b9fc9795c114df4a5041974363b4730a78e715d306fc4946"),
        LEVEL_LAYOUTS(0x39032, 72, "dda7736cd591f1e4df98677dba129d98e1eb93b3d781b7338a5e16c9cc990009"),
        OBJECT_INDEX(0x3DA6, 240, "88b7f9f5fa556ef79f8a5e620e1aa6958b5bbf50a6f716d6ab19cdd61ed738d4"),
        OBJECT_LAYOUTS(0x7BE8, 3658, "8784a57882dfc431ee0c58363b87bc3e3b97106dfc5e3c67d7f21645bf7a557c"),
        ART_LIFE_ICONS(0x32F3C, 384, "0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a"),
        ART_HUD_NUMBERS(0x330BC, 1024, "843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42"),
        ART_SHIELD(0x3114E, 1152, "43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e"),
        ART_INVINCIBILITY(0x315CE, 1152, "486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75"),
        ART_TIME_STARS(0x31A4E, 992, "1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa"),
        ANIM_FRAME0_PRIMARY(0x3459A, 320, "3fe066d7ea3d960f116314b5eefe9175ca420861675846e52a528850d8234d96"),
        ANIM_FRAME0_SECONDARY(0x346DA, 192, "9340668686f0d6d1f18d3a7b0935479c03155998524a74d613656cff3eed07a0"),
        ANIM_FRAME1_PRIMARY(0x3439A, 320, "7ee5db870b561cd52bac0e6e3a84ee687b5e585ff5c6a80707e3499d4a186ff3"),
        ANIM_FRAME1_SECONDARY(0x344DA, 192, "4a07d0ef3897788a36caac6624f27382e4e863d5732d704e1cfdabcff901d78f"),
        ANIM_FRAME0_PRIMARY_READ_WINDOW(0x3459A, 768, "7b8de52f6f5342324a34ef2c7e922b5167aff3c7cfcb38d8b5eac479f0849449"),
        ANIM_FRAME0_SECONDARY_READ_WINDOW(0x346DA, 768, "416707606e59d78b12eea1a472c59e4d0d46fbdb3404bf1736e7e590e142e1bc"),
        ANIM_FRAME1_PRIMARY_READ_WINDOW(0x3439A, 768, "8f9f06d563d52294d56ac8c525cc66010eade910bae01df872a5b3a489e996e6"),
        ANIM_FRAME1_SECONDARY_READ_WINDOW(0x344DA, 768, "a6a3bad83d636895f8517ed60e010cfb8825b1bbb2452ebc1c8eddeaeb6b0288"),
        PLAYER_BLOCKS(0x684A, 8, "fa316848863cb1723eff5b464a05b21e48b87da9444792decf31c760cf992478"),
        PLAYER_BLOCKS_A(0x6852, 34, "d05eb8d9f7ab1c19d8dc26425c9d922c8accb09b9c74da0f652e2a89d270f7a9"),
        PLAYER_BLOCKS_B(0x6874, 6, "7b054c17b49ee53fe432f8700be73ba7d337e56cc0aa4aeced43ef662a7f2ebe"),
        PLAYER_BLOCKS_C(0x687A, 34, "4919cb2975fad8d7ce977c58c4235b139abbfe707a382ad68cb302e081521805"),
        PLAYER_BLOCKS_D(0x689C, 34, "a0e3da04701e194ebf10531da38153f9c058051ca7340833cc068da408c9dc30"),
        PLAYER_BLOCK_ROUTINES(0x6914, 32, "234e4a3bb1b19f9237548700a94e9a515571e11284f2ec9d333825c5ad4a9003"),
        PLAYER_BUMPER_COLLIDE(0x6B20, 6, "b310628db3d435d16937fc75ab8b04097658ac0fcf15530843d3c0aa591cae74"),
        PLAYER_BUMPER_COLLIDE_1(0x6B26, 16, "46fd7d2954ec5a94f0ab18ed3ce82ef3738b9e9a5f444e952caccba97ec43cd8"),
        PLAYER_BUMPER_COLLIDE_2(0x6B36, 16, "7039c80bf965bd800e6e814ae50b42d9316184aa79c63a41075fa1945bf7dd47"),
        PLAYER_BUMPER_COLLIDE_3(0x6B46, 16, "55863b4d00c3326f9acf1d393883e0964cd7598563e7f4548d12a1eea4fdf745");

        private final int offset, length;
        private final String sha256;
        Asset(int offset, int length, String sha256) {
            this.offset = offset; this.length = length; this.sha256 = sha256;
        }
        public int fileOffset() { return offset; }
        public int nativeAddress() { return 0x200000 + offset; }
        public int length() { return length; }
        public int endExclusive() { return offset + length; }
        public String sha256() { return sha256; }
    }

    /** Source-selected instruction operands; comparison data, never executable/native CPU input. */
    public record Operand(int fileOffset, String hexBytes) { }
    public static final List<Operand> OPERANDS = List.of(
            new Operand(0x15E6, "45f90020f16a"),
            new Operand(0x3572, "45f90020f16a"),
            new Operand(0x35EE, "43f900239032"),
            new Operand(0x2450, "43f90020f17a"),
            new Operand(0x2482, "43f90020f17a"),
            new Operand(0x25BE, "43f90020f17a"),
            new Operand(0x3994, "43f900203da6"),
            new Operand(0x7950, "41f900207be8"),
            new Operand(0x283A, "41f90020287c"),
            new Operand(0x28C8, "43f900202940"),
            new Operand(0x2934, "43f900202944"),
            new Operand(0x3FC8, "217c0022df200004"),
            new Operand(0x5B54, "45f90022e8ac"),
            new Operand(0x5B84, "43f900220000"),
            new Operand(0x560C, "43f9002058fc"),
            new Operand(0x71BC, "45f90020753c"),
            new Operand(0x2B26, "47f900202bf8"),
            new Operand(0x2BDE, "47f900202c0649f90023ce4c"),
            new Operand(0x110E, "45f900236c76"),
            new Operand(0x113C, "45f900236d76"),
            new Operand(0x12AC, "45f900237d76"),
            new Operand(0x127E, "45f900236c76"),
            new Operand(0x312C, "41f9002034f6"),
            new Operand(0x34D0, "41f9002034f6"),
            new Operand(0x682A, "d241323b101c43fb10187c003c197200b0711000677e544151ce"),
            new Operand(0x68DA, "303b10384ebb0034"),
            new Operand(0x6AAE, "044100126b2c303b106a43fb00660242000f0243"));
}
