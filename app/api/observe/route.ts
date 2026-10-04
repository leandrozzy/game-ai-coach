import {NextRequest,NextResponse} from 'next/server';
import {analyzeObservation,ObservationPayload} from '../../../lib/coach';
export async function POST(req:NextRequest){try{const body=await req.json() as ObservationPayload;if(!body.game||!Array.isArray(body.lines))return NextResponse.json({ok:false,error:'game e lines são obrigatórios'},{status:400});return NextResponse.json(analyzeObservation(body));}catch{return NextResponse.json({ok:false,error:'JSON inválido'},{status:400});}}
export async function GET(){return NextResponse.json({ok:true,service:'Game AI Coach Observe API',version:'0.2.0'});}
