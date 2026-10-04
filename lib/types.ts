export type GameId = "msf" | "saint-seiya" | "f1-clash";

export type SyncStatus = "connected" | "needs-config" | "error";

export type NormalizedAccount = {
  game: GameId;
  playerName?: string;
  accountId?: string;
  lastSync: string;
  status: SyncStatus;
  summary: {
    characters?: number;
    teams?: number;
    resources?: number;
    changes?: number;
    notes?: string[];
  };
  rawPreview?: unknown;
};
