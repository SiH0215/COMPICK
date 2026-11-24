(() => {
 "use strict";
 const active=new Set();const nativeFetch=window.fetch.bind(window);
 async function request(url,options={}) {
  const method=(options.method||"GET").toUpperCase();const key=method+":"+url;
  if(method!=="GET"&&active.has(key))throw new Error("이미 처리 중인 요청입니다. 잠시 기다려 주세요.");
  const button=options.button||(document.activeElement?.tagName==="BUTTON"?document.activeElement:null);
  const disabled=button?.disabled; if(button){button.disabled=true;button.setAttribute("aria-busy","true");}
  active.add(key);
  try {
   for(let attempt=0;attempt<2;attempt++){
    const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),25000);
    const headers=new Headers(options.headers||{});const token=document.querySelector('meta[name="_csrf"]')?.content;
    const header=document.querySelector('meta[name="_csrf_header"]')?.content;
    if(token&&header)headers.set(header,token);
    if(options.body&&typeof options.body==="string"&&!headers.has("Content-Type"))headers.set("Content-Type","application/json");
    try {return await nativeFetch(url,{...options,headers,signal:options.signal||controller.signal});}
    catch(error){
     if(method==="GET"&&attempt===0&&!options.signal?.aborted)continue;
     throw new Error(method==="GET"?"화면을 불러오지 못했습니다. 다시 시도해 주세요.":"처리 결과를 확인하지 못했습니다. 결제는 상태 확인 후 진행하고, 입력한 내용은 유지됩니다.");
    } finally {clearTimeout(timer);}
   }
  } finally {active.delete(key);if(button){button.disabled=disabled;button.removeAttribute("aria-busy");}}
 }
 window.CompickUI={fetch:request};
 document.addEventListener("DOMContentLoaded",()=>{
  const input=document.querySelector("#requirements");const key="compick:ai-draft";
  if(input){
   try {if(!input.value&&!document.querySelector(".result-explanation"))input.value=sessionStorage.getItem(key)||"";}catch{}
   input.addEventListener("input",()=>{try {sessionStorage.setItem(key,input.value);}catch{}});
  }
  document.querySelectorAll("form").forEach(form=>form.addEventListener("submit",event=>{
   if(event.defaultPrevented||!form.checkValidity())return;
   if(form.dataset.submitting){event.preventDefault();return;}
   if(input){try{sessionStorage.setItem(key,input.value);}catch{}}
   form.dataset.submitting="true";
   queueMicrotask(()=>{if(!event.defaultPrevented)form.querySelectorAll('button[type="submit"],button:not([type])').forEach(b=>{b.disabled=true;b.setAttribute("aria-busy","true");});else delete form.dataset.submitting;});
  }));
 });
 window.addEventListener("pageshow",()=>document.querySelectorAll('form[data-submitting]').forEach(f=>{delete f.dataset.submitting;f.querySelectorAll('[aria-busy="true"]').forEach(b=>{b.disabled=false;b.removeAttribute("aria-busy");});}));
})();
