export type ObservationPayload={
  game:string;
  sessionId?:string;
  lines:string[];
  observedFrames?:{index:number;lines:string[]}[];
  screens?:number;
  localSummary?:any;
  previousSummary?:any;
  keys?:{google?:string;groq?:string};
};
export type WebIntel={title:string;url:string;snippet:string};

const safeJson=(text:string)=>{
  const c=String(text||'').trim().replace(/^```json\s*/i,'').replace(/^```\s*/,'').replace(/```$/,'').trim();
  try{return JSON.parse(c)}catch{}
  const a=c.indexOf('{'),b=c.lastIndexOf('}');
  if(a>=0&&b>a)try{return JSON.parse(c.slice(a,b+1))}catch{}
  return null;
};
const arr=(v:any,n=8)=>Array.isArray(v)?v.slice(0,n):[];
const s=(v:any,n=400)=>String(v??'').trim().slice(0,n);

function normalize(r:any,provider:string,model?:string){
 return {
  connected:true,provider,model,
  confidence:Math.max(0,Math.min(100,Number(r?.confidence||60))),
  headline:s(r?.headline||'Plano atualizado',180),
  accountSummary:s(r?.accountSummary||r?.summary||'Sessão analisada.',1200),
  units:arr(r?.units,20),
  resources:arr(r?.resources,12),
  modesSeen:arr(r?.modesSeen,12),
  planToday:arr(r?.planToday,6),
  upgrades:arr(r?.upgrades,10),
  avoid:arr(r?.avoid,8),
  teams:arr(r?.teams,8),
  changes:arr(r?.changes,10).map((x:any)=>s(x,300)),
  webFindings:arr(r?.webFindings,8),
  gaps:arr(r?.gaps,6).map((x:any)=>s(x,280))
 };
}

export async function fetchWebIntel(game:string):Promise<WebIntel[]>{
 const queries=game==='Marvel Strike Force'?
  ['Marvel Strike Force latest meta raid arena crucible','Marvel Strike Force latest events upgrade priority']:
  game==='Saint Seiya Awakening'?
  ['Saint Seiya Awakening latest meta cosmos teams','Saint Seiya Awakening latest events codes']:
  ['F1 Clash latest best drivers components','F1 Clash current upgrade strategy'];
 const out:WebIntel[]=[];
 for(const q of queries){
  try{
   const r=await fetch(`https://html.duckduckgo.com/html/?q=${encodeURIComponent(q)}`,{headers:{'user-agent':'Mozilla/5.0'}});
   if(!r.ok)continue;
   const h=await r.text();
   for(const m of [...h.matchAll(/<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi)].slice(0,3)){
    out.push({title:m[2].replace(/<[^>]+>/g,' ').replace(/\s+/g,' ').trim(),url:m[1],snippet:''});
   }
  }catch{}
 }
 return out.slice(0,6);
}

function prompt(p:ObservationPayload,web:WebIntel[]){
 const lines=(p.lines||[]).slice(0,2000).join('\n');
 return `Você é um coach especialista em ${p.game}. Analise a conta do usuário e entregue decisões úteis.

NÃO fale sobre OCR, cobertura ou "continue jogando".
NÃO invente personagens, níveis ou recursos.
Use apenas o que aparece nos dados.
Quando houver base, diga exatamente:
- quem evoluir agora e até onde;
- quais recursos guardar;
- quais investimentos evitar;
- equipes/formações por modo usando somente unidades detectadas;
- mudanças desde a sessão anterior;
- impacto esperado.

Para MSF: foque Gear, ISO-8, T4, estrelas/Diamonds, Raid, Arena, War e Cosmic Crucible.
Para Saint Seiya: skills, Cosmos, oitavo sentido, armadura, gemas, PvE/PvP.
Para F1 Clash: pilotos, componentes, custo/benefício, séries e eventos.

Retorne APENAS JSON:
{
 "confidence":0,
 "headline":"",
 "accountSummary":"",
 "units":[],
 "resources":[],
 "modesSeen":[],
 "planToday":[{"priority":1,"title":"","action":"","reason":"","impact":""}],
 "upgrades":[{"target":"","current":"","next":"","reason":"","priority":1}],
 "avoid":[{"title":"","reason":""}],
 "teams":[{"mode":"","name":"","units":[],"why":""}],
 "changes":[],
 "webFindings":[],
 "gaps":[]
}

DADOS ATUAIS:
${lines}

RESUMO LOCAL:
${JSON.stringify(p.localSummary||{})}

SESSÃO ANTERIOR:
${JSON.stringify(p.previousSummary||{})}

WEB:
${JSON.stringify(web||[])}`;
}

async function gemini(pr:string,key?:string){
 if(!key)return null;
 const model='gemini-2.5-flash';
 try{
  const r=await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,{
   method:'POST',
   headers:{'content-type':'application/json','x-goog-api-key':key},
   body:JSON.stringify({
    contents:[{parts:[{text:pr}]}],
    generationConfig:{temperature:0.1,responseMimeType:'application/json',maxOutputTokens:3500,thinkingConfig:{thinkingBudget:0}}
   })
  });
  if(!r.ok)return null;
  const d:any=await r.json();
  const txt=d?.candidates?.[0]?.content?.parts?.filter((x:any)=>!x.thought).map((x:any)=>x.text||'').join('')||'';
  const parsed=safeJson(txt);
  return parsed?normalize(parsed,'Gemini',model):null;
 }catch{return null}
}

async function groq(pr:string,key?:string){
 if(!key)return null;
 const model='llama-3.3-70b-versatile';
 try{
  const r=await fetch('https://api.groq.com/openai/v1/chat/completions',{
   method:'POST',
   headers:{'content-type':'application/json','authorization':`Bearer ${key}`},
   body:JSON.stringify({model,messages:[{role:'user',content:pr}],temperature:0.1,max_tokens:3500,response_format:{type:'json_object'}})
  });
  if(!r.ok)return null;
  const d:any=await r.json();
  const parsed=safeJson(d?.choices?.[0]?.message?.content||'');
  return parsed?normalize(parsed,'Groq',model):null;
 }catch{return null}
}

export async function analyzeObservation(p:ObservationPayload,web:WebIntel[]=[]){
 const pr=prompt(p,web);
 const google=(p.keys?.google||process.env.GEMINI_API_KEY||'').trim();
 const groqKey=(p.keys?.groq||process.env.GROQ_API_KEY||'').trim();
 const ai=await gemini(pr,google)||await groq(pr,groqKey)||{
  connected:false,
  provider:'IA indisponível',
  confidence:0,
  headline:'Sessão salva; IA não respondeu',
  accountSummary:`${(p.lines||[]).length} linhas estão preservadas. Tente reanalisar sem entrar no jogo novamente.`,
  units:[],resources:[],modesSeen:[],planToday:[],upgrades:[],avoid:[],teams:[],changes:[],webFindings:[],gaps:[]
 };
 return {
  ok:true,
  game:p.game,
  sessionId:p.sessionId||null,
  screens:p.screens||0,
  uniqueLines:new Set((p.lines||[]).map(String)).size,
  receivedAt:new Date().toISOString(),
  localSummary:p.localSummary||{},
  ai,
  webIntel:web
 };
}
