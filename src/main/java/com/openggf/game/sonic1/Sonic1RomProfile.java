package com.openggf.game.sonic1;

import com.openggf.data.RetailRomRevision;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;

import java.io.IOException;

/**
 * Explicit retail S1 data-base selection. Inputs are the existing REV01 constants;
 * ROM-derived absolute pointers and offsets are already native and must not pass
 * through this resolver. No ROM bytes or pointer values are rewritten.
 *
 * <p>REV00 addresses were obtained from matching symbols in FixBugs=0 builds of
 * the inspected S1 disassembly (f6ece657 reference, FixBugs=0).
 * Both assembled images were SHA-1 identical to their
 * retail originals (REV00 6DDB7DE1E17E7F6CDB88927BD906352030DAA194,
 * REV01 69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B).
 * Unknown images retain the historical REV01-compatible address policy.
 */
public final class Sonic1RomProfile {
    private Sonic1RomProfile() { }

    public static boolean isRev00(RomByteReader reader) {
        return reader != null && RetailRomRevision.detect(reader) == RetailRomRevision.S1_REV00;
    }

    public static boolean isRev00(Rom rom) {
        if (rom == null) return false;
        try {
            return isRev00(RomByteReader.fromRom(rom));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot identify the Sonic 1 ROM", error);
        }
    }

    private static RetailRomRevision revision(com.openggf.level.objects.ObjectServices services) {
        try {
            RomByteReader reader = services == null ? null : services.romReader();
            return reader == null ? RetailRomRevision.UNKNOWN : RetailRomRevision.detect(reader);
        } catch (IOException | IllegalStateException error) {
            // ROM-free object probes retain the historical compatible behavior.
            return RetailRomRevision.UNKNOWN;
        }
    }

    public static boolean isRev00(com.openggf.level.objects.ObjectServices services) {
        return revision(services) == RetailRomRevision.S1_REV00;
    }

    public static boolean isKnownRetail(com.openggf.level.objects.ObjectServices services) {
        return revision(services).belongsTo(com.openggf.data.RomIdentity.S1);
    }

    public static int resolve(Rom rom, int revision01Base) {
        return isRev00(rom) ? revision00Address(revision01Base) : revision01Base;
    }

    public static int resolve(RomByteReader reader, int revision01Base) {
        return isRev00(reader) ? revision00Address(revision01Base) : revision01Base;
    }

