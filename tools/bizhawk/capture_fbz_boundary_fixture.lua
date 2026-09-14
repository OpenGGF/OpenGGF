-- FBZ boundary fixture pilot, originating 2026-09-14 FBZ completion.
-- Inputs: verified host ROM/movie, OGGF_FBZ_FIXTURE_STATE saved native state.
-- This is explicit frozen-recipe setup, NOT a trace recorder or gameplay oracle.
-- Only the declared boundary-6 position/event/LFC fields may be written; all
-- subsequent event/redraw/VDP progress is native execution. Never copy output
-- RAM into an engine. Camera/physics/history remain observed, not synthesized.
local output = assert(os.getenv("OGGF_FBZ_VISUAL_OUTPUT"))
local state = assert(os.getenv("OGGF_FBZ_FIXTURE_STATE"))
local plan = dofile(assert(os.getenv("OGGF_FBZ_VISUAL_PLAN")))
assert(plan.manifest_sha256 == "BAE29DD285FF8D43166589164E31E1163F4196FCC1EA8DE8E2A5B90817AF7FC8")
local allowed = {[0xB010]=true,[0xB014]=true,[0xEED2]=true,[0xEED4]=true,
                 [0xEED6]=true,[0xEEC2]=true,[0xFE04]=true}
local log = assert(io.open(output.."/fixture.jsonl", "w"))
local function write(address,value,phase)
 assert(allowed[address], "undeclared fixture write")
 mainmemory.write_u16_be(address,value)
 assert(mainmemory.read_u16_be(address)==value)
 log:write(string.format('{"kind":"write","phase":"%s","address":%d,"value":%d}\n',phase,address,value))
 log:flush()
end
local function dump(domain,count,path)
 local data=memory.read_bytes_as_array(0,count,domain)
 local chars={};for i=1,#data do chars[i]=string.char(data[i]) end
 local f=assert(io.open(path,"wb"));f:write(table.concat(chars));f:close()
end
local fields={player_x=0xB010,player_y=0xB014,camera_x=0xEE78,camera_y=0xEE7C,
 events_region=0xEED2,foreground_outdoor=0xEED4,background_outdoor=0xEED6,
 events_routine_bg=0xEEC2,level_frame_counter=0xFE04,zone_and_act=0xFE10}
local function sample(phase,index)
 local stem=output.."/"..phase.."-"..string.format("%02d",index)
 client.screenshot(stem..".png")
 dump("VRAM",65536,stem..".vram");dump("CRAM",128,stem..".cram");dump("VSRAM",128,stem..".vsram")
 local parts={string.format('"kind":"sample","phase":"%s","index":%d,"native_frame":%d',phase,index,emu.framecount())}
 parts[#parts+1]=string.format('"player_routine":%d,"player_status":%d,"player_object_control":%d',mainmemory.read_u8(0xB005),mainmemory.read_u8(0xB02A),mainmemory.read_u8(0xB02E))
 for name,address in pairs(fields) do parts[#parts+1]=string.format('"%s":%d',name,mainmemory.read_u16_be(address)) end
 log:write("{"..table.concat(parts,",").."}\n");log:flush()
end
local function step()
 -- Fixture input is none. Stop movie playback after loading its provenance-
 -- bound savestate; neutral controller input owns subsequent pilot frames.
 joypad.set({["P1 Up"]=false,["P1 Down"]=false,["P1 Left"]=false,["P1 Right"]=false,
 ["P1 A"]=false,["P1 B"]=false,["P1 C"]=false,["P1 Start"]=false})
 emu.frameadvance()
end
client.invisibleemulation(false);client.speedmode(6400)
assert(savestate.load(state));assert(mainmemory.read_u16_be(0xFE10)==0x0400)
movie.stop()
write(0xB010,0x100,"setup");write(0xB014,0x63F,"setup")
-- Observe native tracker convergence before installing event/LFC recipe. No
-- camera, status, control, velocity, or position rewrites during this wait.
local initial_lfc=mainmemory.read_u16_be(0xFE04)
local camera_steps=0
while (mainmemory.read_u16_be(0xFE04)-initial_lfc)%65536<80 do
 camera_steps=camera_steps+1;assert(camera_steps<=120,"native setup gameplay clock stalled")
 step();sample("camera-wait",camera_steps)
end
sample("camera-prerequisite",0)
assert(mainmemory.read_u16_be(0xB010)==0x100 and mainmemory.read_u16_be(0xB014)==0x63F,"player moved during native camera setup")
assert(mainmemory.read_u8(0xB005)==2 and mainmemory.read_u8(0xB02E)==0,"native player is not ordinarily controlled")
write(0xEED2,0x18,"setup");write(0xEED4,0,"setup");write(0xEED6,0,"setup")
write(0xEEC2,0,"setup");write(0xFE04,0,"setup")
sample("setup",0)
local function cross(phase, coordinate)
 write(0xB014,coordinate,phase)
 local entered=false
 for i=1,40 do
  step();sample(phase,i)
  local routine=mainmemory.read_u16_be(0xEEC2)
  if routine~=0 then entered=true end
  if entered and routine==0 then return true end
 end
 log:write('{"kind":"failure","phase":"'..phase..'","reason":"redraw-did-not-settle-within-40-native-frames"}\n');log:flush()
 return false
end
local forward = cross("forward",0x641)
step();sample("forward-after",1)
write(0xEEC2,0,"reverse-setup")
write(0xEED4,0xFF00,"reverse-setup");write(0xEED6,0xFF00,"reverse-setup")
local reverse = cross("reverse",0x63F)
step();sample("after",1)
log:write('{"kind":"verdict","status":"unaccepted","camera_setup":"native-tracker-observed-not-forced","acceptance":"requires-paired-review"}\n')
log:close();client.exit()
