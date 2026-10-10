#!/usr/bin/env python3
"""Record OpenGGF on an owned virtual X11 display, without desktop input.

Inputs: compiled checkout/classpath, optional development mod, supplied ROM
paths and JSON-line key/wait/screenshot/focus/close actions. No gameplay-state
writes. Creates an isolated config, a unique null sink and owned recorder
processes. Never connects to the user's display or falls back to it.
Origin: 2026-10-07 Mutator Lab visibility/input/audio investigation.
Requires Xvfb, python-xlib, ffmpeg, pactl and Pulse. Prefer headless tests and
offscreen GameplayCaptureTool captures for ordinary validation and promo footage.
"""
import argparse, json, os, select, shutil, signal, subprocess, sys, time, uuid
from pathlib import Path
from Xlib import X, XK, display
from Xlib.protocol import event as xevent

def stop(process):
    if process is None or process.poll() is not None: return
    process.send_signal(signal.SIGINT)
    try: process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.terminate()
        try: process.wait(timeout=3)
        except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=3)


class VirtualDisplay:
    """Own a fresh server; displayfd allocates it without touching existing locks."""
    def __init__(self, log):
        self.log = log
        self.process = None
        self.name = None

    def start(self):
        executable = shutil.which('Xvfb')
        if not executable:
            raise RuntimeError('Xvfb is required for isolated window diagnostics; '
                               'use headless tests or offscreen captures instead')
        read_fd, write_fd = os.pipe()
        environment = os.environ.copy()
        for key in ('DISPLAY', 'WAYLAND_DISPLAY', 'XAUTHORITY'):
            environment.pop(key, None)
        try:
            self.process = subprocess.Popen(
                [executable, '-displayfd', str(write_fd), '-screen', '0', '1280x720x24',
                 '-nolisten', 'tcp', '-noreset', '-ac'],
                env=environment, pass_fds=(write_fd,), stdin=subprocess.DEVNULL,
                stdout=self.log, stderr=self.log)
            os.close(write_fd)
            write_fd = None
            deadline = time.monotonic() + 10
            response = b''
            while b'\n' not in response:
                remaining = deadline - time.monotonic()
                if remaining <= 0 or self.process.poll() is not None:
                    raise RuntimeError('Owned Xvfb failed to become ready within ten seconds')
                if not select.select([read_fd], [], [], remaining)[0]:
                    raise RuntimeError('Owned Xvfb display allocation timed out')
                chunk = os.read(read_fd, 32)
                if not chunk or len(response) + len(chunk) > 16:
                    raise RuntimeError('Owned Xvfb returned an invalid display number')
                response += chunk
            number = response.strip()
            if not number.isdigit():
                raise RuntimeError('Owned Xvfb returned an invalid display number')
            ambient_number = os.environ.get('DISPLAY', '').rsplit(':', 1)[-1].split('.')[0]
            if ambient_number.isdigit() and int(number) == int(ambient_number):
                raise RuntimeError('Owned Xvfb must not reuse the desktop display number')
            self.name = ':' + str(int(number))
            return self.child_environment()
        except BaseException:
            self.close()
            raise
        finally:
            os.close(read_fd)
            if write_fd is not None:
                os.close(write_fd)

    def child_environment(self):
        if self.name is None or self.process.poll() is not None:
            raise RuntimeError('Owned virtual display is not running')
        environment = os.environ.copy()
        environment.pop('WAYLAND_DISPLAY', None)
        environment.update(DISPLAY=self.name, XDG_SESSION_TYPE='x11', XAUTHORITY='/dev/null')
        return environment

    def close(self):
        stop(self.process)

def require_successful_close(host):
    if host.returncode != 0:
        raise RuntimeError(f"Engine window close exited {host.returncode}")


