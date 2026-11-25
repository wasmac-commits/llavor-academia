/* One copy of each real form. Native details supplies keyboard and no-JS fallback. */
(()=>{
 'use strict';
 const menu=document.getElementById('platform-controls');if(!menu)return;
 const summary=menu.querySelector('summary');
 const compact=window.matchMedia('(max-width: 900px)');
 function sync(){menu.open=!compact.matches;menu.classList.add('is-enhanced');}
 function close(restore=false){if(!compact.matches)return;menu.open=false;if(restore)summary.focus();}
 compact.addEventListener('change',sync);
 document.addEventListener('keydown',event=>{if(event.key==='Escape'&&compact.matches&&menu.open){event.preventDefault();close(true);}});
 document.addEventListener('click',event=>{
  // PrimeFaces language popup is appended to body and belongs to these controls.
  if(!menu.contains(event.target)&&!event.target.closest('.ui-selectonemenu-panel'))close();
 });
 menu.addEventListener('focusout',()=>queueMicrotask(()=>{if(!menu.contains(document.activeElement)&&!document.activeElement.closest('.ui-selectonemenu-panel'))close();}));
 window.addEventListener('pagehide',()=>close());sync();
})();
