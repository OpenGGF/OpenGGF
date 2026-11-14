// Sonic CD video, ending and secret-program native probe (lane END, 2026-11-12).
// Purpose: drive the USA disc through BizHawk 2.11 GPGX by a button schedule and record what the engine's
// video player and program hosts are compared against: per-frame program variables (any bytes of any
// memory domain, named in the schedule), the lag flag, video memory dumps, post-frame pictures and the
// whole audio stream with its per-frame sample counts (for audio/video alignment).
// Inputs: USA CUE, firmware dir, output dir (new), schedule JSON, BIOS SHA-1. Schedule keys:
//   frames, inputs[{first,last,button}] (1-based probe frames = BK2 row + 1), screenshots[], vram_dumps[]
//   (VRAM, CRAM, VSRAM, 68K RAM), screenshot_every (optional: a picture every N frames inside
//   [screenshot_from, screenshot_to]), peeks[{name,domain,address,size}] (CSV columns, big-endian),
//   pokes[{frame,domain,address,bytes}] (setup writes before that frame), audio (bool: write audio.s16).
// Built with tools/soniccd/fmv_native_probe.sh against tools/soniccd/oracle_probe.cs (frozen branch).
using System; using System.IO; using System.Linq; using System.Collections.Generic;
using BizHawk.Emulation.Common; using BizHawk.Emulation.Cores;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx; using BizHawk.Emulation.DiscSystem;
using Newtonsoft.Json.Linq;
internal sealed class FmvPad : IController
{
    public ControllerDefinition Definition { get; set; }
    public HashSet<string> Buttons = new HashSet<string>();
    public bool IsPressed(string name) { return Buttons.Contains(name); }
    public int AxisValue(string name) { return 0; }
    public IReadOnlyCollection<(string Name, int Strength)> GetHapticsSnapshot() { return new (string Name, int Strength)[0]; }
    public void SetHapticChannelStrength(string name,int strength) { }
}
internal static class FmvProbe
{
    static byte[] Dump(MemoryDomain d){var b=new byte[d.Size];for(long i=0;i<d.Size;i++)b[i]=d.PeekByte(i);return b;}
    static void Ppm(GPGX c,string file)
    {
        using(var s=File.Create(file))
        {
            byte[] h=System.Text.Encoding.ASCII.GetBytes("P6\n"+c.BufferWidth+" "+c.BufferHeight+"\n255\n");s.Write(h,0,h.Length);
            var p=c.GetVideoBuffer();
            for(int i=0;i<c.BufferWidth*c.BufferHeight;i++){s.WriteByte((byte)(p[i]>>16));s.WriteByte((byte)(p[i]>>8));s.WriteByte((byte)p[i]);}
        }
    }
    static HashSet<int> Set(JObject s,string k){return new HashSet<int>(s[k]==null?new int[0]:s[k].Select(t=>(int)t).ToArray());}
    public static int Main(string[] args)
    {
        try {
        var sched=JObject.Parse(File.ReadAllText(args[3]));
        int frames=(int)sched["frames"];
        string output=Path.GetFullPath(args[2]); if(Directory.Exists(output))throw new IOException("Output exists"); Directory.CreateDirectory(output);
        var fw=new Firmware(args[1],args[4]);var disc=Disc.LoadAutomagic(args[0]);
        using(var c=new GPGX(new CoreLoadParameters<GPGX.GPGXSettings,GPGX.GPGXSyncSettings>{
            Comm=new CoreComm(m=>{},(m,n)=>{},fw,CoreComm.CorePreferencesFlags.None,null),
            Game=new GameInfo{Name="Sonic CD program probe",System="GEN"},
            Settings=new GPGX.GPGXSettings(),SyncSettings=new GPGX.GPGXSyncSettings(),
            Discs={new CdAsset{DiscData=disc,DiscName=Path.GetFileName(args[0])}},DeterministicEmulationRequested=true}))
        {
            var dom=c.ServiceProvider.GetService<IMemoryDomains>();
            File.WriteAllText(Path.Combine(output,"domains.txt"),string.Join("\n",dom.Select(d=>d.Name+" "+d.Size+" "+d.EndianType))+"\n");
            var pad=new FmvPad{Definition=c.ControllerDefinition};
            var shots=Set(sched,"screenshots"); var vdumps=Set(sched,"vram_dumps");
            int every=sched["screenshot_every"]==null?0:(int)sched["screenshot_every"];
            int shotFrom=sched["screenshot_from"]==null?0:(int)sched["screenshot_from"];
            int shotTo=sched["screenshot_to"]==null?int.MaxValue:(int)sched["screenshot_to"];
            bool audio=sched["audio"]!=null&&(bool)sched["audio"];
            var peeks=sched["peeks"]==null?new List<JToken>():sched["peeks"].ToList();
            using(var log=new StreamWriter(Path.Combine(output,"frames.csv")))
            using(var wav=audio?File.Create(Path.Combine(output,"audio.s16")):null)
            {
                log.WriteLine("frame,lag,input,samples"+string.Concat(peeks.Select(p=>","+(string)p["name"])));
                for(int f=1;f<=frames;f++)
                {
                    if(sched["pokes"]!=null) foreach(var k in sched["pokes"]) if((int)k["frame"]==f){
                        // Setup only, named in the run's README (for example the title flag that lists Visual Mode).
                        var d=dom[(string)k["domain"]]; var hex=(string)k["bytes"]; int at=Convert.ToInt32((string)k["address"],16);
                        for(int i=0;i<hex.Length/2;i++) d.PokeByte(at+i,Convert.ToByte(hex.Substring(2*i,2),16));
                        File.AppendAllText(Path.Combine(output,"log.txt"),"poke "+k["domain"]+" "+k["address"]+"="+hex+" at frame "+f+"\n");
                    }
                    pad.Buttons.Clear();
                    foreach(var x in sched["inputs"]) if(f>=(int)x["first"]&&f<=(int)x["last"])pad.Buttons.Add("P1 "+(string)x["button"]);
                    c.FrameAdvance(pad,true,true); short[] a;int n;c.GetSamplesSync(out a,out n);
                    if(wav!=null){var bytes=new byte[n*4];Buffer.BlockCopy(a,0,bytes,0,n*4);wav.Write(bytes,0,bytes.Length);}
                    var cols=new List<string>{f.ToString(),c.IsLagFrame?"1":"0",string.Join("+",pad.Buttons.Select(b=>b.Substring(3)).OrderBy(b=>b)),n.ToString()};
                    foreach(var p in peeks){
                        var d=dom[(string)p["domain"]]; long at=Convert.ToInt64((string)p["address"],16); int size=(int)p["size"]; long v=0;
                        for(int i=0;i<size;i++) v=(v<<8)|d.PeekByte(at+i);
                        cols.Add(v.ToString("X"+(2*size)));
                    }
                    log.WriteLine(string.Join(",",cols));
                    if(shots.Contains(f)||(every>0&&f>=shotFrom&&f<=shotTo&&(f-shotFrom)%every==0))Ppm(c,Path.Combine(output,"postframe-"+f.ToString("D5")+".ppm"));
                    if(vdumps.Contains(f)){
                        File.WriteAllBytes(Path.Combine(output,"vram-"+f.ToString("D5")+".bin"),Dump(dom["VRAM"]));
                        File.WriteAllBytes(Path.Combine(output,"cram-"+f.ToString("D5")+".bin"),Dump(dom["CRAM"]));
                        File.WriteAllBytes(Path.Combine(output,"vsram-"+f.ToString("D5")+".bin"),Dump(dom["VSRAM"]));
                        File.WriteAllBytes(Path.Combine(output,"ram68k-"+f.ToString("D5")+".bin"),Dump(dom["68K RAM"]));
                    }
                }
            }
            File.WriteAllText(Path.Combine(output,"done.txt"),"ok\n");
        }
        return 0;
        } catch(Exception ex){Console.Error.WriteLine(ex);return 1;}
    }
}
