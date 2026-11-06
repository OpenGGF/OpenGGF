 let historyBins=[],historySelected=null,historyScope=0,historyPage=0;
 const iso=t=>new Date(t*1000).toISOString().slice(0,10);
 const dateLabel=d3.utcFormat('%d %b %Y');
 const byPath=new Map(files.map(f=>[path(f),f.id]));
 const historyCommits=H.commits.map((r,id)=>({id,sha:r[0],time:r[1],subject:r[2],stats:r[3],sample:r[4]}));
 function historyWindow(){
  const a=$('history-from').value,b=$('history-to').value;
  return [new Date(a+'T00:00:00Z'),new Date(new Date(b+'T00:00:00Z').getTime()+86400000)];
 }
 function initializeHistory(){
  const end=new Date(H.end*1000),year=end.getUTCFullYear();
  $('history-range').options[0].textContent=year.toString();
  for(const id of ['history-from','history-to']){$(id).min=iso(H.start);$(id).max=iso(H.end)}
  function preset(){
   const value=$('history-range').value;let start;
   if(value==='custom'){drawHistory();return}
   if(value==='all')start=iso(H.start);
   else if(value==='90')start=iso(H.end-89*86400);
   else start=year+'-01-01';
   $('history-from').value=start;$('history-to').value=iso(H.end);historySelected=null;historyPage=0;drawHistory();
  }
  $('history-range').onchange=preset;
  for(const id of ['history-from','history-to'])$(id).onchange=()=>{$('history-range').value='custom';historySelected=null;historyPage=0;drawHistory()};
  $('history-scope').onchange=()=>{historyScope=Number($('history-scope').value);historySelected=null;historyPage=0;drawHistory()};
  for(const id of ['show-diff','show-milestones'])$(id).onchange=()=>{drawHistory();remember()};
  $('history-method').textContent=fmt(H.firstParentCommits)+' first-parent commits, from '+fmt(H.reachableCommits)+' reachable commits, through '+H.pin.slice(0,12)+'. Each integration is diffed against its first parent, so branch commits are not counted a second time. Exact daily totals cover all history. Commit/file drill-down is bundled for the '+H.detailLimit+' most recent first-parent commits; older intervals still have complete totals. UTC committer timestamps determine bins. git numstat supplies additions/removals; binaries have file-change counts but no line counts. Rename detection is disabled, so a path move appears as removal plus addition. Milestones are selected source checkpoints and release-index tags, plotted at their target commit timestamps, not claimed publication dates or complete feature certification. The source census above remains the current snapshot, not a historical class count. Diff volume measures activity, not progress or quality.';
  preset();
 }
 function historySelection(){
  const [start,end]=historyWindow();
  const commits=historyCommits.filter(c=>c.time*1000>=start&&c.time*1000<end);
  const events=H.milestones.filter(m=>m.time*1000>=start&&m.time*1000<end);
  const days=(end-start)/86400000,interval=days>730?d3.utcMonth:days>100?d3.utcMonday:d3.utcDay;
  const name=days>730?'month':days>100?'week':'day';
  const bins=new Map(interval.range(interval.floor(start),interval.ceil(end)).map(d=>[+d,{date:d,end:interval.offset(d,1),adds:0,removes:0,edits:0,binaries:0,commitCount:0,commits:[]} ]));
  for(const day of H.daily){if(day[0]*1000<start||day[0]*1000>=end)continue;const key=+interval.floor(new Date(day[0]*1000));if(!bins.has(key))continue;const b=bins.get(key),s=day[2][historyScope];b.adds+=s[0];b.removes+=s[1];b.edits+=s[2];b.binaries+=s[3];b.commitCount+=s[4]}
  for(const c of commits){const key=+interval.floor(new Date(c.time*1000));if(bins.has(key))bins.get(key).commits.push(c)}
  historyBins=[...bins.values()];
  return {start,end,commits,events,bins:historyBins,interval,name};
 }
 function chartBase(id,height,start,end){
  const svg=d3.select('#'+id),W=Math.max(280,$(id).clientWidth),left=90,right=18,top=27,bottom=44;
  svg.selectAll('*').remove();svg.attr('viewBox','0 0 '+W+' '+height).style('height',height+'px');
  const x=d3.scaleUtc().domain([start,end]).range([left+8,W-right-8]);
  svg.append('rect').attr('data-chart-frame','').attr('x',left).attr('y',top).attr('width',W-left-right).attr('height',height-top-bottom).attr('fill','none').attr('stroke','var(--border)');
  const sameYear=start.getUTCFullYear()===new Date(end-1).getUTCFullYear();
  const axis=svg.append('g').attr('transform','translate(0,'+(height-bottom)+')').call(d3.axisBottom(x).ticks(W<481?3:6).tickFormat(sameYear?d3.utcFormat('%b'):d3.utcFormat('%Y')).tickSizeOuter(0));
  axis.selectAll('text').each(function(){const b=this.getBBox(),tx=Number(this.parentNode.getAttribute('transform').match(/[\d.]+/)[0]);if(b.x+tx<4)this.setAttribute('text-anchor','start');if(b.x+b.width+tx>W-4)this.setAttribute('text-anchor','end')});
  svg.append('text').attr('class','axis-title').attr('data-axis','x').attr('x',(left+W-right)/2).attr('y',height-7).attr('text-anchor','middle').text('UTC date');
  return {svg,W,left,right,top,bottom,height,x};
 }
 function tooltip(event,html){
  const tip=$('history-tooltip');tip.innerHTML=html;tip.hidden=false;
  const box=$('history-view').getBoundingClientRect(),x=event.clientX-box.left,y=event.clientY-box.top;
  tip.style.left=Math.max(0,Math.min(box.width-265,x+12))+'px';tip.style.top=(y+14)+'px';
 }
 function drawDiff(selection){
  const {start,end,bins}=selection,{svg,W,left,right,top,bottom,height,x}=chartBase('history-diff-svg',290,start,end);
  svg.append('title').text('Git diff timeline: additions and removals per '+selection.name);
  const maxAdds=d3.max(bins,b=>b.adds)||1,maxRemoves=d3.max(bins,b=>b.removes)||1;
  const y=d3.scaleLinear().domain([-maxRemoves*1.08,maxAdds*1.08]).range([height-bottom,top]);
  svg.append('g').attr('transform','translate('+left+',0)').call(d3.axisLeft(y).ticks(5).tickFormat(d3.format('~s')).tickSizeOuter(0));
  svg.append('text').attr('class','axis-title').attr('data-axis','y').attr('x',10).attr('y',16).text('Git lines');
  svg.append('line').attr('x1',left).attr('x2',W-right).attr('y1',y(0)).attr('y2',y(0)).attr('stroke','var(--border)');
  const barX=b=>x(new Date(Math.max(+b.date,+start))),barEnd=b=>x(new Date(Math.min(+b.end,+end)));
  for(const [key,color,sign] of [['adds',palette[1],1],['removes',palette[4],-1]]){
   svg.append('g').selectAll('rect').data(bins).join('rect').attr('data-series',key).attr('x',b=>barX(b)+.5).attr('width',b=>Math.max(.5,barEnd(b)-barX(b)-1)).attr('y',b=>Math.min(y(0),y(sign*b[key]))).attr('height',b=>Math.abs(y(sign*b[key])-y(0))).attr('fill',color).attr('opacity',.8);
  }
  const guide=svg.append('line').attr('data-chart-hover-guide','').attr('y1',top).attr('y2',height-bottom).attr('stroke','var(--muted-foreground)').attr('opacity',0);
  const markers=svg.append('g').attr('opacity',0);
  const addMark=markers.append('circle').attr('data-chart-hover-marker','').attr('r',3).attr('fill',palette[1]);
  const removeMark=markers.append('circle').attr('data-chart-hover-marker','').attr('r',3).attr('fill',palette[4]);
  const bisect=d3.bisector(b=>+b.date).right;
  const getBin=mx=>bins[Math.max(0,Math.min(bins.length-1,bisect(bins,+x.invert(mx))-1))];
  svg.append('rect').attr('data-chart-hit','').attr('data-chart-hover-overlay','cross-series').attr('x',left).attr('y',top).attr('width',W-left-right).attr('height',height-top-bottom).attr('fill','transparent').style('cursor','crosshair')
   .on('pointermove',function(event){const [raw]=d3.pointer(event,svg.node()),mx=Math.max(left+8,Math.min(W-right-8,raw)),b=getBin(mx);if(!b)return;guide.attr('x1',mx).attr('x2',mx).attr('opacity',.7);markers.attr('opacity',1);addMark.attr('cx',mx).attr('cy',y(b.adds));removeMark.attr('cx',mx).attr('cy',y(-b.removes));tooltip(event,'<strong>'+escape(dateLabel(b.date))+'</strong><br>+'+fmt(b.adds)+' / −'+fmt(b.removes)+' lines<br>'+fmt(b.commitCount)+' commits touching this scope')})
   .on('pointerleave',()=>{guide.attr('opacity',0);markers.attr('opacity',0);$('history-tooltip').hidden=true})
   .on('click',function(event){const [mx]=d3.pointer(event,svg.node()),b=getBin(mx);if(b)chooseBin(b)});
  $('history-bin-note').textContent='Binned by '+selection.name+'; hover for counts, select an interval for its commits. Binary changes have no line height. '+H.scopes[historyScope]+'.';
  const controls=$('history-bin-controls');controls.replaceChildren();const label=document.createElement('label');label.textContent='Inspect an interval';const picker=document.createElement('select');picker.id='history-bin-picker';
  for(const b of bins){const option=document.createElement('option');option.value=String(+b.date);option.textContent=dateLabel(b.date)+' · '+fmt(b.commitCount)+' commits';picker.appendChild(option)}
  if(historySelected?.bin!==undefined)picker.value=String(historySelected.bin);label.appendChild(picker);controls.appendChild(label);picker.onchange=()=>{const b=bins.find(b=>+b.date===Number(picker.value));if(b)chooseBin(b)};
 }
 function shortMilestone(m){
  if(m.kind==='Release tag')return 'v'+m.label.split('.').slice(0,2).join('.');
  return {'First commit':'First commit','Multi-game configuration':'Multi-game','S3K data foundation':'S3K data','Rewind controller':'Rewind','Stageplay feature checkpoint':'Stageplay','Mod API feature checkpoint':'Mod API','Multiplayer feature checkpoint':'Multiplayer','Native-time test reels':'Test reels','Protocol 8 hardening':'Protocol 8','Atlas source snapshot':'Snapshot'}[m.label]||m.label;
 }
 function drawMilestones(selection){
  const {start,end,events}=selection,lanes=H.lanes.filter(lane=>events.some(m=>m.lane===lane));
  const frame=chartBase('history-milestone-svg',Math.max(190,lanes.length*44+85),start,end),{svg,W,left,right,top,bottom,height,x}=frame;
  svg.append('title').text('Project milestone checkpoints and release tags at their source commit timestamps');
  svg.append('text').attr('class','axis-title').attr('data-axis','y').attr('x',1).attr('y',16).text('Milestone area');
  const y=d3.scalePoint().domain(lanes).range([top+23,height-bottom-23]);
  for(const lane of lanes){const yy=y(lane);svg.append('line').attr('x1',left).attr('x2',W-right).attr('y1',yy).attr('y2',yy).attr('stroke','var(--border)');svg.append('text').attr('x',left-9).attr('y',yy+4).attr('text-anchor','end').text(lane)}
  const used=new Map();
  for(const m of events){
   const xx=x(new Date(m.time*1000)),yy=y(m.lane),selected=historySelected?.kind==='milestone'&&historySelected.sha===m.sha;
   const shape=m.kind==='Release tag'?d3.symbolDiamond:d3.symbolCircle;
   const marker=svg.append('path').attr('data-milestone',m.sha).attr('d',d3.symbol().type(shape).size(selected?130:70)()).attr('transform','translate('+xx+','+yy+')').attr('fill',m.kind==='Release tag'?palette[2]:palette[1]).style('cursor','pointer');
   marker.on('pointermove',event=>tooltip(event,escape(m.label)+'<br>'+escape(dateLabel(new Date(m.time*1000)))+' · '+m.sha)).on('pointerleave',()=>{$('history-tooltip').hidden=true}).on('click',()=>chooseMilestone(m));
   const label=svg.append('text').attr('x',xx).attr('y',yy-12).attr('text-anchor',xx>W-100?'end':'start').text(shortMilestone(m));
   let box=label.node().getBBox();if(box.x<4){label.attr('x',xx+4-box.x);box=label.node().getBBox()}
   if(box.x+box.width>W-4){label.attr('x',Number(label.attr('x'))-(box.x+box.width-W+4));box=label.node().getBBox()}
   const occupied=used.get(m.lane)||[];if(occupied.some(([a,b])=>box.x<b+8&&box.x+box.width>a-8))label.remove();else{occupied.push([box.x,box.x+box.width]);used.set(m.lane,occupied)}
  }
  const list=$('history-milestone-list');list.replaceChildren();$('milestone-count').textContent=events.length+' milestones in range · inspect evidence';
  for(const m of events){const b=actionButton(m.label,iso(m.time),()=>chooseMilestone(m));list.appendChild(b)}
  if(!events.length)svg.append('text').attr('x',(left+W-right)/2).attr('y',90).attr('text-anchor','middle').text('No selected checkpoints in this interval');
 }
 function chooseBin(b){historySelected={kind:'bin',bin:+b.date};historyPage=0;$('history-bin-picker').value=String(+b.date);renderHistoryDetail();remember()}
 function chooseMilestone(m){historySelected={kind:'milestone',sha:m.sha};$('history-tooltip').hidden=true;drawMilestones(historySelection());renderHistoryDetail();remember()}
 function chooseCommit(c,bin){historySelected={kind:'commit',id:c.id,bin};renderHistoryDetail();remember()}
 function renderHistoryDetail(){
  const panel=$('history-detail');panel.replaceChildren();
  if(!historySelected){panel.innerHTML='<p class="note">Select a Git interval or milestone to inspect its evidence.</p>';return}
  if(historySelected.kind==='milestone'){
   const m=H.milestones.find(m=>m.sha===historySelected.sha);if(!m)return;
   panel.innerHTML='<h3>'+escape(m.label)+'</h3><p>'+escape(dateLabel(new Date(m.time*1000)))+' · '+escape(m.lane)+' · '+escape(m.kind)+'</p><p>'+escape(m.why)+'</p><p class="path">'+m.sha+' · '+escape(m.subject)+'</p>';return;
  }
  const bin=historyBins.find(b=>+b.date===historySelected.bin);
  if(historySelected.kind==='commit'){
   const c=historyCommits[historySelected.id],s=c.stats[historyScope];
   panel.innerHTML='<h3>'+escape(c.subject)+'</h3><p class="path">'+c.sha+' · '+new Date(c.time*1000).toISOString()+'</p><p>+'+fmt(s[0])+' / −'+fmt(s[1])+' lines · '+fmt(s[2])+' file edits'+(s[3]?' · '+fmt(s[3])+' binary changes':'')+'</p>';
   const back=document.createElement('button');back.type='button';back.className='plain';back.textContent='Back to interval';back.onclick=()=>{if(bin)chooseBin(bin)};panel.appendChild(back);
   const note=document.createElement('p');note.className='note';note.textContent='Largest '+Math.min(5,s[2])+' changed paths in this scope, by added + removed lines. Counts are this integration versus its first parent.';panel.appendChild(note);
   for(const [pid,a,d,binary] of c.sample[historyScope]){
    const name=H.paths[pid],row=document.createElement('div');row.className='row';const text=document.createElement('span');text.textContent=name;const value=document.createElement('span');value.textContent=binary?'binary':'+'+fmt(a)+' / −'+fmt(d);row.append(text,value);panel.appendChild(row);
    if(byPath.has(name)){const b=document.createElement('button');b.type='button';b.className='crumb';b.textContent='Inspect current source';b.onclick=()=>openFile(byPath.get(name));panel.appendChild(b)}
   }
   return;
  }
  if(!bin)return;const matching=bin.commits.filter(c=>c.stats[historyScope][2]>0).sort((a,b)=>b.time-a.time);
  panel.innerHTML='<h3>Interval beginning '+escape(dateLabel(bin.date))+'</h3><p>+'+fmt(bin.adds)+' / −'+fmt(bin.removes)+' lines · '+fmt(bin.edits)+' file edits · '+fmt(bin.commitCount)+' commits touching '+escape(H.scopes[historyScope].toLowerCase())+'</p>';
  const coverage=document.createElement('p');coverage.className='note';coverage.textContent=matching.length+' of '+bin.commitCount+' commits have bundled file details here. Complete interval totals cover all commits; detailed drill-down covers the '+H.detailLimit+' most recent first-parent commits.';panel.appendChild(coverage);
  const list=document.createElement('div');list.className='items';panel.appendChild(list);
  for(const c of matching.slice(historyPage*6,historyPage*6+6))list.appendChild(actionButton(c.subject,c.sha.slice(0,8),()=>chooseCommit(c,+bin.date)));
  if(!matching.length){const p=document.createElement('p');p.className='note';p.textContent=bin.commitCount?'This older interval has complete aggregate counts; individual file details are outside the bundled recent-commit window.':'No changes to this scope in this interval.';panel.appendChild(p)}
  if(matching.length>6){const pager=document.createElement('div');pager.className='pager';const prev=document.createElement('button'),next=document.createElement('button'),text=document.createElement('span');prev.type=next.type='button';prev.textContent='Previous';next.textContent='Next';prev.disabled=historyPage===0;next.disabled=(historyPage+1)*6>=matching.length;text.textContent=(historyPage*6+1)+'–'+Math.min((historyPage+1)*6,matching.length)+' of '+matching.length;prev.onclick=()=>{historyPage--;renderHistoryDetail()};next.onclick=()=>{historyPage++;renderHistoryDetail()};pager.append(prev,text,next);panel.appendChild(pager)}
 }
 function drawHistory(){
  if(mode!=='history')return;
  const [start,end]=historyWindow();
  const valid=Number.isFinite(+start)&&Number.isFinite(+end)&&start<end;
  $('history-diff-section').hidden=!$('show-diff').checked||!valid;$('history-milestone-section').hidden=!$('show-milestones').checked||!valid;
  if(!valid){$('history-summary').textContent='Choose a valid date window with From on or before Through.';$('history-detail').replaceChildren();return}
  const selection=historySelection(),adds=d3.sum(selection.bins,b=>b.adds),removes=d3.sum(selection.bins,b=>b.removes),matching=d3.sum(selection.bins,b=>b.commitCount);
  $('history-summary').textContent=fmt(matching)+' first-parent commits touching '+H.scopes[historyScope].toLowerCase()+' · +'+fmt(adds)+' / −'+fmt(removes)+' lines · '+selection.bins.length+' '+selection.name+' bins. Current source census remains above.';
  if(!historySelected||historySelected.kind!=='milestone'&&!selection.bins.some(b=>+b.date===historySelected.bin)){
   const last=selection.bins.slice().reverse().find(b=>b.commitCount>0);historySelected=last?{kind:'bin',bin:+last.date}:null;historyPage=0;
  }
  if($('show-diff').checked)drawDiff(selection);
  if($('show-milestones').checked)drawMilestones(selection);
  renderHistoryDetail();
 }