def finish_engine_run(host, intentional_stop, receipt):
    if receipt['status'] == 'closed':
        require_successful_close(host)
    elif host.poll() is not None:
        raise RuntimeError(f"Unexpected Engine exit {host.returncode} before normal window close")
    elif not intentional_stop:
        raise RuntimeError('Engine walkthrough did not finish intentionally')
    else:
        receipt['status'] = 'stopped before normal window close'


def cleanup_owned(processes, focus_window, connection, module, log, virtual_display=None):
    """Attempt every owned resource, retaining independent errors and actual outcomes."""
    result = {'processes_stopped': {}, 'errors': []}
    for name, process in processes.items():
        try:
            stop(process)
        except BaseException as failure:
            result['errors'].append(f'{name}: {failure!r}')
        try:
            result['processes_stopped'][name] = process is None or process.poll() is not None
        except BaseException as failure:
            result['processes_stopped'][name] = False
            result['errors'].append(f'{name} status: {failure!r}')
    operations = [('focus_window_destroyed', lambda: focus_window.destroy() if focus_window else None),
                  ('display_closed', lambda: connection.close() if connection else None),
                  ('virtual_display_stopped', lambda: virtual_display.close() if virtual_display else None),
                  ('audio_module_unloaded', lambda: subprocess.run(['pactl', 'unload-module', module], check=True, timeout=5) if module else None),
                  ('temporary_log_closed', log.close)]
    for name, action in operations:
        try:
            action()
            result[name] = True
        except BaseException as failure:
            result[name] = False
            result['errors'].append(f'{name}: {failure!r}')
    return result


def key_focus(d,window):
    """X input focus at key time. Engine pauses and clears key state while its window is unfocused."""
    focus=d.get_input_focus().focus
    focus_id=getattr(focus,'id',focus)
    return {'window_id':focus_id if isinstance(focus_id,int) else None,'owned':focus_id==window.id}

