-- Standalone Lua 5.4 guard for the declared native boundary fixture writer.
-- No emulator/ROM required. Exercises write allowlist, neutral input, bounded
-- failure in both directions and native completion without synthetic RAM.
local script = assert(arg[1], "supply capture_fbz_boundary_fixture.lua")
local real = {open=io.open, getenv=os.getenv, dofile=dofile}
local boundaries={
 {id="fbz1-boundary-1-outdoor",x=0xAFF,y=0x9BF,region=4,address=0xB014,forward=0x9C1,reverse=0x9BF},
 {id="fbz1-boundary-4-horizontal",x=0x1AFF,y=0xFF,region=16,address=0xB010,forward=0x1B01,reverse=0x1AFF},
 {id="fbz1-boundary-6-outdoor",x=0x100,y=0x63F,region=24,address=0xB014,forward=0x641,reverse=0x63F},
 {id="fbz2-boundary-outdoor",act=2,control=1,normal=4,region_address=0xEEC0,x=0x1000,y=0xA3F,region=0,address=0xB014,forward=0xA41,reverse=0xA3F}}
for _,boundary in ipairs(boundaries) do
for _, settles in ipairs({false,true}) do
 local ram, records, frames, exited, writes = {}, {}, 0, false, 0
 local phaseSteps=0
 local byteWrites=0
 local output = {write=function(_,s) records[#records+1]=s end,flush=function()end,close=function()end}
 io.open=function()return output end
 os.getenv=function(name) return name=="OGGF_FBZ_VISUAL_OUTPUT" and "output" or "input" end
 dofile=function()return {manifest_sha256="261535247F627A3A48E088C4E640A544453D3AC9602054570088BD24737406D1",boundary=boundary}end
 mainmemory={read_bytes_as_array=function(a,n) assert(a==0xF800 and n==0x280);local t={};for i=1,n do t[i]=0 end;return t end,read_u32_be=function()return 0 end,read_u8=function(a)return a==0xB005 and 2 or (ram[a] or 0) end,write_u8=function(a,v)assert(a==0xB02E and v==1);byteWrites=byteWrites+1;ram[a]=v end,read_u16_be=function(a)return ram[a] or 0 end,write_u16_be=function(a,v)
  assert(({[0xB010]=true,[0xB014]=true,[0xEED2]=true,[0xEEC0]=true,[0xEED4]=true,[0xEED6]=true,[0xEEC2]=true,[0xFE04]=true})[a])
  ram[a]=v;writes=writes+1;if a==boundary.address then phaseSteps=0 end
 end}
 savestate={load=function()ram[0xFE10]=0x0400+(boundary.act or 1)-1;return true end}
 movie={stop=function()end}
 memory={read_bytes_as_array=function()return {0}end}
 joypad={set=function(buttons)for _,v in pairs(buttons)do assert(v==false)end end}
 client={invisibleemulation=function()end,speedmode=function()end,screenshot=function()end,exit=function()exited=true end}
 emu={framecount=function()return frames end,frameadvance=function()
  frames=frames+1;phaseSteps=phaseSteps+1;ram[0xEE78]=boundary.x-160;ram[0xEE7C]=boundary.y-96;ram[0xFE04]=(ram[0xFE04] or 0)+1;ram[0xEEC2]=settles and (phaseSteps%2==0 and (boundary.normal or 0) or 8) or (phaseSteps%2==0 and ((boundary.normal or 0)+4) or ((boundary.normal or 0)+8))
 end}
 real.dofile(script)
 local text=table.concat(records)
 assert(exited and writes==12)
 assert(byteWrites==(boundary.control or 0))
 assert(ram[boundary.region_address or 0xEED2]==boundary.region and ram[boundary.address]==boundary.reverse)
 assert(frames==(settles and 86 or 162))
 assert((text:find('"phase":"reverse","reason"',1,true)~=nil)==not settles)
end
end
io.open=real.open;os.getenv=real.getenv;dofile=real.dofile
print("FBZ boundary fixture guard: 8 scenarios passed")
