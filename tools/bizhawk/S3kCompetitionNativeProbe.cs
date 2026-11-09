// Purpose: cold title -> Competition ingress observation; no RAM writes.
// Inputs: original locked-on S3K ROM; BizHawk 2.11 pinned headless host.
// Origin: lane N native miniature physics qualification, 2026-11-09.
using System;
using System.IO;
using OpenGGF.BizHawk.Headless;
// Player_1 at $B000; object_size=$4A, x_pos=$10, y_pos=$14,
// x/y/ground_vel=$18/$1A/$1C, anim=$20, mapping_frame=$22,
// angle=$26, status=$2A, status_secondary=$2B, object_control=$2E,
// interact=$42 (sonic3k.constants.asm). No WriteMainRam capability is used.
class S3kCompetitionNativeProbe {
 static int B(GpgxHost h,int a) {return h.ReadMainRamByte(a);}
 static int W(GpgxHost h,int a) {return B(h,a)*256+B(h,a+1);}
 static void Main(string[] args) {
 if(args.Length!=3) throw new ArgumentException("ROM, engine countdown+race mask tape, existing external output directory required");
 string output=Path.GetFullPath(args[2]);
 if(!Directory.Exists(output)) throw new DirectoryNotFoundException(output);
 string statePath=Path.Combine(output,"start.state");
 Console.WriteLine("OPEN"); Console.Out.Flush();
 using(var h=GpgxHost.Open(args[0],GpgxHost.CreateGhz1SyncSettings())) {
 Console.WriteLine("OPENED"); Console.Out.Flush();
 int[] tape=Array.ConvertAll(File.ReadAllLines(args[1]),int.Parse);
 int race=-1; bool saved=File.Exists(statePath);
 if(saved) typeof(GpgxHost).GetMethod("LoadSavestate",System.Reflection.BindingFlags.NonPublic|System.Reflection.BindingFlags.Instance,null,new Type[]{typeof(byte[])},null).Invoke(h,new object[]{File.ReadAllBytes(statePath)});
 var csv=new StreamWriter(Path.Combine(output,"native.csv"));
 csv.WriteLine("race,hardware,lag,vint,level,input,x,xf,y,yf,xv,yv,gsp,angle,status,secondary,routine,control,interact,anim,mapping,timerflag,timerm,timers,timerf,lap");
 for(int i=0;i<12000;i++) {
 h.ClearButtons();
 // Read-only menu selection. Each pulse is a fresh held-state edge. The solo
 // character confirmation is owned by Events_bg+$10; track cursor 3 is DPZ.
 int mode=B(h,0xf600);
 if(!saved && mode==0xc && B(h,0xfe10)==0x10) {
 var state=(byte[])typeof(GpgxHost).GetMethod("CloneSavestate",System.Reflection.BindingFlags.NonPublic|System.Reflection.BindingFlags.Instance).Invoke(h,null);
 File.WriteAllBytes(statePath,state); saved=true;
 }
 if(saved && B(h,0xfe1e)==1 && race<0) {race=0;Console.WriteLine("GO "+i);}
 int mask=race>=0 && race+240<tape.Length?tape[race+240]:8;
 if(race>=0) {string[] names={"P1 Up","P1 Down","P1 Left","P1 Right","P1 A"};for(int k=0;k<5;k++) h.SetButton(names[k],(mask&(1<<k))!=0);}

 if(race<0 && i%60==59) {
 if(mode==4) h.SetButton(B(h,0xff86)==1?"P1 Start":"P1 Down",true);
 else if(mode==0x38) h.SetButton(B(h,0xef48)==2?"P1 Start":"P1 Down",true);
 else if(mode==0xc0) h.SetButton((B(h,0xeee2)!=0 || B(h,0xef4a)==3)?"P1 Start":"P1 Down",true);
 else if(mode!=0xc) h.SetButton("P1 Start",true);
 }
 if(i%60==0) {Console.WriteLine("PRE "+i+" mode="+B(h,0xf600).ToString("X2")+" opt="+B(h,0xff86)+" menu="+B(h,0xef48)+" mz="+B(h,0xef4a)+" ev="+W(h,0xeee2)+" zone="+B(h,0xfe10).ToString("X2")+" lock="+B(h,0xf7ca)+" timer="+W(h,0xfe24)+" lap="+B(h,0xfedc)+" x="+W(h,0xb010)+" y="+W(h,0xb014));Console.Out.Flush();}
 h.Advance();
 if(race>=0) {
 csv.WriteLine(string.Join(",",new int[]{race,h.CompletedFrame,h.IsLagged?1:0,W(h,0xfe0e),W(h,0xfe04),mask,W(h,0xb010),W(h,0xb012),W(h,0xb014),W(h,0xb016),(short)W(h,0xb018),(short)W(h,0xb01a),(short)W(h,0xb01c),B(h,0xb026),B(h,0xb02a),B(h,0xb02b),B(h,0xb005),B(h,0xb02e),W(h,0xb042),B(h,0xb020),B(h,0xb022),B(h,0xfe1e),B(h,0xfe23),B(h,0xfe24),B(h,0xfe25),B(h,0xfedc)}));

 if(++race>=tape.Length-240 || B(h,0xfedc)>5) break;
 }

 }
 csv.Dispose();
 if(race!=tape.Length-240 || B(h,0xfedc)!=6 || B(h,0xfe1e)!=128) throw new InvalidOperationException("Incomplete native five-lap race: "+race);
 }
 }
}
