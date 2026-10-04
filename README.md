# Game AI Coach

Base inicial do Central Coach para **Saint Seiya Awakening**, **Marvel Strike Force** e **F1 Clash**.

## O que esta versão já faz

- Dashboard único para os 3 jogos.
- Núcleo de conectores separados por jogo.
- Endpoint único `/api/sync`.
- Normalização básica dos dados recebidos.
- Estrutura pronta para detecção de mudanças e recomendações.
- Sem vídeo/OCR como fluxo principal.
- Pronto para publicar no Vercel.

## Estrutura enxuta

Há somente duas pastas principais:

- `app` — telas + API do Next.js
- `lib` — conectores e lógica do coach

## Como subir no GitHub pelo Android

1. Extraia o ZIP no celular.
2. No seu repositório, envie primeiro os arquivos da raiz.
3. Envie a pasta `app` preservando a estrutura.
4. Envie a pasta `lib`.
5. No Vercel, importe o repositório.
6. Framework: Next.js (normalmente detectado automaticamente).
7. Build command: `npm run build`.

## Variáveis no Vercel

Copie as chaves de `.env.example` para **Vercel > Project > Settings > Environment Variables**.

### Marvel Strike Force

O projeto está preparado para usar um endpoint/API de leitura via `MSF_SYNC_URL` + token. A integração final deve usar OAuth/read-only da API do MSF, em vez de guardar senha Google/Scopely no app.

### Saint Seiya Awakening

Não há API pública documentada equivalente ao MSF. O conector foi deixado isolado e read-only para receber a fonte estruturada validada posteriormente, sem depender de vídeo.

### F1 Clash

O suporte oficial confirma que no Android o progresso pode ser vinculado ao Google Play Games. Não encontrei uma API pública de roster/garagem. Por isso o conector também está isolado para uma fonte read-only a ser validada sem automatizar gameplay.

## Segurança das contas Google

Não coloque senha Google no código nem no Vercel. Cada jogo pode estar em uma conta Google diferente. O Central Coach deve armazenar somente tokens/autorização específicos do conector quando o fornecedor oferecer OAuth ou outra autorização segura.

## Próximas etapas recomendadas

1. Finalizar OAuth real do MSF.
2. Adicionar banco persistente (Supabase/Postgres) para histórico e diferenças.
3. Construir conector Saint Seiya read-only após validar uma fonte estável da própria conta.
4. Construir conector F1 Clash read-only após validar uma fonte estável da própria conta.
5. Adicionar motor de pesquisa/meta/eventos/códigos.
6. Adicionar notificações e plano diário.
