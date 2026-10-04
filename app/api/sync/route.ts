import { NextRequest, NextResponse } from "next/server";
import { syncGame } from "@/lib/connectors";
import { GameId } from "@/lib/types";
import { buildCoachAdvice } from "@/lib/coach";

const allowed: GameId[] = ["msf", "saint-seiya", "f1-clash"];

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => ({}));
  const game = body?.game as GameId;
  if (!allowed.includes(game)) {
    return NextResponse.json({ error: "Jogo inválido" }, { status: 400 });
  }
  const account = await syncGame(game);
  return NextResponse.json({ account, advice: buildCoachAdvice(game, account) });
}
