# Game AI Coach 0.2

Central Coach Android + Vercel para Saint Seiya Awakening, Marvel Strike Force e F1 Clash.

## Nesta versão
- Não exige vídeo manual.
- Não exige login em sites dos jogos.
- APK Android usa MediaProjection para capturar quadros e ML Kit OCR para extrair texto localmente.
- O app guarda observações localmente e envia apenas texto extraído ao endpoint `/api/observe`.
- Sem automação de cliques ou gameplay.
- Vercel não precisa de variáveis de ambiente na v0.2.

## Publicação web
Suba a raiz deste repositório no GitHub e importe no Vercel como Next.js.

## APK Android
O workflow `.github/workflows/android.yml` compila o APK automaticamente no GitHub Actions.
Depois de fazer push, abra GitHub > Actions > Build Android APK > execução mais recente > Artifacts > Game-AI-Coach-APK.

## Configuração do APK
Ao abrir pela primeira vez, informe a URL do seu Vercel, por exemplo:
`https://seu-projeto.vercel.app`
Ela fica salva no aparelho.

## Uso
1. Escolha Saint Seiya, MSF ou F1 Clash.
2. Toque em Iniciar Coach.
3. Aceite a captura de tela do Android.
4. Jogue normalmente.
5. Volte ao Coach e toque em Finalizar sessão.

Observação: informações que nunca aparecem na tela não podem ser lidas por OCR. O Coach acumula o que vê ao longo das sessões.
