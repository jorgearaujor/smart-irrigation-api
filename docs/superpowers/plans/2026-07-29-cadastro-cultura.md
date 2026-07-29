# Cadastro de Cultura Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adicionar um endpoint `POST /api/culturas` (admin-only) e uma tela dedicada no front (`/culturas/nova`) para cadastrar uma cultura nova no catálogo (nome + Kc das 4 fases de crescimento).

**Architecture:** Backend Spring Boot em camadas (Controller → Service → Repository → Firestore), mesmo padrão já usado por `POST /api/usuarios`. Front Angular standalone component com `FormsModule`/`ngModel` e signals, mesmo padrão de `CadastroComponent`.

**Tech Stack:** Java 17, Spring Boot, Firestore (google-cloud-firestore), JUnit 5 + Mockito (backend); Angular 21 standalone components, RxJS, Vitest (front, sem suíte de testes de componente pré-existente).

## Global Constraints

- Endpoint novo requer `@PreAuthorize("hasRole('ADMIN')")`, mesmo padrão de `UsuarioController.criarUsuario`.
- Os 4 coeficientes Kc (fases 1 a 4) são obrigatórios no cadastro — nenhum pode ficar nulo.
- Nome de cultura duplicado deve ser bloqueado com `409 Conflict` (`ConflictException`), nunca sobrescrever silenciosamente.
- Kc é armazenado no Firestore como `String` (mesmo formato das culturas já existentes na coleção `coeficientes`) — não migrar o schema existente.
- Front: rota nova protegida por `authGuard` + `adminGuard`, mesmo padrão de `/cadastro`.
- Sem testes de componente/serviço no front (nenhum existe hoje além do smoke test padrão do Angular CLI) — não introduzir um padrão novo isolado; verificação é manual/Playwright, igual ao resto do projeto.

---

## Task 1: Handler genérico de validação (400) no backend

Hoje `GlobalExceptionHandler` só mapeia `NotFoundException` (404), `ConflictException` (409), `UnauthorizedException` (401) e `AccessDeniedException` (403) — falta um genérico para "campo obrigatório faltando", que o cadastro de cultura vai precisar.

