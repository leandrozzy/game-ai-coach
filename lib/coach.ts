export type ObservationPayload={
  game:string;
  sessionId?:string;
  lines:string[];
  screens?:number;
  startedAt?:string;
  endedAt?:string;
  localSummary?:any;
};

export type WebIntel={title:string;url:string;snippet:string};

const gameQueries:Record<string,string[]>={
  'Marvel Strike Force':[
    'Marvel Strike Force latest meta events codes guides 2026',
    'site:youtube.com Marvel Strike Force guide roster raid arena 2026',
    'Marvel Strike Force redeem codes events rewards 2026'
  ],
  'Saint Seiya Awakening':[
    'Saint Seiya Awakening Knights of the Zodiac latest events codes meta guides 2026',
    'site:youtube.com Saint Seiya Awakening guide cosmos team 2026',
    'Saint Seiya Awakening gift codes rewards 2026'
  ],
  'F1 Clash':[
    'F1 Clash latest events best drivers components guide 2026',
    'site:youtube.com F1 Clash best drivers components strategy 2026',
    'F1 Clash events rewards series guide 2026'
  ]
};

function stripHtml(s:string){return s.replace(/<script[\s\S]*?<\/script>/gi,' ').replace(/<style[\s\S]*?<\/style>/gi,' ').replace(/<[^>]+>/g,' ').replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#x27;/g,"'").replace(/\s+/g,' ').trim();}

export async function fetchWebIntel(game:string):Promise<WebIntel[]> {
  const queries=gameQueries[game]||[`${game} latest guide events codes`];
  const collected:WebIntel[]=[];
  for(const query of queries){
    const q=encodeURIComponent(query);
    const url=`https://html.duckduckgo.com/html/?q=${q}`;
    try{
      const res=await fetch(url,{headers:{'user-agent':'Mozilla/5.0 GameAICoach/1.0'},next:{revalidate:1800}});
      if(!res.ok) continue;
      const html=await res.text();
      const blocks=[...html.matchAll(/<div[^>]*class="result[^>]*"[\s\S]*?<\/div>\s*<\/div>/gi)].slice(0,5);
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
    if(collected.length>=8)break;
  }
  return collected.length?collected.slice(0,8):fallbackIntel(game);
}

function fallbackIntel(game:string):WebIntel[]{
  if(game==='Marvel Strike Force')return [
    {title:'Marvel Strike Force — site oficial',url:'https://marvelstrikeforce.com/',snippet:'Notícias, eventos e informações oficiais do jogo.'},
    {title:'Marvel Strike Force — comunidade Reddit',url:'https://www.reddit.com/r/MarvelStrikeForce/',snippet:'Discussões da comunidade sobre equipes, eventos e evolução.'}
  ];
  if(game==='Saint Seiya Awakening')return [
    {title:'GTarcade — Saint Seiya Awakening',url:'https://forum.gtarcade.com/',snippet:'Fórum e publicações da GTarcade sobre eventos e guias.'},
    {title:'Saint Seiya Awakening — comunidade',url:'https://www.reddit.com/search/?q=Saint%20Seiya%20Awakening',snippet:'Discussões recentes da comunidade.'}
  ];
  return [
    {title:'F1 Clash — suporte e notícias',url:'https://hutch.helpshift.com/hc/en/10-f1-clash/',snippet:'Informações oficiais do F1 Clash.'},
    {title:'F1 Clash — comunidade Reddit',url:'https://www.reddit.com/r/F1Clash/',snippet:'Estratégias e discussões atuais da comunidade.'}
  ];
}

export function analyzeObservation(p:ObservationPayload,webIntel:WebIntel[]=[]){
  const clean=[...new Set((p.lines||[]).map(x=>String(x).trim()).filter(Boolean))];
  const local=p.localSummary||{};
  const recs=Array.isArray(local.recommendations)?local.recommendations:[];
  const coach:string[]=[];
  if(local.coveragePercent!=null&&local.coveragePercent<50) coach.push('Continue jogando normalmente: ainda faltam algumas áreas da conta para o Coach mapear com confiança.');
  if(local.newEntities>0) coach.push(`${local.newEntities} itens ou nomes novos foram detectados desde a sessão anterior.`);
  recs.slice(0,5).forEach((x:any)=>coach.push(String(x)));
  if(webIntel.length) coach.push(`Inteligência web atualizada com ${webIntel.length} fontes públicas.`);
  return {
    ok:true,game:p.game,sessionId:p.sessionId||null,screens:p.screens||0,
    uniqueLines:clean.length,receivedAt:new Date().toISOString(),
    localSummary:local,coach:coach.slice(0,8),webIntel
  };
}
