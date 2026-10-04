import { GameId, NormalizedAccount } from "./types";

export function buildCoachAdvice(game: GameId, account: NormalizedAccount) {
  if (account.status !== "connected") {
    return ["Conecte a fonte de dados deste jogo para liberar recomendações automáticas."];
  }

  const base: Record<GameId, string[]> = {
    msf: [
      "Priorize personagens que melhoram Raid/Arena antes de gastar T4 dispersos.",
      "Compare evolução de Gear, estrelas, Diamonds e ISO desde a última sincronização.",
    ],
    "saint-seiya": [
      "Cruze Saints, skills, Cosmos e recursos com o meta atual antes de gastar livros ou gemas.",
      "Recalcule automaticamente formações e prioridades após cada mudança detectada.",
    ],
    "f1-clash": [
      "Compare pilotos, componentes e recursos para indicar o próximo upgrade com maior retorno.",
      "Use a série atual e o inventário para sugerir a dupla e setup mais eficiente.",
    ],
  };
  return base[game];
}
