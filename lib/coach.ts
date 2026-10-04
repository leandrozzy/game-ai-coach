export type ObservationPayload={
  game:string;
  sessionId?:string;
  lines:string[];
  observedFrames?:{index:number;lines:string[]}[];
  screens?:number;
  startedAt?:string;
  endedAt?:string;
  localSummary?:any;
  previousSummary?:any;
  keys?:{google?:string;groq?:string};
};

export type WebIntel={title:string;url:string;snippet:string};

type Unit={
  name:string; power?:string; level?:string; gear?:string; iso?:string;
  stars?:string; role?:string; note?:string; confidence?:number;
};
type PlanItem={priority:number;title:string;action:string;reason:string;impact?:string};
type Upgrade={target:string;current?:string;next?:string;reason:string;priority:number};
type Team={mode:string;name?:string;units:string[];why:string};

export type AiCoachResult={
  connected:boolean;
  provider:string;
  model?:string;
  confidence:number;
  headline:string;
  accountSummary:string;
  units:Unit[];
  resources:{name:string;value?:string;advice?:string}[];
  modesSeen:string[];
  planToday:PlanItem[];
  upgrades:Upgrade[];
  avoid:{title:string;reason:string}[];
  teams:Team[];
  changes:string[];
  webFindings:{title:string;finding:string}[];
  gaps:string[];
};

const queries:Record<string,string[]>={
  'Marvel Strike Force':[
    'Marvel Strike Force current meta raid arena cosmic crucible teams latest',
    'Marvel Strike Force latest events character release upgrade priority',
    'Marvel Strike Force current ISO-8 T4 gear guide'
  ],
  'Saint Seiya Awakening':[
    'Saint Seiya Awakening current meta pvp pve teams cosmos latest',
    'Saint Seiya Awakening latest events summons codes guide',
    'Saint Seiya Awakening best saints cosmos skills current'
  ],
  'F1 Clash':[
    'F1 Clash current best drivers components upgrade priority',
    'F1 Clash latest series events strategy drivers components',
    'F1 Clash current meta setup coins upgrades'
  ]
};

function stripHtml(s:string){
  return s.replace(/<script[\s\S]*?<\/script>/gi,' ')
    .replace(/<style[\s\S]*?<\/style>/gi,' ')
    .replace(/<[^>]+>/g,' ')
    .replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#x27;/g,"'")
    .replace(/\s+/g,' ').trim();
}

export async function fetchWebIntel(game:string):Promise<WebIntel[]>{
  const out:WebIntel[]=[];
  for(const q of (queries[game]||[`${game} latest guide meta events`])){
    try{
      const res=await fetch(`https://html.duckduckgo.com/html/?q=${encodeURIComponent(q)}`,{
        headers:{'user-agent':'Mozilla/5.0 GameAICoach/2.2'},next:{revalidate:1200}
      });
      if(!res.ok)continue;
      const html=await res.text();
      for(const m of [...html.matchAll(/<div[^>]*class="result[^>]*"[\s\S]*?<\/div>\s*<\/div>/gi)].slice(0,4)){
        const b=m[0],a=b.match(/<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/i);
        if(!a)continue;
        const sn=b.match(/class="result__snippet"[^>]*>([\s\S]*?)<\/a>|class="result__snippet"[^>]*>([\s\S]*?)<\/div>/i);
        let href=a[1].replace(/&amp;/g,'&');
        try{const u=new URL(href,'https://duckduckgo.com');href=u.searchParams.get('uddg')||u.toString()}catch{}
        const item={title:stripHtml(a[2]).slice(0,170),url:href,snippet:stripHtml(sn?.[1]||sn?.[2]||'').slice(0,320)};
        if(item.title&&!out.some(x=>x.url===item.url))out.push(item);
      }
    }catch{}
    if(out.length>=6)break;
  }
  return out.slice(0,6);
}