**Files:**
- Modify: `src/test/java/com/smartirrigation/smartirrigation/exception/GlobalExceptionHandlerTest.java`
- Modify: `src/main/java/com/smartirrigation/smartirrigation/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: `GlobalExceptionHandler.handleIllegalArgument(IllegalArgumentException ex): ResponseEntity<ErrorResponseDTO>`, mapeado via `@ExceptionHandler(IllegalArgumentException.class)`, status 400.

- [ ] **Step 1: Escrever o teste que falha**

Em `GlobalExceptionHandlerTest.java`, adicione este teste depois de `handleUnauthorized_returns401`:

```java
    @Test
    void handleIllegalArgument_returns400() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleIllegalArgument(new IllegalArgumentException("nome e obrigatorio"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("nome e obrigatorio", response.getBody().getMessage());
    }
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: FAIL na compilação — `cannot find symbol: method handleIllegalArgument`

- [ ] **Step 3: Implementar o handler**

Em `GlobalExceptionHandler.java`, adicione depois do método `handleUnauthorized`:

```java
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: PASS (5 testes)

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/smartirrigation/smartirrigation/exception/GlobalExceptionHandlerTest.java src/main/java/com/smartirrigation/smartirrigation/exception/GlobalExceptionHandler.java
git commit -m "feat: adiciona handler 400 para IllegalArgumentException"
```

---

## Task 2: `CulturaService.criar(...)` — DTO, repositório e validação

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/dto/CulturaCreateRequestDTO.java`
- Modify: `src/main/java/com/smartirrigation/smartirrigation/repository/CulturaRepository.java`
- Modify: `src/main/java/com/smartirrigation/smartirrigation/service/CulturaService.java`
- Modify: `src/test/java/com/smartirrigation/smartirrigation/service/CulturaServiceTest.java`

**Interfaces:**
- Consumes: `CulturaRepository.findByName(String): Optional<CoeficienteCultura>` (já existe), `CulturaDTO` (já existe, campos `nome, kc1, kc2, kc3, kc4` todos `Double` exceto `nome`).
- Produces: `CulturaCreateRequestDTO{nome: String, kc1/kc2/kc3/kc4: Double}` (com getters/setters). `CulturaRepository.save(String nome, CoeficienteCultura cultura): void`. `CulturaService.criar(CulturaCreateRequestDTO dto): CulturaDTO` — lança `IllegalArgumentException` se `nome` vazio ou algum Kc nulo; lança `ConflictException` se já existir cultura com esse nome.

- [ ] **Step 1: Criar o DTO de request**

Crie `src/main/java/com/smartirrigation/smartirrigation/dto/CulturaCreateRequestDTO.java`:

```java
package com.smartirrigation.smartirrigation.dto;

public class CulturaCreateRequestDTO {
    private String nome;
    private Double kc1;
    private Double kc2;
    private Double kc3;
    private Double kc4;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Double getKc1() { return kc1; }
    public void setKc1(Double kc1) { this.kc1 = kc1; }
    public Double getKc2() { return kc2; }
    public void setKc2(Double kc2) { this.kc2 = kc2; }
    public Double getKc3() { return kc3; }
    public void setKc3(Double kc3) { this.kc3 = kc3; }
    public Double getKc4() { return kc4; }
    public void setKc4(Double kc4) { this.kc4 = kc4; }
}
```

- [ ] **Step 2: Adicionar `save(...)` no `CulturaRepository`**

Em `CulturaRepository.java`, adicione este método depois de `findAll()` (mesmo padrão de tratamento de erro usado em `IrrigationHistoryRepository.add`):

```java
    public void save(String nome, CoeficienteCultura cultura) {
        try {
            firestore.collection(COLLECTION).document(nome).set(cultura).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao salvar cultura " + nome + " no Firestore", e);
        }
    }
```

- [ ] **Step 3: Escrever os testes que falham**

Em `CulturaServiceTest.java`, ajuste os imports do topo do arquivo para:

```java
package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.CulturaCreateRequestDTO;
import com.smartirrigation.smartirrigation.dto.CulturaDTO;
import com.smartirrigation.smartirrigation.exception.ConflictException;
import com.smartirrigation.smartirrigation.model.CoeficienteCultura;
import com.smartirrigation.smartirrigation.repository.CulturaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
```

Adicione estes 4 testes no final da classe (antes da última chave `}`):

```java
    @Test
    void criar_throwsIllegalArgument_whenNomeIsBlank() {
        CulturaCreateRequestDTO dto = new CulturaCreateRequestDTO();
        dto.setNome("  ");
        dto.setKc1(0.5);
        dto.setKc2(0.7);
        dto.setKc3(1.0);
        dto.setKc4(0.8);

        assertThrows(IllegalArgumentException.class, () -> service.criar(dto));
    }

    @Test
    void criar_throwsIllegalArgument_whenKcIsMissing() {
        CulturaCreateRequestDTO dto = new CulturaCreateRequestDTO();
        dto.setNome("Alface");
        dto.setKc1(0.5);
        dto.setKc2(null);
        dto.setKc3(1.0);
        dto.setKc4(0.8);

        assertThrows(IllegalArgumentException.class, () -> service.criar(dto));
    }

    @Test
    void criar_throwsConflict_whenCulturaJaExiste() {
        CulturaCreateRequestDTO dto = new CulturaCreateRequestDTO();
        dto.setNome("Tomate");
        dto.setKc1(0.6);
        dto.setKc2(0.8);
        dto.setKc3(1.15);
        dto.setKc4(0.9);
        when(culturaRepository.findByName("Tomate")).thenReturn(Optional.of(new CoeficienteCultura()));

        assertThrows(ConflictException.class, () -> service.criar(dto));
    }

    @Test
    void criar_savesAndReturnsDto_whenValido() {
        CulturaCreateRequestDTO dto = new CulturaCreateRequestDTO();
        dto.setNome("Alface");
        dto.setKc1(0.5);
        dto.setKc2(0.7);
        dto.setKc3(1.0);
        dto.setKc4(0.8);
        when(culturaRepository.findByName("Alface")).thenReturn(Optional.empty());

        CulturaDTO result = service.criar(dto);

        assertEquals("Alface", result.getNome());
        assertEquals(0.5, result.getKc1());
        assertEquals(0.7, result.getKc2());
        assertEquals(1.0, result.getKc3());
        assertEquals(0.8, result.getKc4());

        ArgumentCaptor<CoeficienteCultura> captor = ArgumentCaptor.forClass(CoeficienteCultura.class);
        verify(culturaRepository).save(eq("Alface"), captor.capture());
        assertEquals("0.5", captor.getValue().getKc1());
    }
