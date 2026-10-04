# Game AI Coach v1.0

Central Coach Android + Vercel para Marvel Strike Force, Saint Seiya Awakening e F1 Clash.

## O que já faz
- leitura passiva da tela pelo Android MediaProjection;
- OCR local aproximadamente a cada 1,5 s, sem gravar/enviar vídeo;
- memória persistente por jogo;
- comparação de sessões e detecção de novos itens/nomes;
- parsers separados por jogo;
- cobertura da conta por áreas;
- recomendações imediatas no Android;
- consulta pública de web intelligence sem variável obrigatória;
- URL Vercel já embutida: https://game-ai-coach-indol.vercel.app;
- APK com assinatura fixa para permitir futuras atualizações geradas pelo mesmo projeto.

## GitHub/Vercel
Substitua o conteúdo do repositório atual pelos arquivos desta versão. O Vercel faz deploy da parte Next.js. O GitHub Actions gera o APK automaticamente.

## Variáveis
Nenhuma variável de ambiente é obrigatória nesta versão.

## Limite técnico importante
O Android não permite que um app leia diretamente os bancos privados de outros jogos sem API/root. O Coach usa a tela que o próprio usuário vê. Informações que nunca aparecem durante o uso não podem ser inventadas. A memória persistente evita reabrir tudo diariamente.
