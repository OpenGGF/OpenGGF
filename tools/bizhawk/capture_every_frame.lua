-- Purpose: uncut native video frames for the S2 Competition parity demo: one screenshot per
-- emulated frame plus lag/mode rows for alignment. Read-only: no RAM or controller writes.
-- Inputs: OGGF_NATIVE_PLAN (returns {last=N}) and OGGF_NATIVE_OUTPUT, with the cold power-on BK2.
-- Origin: 2026-11-08 PR #270 parity video request.
local out=assert(os.getenv('OGGF_NATIVE_OUTPUT'))
local p=dofile(assert(os.getenv('OGGF_NATIVE_PLAN')))
movie.setreadonly(true)
assert(movie.getreadonly(), "native reference requires read-only movie playback")
client.speedmode(6400)
client.invisibleemulation(false)
os.execute('mkdir -p "'..out..'/frames"')
local csv=assert(io.open(out..'/frames.csv','w'))
csv:write('frame,lag,mode,two_player,lfc,width,height\n')
-- Obj2E_Init/Raise (s2.asm:25745-25816); read-only award and breaker witnesses.
local monitor=assert(io.open(out..'/monitors.csv','w'))
monitor:write('frame,lfc,content,icon_routine,icon_x,parent,shell_routine,p1x,p2x,p1_lives,p2_lives,p1_rings,p2_rings,p1_routine,p2_routine,p1_power,p2_power,p1_timer,p2_timer,items,p1_monitors,p2_monitors\n')
while emu.framecount()<p.last do
 emu.frameadvance()
 local f=emu.framecount()
 client.screenshot(string.format('%s/frames/%05d.png',out,f))
 csv:write(f,',',emu.islagged() and 1 or 0,',',mainmemory.read_u8(0xf600),',',mainmemory.read_u16_be(0xffd8),',',
  mainmemory.read_u16_be(0xfe04),',',client.screenwidth(),',',client.screenheight(),'\n')
 local content,routine,x,parent,shell=0,0,0,0,0
 for slot=2,127 do
  local base=0xb000+slot*64
  local id=mainmemory.read_u8(base)
  if id==0x2e and mainmemory.read_u8(base+0x24)>0 then
   content=mainmemory.read_u8(base+0x1c);routine=mainmemory.read_u8(base+0x24)
   x=mainmemory.read_u16_be(base+8);parent=mainmemory.read_u16_be(base+0x3e)
  end
  if id==0x26 and mainmemory.read_u16_be(base+8)==960 then shell=mainmemory.read_u8(base+0x24) end
 end
 local values={f,mainmemory.read_u16_be(0xfe04),content,routine,x,parent,shell,
  mainmemory.read_u16_be(0xb008),mainmemory.read_u16_be(0xb048),
  mainmemory.read_u8(0xfe12),mainmemory.read_u8(0xfec6),mainmemory.read_u16_be(0xfe20),mainmemory.read_u16_be(0xfed0),
  mainmemory.read_u8(0xb024),mainmemory.read_u8(0xb064),mainmemory.read_u8(0xb02b),mainmemory.read_u8(0xb06b),
  mainmemory.read_u8(0xfe1e),mainmemory.read_u8(0xfeca),mainmemory.read_u16_be(0xff74),
  mainmemory.read_u16_be(0xfef4),mainmemory.read_u16_be(0xfef6)}
 monitor:write(table.concat(values,','),'\n')
 if f%500==0 then csv:flush();monitor:flush() end
end
csv:close();monitor:close()
local done=assert(io.open(out..'/done.txt','w'));done:write('ok\n');done:close()
client.exit()