```

- [ ] **Step 4: Rodar os testes e confirmar que falham**

Run: `./mvnw test -Dtest=CulturaServiceTest`
Expected: FAIL na compilação — `cannot find symbol: method criar`

- [ ] **Step 5: Implementar `CulturaService.criar(...)`**

Em `CulturaService.java`, adicione os imports:

```java
import com.smartirrigation.smartirrigation.dto.CulturaCreateRequestDTO;
import com.smartirrigation.smartirrigation.exception.ConflictException;
```

E adicione este método (antes do `toDto` privado):

```java
    public CulturaDTO criar(CulturaCreateRequestDTO dto) {
        if (dto.getNome() == null || dto.getNome().isBlank()) {
            throw new IllegalArgumentException("O nome da cultura e obrigatorio.");
        }
        if (dto.getKc1() == null || dto.getKc2() == null || dto.getKc3() == null || dto.getKc4() == null) {
            throw new IllegalArgumentException("Os coeficientes Kc das 4 fases sao obrigatorios.");
        }

        String nome = dto.getNome().trim();
        if (culturaRepository.findByName(nome).isPresent()) {
            throw new ConflictException("Ja existe uma cultura chamada '" + nome + "'.");
        }

        CoeficienteCultura cultura = new CoeficienteCultura();
        cultura.setCultura(nome);
        cultura.setKc1(String.valueOf(dto.getKc1()));
        cultura.setKc2(String.valueOf(dto.getKc2()));
        cultura.setKc3(String.valueOf(dto.getKc3()));
        cultura.setKc4(String.valueOf(dto.getKc4()));
        culturaRepository.save(nome, cultura);

        return toDto(cultura);
    }
```

- [ ] **Step 6: Rodar os testes e confirmar que passam**

Run: `./mvnw test -Dtest=CulturaServiceTest`
Expected: PASS (6 testes)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/dto/CulturaCreateRequestDTO.java src/main/java/com/smartirrigation/smartirrigation/repository/CulturaRepository.java src/main/java/com/smartirrigation/smartirrigation/service/CulturaService.java src/test/java/com/smartirrigation/smartirrigation/service/CulturaServiceTest.java
git commit -m "feat: adiciona CulturaService.criar com validacao e checagem de duplicidade"
```

---

## Task 3: Endpoint `POST /api/culturas`

**Files:**
- Modify: `src/main/java/com/smartirrigation/smartirrigation/controller/web/CulturaController.java`

**Interfaces:**
- Consumes: `CulturaService.criar(CulturaCreateRequestDTO): CulturaDTO` (Task 2).
- Produces: `POST /api/culturas` — `201 Created` com `CulturaDTO`, `400` se faltar campo, `409` se nome duplicado, `403` se não-admin.

- [ ] **Step 1: Adicionar o endpoint**

Substitua o conteúdo de `CulturaController.java` por:

```java
package com.smartirrigation.smartirrigation.controller.web;

import com.smartirrigation.smartirrigation.dto.CulturaCreateRequestDTO;
import com.smartirrigation.smartirrigation.dto.CulturaDTO;
import com.smartirrigation.smartirrigation.service.CulturaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/culturas")
@Tag(name = "Culturas", description = "Catalogo de culturas cadastradas e seus coeficientes por fase")
public class CulturaController {

    private final CulturaService culturaService;

    public CulturaController(CulturaService culturaService) {
        this.culturaService = culturaService;
    }

    @GetMapping
    public ResponseEntity<List<CulturaDTO>> listCulturas() {
        return ResponseEntity.ok(culturaService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CulturaDTO> criarCultura(@RequestBody CulturaCreateRequestDTO dto) {
        CulturaDTO created = culturaService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

- [ ] **Step 2: Rodar a suíte inteira**

Run: `./mvnw test`
Expected: BUILD SUCCESS, todos os testes passam (incluindo os 5 da Task 1 e 6 da Task 2 dentro do total).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/controller/web/CulturaController.java
git commit -m "feat: expõe POST /api/culturas (admin-only)"
```

