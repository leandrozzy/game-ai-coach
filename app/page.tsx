"use client";

import { useState } from "react";

type GameId = "msf" | "saint-seiya" | "f1-clash";

type Result = {
  account?: {
    status: string;
    lastSync: string;
    playerName?: string;
    summary?: {
      characters?: number;
      teams?: number;
      resources?: number;
      changes?: number;
      notes?: string[];
    };
  };
  advice?: string[];
  error?: string;
};

const games: { id: GameId; name: string; subtitle: string; mode: string }[] = [
  { id: "saint-seiya", name: "Saint Seiya Awakening", subtitle: "Saints • Cosmos • Skills • Recursos", mode: "Conector read-only" },
  { id: "msf", name: "Marvel Strike Force", subtitle: "Roster • Gear • ISO-8 • T4 • Times", mode: "API/OAuth" },
  { id: "f1-clash", name: "F1 Clash", subtitle: "Pilotos • Componentes • Séries • Recursos", mode: "Conector read-only" },
];

export default function Home() {
  const [loading, setLoading] = useState<GameId | null>(null);
  const [results, setResults] = useState<Partial<Record<GameId, Result>>>({});

  async function sync(game: GameId) {
    setLoading(game);
    try {
      const res = await fetch("/api/sync", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ game }),
      });
      const data = await res.json();
      setResults((old) => ({ ...old, [game]: data }));
    } finally {
      setLoading(null);
    }
  }

  return (
    <main className="shell">
      <header className="hero">
        <div>
          <p className="eyebrow">CENTRAL COACH</p>
          <h1>Game AI Coach</h1>
          <p className="lead">Uma central única para acompanhar suas contas e dizer o que fazer agora — sem vídeo como fluxo normal.</p>
        </div>
        <div className="owner">Perfil único<br/><strong>{process.env.NEXT_PUBLIC_COACH_OWNER_NAME || "Leandro"}</strong></div>
      </header>

      <section className="today">
        <div>
          <span className="pill">OBJETIVO</span>
          <h2>O que fazer agora</h2>
          <p>Conecte cada jogo uma vez. Depois o Coach compara o estado atual com o anterior e recalcula prioridades.</p>
        </div>
      </section>

      <section className="grid">
        {games.map((game) => {
          const r = results[game.id];
          const status = r?.account?.status || "não sincronizado";
          return (
            <article className="card" key={game.id}>
              <div className="cardTop">
                <div>
                  <h3>{game.name}</h3>
                  <p>{game.subtitle}</p>
                </div>
                <span className={`status ${status}`}>{status}</span>
              </div>
              <div className="mode">{game.mode}</div>
              {r?.account && (
                <div className="metrics">
                  <span>Personagens: <b>{r.account.summary?.characters ?? "—"}</b></span>
                  <span>Times: <b>{r.account.summary?.teams ?? "—"}</b></span>
                  <span>Mudanças: <b>{r.account.summary?.changes ?? 0}</b></span>
                </div>
              )}
              {r?.account?.summary?.notes?.map((n, i) => <p className="note" key={i}>{n}</p>)}
              {r?.advice?.length ? (
                <div className="advice">
                  {r.advice.map((a, i) => <p key={i}>• {a}</p>)}
                </div>
              ) : null}
              <button onClick={() => sync(game.id)} disabled={loading === game.id}>
                {loading === game.id ? "Sincronizando..." : "Sincronizar agora"}
              </button>
            </article>
          );
        })}
      </section>

      <section className="footerCard">
        <h2>Arquitetura preparada para crescer</h2>
        <p>Novos jogos entram como conectores, sem refazer o núcleo do Coach.</p>
      </section>
    </main>
  );
}
