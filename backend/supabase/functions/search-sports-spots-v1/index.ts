import "jsr:@supabase/functions-js/edge-runtime.d.ts";
const BASE=Deno.env.get("SUPABASE_URL"), KEY=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const SPORTS=new Set(["all","soccer","basketball","volleyball","tennis","fitness"]);
const send=(obj:unknown,status=200,extra:Record<string,string>={})=>new Response(JSON.stringify(obj),{status,headers:{"content-type":"application/json; charset=utf-8","cache-control":"no-store",...extra}});
function coord(v:string|null,limit:number){if(!v||!/^-?\d{1,3}(\.\d{1,6})?$/.test(v))return null;const n=Number(v);return Math.abs(n)<=limit?n:null;}
async function rpc(name:string, body:Record<string,unknown>){return fetch(BASE+"/rest/v1/rpc/"+name,{method:"POST",headers:{apikey:KEY!,"authorization":"Bearer "+KEY,"content-type":"application/json"},body:JSON.stringify(body),signal:AbortSignal.timeout(3500)});}
function query(lat:number,lon:number,radius:number,sport:string){const filter=sport==="all"?'["leisure"="pitch"]':sport==="fitness"?'["leisure"="fitness_station"]':'["leisure"="pitch"]["sport"~"(^|;)'+sport+'(;|$)"]';return '[out:json][timeout:20];(node(around:'+radius+','+lat+','+lon+')'+filter+';way(around:'+radius+','+lat+','+lon+')'+filter+';relation(around:'+radius+','+lat+','+lon+')'+filter+';);out center 100;';}
function convert(v:unknown){if(!v||typeof v!=="object"||Array.isArray(v))return null;const x=v as Record<string,unknown>;if(!["node","way","relation"].includes(String(x.type))||!Number.isSafeInteger(x.id))return null;const c=(x.center&&typeof x.center==="object"?x.center:x) as Record<string,unknown>;if(typeof c.lat!=="number"||Math.abs(c.lat)>90||typeof c.lon!=="number"||Math.abs(c.lon)>180)return null;const t=(x.tags&&typeof x.tags==="object"?x.tags:{}) as Record<string,unknown>;const str=(k:string)=>typeof t[k]==="string"?String(t[k]).slice(0,250):null;return {id:"osm:"+x.type+":"+x.id,source:"osm",osm_type:x.type,osm_id:x.id,name:str("name")||"Спортивная площадка",sport_type:str("sport")||(t.leisure==="fitness_station"?"fitness":"unspecified"),latitude:c.lat,longitude:c.lon,address:[str("addr:street"),str("addr:housenumber")].filter(Boolean).join(", ")||null,surface:str("surface"),lighting:t.lit==="yes"?true:t.lit==="no"?false:null,equipment:str("fitness_station")};}
Deno.serve(async(req)=>{
const id=crypto.randomUUID(),start=Date.now();
const log=(status:number,code:string,count=0)=>console.log(JSON.stringify({request_id:id,status,code,count,duration_ms:Date.now()-start}));
const err=(code:string,message:string,status:number,h:Record<string,string>={})=>{log(status,code);return send({error:{code,message,request_id:id}},status,h)};
if(req.method!=="GET")return err("METHOD_NOT_ALLOWED","Use GET",405,{"allow":"GET"});
const p=new URL(req.url).searchParams;
for(const k of p.keys())if(!["lat","lon","radius","sport"].includes(k)||p.getAll(k).length!==1)return err("INVALID_REQUEST","Unknown or repeated parameter",400);
const lat=coord(p.get("lat"),90),lon=coord(p.get("lon"),180),r=p.get("radius")||"1000",sport=p.get("sport")||"all";
if(lat===null||lon===null||!/^\d+$/.test(r)||Number(r)<100||Number(r)>3000||!SPORTS.has(sport))return err("INVALID_REQUEST","Invalid coordinates, radius or sport",400);
if(!BASE||!KEY)return err("INTERNAL_ERROR","Service not configured",500);
const externalKey=Deno.env.get("OVERPASS_CH_KEY");
if(!externalKey)return err("SERVER_CONFIG_ERROR","Overpass API key is not configured",500);

const radius=Number(r),key="v2:"+lat.toFixed(6)+":"+lon.toFixed(6)+":"+radius+":"+sport;
try{
const q=await rpc("spot_search_take_quota",{p_scope:"search-global",p_period:"minute",p_limit:60});if(!q.ok)throw Error("Quota unavailable");if(await q.json()!==true)return err("RATE_LIMITED","Too many requests",429,{"retry-after":"60"});
const c=await rpc("spot_search_cache_read",{p_key:key});if(!c.ok)throw Error("Cache unavailable");const saved=await c.json();if(saved&&typeof saved==="object"){log(200,"CACHE_HIT",saved.count||0);return send({...saved,cache:"hit"},200,{"x-request-id":id});}
const outbound=await rpc("spot_search_take_quota",{p_scope:"overpass-outbound",p_period:"minute",p_limit:5});if(!outbound.ok)throw Error("Outbound quota unavailable");if(await outbound.json()!==true)return err("RATE_LIMITED","External API is busy",429,{"retry-after":"60"});
const daily=await rpc("spot_search_take_quota",{p_scope:"overpass-outbound-daily",p_period:"day",p_limit:500});if(!daily.ok)throw Error("Daily quota unavailable");if(await daily.json()!==true)return err("RATE_LIMITED","Daily external API limit reached",429,{"retry-after":"3600"});
// Use the configured Overpass.ch provider; failures return a controlled error.
const endpoints=[
  {name:"overpass-ch",url:"https://api.overpass.ch/v1/interpreter"},
];
let data: {elements:unknown[];remark?:unknown}|null=null;
for(const ep of endpoints){
  try{
    const res=await fetch(ep.url,{
      method:"POST",
      headers:{
        "content-type":"application/x-www-form-urlencoded; charset=UTF-8",
        "accept":"application/json",
        "X-API-Key":externalKey,
        "user-agent":"SportSpot/1.0 (+https://github.com/MiraWUSE/SportSpot; educational sports map app)"
      },
      body:new URLSearchParams({data:query(lat,lon,radius,sport)}),
      signal:AbortSignal.timeout(18000)
    });
    if(!res.ok){
      console.warn(JSON.stringify({request_id:id,code:"UPSTREAM_HTTP_ERROR",provider:ep.name,status:res.status}));
      continue;
    }
    if(Number(res.headers.get("content-length")||0)>1500000){
      console.warn(JSON.stringify({request_id:id,code:"UPSTREAM_OVERSIZE",provider:ep.name}));
      continue;
    }
    const raw=await res.text();
    if(raw.length>1500000){
      console.warn(JSON.stringify({request_id:id,code:"UPSTREAM_OVERSIZE",provider:ep.name}));
      continue;
    }
    const parsed:unknown=JSON.parse(raw);
    if(!parsed || typeof parsed!=="object" || !Array.isArray((parsed as Record<string,unknown>).elements) || (parsed as Record<string,unknown>).remark){
      console.warn(JSON.stringify({request_id:id,code:"UPSTREAM_INVALID_DATA",provider:ep.name}));
      continue;
    }
    data=parsed as {elements:unknown[]};
    console.log(JSON.stringify({request_id:id,code:"UPSTREAM_OK",provider:ep.name}));
    break;
  }catch(e){
    console.warn(JSON.stringify({request_id:id,code:"UPSTREAM_FETCH_FAILED",provider:ep.name,reason:e instanceof Error?e.name:"unknown"}));
  }
}
if(!data)return err("UPSTREAM_UNAVAILABLE","Sports data providers temporarily unavailable",503,{"retry-after":"60"});
const spots=((data as {elements:unknown[]}).elements).map(convert).filter(Boolean).slice(0,100);
const payload={spots,count:spots.length,source:"Overpass API",attribution:"© OpenStreetMap contributors"};
const savedResult=await rpc("spot_search_cache_write",{p_key:key,p_payload:payload});if(!savedResult.ok)console.error(JSON.stringify({request_id:id,code:"CACHE_WRITE_FAILED",status:savedResult.status}));
log(200,"OK",spots.length);return send({...payload,cache:"miss"},200,{"x-request-id":id});
}catch(e){console.error(JSON.stringify({request_id:id,code:"REQUEST_FAILED",reason:e instanceof Error?e.name:"unknown"}));return err("UPSTREAM_UNAVAILABLE","Temporary service error",503,{"retry-after":"60"});}
});
