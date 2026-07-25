# SmartIrrigation Backend API — Design

Data: 2026-07-25

## 1. Objetivo

Construir a API REST (Java 17 + Spring Boot) que serve de ponte entre o novo
front-end Angular 20 (substituindo o app mobile), o Firebase (Firestore +
Auth) e o hardware ESP32, substituindo o backend Quarkus atual. O firmware do
ESP32 (`irrigation_controller_esp32.ino`) não é modificado em sua lógica de
negócio; apenas recebe uma linha nova (`http.addHeader("X-Device-Key", ...)`)
para autenticar suas chamadas.

## 2. Contexto e decisões já tomadas

- **Substituição do Quarkus**: o Spring Boot assume integralmente o papel do
  backend atual. O contrato HTTP hoje usado pelo firmware (paths, payloads,
  tipos de comando) é preservado, para não exigir reflash do dispositivo além
  do header de API key.
- **Single-device por enquanto**: existe um único ESP32 (`esp32-01`). O
  `deviceId` já está presente em toda rota/payload (preparação natural para
  multi-device no futuro), mas não há UI de troca de dispositivo nesta fase.
- **Roles reais**: o campo `usuarios/{uid}.tipo` (hoje só informativo no app
  mobile) passa a ser verificado de fato no backend. Valor `"Administrador"`
  mapeia para `ROLE_ADMIN`; qualquer outro valor mapeia para `ROLE_USER`.
- **Auth do device**: o firmware não envia nenhum header de autenticação
  hoje. Decidido adicionar `X-Device-Key: <chave fixa por device>` como única
  mudança no `.ino` (uma linha por chamada HTTP), validada contra
  `DEVICE_API_KEY` do `.env`.
- **Segurança de credenciais**: `.env` e
  `smart-irrigation-ifmaker-firebase.json` foram removidos do stage do
  primeiro commit e adicionados ao `.gitignore` (nunca chegaram a ser
  commitados, então não há necessidade de reescrever histórico). Como o
  conteúdo da chave privada foi exposto nesta conversa, recomenda-se rotacionar
  a service account no console do Firebase antes de ir para produção.

## 3. Arquitetura

```
Angular Web  ──(HTTPS + Firebase ID Token)──▶  Spring Boot API  ──(Admin SDK)──▶  Firestore
ESP32        ──(HTTPS + X-Device-Key)───────▶  Spring Boot API
Spring Boot API ──(long-poll suspenso via DeferredResult)──▶  ESP32 (comandos)
```

Um único backend Spring Boot serve dois clientes com contratos e mecanismos
de autenticação distintos:

- **Device (ESP32)**: contrato fixo herdado do Quarkus, autenticado por
  `X-Device-Key`, sem Firebase Auth (o device não tem identidade de usuário).
- **Web (Angular)**: autenticado via Firebase Auth (ID Token no header
  `Authorization: Bearer <token>`), com autorização por role.

## 4. Estrutura de pacotes

Segue o padrão em camadas do MovelControl (`Controller/Service/Repository/
Model/DTO/Exceptions/Config`), adaptado para Firestore no lugar de JPA:

```
com.smartirrigation.smartirrigation
├── config/
│   ├── FirebaseConfig.java       → inicializa FirebaseApp/Firestore a partir do .env
│   ├── SecurityConfig.java       → SecurityFilterChain, regras por path
│   ├── WebConfig.java            → CORS (allowed-origins do Angular)
│   └── OpenApiConfig.java        → Swagger/OpenAPI
├── security/
│   ├── FirebaseAuthFilter.java   → valida ID Token, popula Authentication + roles
│   ├── DeviceKeyFilter.java      → valida X-Device-Key nas rotas /api/webhooks/**, /api/devices/**
│   └── CurrentUser.java          → helper para extrair uid/role da request atual
├── controller/
│   ├── device/
│   │   ├── DeviceWebhookController.java   → POST telemetry/status/history
│   │   ├── DeviceConfigController.java    → GET config inicial
│   │   └── CommandPollController.java     → GET commands/poll (long-poll)
│   └── web/
│       ├── StatusController.java
│       ├── TelemetryController.java
│       ├── IrrigationHistoryController.java
│       ├── ConfigIrrigacaoController.java
│       ├── CommandController.java         → PUT modo/autorizacao/latitude, POST irrigar-manual
│       ├── CulturaController.java
│       └── UsuarioController.java         → perfil (me)
├── service/
│   ├── DeviceService.java
│   ├── TelemetryService.java
│   ├── IrrigationHistoryService.java
│   ├── ConfigIrrigacaoService.java
│   ├── CommandDispatchService.java        → fila em memória por deviceId
│   ├── CulturaService.java
│   └── UsuarioService.java
├── repository/
│   ├── DeviceRepository.java
│   ├── TelemetryRepository.java
│   ├── IrrigationHistoryRepository.java
│   ├── UsuarioRepository.java
│   └── CulturaRepository.java
├── model/
│   ├── Device.java
│   ├── ConfigIrrigacao.java
│   ├── Telemetry.java
│   ├── IrrigationHistoryRecord.java
│   ├── Usuario.java
│   └── CoeficienteCultura.java
├── dto/
│   ├── *RequestDTO.java / *ResponseDTO.java
│   └── DataMapper.java            → wrapper de ModelMapper, igual ao MovelControl
└── exception/
    ├── NotFoundException.java
    ├── ConflictException.java
    ├── UnauthorizedException.java
    ├── ErrorResponseDTO.java
    └── GlobalExceptionHandler.java
```

