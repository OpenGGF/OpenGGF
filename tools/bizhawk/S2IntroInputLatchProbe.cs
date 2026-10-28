// Comparison-only S2 REV01 title ingress. No RAM writes or engine hydration.
using System;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Collections.Generic;
using OpenGGF.BizHawk.Headless;
class IntroProbe {
 static GpgxHost h; static StreamWriter log; static int row; static byte[] movie;
 static int B(int a){return h.ReadMainRamByte(a);} static int W(int a){return B(a)*256+B(a+1);}
 static uint R(string r){return h.ReadCpuRegister("M68K "+r);}
 static string Hash(byte[] d){using(var s=SHA256.Create())return BitConverter.ToString(s.ComputeHash(d)).Replace("-","").ToLowerInvariant();}
 static string RamHash(int a,int n){return Hash(Enumerable.Range(a,n).Select(x=>(byte)B(x)).ToArray());}
 static void Record(string e){log.WriteLine(string.Join(",",e,row,h.CompletedFrame,R("PC").ToString("X"),R("D0").ToString("X"),R("D4").ToString("X"),R("A0").ToString("X"),W(0xF604).ToString("X4"),W(0xF606).ToString("X4"),B(0xF600),B(0xF62A),B(0xB064),B(0xB065),B(0xB06F),W(0xB074),RamHash(0xFB00,128)));}
 static void Main(string[] args){
  var rom=File.ReadAllBytes(args[0]);movie=File.ReadAllBytes(args[1]); string name=args[2];string output=args[3];Directory.CreateDirectory(output);
  using(h=GpgxHost.Open(args[0],GpgxHost.CreateGhz1SyncSettings()))using(log=new StreamWriter(Path.Combine(output,name+".csv"))){
   log.WriteLine("event,input_row,completed_frame,pc,d0,d4,a0,p1,p2,mode,vint,obj_routine,obj_subroutine,intro_complete,obj_frame,palette_sha256");
   var hooks=new List<IDisposable>();
   Action<uint,string,string> hook=(a,b,e)=>{string actual=BitConverter.ToString(rom.Skip((int)a).Take(b.Length/2).ToArray()).Replace("-","");if(actual!=b)throw new Exception("opcode "+a.ToString("X")+" "+actual); hooks.Add(h.RegisterExecuteCallback(a,()=>{Record(e);if(e=="clear_p1"){int expected=name=="boundary"?0x8080:name=="p2-held"?0x8000:0;if(row!=567||W(0xF606)!=expected)throw new Exception("Unreached native input inlet "+name+" row"+row+" P2="+W(0xF606).ToString("X4"));}}));};
   hook(0x3BA8,"4278F604","clear_p1"); hook(0x12E18,"7000","obj0e");hook(0x134BC,"4A28002F","set_final_test");hook(0x3C14,"11FC0004F62A","title_loop");hook(0x246A,"31FC003FF626","fade_to_black");hook(0x23C6,"31FC003FF626","fade_from_black");hook(0x1156,"4E75","pad_sampled");
   for(row=0;row<630;row++){
    h.ClearButtons(); int p1=0,p2=0;
    // Source-aligned diagnostic: preserve original initial P2 pulse245, then
    // neutral until the observed final credit fade565. Actual title-loop
    // controls590(P2)/591(P1) are shared by every variant. No RAM writes.
    if(row==245 || row==590)p2=128;
    if(row==591)p1=128;
    if(name=="boundary" && row==565 || name=="p2-early"&&row==564 || name=="p2-late"&&row==566 || name=="p2-held"&&row>=564&&row<=566)p2|=128;
    if(name=="p1-only"&&row==565)p1|=128;
    string[] buttons={"Up","Down","Left","Right","A","B","C","Start"};
    // GMV low face bits are B,C,A; native GPGX names are explicit.
    string[] names={"Up","Down","Left","Right","B","C","A","Start"};
    for(int b=0;b<8;b++){h.SetButton("P1 "+names[b],(p1&(1<<b))!=0);h.SetButton("P2 "+names[b],(p2&(1<<b))!=0);}
    h.AdvanceWithVideo();
    if(row>=550&&row<=610){Record("frame");var f=h.CapturePublishedFrame();File.WriteAllBytes(Path.Combine(output,name+"-"+row+".argb"),f.ArgbPixels.SelectMany(BitConverter.GetBytes).ToArray());File.WriteAllBytes(Path.Combine(output,name+"-"+row+".vram"),f.Vram);}
   }
   foreach(var hookLease in hooks)hookLease.Dispose();
  }
 }
}
