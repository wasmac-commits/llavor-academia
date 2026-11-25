/* Presentation only. Authorization, language and logout remain server controlled. */
(() => {
 'use strict';
 const shell=document.querySelector('.academy-shell');
 const sidebar=document.getElementById('academy-sidebar');
 const toggle=document.getElementById('academy-menu-toggle');
 const closeButton=document.getElementById('academy-menu-close');
 const backdrop=document.getElementById('academy-menu-backdrop');
 const main=document.getElementById('academy-main');
 const header=document.getElementById('academy-header');
 if(!shell||!sidebar||!toggle||!main||!header) return;
 const mobile=window.matchMedia('(max-width: 1023px)');
 const links=[...sidebar.querySelectorAll('[data-academy-view]')];
 const panels=[...main.querySelectorAll('[data-academy-panel]')];
 const sessionControls=document.getElementById('academy-session-controls');
 const headerSession=document.getElementById('academy-header-session');
 const sidebarSession=document.getElementById('academy-sidebar-session');
 const languagePanel=()=>document.getElementById('academyLocaleForm:locale_panel');
 function hideLanguagePanel(){const widget=window.PrimeFaces?.widgets?.academyLanguageSelector;if(widget)widget.hide();}
 function moveSessionControls(){
  const destination=mobile.matches?sidebarSession:headerSession;
  if(sessionControls&&destination&&sessionControls.parentElement!==destination){hideLanguagePanel();destination.appendChild(sessionControls);}
 }
 let opened=false;
 function close(restore=true) {
  hideLanguagePanel();opened=false;shell.classList.remove('menu-open');toggle.setAttribute('aria-expanded','false');
  backdrop.hidden=true;main.inert=false;header.inert=false;
  sidebar.inert=mobile.matches;sidebar.removeAttribute('role');sidebar.removeAttribute('aria-modal');sidebar.removeAttribute('aria-owns');
  if(restore) toggle.focus();
 }
 function open() {
  if(!mobile.matches) return;
  opened=true;sidebar.inert=false;shell.classList.add('menu-open');
  toggle.setAttribute('aria-expanded','true');backdrop.hidden=false;
  main.inert=true;header.inert=true;sidebar.setAttribute('role','dialog');sidebar.setAttribute('aria-modal','true');
  sidebar.setAttribute('aria-owns','academyLocaleForm:locale_panel');
  closeButton.focus();
 }
 function show(id,focus=false) {
  if(!panels.some(panel=>panel.dataset.academyPanel===id)) id='inicio';
  panels.forEach(panel=>{panel.hidden=panel.dataset.academyPanel!==id;});
  links.forEach(link=>{if(link.dataset.academyView===id)link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');});
  close(false);
  if(focus){main.focus();window.scrollTo({top:0,behavior:'instant'});}
 }
 toggle.addEventListener('click',()=>opened?close():open());
 closeButton.addEventListener('click',()=>close());backdrop.addEventListener('click',()=>close());
 document.addEventListener('keydown',event=>{
  if(!opened) return;
  if(event.key==='Escape'){event.preventDefault();if(languagePanel()?.getClientRects().length){hideLanguagePanel();return;}close();return;}
  if(event.key!=='Tab')return;
  const items=[...sidebar.querySelectorAll('a[href],button:not([disabled]),[tabindex="0"]')].filter(item=>!item.hidden&&item.getClientRects().length&&!item.closest('[inert]'));
  const first=items[0],last=items[items.length-1];
  if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
  else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
 });
 links.forEach(link=>link.addEventListener('click',()=>show(link.dataset.academyView,true)));
 window.addEventListener('hashchange',()=>show(window.location.hash.slice(1),true));
 mobile.addEventListener('change',()=>{const active=document.activeElement;const movedFocus=sessionControls?.contains(active);moveSessionControls();close(false);if(movedFocus)(mobile.matches?toggle:active).focus();});
 window.addEventListener('pagehide',()=>close(false));
 moveSessionControls();shell.classList.add('is-enhanced');show(window.location.hash.slice(1));
 // Header may grow with a long real identity, browser zoom, or pending language message.
 if(window.ResizeObserver)new ResizeObserver(entries=>shell.style.setProperty('--academy-header-height',Math.ceil(entries[0].borderBoxSize?.[0]?.blockSize||header.getBoundingClientRect().height)+'px')).observe(header);
})();