def owned_window(d,pid,visible=True):
    atom=d.intern_atom("_NET_WM_PID")
    def visit(w,depth=0):
        try:
            prop=w.get_full_property(atom,X.AnyPropertyType)
            if prop is not None and len(prop.value) and int(prop.value[0])==pid:
                geom=w.get_geometry()
                if geom.width>100 and geom.height>100 and str(w.get_wm_name()).startswith('OpenGGF') and (not visible or w.get_attributes().map_state==X.IsViewable): return w
            if depth<4:
                for child in w.query_tree().children:
                    found=visit(child,depth+1)
                    if found is not None:return found
        except Exception: pass
    return visit(d.screen().root)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath',help='absolute compiled classes and dependency classpath')
    parser.add_argument('--mod-dir',type=Path,help='prepared development mod classes')
    parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[2])
    parser.add_argument('--rom-s1',type=Path);parser.add_argument('--rom-s2',type=Path);parser.add_argument('--rom-s3k',type=Path)
    parser.add_argument('--actions',type=Path,help='JSON lines; omit for interactive stdin')
    parser.add_argument('--disable-vsync',action='store_true',help='child-only driver settings; Engine tick limiter stays enabled')
    parser.add_argument('--keyutils-preload',type=Path,help='existing library for hosts whose OpenAL dependency misses keyutils')
    parser.add_argument('--out',type=Path,required=True);parser.add_argument('--missing-rom',action='store_true')
    parser.add_argument('--unmanaged-window',action='store_true',help='Map only this owned window without host decoration management')
    args=parser.parse_args();root=args.repo.resolve()
    if not (root/'pom.xml').is_file():raise ValueError('Expected an OpenGGF checkout')
    classpath=args.classpath
    if not classpath:
        classpath=os.pathsep.join([str(root/'target/classes'),(root/'target/mutators-classpath.txt').read_text().strip()])
    if any(not Path(entry).is_absolute() for entry in classpath.split(os.pathsep)):
        raise ValueError('Every classpath entry must be absolute: isolated user.dir changes relative resolution')
    if not args.rom_s2 and not args.missing_rom:raise ValueError('Supply --rom-s2 or explicitly select --missing-rom')
    if args.mod_dir and not args.mod_dir.resolve().is_dir():raise ValueError('Development mod directory unavailable')
    if args.keyutils_preload and not args.keyutils_preload.resolve().is_file():raise ValueError('Preload library unavailable')
    out=args.out.resolve()
    if root==out or root in out.parents:raise ValueError('Evidence must stay outside repository')
    if out.exists() and any(out.iterdir()):raise ValueError('Capture requires a fresh empty output directory')
    out.mkdir(parents=True,exist_ok=True)
    config=out/'config';config.mkdir();(config/'empty-roms').mkdir()
    roms={key:value.resolve() for key,value in [('sonic1',args.rom_s1),('sonic2',args.rom_s2),('sonic3k',args.rom_s3k)] if value}
    if args.missing_rom:roms['sonic2']=config/'absent-s2.gen'
    text='roms:\n'+''.join('  '+key+': '+json.dumps(str(value))+'\n' for key,value in roms.items())
    text+='  directory: '+json.dumps(str(config/'empty-roms'))+'\n  default: s2\n'
    text+='display:\n  aspect: NATIVE_4_3\n  windowAutosize: false\ndebug:\n  window:\n    width: 640\n    height: 448\n    scale: 2\n'
    text+='startup:\n  legalDisclaimer: false\n  titleScreen: true\n  masterTitleScreen: true\ncharacters:\n  main: sonic\n  sidekick: ""\n'
    (config/'config.yaml').write_text(text)
    d=None;host=video=sound=None;module=None;focus_window=None
    sink='openggf_capture_'+uuid.uuid4().hex[:12]
    log_path=root/'target'/('engine-window-'+uuid.uuid4().hex+'.log');log_path.parent.mkdir(exist_ok=True)
    log=log_path.open('wb');started=time.monotonic()
    virtual=VirtualDisplay(log)
    receipt={'sink':sink,'config':str(config),'processes':{},'status':'starting','actions':[]}
    def record(): (out/'window-evidence.json').write_text(json.dumps(receipt,indent=2)+'\n')
    def reply(value): print(json.dumps(value),flush=True)
    try:
        env=virtual.start()
        receipt['virtual_display']={'name':virtual.name,'pid':virtual.process.pid,'owned':True}
        record()
        d=display.Display(virtual.name)
        module=subprocess.check_output(['pactl','load-module','module-null-sink','sink_name='+sink,'rate=48000','channels=2'],text=True,timeout=5).strip()
        if not module.isdigit():raise RuntimeError('No exact owned audio module ID')
        receipt['owned_audio_module']=module
        env.update(PULSE_SINK=sink,ALSOFT_DRIVERS='pulse')
        if args.disable_vsync:
            # This own frameless surface gets no normal WM presentation pacing; the Engine retains its tick limiter.
            env.update(vblank_mode='0',__GL_SYNC_TO_VBLANK='0')
        if args.keyutils_preload:
            env['LD_PRELOAD']=str(args.keyutils_preload.resolve())+(':'+env['LD_PRELOAD'] if env.get('LD_PRELOAD') else '')
        command=['java','-Duser.dir='+str(config),'-Dopenggf.saveRoot='+str(out/'player-settings')]
        if args.mod_dir:command.append('-Dggfmod.dev.modDir='+str(args.mod_dir.resolve()))
        command+=['-cp',classpath,'com.openggf.Engine']
        receipt['command']=command
        receipt['child_environment']={key:env.get(key) for key in ['DISPLAY','WAYLAND_DISPLAY','XDG_SESSION_TYPE','ALSOFT_DRIVERS','PULSE_SINK','LD_PRELOAD','vblank_mode','__GL_SYNC_TO_VBLANK']}
        host=subprocess.Popen(command,cwd=root,env=env,stdout=log,stderr=log)
        receipt['processes']['engine']=host.pid;record()
        window=None;selected_title=None;deadline=time.monotonic()+90;thread_sampled=False
        while window is None and host.poll() is None and time.monotonic()<deadline:
            window=owned_window(d,host.pid,not args.unmanaged_window)
            if window is not None:selected_title=window.get_wm_name()
            if window is not None and args.unmanaged_window:
                receipt['host_window_management']='owned override_redirect window'
                window.change_attributes(override_redirect=True)
                window.configure(x=0,y=0,width=640,height=448,stack_mode=X.Above);window.map();d.sync();time.sleep(.3)
            if not thread_sampled and time.monotonic()-started>20:
                thread_sampled=True
                subprocess.run(['jcmd',str(host.pid),'Thread.print'],stdout=log,stderr=log,timeout=10)
            time.sleep(.05)
        if window is None:raise RuntimeError('Owned Engine window unavailable; inspect temporary stderr tail')
        window.configure(x=0,y=0,width=640,height=448,stack_mode=X.Above);d.sync();time.sleep(.3)
        window.set_input_focus(X.RevertToParent,X.CurrentTime);d.sync()
        # Fail closed immediately before starting recorders, even after our map/resize operations.
        pid_property=window.get_full_property(d.intern_atom('_NET_WM_PID'),X.AnyPropertyType)
        geom=window.get_geometry();attributes=window.get_attributes();window_title=window.get_wm_name()
        if host.poll() is not None or pid_property is None or len(pid_property.value)!=1 or int(pid_property.value[0])!=host.pid:
            raise RuntimeError('Owned Engine PID changed before recorder start')
        if window_title!=selected_title or not str(window_title).startswith('OpenGGF') or attributes.map_state!=X.IsViewable or geom.width<=0 or geom.height<=0:
            raise RuntimeError('Owned Engine title/visibility/geometry invalid before recorder start')
        receipt['window_title']=window_title;receipt['window_id']=window.id;receipt['window_size']=[geom.width,geom.height]
        receipt['window_depth']=geom.depth;receipt['window_map_state']=attributes.map_state
        grab=['ffmpeg','-hide_banner','-loglevel','error','-y','-f','x11grab','-window_id',str(window.id),
              '-video_size',str(geom.width)+'x'+str(geom.height),'-framerate','30','-draw_mouse','0','-i',virtual.name]
        video=subprocess.Popen(grab+['-c:v','libx264','-preset','veryfast','-crf','16',str(out/'window.mkv')],env=env,stdout=subprocess.DEVNULL,stderr=log)
        sound=subprocess.Popen(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','pulse','-i',sink+'.monitor',
                                '-ar','48000','-ac','2','-c:a','pcm_s16le',str(out/'device-output.wav')],env=env,stdout=subprocess.DEVNULL,stderr=log)
        receipt['processes'].update(video=video.pid,audio=sound.pid)
        receipt['recording_started_seconds']=round(time.monotonic()-started,3)
        receipt['status']='recording';record()
        reply({'ready':True,'engine_pid':host.pid,'window_id':window.id,'log':str(log_path)})
        pending=args.actions.read_bytes().splitlines() if args.actions else [];buffer=b'';intentional_stop=False
        while host.poll() is None and time.monotonic()-started<600:
            if virtual.process.poll() is not None:raise RuntimeError('Owned virtual display stopped early')
            if video.poll() is not None or sound.poll() is not None:raise RuntimeError('Output recorder stopped early')
            if not pending:
                if args.actions:intentional_stop=True;break
                if not select.select([sys.stdin],[],[],1)[0]:continue
                chunk=os.read(sys.stdin.fileno(),65536)
                if not chunk:intentional_stop=True;break
                buffer+=chunk
                *lines,buffer=buffer.split(b'\n')
                pending.extend(line for line in lines if line.strip())
            if not pending:continue
            action=json.loads(pending.pop(0));op=action['op'];stamp=round(time.monotonic()-started,3)
            receipt['actions'].append({'at_seconds':stamp,**action});record()
            if op=='key':
                window.set_input_focus(X.RevertToParent,X.CurrentTime);d.sync()
                focus=key_focus(d,window);receipt['actions'][-1]['focus']=focus;record()
                if action.get('require_focus') and not focus['owned']:
                    raise RuntimeError('Owned Engine window lacked X input focus at key time')
                names=action.get('keys',[action.get('key','Return')]);codes=[d.keysym_to_keycode(XK.string_to_keysym(name)) for name in names]
                if not all(codes):raise ValueError('Unknown X11 key')
                for event_type,mask in ((X.KeyPress,X.KeyPressMask),(X.KeyRelease,X.KeyReleaseMask)):
                    for code in codes:
                        event_class=xevent.KeyPress if event_type==X.KeyPress else xevent.KeyRelease
                        event=event_class(detail=code,time=X.CurrentTime,root=d.screen().root,
                            window=window,child=X.NONE,root_x=0,root_y=0,event_x=0,event_y=0,state=0,same_screen=1)
                        window.send_event(event,event_mask=mask)
                    d.flush();time.sleep(min(2,max(.08,float(action.get('hold',.09)))))
            elif op=='wait':time.sleep(min(30,max(0,float(action.get('seconds',1)))))
            elif op=='screenshot':
                name=action.get('name','window')
                if not name.replace('-','').replace('_','').isalnum():raise ValueError('Unsafe image name')
                image=out/(name+'.png')
                subprocess.run(grab+['-frames:v','1',str(image)],env=env,stdout=subprocess.DEVNULL,stderr=log,check=True,timeout=10)
                reply({'image':str(image)})
            elif op=='focus-away':
                if focus_window is None:
                    focus_window=d.screen().root.create_window(0,0,1,1,0,d.screen().root_depth,X.InputOutput,X.CopyFromParent,override_redirect=True)
                    focus_window.map()
                focus_window.set_input_focus(X.RevertToParent,X.CurrentTime);d.sync()
            elif op=='focus-back':window.set_input_focus(X.RevertToParent,X.CurrentTime);d.sync()
            elif op=='close':
                event=xevent.ClientMessage(window=window,client_type=d.intern_atom('WM_PROTOCOLS'),
                    data=(32,[d.intern_atom('WM_DELETE_WINDOW'),X.CurrentTime,0,0,0]))
                window.send_event(event,event_mask=X.NoEventMask);d.flush()
                try:host.wait(timeout=7)
                except subprocess.TimeoutExpired:raise RuntimeError('Engine did not close its owned window normally')
                require_successful_close(host)
                receipt['engine_exit_code']=host.returncode;receipt['status']='closed';record();reply({'closed':host.returncode});break
            else:raise ValueError('Unsupported command')
            reply({'done':op,'elapsed_seconds':round(time.monotonic()-started,3)})
        if host.poll() is None and time.monotonic()-started>=600:raise RuntimeError('Bounded probe expired')
        finish_engine_run(host,intentional_stop,receipt)
    except BaseException as failure:
        receipt['status']='failed';receipt['failure']=repr(failure)
        raise
    finally:
        primary_failure=sys.exc_info()[0] is not None
        cleanup=cleanup_owned({'engine':host,'video':video,'audio':sound},focus_window,d,module,log,virtual)
        receipt['cleanup']=cleanup
        receipt['all_owned_processes_stopped']=all(cleanup['processes_stopped'].values()) and cleanup['virtual_display_stopped']
        receipt['owned_audio_module_removed']=module if cleanup['audio_module_unloaded'] else None
        receipt['temporary_log']=str(log_path)
        if cleanup['errors'] and not primary_failure:receipt['status']='cleanup failed'
        try:
            record();reply({'cleanup':not cleanup['errors'],'log':str(log_path)})
        except BaseException:
            if not primary_failure:raise
        if cleanup['errors'] and not primary_failure:
            raise RuntimeError('Owned capture cleanup failed: '+'; '.join(cleanup['errors']))
if __name__=='__main__':main()
