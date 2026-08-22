# SmartIrrigation — Backend

API REST em Spring Boot que centraliza o sistema de irrigação inteligente: recebe dados de sensores e status de um dispositivo ESP32, guarda tudo no Firestore, expõe esses dados para um painel web (e futuramente um app mobile) e envia comandos de volta para o dispositivo através de long-polling.

Repositórios relacionados:
- Frontend web (Angular): [`smart-irrigation-front`](https://github.com/jorgearaujor/smart-irrigation-front)
- App mobile (Android/Kotlin): [`SmartIrrigation`](https://github.com/jorgearaujor/SmartIrrigation)

## Stack

- **Java 17** + **Spring Boot 4.1** (`spring-boot-starter-webmvc`, `-security`, `-validation`, `-restclient`)
- **Firebase Admin SDK 9.4.1** — autenticação (Firebase Auth) e banco de dados (Firestore)
- **springdoc-openapi** — Swagger UI gerado automaticamente
- **Maven** (com wrapper `mvnw` / `mvnw.cmd`)

## Como rodar

1. Copie `.env.example` para `.env` e preencha:
   ```
   FIRESTORE_PROJECT_ID=<id do projeto no Firebase>
   GOOGLE_APPLICATION_CREDENTIALS=<caminho do JSON da service account>
   DEVICE_API_KEY=<chave secreta compartilhada com o ESP32>
   MYAPP_CORS_ALLOWED_ORIGINS=http://localhost:4200
   DASHBOARD_AUTH_ENABLED=false
   ```
   As três primeiras variáveis são obrigatórias — a aplicação não sobe sem elas.
2. Rode:
   ```
   ./mvnw spring-boot:run
   ```
3. A API sobe em `http://localhost:8080`. Documentação interativa (Swagger) em `http://localhost:8080/swagger-ui.html`.

Rodar os testes: `./mvnw test`.

## Arquitetura em duas frentes

O backend serve **dois tipos de cliente diferentes**, cada um com seu próprio mecanismo de autenticação:

| | Dashboard web/mobile | Dispositivo ESP32 |
|---|---|---|
| Rotas | `/api/devices/{id}/...` (exceto `/config` e `/commands/poll`), `/api/culturas`, `/api/usuarios` | `/api/webhooks/**`, `/api/devices/{id}/config`, `/api/devices/{id}/commands/poll` |
| Autenticação | Firebase ID Token (`Authorization: Bearer ...`) | Header fixo `X-Device-Key` |
| Filtro responsável | `FirebaseAuthFilter` | `DeviceKeyFilter` |
| Estado atual | **Desativado** (veja abaixo) | Sempre ativo |

### Autenticação do dashboard (Firebase Auth + allowlist no Firestore)

Login é feito com Firebase Authentication (e-mail/senha). Mas **um token Firebase válido sozinho não é suficiente**: depois de validar o token, o backend busca o documento `usuarios/{uid}` no Firestore — se não existir, a requisição é rejeitada com `403`, mesmo com login válido. Isso funciona como uma allowlist: só quem tem um documento cadastrado em `usuarios` consegue usar o dashboard. O campo `tipo` desse documento (`"Administrador"` ou qualquer outro valor) define o papel do usuário — só `"Administrador"` recebe `ROLE_ADMIN`.

**Esse mecanismo está implementado, mas atualmente desligado** via `DASHBOARD_AUTH_ENABLED=false` — todas as rotas do dashboard ficam abertas sem autenticação enquanto essa flag estiver `false`. Para reativar, basta colocar `DASHBOARD_AUTH_ENABLED=true` no `.env` e reiniciar. Uma exceção: `POST /api/usuarios`, `POST /api/culturas` e todos os endpoints de escrita de `/api/devices/{id}/**` (irrigação manual, modo, autorização, latitude, config-irrigação) têm `@PreAuthorize("hasRole('ADMIN')")` fixo no código — com a flag desligada (nenhuma autenticação é populada), esses endpoints ficam bloqueados mesmo sem exigir token nas outras rotas. Reative a flag para poder usá-los.

### Autenticação do dispositivo (chave fixa)

O ESP32 envia o header `X-Device-Key` em toda chamada aos endpoints de webhook/config/poll. O valor precisa bater exatamente com `DEVICE_API_KEY` do `.env`. Esse mecanismo **não depende de nenhuma flag** — está sempre ativo.

### Como o dispositivo recebe comandos

O ESP32 não recebe pushes diretos. Quando o dashboard chama uma ação (iniciar irrigação, trocar modo, autorizar, mudar latitude, salvar configuração agronômica), o backend enfileira um "comando" (`CommandDispatchService`) e o dispositivo o consome fazendo long-polling em `GET /api/devices/{id}/commands/poll` (aguarda até 30s por um comando, retorna `204` se não houver nada).

## Modelo de dados (Firestore)

| Coleção | Chave do documento | Conteúdo |
|---|---|---|
| `devices` | `deviceId` (ex: `esp32-01`) | Status atual (`modo`, `autorizado`, `quantidadeAgua`, `tempMin/Max`, `emExecucao`, `latitude`) + `configIrrigacao` aninhado (kc, cultura, fase de crescimento, espaçamento, vazão, nº de plantas, umidade mínima) |
| `devices/{id}/telemetry` | auto | Leituras de temperatura/umidade do ar/umidade do solo ao longo do tempo |
| `devices/{id}/irrigationHistory` | auto | Histórico de eventos de irrigação (duração, volume, umidade antes, modo) |
| `coeficientes` | nome da cultura | Catálogo de culturas cadastradas, com `kc1`..`kc4` (coeficiente de cultura por fase de crescimento) |
| `usuarios` | `uid` do Firebase Auth | Perfil do usuário do dashboard (`nome`, `email`, `tipo`, `urlImg`) — também serve de allowlist de acesso |

## Endpoints

### Dashboard (web/mobile) — `Authorization: Bearer <token>` quando `DASHBOARD_AUTH_ENABLED=true`

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/devices/{id}/status` | Snapshot atual do dispositivo |
| GET | `/api/devices/{id}/config-irrigacao` | Parâmetros agronômicos atuais |
| PUT | `/api/devices/{id}/config-irrigacao` | Atualiza parâmetros agronômicos (kc, cultura, fase, espaçamento, vazão, etc.) — só `ROLE_ADMIN` |
| POST | `/api/devices/{id}/irrigation/start` | Inicia irrigação manual (`{ quantidadeAgua }`) — só `ROLE_ADMIN` |
| POST | `/api/devices/{id}/irrigation/stop` | Para a irrigação manual — só `ROLE_ADMIN` |
| PUT | `/api/devices/{id}/mode` | Troca modo manual/automático (`{ modo }`) — só `ROLE_ADMIN` |
| PUT | `/api/devices/{id}/authorization` | Autoriza/desautoriza a irrigação automática (`{ autorizado }`) — só `ROLE_ADMIN` |
| PUT | `/api/devices/{id}/latitude` | Atualiza a latitude do sistema (`{ latitude }`) — só `ROLE_ADMIN` |
| GET | `/api/devices/{id}/telemetry` | Histórico de telemetria (`from`, `to`, `limit`) |
| GET | `/api/devices/{id}/irrigation-history` | Histórico de irrigações (`from`, `to`) |
| GET | `/api/culturas` | Catálogo de culturas cadastradas e seus coeficientes por fase |
| GET | `/api/usuarios/me` | Perfil do usuário autenticado |
| POST | `/api/usuarios` | Cria um novo usuário (só `ROLE_ADMIN`) — cria no Firebase Auth + grava em `usuarios` |

### Dispositivo (ESP32) — `X-Device-Key: <chave>`

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/devices/{id}/config` | Configuração completa que o firmware precisa (status + parâmetros agronômicos) |
| GET | `/api/devices/{id}/commands/poll` | Long-poll (30s) aguardando o próximo comando enfileirado |
| POST | `/api/webhooks/telemetry` | Dispositivo envia uma leitura de sensor |
| POST | `/api/webhooks/status` | Dispositivo reporta seu status atual |
| POST | `/api/webhooks/history` | Dispositivo reporta um evento de irrigação concluído |

### Públicas (sempre, sem autenticação)

`/swagger-ui.html`, `/v3/api-docs/**`, `/error` (mantida pública de propósito — filtros usam `response.sendError()`, que reencaminha internamente para `/error`; se essa rota exigisse auth, o redirecionamento interno seria barrado e mascararia o erro original com um 403 vazio).

## Erros

Respostas de erro seguem o formato:
```json
{ "status": 404, "error": "Not Found", "message": "...", "timestamp": "..." }
```
