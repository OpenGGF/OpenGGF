-- Read-only published SOZ2 flame-slope observer, 2026-10-26.
-- API: official Gens11_Lua_Docs.zip/gens-lua.html. Gameplay addresses: original
-- S3&K SHA1 cfbf98c36c776677290a872547ac47c53d2761d6, skdisasm 8b2cf3c.
-- No RAM/register/input writes; no state loads. Native observations are ONLY
-- comparison data and must never hydrate OpenGGF. Parent owns runtime/display.
-- Set SOZ_NATIVE_PREFIX to a NEW absolute Windows/Wine-visible file prefix.
-- After DONE, gzip CSV/terrain/metadata and remove consumed plain originals.
-- Source movie frame alignment is deliberately recorded, not assumed: framecount
-- is sampled at before/exec/after boundaries, along with ROM and joypad inputs.
local prefix = assert(os.getenv('SOZ_NATIVE_PREFIX'), 'Set a new Windows-visible SOZ_NATIVE_PREFIX')
local max_capture = tonumber(os.getenv('SOZ_NATIVE_FRAMES') or '1200')
local max_total = tonumber(os.getenv('SOZ_NATIVE_MAX_TOTAL') or '90000')
local auto_pause = os.getenv('SOZ_NATIVE_AUTOPAUSE') ~= '0'
for _, k in ipairs({'readbyte','readword','readwordsigned','readlong','getregister','registerexec'}) do
 assert(type(memory[k])=='function', 'Required Gens API unavailable: memory.'..k)
end
for _, k in ipairs({'framecount','lagcount','lagged','registerbefore','registerafter','registerexit','pause','emulating'}) do
 assert(type(gens[k])=='function', 'Required Gens API unavailable: gens.'..k)
end
assert(type(joypad.get)=='function' and type(movie.name)=='function' and type(movie.mode)=='function')
local function create(suffix)
 local path=prefix..suffix;local exists=io.open(path,'rb')
 if exists then exists:close();error('Refusing to overwrite '..path) end
 return assert(io.open(path,'wb'))
