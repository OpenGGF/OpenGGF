// Sonic CD lag probe (LAG lane, 2026-11-13).
// Purpose: say what the stage main loop was doing at every recorder sample. Replays a stage
// movie.bk2 from power-on exactly as the TraceChaser --scd-trace recorder does (one
// FrameAdvance per BK2 row, read-only RAM polling afterwards) and, on every frame where the
// stage has started, writes the 68K PC, SR and A7, the eight longs on top of the stack, and
// the long at $FFFCE8 (Level_MainLoop's stack slot: the return address of the main-loop call in
// progress; the slot holds VSync's return while the pass waits), and the counters the recorder
// samples (levelFrames, levelVIntCounter, vintRoutine) and the V-INT-owned state the census asks
// about (ctrlLocked $FFF7CC, plcBuffer head $FFF680, waterHeight $FFF646, waterSwayAngle $FF1955,
// timeWarpTimer $FFF786, boredTimer $FFF780; Level/_Definitions.inc:215-327, Main CPU Variables.inc:182), so
// lag_census.py can place each sample inside V-INT, inside a main-loop call or in VSync.
// It also dumps the 256 KB of Word RAM once, at the first started frame, so the analysis can
// find Level_MainLoop's call sites in the running stage program.
// Inputs: USA CUE, firmware dir, BIOS SHA-1, movie.bk2, new output dir. Output: lag.csv and
// wordram.bin. Origin: derived from rehost-fixtures/_tools/scd_route_probe.cs (TRACES lane);
// compiled with tools/soniccd/oracle_probe.cs (Firmware) by lag_native_probe.sh.
using System; using System.IO; using System.IO.Compression; using System.Linq; using System.Collections.Generic;
using BizHawk.Emulation.Common; using BizHawk.Emulation.Cores;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx; using BizHawk.Emulation.DiscSystem;
internal sealed class LagPad : IController
{
    public ControllerDefinition Definition { get; set; }
    public HashSet<string> Buttons = new HashSet<string>();
    public bool IsPressed(string name) { return Buttons.Contains(name); }
    public int AxisValue(string name) { return 0; }
    public IReadOnlyCollection<(string Name, int Strength)> GetHapticsSnapshot() { return new (string Name, int Strength)[0]; }
    public void SetHapticChannelStrength(string name,int strength) { }
}
internal static class LagProbe
{
    static int U8(MemoryDomain d,long a){return d.PeekByte(a);}
    static int U16(MemoryDomain d,long a){return (d.PeekByte(a)<<8)|d.PeekByte(a+1);}
    static long U32(MemoryDomain d,long a){return ((long)U16(d,a)<<16)|(long)U16(d,a+2);}
    // BK2 LogKey #Power|Reset|#P1 Up|Down|Left|Right|A|B|C|Start|#P2 ... ; one line per row.
    static List<string[]> ReadBk2(string path)
    {
        var rows=new List<string[]>();
        using(var z=ZipFile.OpenRead(path))
        using(var r=new StreamReader(z.GetEntry("Input Log.txt").Open()))
        {
            string line;
            while((line=r.ReadLine())!=null)
            {
                if(!line.StartsWith("|"))continue;
                rows.Add(line.Split('|'));
            }
        }
        return rows;
    }
    static readonly string[] P1={"Up","Down","Left","Right","A","B","C","Start"};
    public static int Main(string[] args)
    {
        try {
        var fw=new Firmware(args[1],args[2]); var disc=Disc.LoadAutomagic(args[0]);
        var bk2=ReadBk2(args[3]);
        string output=Path.GetFullPath(args[4]); if(Directory.Exists(output))throw new IOException("Output exists"); Directory.CreateDirectory(output);
        using(var c=new GPGX(new CoreLoadParameters<GPGX.GPGXSettings,GPGX.GPGXSyncSettings>{
            Comm=new CoreComm(m=>{},(m,n)=>{},fw,CoreComm.CorePreferencesFlags.None,null),
            Game=new GameInfo{Name="Sonic CD lag probe",System="GEN"},
            Settings=new GPGX.GPGXSettings(),SyncSettings=new GPGX.GPGXSyncSettings(),
            Discs={new CdAsset{DiscData=disc,DiscName=Path.GetFileName(args[0])}},DeterministicEmulationRequested=true}))
        {
            var dom=c.ServiceProvider.GetService<IMemoryDomains>(); var ram=dom["68K RAM"]; var word=dom["CD WORD RAM (2M)"];
            var dbg=c.ServiceProvider.GetService<IDebuggable>();
            var pad=new LagPad{Definition=c.ControllerDefinition};
            bool dumped=false;
            using(var log=new StreamWriter(Path.Combine(output,"lag.csv")))
            {
                log.WriteLine("row,lagged,levelStarted,gameMode,vintRoutine,levelFrames,vint,paused,ctrlLocked,plcHead,waterY,waterAngle,warpTimer,boredTimer,pc,sr,sp,mlret,stack");
                for(int f=0;f<bk2.Count;f++)
                {
                    pad.Buttons.Clear();
                    string p1=bk2[f].Length>2?bk2[f][2]:"";
                    for(int i=0;i<P1.Length&&i<p1.Length;i++) if(p1[i]!='.')pad.Buttons.Add("P1 "+P1[i]);
                    c.FrameAdvance(pad,true,true); short[] a;int n;c.GetSamplesSync(out a,out n);
                    int started=U8(ram,0x1957);
                    if(started!=1)continue;
                    var regs=dbg.GetCpuFlagsAndRegisters();
                    if(!dumped)
                    {
                        using(var s=File.Create(Path.Combine(output,"wordram.bin")))
                            for(int i=0;i<0x40000;i++)s.WriteByte(word.PeekByte(i));
                        File.WriteAllLines(Path.Combine(output,"registers.txt"),regs.Select(kv=>kv.Key+"="+kv.Value.Value.ToString("X")));
                        dumped=true;
                    }
                    long pc=(long)regs["M68K PC"].Value, sr=(long)regs["M68K SR"].Value, sp=(long)regs["M68K A7"].Value;
                    var st=new List<string>();
                    for(int k=0;k<8;k++)
                    {
                        long addr=(sp+4*k)&0xFFFFFF;
                        st.Add(addr>=0xFF0000?U32(ram,addr-0xFF0000).ToString("X8"):"--------");
                    }
                    log.WriteLine(string.Join(",",f,c.IsLagFrame?1:0,started,U8(ram,0xF600).ToString("X2"),U8(ram,0xF62A).ToString("X2"),
                        U16(ram,0x1504).ToString("X4"),U16(ram,0x190C).ToString("X4"),U8(ram,0xF63A).ToString("X2"),
                        U8(ram,0xF7CC).ToString("X2"),U32(ram,0xF680).ToString("X8"),U16(ram,0xF646).ToString("X4"),
                        U8(ram,0x1955).ToString("X2"),U16(ram,0xF786).ToString("X4"),U16(ram,0xF780).ToString("X4"),
                        pc.ToString("X6"),sr.ToString("X4"),sp.ToString("X6"),U32(ram,0xFCE8).ToString("X8"),string.Join(" ",st)));
                }
            }
        }
        return 0;
        } catch(Exception e){Console.Error.WriteLine(e);return 2;}
    }
}