- [ ] **Step 4: Verificação manual end-to-end**

Suba o backend (mate qualquer instância antiga primeiro — `mvn spring-boot:run` com DevTools ativo é instável para restart automático neste projeto, prefira sempre matar e subir de novo em vez de deixar o watch recarregar sozinho):

```bash
./mvnw spring-boot:run
```

Em outro terminal, pegue um token de admin (troque email/senha por um usuário `Administrador` real do projeto) e teste o fluxo completo:

```bash
RESP=$(curl -s -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=<FIREBASE_API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{"email":"<admin-email>","password":"<admin-senha>","returnSecureToken":true}')
TOKEN=$(echo "$RESP" | node -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>{console.log(JSON.parse(d).idToken)})")

# Caminho feliz: 201 com a cultura criada
curl -s -w "\nHTTP:%{http_code}\n" -X POST http://localhost:8080/api/culturas \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nome":"Alface Teste","kc1":0.5,"kc2":0.7,"kc3":1.0,"kc4":0.8}'

# Nome duplicado: 409
curl -s -w "\nHTTP:%{http_code}\n" -X POST http://localhost:8080/api/culturas \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nome":"Alface Teste","kc1":0.5,"kc2":0.7,"kc3":1.0,"kc4":0.8}'

# Campo faltando: 400
curl -s -w "\nHTTP:%{http_code}\n" -X POST http://localhost:8080/api/culturas \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nome":"Sem Kc"}'

# GET /api/culturas deve listar a cultura criada
curl -s http://localhost:8080/api/culturas -H "Authorization: Bearer $TOKEN" | grep "Alface Teste"
```

Expected: primeiro `HTTP:201` com o corpo `{"nome":"Alface Teste","kc1":0.5,...}`; segundo `HTTP:409`; terceiro `HTTP:400`; o `GET` final encontra "Alface Teste" na lista.

---

## Task 4: Frontend — rota, serviço e tela `/culturas/nova`

**Files:**
- Modify: `src/app/core/services/irrigation.service.ts`
- Create: `src/app/features/culturas/nova/nova-cultura.component.ts`
- Create: `src/app/features/culturas/nova/nova-cultura.component.html`
- Modify: `src/app/app.routes.ts`
- Modify: `src/app/features/settings/settings.component.html`

**Interfaces:**
- Consumes: `Cultura{nome: string, kc1: number, kc2: number, kc3: number, kc4: number}` (já existe em `core/models/cultura.model.ts` — reaproveitado como shape tanto de request quanto de response, já que os campos são idênticos nos dois sentidos, diferente do caso de usuário).
- Produces: `IrrigationService.criarCultura(cultura: Cultura): Observable<Cultura>`. Rota `/culturas/nova` protegida por `authGuard` + `adminGuard`.

- [ ] **Step 1: Adicionar `criarCultura` no `IrrigationService`**

Em `src/app/core/services/irrigation.service.ts`, logo depois do método `getCulturas()` existente, adicione:

```ts
  criarCultura(cultura: Cultura): Observable<Cultura> {
    return this.http.post<Cultura>(`${environment.apiBaseUrl}/culturas`, cultura);
  }
```

- [ ] **Step 2: Criar o componente `NovaCulturaComponent`**

Crie `src/app/features/culturas/nova/nova-cultura.component.ts`:

```ts
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { IrrigationService } from '../../../core/services/irrigation.service';

@Component({
  selector: 'app-nova-cultura',
  imports: [FormsModule],
  templateUrl: './nova-cultura.component.html',
})
export class NovaCulturaComponent {
  private readonly irrigation = inject(IrrigationService);
  private readonly router = inject(Router);

  protected readonly nome = signal('');
  protected readonly kc1 = signal<number | null>(null);
  protected readonly kc2 = signal<number | null>(null);
  protected readonly kc3 = signal<number | null>(null);
  protected readonly kc4 = signal<number | null>(null);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal(false);

  protected cadastrar(): void {
    if (!this.nome() || this.kc1() == null || this.kc2() == null || this.kc3() == null || this.kc4() == null) {
      this.error.set('Preencha todos os campos');
      return;
    }

    this.submitting.set(true);
    this.error.set(null);
    this.success.set(false);

    this.irrigation
      .criarCultura({
        nome: this.nome(),
        kc1: this.kc1()!,
        kc2: this.kc2()!,
        kc3: this.kc3()!,
        kc4: this.kc4()!,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.success.set(true);
          this.nome.set('');
          this.kc1.set(null);
          this.kc2.set(null);
          this.kc3.set(null);
          this.kc4.set(null);
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(
            err?.status === 409 ? 'Já existe uma cultura com esse nome.' : 'Erro ao cadastrar cultura',
          );
        },
      });
  }

  protected voltar(): void {
    this.router.navigateByUrl('/configuracoes');
  }
}
```

