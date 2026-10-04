import { GameId, NormalizedAccount } from "./types";

type ConnectorConfig = {
  url?: string;
  token?: string;
};

const CONFIG: Record<GameId, ConnectorConfig> = {
  msf: { url: process.env.MSF_SYNC_URL, token: process.env.MSF_BEARER_TOKEN },
  "saint-seiya": { url: process.env.SSA_SYNC_URL, token: process.env.SSA_BEARER_TOKEN },
  "f1-clash": { url: process.env.F1C_SYNC_URL, token: process.env.F1C_BEARER_TOKEN },
};

function normalize(game: GameId, payload: any): NormalizedAccount {
  const list = payload?.characters ?? payload?.roster ?? payload?.saints ?? payload?.drivers ?? [];
  const teams = payload?.teams ?? payload?.squads ?? payload?.lineups ?? [];
  const resources = payload?.resources ?? payload?.inventory ?? [];

  return {
    game,
    playerName: payload?.playerName ?? payload?.name ?? payload?.profile?.name,
    accountId: payload?.accountId ?? payload?.playerId ?? payload?.profile?.id,
    lastSync: new Date().toISOString(),
    status: "connected",
    summary: {
      characters: Array.isArray(list) ? list.length : undefined,
      teams: Array.isArray(teams) ? teams.length : undefined,
      resources: Array.isArray(resources) ? resources.length : undefined,
      changes: 0,
      notes: ["Leitura concluída pelo conector configurado."],
    },
    rawPreview: payload,
  };
}

export async function syncGame(game: GameId): Promise<NormalizedAccount> {
  const cfg = CONFIG[game];
  if (!cfg.url) {
    return {
      game,
      lastSync: new Date().toISOString(),
      status: "needs-config",
      summary: {
        changes: 0,
        notes: [
          game === "msf"
            ? "Base pronta para conectar à API/OAuth do Marvel Strike Force."
            : "Base pronta para receber o conector read-only do jogo sem depender de vídeo.",
        ],
      },
    };
  }

  try {
    const res = await fetch(cfg.url, {
      method: "GET",
      headers: cfg.token ? { Authorization: `Bearer ${cfg.token}` } : undefined,
      cache: "no-store",
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    return normalize(game, data);
  } catch (error) {
    return {
      game,
      lastSync: new Date().toISOString(),
      status: "error",
      summary: {
        changes: 0,
        notes: [error instanceof Error ? error.message : "Falha desconhecida no conector."],
      },
    };
  }
}
