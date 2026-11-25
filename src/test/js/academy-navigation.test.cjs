const test=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const source=fs.readFileSync('src/main/webapp/resources/llavor/js/academy-shell.js','utf8');
function fixture(width){
 const events=()=>({listeners:{},addEventListener(name,fn){(this.listeners[name]??=[]).push(fn)},fire(name,event={}){for(const fn of this.listeners[name]||[])fn(event)}});
 const document={...events(),activeElement:null};
 class Element {
  constructor(id){Object.assign(this,events());this.id=id;this.children=[];this.parentElement=null;this.attrs={};this.dataset={};this.hidden=false;this.inert=false;this.focusable=false;this.classList={values:new Set(),add(name){this.values.add(name)},remove(name){this.values.delete(name)}};}
  appendChild(child){if(child.parentElement)child.parentElement.children=child.parentElement.children.filter(x=>x!==child);this.children.push(child);child.parentElement=this;}
  contains(child){return this===child||this.children.some(item=>item.contains(child));}
  setAttribute(name,value){this.attrs[name]=value;}
  removeAttribute(name){delete this.attrs[name];}
  focus(){document.activeElement=this;}
  getClientRects(){return this.hidden?[]:[{}];}
  closest(selector){if(selector==='[inert]'){for(let p=this;p;p=p.parentElement)if(p.inert)return p;}return null;}
  descendants(){return this.children.flatMap(c=>[c,...c.descendants()]);}
  querySelectorAll(selector){if(selector==='[data-academy-view]')return this.descendants().filter(x=>x.dataset.academyView);if(selector==='[data-academy-panel]')return this.descendants().filter(x=>x.dataset.academyPanel);return this.descendants().filter(x=>x.focusable);}
 }
 const ids={};const add=id=>(ids[id]=new Element(id));
 const shell=add('shell'),sidebar=add('academy-sidebar'),toggle=add('academy-menu-toggle'),close=add('academy-menu-close'),backdrop=add('academy-menu-backdrop'),main=add('academy-main'),header=add('academy-header');
 const controls=add('academy-session-controls'),headerSession=add('academy-header-session'),sidebarSession=add('academy-sidebar-session'),panel=add('academyLocaleForm:locale_panel'),language=add('language'),logout=add('logout');
 header.appendChild(toggle);header.appendChild(headerSession);sidebar.appendChild(close);sidebar.appendChild(sidebarSession);sidebarSession.appendChild(controls);controls.appendChild(language);controls.appendChild(logout);
 for(const item of [close,language,logout])item.focusable=true;
 for(const id of ['inicio','perfil','informacion','acerca']){const link=add('nav-'+id);link.dataset.academyView=id;link.focusable=true;sidebar.appendChild(link);const view=add('view-'+id);view.dataset.academyPanel=id;main.appendChild(view);}
 // Place the session zone last, as it is in the real sidebar.
 sidebar.appendChild(sidebarSession);panel.hidden=true;
 const media={...events(),matches:width<=1023};
 const window={...events(),matchMedia:()=>media,location:{hash:''},scrollTo(){},PrimeFaces:{widgets:{academyLanguageSelector:{hide(){panel.hidden=true}}}}};
 document.getElementById=id=>ids[id]||null;document.querySelector=selector=>selector==='.academy-shell'?shell:null;
 vm.runInNewContext(source,{document,window});
 return {document,window,media,ids,sidebar,toggle,close,backdrop,main,header,controls,headerSession,sidebarSession,panel,language,logout};
}
for(const width of [360,375,390,768])test(`single existing control group moves to sidebar at ${width}px`,()=>{const f=fixture(width);assert.equal(f.controls.parentElement,f.sidebarSession);assert.equal(f.headerSession.contains(f.controls),false);assert.equal(f.sidebar.inert,true);});
test('desktop retains the same controls in its header; resizing preserves identity and focus',()=>{const f=fixture(1440);const original=f.controls;assert.equal(f.controls.parentElement,f.headerSession);f.language.focus();f.media.matches=true;f.media.fire('change');assert.equal(f.controls,original);assert.equal(f.controls.parentElement,f.sidebarSession);assert.equal(f.document.activeElement,f.toggle);f.media.matches=false;f.media.fire('change');assert.equal(f.controls.parentElement,f.headerSession);});
test('menu button, Escape and backdrop preserve modal state and return focus',()=>{const f=fixture(375);f.toggle.fire('click');assert.equal(f.sidebar.attrs['aria-modal'],'true');assert.equal(f.header.inert,true);assert.equal(f.main.inert,true);assert.equal(f.document.activeElement,f.close);f.document.fire('keydown',{key:'Escape',preventDefault(){}});assert.equal(f.sidebar.inert,true);assert.equal(f.document.activeElement,f.toggle);assert.equal(f.header.inert,false);f.toggle.fire('click');f.backdrop.fire('click');assert.equal(f.sidebar.inert,true);});
test('Tab trap includes language and POST logout; Escape dismisses the language popup first',()=>{const f=fixture(390);f.toggle.fire('click');f.logout.focus();let prevented=false;f.document.fire('keydown',{key:'Tab',shiftKey:false,preventDefault(){prevented=true}});assert.equal(prevented,true);assert.equal(f.document.activeElement,f.close);f.document.fire('keydown',{key:'Tab',shiftKey:true,preventDefault(){}});assert.equal(f.document.activeElement,f.logout);f.panel.hidden=false;f.document.fire('keydown',{key:'Escape',preventDefault(){}});assert.equal(f.panel.hidden,true);assert.equal(f.sidebar.attrs['aria-modal'],'true');f.document.fire('keydown',{key:'Escape',preventDefault(){}});assert.equal(f.sidebar.inert,true);});
test('navigation retains its existing destination and closes the drawer',()=>{const f=fixture(375);f.toggle.fire('click');f.ids['nav-perfil'].fire('click');assert.equal(f.ids['view-perfil'].hidden,false);assert.equal(f.ids['view-inicio'].hidden,true);assert.equal(f.sidebar.inert,true);assert.equal(f.document.activeElement,f.main);});