Crie `src/app/features/culturas/nova/nova-cultura.component.html`:

```html
<div class="flex flex-col gap-6">
  <div>
    <h1 class="text-2xl font-bold tracking-tight">Nova cultura</h1>
    <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">
      Cadastre uma cultura nova com o coeficiente Kc de cada fase de crescimento.
    </p>
  </div>

  <div class="max-w-lg rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
    @if (error(); as message) {
      <div class="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 dark:border-red-900 dark:bg-red-950/50 dark:text-red-400">
        {{ message }}
      </div>
    }

    @if (success()) {
      <div class="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950/50 dark:text-emerald-400">
        Cultura cadastrada com sucesso ✓
      </div>
    }

    <form class="flex flex-col gap-4" (ngSubmit)="cadastrar()">
      <label class="block">
        <span class="text-sm font-semibold text-slate-600 dark:text-slate-300">Nome</span>
        <input
          type="text"
          [ngModel]="nome()"
          (ngModelChange)="nome.set($event)"
          name="nome"
          class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-base focus:border-emerald-500 focus:outline-none dark:border-slate-700 dark:bg-slate-800"
        />
      </label>

      <div class="grid grid-cols-2 gap-4">
        <label class="block">
          <span class="text-sm font-semibold text-slate-600 dark:text-slate-300">Kc fase 1</span>
          <input
            type="number"
            step="0.01"
            min="0"
            [ngModel]="kc1()"
            (ngModelChange)="kc1.set($event)"
            name="kc1"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-base focus:border-emerald-500 focus:outline-none dark:border-slate-700 dark:bg-slate-800"
          />
        </label>

        <label class="block">
          <span class="text-sm font-semibold text-slate-600 dark:text-slate-300">Kc fase 2</span>
          <input
            type="number"
            step="0.01"
            min="0"
            [ngModel]="kc2()"
            (ngModelChange)="kc2.set($event)"
            name="kc2"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-base focus:border-emerald-500 focus:outline-none dark:border-slate-700 dark:bg-slate-800"
          />
        </label>

        <label class="block">
          <span class="text-sm font-semibold text-slate-600 dark:text-slate-300">Kc fase 3</span>
          <input
            type="number"
            step="0.01"
            min="0"
            [ngModel]="kc3()"
            (ngModelChange)="kc3.set($event)"
            name="kc3"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-base focus:border-emerald-500 focus:outline-none dark:border-slate-700 dark:bg-slate-800"
          />
        </label>

        <label class="block">
          <span class="text-sm font-semibold text-slate-600 dark:text-slate-300">Kc fase 4</span>
          <input
            type="number"
            step="0.01"
            min="0"
            [ngModel]="kc4()"
            (ngModelChange)="kc4.set($event)"
            name="kc4"
            class="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-base focus:border-emerald-500 focus:outline-none dark:border-slate-700 dark:bg-slate-800"
          />
        </label>
      </div>

      <div class="mt-2 flex items-center gap-3">
        <button
          type="submit"
          [disabled]="submitting()"
          class="rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-slate-700 disabled:opacity-60 dark:bg-emerald-600 dark:hover:bg-emerald-700"
        >
          {{ submitting() ? 'Cadastrando...' : 'Cadastrar' }}
        </button>
        <button
          type="button"
          (click)="voltar()"
          class="rounded-xl px-4 py-2.5 text-sm font-semibold text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
        >
          Voltar
        </button>
      </div>
    </form>
  </div>
</div>
```

- [ ] **Step 3: Adicionar a rota**

Em `src/app/app.routes.ts`, adicione esta entrada logo depois da rota `cadastro`:

