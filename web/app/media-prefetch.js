/* Posted public media only; metadata and private stories keep their authorization rules. */
(function(scope){
  'use strict';
  let ready=null,state={queued:0,pending:0,completed:0,failed:0,bytes:0};
  const waiting=new Set();
  async function worker(){
    if(!('serviceWorker' in navigator))return null;
    if(!ready)ready=navigator.serviceWorker.register('/media-cache-worker.js',{scope:'/',updateViaCache:'none'})
      .then(()=>navigator.serviceWorker.ready).then(reg=>reg.active).catch(()=>null);
    return ready;
  }
  function enqueueUrls(urls){
    for(const raw of urls)try{
      const u=new URL(raw,location.origin);
      if(u.origin===location.origin && /^\/media\/u\/[a-f0-9-]{36}\.(?:jpe?g|png|webp|gif|mp4|webm)$/i.test(u.pathname) && !u.search)waiting.add(u.href);
    }catch{}
    if(!waiting.size)return;
    state={...state,queued:waiting.size,pending:waiting.size};
    void worker().then(active=>{
      if(!active){waiting.clear();state={...state,queued:0,pending:0};return;}
      const urls=[...waiting];waiting.clear();const channel=new MessageChannel();
      channel.port1.onmessage=event=>{state={...event.data};if(state.pending===0)channel.port1.close();};
      active.postMessage({type:'OGT_PREFETCH_MEDIA',urls},[channel.port2]);
    });
  }
  function enqueue(posts,mediaUrl=url=>url){
    const urls=[];
    for(const post of posts||[])for(const item of scope.OgtPostCarousel?.rows(post)||[]){urls.push(mediaUrl(item.url));if(item.posterUrl)urls.push(mediaUrl(item.posterUrl));}
    enqueueUrls(urls);
  }
  scope.OgtMediaPrefetch={enqueue,enqueueUrls,stats:()=>({...state})};
})(globalThis);
