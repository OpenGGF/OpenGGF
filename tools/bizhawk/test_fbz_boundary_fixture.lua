-- Standalone Lua 5.4 guard for the declared native boundary fixture writer.
-- No emulator/ROM required. Exercises write allowlist, neutral input, bounded
-- failure in both directions and native completion without synthetic RAM.
local script = assert(arg[1], "supply capture_fbz_boundary_fixture.lua")
local real = {open=io.open, getenv=os.getenv, dofile=dofile}
for _, settles in ipairs({false,true}) do
 local ram, records, frames, exited, writes = {}, {}, 0, false, 0
 local output = {write=function(_,s) records[#records+1]=s end,flush=function()end,close=function()end}
 io.open=function()return output end
 os.getenv=function(name) return name=="OGGF_FBZ_VISUAL_OUTPUT" and "output" or "input" end
 dofile=function()return {manifest_sha256="D13D037BAF52BBD65D28096A71A54ACACB4229B8C4C560C76DCB921E90DC40DD"}end
 mainmemory={read_u16_be=function(a)return ram[a] or 0 end,write_u16_be=function(a,v)
  assert(({[0xB010]=true,[0xB014]=true,[0xEED2]=true,[0xEED4]=true,[0xEED6]=true,[0xEEC2]=true,[0xFE04]=true})[a])
  ram[a]=v;writes=writes+1
 end}
 savestate={load=function()ram[0xFE10]=0x0400;return true end}
 movie={stop=function()end}
 memory={read_bytes_as_array=function()return {0}end}
 joypad={set=function(buttons)for _,v in pairs(buttons)do assert(v==false)end end}
 client={invisibleemulation=function()end,speedmode=function()end,screenshot=function()end,exit=function()exited=true end}
 emu={framecount=function()return frames end,frameadvance=function()
  frames=frames+1;ram[0xEEC2]=settles and (frames%2==0 and 0 or 8) or (frames%2==0 and 4 or 8)
 end}
 real.dofile(script)
 local text=table.concat(records)
 assert(exited and writes==12)
 assert(frames==(settles and 5 or 81))
 assert((text:find('"phase":"reverse","reason"',1,true)~=nil)==not settles)
end
io.open=real.open;os.getenv=real.getenv;dofile=real.dofile
print("FBZ boundary fixture guard: 2 scenarios passed")
