(function(scope){
  function createDiscoveryLocation(nav=scope.navigator,changed=()=>{},now=()=>Date.now()) {
    let fix=null,at=0,inflight=null,permission=null,revision=0;
    function clear(notify=true){revision++;const had=fix;fix=null;at=0;if(had&&notify)changed();}
    async function state(){
      if(!nav?.permissions?.query)return null;
      try {
        if(!permission){permission=await nav.permissions.query({name:'geolocation'});permission.addEventListener?.('change',()=>{if(permission.state!=='granted')clear();changed();});}
        return permission.state;
      }catch{return null;}
    }
    async function read(){
      if(!nav?.geolocation)throw Error('Elegí tu barrio o localidad.');
      const generation=revision;
      const result=await new Promise((resolve,reject)=>nav.geolocation.getCurrentPosition(resolve,reject,{timeout:5000,maximumAge:120000}));
      if(generation!==revision || permission?.state==='denied')throw Error('Elegí tu barrio o localidad.');
      const latitude=Number(result.coords.latitude),longitude=Number(result.coords.longitude);
      if(!Number.isFinite(latitude)||!Number.isFinite(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180)throw Error('Ubicación inválida.');
      fix={latitude,longitude};at=now();return {...fix};
    }
    async function payload(){
      if(inflight)return inflight;
      inflight=(async()=>{
        const access=await state();
        if(access==='denied'||access==='prompt'){clear();return {};}
        if(fix&&now()-at<120000)return {...fix};
        if(access!=='granted'){clear();return {};}
        try{return await read();}catch{clear();return {};}
      })();
      try{return await inflight;}finally{inflight=null;}
    }
    async function useDevice(){
      inflight=read();try{return await inflight;}catch(e){clear();throw e;}finally{inflight=null;}
    }
    function current(){return fix&&now()-at<120000&&!['denied','prompt'].includes(permission?.state)?{...fix}:null;}
    return {payload,useDevice,clear,current};
  }
  scope.OgtCreateDiscoveryLocation=createDiscoveryLocation;
  scope.OgtDiscoveryLocation=createDiscoveryLocation(scope.navigator,()=>scope.dispatchEvent?.(new Event('ogt-discovery-device')));
})(globalThis);
