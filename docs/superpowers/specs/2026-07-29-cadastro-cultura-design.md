# Cadastro de Cultura — Design

Data: 2026-07-29

## 1. Objetivo

Hoje o catálogo de culturas (`GET /api/culturas`, coleção `coeficientes` no
Firestore) só é populado manualmente direto no Firestore. Isso adiciona uma
função para cadastrar uma cultura nova (nome + coeficiente Kc por fase de
crescimento) pela própria interface, sem precisar mexer no console do
Firebase.

## 2. Decisões já tomadas (brainstorming)

- **Tela separada**, não um formulário inline dentro do card de Cultura em
  Configurações — mesmo padrão da tela de cadastro de usuário (`/cadastro`).
- **Somente Administrador** pode cadastrar cultura — culturas são dado de
  referência compartilhado por todo mundo, mesma lógica de permissão do
  cadastro de usuários.
- **Os 4 coeficientes Kc (fases 1 a 4) são obrigatórios** no cadastro — a
  cultura já nasce completa e utilizável em qualquer fase.
- **Nome duplicado é bloqueado** com erro (409), não sobrescreve a cultura
  existente silenciosamente.

## 3. Fora de escopo

- Editar ou excluir uma cultura já cadastrada (não existe hoje nem para o
  fluxo manual via Firestore; fica para uma iteração futura se for
  necessário).
- Paginação/busca na lista de culturas (o catálogo é pequeno, a tela de
  Configurações já lista todas via `GET /api/culturas`).

## 4. Backend

### Endpoint novo

`POST /api/culturas`, `@PreAuthorize("hasRole('ADMIN')")` — mesmo padrão de
`POST /api/usuarios`.

Body (`CulturaCreateRequestDTO`): `{ nome, kc1, kc2, kc3, kc4 }` (nome
`String`, demais `Double`).

Resposta: `201 Created` com o `CulturaDTO` da cultura criada.

### `CulturaService.criar(CulturaCreateRequestDTO dto)`

1. Valida `nome` não vazio e `kc1..kc4` não nulos — se faltar algo, lança
   `IllegalArgumentException` com mensagem explicando o campo faltando.
2. Verifica duplicidade via `culturaRepository.findByName(nome)` — se já
   existir, lança `ConflictException("Já existe uma cultura com esse
   nome.")`.
3. Monta `CoeficienteCultura` (kc1..kc4 convertidos para `String`, mesmo
   formato de armazenamento já usado pelas culturas existentes no
   Firestore) e salva via `culturaRepository.save(nome, cultura)`.
4. Retorna o DTO da cultura criada.

### `CulturaRepository`

Novo método `save(String nome, CoeficienteCultura cultura)` —
`firestore.collection("coeficientes").document(nome).set(cultura)`. O nome
da cultura é o ID do documento (mesma convenção de `findByName`).

### `GlobalExceptionHandler`

Novo handler genérico `@ExceptionHandler(IllegalArgumentException.class)` →
`400 Bad Request`. Não existia nenhum handler de validação simples até agora
(só 404/409/401); esse cobre a validação de campo obrigatório faltando aqui
e fica disponível para outros endpoints no futuro.

### Testes

`CulturaServiceTest`: caminho feliz (cria e retorna DTO correto), nome
duplicado (`ConflictException`), campo obrigatório faltando
(`IllegalArgumentException`).

## 5. Frontend

### Rota nova

`/culturas/nova`, `canActivate: [authGuard, adminGuard]` — mesmo padrão de
`/cadastro`.

### `IrrigationService`

Novo método `criarCultura(cultura: Cultura): Observable<Cultura>` — `POST
${apiBaseUrl}/culturas`, ao lado do `getCulturas()` já existente no mesmo
serviço.

### `NovaCulturaComponent`

Calcado em `CadastroComponent`: campo texto (nome) + 4 campos numéricos (Kc
fase 1 a 4), validação client-side simples (nenhum campo vazio, igual
`CadastroComponent` já faz), botão salvar chama `criarCultura(...)`, trata
erro 409 com mensagem amigável ("Já existe uma cultura com esse nome"),
link "Voltar" leva para `/configuracoes`.

### Configurações

O card "Usuários" existente (visível só para admin, `canManageUsuarios()`)
ganha um segundo botão "Nova cultura" ao lado de "Novo usuário", linkando
para `/culturas/nova` — mesma condição de visibilidade (admin-only).
