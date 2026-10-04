const games=[
 {name:'Marvel Strike Force',tag:'MSF',mode:'Leitura Android + parser de roster, Gear, ISO e habilidades'},
 {name:'Saint Seiya Awakening',tag:'SSA',mode:'Leitura Android + parser de Cavaleiros, Cosmos, skills e evolução'},
 {name:'F1 Clash',tag:'F1',mode:'Leitura Android + parser de pilotos, componentes, séries e eventos'}
];
export default function Home(){return <main className="wrap">
 <header className="hero"><div><div className="eyebrow">CENTRAL COACH</div><h1>Game AI Coach</h1><p>Um núcleo único para seus jogos. O Android observa as telas enquanto você joga, mantém memória da conta, compara mudanças e consulta inteligência pública na web.</p></div><div className="badge">v1.0</div></header>
 <section className="grid">{games.map(g=><article className="card" key={g.tag}><div className="gamehead"><span className="tag">{g.tag}</span><span className="dot">● ATIVO</span></div><h2>{g.name}</h2><p>{g.mode}</p><div className="small">Sem upload de vídeo • Sem login em sites • Sem variável obrigatória</div></article>)}</section>
 <section className="card wide"><h2>Fluxo</h2><div className="steps"><span>1. Inicie o Coach</span><b>→</b><span>2. Jogue normalmente</span><b>→</b><span>3. Finalize</span><b>→</b><span>4. Veja mudanças e prioridades</span></div></section>
 <section className="card wide"><h2>Backend</h2><p>A API de observação está ativa em <code>/api/observe</code>. A inteligência pública é consultada sem exigir chave de API do usuário.</p></section>
 </main>}
