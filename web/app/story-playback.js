/** Author-wide timeline; buffering and page turns do not count as viewing time. */
export const storyDuration = row => row.kind === 'VIDEO' ? Math.max(1, Math.min(30000, Number(row.durationMs) || 1)) : 5000;
export function storyBookProgress(queue, row, elapsed) {
  const pages=queue.filter(s=>s.authorId===row.authorId).sort((a,b)=>a.createdAtEpochMs-b.createdAtEpochMs);
  const at=Math.max(0,pages.findIndex(s=>s.id===row.id));
  const total=pages.reduce((sum,s)=>sum+storyDuration(s),0);
  const position=pages.slice(0,at).reduce((sum,s)=>sum+storyDuration(s),0)+Math.max(0,Math.min(storyDuration(row),elapsed));
  return {total,position,page:at+1,pages:pages.length};
}

/** Session-private bounded media cache, never a shared service-worker/public cache. */
export function createStoryMediaCache({fetchMedia=fetch, makeUrl=blob=>URL.createObjectURL(blob), revokeUrl=url=>URL.revokeObjectURL(url), budget=128*1024*1024}={}) {
  const entries=new Map(), pending=new Map();let allowed=new Set(),disposed=false;
  function remove(key){const item=entries.get(key);if(item)revokeUrl(item.url);entries.delete(key);}
  function authorize(rows){allowed=new Set(rows.map(s=>s.id));for(const [key,item] of entries)if(!allowed.has(item.id)||item.expires<=Date.now())remove(key);for(const [key,item] of pending)if(!allowed.has(item.id)) {item.abort.abort();pending.delete(key);}}
  async function source(row){
    if(disposed||!allowed.has(row.id)||row.expiresAtEpochMs<=Date.now())throw Error('Historia no disponible');
    const key=row.id+'|'+row.mediaUrl.split('?')[0];
    const cached=entries.get(key);if(cached){cached.used=Date.now();return cached.url;}
    if(pending.has(key))return pending.get(key).promise;
    const abort=new AbortController();
    const timeout=setTimeout(()=>abort.abort(),30000);
    const promise=(async()=>{
      const response=await fetchMedia(row.mediaUrl,{signal:abort.signal,credentials:'same-origin'});if(!response.ok)throw Error('No se pudo cargar la historia');
      const blob=await response.blob();if(!blob.size||blob.size>12*1024*1024)throw Error('Archivo de historia inválido');
      if(disposed||!allowed.has(row.id)||row.expiresAtEpochMs<=Date.now()||abort.signal.aborted)throw Error('Historia no disponible');
      const url=makeUrl(blob);entries.set(key,{id:row.id,url,bytes:blob.size,expires:row.expiresAtEpochMs,used:Date.now()});
      let bytes=[...entries.values()].reduce((sum,s)=>sum+s.bytes,0);
      for(const [old,item] of [...entries].sort((a,b)=>a[1].used-b[1].used)){if(bytes<=budget)break;if(old===key)continue;bytes-=item.bytes;remove(old);}
      return url;
    })().finally(()=>{clearTimeout(timeout);if(pending.get(key)?.promise===promise)pending.delete(key);});
    pending.set(key,{id:row.id,abort,promise});return promise;
  }
  function dispose(){disposed=true;for(const item of pending.values())item.abort.abort();pending.clear();for(const key of entries.keys())remove(key);allowed.clear();}
  return {authorize,source,dispose};
}
