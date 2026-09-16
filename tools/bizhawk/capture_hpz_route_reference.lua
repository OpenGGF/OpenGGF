-- HPZ native route reference, 2026-09-17 (feature/ai-hpz-bring-up).
-- Purpose: answer named presentation/behaviour questions about Hidden Palace ($1601)
-- from ordinary complete-run movie playback, not a seeded position.
-- Inputs: common native capture host, verified locked-on ROM, a complete-run BK2 and a plan
-- returning {windows={{label=..., first=<movie frame>, last=<movie frame>, every=<n>}, ...}}.
-- Optional plan.save_states={<movie frame>,...} writes states/<frame>.State while the movie
-- plays, so later probes can pass one as --fixture-state instead of replaying ~430k frames.
-- With a fixture state the exporter loads it with the movie still attached (a state saved
-- during this movie's playback), checks the movie frame and zone, then continues playback.
-- Outputs: observations.csv (every frame inside any window) and <label>/<frame>.png images,
-- plus done.txt once the last window has been captured.
-- RAM (sonic3k.lst): Camera_X/Y_pos $EE78/$EE7C, Events_routine_bg $EEC2, Events_fg_4 $EEC4,
-- Screen_shake_offset $EECE, Palette_cycle_counters $F650, _unkFAB8 $FAB8, Normal_palette $FC00 ($20 per line; line 4 colours 1-2 at $FC62/$FC64),
-- Chaos/Super_emerald_count $FFB0/$FFB1, Collected_emeralds_array $FFB2,
-- Level_frame_counter $FE04, Current_zone_and_act $FE10, Player_1 x/y $B010/$B014.
local out=assert(os.getenv('OGGF_NATIVE_OUTPUT'))
local plan=dofile(assert(os.getenv('OGGF_NATIVE_PLAN')))
local windows=assert(plan.windows,'plan.windows required')
local lastFrame=0
for _,w in ipairs(windows) do
  assert(w.label and w.first and w.last and w.every,'window needs label/first/last/every')
  os.execute('mkdir -p "'..out..'/'..w.label..'"')
  if w.last>lastFrame then lastFrame=w.last end
end
local saves={}
local lastSave=0
for _,f in ipairs(plan.save_states or {}) do saves[f]=true if f>lastSave then lastSave=f end end
if next(saves) then os.execute('mkdir -p "'..out..'/states"') end
if lastSave>lastFrame then lastFrame=lastSave end
local fixture=os.getenv('OGGF_NATIVE_FIXTURE_STATE')
if fixture then
  assert(savestate.load(fixture),'fixture state failed to load: '..fixture)
  assert(plan.fixture_frame==nil or emu.framecount()==plan.fixture_frame,
    'fixture movie frame '..emu.framecount()..' expected '..tostring(plan.fixture_frame))
  assert(plan.fixture_zone==nil or mainmemory.read_u16_be(0xFE10)==plan.fixture_zone,
    string.format('fixture zone %04X', mainmemory.read_u16_be(0xFE10)))
end
local log=assert(io.open(out..'/observations.csv','w'))
log:write('movie_frame,label,zone_act,lfc,cam_x,cam_y,p1_x,p1_y,p2_x,p2_y,bg_routine,fg4,shake,cycle0,fab8,pal4_c1,pal4_c2,pal2_c0,chaos,super,emeralds\n')
client.speedmode(6400)
local function emeralds()
  local t={}
  for i=0,6 do t[#t+1]=tostring(mainmemory.read_u8(0xFFB2+i)) end
  return table.concat(t,'')
end
local function active(frame)
  for _,w in ipairs(windows) do
    if frame>=w.first-2 and frame<=w.last then return w end
  end
  return nil
end
while true do
  local frame=emu.framecount()
  local w=active(frame+1)
  client.invisibleemulation(w==nil)
  emu.frameadvance()
  frame=emu.framecount()
  if saves[frame] then savestate.save(string.format('%s/states/%07d.State',out,frame)) end
  local cw=nil
  for _,x in ipairs(windows) do
    if frame>=x.first and frame<=x.last then cw=x break end
  end
  if cw then
    log:write(string.format('%d,%s,%04X,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%04X,%04X,%04X,%d,%d,%s\n',
      frame,cw.label,mainmemory.read_u16_be(0xFE10),mainmemory.read_u16_be(0xFE04),
      mainmemory.read_u16_be(0xEE78),mainmemory.read_u16_be(0xEE7C),
      mainmemory.read_u16_be(0xB010),mainmemory.read_u16_be(0xB014),
      mainmemory.read_u16_be(0xB05A),mainmemory.read_u16_be(0xB05E),
      mainmemory.read_u16_be(0xEEC2),mainmemory.read_u16_be(0xEEC4),mainmemory.read_s16_be(0xEECE),
      mainmemory.read_u8(0xF650),mainmemory.read_u8(0xFAB8),
      mainmemory.read_u16_be(0xFC62),mainmemory.read_u16_be(0xFC64),mainmemory.read_u16_be(0xFC20),
      mainmemory.read_u8(0xFFB0),mainmemory.read_u8(0xFFB1),emeralds()))
    if (frame-cw.first)%cw.every==0 then
      client.screenshot(string.format('%s/%s/%07d.png',out,cw.label,frame))
    end
  end
  if frame>=lastFrame then break end
end
log:close()
local done=assert(io.open(out..'/done.txt','w'));done:write('last_frame='..lastFrame..'\n');done:close()
client.exit()
