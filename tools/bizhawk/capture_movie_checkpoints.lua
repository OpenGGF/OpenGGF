-- Native screenshot and VDP checkpoints during BK2 playback (SOZ pixel checkpoints, 2026-09-17).
-- Inputs: common native capture host (capture_native_references.py) with the source BK2,
-- and a plan returning
--   frames           = {emu.framecount() values to capture}
--   save_state_frame = optional frame at which to save save_state_path (first pass)
--   state_frame      = frame count the --fixture-state was saved at (later passes)
-- Outputs in OGGF_NATIVE_OUTPUT: f<frame>.png (348x240 bordered framebuffer),
-- f<frame>.vram/.cram/.vsram (whole GPGX domains), frames.csv (camera, level frame
-- counter) and done.txt. For a trace row r the frame is
-- bk2_frame_offset + pre_trace_osc_frames + r (trace metadata.json).
-- The movie keeps playing in read-only mode after a state load, so a zone-entry state
-- saved on the first pass replaces minutes of replay on later passes. Never print() per
-- frame: the Linux Lua console redraw slows emulation progressively.
local out=assert(os.getenv('OGGF_NATIVE_OUTPUT'))
local plan=dofile(assert(os.getenv('OGGF_NATIVE_PLAN')))
local want={}; local last=0
for _,f in ipairs(plan.frames) do want[f]=true; if f>last then last=f end end
client.invisibleemulation(true); client.speedmode(6400)
local state=os.getenv('OGGF_NATIVE_FIXTURE_STATE')
if state then
  assert(savestate.load(state),'fixture state load failed')
  assert(movie.isloaded() and emu.framecount()==plan.state_frame,
    'state frame '..emu.framecount()..' != plan.state_frame')
end
local function dump(dom,path)
  local t=memory.read_bytes_as_array(0,memory.getmemorydomainsize(dom),dom)
  local fh=assert(io.open(path,'wb'))
  for i=1,#t do fh:write(string.char(t[i])) end
  fh:close()
end
local log=assert(io.open(out..'/frames.csv','w'))
log:write('frame,camx,camy,lfc\n')
while emu.framecount()<last do
  -- Screenshots need the frame rendered, so emulation is visible only for planned frames.
  if want[emu.framecount()+1] then client.invisibleemulation(false) end
  emu.frameadvance()
  local f=emu.framecount()
  if plan.save_state_frame and f==plan.save_state_frame and not state then
    savestate.save(plan.save_state_path)
  end
  if want[f] then
    local stem=out..'/f'..f
    client.screenshot(stem..'.png')
    dump('VRAM',stem..'.vram'); dump('CRAM',stem..'.cram'); dump('VSRAM',stem..'.vsram')
    -- Camera_X_pos/Camera_Y_pos ($FFEE78/$FFEE7C) and Level_frame_counter ($FFFE04).
    log:write(string.format('%d,%d,%d,%d\n',f,mainmemory.read_u16_be(0xEE78),
      mainmemory.read_u16_be(0xEE7C),mainmemory.read_u16_be(0xFE04)))
    client.invisibleemulation(true)
  end
end
log:close()
local d=assert(io.open(out..'/done.txt','w')); d:write('ok\n'); d:close()
client.exit()
