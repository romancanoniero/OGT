(function(){
"use strict";
const mode=document.documentElement.dataset.crm;
const root=document.getElementById("crm-root");
const status=document.getElementById("crm-status");
const login=document.getElementById("crm-login");
const userEmail=document.getElementById("email");
const password=document.getElementById("password");
const setStatus=(s)=>{status.textContent=s;};
const label=(name,val)=>{const p=document.createElement("p"),b=document.createElement("strong");b.textContent=name+": ";p.append(b,document.createTextNode(String(val)));return p;};
function api(path,token,body={}){
return fetch(path,{method:"POST",headers:{"Content-Type":"application/json","Authorization":"Bearer "+token},body:JSON.stringify(body),cache:"no-store",credentials:"omit"}).then(async r=>{const data=await r.json();if(!r.ok||!data.success)throw new Error(data.message||"Acceso no autorizado");return data.data;});
}
function useFirebase(){
if(!window.firebase||!firebase.auth)throw new Error("Firebase no disponible");
if(!firebase.apps.length)firebase.initializeApp({apiKey:"AIzaSyB1RV-RbmE-JDpJuczwVed2bV-cnSaPZSQ",authDomain:"goodthings-55612.firebaseapp.com",projectId:"goodthings-55612",appId:"1:521063054927:android:12ddeef4d6ee824f30743a",messagingSenderId:"521063054927"});
return firebase.auth();
}
async function checkAccess(){
root.replaceChildren();setStatus("Verificando identidad, segundo factor y permisos…");
const auth=useFirebase(),user=auth.currentUser;
if(!user){login.hidden=false;setStatus("Iniciá sesión para acceder al CRM.");return;}
const token=await user.getIdToken(true);
const access=await api("/api/v1/crm/access",token);
if(mode==="control"&&!access.platformRole)throw new Error("Tu cuenta no está autorizada para la administración central.");
if(mode==="empresa"&&!access.companies.length)throw new Error("Tu cuenta no tiene empresas autorizadas.");
login.hidden=true;
const h=document.createElement("h2");h.textContent=mode==="control"?"Resumen operativo":"Empresas autorizadas";root.append(h);
if(mode==="control"){
const summary=await api("/api/v1/crm/platform/summary",token);
for(const [n,v] of [["Usuarios activos",summary.users],["Empresas",summary.companies],["Empresas por verificar",summary.pendingCompanies],["Denuncias pendientes",summary.openReports],["Publicaciones visibles",summary.visiblePosts]])root.append(label(n,v));
}else{
const choose=document.createElement("select");choose.setAttribute("aria-label","Seleccionar empresa");
for(const grant of access.companies){const o=document.createElement("option");o.value=grant.companyId;o.textContent=grant.companyId+" ("+grant.role+")";choose.append(o);}
root.append(choose);
const content=document.createElement("div");root.append(content);
const refresh=async()=>{content.replaceChildren();const x=await api("/api/v1/crm/company/summary",token,{companyId:choose.value});for(const [n,v] of [["Empresa",x.tradeName||x.legalName],["Estado",x.verificationStatus],["Campañas",x.campaigns],["Campañas activas",x.activeCampaigns],["Cupones emitidos",x.issuedCoupons],["Saldo en centavos",x.balanceCents],["Puntaje de impacto",x.impactScore]])content.append(label(n,v));};
choose.addEventListener("change",()=>refresh().catch(err=>setStatus(err.message)));await refresh();
}
setStatus("Acceso autorizado. Información obtenida directamente del servidor.");
}
document.getElementById("crm-login").addEventListener("submit",async ev=>{ev.preventDefault();setStatus("Iniciando sesión…");try{await useFirebase().signInWithEmailAndPassword(userEmail.value,password.value);password.value="";await checkAccess();}catch(err){setStatus(err.code==="auth/multi-factor-auth-required"?"Tu cuenta requiere completar la autenticación multifactor en la aplicación principal. No se omitirá ese control.":err.message);}});
document.getElementById("crm-refresh").addEventListener("click",()=>checkAccess().catch(err=>setStatus(err.message)));
useFirebase().onAuthStateChanged(()=>checkAccess().catch(err=>{root.replaceChildren();setStatus(err.message);login.hidden=false;}));
})();