 let timePosition=T.steps.length-1;
 const namespaceAliases=new Map(T.aliases||[]);
 function snapshotPath(f){
  const index=T.steps[timePosition][0];
  for(const [name,spans] of namespaceAliases.get(f.id)||[]){
   for(let i=0;i<spans.length;i+=2)if(spans[i]<=index&&index<spans[i+1])return name;
  }
  return path(f);
 }
 function aliasSearchText(f){return (namespaceAliases.get(f.id)||[]).map(([name])=>name+' '+name.replaceAll('/','.')).join(' ')}
 function installStandaloneTheme(){
  const element=document.documentElement;
  if(getComputedStyle(element).getPropertyValue('--foreground').trim())return;
  element.dataset.atlasStandalone='';
  const dark=matchMedia('(prefers-color-scheme: dark)').matches;
  const tokens=dark?{'--background':'#101114','--foreground':'#ededf0','--muted-foreground':'#a4a5ae','--border':'#30313a','--primary':'#3569f6','--primary-foreground':'#ffffff','--accent':'#292d3c','--accent-foreground':'#eeeeff','--secondary':'#24262e','--secondary-foreground':'#eeeeff','--popover':'#24262e','--popover-foreground':'#eeeeff'}:{'--background':'#fafbfe','--foreground':'#242630','--muted-foreground':'#656a79','--border':'#dce0e8','--primary':'#2454dc','--primary-foreground':'#ffffff','--accent':'#e5ecff','--accent-foreground':'#183776','--secondary':'#ebedf4','--secondary-foreground':'#303340','--popover':'#ffffff','--popover-foreground':'#242630'};
  const series=dark?['#477cff','#22caba','#f5c140','#bc83ee','#ef7189','#9cce50']:['#2454dc','#008f87','#ba7000','#8552bd','#c43a5a','#5f8c12'];
  series.forEach((c,i)=>tokens['--chart-'+(i+1)]=c);
  Object.assign(tokens,{'--font-sans':'system-ui, sans-serif','--font-mono':'ui-monospace, monospace','--radius':'8px'});
  for(const [name,value] of Object.entries(tokens))element.style.setProperty(name,value);
 }
 function existsAt(id,index){
  const spans=T.lifetimes[id];
  for(let i=0;i<spans.length;i+=2)if(spans[i]<=index&&index<spans[i+1])return true;
  return false;
 }
 function timeDate(){return new Date(T.steps[timePosition][1]*1000).toISOString().slice(0,10)}
 function fileTimeState(f){return f.present?(f.current?'Exists then and now':'Exists then; removed now'):'Absent at selected snapshot'}
 function sourceStyle(n){
  if(!n)return {kind:'ghost',color:foreground};
  const kind=n.presentCount===0?'ghost':n.removedPresentCount===n.presentCount?'removed':'current';
  const c=d3.color(foreground),v=c?Math.round((c.r+c.g+c.b)/3*.7+128*.3):128;
  const gray='rgb('+v+','+v+','+v+')';
  return {kind,color:kind==='removed'||n.currentCount===0?gray:palette[n.data.domain]};
 }
 function initializeTime(){
  const slider=$('source-time-slider');slider.max=String(T.steps.length-1);slider.value=String(timePosition);
  $('source-time-start').textContent=new Date(T.steps[0][1]*1000).toISOString().slice(0,10)+' ← Earlier';
  slider.addEventListener('input',()=>selectTime(Number(slider.value)));
 }
 function selectTime(position){
  if(!Number.isFinite(Number(position)))return;
  timePosition=Math.max(0,Math.min(T.steps.length-1,Math.round(Number(position))));
  $('source-time-slider').value=String(timePosition);applyTime();remember();
 }
 function applyTime(updateSearch=true){
  const [index,stamp,sha]=T.steps[timePosition];
  for(const f of universeFiles)f.present=existsAt(f.id,index);
  tree.eachAfter(n=>{
   if(!n.children){const f=getFile(n.data.fid);n.presentCount=Number(f.present);n.removedPresentCount=Number(f.present&&!f.current);n.currentCount=Number(f.current)}
   else{n.presentCount=d3.sum(n.children,c=>c.presentCount);n.removedPresentCount=d3.sum(n.children,c=>c.removedPresentCount);n.currentCount=d3.sum(n.children,c=>c.currentCount)}
  });
  const text=timeDate()+' UTC · '+sha+' · integration '+fmt(index+1)+' of '+fmt(T.count)+(timePosition===T.steps.length-1?' · present snapshot':'');
  $('source-time-label').textContent=text;$('source-time-slider').setAttribute('aria-valuetext',text);
  $('source-time-summary').textContent=fmt(tree.presentCount)+' present at this snapshot · '+fmt(tree.leaves().length-tree.presentCount)+' dotted / ghost · '+fmt(tree.removedPresentCount)+' present then but removed now. '+T.steps.length+' exact snapshots; '+fmt(T.namespaceMoves||0)+' namespace moves linked; stable layout.';
  showFocus();draw();if(updateSearch&&$('search').value.trim())search();
 }