```ts
  {
    path: 'culturas/nova',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/culturas/nova/nova-cultura.component').then((m) => m.NovaCulturaComponent),
    title: 'SmartIrrigation · Nova cultura',
  },
```

- [ ] **Step 4: Adicionar o botão "Nova cultura" em Configurações**

Em `src/app/features/settings/settings.component.html`, substitua o bloco `@if (canManageUsuarios()) { ... }` (linhas 9–26) por:

```html
  @if (canManageUsuarios()) {
    <div class="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <div class="flex items-center justify-between gap-4">
        <div>
          <h2 class="text-lg font-bold tracking-tight">Usuários</h2>
          <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">
            Cadastre o acesso de novos usuários ao painel.
          </p>
        </div>
        <a
          routerLink="/cadastro"
          class="shrink-0 rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-slate-700 dark:bg-emerald-600 dark:hover:bg-emerald-700"
        >
          Novo usuário
        </a>
      </div>

      <div class="flex items-center justify-between gap-4 border-t border-slate-100 pt-4 dark:border-slate-800">
        <div>
          <h2 class="text-lg font-bold tracking-tight">Culturas</h2>
          <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">
            Cadastre uma cultura nova no catálogo, com o Kc de cada fase.
          </p>
        </div>
        <a
          routerLink="/culturas/nova"
          class="shrink-0 rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-slate-700 dark:bg-emerald-600 dark:hover:bg-emerald-700"
        >
          Nova cultura
        </a>
      </div>
    </div>
  }
```

- [ ] **Step 5: Build**

Run: `npm run build`
Expected: `Application bundle generation complete`, sem erros TypeScript.

- [ ] **Step 6: Commit**

```bash
git add src/app/core/services/irrigation.service.ts src/app/features/culturas src/app/app.routes.ts src/app/features/settings/settings.component.html
git commit -m "feat: adiciona tela de cadastro de cultura (/culturas/nova)"
```

- [ ] **Step 7: Verificação manual end-to-end (Playwright)**

Com o backend (Task 3) e o front (`npm start`) rodando, e um usuário Administrador de teste já criado no Firebase Auth + Firestore (`usuarios/{uid}.tipo = "Administrador"`), crie um script Python temporário (em qualquer pasta de scratch) com este conteúdo:

```python
from playwright.sync_api import sync_playwright

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()

    # login como admin
    page.goto('http://localhost:4200/login')
    page.wait_for_load_state('networkidle')
    page.fill('input[type="email"]', '<admin-email>')
    page.fill('input[type="password"]', '<admin-senha>')
    page.click('button[type="submit"]')
    page.wait_for_url('**/dashboard', timeout=10000)

    # vai pra tela de nova cultura
    page.goto('http://localhost:4200/culturas/nova')
    page.wait_for_load_state('networkidle')

    page.fill('input[name="nome"]', 'Cultura Playwright')
    page.fill('input[name="kc1"]', '0.5')
    page.fill('input[name="kc2"]', '0.7')
    page.fill('input[name="kc3"]', '1.0')
    page.fill('input[name="kc4"]', '0.8')
    page.click('button[type="submit"]')
    page.wait_for_timeout(2000)

    print('sucesso visivel:', page.locator('text=Cultura cadastrada com sucesso').count())

    # tenta de novo com o mesmo nome -> deve mostrar erro de duplicado
    page.click('button[type="submit"]')
    page.wait_for_timeout(2000)
    print('erro duplicado visivel:', page.locator('text=Já existe uma cultura com esse nome').count())

    browser.close()
```

Run: `python <caminho-do-script>.py`
Expected: `sucesso visivel: 1` na primeira submissão; `erro duplicado visivel: 1` na segunda (mesmo nome). Confirme também via `GET /api/culturas` (ou visualmente no card de Cultura em Configurações) que "Cultura Playwright" aparece na lista.

---

## Verificação final

- [ ] Rodar a suíte completa do backend uma última vez: `./mvnw test` — todos os testes (incluindo os 11 novos desta feature) devem passar.
- [ ] Rodar `npm run build` no front uma última vez — sem erros.
- [ ] Confirmar visualmente (screenshot ou Playwright) que o botão "Nova cultura" só aparece em Configurações quando logado como Administrador (logar como um usuário `Visualizador` e confirmar que o card de Usuários/Culturas inteiro some).
