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
        headers:{'user-agent':'Mozilla/5.0 GameAICoach/2.0'},next:{revalidate:1200}
      });
      if(!res.ok) continue;
      const html=await res.text();
      for(const m of [...html.matchAll(/<div[^>]*class="result[^>]*"[\s\S]*?<\/div>\s*<\/div>/gi)].slice(0,5)){
        const b=m[0],a=b.match(/<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/i);
        if(!a)continue;
        const sn=b.match(/class="result__snippet"[^>]*>([\s\S]*?)<\/a>|class="result__snippet"[^>]*>([\s\S]*?)<\/div>/i);
        let href=a[1].replace(/&amp;/g,'&');
        try{const u=new URL(href,'https://duckduckgo.com');href=u.searchParams.get('uddg')||u.toString();}catch{}
        const item={title:stripHtml(a[2]).slice(0,170),url:href,snippet:stripHtml(sn?.[1]||sn?.[2]||'').slice(0,360)};
        if(item.title&&!out.some(x=>x.title===item.title||x.url===item.url))out.push(item);
      }
    }catch{}
    if(out.length>=8)break;
  }
  return out.slice(0,8);
}

function safeJson(text:string){
  const cleaned=String(text||'').trim().replace(/^```json\s*/i,'').replace(/^```\s*/,'').replace(/```$/,'').trim();
  try{return JSON.parse(cleaned)}catch{}
  const s=cleaned.indexOf('{'),e=cleaned.lastIndexOf('}');
  if(s>=0&&e>s)try{return JSON.parse(cleaned.slice(s,e+1))}catch{}
  return null;
}

const str=(v:any,max=500)=>String(v??'').trim().slice(0,max);
const arr=(v:any,max=8)=>Array.isArray(v)?v.slice(0,max):[];

function normalize(raw:any,provider:string,model?:string):AiCoachResult{
  const units=arr(raw?.units,18).map((u:any)=>({
    name:str(u?.name,80),power:str(u?.power,40)||undefined,level:str(u?.level,30)||undefined,
    gear:str(u?.gear,30)||undefined,iso:str(u?.iso,40)||undefined,stars:str(u?.stars,40)||undefined,
    role:str(u?.role,80)||undefined,note:str(u?.note,180)||undefined,
    confidence:Math.max(0,Math.min(100,Number(u?.confidence??60)))
  })).filter((u:any)=>u.name);

  const planToday=arr(raw?.planToday,7).map((x:any,i:number)=>({
    priority:Number(x?.priority)||i+1,title:str(x?.title,120),action:str(x?.action,320),
    reason:str(x?.reason,320),impact:str(x?.impact,140)||undefined
  })).filter((x:any)=>x.title||x.action);

  const upgrades=arr(raw?.upgrades,8).map((x:any,i:number)=>({
    target:str(x?.target,100),current:str(x?.current,100)||undefined,next:str(x?.next,100)||undefined,
    reason:str(x?.reason,260),priority:Number(x?.priority)||i+1
  })).filter((x:any)=>x.target);

  const teams=arr(raw?.teams,6).map((x:any)=>({
    mode:str(x?.mode,90),name:str(x?.name,100)||undefined,
    units:arr(x?.units,8).map((y:any)=>str(y,80)).filter(Boolean),why:str(x?.why,260)
  })).filter((x:any)=>x.mode||x.units.length);

  return {
    connected:true,provider,model,
    confidence:Math.max(0,Math.min(100,Number(raw?.confidence??60))),
    headline:str(raw?.headline||'Plano atualizado',180),
    accountSummary:str(raw?.accountSummary||raw?.summary||'Sessão analisada.',1000),
    units,
    resources:arr(raw?.resources,10).map((x:any)=>({name:str(x?.name,80),value:str(x?.value,80)||undefined,advice:str(x?.advice,220)||undefined})).filter((x:any)=>x.name),
    modesSeen:arr(raw?.modesSeen,10).map((x:any)=>str(x,80)).filter(Boolean),
    planToday,
    upgrades,
    avoid:arr(raw?.avoid,7).map((x:any)=>({title:str(x?.title,130),reason:str(x?.reason,260)})).filter((x:any)=>x.title),
    teams,
    changes:arr(raw?.changes,8).map((x:any)=>str(x,280)).filter(Boolean),
    webFindings:arr(raw?.webFindings,6).map((x:any)=>({title:str(x?.title,160),finding:str(x?.finding,300)})).filter((x:any)=>x.title||x.finding),
    gaps:arr(raw?.gaps,6).map((x:any)=>str(x,260)).filter(Boolean)
  };
}

function gameInstructions(game:string){
  if(game==='Marvel Strike Force')return `
FOCO MSF:
- Detecte personagens, poder, nível, Gear, estrelas/Diamonds, ISO-8, habilidades/T4, recursos e modos vistos.
- Priorize evolução de personagens/equipes que realmente aparecem na conta.
- Se houver base suficiente, sugira equipes para Raid, Arena, War e Cosmic Crucible.
- Diga explicitamente onde NÃO gastar Gold, Training Mats, T4 ou ISO quando houver evidência.
`;
  if(game==='Saint Seiya Awakening')return `
FOCO SAINT SEIYA:
- Detecte Cavaleiros, poder, nível, estrelas, skills, Cosmos, oitavo sentido, armadura, gemas/recursos e modos vistos.
- Priorize quem evoluir e em que ordem. Quando houver base, sugira formação por PvE/PvP/treinamento.
- Diga quando guardar gemas, livros e materiais; não recomende invocar sem evidência atual.
`;
  return `
FOCO F1 CLASH:
- Detecte pilotos, níveis, componentes, carro, moedas/Bucks, séries e eventos vistos.
- Priorize upgrades com maior impacto por custo e indique o que NÃO melhorar agora.
- Sugira dupla de pilotos/configuração apenas quando sustentada pela conta observada.
- Não tente prever pista aleatória; foque eficiência de evolução e preparação.
`;
}

function buildPrompt(p:ObservationPayload,web:WebIntel[]){
  const frames=(p.observedFrames||[]).slice(0,140).map(f=>`[TELA ${f.index}]\n${(f.lines||[]).slice(0,45).join('\n')}`).join('\n\n');
  const raw=(p.lines||[]).slice(0,1800).join('\n');
  const webTxt=web.slice(0,8).map((x,i)=>`${i+1}. ${x.title}\n${x.snippet}`).join('\n\n');
  return `Você é o cérebro do Game AI Coach para ${p.game}. Sua resposta será exibida diretamente num app Android pessoal.

OBJETIVO: dizer ao usuário O QUE FAZER AGORA para melhorar a conta. Não entregue telemetria de OCR. Não diga frases genéricas como "continue jogando". Não invente.

REGRAS:
1. Só trate como possuído um personagem/cavaleiro/piloto que tenha evidência nas telas/OCR.
2. Não confunda botão, menu, nível ou texto da interface com nome de personagem.
3. Se um valor não estiver comprovado, omita o valor em vez de estimar.
4. Use a sessão anterior para mudanças apenas quando a comparação for sustentada.
5. Use web apenas para meta/eventos/contexto atual. Se o snippet não comprova algo, não afirme.
6. O plano de hoje deve conter no máximo 5 ações e cada ação precisa ter motivo.
7. Se não houver dados suficientes para decidir um upgrade, não desperdice um item de planToday com "abra tal tela": registre isso apenas em gaps.
8. Dê prioridade a economia de recursos e retorno de investimento.
${gameInstructions(p.game)}

RETORNE APENAS JSON VÁLIDO:
{
 "confidence":0-100,
 "headline":"frase forte e específica",
 "accountSummary":"resumo específico da conta observada",
 "units":[{"name":"","power":"","level":"","gear":"","iso":"","stars":"","role":"","note":"","confidence":0-100}],
 "resources":[{"name":"","value":"","advice":""}],
 "modesSeen":[""],
 "planToday":[{"priority":1,"title":"","action":"","reason":"","impact":""}],
 "upgrades":[{"target":"","current":"","next":"","reason":"","priority":1}],
 "avoid":[{"title":"","reason":""}],
 "teams":[{"mode":"","name":"","units":[""],"why":""}],
 "changes":[""],
 "webFindings":[{"title":"","finding":""}],
 "gaps":["somente lacunas que realmente impedem uma decisão importante"]
}

RESUMO LOCAL:
${JSON.stringify(p.localSummary||{})}

SESSÃO ANTERIOR:
${JSON.stringify(p.previousSummary||{})}

TELAS COM CONTEXTO:
${frames||'(não disponível)'}

OCR CONSOLIDADO:
${raw}

WEB:
${webTxt||'(sem resultados)'}`;
}

async function gemini(prompt:string){
  const key=process.env.GEMINI_API_KEY;if(!key)return null;
  const model=process.env.GEMINI_MODEL||'gemini-2.5-flash';
  try{
    const res=await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,{
      method:'POST',headers:{'content-type':'application/json','x-goog-api-key':key},
      body:JSON.stringify({contents:[{role:'user',parts:[{text:prompt}]}],generationConfig:{temperature:0.1,responseMimeType:'application/json',maxOutputTokens:3000,...(/^gemini-2\.5-flash/.test(model)?{thinkingConfig:{thinkingBudget:0}}:{})}})
    });
    if(!res.ok)return null;
    const d:any=await res.json();
    const txt=d?.candidates?.[0]?.content?.parts?.filter((x:any)=>!x.thought).map((x:any)=>x.text||'').join('')||'';
    const parsed=safeJson(txt);return parsed?normalize(parsed,'Gemini',model):null;
  }catch{return null}
}

async function groq(prompt:string){
  const key=process.env.GROQ_API_KEY;if(!key)return null;
  const model=process.env.GROQ_TEXT_MODEL||process.env.GROQ_MODEL||'llama-3.3-70b-versatile';
  try{
    const res=await fetch('https://api.groq.com/openai/v1/chat/completions',{
      method:'POST',headers:{'content-type':'application/json','authorization':`Bearer ${key}`},
      body:JSON.stringify({model,messages:[{role:'user',content:prompt}],temperature:0.1,max_tokens:3000,response_format:{type:'json_object'}})
    });
    if(!res.ok)return null;
    const d:any=await res.json();const parsed=safeJson(d?.choices?.[0]?.message?.content||'');
    return parsed?normalize(parsed,'Groq',model):null;
  }catch{return null}
}

function noAi(p:ObservationPayload):AiCoachResult{
  const local=p.localSummary||{};
  return {
    connected:false,provider:'IA NÃO CONECTADA',confidence:0,
    headline:'Conecte Gemini ou Groq para liberar o Coach inteligente',
    accountSummary:`A leitura Android está funcionando (${local?.coveragePercent??0}% de cobertura local), mas este projeto Vercel não encontrou GEMINI_API_KEY nem GROQ_API_KEY. Não vou fingir recomendações sem IA.`,
    units:[],resources:[],modesSeen:[],planToday:[],upgrades:[],avoid:[],teams:[],changes:[],webFindings:[],
    gaps:['Copie para este projeto Vercel a mesma GEMINI_API_KEY ou GROQ_API_KEY já usada no seu OSM.']
  };
}

export async function analyzeObservation(p:ObservationPayload,web:WebIntel[]=[]){
  const prompt=buildPrompt(p,web);
  const ai=await gemini(prompt)||await groq(prompt)||noAi(p);
  return {
    ok:true,game:p.game,sessionId:p.sessionId||null,screens:p.screens||0,
    uniqueLines:[...new Set((p.lines||[]).map(String))].length,
    receivedAt:new Date().toISOString(),
    localSummary:p.localSummary||{},ai,webIntel:web
  };
}
