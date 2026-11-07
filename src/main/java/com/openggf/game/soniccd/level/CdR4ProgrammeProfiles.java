package com.openggf.game.soniccd.level;

import java.util.*;
import static com.openggf.game.soniccd.level.CdProgrammeDefinition.*;

/**
 * Closed TT binding facts from FINAL 4c6055a95 and its independently authenticated
 * shared-parser-differences.json (6621161b8dddef1de1308157c16a3666be207900cb329c66477400ae68706c62).
 * Actual bytes are always read/authenticated by CdProgrammeReader. No decoder cursor,
 * native prefetch, terrain Shape, player source, World or water-service admission is supplied.
 */
final class CdR4ProgrammeProfiles {
    private CdR4ProgrammeProfiles() { }
    private record PaletteFact(String range,int destination,int counter) { }
    private record CycleFact(CycleKind kind,String script,String colors) { }
    private record Facts(String headers,List<Integer> roots,int bodies,int maximumCount,List<String> encountered,
                         List<PaletteFact> palettes,List<CycleFact> cycles,CycleProgramme cycleProgramme,
                         int plcOffset,int plcBytes,String plcSha,SectionFact section,BackgroundKind background) { }
    private sealed interface SectionFact permits CameraFact,RtsFact { }
    private record CameraFact(int offset,int bytes,String sha,List<SectionRow> rows) implements SectionFact { }
    private record RtsFact(int initialQueueId) implements SectionFact { }
    private enum BackgroundKind { COPIED_AND_LIVE, DIRECT_CAMERA }