## 5. Schema Firestore

```
devices/{deviceId}                          (doc único; "esp32-01" nesta fase)
  ├─ modo: string ("manual" | "automatico")
  ├─ autorizado: bool
  ├─ quantidadeAgua: number
  ├─ tempMin, tempMax: number
  ├─ emExecucao: bool
  ├─ latitude: number
  ├─ configIrrigacao: map {
  │     kc, espacamento_plantas_m, espacamento_linhas_m,
  │     vazao_gotejador_lph, num_gotejadores_planta,
  │     numero_total_plantas, limiar_umidade_minima
  │   }
  ├─ updatedAt: timestamp
  ├─ telemetry/{autoId}
  │     temperature, humidity, soilPercent, timestamp (epoch do device), recordedAt (server timestamp)
  └─ irrigationHistory/{autoId}
        umidadeAtual, tempo, volumePorPlanta, volumeTotal, data, hora, modo, recordedAt

usuarios/{uid}          → nome, email, tipo, urlImg      (reaproveitado do app mobile)
coeficientes/{cultura}  → cultura, kc1, kc2, kc3, kc4     (reaproveitado do app mobile)
```

Notas importantes:

- Os nomes de campo dentro de `configIrrigacao` ficam em **snake_case**
  propositalmente — é exatamente o que o firmware espera ao processar o
  comando `CONFIG_IRRIGACAO` e ao ler a config inicial
  (`atualizarConfigIrrigacao` no `.ino` lê `"espacamento_plantas_m"`,
  `"vazao_gotejador_lph"`, etc.). Os DTOs Java usam `@JsonProperty` para
  mapear os campos camelCase do modelo Java para essas chaves.
- `devices/{deviceId}/telemetry` é a nova subcoleção que atende ao requisito
  de histórico de umidade do solo/temperatura/umidade do ar. O ESP32 já envia
  essa telemetria a cada 10s via `/api/webhooks/telemetry`; hoje esse dado é
  descartado após uso momentâneo, e passa a ser persistido.
- `usuarios` e `coeficientes` são reaproveitados do schema já usado pelo app
  mobile, sem alteração de formato — evita migração de dados existentes.

## 6. Endpoints

### 6.1 Device-facing (contrato preservado do `.ino`, protegido por `X-Device-Key`)

| Método | Path | Uso |
|---|---|---|
| POST | `/api/webhooks/telemetry` | grava um doc em `devices/{id}/telemetry` |
| POST | `/api/webhooks/status` | upsert do snapshot em `devices/{id}` |
| POST | `/api/webhooks/history` | grava um doc em `devices/{id}/irrigationHistory` |
| GET | `/api/devices/{deviceId}/config` | retorna config inicial (modo, autorizado, quantidadeAgua, latitude, configIrrigacao) |
| GET | `/api/devices/{deviceId}/commands/poll?deviceId={deviceId}` | long-poll de comando pendente; 200 + `{type, payload}` ou 204 após timeout |

Payloads e nomes de campo idênticos aos hoje enviados/recebidos pelo `.ino`
(ver seção "Contrato HTTP real" no cabeçalho do arquivo).

### 6.2 Web-facing (Angular, Firebase Auth via `Authorization: Bearer <idToken>`)

| Método | Path | Role mínima | Uso |
|---|---|---|---|
| GET | `/api/devices/{deviceId}/status` | user | dashboard: snapshot atual do device |
| GET | `/api/devices/{deviceId}/telemetry?from=&to=&limit=` | user | série temporal para gráficos (temp/umidade ar/solo) |
| GET | `/api/devices/{deviceId}/irrigation-history?from=&to=` | user | tela de histórico de irrigações |
| GET | `/api/devices/{deviceId}/config-irrigacao` | user | parâmetros agronômicos atuais |
| PUT | `/api/devices/{deviceId}/config-irrigacao` | admin | atualiza parâmetros agronômicos → enfileira `CONFIG_IRRIGACAO` |
| PUT | `/api/devices/{deviceId}/modo` | admin | troca manual/automático → enfileira `DEFINIR_MODO` |
| PUT | `/api/devices/{deviceId}/autorizacao` | admin | liga/desliga irrigação automática → enfileira `AUTORIZAR_IRRIGACAO` |
| PUT | `/api/devices/{deviceId}/quantidade-agua` | admin | litros do modo manual → enfileira `QUANTIDADE_AGUA` |
| PUT | `/api/devices/{deviceId}/latitude` | admin | usada no cálculo de ETo → enfileira `LATITUDE` |
| POST | `/api/devices/{deviceId}/comandos/irrigar-manual` | admin | aciona irrigação manual → enfileira `IRRIGACAO_MANUAL` |
| GET | `/api/culturas` | user | lista culturas cadastradas |
| GET | `/api/culturas/{nome}` | user | Kc por fase de uma cultura |
| GET | `/api/usuarios/me` | user | perfil do usuário autenticado |
| PUT | `/api/usuarios/me` | user | atualiza nome/senha (via Firebase Auth Admin SDK) |

