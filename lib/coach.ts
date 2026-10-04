export type ObservationPayload={
  game:string;
  sessionId?:string;
  lines:string[];
  screens?:number;
  startedAt?:string;
  endedAt?:string;
  localSummary?:any;
  previousSummary?:any;
};

export type WebIntel={title:string;url:string;snippet:string};
export type AiCoachResult={
  provider:string;
  model?:string;
  confidence:number;
  accountSummary:string;
  actions:string[];
  avoid:string[];
  teams:string[];
  changes:string[];
  gaps:string[];
  webFindings:string[];
};

const gameQueries:Record<string,string[]>={
  'Marvel Strike Force':[
    'Marvel Strike Force current meta raid arena crucible teams latest',
    'Marvel Strike Force current events rewards codes latest',
    'Marvel Strike Force best characters gear iso t4 guide current'
  ],
  'Saint Seiya Awakening':[
    'Saint Seiya Awakening current meta teams cosmos guide latest',
    'Saint Seiya Awakening current events codes rewards latest',
    'Saint Seiya Awakening best saints skill cosmos pve pvp guide current'
  ],
  'F1 Clash':[
    'F1 Clash current best drivers components upgrades latest',
    'F1 Clash current events rewards series strategy latest',
    'F1 Clash best upgrade priority coins components drivers current'
  ]
};

