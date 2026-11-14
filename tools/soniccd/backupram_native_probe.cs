// Sonic CD backup-RAM and Time Attack native probe (lane TA, 2026-11-12).
// Purpose: drive the USA disc through BizHawk 2.11 GPGX by a button schedule and export what the
// engine's backup-RAM device, record codec and program screens are compared against: the internal
// backup RAM ("CD BRAM", 8 KiB), the RAM cartridge ("EBRAM"), main-CPU RAM and post-frame pictures.
// Inputs: USA CUE, firmware dir, output dir (new), schedule JSON, BIOS SHA-1. Schedule keys:
//   frames, inputs[{first,last,button}] (1-based probe frames = BK2 row + 1), screenshots[],
//   bram_dumps[] (CD BRAM + EBRAM + 68K RAM at those frames), vram_dumps[] (VRAM, CRAM, VSRAM, 68K RAM),
//   pokes[{frame,domain,address,bytes}] (setup writes before that frame), initial_bram (optional 8 KiB file
//   stored into the core's save RAM before the first frame; the cartridge part keeps its format).
// Built with tools/soniccd/backupram_native_probe.sh against tools/soniccd/oracle_probe.cs (frozen branch).
using System; using System.IO; using System.Linq; using System.Collections.Generic;
using BizHawk.Emulation.Common; using BizHawk.Emulation.Cores;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx; using BizHawk.Emulation.DiscSystem;
using Newtonsoft.Json.Linq;
internal sealed class BramPad : IController
{
    public ControllerDefinition Definition { get; set; }
    public HashSet<string> Buttons = new HashSet<string>();
    public bool IsPressed(string name) { return Buttons.Contains(name); }
    public int AxisValue(string name) { return 0; }
    public IReadOnlyCollection<(string Name, int Strength)> GetHapticsSnapshot() { return new (string Name, int Strength)[0]; }
    public void SetHapticChannelStrength(string name,int strength) { }
}
internal static class BramProbe
{
    static byte[] Dump(MemoryDomain d){var b=new byte[d.Size];for(long i=0;i<d.Size;i++)b[i]=d.PeekByte(i);return b;}
    // The GPGX "CD BRAM"/"EBRAM" domains present each 16-bit word byte-swapped relative to the device bytes
    // (and to the core's save RAM); the canonical (device-order) image swaps every pair back.
    static byte[] Canonical(MemoryDomain d){var b=Dump(d);for(int i=0;i+1<b.Length;i+=2){var t=b[i];b[i]=b[i+1];b[i+1]=t;}return b;}
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
            Game=new GameInfo{Name="Sonic CD backup RAM probe",System="GEN"},
            Settings=new GPGX.GPGXSettings(),SyncSettings=new GPGX.GPGXSyncSettings(),
            Discs={new CdAsset{DiscData=disc,DiscName=Path.GetFileName(args[0])}},DeterministicEmulationRequested=true}))
        {
            var dom=c.ServiceProvider.GetService<IMemoryDomains>();
            var bram=dom["CD BRAM"]; var ebram=dom["EBRAM"]; var ram=dom["68K RAM"];
            File.WriteAllBytes(Path.Combine(output,"bram-power-on.bin"),Canonical(bram));
            if(sched["initial_bram"]!=null)
            {
                var save=c.ServiceProvider.GetService<ISaveRam>();
                var whole=save.CloneSaveRam(false);
                var mine=File.ReadAllBytes((string)sched["initial_bram"]);
                if(mine.Length!=0x2000) throw new IOException("initial_bram must be 8 KiB");
                Array.Copy(mine,0,whole,whole.Length-0x2000,0x2000);
                save.StoreSaveRam(whole);
                var check=Canonical(bram); if(!check.SequenceEqual(mine)) throw new IOException("initial_bram did not reach CD BRAM");
                File.AppendAllText(Path.Combine(output,"log.txt"),"initial_bram stored ("+whole.Length+" save-RAM bytes)\n");
            }
            var pad=new BramPad{Definition=c.ControllerDefinition};
            var shots=Set(sched,"screenshots"); var dumps=Set(sched,"bram_dumps"); var vdumps=Set(sched,"vram_dumps");
            using(var log=new StreamWriter(Path.Combine(output,"frames.csv")))
            {
                log.WriteLine("frame,lag,input,ta_unlock,title_flags,saved_level,ta_time,ta_level,ta_mode,zone,act,lives");
                byte[] lastBram=Canonical(bram);
                for(int f=1;f<=frames;f++)
                {
                    if(sched["pokes"]!=null) foreach(var k in sched["pokes"]) if((int)k["frame"]==f){
                        // Setup only: IPX variables a Time Attack stage leaves (time, level, lives) poked before the menu reads them.
                        var d=dom[(string)k["domain"]]; var hex=(string)k["bytes"]; int at=Convert.ToInt32((string)k["address"],16);
                        for(int i=0;i<hex.Length/2;i++) d.PokeByte(at+i,Convert.ToByte(hex.Substring(2*i,2),16));
                        File.AppendAllText(Path.Combine(output,"log.txt"),"poke "+k["domain"]+" "+k["address"]+"="+hex+" at frame "+f+"\n");
                    }
                    pad.Buttons.Clear();
                    foreach(var x in sched["inputs"]) if(f>=(int)x["first"]&&f<=(int)x["last"])pad.Buttons.Add("P1 "+(string)x["button"]);
                    c.FrameAdvance(pad,true,true); short[] a;int n;c.GetSamplesSync(out a,out n);
                    log.WriteLine(string.Join(",",new object[]{f,c.IsLagFrame?1:0,string.Join("+",pad.Buttons.Select(b=>b.Substring(3)).OrderBy(b=>b)),
                        ram.PeekByte(0xF18),ram.PeekByte(0xF1D),(ram.PeekByte(0xF02)<<8)|ram.PeekByte(0xF03),
                        ((long)ram.PeekByte(0xF10)<<24)|((long)ram.PeekByte(0xF11)<<16)|((long)ram.PeekByte(0xF12)<<8)|ram.PeekByte(0xF13),
                        (ram.PeekByte(0xF14)<<8)|ram.PeekByte(0xF15),ram.PeekByte(0xF01),ram.PeekByte(0x1506),ram.PeekByte(0x1507),ram.PeekByte(0x1508)}));
                    var now=Canonical(bram);
                    if(!now.SequenceEqual(lastBram)){File.AppendAllText(Path.Combine(output,"bram-changes.txt"),f+"\n");File.WriteAllBytes(Path.Combine(output,"bram-change-"+f.ToString("D5")+".bin"),now);lastBram=now;}
                    if(shots.Contains(f))Ppm(c,Path.Combine(output,"postframe-"+f.ToString("D5")+".ppm"));
                    if(vdumps.Contains(f)){
                        // VDP memories: GPGX's VRAM/CRAM/VSRAM domains already read as big-endian words.
                        File.WriteAllBytes(Path.Combine(output,"vram-"+f.ToString("D5")+".bin"),Dump(dom["VRAM"]));
                        File.WriteAllBytes(Path.Combine(output,"cram-"+f.ToString("D5")+".bin"),Dump(dom["CRAM"]));
                        File.WriteAllBytes(Path.Combine(output,"vsram-"+f.ToString("D5")+".bin"),Dump(dom["VSRAM"]));
                        File.WriteAllBytes(Path.Combine(output,"ram68k-"+f.ToString("D5")+".bin"),Dump(ram));
                    }
                    if(dumps.Contains(f)){
                        File.WriteAllBytes(Path.Combine(output,"bram-"+f.ToString("D5")+".bin"),now);
                        File.WriteAllBytes(Path.Combine(output,"ebram-tail-"+f.ToString("D5")+".bin"),Canonical(ebram).Skip((int)ebram.Size-0x2000).ToArray());
                        var sr=c.ServiceProvider.GetService<ISaveRam>().CloneSaveRam(false); File.WriteAllBytes(Path.Combine(output,"saveram-"+f.ToString("D5")+".bin"),sr.Skip(sr.Length-0x2000).ToArray());
                        File.WriteAllBytes(Path.Combine(output,"ram68k-"+f.ToString("D5")+".bin"),Dump(ram));
                    }
                }
            }
            File.WriteAllText(Path.Combine(output,"done.txt"),"ok\n");
        }
        return 0;
        } catch(Exception ex){Console.Error.WriteLine(ex);return 1;}
    }
}