end
local csv=create('.csv');local progress=create('.progress.txt')
local P=0xFFB000
local B,W,S,L=memory.readbyte,memory.readword,memory.readwordsigned,memory.readlong
local player_serial=0;local player_start_frame=-1;local player_input=-1
local active=false;local done=false;local checked=false;local n=0;local rows=0
local buttons={'up','down','left','right','A','B','C','start','X','Y','Z','mode'}
local function pad(which)
 local p=joypad.get(which);local out={}
 for _,b in ipairs(buttons) do if p[b] then out[#out+1]=b end end
 return #out>0 and table.concat(out,'+') or '-'
end
local function active_level() return B(0xFFF600)==0x0C and W(0xFFFE10)==0x0801 and B(P+5)==2 end
local function note(s) progress:write(s,'\n');progress:flush() end
local function hexrange(a,len)
 local t={};for j=0,len-1 do t[#t+1]=string.format('%02x',B(a+j)) end
 return table.concat(t)
end
local function verify_rom()
 assert(hexrange(0x11958,12)=='314000183140001c317c0000','Wrong ROM at fire dash stores')
 assert(hexrange(0x730C,12)=='08290001002a663608290003','Wrong ROM at SOZ slide owner')
 checked=true
 note('ROM_HOOK_BYTES_VERIFIED; movie='..tostring(movie.name())..'; mode='..tostring(movie.mode())..'; frame='..gens.framecount())
end
local header={'boundary','player_serial','player_start_emu_frame','player_logical_input','emu_frame','gens_lag_count','lagged','capture_post_count','movie_mode','joy1','joy2','mode','zoneact','apparent_act','level_frame','vint_low','timer_min','timer_sec','timer_frame','routine','x','xfrac','y','yfrac','vx','vy','g','angle','status','secondary','tertiary','flip_type','spin_dash_flag','spin_dash_counter','interact','yr','xr','anim','double_jump','jumping','move_lock','control','default_yr','default_xr','top_solid','lrb_solid','logical_pad1','raw_pad1','logical_pad2','locked_pad1','camera_x','camera_y','loader','player_mode','primary_collision','secondary_collision','pc','a0','a1','d0','d1','d2','ost_hex'}
csv:write(table.concat(header,','),'\n');csv:flush()
local function row(boundary)
 local v={boundary,player_serial,player_start_frame,player_input,gens.framecount(),gens.lagcount(),gens.lagged() and 1 or 0,n,tostring(movie.mode()),pad(1),pad(2),B(0xFFF600),W(0xFFFE10),B(0xFFEE4F),W(0xFFFE04),W(0xFFFE0E),B(0xFFFE23),B(0xFFFE24),B(0xFFFE25),B(P+5),W(P+0x10),W(P+0x12),W(P+0x14),W(P+0x16),S(P+0x18),S(P+0x1A),S(P+0x1C),B(P+0x26),B(P+0x2A),B(P+0x2B),B(P+0x37),B(P+0x2D),B(P+0x3D),W(P+0x3E),W(P+0x42),B(P+0x1E),B(P+0x1F),B(P+0x20),B(P+0x2F),B(P+0x40),W(P+0x32),B(P+0x2E),B(P+0x44),B(P+0x45),B(P+0x46),B(P+0x47),W(0xFFF602),W(0xFFF604),W(0xFFF66A),B(0xFFF7CA),W(0xFFEE78),W(0xFFEE7C),B(0xFFF76C),W(0xFFFF08),L(0xFFF7B4),L(0xFFF7B8),memory.getregister('pc'),memory.getregister('a0'),memory.getregister('a1'),memory.getregister('d0'),memory.getregister('d1'),memory.getregister('d2'),hexrange(P,0x4A)}
 assert(#v==#header,'Observer field count mismatch')
 for j=1,#v do v[j]=tostring(v[j]) end
 csv:write(table.concat(v,','),'\n');rows=rows+1
end
local function terrain_snapshot()
 -- Initial decoded terrain ONLY. Exact collision geometry can be independently
 -- inspected from these loaded chunks/layout and original ROM collision tables.
 -- Top/side/path bits are separately recorded per row; do not assume one path.
 local f=create('.terrain.txt')
 f:write('frame=',gens.framecount(),' primary=',L(0xFFF7B4),' secondary=',L(0xFFF7B8),'\n')
 for a=0xFF0000,0xFF8FF0,16 do f:write(string.format('%06x ',a),hexrange(a,16),'\n') end
 f:close()
end
local function finish(reason)
 if done then return end;done=true
 note('DONE '..reason..' frame='..gens.framecount()..' captured_post_frames='..n..' rows='..rows)
 csv:flush();csv:close();progress:close()
 local f=create('.done');f:write(reason,'\n');f:close()
 if auto_pause then gens.pause() end
end
gens.registerbefore(function()
 if done then return end
 if active and active_level() then row('before_frame') end
end)
gens.registerafter(function()
 if done then return end
 if not checked and gens.emulating() then verify_rom() end
 if gens.framecount()%6000==0 then note('PROGRESS frame='..gens.framecount()..' mode='..B(0xFFF600)..' zoneact='..W(0xFFFE10)) end
 if active_level() then
  if not active then
   active=true;terrain_snapshot();note('SOZ2_START frame='..gens.framecount()..' x='..W(P+0x10)..' y='..W(P+0x14))
  end
  n=n+1;row('after_frame');if n%60==0 then csv:flush() end
  if n>=max_capture then finish('capture_limit') end
 elseif active then finish('left_active_soz2') end
 if not done and gens.framecount()>=max_total then finish('total_frame_limit_without_target') end
 if not done and movie.mode()=='finished' then finish('movie_finished') end
end)
memory.registerexec(0x11958,function()
 if not done and active and active_level() and memory.getregister('a0')%65536==0xB000 then row('pre_dash_velocity_stores') end
end)
memory.registerexec(0x11966,function()
 if not done and active and active_level() and memory.getregister('a0')%65536==0xB000 then row('post_dash_velocity_stores') end
end)
memory.registerexec(0x730C,function()
 if not done and active and active_level() and memory.getregister('a1')%65536==0xB000 then row('pre_slide_owner') end
end)
-- Grounded running AND rolling converge on this forward collision probe.
-- Pinned loc_1137E calls sub_F61C; loc_113A0 is reached only with d1<0,
-- after d1<<8 and the shipped SOZ quadrant adjustment. locret_113CE is the
-- horizontal correction return. This separates collision-added velocity from
-- inherited approach displacement; d0/d1 native registers are retained.
for _,hook in ipairs({{0x1137E,'pre_ground_wall_probe'},{0x113A0,'negative_ground_wall_distance'},{0x113CE,'post_ground_horizontal_correction'},{0x113F4,'post_ground_probe_other'}}) do
 local pc,label=hook[1],hook[2]
 memory.registerexec(pc,function()
  if not done and active and active_level() and memory.getregister('a0')%65536==0xB000 then row(label) end
 end)
end
-- Read the owner-complete boundaries, because an emulated video frame may
-- end midway through the player's native movement routine.
memory.registerexec(0x10BFC,function()
 if not done and active and active_level() and memory.getregister('a0')%65536==0xB000 then
  player_serial=player_serial+1;player_start_frame=gens.framecount();player_input=W(0xFFF602);row('player_step_begin')
 end
end)
memory.registerexec(0x10C26,function()
 if not done and active and active_level() and memory.getregister('a0')%65536==0xB000 then row('player_movement_complete') end
end)
memory.registerexec(0x10C8E,function()
 if not done and active and active_level() and memory.getregister('a0')%65536==0xB000 then row('player_object_complete') end
end)
gens.registerexit(function()
 if not done then note('OBSERVER_EXIT incomplete frame='..gens.framecount()..' captured_post_frames='..n..' rows='..rows);csv:close();progress:close() end
end)
note('OBSERVER_READY prefix='..prefix..' max_capture='..max_capture..' max_total='..max_total)
-- Event-driven observer deliberately does not call frameadvance, speedmode,
-- movie.play, joypad.set, memory.write*, setregister or savestate.load.
