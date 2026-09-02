(() => {
'use strict';

let warming=false;
async function warmPromotions(){
  if(warming||!window.OFaroApi||!localStorage.getItem('ofaro_gestion_key'))return;
  warming=true;
  try{
    await OFaroApi.post('promotionList',{}).catch(()=>null);
    await OFaroApi.post('prizeList',{includeNoPrize:true}).catch(()=>null);
    await OFaroApi.post('promotionStats',{}).catch(()=>null);
  }finally{warming=false;}
}

function lockButton(btn,ms=50000){
  if(!btn||btn.dataset.ofaroLocked==='1')return false;
  btn.dataset.ofaroLocked='1';
  btn.setAttribute('aria-busy','true');
  setTimeout(()=>{
    if(!btn.isConnected)return;
    btn.dataset.ofaroLocked='0';
    btn.removeAttribute('aria-busy');
    btn.disabled=false;
  },ms);
  requestAnimationFrame(()=>{if(btn.isConnected)btn.disabled=true;});
  return true;
}

document.addEventListener('click',e=>{
  const btn=e.target.closest('button');
  if(!btn)return;
  if(btn.matches('#pcSave,[data-c-toggle],#promoPrizeSave,#wheelSave,#linksSave,#redeemConfirm')){
    if(btn.dataset.ofaroLocked==='1'){
      e.preventDefault();e.stopImmediatePropagation();return;
    }
    lockButton(btn);
  }
  if(btn.matches('[data-route="participations"],#promoRefresh')){
    setTimeout(warmPromotions,0);
  }
},true);

window.addEventListener('load',()=>setTimeout(warmPromotions,150));
window.addEventListener('online',()=>setTimeout(warmPromotions,300));
})();