    static int revision00Address(int revision01Base) {
        return switch (revision01Base) {
            case 0x01B5E -> 0x01B66; // PalCycle data
            case 0x01B7E -> 0x01B86; // PalCycle data
            case 0x01B9E -> 0x01BA6; // PalCycle data
            case 0x01BBE -> 0x01BC6; // PalCycle data
            case 0x01BD0 -> 0x01BD8; // PalCycle data
            case 0x01BE2 -> 0x01BEA; // PalCycle data
            case 0x01C4A -> 0x01C52; // PalCycle data
            case 0x01C6E -> 0x01C76; // PalCycle data
            case 0x01C8E -> 0x01C96; // PalCycle data
            case 0x01D02 -> 0x01D0A; // Pal_SBZCyc1
            case 0x01D12 -> 0x01D1A; // Pal_SBZCyc2
            case 0x01D22 -> 0x01D2A; // Pal_SBZCyc3
            case 0x01D32 -> 0x01D3A; // PalCycle data
            case 0x01D3E -> 0x01D46; // Pal_SBZCyc5
            case 0x01D4E -> 0x01D56; // Pal_SBZCyc6
            case 0x01D5E -> 0x01D66; // Pal_SBZCyc7
            case 0x01D7E -> 0x01D86; // Pal_SBZCyc8
            case 0x01D88 -> 0x01D90; // Pal_SBZCyc9
            case 0x01D98 -> 0x01DA0; // PalCycle data
            case 0x020B8 -> 0x020C0; // Pal_Sega1
            case 0x020C4 -> 0x020CC; // Pal_Sega1_end
            case 0x02168 -> 0x02170; // Pal_Index + 8 (existing palette-ID origin)
            case 0x02200 -> 0x02208; // Pal_SegaBG
            case 0x02280 -> 0x02288; // Pal_SegaBG_end
            case 0x02300 -> 0x02308; // Pal_LevelSel
            case 0x023A0 -> 0x023A8; // Pal_GHZ
            case 0x02400 -> 0x02408; // Pal_GHZ_end
            case 0x02460 -> 0x02468; // Pal_LZWater
            case 0x027A0 -> 0x027A8; // Pal_SBZ3Water
            case 0x02820 -> 0x02828; // Pal_LZSonWater
            case 0x02840 -> 0x02848; // Pal_LZSonWater_end
            case 0x028E0 -> 0x028E8; // Pal_Continue
            case 0x04AD6 -> 0x04ACA; // Pal_SSCyc1
            case 0x04B1E -> 0x04B12; // Pal_SSCyc2
            case 0x05054 -> 0x05048; // Ani_CSon
            case 0x05062 -> 0x05056; // Map_ContScr
            case 0x0568E -> 0x05680; // Map_ESon
            case 0x05788 -> 0x0577A; // Map_ECha
            case 0x057C0 -> 0x057B2; // Map_ECha_internal.eCha_6_gray_End
            case 0x0592A -> 0x0591C; // EndDemo_LampVar + $14
            case 0x05D5E -> 0x05D50; // Demo_EndGHZ1
            case 0x05D8C -> 0x05D7E; // Demo_EndMZ
            case 0x05DCC -> 0x05DBE; // Demo_EndSYZ
            case 0x05DFC -> 0x05DEE; // Demo_EndLZ
            case 0x05E2C -> 0x05E1E; // Demo_EndSLZ
            case 0x05E4C -> 0x05E3E; // Demo_EndSBZ1
            case 0x05E6C -> 0x05E5E; // Demo_EndSBZ2
            case 0x05E9C -> 0x05E8E; // Demo_EndGHZ2
            case 0x05F2A -> 0x05F1C; // LevelSizeArray
            case 0x0611E -> 0x06112; // StartLocArray
            case 0x07D4A -> 0x076CC; // Bri_Data_Y_Max
            case 0x07E5A -> 0x077DC; // Bri_Data_Align
            case 0x07FB2 -> 0x07934; // Map_Bri
            case 0x082C6 -> 0x07C48; // Map_Swing_GHZ
            case 0x082E4 -> 0x07C66; // Map_Swing_SLZ
            case 0x08476 -> 0x07DF8; // Map_Hel
            case 0x087BA -> 0x0813C; // Map_Plat_GHZ
            case 0x08806 -> 0x08188; // Map_Plat_GHZ_internal.large_End
            case 0x08818 -> 0x0819A; // Map_Plat_SLZ
            case 0x08828 -> 0x081AA; // Map_GBall
            case 0x08C1E -> 0x085A0; // Map_Ledge
            case 0x08DC4 -> 0x08746; // Map_CFlo
            case 0x08ED4 -> 0x08856; // Map_Scen
            case 0x0906A -> 0x089EC; // Map_ADoor
            case 0x0948C -> 0x08E0E; // Expl_Main + $6
            case 0x094E0 -> 0x08E62; // Map_Hog
            case 0x09524 -> 0x08EA6; // Map_Hog_internal.hog_Ball2_End
            case 0x09544 -> 0x08EC6; // Map_ExplodeItem
            case 0x09AE4 -> 0x09466; // Map_Animal1
            case 0x09AFC -> 0x0947E; // Map_Animal1_internal.flap2_End
            case 0x09B14 -> 0x09496; // Map_Animal2_internal.flap2_End
            case 0x09B2C -> 0x094AE; // Map_Animal3_internal.flap2_End
            case 0x09DCE -> 0x09750; // Map_Crab
            case 0x0A0B4 -> 0x09A36; // Map_Buzz
            case 0x0A184 -> 0x09B06; // Map_Missile
            case 0x0A610 -> 0x09F92; // Map_Ring
            case 0x0A654 -> 0x09FD2; // Map_GRing
            case 0x0A6F6 -> 0x0A074; // Map_Flash
            case 0x0AC14 -> 0x0A58A; // Map_Monitor
            case 0x0B254 -> 0x0ABCA; // Map_Chop
            case 0x0B2FA -> 0x0AC70; // Map_Jaws
            case 0x0B4E6 -> 0x0AE5C; // Map_Burro
            case 0x0B95E -> 0x0B2D4; // Map_LGrass
            case 0x0B9F8 -> 0x0B36E; // Map_Fire
            case 0x0BC84 -> 0x0B5FA; // Map_Glass
            case 0x0C1D8 -> 0x0BB4E; // Map_CSto.internal
            case 0x0C52E -> 0x0BEA4; // Map_But
            case 0x0C970 -> 0x0C2E6; // Map_Push
            case 0x0D232 -> 0x0CBA4; // M_Card_FZ_End
            case 0x0D266 -> 0x0CBD8; // Map_Got
            case 0x0D328 -> 0x0CC9A; // Map_SSR
            case 0x0D482 -> 0x0CDF4; // M_SSR_GotAll_End
            case 0x0D676 -> 0x0CFE8; // Map_Spike
            case 0x0D79C -> 0x0D10E; // Map_PRock
            case 0x0D944 -> 0x0D2B6; // Map_Smash
            case 0x0E3A8 -> 0x0DD1A; // Map_Spring
            case 0x0E5D0 -> 0x0DF42; // Map_Newt
            case 0x0E830 -> 0x0E1A2; // Map_Roll
            case 0x0E8DE -> 0x0E250; // Map_Edge
            case 0x0EC82 -> 0x0E5F4; // Map_Flame
            case 0x0EF90 -> 0x0E904; // Map_Brick
            case 0x0F00E -> 0x0E982; // Map_Light
            case 0x0F18C -> 0x0EB00; // Map_Bump
            case 0x0F3C4 -> 0x0ED38; // Map_Sign
            case 0x0F8D6 -> 0x0F24A; // Map_Geyser
            case 0x0FBBA -> 0x0F52E; // Map_Geyser_internal.blank_Begin
            case 0x0FE2C -> 0x0F7A0; // Map_Moto
            case 0x0FFBA -> 0x0F92E; // Map_Yad
            case 0x10460 -> 0x0FDD4; // Map_Smab
            case 0x106D6 -> 0x1004A; // Map_MBlock
            case 0x1072C -> 0x100A0; // Map_MBlockLZ
            case 0x108CA -> 0x1023E; // Map_Bas
            case 0x10DD4 -> 0x10700; // Map_FBlock
            case 0x1102A -> 0x10956; // Map_SBall
            case 0x11032 -> 0x1095E; // Map_SBall2
            case 0x11174 -> 0x10AA0; // Map_BBall
            case 0x1141C -> 0x10D48; // Map_Elev
            case 0x11556 -> 0x10E82; // Map_Circ
            case 0x11710 -> 0x1103C; // Map_Stair
            case 0x1176A -> 0x11096; // Map_Pylon
            case 0x119F6 -> 0x11322; // Map_Pole
            case 0x11AC6 -> 0x113F2; // Map_Flap
            case 0x11CE4 -> 0x11610; // Map_Fan
            case 0x12078 -> 0x119A4; // Map_Seesaw
            case 0x120BA -> 0x119E6; // Map_SSawBall
            case 0x122FC -> 0x11C28; // Map_Bomb
            case 0x125B8 -> 0x11EE4; // Map_Orb
            case 0x1266E -> 0x11F9A; // Map_Harp
            case 0x12896 -> 0x121C2; // Map_LBlock
            case 0x129EC -> 0x12318; // Map_Gar
            case 0x12CBC -> 0x125E8; // LCon_Data
            case 0x12D50 -> 0x1267C; // Map_LConv
            case 0x130A0 -> 0x129CC; // Map_Bub
            case 0x13228 -> 0x12B54; // Map_WFall
            case 0x1421C -> 0x13B48; // Ani_Sonic
            case 0x14B1A -> 0x143B2; // Map_Shield
            case 0x14D34 -> 0x145CC; // Map_Splash
            case 0x159FA -> 0x15292; // Map_Jun
            case 0x15DEE -> 0x15686; // Map_Disc
            case 0x16048 -> 0x158E0; // Map_Trap
            case 0x160A2 -> 0x1593A; // Map_Spin
            case 0x16300 -> 0x15B98; // Map_Saw
            case 0x166D0 -> 0x15F68; // Map_Stomp
            case 0x16892 -> 0x1612A; // Map_VanP
            case 0x16948 -> 0x161E0; // Map_Elec
            case 0x16C0E -> 0x164A6; // SpinC_Data
            case 0x16D90 -> 0x16628; // Map_Gird
            case 0x1751A -> 0x16D6E; // Map_Cat
            case 0x178A4 -> 0x170F8; // Map_Lamp
            case 0x179F4 -> 0x17248; // Map_Bonus
            case 0x18468 -> 0x17CBC; // Ani_Eggman
            case 0x184B8 -> 0x17D0C; // Map_Eggman
            case 0x18580 -> 0x17DD4; // Map_BossItems
            case 0x19FAE -> 0x197FE; // Map_BossBlock
            case 0x1A1B2 -> 0x19A02; // Ani_SEgg
            case 0x1A1E4 -> 0x19A34; // Map_SEgg
            case 0x1A4C2 -> 0x19D12; // Map_FFloor
            case 0x1A524 -> 0x19D74; // BossFinal_ObjData
            case 0x1A560 -> 0x19DB0; // BossFinal_ObjData2
            case 0x1AB8A -> 0x1A3D4; // Ani_FZEgg
            case 0x1AB90 -> 0x1A3DA; // Map_FZDamaged
            case 0x1ABD2 -> 0x1A41C; // Map_FZDamaged_internal.damage2_End
            case 0x1AE2C -> 0x1A676; // Map_EggCyl
            case 0x1B20C -> 0x1AA56; // Map_PLaunch
            case 0x1B25C -> 0x1AAA6; // Map_Plasma
            case 0x1B52A -> 0x1AD74; // Map_Pri
            case 0x1B5E2 -> 0x1AE2C; // React_Sizes - 2
            case 0x1BE02 -> 0x1B64C; // SS_LayoutIndex
            case 0x1BE1A -> 0x1B664; // SS_StartLoc
            case 0x1CD6A -> 0x1C5B4; // Map_HUD
            case 0x1D2A6 -> 0x1CADA; // Art_Hud
            case 0x1D5A6 -> 0x1CDDA; // Art_LivesNums
            case 0x1DD16 -> 0x1D5A2; // DebugList.EndingSS_end
            case 0x1DD86 -> 0x1D612; // ArtLoadCues
            case 0x1E700 -> 0x1DC00; // Nem_SegaLogo
            case 0x1EB70 -> 0x1E062; // Eni_SegaLogo
            case 0x1EC6C -> 0x1E15A; // Eni_Title
            case 0x1ED80 -> 0x1E26E; // Nem_TitleFg
            case 0x1FD8C -> 0x1F27A; // Nem_TitleSonic
            case 0x2175A -> 0x20C48; // Nem_TitleTM
            case 0x21CF4 -> 0x211E2; // Map_Sonic
            case 0x22310 -> 0x217FE; // MS_WaterSlide_End
            case 0x22610 -> 0x21AFE; // Art_Sonic
            case 0x2C730 -> 0x2BD5C; // Nem_Shield
            case 0x2C8C6 -> 0x2BEF2; // Nem_Stars
            case 0x2CA8E -> 0x2C5E4; // Map_SSWalls_internal.angledF_End
            case 0x2D3C6 -> 0x2CF1C; // Eni_SSBg1
            case 0x2D4FA -> 0x2D050; // Nem_SSBgFish
            case 0x2E18A -> 0x2DCE0; // Eni_SSBg2
            case 0x2E48A -> 0x2DFE0; // Nem_SSBgCloud
            case 0x2E97E -> 0x2E4D4; // Nem_SSGOAL
            case 0x2EA6C -> 0x2E5C2; // Nem_SSRBlock
            case 0x2EB3C -> 0x2E692; // Nem_SS1UpBlock
            case 0x2EC32 -> 0x2E788; // Nem_SSEmStars
            case 0x2EC90 -> 0x2E7E6; // Nem_SSRedWhite
            case 0x2ED22 -> 0x2E878; // Nem_SSZone1
            case 0x2EDE4 -> 0x2E93A; // Nem_SSZone2
            case 0x2EEB2 -> 0x2EA08; // Nem_SSZone3
            case 0x2EF7E -> 0x2EAD4; // Nem_SSZone4
            case 0x2F046 -> 0x2EB9C; // Nem_SSZone5
            case 0x2F110 -> 0x2EC66; // Nem_SSZone6
            case 0x2F1E0 -> 0x2ED36; // Nem_SSUpDown
            case 0x2F3D4 -> 0x2EF2A; // Nem_SSEmerald
            case 0x2F53C -> 0x2F092; // Nem_SSGhost
            case 0x2F5EC -> 0x2F142; // Nem_SSWBlock
            case 0x2F6C6 -> 0x2F21C; // Nem_SSGlass
            case 0x2F74A -> 0x2F2A0; // Nem_ResultEm
            case 0x2F912 -> 0x2F468; // Nem_Swing
            case 0x2FA2C -> 0x2F582; // Nem_Bridge
            case 0x2FB60 -> 0x2F6B6; // Nem_Ball
            case 0x2FCFE -> 0x2F854; // Nem_Spikes
            case 0x2FF8E -> 0x2FAE4; // Nem_SpikePole
            case 0x300BA -> 0x2FC10; // Nem_PplRock
            case 0x301E8 -> 0x2FD3E; // Nem_GhzWall1
            case 0x30286 -> 0x2FDDC; // Nem_GhzWall2
            case 0x302E6 -> 0x2FE3C; // Nem_Water
            case 0x3040A -> 0x2FF60; // Nem_Splash
            case 0x30BFE -> 0x30754; // Nem_LzSpikeBall
            case 0x30D7E -> 0x308D4; // Nem_FlapDoor
            case 0x30EE8 -> 0x30A3E; // Nem_Bubbles
            case 0x3153E -> 0x31094; // Nem_LzBlock3
            case 0x315F4 -> 0x3114A; // Nem_LzDoor1
            case 0x31696 -> 0x311EC; // Nem_Harpoon
            case 0x317F2 -> 0x31348; // Nem_LzPole
            case 0x31856 -> 0x313AC; // Nem_LzDoor2
            case 0x319A8 -> 0x314FE; // Nem_LzWheel
            case 0x31E8A -> 0x319E0; // Nem_Gargoyle
            case 0x31FFA -> 0x31B50; // Nem_LzBlock2
            case 0x322B2 -> 0x31E08; // Nem_LzPlatfm
            case 0x323EA -> 0x31F40; // Nem_Cork
            case 0x32514 -> 0x3206A; // Nem_LzBlock1
            case 0x32624 -> 0x3217A; // Nem_MzMetal
            case 0x328B2 -> 0x32408; // Nem_MzSwitch
            case 0x32970 -> 0x324C6; // Nem_MzGlass
            case 0x32A7C -> 0x325D2; // Nem_MzFire
            case 0x32D5A -> 0x328B0; // Nem_Lava
            case 0x33670 -> 0x331C6; // Nem_MzBlock
            case 0x3385C -> 0x333B2; // Nem_Seesaw
            case 0x33A98 -> 0x335EE; // Nem_SlzSpike
            case 0x33BDE -> 0x33734; // Nem_Fan
            case 0x33E22 -> 0x33978; // Nem_SlzWall
            case 0x33E84 -> 0x339DA; // Nem_Pylon
            case 0x33F66 -> 0x33ABC; // Nem_SlzSwing
            case 0x34148 -> 0x33C9E; // Nem_SlzBlock
            case 0x34254 -> 0x33DAA; // Nem_SlzCannon
            case 0x342F8 -> 0x33E4E; // Nem_Bumper
            case 0x34462 -> 0x33FB8; // Nem_SyzSpike2
            case 0x344C4 -> 0x3401A; // Nem_LzSwitch
            case 0x345A6 -> 0x340FC; // Nem_SyzSpike1
            case 0x34834 -> 0x3438A; // Nem_SbzWheel1
            case 0x34888 -> 0x343DE; // Nem_SbzWheel2
            case 0x34B24 -> 0x3467A; // Nem_Cutter
            case 0x34D28 -> 0x3487E; // Nem_Stomper
            case 0x34EC6 -> 0x34A1C; // Nem_SpinPform
            case 0x351F6 -> 0x34D4C; // Nem_TrapDoor
            case 0x353D4 -> 0x34F2A; // Nem_SbzFloor
            case 0x3542C -> 0x34F82; // Nem_Electric
            case 0x355AC -> 0x35102; // Nem_SbzBlock
            case 0x356AA -> 0x35200; // Nem_FlamePipe
            case 0x35836 -> 0x3538C; // Nem_SbzDoor1
            case 0x35886 -> 0x353DC; // Nem_SlideFloor
            case 0x358DE -> 0x35434; // Nem_SbzDoor2
            case 0x359DA -> 0x35530; // Nem_Girder
            case 0x35AF0 -> 0x35646; // Nem_BallHog
            case 0x35EB0 -> 0x35A06; // Nem_Crabmeat
            case 0x3639E -> 0x35EF4; // Nem_Buzz
            case 0x3692C -> 0x36482; // Nem_Burrobot
            case 0x37016 -> 0x36B6C; // Nem_Chopper
            case 0x3727E -> 0x36DD4; // Nem_Jaws
            case 0x37508 -> 0x3705E; // Nem_Roller
            case 0x37A2C -> 0x37582; // Nem_Motobug
            case 0x37CB6 -> 0x3780C; // Nem_Newtron
            case 0x382D4 -> 0x37E2A; // Nem_Yadrin
            case 0x386BC -> 0x38212; // Nem_Basaran
            case 0x38C00 -> 0x38756; // Nem_Bomb
            case 0x38E98 -> 0x389EE; // Nem_Orbinaut
            case 0x39076 -> 0x38BCC; // Nem_Cater
            case 0x39204 -> 0x38D5A; // Nem_TitleCard
            case 0x39812 -> 0x39368; // Nem_Hud
            case 0x39908 -> 0x3945E; // Nem_Lives
            case 0x39A0E -> 0x39564; // Nem_Ring
            case 0x39B02 -> 0x39658; // Nem_Monitors
            case 0x39F62 -> 0x39AB8; // Nem_Explode
            case 0x3A5C8 -> 0x3A11E; // Nem_Points
            case 0x3A678 -> 0x3A1CE; // Nem_GameOver
            case 0x3A80A -> 0x3A360; // Nem_HSpring
            case 0x3A90C -> 0x3A462; // Nem_VSpring
            case 0x3A9E8 -> 0x3A53E; // Nem_SignPost
            case 0x3AE64 -> 0x3A9BA; // Nem_Lamp
            case 0x3AF24 -> 0x3AA7A; // Nem_BigFlash
            case 0x3B098 -> 0x3ABEE; // Nem_Bonus
            case 0x3B39A -> 0x3AEF0; // Nem_ContSonic
            case 0x3B64A -> 0x3B1A0; // Nem_MiniSonic
            case 0x3B884 -> 0x3B3DA; // Nem_Rabbit
            case 0x3B9DC -> 0x3B532; // Nem_Chicken
            case 0x3BB38 -> 0x3B68E; // Nem_Penguin
            case 0x3BCB4 -> 0x3B80A; // Nem_Seal
            case 0x3BDD0 -> 0x3B926; // Nem_Pig
            case 0x3BF06 -> 0x3BA5C; // Nem_Flicky
            case 0x3C040 -> 0x3BB96; // Nem_Squirrel
            case 0x3C19C -> 0x3BCF2; // Blk16_GHZ
            case 0x3CB3C -> 0x3C692; // Nem_GHZ_1st
            case 0x3F544 -> 0x3F09A; // Blk256_GHZ
            case 0x5D0FC -> 0x5CC62; // Nem_Eggman
            case 0x5D960 -> 0x5D4C6; // Nem_Weapons
            case 0x5DC4A -> 0x5D7B0; // Nem_Prison
            case 0x5E4CE -> 0x5E034; // Nem_Sbz2Eggman
            case 0x5ECFA -> 0x5E860; // Nem_FzBoss
            case 0x5F462 -> 0x5EFC8; // Nem_FzEggman
            case 0x5F9E2 -> 0x5F548; // Nem_Exhaust
            case 0x5FB02 -> 0x5F668; // Nem_EndEm
            case 0x5FD00 -> 0x5F866; // Nem_EndSonic
            case 0x60EDE -> 0x60A44; // Nem_TryAgain
            case 0x61822 -> 0x6175E; // Kos_EndFlowers
            case 0x6203A -> 0x61F76; // Nem_CreditText
            case 0x62638 -> 0x62574; // Nem_EndStH
            case 0x667A4 -> 0x667C6; // SS_6
            case 0x66A96 -> 0x66AD6; // Art_GhzWater
            case 0x66C96 -> 0x66CD6; // Art_GhzFlower1
            case 0x67096 -> 0x670D6; // Art_GhzFlower2
            case 0x67516 -> 0x67556; // Art_MzLava1
            case 0x67816 -> 0x67856; // Art_MzLava2
            case 0x67E16 -> 0x67E56; // Art_MzTorch
            case 0x68116 -> 0x68156; // Art_SbzSmoke
            case 0x68B96 -> 0x68BD6; // Level_Index
            case 0x68F22 -> 0x68F62; // Level_GHZbg
            case 0x6A2E4 -> 0x6A324; // Art_BigRing
            default -> revision01Base;
        };
    }
}
