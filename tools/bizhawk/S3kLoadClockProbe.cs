using System;
using System.IO;
using System.Collections.Generic;
using System.Security.Cryptography;
using OpenGGF.BizHawk.Headless;
class LoadClockProbe {
 static uint Read(GpgxHost h,int a,int n) {uint v=0;for(int i=0;i<n;i++)v=v*256+h.ReadMainRamByte((a+i)&65535);return v;}
 static string Sha(string p) {using(var h=SHA1.Create())return BitConverter.ToString(h.ComputeHash(File.ReadAllBytes(p))).Replace("-","");}
 static void Apply(Bk2Frame f,GpgxHost h) {
  if(f.P2Active)throw new Exception("Probe requires a single-controller movie");
  h.ClearButtons();
  string[] n={"Power","Reset","P1 Up","P1 Down","P1 Left","P1 Right","P1 A","P1 B","P1 C","P1 Start"};
  bool[] b={f.Power,f.Reset,f.P1Up,f.P1Down,f.P1Left,f.P1Right,f.P1A,f.P1B,f.P1C,f.P1Start};
  for(int i=0;i<n.Length;i++)if(b[i])h.SetButton(n[i],true);
 }
 public static int Main(string[] a) {
  if(a.Length!=3)throw new Exception("ROM, BK2, absent output CSV required");
  if(Sha(a[0])!="CFBF98C36C776677290A872547AC47C53D2761D6")throw new Exception("Wrong ROM");
  if(File.Exists(a[2]))throw new Exception("Output exists");
  var movie=Bk2Reader.Read(a[1]);
  using(var host=GpgxHost.Open(a[0],movie.SyncSettings))
  using(var log=new StreamWriter(a[2])) {
   int input=0,load=0;bool loading=false;int hits=0;
   log.WriteLine("input_row,completed_frame,load,marker,vint,lag,zone_act,level_counter,return_pc");
   Action<string,uint> emit=(name,ret)=>{log.WriteLine(string.Join(",",input,host.CompletedFrame,load,name,Read(host,0xFE0C,4),Read(host,0xF628,2),Read(host,0xFE10,2).ToString("X4"),Read(host,0xFE04,2),ret.ToString("X6")));hits++;};
   var hooks=new List<IDisposable>();
   hooks.Add(host.RegisterExecuteCallback(0x5FF6,()=>{load++;loading=true;emit("fade_begin",0);}));
   uint[] pcs={0x6000,0x62CC,0x62FE,0x6310,0x6322,0x6326,0x632C,0x633A,0x6468,0x64DC};
   string[] names={"fade_end","title_loop","title_end","load_block_start","terrain_queue_begin","direct_decode_begin","direct_decode_end","setup_end","initial_sprites","level_loop"};
   for(int i=0;i<pcs.Length;i++){int j=i;hooks.Add(host.RegisterExecuteCallback(pcs[i],()=>{if(loading){emit(names[j],0);if(pcs[j]==0x64DC)loading=false;}}));}
   // VInt_0_Main before addq.w #1,(Lag_frame_count); saved d0-a6 occupy60 bytes.
   hooks.Add(host.RegisterExecuteCallback(0x608,()=>{if(loading){uint sp=host.ReadCpuRegister("M68K A7");emit("lag_interrupt",Read(host,(int)sp+62,4));}}));
   foreach(var frame in movie.OpenFrameStream()){input++;Apply(frame,host);host.Advance();if(input>=9300)break;}
   foreach(var hook in hooks)hook.Dispose();
   if(input!=9300||load<4||hits<100)throw new Exception("Incomplete observation window");
   Console.WriteLine("Completed "+input+" movie rows; "+load+" loads; "+hits+" markers");
  }
  return 0;
 }
}
