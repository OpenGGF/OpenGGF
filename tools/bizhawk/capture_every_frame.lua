-- Purpose: uncut native video frames for the S2 Competition parity demo: one screenshot per
-- emulated frame plus lag/mode rows for alignment. Read-only: no RAM or controller writes.
-- Inputs: OGGF_NATIVE_PLAN (returns {last=N}) and OGGF_NATIVE_OUTPUT, with the cold power-on BK2.
-- Origin: 2026-11-08 PR #270 parity video request.
local out=assert(os.getenv('OGGF_NATIVE_OUTPUT'))
local p=dofile(assert(os.getenv('OGGF_NATIVE_PLAN')))
client.speedmode(6400)
client.invisibleemulation(false)
os.execute('mkdir -p "'..out..'/frames"')
local csv=assert(io.open(out..'/frames.csv','w'))
csv:write('frame,lag,mode,two_player,lfc,width,height\n')
while emu.framecount()<p.last do
 emu.frameadvance()
 local f=emu.framecount()
 client.screenshot(string.format('%s/frames/%05d.png',out,f))
 csv:write(f,',',emu.islagged() and 1 or 0,',',mainmemory.read_u8(0xf600),',',mainmemory.read_u16_be(0xffd8),',',
  mainmemory.read_u16_be(0xfe04),',',client.screenwidth(),',',client.screenheight(),'\n')
 if f%500==0 then csv:flush() end
end
csv:close()
local done=assert(io.open(out..'/done.txt','w'));done:write('ok\n');done:close()
client.exit()