    static void configure(Builder b,CdR4DataLayout.Programme p) {
        var f=facts(p);b.tt=p;
        for(var r:p.ranges())b.raw(r,r.fileOffset(),r.length(),r.sha256());
        for(var operand:p.operands())b.operand(operand.fileOffset(),operand.encodedHex());
        for(var line:f.headers().strip().lines().toList()) {
            var words=line.split(" ");var range=p.range(words[0]);int header=Integer.parseInt(words[1],16);
            b.sources.add(new Compressed(range,new Span(range,range.fileOffset(),range.length(),range.sha256()),
                OptionalInt.of(header),(header&0x7FFF)*32,range.key().equals("StageBlocks")?0x2000:65536,
                Optional.empty(),Optional.empty(),true));
        }
        b.visual=new Visual(p.sonicFormat(),p.range("PlayerSprites"),p.range("PlayerGfxScript"),p.range("PlayerArtReadView"));
        b.loadRole(CdProgrammeReader.LoadRole.PLC,b.joined("PLC",f.plcOffset(),f.plcBytes(),f.plcSha()));
        b.loadRole(CdProgrammeReader.LoadRole.PALETTE,p.range("PaletteTable"));
        Sections sections=switch(f.section()) {
            case CameraFact section -> {
                b.loadRole(CdProgrammeReader.LoadRole.SECTIONS,b.joined("SECTIONS",section.offset(),section.bytes(),section.sha()));
                yield new CameraSections(section.rows());
            }
            case RtsFact section -> new InitQueueUpdateRts(section.initialQueueId());
        };
        var palettes=f.palettes().stream().map(v->new Palette(p.range(v.range()),v.destination(),v.counter())).toList();
        var cycles=f.cycles().stream().map(v->{var a=p.range(v.script());var c=p.range(v.colors());
            return new Cycle(v.kind(),new Slice(a,0,a.length()),new Slice(c,0,c.length()));}).toList();
        Background background=switch(f.background()) {
            case COPIED_AND_LIVE -> new PointerTables(List.of(
                new Pointers(PointerKind.COPIED,p.range("off_2034BA"),List.of(0xFF192E,0xFF192E,0xFF1936,0xFF193E)),
                new Pointers(PointerKind.LIVE,p.range("off_20389A"),List.of(0xFFF708,0xFFF708,0xFFF710,0xFFF718))),
                Optional.of(p.range("unk_203818")));
            case DIRECT_CAMERA -> DirectCamera.INSTANCE;
        };
        b.load=new Load(f.roots().size(),f.maximumCount(),f.roots(),f.bodies(),
            f.encountered().stream().map(name->(Object)p.range(name)).toList(),palettes,cycles,f.cycleProgramme(),sections,background);
    }
    private static Facts facts(CdR4DataLayout.Programme p) {
        return switch(p) {
            case R41A -> new Facts("""
                WaterSurfaceGfx 806E
                FlowerGfx 0027
                TitleCardTextGfx 8038
                BubbleNumbersGfx 0067
                SpikeBallGfx 0010
                ChainGfx 0008
                TurbineGfxA 0020
                CollapseFloorGfxA 0004
                ElectricBeamGfxA 0005
                ShootGfx 8012
                TonboGfx 002A
                TagaTagaGfx 001C
                YagoGfx 0024
                AmenboGfx 0020
                WoodBlockGfx 8008
                AnimalsGfx 8010
                RobotTransportGfxA 000F
                PointsGfx 0005
                CapsuleGfx 8068
                BigRingGfx 806A
                GoalGfx 8018
                SignpostGfx 804C
                ResultsGfx 8078
                TitleCardGfx 806D
                Spring45Gfx 002E
                SpringGfx 8024
                MonitorTimeGfx 8052
                ExplosionGfx 0046
                RingGfx 000E
                HudGfx 801B
                CheckpointGfx 800C
                StageBlocks 80B3
                StageGfx 8258
                BreakPoleGfxAB 8008
                SpikesV4Gfx 8010
                BlockGfxA 8020
                SwitchGfx 8006
                SlopeElevatorGfxA 8008
                SwingGfxAB 000C
                PrizePointsGfx 8002
                DoorGfxA 8010
                GeyserGfx 803C
                BigRingFlashGfx 804D
                """,
                List.of(0xFEA8,0xFEB6,0xFF1A,0xFEA8,0xFF70,0xFFC6,0xFFCE,0xFF1A,0xFF1A,0xFF1A,0xFF1A,0xFF1A,0xFF1A,0xFF1A,0xFF1A,0xFF1A,0xFF12,0xFF1A,0xFFD6),8,15,
                List.of("StageGfx","CheckpointGfx","WaterSurfaceGfx","TitleCardGfx","TitleCardTextGfx","TonboGfx","DoorGfxA","BlockGfxA","BubbleNumbersGfx","SwitchGfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","BreakPoleGfxAB","GeyserGfx","SwingGfxAB","SlopeElevatorGfxA","CollapseFloorGfxA","ChainGfx","SpikeBallGfx","TurbineGfxA","SpikesV4Gfx","AmenboGfx","TagaTagaGfx","YagoGfx","AnimalsGfx","GoalGfx","RobotTransportGfxA","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
                List.of(new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("PlayerPaletteFullRead",0xFB00,31),new PaletteFact("PlayerPalette",0xFB00,7),new PaletteFact("StagePalette",0xFB20,23)),
                List.of(new CycleFact(CycleKind.UPPER,"R4PaletteCycleAct1UpperGroup1Timing","R4PaletteCycleAct1UpperGroup1Colors"),new CycleFact(CycleKind.UPPER,"R4PaletteCycleAct1UpperGroup2Timing","R4PaletteCycleAct1UpperGroup2Colors"),new CycleFact(CycleKind.UPPER,"R4PaletteCycleAct1UpperGroup3Timing","R4PaletteCycleAct1UpperGroup3Colors"),new CycleFact(CycleKind.UPPER,"R4PaletteCycleAct1UpperGroup4Timing","R4PaletteCycleAct1UpperGroup4Colors"),new CycleFact(CycleKind.UPPER,"R4PaletteCycleAct1UpperGroup5Timing","R4PaletteCycleAct1UpperGroup5Colors"),new CycleFact(CycleKind.UPPER,"R4PaletteCycleAct1UpperGroup6Timing","R4PaletteCycleAct1UpperGroup6Colors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup1Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup2Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup3Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup4Timing","R4PaletteCycleColors")),
                CycleProgramme.UPPER_SIX_OR_STANDARD_FOUR,0xFE82,360,"19969be448699bdbae24e60a1443814b39ccc7245c32100f68b3e9928223e8d5",
                new CameraFact(0xC03E,18,"6b1762bbd486c4cf403af6b288610fcc68fdfba179ee706b7dfadf081b3e9276",List.of(new SectionRow(0x1280,2,5),new SectionRow(0x1500,4,6),new SectionRow(0xFFFF,2,5))),BackgroundKind.COPIED_AND_LIVE);
            case R41B -> new Facts("""
                BreakPoleGfxAB 8008
                SpikesV4Gfx 8010
                BlockGfxB 0020
                SwitchGfx 8006
                SwingGfxAB 000C
                DoorGfxB 0010
                UnusedPlatformGfxBCD 8020
                RotateBlockGfx 804C
                SpikeBallGfx 0010
                ChainGfx 0008
                TurbineGfxB 8020
                CollapseFloorGfxB 0004
                ElectricBeamGfxB 0005
                ShootGfx 8012
                TonboGfx 002A
                TagaTagaGfx 001C
                YagoGfx 0024
                AmenboGfx 0020
                RobotTransportGfxB 0017
                HologramGfx 0033
                PointsGfx 0005
                CapsuleGfx 8068
                BigRingGfx 806A
                GoalGfx 8018
                SignpostGfx 804C
                ResultsGfx 8078
                TitleCardGfx 806D
                Spring45Gfx 002E
                SpringGfx 8024
                MonitorTimeGfx 8052
                ExplosionGfx 0046
                RingGfx 000E
                HudGfx 801B
                CheckpointGfx 800C
                WaterSurfaceGfx 806E
                FlowerGfx 0027
                TitleCardTextGfx 8038
                BubbleNumbersGfx 0067
                StageBlocks 808E
                StageGfx 8236
                AnimalsGfx 8010
                BigRingFlashGfx 804D
                PrizePointsGfx 8002
                GeyserGfx 803C
                WoodBlockGfx 8008
                """,
                List.of(0xF848,0xF850,0xF8B4,0xF848,0xF8EC,0xF91E,0xF956,0xF96A,0xF978,0xF8B4,0xF8B4,0xF8B4,0xF8B4,0xF8B4,0xF8B4,0xF8B4,0xF8AC,0xF8B4,0xF980),10,15,
                List.of("StageGfx","WaterSurfaceGfx","TitleCardGfx","TitleCardTextGfx","TonboGfx","DoorGfxB","BlockGfxB","BubbleNumbersGfx","SwitchGfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","BreakPoleGfxAB","RotateBlockGfx","TurbineGfxB","SpikesV4Gfx","AmenboGfx","TagaTagaGfx","YagoGfx","AnimalsGfx","GoalGfx","HologramGfx","RobotTransportGfxB","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
                List.of(new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1LevelSelectPalette",0xFB00,31),new PaletteFact("PlayerPalette",0xFB00,7),new PaletteFact("StagePalette",0xFB20,23)),
                List.of(new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup1Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup2Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup3Timing","R4PaletteCycleColors")),
                CycleProgramme.THREE_SOURCE_ACT_GATED,0xF822,370,"f75a5484ac43cf221699eb53352c4d48f876ab0ec092fd9a7a818c08a94132b7",
                new CameraFact(0xC0D8,30,"75fe3787aa514dd398c0e21ea8bee62ba89257915d41930425333a6f1d86b687",List.of(new SectionRow(0x680,2,6),new SectionRow(0xA80,4,7),new SectionRow(0x1280,2,6),new SectionRow(0x1680,5,8),new SectionRow(0xFFFF,2,6))),BackgroundKind.DIRECT_CAMERA);
            case R41C -> new Facts("""
                BreakPoleGfxC 8008
                SpikesV4Gfx 8010
                UnusedPlatformGfxC 8010
                BlockGfxC 8020
                SwitchGfx 8006
                SwingGfxC 000C
                DoorGfxC 8010
                RotateBlockGfx 804C
                UnusedPlatformGfxBCD 8020
                SlopeElevatorGfxC 8008
                SpikeBallGfx 0010
                ChainGfx 0008
                TurbineGfxC 0010
                CollapseFloorGfxC 0004
                ElectricBeamGfxC 0005
                ShootGfx 8012
                TonboGfx 002A
                TagaTagaGfx 001C
                YagoGfx 0024
                FloatElevatorGfxC 8020
                AmenboGfx 0020
                WoodBlockGfx 8008
                AnimalsGfx 8010
                PointsGfx 0005
                CapsuleGfx 8068
                BigRingGfx 806A
                GoalGfx 8018
                SignpostGfx 804C
                ResultsGfx 8078
                TitleCardGfx 806D
                Spring45Gfx 002E
                SpringGfx 8024
                MonitorTimeGfx 8052
                ExplosionGfx 0046
                RingGfx 000E
                HudGfx 801B
                CheckpointGfx 800C
                WaterSurfaceGfx 806E
                FlowerGfx 0027
                TitleCardTextGfx 8038
                BubbleNumbersGfx 0067
                StageBlocks 80E9
                StageGfx 821F
                PrizePointsGfx 8002
                BigRingFlashGfx 804D
                GeyserGfx 803C
                """,
                List.of(0xF4A2,0xF4AA,0xF506,0xF4A2,0xF506,0xF506,0xF506,0xF506,0xF506,0xF506,0xF506,0xF506,0xF506,0xF506,0xF506,0xF506,0xF550,0xF506,0xF558),5,15,
                List.of("StageGfx","WaterSurfaceGfx","TitleCardGfx","TitleCardTextGfx","DoorGfxC","AnimalsGfx","BlockGfxC","BubbleNumbersGfx","SwitchGfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","BreakPoleGfxC","SwingGfxC","SlopeElevatorGfxC","PrizePointsGfx","SpikeBallGfx","TurbineGfxC","SpikesV4Gfx","AmenboGfx","TagaTagaGfx","YagoGfx","TonboGfx","GoalGfx","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
                List.of(new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1LevelSelectPalette",0xFB00,31),new PaletteFact("PlayerPalette",0xFB00,7),new PaletteFact("StagePalette",0xFB20,23),new PaletteFact("Pal_2006AC",0xFB20,7)),
                List.of(new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup1Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup2Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup3Timing","R4PaletteCycleColors")),
                CycleProgramme.THREE_SOURCE_ACT_GATED,0xF47C,240,"56d336690714f59fb45badced63b1aa20cbb1fb736080963c69cb2eec53291c9",
                new RtsFact(2),BackgroundKind.DIRECT_CAMERA);
            case R41D -> new Facts("""
                BreakPoleGfxD 0008
                SpikesV4Gfx 8010
                UnusedPlatformGfxD 8010
                BlockGfxD 8020
                SwitchGfx 8006
                SwingGfxD 000C
                PrizePointsGfx 8002
                DoorGfxD 8010
                GeyserGfx 803C
                UnusedPlatformGfxBCD 8020
                SlopeElevatorGfxD 8008
                SpikeBallGfx 0010
                ChainGfx 0008
                TurbineGfxD 0010
                CollapseFloorGfxD 0004
                ElectricBeamGfxD 0005
                ShootGfx 8012
                TonboGfx 002A
                TagaTagaGfx 001C
                YagoGfx 0024
                FloatElevatorGfxD 8020
                AmenboGfx 0020
                WoodBlockGfx 8008
                PointsGfx 0005
                CapsuleGfx 8068
                BigRingGfx 806A
                GoalGfx 8018
                SignpostGfx 804C
                ResultsGfx 8078
                TitleCardGfx 806D
                Spring45Gfx 002E
                SpringGfx 8024
                MonitorTimeGfx 8052
                ExplosionGfx 0046
                RingGfx 000E
                HudGfx 801B
                CheckpointGfx 800C
                WaterSurfaceGfx 806E
                FlowerGfx 0027
                TitleCardTextGfx 8038
                BubbleNumbersGfx 0067
                StageBlocks 8096
                StageGfx 8237
                AnimalsGfx 8010
                BigRingFlashGfx 804D
                """,
                List.of(0xF490,0xF498,0xF4F4,0xF490,0xF4F4,0xF4F4,0xF4F4,0xF4F4,0xF4F4,0xF4F4,0xF4F4,0xF4F4,0xF4F4,0xF4F4,0xF4F4,0xF4F4,0xF53E,0xF4F4,0xF546),5,15,
                List.of("StageGfx","WaterSurfaceGfx","TitleCardGfx","TitleCardTextGfx","DoorGfxD","AnimalsGfx","BlockGfxD","BubbleNumbersGfx","SwitchGfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","BreakPoleGfxD","SwingGfxD","SlopeElevatorGfxD","PrizePointsGfx","SpikeBallGfx","TurbineGfxD","SpikesV4Gfx","AmenboGfx","TagaTagaGfx","YagoGfx","TonboGfx","GoalGfx","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
                List.of(new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1LevelSelectPalette",0xFB00,31),new PaletteFact("PlayerPalette",0xFB00,7),new PaletteFact("StagePalette",0xFB20,23),new PaletteFact("Pal_20069A",0xFB20,7)),
                List.of(new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup1Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup2Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup3Timing","R4PaletteCycleColors")),
                CycleProgramme.THREE_SOURCE_ACT_GATED,0xF46A,240,"e294fe6fd79aab1b227d867ff2f501f07081ee437b3a1232351018679f70d45c",
                new RtsFact(2),BackgroundKind.DIRECT_CAMERA);
            case R42A -> new Facts("""
                SpikeBallGfx 0010
                ChainGfx 0008
                TurbineGfxA 0020
                CollapseFloorGfxA 0004
                ElectricBeamGfxA 0005
                ShootGfx 8012
                TonboGfx 002A
                RobotTransportGfxA 000F
                PointsGfx 0005
                CapsuleGfx 8068
                BigRingGfx 806A
                GoalGfx 8018
                SignpostGfx 804C
                ResultsGfx 8078
                TitleCardGfx 806D
                Spring45Gfx 002E
                SpringGfx 8024
                MonitorTimeGfx 8052
                ExplosionGfx 0046
                RingGfx 000E
                HudGfx 801B
                CheckpointGfx 800C
                WaterSurfaceGfx 806E
                FlowerGfx 0027
                TitleCardTextGfx 8038
                BubbleNumbersGfx 0067
                StageBlocks 8071
                StageGfx 8223
                BreakPoleGfxAB 8008
                SpikesV4Gfx 8010
                BlockGfxA 8020
                SwitchGfx 8006
                SlopeElevatorGfxA 8008
                SwingGfxAB 000C
                PrizePointsGfx 8002
                DoorGfxA 8010
                WoodBlockGfx 8008
                AmenboGfx 0020
                TagaTagaGfx 001C
                YagoGfx 0024
                AnimalsGfx 8010
                BigRingFlashGfx 804D
                """,
                List.of(0xFADE,0xFAEC,0xFB50,0xFADE,0xFBAC,0xFC08,0xFC10,0xFB50,0xFB50,0xFB50,0xFB50,0xFB50,0xFB50,0xFB50,0xFB50,0xFB50,0xFB48,0xFB50,0xFC18,0xFC2C),9,15,
                List.of("StageGfx","CheckpointGfx","WaterSurfaceGfx","TitleCardGfx","TitleCardTextGfx","TonboGfx","DoorGfxA","BlockGfxA","BubbleNumbersGfx","SwitchGfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","ShootGfx","ElectricBeamGfxA","SwingGfxAB","WoodBlockGfx","SlopeElevatorGfxA","CollapseFloorGfxA","ChainGfx","SpikeBallGfx","TurbineGfxA","SpikesV4Gfx","AmenboGfx","TagaTagaGfx","YagoGfx","AnimalsGfx","BreakPoleGfxAB","RobotTransportGfxA","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx","GoalGfx"),
                List.of(new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("PlayerPaletteFullRead",0xFB00,31),new PaletteFact("PlayerPalette",0xFB00,7),new PaletteFact("StagePalette",0xFB20,23)),
                List.of(new CycleFact(CycleKind.UPPER,"R4PaletteCycleAct1UpperGroup1Timing","R4PaletteCycleAct1UpperGroup1Colors"),new CycleFact(CycleKind.UPPER,"R4PaletteCycleAct1UpperGroup2Timing","R4PaletteCycleAct1UpperGroup2Colors"),new CycleFact(CycleKind.UPPER,"R4PaletteCycleAct1UpperGroup3Timing","R4PaletteCycleAct1UpperGroup3Colors"),new CycleFact(CycleKind.UPPER,"R4PaletteCycleAct1UpperGroup4Timing","R4PaletteCycleAct1UpperGroup4Colors"),new CycleFact(CycleKind.UPPER,"R4PaletteCycleAct1UpperGroup5Timing","R4PaletteCycleAct1UpperGroup5Colors"),new CycleFact(CycleKind.UPPER,"R4PaletteCycleAct1UpperGroup6Timing","R4PaletteCycleAct1UpperGroup6Colors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup1Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup2Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup3Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup4Timing","R4PaletteCycleColors")),
                CycleProgramme.STANDARD_FOUR_WITH_RETAINED_UPPER,0xFAB6,382,"b6c4e7ded1a8998c8d404c29120a20479c77e6e819668b54d564d74dea3d26ab",
                new CameraFact(0xBF94,18,"34dedd9006846648e333e778e1f76def642dcc16bc7b9034a9b7d0a5b0f9093c",List.of(new SectionRow(0x1180,2,5),new SectionRow(0x1580,4,6),new SectionRow(0xFFFF,2,5))),BackgroundKind.DIRECT_CAMERA);
            case R42B -> new Facts("""
                BreakPoleGfxAB 8008
                SpikesV4Gfx 8010
                BlockGfxB 0020
                SwitchGfx 8006
                SwingGfxAB 000C
                PrizePointsGfx 8002
                DoorGfxB 0010
                GeyserGfx 803C
                UnusedPlatformGfxBCD 8020
                RotateBlockGfx 804C
                SpikeBallGfx 0010
                ChainGfx 0008
                TurbineGfxB 8020
                CollapseFloorGfxB 0004
                ElectricBeamGfxB 0005
                ShootGfx 8012
                TonboGfx 002A
                TagaTagaGfx 001C
                YagoGfx 0024
                AmenboGfx 0020
                WoodBlockGfx 8008
                AnimalsGfx 8010
                RobotTransportGfxB 0017
                HologramGfx 0033
                PointsGfx 0005
                CapsuleGfx 8068
                BigRingGfx 806A
                GoalGfx 8018
                SignpostGfx 804C
                ResultsGfx 8078
                TitleCardGfx 806D
                Spring45Gfx 002E
                SpringGfx 8024
                MonitorTimeGfx 8052
                ExplosionGfx 0046
                RingGfx 000E
                HudGfx 801B
                CheckpointGfx 800C
                WaterSurfaceGfx 806E
                FlowerGfx 0027
                TitleCardTextGfx 8038
                BubbleNumbersGfx 0067
                StageBlocks 808E
                StageGfx 8236
                BigRingFlashGfx 804D
                """,
                List.of(0xFC4E,0xFC56,0xFCBA,0xFC4E,0xFCF8,0xFD30,0xFD6E,0xFD82,0xFD90,0xFCBA,0xFCBA,0xFCBA,0xFCBA,0xFCBA,0xFCBA,0xFCBA,0xFCB2,0xFCBA,0xFDA4),10,15,
                List.of("StageGfx","WaterSurfaceGfx","TitleCardGfx","TitleCardTextGfx","TonboGfx","DoorGfxB","BlockGfxB","BubbleNumbersGfx","SwitchGfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","BreakPoleGfxAB","RotateBlockGfx","CollapseFloorGfxB","TurbineGfxB","SpikesV4Gfx","AmenboGfx","TagaTagaGfx","YagoGfx","AnimalsGfx","GoalGfx","HologramGfx","RobotTransportGfxB","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
                List.of(new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1LevelSelectPalette",0xFB00,31),new PaletteFact("PlayerPalette",0xFB00,7),new PaletteFact("StagePalette",0xFB20,23)),
                List.of(new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup1Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup2Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup3Timing","R4PaletteCycleColors")),
                CycleProgramme.THREE_SOURCE_ACT_GATED,0xFC28,400,"c74d275900bdfdd01e5ad5e0c6548caca556d7b95e2b37f38a08a3eda725fc60",
                new CameraFact(0xC19C,30,"698a1e79b364ca76e21c18375f8d5e72126e2e5e3bece9a52e9515d85ba9fc1f",List.of(new SectionRow(0xA80,2,6),new SectionRow(0xE80,4,7),new SectionRow(0x1000,2,6),new SectionRow(0x1F80,5,8),new SectionRow(0xFFFF,2,6))),BackgroundKind.DIRECT_CAMERA);
            case R42C -> new Facts("""
                BreakPoleGfxC 8008
                SpikesV4Gfx 8010
                UnusedPlatformGfxC 8010
                BlockGfxC 8020
                SwitchGfx 8006
                SwingGfxC 000C
                PrizePointsGfx 8002
                DoorGfxC 8010
                RotateBlockGfx 804C
                GeyserGfx 803C
                UnusedPlatformGfxBCD 8020
                SlopeElevatorGfxC 8008
                SpikeBallGfx 0010
                ChainGfx 0008
                TurbineGfxC 0010
                CollapseFloorGfxC 0004
                ElectricBeamGfxC 0005
                ShootGfx 8012
                TonboGfx 002A
                TagaTagaGfx 001C
                YagoGfx 0024
                FloatElevatorGfxC 8020
                AmenboGfx 0020
                WoodBlockGfx 8008
                AnimalsGfx 8010
                PointsGfx 0005
                CapsuleGfx 8068
                BigRingGfx 806A
                GoalGfx 8018
                SignpostGfx 804C
                ResultsGfx 8078
                TitleCardGfx 806D
                Spring45Gfx 002E
                SpringGfx 8024
                MonitorTimeGfx 8052
                ExplosionGfx 0046
                RingGfx 000E
                HudGfx 801B
                CheckpointGfx 800C
                WaterSurfaceGfx 806E
                FlowerGfx 0027
                TitleCardTextGfx 8038
                BubbleNumbersGfx 0067
                StageBlocks 80E9
                StageGfx 821F
                BigRingFlashGfx 804D
                """,
                List.of(0xFDEE,0xFDF6,0xFE52,0xFDEE,0xFE52,0xFE52,0xFE52,0xFE52,0xFE52,0xFE52,0xFE52,0xFE52,0xFE52,0xFE52,0xFE52,0xFE52,0xFEA2,0xFE52,0xFEAA),5,15,
                List.of("StageGfx","WaterSurfaceGfx","TitleCardGfx","TitleCardTextGfx","DoorGfxC","AnimalsGfx","BlockGfxC","BubbleNumbersGfx","SwitchGfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","BreakPoleGfxC","FloatElevatorGfxC","UnusedPlatformGfxC","SwingGfxC","CollapseFloorGfxC","SpikeBallGfx","TurbineGfxC","SpikesV4Gfx","AmenboGfx","TagaTagaGfx","YagoGfx","TonboGfx","GoalGfx","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
                List.of(new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1LevelSelectPalette",0xFB00,31),new PaletteFact("PlayerPalette",0xFB00,7),new PaletteFact("StagePalette",0xFB20,23),new PaletteFact("Pal_2006AC",0xFB20,7)),
                List.of(new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup1Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup2Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup3Timing","R4PaletteCycleColors")),
                CycleProgramme.THREE_SOURCE_ACT_GATED,0xFDC8,246,"8f833347ebfcdc63f2c194ded768b21e15a066d8095b8c6385411a2ea53f528f",
                new RtsFact(2),BackgroundKind.DIRECT_CAMERA);
            case R42D -> new Facts("""
                BreakPoleGfxD 0008
                SpikesV4Gfx 8010
                UnusedPlatformGfxD 8010
                BlockGfxD 8020
                SwitchGfx 8006
                SwingGfxD 000C
                PrizePointsGfx 8002
                DoorGfxD 8010
                GeyserGfx 803C
                UnusedPlatformGfxBCD 8020
                SlopeElevatorGfxD 8008
                SpikeBallGfx 0010
                ChainGfx 0008
                TurbineGfxD 0010
                CollapseFloorGfxD 0004
                ElectricBeamGfxD 0005
                ShootGfx 8012
                TonboGfx 002A
                TagaTagaGfx 001C
                YagoGfx 0024
                FloatElevatorGfxD 8020
                AmenboGfx 0020
                WoodBlockGfx 8008
                PointsGfx 0005
                CapsuleGfx 8068
                BigRingGfx 806A
                GoalGfx 8018
                SignpostGfx 804C
                ResultsGfx 8078
                TitleCardGfx 806D
                Spring45Gfx 002E
                SpringGfx 8024
                MonitorTimeGfx 8052
                ExplosionGfx 0046
                RingGfx 000E
                HudGfx 801B
                CheckpointGfx 800C
                WaterSurfaceGfx 806E
                FlowerGfx 0027
                TitleCardTextGfx 8038
                BubbleNumbersGfx 0067
                StageBlocks 808F
                StageGfx 8240
                AnimalsGfx 8010
                BigRingFlashGfx 804D
                """,
                List.of(0xFDDC,0xFDE4,0xFE40,0xFDDC,0xFE40,0xFE40,0xFE40,0xFE40,0xFE40,0xFE40,0xFE40,0xFE40,0xFE40,0xFE40,0xFE40,0xFE40,0xFE90,0xFE40,0xFE98),5,15,
                List.of("StageGfx","WaterSurfaceGfx","TitleCardGfx","TitleCardTextGfx","DoorGfxD","AnimalsGfx","BlockGfxD","BubbleNumbersGfx","SwitchGfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","BreakPoleGfxD","FloatElevatorGfxD","UnusedPlatformGfxD","SwingGfxD","CollapseFloorGfxD","SpikeBallGfx","TurbineGfxD","SpikesV4Gfx","AmenboGfx","TagaTagaGfx","YagoGfx","TonboGfx","GoalGfx","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
                List.of(new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1LevelSelectPalette",0xFB00,31),new PaletteFact("PlayerPalette",0xFB00,7),new PaletteFact("StagePalette",0xFB20,23),new PaletteFact("Pal_20069A",0xFB20,7)),
                List.of(new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup1Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup2Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup3Timing","R4PaletteCycleColors")),
                CycleProgramme.THREE_SOURCE_ACT_GATED,0xFDB6,246,"5cf8964af93388b728c74ed729fdc21d90aca02a27c78284cf8fcb7945e6fcd7",
                new RtsFact(2),BackgroundKind.DIRECT_CAMERA);
            case R43C -> new Facts("""
                PointsGfx 0005
                CapsuleGfx 8068
                BigRingGfx 806A
                GoalGfx 8018
                SignpostGfx 804C
                ResultsGfx 8078
                TitleCardGfx 806D
                Spring45Gfx 002E
                SpringGfx 8024
                MonitorTimeGfx 8052
                ExplosionGfx 0046
                RingGfx 000E
                HudGfx 801B
                CheckpointGfx 800C
                WaterSurfaceGfx 806E
                FlowerGfx 0027
                TitleCardTextGfx 8038
                BubbleNumbersGfx 0067
                StageBlocks 80E9
                StageGfx 821F
                TonboGfx 002A
                DoorGfxC 8010
                DoorGfxD 8010
                BlockGfxC 8020
                BlockGfxD 8020
                AnimalsGfx 8010
                SwitchGfx 8006
                BossChaseGfx 80A6
                BossBubbleGfx 00FA
                SwingGfxC 000C
                SwingGfxD 000C
                """,
                List.of(0xD244,0xD24C,0xD290,0xD244,0xD2BC,0xD290,0xD290,0xD290,0xD290,0xD290,0xD290,0xD290,0xD290,0xD290,0xD290,0xD290,0xD2C4,0xD290,0xD2CC),6,11,
                List.of("StageGfx","WaterSurfaceGfx","TitleCardGfx","TitleCardTextGfx","BubbleNumbersGfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","BossChaseGfx","SwingGfxC","TonboGfx","DoorGfxC","AnimalsGfx","BlockGfxC","SwitchGfx","BossBubbleGfx","ResultsGfx","CapsuleGfx"),
                List.of(new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1LevelSelectPalette",0xFB00,31),new PaletteFact("PlayerPalette",0xFB00,7),new PaletteFact("StagePalette",0xFB20,23),new PaletteFact("Pal_2006AC",0xFB20,7)),
                List.of(new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup1Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup2Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup3Timing","R4PaletteCycleColors")),
                CycleProgramme.THREE_SOURCE_ACT_GATED,0xD21E,182,"78e14560fa4c0d201231605eca94ff8294d00a2c31f3c72f5675f9f7ff50507f",
                new RtsFact(2),BackgroundKind.DIRECT_CAMERA);
            case R43D -> new Facts("""
                PointsGfx 0005
                CapsuleGfx 8068
                BigRingGfx 806A
                GoalGfx 8018
                SignpostGfx 804C
                ResultsGfx 8078
                TitleCardGfx 806D
                Spring45Gfx 002E
                SpringGfx 8024
                MonitorTimeGfx 8052
                ExplosionGfx 0046
                RingGfx 000E
                HudGfx 801B
                CheckpointGfx 800C
                WaterSurfaceGfx 806E
                FlowerGfx 0027
                TitleCardTextGfx 8038
                BubbleNumbersGfx 0067
                StageBlocks 80B0
                StageGfx 8237
                TonboGfx 002A
                DoorGfxC 8010
                DoorGfxD 8010
                BlockGfxC 8020
                BlockGfxD 8020
                AnimalsGfx 8010
                SwitchGfx 8006
                BossChaseGfx 80A6
                BossBubbleGfx 00FA
                SwingGfxD 000C
                SwingGfxC 000C
                """,
                List.of(0xD258,0xD260,0xD2A4,0xD258,0xD2D0,0xD2A4,0xD2A4,0xD2A4,0xD2A4,0xD2A4,0xD2A4,0xD2A4,0xD2A4,0xD2A4,0xD2A4,0xD2A4,0xD2D8,0xD2A4,0xD2E0),6,11,
                List.of("StageGfx","WaterSurfaceGfx","TitleCardGfx","TitleCardTextGfx","BubbleNumbersGfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","BossChaseGfx","SwingGfxD","TonboGfx","DoorGfxD","AnimalsGfx","BlockGfxD","SwitchGfx","BossBubbleGfx","ResultsGfx","CapsuleGfx"),
                List.of(new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1TitlePalette",0xFB00,31),new PaletteFact("S1LevelSelectPalette",0xFB00,31),new PaletteFact("PlayerPalette",0xFB00,7),new PaletteFact("StagePalette",0xFB20,23),new PaletteFact("Pal_20069A",0xFB20,7)),
                List.of(new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup1Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup2Timing","R4PaletteCycleColors"),new CycleFact(CycleKind.STANDARD,"R4PaletteCycleGroup3Timing","R4PaletteCycleColors")),
                CycleProgramme.THREE_SOURCE_ACT_GATED,0xD232,182,"e922b604de9a1f6c54544715a53d72472b4928ead3f05d113018f386b7d3f9fc",
                new RtsFact(2),BackgroundKind.DIRECT_CAMERA);
        };
    }
}
