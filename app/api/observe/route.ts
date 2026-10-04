import {NextRequest,NextResponse} from 'next/server';
import {analyzeObservation,fetchWebIntel,ObservationPayload} from '../../../lib/coach';

export const runtime='nodejs';
export const maxDuration=30;

export async function POST(req:NextRequest){
  try{
    const body=await req.json() as ObservationPayload;
    if(!body.game||!Array.isArray(body.lines)) return NextResponse.json({ok:false,error:'game e lines são obrigatórios'},{status:400});
    const webIntel=await fetchWebIntel(body.game);
    const result=await analyzeObservation(body,webIntel);
    return NextResponse.json(result);
  }catch(e:any){
    return NextResponse.json({ok:false,error:e?.message||'Falha na análise'},{status:400});
  }
}

export async function GET(){
  return NextResponse.json({ok:true,service:'Game AI Coach Observe API',version:'1.1.0',ai:{openrouter:!!process.env.OPENROUTER_API_KEY,groq:!!process.env.GROQ_API_KEY,gemini:!!process.env.GEMINI_API_KEY}});
}