function stripHtml(s:string){return s.replace(/<script[\s\S]*?<\/script>/gi,' ').replace(/<style[\s\S]*?<\/style>/gi,' ').replace(/<[^>]+>/g,' ').replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#x27;/g,"'").replace(/\s+/g,' ').trim();}

export async function fetchWebIntel(game:string):Promise<WebIntel[]> {
  const queries=gameQueries[game]||[`${game} latest guide events codes`];
  const collected:WebIntel[]=[];
  for(const query of queries){
    try{
      const url=`https://html.duckduckgo.com/html/?q=${encodeURIComponent(query)}`;
      const res=await fetch(url,{headers:{'user-agent':'Mozilla/5.0 GameAICoach/1.1'},next:{revalidate:900}});
      if(!res.ok) continue;
      const html=await res.text();
      const blocks=[...html.matchAll(/<div[^>]*class="result[^>]*"[\s\S]*?<\/div>\s*<\/div>/gi)].slice(0,6);
      for(const m of blocks){
        const b=m[0];
        const am=b.match(/<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/i);
        if(!am) continue;
        const sn=b.match(/class="result__snippet"[^>]*>([\s\S]*?)<\/a>|class="result__snippet"[^>]*>([\s\S]*?)<\/div>/i);
        const raw=am[1].replace(/&amp;/g,'&');
        let href=raw;
        try{const u=new URL(raw,'https://duckduckgo.com');href=u.searchParams.get('uddg')||u.toString();}catch{}
        const item={title:stripHtml(am[2]).slice(0,180),url:href,snippet:stripHtml((sn?.[1]||sn?.[2]||'')).slice(0,320)};
        if(item.title&&!collected.some(x=>x.url===item.url||x.title===item.title)) collected.push(item);
      }
    }catch{}
    if(collected.length>=10) break;
  }
  return collected.slice(0,10);
}

function safeJsonParse(text:string):any|null{
  const cleaned=text.trim().replace(/^```json\s*/i,'').replace(/^```\s*/,'').replace(/```$/,'').trim();
  try{return JSON.parse(cleaned)}catch{}
  const start=cleaned.indexOf('{'), end=cleaned.lastIndexOf('}');
  if(start>=0&&end>start){try{return JSON.parse(cleaned.slice(start,end+1))}catch{}}
  return null;
}

function normalizeAi(raw:any,provider:string,model?:string):AiCoachResult{
  const arr=(v:any)=>Array.isArray(v)?v.map(String).filter(Boolean).slice(0,8):[];
  return {
    provider,model,
    confidence:Math.max(0,Math.min(100,Number(raw?.confidence??60))),
    accountSummary:String(raw?.accountSummary||raw?.summary||'Sessão analisada.').slice(0,900),
    actions:arr(raw?.actions),
    avoid:arr(raw?.avoid),
    teams:arr(raw?.teams),
    changes:arr(raw?.changes),
    gaps:arr(raw?.gaps),
    webFindings:arr(raw?.webFindings)
  };
}

function buildPrompt(p:ObservationPayload,webIntel:WebIntel[]){
  const lines=(p.lines||[]).slice(0,1200);
  const web=webIntel.slice(0,8).map((x,i)=>`${i+1}. ${x.title} — ${x.snippet}`).join('\n');
  return `Você é um coach especialista no jogo ${p.game}. Analise SOMENTE os dados observados da conta e as fontes web fornecidas. Não invente valores ausentes. Se OCR estiver ambíguo, marque como lacuna. O objetivo é entregar ações úteis e específicas para melhorar a conta, evitando recomendações genéricas.\n\nREGRAS:\n- Priorize ações concretas: quem evoluir, onde gastar, onde NÃO gastar, que equipe usar, que recurso guardar, qual conteúdo priorizar.\n- Compare com a sessão anterior quando houver dados.\n- Não recomende personagem/cavaleiro/piloto como possuído se isso não estiver sustentado pelos dados.\n- Se faltarem dados para uma decisão, diga exatamente qual dado falta em gaps.\n- Use as fontes web apenas como contexto atual; não trate snippet como verdade absoluta.\n- Retorne APENAS JSON válido, sem markdown.\n\nFORMATO EXATO:\n{\n  "confidence": 0-100,\n  "accountSummary": "resumo curto e específico",\n  "actions": ["ação 1", "ação 2"],\n  "avoid": ["não faça 1"],\n  "teams": ["time/formação e uso, se houver base"],\n  "changes": ["mudanças desde a sessão anterior"],\n  "gaps": ["dados faltantes que impedem decisão"],\n  "webFindings": ["achado atual relevante da web"]\n}\n\nRESUMO LOCAL ATUAL:\n${JSON.stringify(p.localSummary||{})}\n\nRESUMO ANTERIOR:\n${JSON.stringify(p.previousSummary||{})}\n\nOCR DA SESSÃO:\n${lines.join('\n')}\n\nFONTES WEB:\n${web||'Nenhuma fonte encontrada.'}`;
}

async function callOpenRouter(prompt:string):Promise<AiCoachResult|null>{
  const key=process.env.OPENROUTER_API_KEY; if(!key) return null;
  try{
    const model=process.env.OPENROUTER_MODEL||'google/gemini-2.0-flash-001';
    const res=await fetch('https://openrouter.ai/api/v1/chat/completions',{method:'POST',headers:{'content-type':'application/json','authorization':`Bearer ${key}`},body:JSON.stringify({model,messages:[{role:'user',content:prompt}],temperature:0.2,max_tokens:1600})});
    if(!res.ok)return null;
    const data:any=await res.json();
    const parsed=safeJsonParse(data?.choices?.[0]?.message?.content||'');
    return parsed?normalizeAi(parsed,'OpenRouter',model):null;
  }catch{return null}
}

async function callGroq(prompt:string):Promise<AiCoachResult|null>{
  const key=process.env.GROQ_API_KEY; if(!key) return null;
  try{
    const model=process.env.GROQ_MODEL||'llama-3.3-70b-versatile';
    const res=await fetch('https://api.groq.com/openai/v1/chat/completions',{method:'POST',headers:{'content-type':'application/json','authorization':`Bearer ${key}`},body:JSON.stringify({model,messages:[{role:'user',content:prompt}],temperature:0.2,max_tokens:1600,response_format:{type:'json_object'}})});
    if(!res.ok)return null;
    const data:any=await res.json();
    const parsed=safeJsonParse(data?.choices?.[0]?.message?.content||'');
    return parsed?normalizeAi(parsed,'Groq',model):null;
  }catch{return null}
}

async function callGemini(prompt:string):Promise<AiCoachResult|null>{
  const key=process.env.GEMINI_API_KEY; if(!key) return null;
  try{
    const model=process.env.GEMINI_MODEL||'gemini-2.0-flash';
    const url=`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(key)}`;
    const res=await fetch(url,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({contents:[{parts:[{text:prompt}]}],generationConfig:{temperature:0.2,responseMimeType:'application/json',maxOutputTokens:1600}})});
    if(!res.ok)return null;
    const data:any=await res.json();
    const text=data?.candidates?.[0]?.content?.parts?.map((x:any)=>x.text||'').join('')||'';
    const parsed=safeJsonParse(text);
    return parsed?normalizeAi(parsed,'Gemini',model):null;
  }catch{return null}
}

function heuristicAi(p:ObservationPayload,webIntel:WebIntel[]):AiCoachResult{
  const local=p.localSummary||{};
  const recs=Array.isArray(local?.recommendations)?local.recommendations.map(String):[];
  const gaps:string[]=[];
  if(Number(local?.coveragePercent||0)<80)gaps.push(`Cobertura atual ${Number(local?.coveragePercent||0)}%; continue usando o jogo normalmente para completar áreas faltantes.`);
  return {
    provider:'Local fallback',confidence:35,
    accountSummary:`${p.game}: ${local?.status||'sessão registrada'} com ${local?.entities?.length||0} itens reconhecidos.`,
    actions:recs.slice(0,5),avoid:[],teams:[],changes:local?.newEntities>0?[`${local.newEntities} novos itens/nomes foram detectados.`]:[],gaps,
    webFindings:webIntel.slice(0,3).map(x=>x.title)
  };
}

export async function generateAiCoach(p:ObservationPayload,webIntel:WebIntel[]):Promise<AiCoachResult>{
  const prompt=buildPrompt(p,webIntel);
  return await callOpenRouter(prompt) || await callGroq(prompt) || await callGemini(prompt) || heuristicAi(p,webIntel);
}

export async function analyzeObservation(p:ObservationPayload,webIntel:WebIntel[]=[]){
  const clean=[...new Set((p.lines||[]).map(x=>String(x).trim()).filter(Boolean))];
  const ai=await generateAiCoach(p,webIntel);
  return {ok:true,game:p.game,sessionId:p.sessionId||null,screens:p.screens||0,uniqueLines:clean.length,receivedAt:new Date().toISOString(),localSummary:p.localSummary||{},ai,webIntel};
}
