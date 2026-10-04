import {NextRequest,NextResponse} from 'next/server';
import {analyzeObservation,fetchWebIntel,ObservationPayload} from '../../../lib/coach';

export const runtime='nodejs';

export async function POST(req:NextRequest){
  try{
    const body=await req.json() as ObservationPayload;
    if(!body.game||!Array.isArray(body.lines)) return NextResponse.json({ok:false,error:'game e lines são obrigatórios'},{status:400});
    const webIntel=await fetchWebIntel(body.game);
    return NextResponse.json(analyzeObservation(body,webIntel));
  }catch(e:any){return NextResponse.json({ok:false,error:e?.message||'JSON inválido'},{status:400});}
}
export async function GET(){return NextResponse.json({ok:true,service:'Game AI Coach Observe API',version:'1.0.0',games:['Marvel Strike Force','Saint Seiya Awakening','F1 Clash']});}
