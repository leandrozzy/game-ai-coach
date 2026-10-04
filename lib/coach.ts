export type ObservationPayload={game:string;sessionId?:string;lines:string[];screens?:number;startedAt?:string;endedAt?:string};
export function analyzeObservation(p:ObservationPayload){
 const clean=[...new Set((p.lines||[]).map(x=>x.trim()).filter(Boolean))];
 const joined=clean.join(' ').toLowerCase();
 const hints:string[]=[];
 if(/cosmo|cosmos|cavaleir|saint|skill|habilidade/.test(joined)) hints.push('Dados de evolução de cavaleiros/skills detectados nesta sessão.');
 if(/gear|iso|power|poder|star|estrela|ability/.test(joined)) hints.push('Dados de progressão de personagens detectados nesta sessão.');
 if(/piloto|driver|componente|component|series|série|evento|event/.test(joined)) hints.push('Dados de pilotos/componentes/eventos detectados nesta sessão.');
 if(/gems|gemas|gold|ouro|crystal|cristal|moeda|coins/.test(joined)) hints.push('Recursos/moedas apareceram nas telas observadas.');
 if(!hints.length) hints.push('Sessão recebida. Continue jogando normalmente para o Coach aumentar a cobertura da conta.');
 return {ok:true,game:p.game,uniqueLines:clean.length,screens:p.screens||0,hints,receivedAt:new Date().toISOString()};
}