function safeJson(text:string){
  const cleaned=String(text||'').trim().replace(/^```json\s*/i,'').replace(/^```\s*/,'').replace(/```$/,'').trim();
  try{return JSON.parse(cleaned)}catch{}
  const a=cleaned.indexOf('{'),b=cleaned.lastIndexOf('}');
  if(a>=0&&b>a)try{return JSON.parse(cleaned.slice(a,b+1))}catch{}
  return null;
}
const str=(v:any,max=500)=>String(v??'').trim().slice(0,max);
const arr=(v:any,max=8)=>Array.isArray(v)?v.slice(0,max):[];

function normalize(raw:any,provider:string,model?:string):AiCoachResult{
  const units=arr(raw?.units,20).map((u:any)=>({
    name:str(u?.name,80),
    power:str(u?.power,40)||undefined,
    level:str(u?.level,30)||undefined,
    gear:str(u?.gear,30)||undefined,
    iso:str(u?.iso,40)||undefined,
    stars:str(u?.stars,40)||undefined,
    role:str(u?.role,80)||undefined,
    note:str(u?.note,180)||undefined,
    confidence:Math.max(0,Math.min(100,Number(u?.confidence??60)))
  })).filter((u:any)=>u.name);

  return {
    connected:true,
    provider,
    model,
    confidence:Math.max(0,Math.min(100,Number(raw?.confidence??60))),
    headline:str(raw?.headline||'Plano atualizado',180),
    accountSummary:str(raw?.accountSummary||raw?.summary||'Sessão analisada.',1200),
    units,
    resources:arr(raw?.resources,12).map((x:any)=>({name:str(x?.name,90),value:str(x?.value,90)||undefined,advice:str(x?.advice,260)||undefined})).filter((x:any)=>x.name),
    modesSeen:arr(raw?.modesSeen,12).map((x:any)=>str(x,80)).filter(Boolean),
    planToday:arr(raw?.planToday,6).map((x:any,i:number)=>({
      priority:Number(x?.priority)||i+1,
      title:str(x?.title,120),
      action:str(x?.action,360),
      reason:str(x?.reason,360),
      impact:str(x?.impact,160)||undefined
    })).filter((x:any)=>x.title||x.action),
    upgrades:arr(raw?.upgrades,10).map((x:any,i:number)=>({
      target:str(x?.target,100),
      current:str(x?.current,100)||undefined,
      next:str(x?.next,100)||undefined,
      reason:str(x?.reason,320),
      priority:Number(x?.priority)||i+1
    })).filter((x:any)=>x.target),
    avoid:arr(raw?.avoid,8).map((x:any)=>({title:str(x?.title,140),reason:str(x?.reason,300)})).filter((x:any)=>x.title),
    teams:arr(raw?.teams,8).map((x:any)=>({
      mode:str(x?.mode,90),
      name:str(x?.name,100)||undefined,
      units:arr(x?.units,8).map((y:any)=>str(y,80)).filter(Boolean),
      why:str(x?.why,300)
    })).filter((x:any)=>x.mode||x.units.length),
    changes:arr(raw?.changes,10).map((x:any)=>str(x,300)).filter(Boolean),
    webFindings:arr(raw?.webFindings,8).map((x:any)=>({title:str(x?.title,160),finding:str(x?.finding,320)})).filter((x:any)=>x.title||x.finding),
    gaps:arr(raw?.gaps,6).map((x:any)=>str(x,280)).filter(Boolean)
  };
}

function gameInstructions(game:string){
  if(game==='Marvel Strike Force')return `
FOCO MSF:
- Separe personagens de textos de interface.
- Identifique poder, nível, Gear, estrelas/Diamonds, ISO-8, habilidades/T4, recursos e modos quando houver evidência.
- Priorize evolução real: quem subir agora, até onde, por qual motivo e qual recurso preservar.
- Quando a conta permitir, monte times específicos para Raid, Arena, War e Cosmic Crucible usando somente personagens detectados.
- Diga claramente quais personagens/recursos NÃO merecem investimento agora.
`;
  if(game==='Saint Seiya Awakening')return `
FOCO SAINT SEIYA:
- Separe cavaleiros de textos de interface.
- Identifique nível, estrelas, skills, Cosmos, oitavo sentido, armadura, gemas e recursos.
- Diga quem evoluir, quais skills priorizar, quais Cosmos procurar/equipar e quando guardar gemas/livros.
- Monte formações PvE/PvP apenas com cavaleiros detectados.
`;
  return `
FOCO F1 CLASH:
- Identifique pilotos, níveis, componentes, carro, moedas/Bucks, séries e eventos.
- Priorize upgrades de melhor retorno por custo.
- Diga o que não melhorar agora.
- Sugira dupla de pilotos e foco de componentes quando houver dados suficientes.
`;
}

function buildPrompt(p:ObservationPayload,web:WebIntel[]){
  const frames=(p.observedFrames||[]).slice(0,120).map(f=>`[TELA ${f.index}]\n${(f.lines||[]).slice(0,45).join('\n')}`).join('\n\n');
  const raw=(p.lines||[]).slice(0,2000).join('\n');
  const webTxt=web.map((x,i)=>`${i+1}. ${x.title}\n${x.snippet}`).join('\n\n');
  return `Você é o cérebro do Game AI Coach para ${p.game}.

OBJETIVO: entregar um plano realmente útil para melhorar a conta. Não fale sobre OCR, cobertura ou "continue jogando". Não invente dados.

REGRAS:
1. Só trate como possuído algo que tenha evidência no OCR.
2. Não confunda menus, botões, moedas, níveis ou textos genéricos com nomes.
3. Se um valor não estiver comprovado, omita.
4. Use sessão anterior apenas para mudanças sustentadas.
5. Use web para meta/eventos/contexto atual, sem inventar detalhes ausentes.
6. O plano de hoje deve ter 3 a 5 ações concretas quando houver base.
7. Prefira decisões: evoluir X, parar Y, guardar Z, usar time A/B/C.
8. Se faltar informação para uma decisão, registre em gaps; não transforme isso em conselho genérico.
${gameInstructions(p.game)}

RETORNE APENAS JSON:
{
 "confidence":0,
 "headline":"",
 "accountSummary":"",
 "units":[{"name":"","power":"","level":"","gear":"","iso":"","stars":"","role":"","note":"","confidence":0}],
 "resources":[{"name":"","value":"","advice":""}],
 "modesSeen":[""],
 "planToday":[{"priority":1,"title":"","action":"","reason":"","impact":""}],
 "upgrades":[{"target":"","current":"","next":"","reason":"","priority":1}],
 "avoid":[{"title":"","reason":""}],
 "teams":[{"mode":"","name":"","units":[""],"why":""}],
 "changes":[""],
 "webFindings":[{"title":"","finding":""}],
 "gaps":[""]
}

RESUMO LOCAL:
${JSON.stringify(p.localSummary||{})}

SESSÃO ANTERIOR:
${JSON.stringify(p.previousSummary||{})}

TELAS:
${frames||'(sem agrupamento de telas nesta sessão)'}

OCR:
${raw}

WEB:
${webTxt||'(sem resultados públicos úteis)'}`;
}

async function gemini(prompt:string,keyOverride?:string){
  const key=(keyOverride||process.env.GEMINI_API_KEY||'').trim();
  if(!key)return null;
  const model=process.env.GEMINI_MODEL||'gemini-2.5-flash';
  try{
    const res=await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,{
      method:'POST',
      headers:{'content-type':'application/json','x-goog-api-key':key},
      body:JSON.stringify({
        contents:[{role:'user',parts:[{text:prompt}]}],
        generationConfig:{
          temperature:0.1,
          responseMimeType:'application/json',
          maxOutputTokens:3500,
          ...(/^gemini-2\.5-flash/.test(model)?{thinkingConfig:{thinkingBudget:0}}:{})
        }
      })
    });
    if(!res.ok)return null;
    const d:any=await res.json();
    const txt=d?.candidates?.[0]?.content?.parts?.filter((x:any)=>!x.thought).map((x:any)=>x.text||'').join('')||'';
    const parsed=safeJson(txt);
    return parsed?normalize(parsed,'Gemini',model):null;
  }catch{return null}
}

async function groq(prompt:string,keyOverride?:string){
  const key=(keyOverride||process.env.GROQ_API_KEY||'').trim();
  if(!key)return null;
  const model=process.env.GROQ_TEXT_MODEL||process.env.GROQ_MODEL||'llama-3.3-70b-versatile';
  try{
    const res=await fetch('https://api.groq.com/openai/v1/chat/completions',{
      method:'POST',
      headers:{'content-type':'application/json','authorization':`Bearer ${key}`},
      body:JSON.stringify({
        model,
        messages:[{role:'user',content:prompt}],
        temperature:0.1,
        max_tokens:3500,
        response_format:{type:'json_object'}
      })
    });
    if(!res.ok)return null;
    const d:any=await res.json();
    const parsed=safeJson(d?.choices?.[0]?.message?.content||'');
    return parsed?normalize(parsed,'Groq',model):null;
  }catch{return null}
}

function noAi(p:ObservationPayload):AiCoachResult{
  return {
    connected:false,
    provider:'IA NÃO CONECTADA',
    confidence:0,
    headline:'APIs importadas no app, mas nenhuma IA respondeu',
    accountSummary:`A sessão foi recebida (${(p.lines||[]).length} linhas), porém Gemini e Groq não responderam. Tente reanalisar; a sessão permanece salva no Android.`,
    units:[],resources:[],modesSeen:[],planToday:[],upgrades:[],avoid:[],teams:[],changes:[],webFindings:[],
    gaps:['Verificar disponibilidade/quota das APIs importadas.']
  };
}

export async function analyzeObservation(p:ObservationPayload,web:WebIntel[]=[]){
  const prompt=buildPrompt(p,web);
  const ai=
    await gemini(prompt,p.keys?.google) ||
    await groq(prompt,p.keys?.groq) ||
    await gemini(prompt) ||
    await groq(prompt) ||
    noAi(p);

  return {
    ok:true,
    game:p.game,
    sessionId:p.sessionId||null,
    screens:p.screens||0,
    uniqueLines:[...new Set((p.lines||[]).map(String))].length,
    receivedAt:new Date().toISOString(),
    localSummary:p.localSummary||{},
    ai,
    webIntel:web
  };
}