Cada `PUT`/`POST` de comando grava o novo valor em `devices/{deviceId}`
(fonte da verdade para o próximo GET de status) **e** enfileira o comando
correspondente no `CommandDispatchService`, que o entrega na próxima chamada
de long-poll do device — os mesmos 6 tipos de comando que o firmware já
interpreta (`IRRIGACAO_MANUAL`, `DEFINIR_MODO`, `AUTORIZAR_IRRIGACAO`,
`QUANTIDADE_AGUA`, `CONFIG_IRRIGACAO`, `LATITUDE`).

## 7. Despacho de comandos (long-polling)

`CommandDispatchService` mantém um `Map<String deviceId, CompletableFuture<Command>>`
em memória. `CommandPollController` cria um `DeferredResult<ResponseEntity<?>>`,
registra-o no service e o completa quando:

- um comando é enfileirado (responde 200 com `{type, payload}`); ou
- o timeout de 30s expira (responde 204).

Essa abordagem não bloqueia threads do Tomcat durante a espera e dispensa
infraestrutura extra (fila externa, Firestore listener), adequada para um
único device/uma única instância do backend. Se no futuro for necessário
rodar múltiplas instâncias do backend, a fila precisará migrar para algo
compartilhado (ex.: Firestore com listener ou Redis) — não é necessário
agora.

## 8. Segurança

- **`SecurityConfig`**: duas cadeias de filtro (ou uma cadeia com matchers
  condicionais) — `/api/webhooks/**` e `/api/devices/*/config` e
  `/api/devices/*/commands/poll` passam por `DeviceKeyFilter`; todo o resto
  sob `/api/**` passa por `FirebaseAuthFilter` e exige autenticação.
- **`DeviceKeyFilter`**: compara o header `X-Device-Key` com `DEVICE_API_KEY`
  (do `.env`). Sem correspondência → 401.
- **`FirebaseAuthFilter`**: extrai `Authorization: Bearer <idToken>`, valida
  via `FirebaseAuth.getInstance().verifyIdToken(...)`, busca
  `usuarios/{uid}.tipo` no Firestore, popula `Authentication` com
  `ROLE_ADMIN` (se `tipo == "Administrador"`) ou `ROLE_USER` (caso
  contrário). Token ausente/inválido → 401. Endpoints marcados como `admin`
  usam `@PreAuthorize("hasRole('ADMIN')")`.

## 9. Configuração (`.env`)

`FirebaseConfig` lê do `.env` (via `spring-dotenv` ou `System.getenv`, nunca
de `application.properties` versionado):

- `FIRESTORE_PROJECT_ID`
- `GOOGLE_APPLICATION_CREDENTIALS` (caminho para o JSON da service account)
- `DEVICE_API_KEY` (nova variável — chave fixa comparada ao `X-Device-Key`)
- `MYAPP_CORS_ALLOWED_ORIGINS` (URL do Angular em dev/produção)

`FirebaseConfig` inicializa `FirebaseApp` e expõe `Firestore` e `FirebaseAuth`
como `@Bean`.

## 10. Dependências (`pom.xml`)

Adicionar:

- `com.google.firebase:firebase-admin`
- `org.springframework.boot:spring-boot-starter-security`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- `org.modelmapper:modelmapper`
- `me.paulschwarz:spring-dotenv` (leitura de `.env`)

Remover do `pom.xml` atual (não fazem parte do padrão MovelControl nem são
necessários aqui): `spring-cloud-config-server`,
`spring-cloud-starter-config`, `spring-cloud-starter-gateway-server-webmvc`,
`spring-boot-starter-hateoas(-test)`. Manter `spring-boot-starter-webmvc`,
`spring-boot-starter-restclient`, `lombok`, `devtools`, `docker-compose`.

## 11. Testes

Seguir o padrão do MovelControl: uma classe `*ServiceTest` por Service,
mockando o Repository correspondente (Mockito) — sem depender de um
Firestore real rodando em testes unitários.

## 12. Fora de escopo (nesta fase)

- Multi-device (CRUD de devices, seleção de device na UI).
- Agregação/down-sampling de telemetria (retenção e volume de dados ficam
  para uma fase futura, se necessário).
- Rotação automática de credenciais / segredo gerenciado (Secret Manager) —
  `.env` local é suficiente para este estágio do projeto.
