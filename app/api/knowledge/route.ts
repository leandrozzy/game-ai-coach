import {NextRequest,NextResponse} from 'next/server';
import {fetchWebIntel} from '../../../lib/coach';
export const runtime='nodejs';
export async function GET(req:NextRequest){
 const game=req.nextUrl.searchParams.get('game')||'Marvel Strike Force';
 return NextResponse.json({ok:true,game,results:await fetchWebIntel(game)});
}
