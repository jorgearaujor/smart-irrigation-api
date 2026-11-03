# SmartIrrigation Backend Foundation Implementation Plan

**Goal:** Stand up the Spring Boot backend foundation — dependencies, Firebase/Firestore config read from `.env`, security (device API key + Firebase Auth roles), exception handling, and the full device-facing contract (webhooks + config + command long-poll) plus one complete web-facing vertical slice (status + config de irrigação), all following the MovelControl layered architecture.

**Architecture:** Layered `config/security/controller/service/repository/model/dto/exception` packages under `com.smartirrigation.smartirrigation`, mirroring MovelControl's `Controller → Service → Repository → Model` flow, with Firestore (Firebase Admin SDK) replacing JPA. Two independent auth paths: `DeviceKeyFilter` (ESP32, header `X-Device-Key`) and `FirebaseAuthFilter` (Angular, Firebase ID Token + role from Firestore).

**Tech Stack:** Java 17, Spring Boot 4.1.0, Spring Security, Firebase Admin SDK (Firestore + Auth), ModelMapper, springdoc-openapi, spring-dotenv, JUnit 5 + Mockito.

## Global Constraints

- Base package: `com.smartirrigation.smartirrigation` (already established by `SmartIrrigationApplication`).
- Firestore field names inside `configIrrigacao` MUST stay snake_case (`espacamento_plantas_m`, `espacamento_linhas_m`, `vazao_gotejador_lph`, `num_gotejadores_planta`, `numero_total_plantas`, `limiar_umidade_minima`) — the ESP32 firmware parses these exact keys and is not being modified beyond the `X-Device-Key` header.
- Device webhook/config/poll endpoints and payload shapes must exactly match `irrigation_controller_esp32.ino`'s `ApiClient`/`CommandPoller` contract (paths, JSON field names, status codes 200/204).
- No secrets in `application.properties` — everything sensitive comes from `.env` (`FIRESTORE_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS`, `DEVICE_API_KEY`, `MYAPP_CORS_ALLOWED_ORIGINS`).
- Firestore repository classes are thin wrappers around the Admin SDK and are not unit-tested against a real Firestore (no emulator in this environment); business logic that can be isolated from Firestore I/O (config parsing, command dispatch, role mapping, request validation) IS unit-tested.
- This plan covers the foundation + device contract + one web vertical slice (status, config-irrigação). The remaining web endpoints listed in `docs/superpowers/specs/2026-07-25-backend-api-design.md` section 6.2 (telemetry history, irrigation history, modo/autorização/quantidade-agua/latitude, comandos/irrigar-manual, culturas, usuarios/me) follow the exact same Controller→Service→Repository→command-enqueue pattern established in Task 13 and are left to a follow-up plan.

---

## File Structure

```
pom.xml                                                          (modify)
.env.example                                                     (modify)
src/main/resources/application.properties                        (modify)
src/main/java/com/smartirrigation/smartirrigation/
├── exception/
│   ├── NotFoundException.java
│   ├── ConflictException.java
│   ├── UnauthorizedException.java
│   ├── ErrorResponseDTO.java
│   └── GlobalExceptionHandler.java
├── dto/
│   └── DataMapper.java
├── model/
│   ├── ConfigIrrigacao.java
│   ├── Device.java
│   ├── Telemetry.java
│   ├── IrrigationHistoryRecord.java
│   ├── Usuario.java
│   └── CoeficienteCultura.java
├── config/
│   ├── FirebaseEnv.java
│   ├── FirebaseConfig.java
│   ├── WebConfig.java
│   └── OpenApiConfig.java
├── service/
│   ├── CommandDispatchService.java
│   ├── DeviceService.java
│   └── ConfigIrrigacaoService.java
├── repository/
│   ├── DeviceRepository.java
│   ├── TelemetryRepository.java
│   ├── IrrigationHistoryRepository.java
│   ├── UsuarioRepository.java
│   └── CulturaRepository.java
├── security/
│   ├── DeviceKeyFilter.java
│   ├── FirebaseAuthFilter.java
│   └── SecurityConfig.java
├── dto/
│   ├── TelemetryWebhookRequestDTO.java
│   ├── StatusWebhookRequestDTO.java
│   ├── HistoryWebhookRequestDTO.java
│   ├── ConfigIrrigacaoDTO.java
│   ├── DeviceConfigResponseDTO.java
│   ├── DeviceStatusResponseDTO.java
│   └── CommandResponseDTO.java
└── controller/
    ├── device/
    │   ├── DeviceWebhookController.java
    │   ├── DeviceConfigController.java
    │   └── CommandPollController.java
    └── web/
        ├── StatusController.java
        └── ConfigIrrigacaoController.java

src/test/java/com/smartirrigation/smartirrigation/
├── exception/GlobalExceptionHandlerTest.java
├── config/FirebaseEnvTest.java
├── service/CommandDispatchServiceTest.java
├── service/DeviceServiceTest.java
├── service/ConfigIrrigacaoServiceTest.java
├── security/DeviceKeyFilterTest.java
└── security/FirebaseAuthFilterTest.java
```

---

## Task 1: Update `pom.xml` dependencies

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Produces: build classpath containing `firebase-admin`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `springdoc-openapi-starter-webmvc-ui`, `modelmapper`, `spring-dotenv`; removes `spring-cloud-*` and `spring-boot-starter-hateoas*`.

- [ ] **Step 1: Edit dependencies**

Replace the `<dependencies>` block in `pom.xml` with:

```xml
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-restclient</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.google.firebase</groupId>
            <artifactId>firebase-admin</artifactId>
            <version>9.4.1</version>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.8.8</version>
        </dependency>
        <dependency>
            <groupId>org.modelmapper</groupId>
            <artifactId>modelmapper</artifactId>
            <version>3.2.1</version>
        </dependency>
        <dependency>
            <groupId>me.paulschwarz</groupId>
            <artifactId>spring-dotenv</artifactId>
            <version>4.0.0</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-docker-compose</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-restclient-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
```

Also delete the now-unused `<dependencyManagement>` block (it only imported `spring-cloud-dependencies`, which no longer applies) and the `<spring-cloud.version>` property.

- [ ] **Step 2: Verify the project still builds**

Run: `./mvnw.cmd compile -q` (Windows) or `./mvnw compile -q`
Expected: `BUILD SUCCESS`, no output otherwise.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: swap cloud/hateoas deps for firebase-admin, security, and openapi"
```

---

## Task 2: Exception handling layer

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/exception/NotFoundException.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/exception/ConflictException.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/exception/UnauthorizedException.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/exception/ErrorResponseDTO.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/smartirrigation/smartirrigation/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `NotFoundException(String)`, `ConflictException(String)`, `UnauthorizedException(String)` (all `RuntimeException`); `ErrorResponseDTO(int status, String error, String message, LocalDateTime timestamp)` with getters; `GlobalExceptionHandler` handling all three plus `MethodArgumentNotValidException` and generic `Exception`.

- [ ] **Step 1: Write the failing test**

```java
package com.smartirrigation.smartirrigation.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404WithMessage() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleNotFound(new NotFoundException("device esp32-01 nao encontrado"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("device esp32-01 nao encontrado", response.getBody().getMessage());
        assertEquals(404, response.getBody().getStatus());
    }

    @Test
    void handleConflict_returns409() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleConflict(new ConflictException("device ja cadastrado"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
    }

    @Test
    void handleUnauthorized_returns401() {
        ResponseEntity<ErrorResponseDTO> response =
                handler.handleUnauthorized(new UnauthorizedException("token invalido"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().getStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd test -Dtest=GlobalExceptionHandlerTest`
Expected: FAIL — compile error, classes don't exist yet.

- [ ] **Step 3: Write the exception classes**

```java
package com.smartirrigation.smartirrigation.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
```

```java
package com.smartirrigation.smartirrigation.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
```

```java
package com.smartirrigation.smartirrigation.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
```

```java
package com.smartirrigation.smartirrigation.exception;

import java.time.LocalDateTime;

public class ErrorResponseDTO {

    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;

    public ErrorResponseDTO() {
    }

    public ErrorResponseDTO(int status, String error, String message, LocalDateTime timestamp) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
```

```java
package com.smartirrigation.smartirrigation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponseDTO> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnauthorized(UnauthorizedException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse(ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, mensagem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneral(Exception ex) {
        ex.printStackTrace();
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado. Por favor, tente novamente.");
    }

    private ResponseEntity<ErrorResponseDTO> build(HttpStatus status, String message) {
        ErrorResponseDTO error = new ErrorResponseDTO(status.value(), status.getReasonPhrase(), message, LocalDateTime.now());
        return ResponseEntity.status(status).body(error);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw.cmd test -Dtest=GlobalExceptionHandlerTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/exception src/test/java/com/smartirrigation/smartirrigation/exception
git commit -m "feat: add exception hierarchy and global exception handler"
```

---

## Task 3: `DataMapper`

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/dto/DataMapper.java`

**Interfaces:**
- Produces: `DataMapper.parseObject(Object origin, Class<D> destination)`, `DataMapper.parseListObjects(List<O> origin, Class<D> destination)` — used by every Service→DTO conversion in later tasks.

- [ ] **Step 1: Write `DataMapper`**

```java
package com.smartirrigation.smartirrigation.dto;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DataMapper {

    private static final ModelMapper mapper = new ModelMapper();

    public static <O, D> D parseObject(O origin, Class<D> destination) {
        return mapper.map(origin, destination);
    }

    public static <O, D> List<D> parseListObjects(List<O> origin, Class<D> destination) {
        List<D> destinationObjects = new ArrayList<>();
        for (O o : origin) {
            destinationObjects.add(mapper.map(o, destination));
        }
        return destinationObjects;
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./mvnw.cmd compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/dto/DataMapper.java
git commit -m "feat: add DataMapper (ModelMapper wrapper) matching MovelControl pattern"
```

---

## Task 4: Model classes

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/model/ConfigIrrigacao.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/model/Device.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/model/Telemetry.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/model/IrrigationHistoryRecord.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/model/Usuario.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/model/CoeficienteCultura.java`

**Interfaces:**
- Produces: POJOs consumed by `repository/*` (Task 7), `service/*` (Tasks 6, 12, 13) and `dto/*` (Task 11, 13). All have public no-arg constructors and public getters/setters so the Firestore SDK's `DocumentSnapshot.toObject(...)` can deserialize them.

- [ ] **Step 1: Write `ConfigIrrigacao`**

```java
package com.smartirrigation.smartirrigation.model;

import com.google.cloud.firestore.annotation.PropertyName;

public class ConfigIrrigacao {

    private Double kc = 1.40;
    private Double espacamentoPlantasM = 0.3;
    private Double espacamentoLinhasM = 0.3;
    private Double vazaoGotejadorLph = 2.0;
    private Integer numGotejadoresPlanta = 1;
    private Integer numeroTotalPlantas = 30;
    private Double limiarUmidadeMinima = 50.0;

    public Double getKc() {
        return kc;
    }

    public void setKc(Double kc) {
        this.kc = kc;
    }

    @PropertyName("espacamento_plantas_m")
    public Double getEspacamentoPlantasM() {
        return espacamentoPlantasM;
    }

    @PropertyName("espacamento_plantas_m")
    public void setEspacamentoPlantasM(Double espacamentoPlantasM) {
        this.espacamentoPlantasM = espacamentoPlantasM;
    }

    @PropertyName("espacamento_linhas_m")
    public Double getEspacamentoLinhasM() {
        return espacamentoLinhasM;
    }

    @PropertyName("espacamento_linhas_m")
    public void setEspacamentoLinhasM(Double espacamentoLinhasM) {
        this.espacamentoLinhasM = espacamentoLinhasM;
    }

    @PropertyName("vazao_gotejador_lph")
    public Double getVazaoGotejadorLph() {
        return vazaoGotejadorLph;
    }

    @PropertyName("vazao_gotejador_lph")
    public void setVazaoGotejadorLph(Double vazaoGotejadorLph) {
        this.vazaoGotejadorLph = vazaoGotejadorLph;
    }

    @PropertyName("num_gotejadores_planta")
    public Integer getNumGotejadoresPlanta() {
        return numGotejadoresPlanta;
    }

    @PropertyName("num_gotejadores_planta")
    public void setNumGotejadoresPlanta(Integer numGotejadoresPlanta) {
        this.numGotejadoresPlanta = numGotejadoresPlanta;
    }

    @PropertyName("numero_total_plantas")
    public Integer getNumeroTotalPlantas() {
        return numeroTotalPlantas;
    }

    @PropertyName("numero_total_plantas")
    public void setNumeroTotalPlantas(Integer numeroTotalPlantas) {
        this.numeroTotalPlantas = numeroTotalPlantas;
    }

    @PropertyName("limiar_umidade_minima")
    public Double getLimiarUmidadeMinima() {
        return limiarUmidadeMinima;
    }

    @PropertyName("limiar_umidade_minima")
    public void setLimiarUmidadeMinima(Double limiarUmidadeMinima) {
        this.limiarUmidadeMinima = limiarUmidadeMinima;
    }
}
```

- [ ] **Step 2: Write `Device`**

```java
package com.smartirrigation.smartirrigation.model;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.annotation.Exclude;

public class Device {

    @Exclude
    private String deviceId;

    private String modo = "manual";
    private Boolean autorizado = false;
    private Double quantidadeAgua = 0.0;
    private Double tempMin;
    private Double tempMax;
    private Boolean emExecucao = false;
    private Double latitude = 0.0;
    private ConfigIrrigacao configIrrigacao = new ConfigIrrigacao();
    private Timestamp updatedAt;

    @Exclude
    public String getDeviceId() {
        return deviceId;
    }

    @Exclude
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getModo() {
        return modo;
    }

    public void setModo(String modo) {
        this.modo = modo;
    }

    public Boolean getAutorizado() {
        return autorizado;
    }

    public void setAutorizado(Boolean autorizado) {
        this.autorizado = autorizado;
    }

    public Double getQuantidadeAgua() {
        return quantidadeAgua;
    }

    public void setQuantidadeAgua(Double quantidadeAgua) {
        this.quantidadeAgua = quantidadeAgua;
    }

    public Double getTempMin() {
        return tempMin;
    }

    public void setTempMin(Double tempMin) {
        this.tempMin = tempMin;
    }

    public Double getTempMax() {
        return tempMax;
    }

    public void setTempMax(Double tempMax) {
        this.tempMax = tempMax;
    }

    public Boolean getEmExecucao() {
        return emExecucao;
    }

    public void setEmExecucao(Boolean emExecucao) {
        this.emExecucao = emExecucao;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public ConfigIrrigacao getConfigIrrigacao() {
        return configIrrigacao;
    }

    public void setConfigIrrigacao(ConfigIrrigacao configIrrigacao) {
        this.configIrrigacao = configIrrigacao;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
```

- [ ] **Step 3: Write `Telemetry`**

```java
package com.smartirrigation.smartirrigation.model;

import com.google.cloud.Timestamp;

public class Telemetry {

    private Double temperature;
    private Double humidity;
    private Double soilPercent;
    private Long timestamp;
    private Timestamp recordedAt;

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getHumidity() {
        return humidity;
    }

    public void setHumidity(Double humidity) {
        this.humidity = humidity;
    }

    public Double getSoilPercent() {
        return soilPercent;
    }

    public void setSoilPercent(Double soilPercent) {
        this.soilPercent = soilPercent;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Timestamp getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Timestamp recordedAt) {
        this.recordedAt = recordedAt;
    }
}
```

- [ ] **Step 4: Write `IrrigationHistoryRecord`**

```java
package com.smartirrigation.smartirrigation.model;

import com.google.cloud.Timestamp;

public class IrrigationHistoryRecord {

    private Double umidadeAtual;
    private String tempo;
    private Double volumePorPlanta;
    private Double volumeTotal;
    private String data;
    private String hora;
    private String modo;
    private Timestamp recordedAt;

    public Double getUmidadeAtual() {
        return umidadeAtual;
    }

    public void setUmidadeAtual(Double umidadeAtual) {
        this.umidadeAtual = umidadeAtual;
    }

    public String getTempo() {
        return tempo;
    }

    public void setTempo(String tempo) {
        this.tempo = tempo;
    }

    public Double getVolumePorPlanta() {
        return volumePorPlanta;
    }

    public void setVolumePorPlanta(Double volumePorPlanta) {
        this.volumePorPlanta = volumePorPlanta;
    }

    public Double getVolumeTotal() {
        return volumeTotal;
    }

    public void setVolumeTotal(Double volumeTotal) {
        this.volumeTotal = volumeTotal;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public String getModo() {
        return modo;
    }

    public void setModo(String modo) {
        this.modo = modo;
    }

    public Timestamp getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Timestamp recordedAt) {
        this.recordedAt = recordedAt;
    }
}
```

- [ ] **Step 5: Write `Usuario`**

```java
package com.smartirrigation.smartirrigation.model;

public class Usuario {

    private String uid = "";
    private String nome = "";
    private String email = "";
    private String tipo = "";
    private String urlImg = "";

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getUrlImg() {
        return urlImg;
    }

    public void setUrlImg(String urlImg) {
        this.urlImg = urlImg;
    }
}
```

- [ ] **Step 6: Write `CoeficienteCultura`**

```java
package com.smartirrigation.smartirrigation.model;

public class CoeficienteCultura {

    private String cultura = "";
    private String kc1 = "";
    private String kc2 = "";
    private String kc3 = "";
    private String kc4 = "";

    public String getCultura() {
        return cultura;
    }

    public void setCultura(String cultura) {
        this.cultura = cultura;
    }

    public String getKc1() {
        return kc1;
    }

    public void setKc1(String kc1) {
        this.kc1 = kc1;
    }

    public String getKc2() {
        return kc2;
    }

    public void setKc2(String kc2) {
        this.kc2 = kc2;
    }

    public String getKc3() {
        return kc3;
    }

    public void setKc3(String kc3) {
        this.kc3 = kc3;
    }

    public String getKc4() {
        return kc4;
    }

    public void setKc4(String kc4) {
        this.kc4 = kc4;
    }
}
```

- [ ] **Step 7: Verify it compiles**

Run: `./mvnw.cmd compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/model
git commit -m "feat: add Firestore model classes for device, telemetry, history, usuario, cultura"
```

---

## Task 5: `FirebaseEnv` + `FirebaseConfig`

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/config/FirebaseEnv.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/config/FirebaseConfig.java`
- Test: `src/test/java/com/smartirrigation/smartirrigation/config/FirebaseEnvTest.java`

**Interfaces:**
- Produces: `FirebaseEnv.fromEnvironment(Environment env)` → `FirebaseEnv` record with `projectId()`, `credentialsPath()`, `deviceApiKey()`, `corsAllowedOrigins()`; throws `IllegalStateException` when a required variable is missing. `FirebaseConfig` exposes `@Bean Firestore firestore()`, `@Bean FirebaseAuth firebaseAuth()`, `@Bean FirebaseEnv firebaseEnv()`. Consumed by `repository/*` (Task 7), `security/*` (Tasks 8–9).

- [ ] **Step 1: Write the failing test**

```java
package com.smartirrigation.smartirrigation.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FirebaseEnvTest {

    @Test
    void fromEnvironment_readsAllRequiredValues() {
        Environment env = mock(Environment.class);
        when(env.getProperty("FIRESTORE_PROJECT_ID")).thenReturn("smart-irrigation-ifmaker");
        when(env.getProperty("GOOGLE_APPLICATION_CREDENTIALS")).thenReturn("./firebase.json");
        when(env.getProperty("DEVICE_API_KEY")).thenReturn("chave-secreta");
        when(env.getProperty("MYAPP_CORS_ALLOWED_ORIGINS", "http://localhost:4200"))
                .thenReturn("http://localhost:4200");

        FirebaseEnv result = FirebaseEnv.fromEnvironment(env);

        assertEquals("smart-irrigation-ifmaker", result.projectId());
        assertEquals("./firebase.json", result.credentialsPath());
        assertEquals("chave-secreta", result.deviceApiKey());
        assertEquals("http://localhost:4200", result.corsAllowedOrigins());
    }

    @Test
    void fromEnvironment_throwsWhenProjectIdMissing() {
        Environment env = mock(Environment.class);
        when(env.getProperty("FIRESTORE_PROJECT_ID")).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> FirebaseEnv.fromEnvironment(env));
    }

    @Test
    void fromEnvironment_throwsWhenDeviceApiKeyMissing() {
        Environment env = mock(Environment.class);
        when(env.getProperty("FIRESTORE_PROJECT_ID")).thenReturn("smart-irrigation-ifmaker");
        when(env.getProperty("GOOGLE_APPLICATION_CREDENTIALS")).thenReturn("./firebase.json");
        when(env.getProperty("DEVICE_API_KEY")).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> FirebaseEnv.fromEnvironment(env));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd test -Dtest=FirebaseEnvTest`
Expected: FAIL — `FirebaseEnv` doesn't exist.

- [ ] **Step 3: Write `FirebaseEnv`**

```java
package com.smartirrigation.smartirrigation.config;

import org.springframework.core.env.Environment;

public record FirebaseEnv(String projectId, String credentialsPath, String deviceApiKey, String corsAllowedOrigins) {

    public static FirebaseEnv fromEnvironment(Environment env) {
        String projectId = require(env, "FIRESTORE_PROJECT_ID");
        String credentialsPath = require(env, "GOOGLE_APPLICATION_CREDENTIALS");
        String deviceApiKey = require(env, "DEVICE_API_KEY");
        String corsAllowedOrigins = env.getProperty("MYAPP_CORS_ALLOWED_ORIGINS", "http://localhost:4200");
        return new FirebaseEnv(projectId, credentialsPath, deviceApiKey, corsAllowedOrigins);
    }

    private static String require(Environment env, String key) {
        String value = env.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variavel de ambiente obrigatoria ausente: " + key);
        }
        return value;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw.cmd test -Dtest=FirebaseEnvTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Write `FirebaseConfig`**

```java
package com.smartirrigation.smartirrigation.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Configuration
public class FirebaseConfig {

    @Bean
    public FirebaseEnv firebaseEnv(Environment env) {
        return FirebaseEnv.fromEnvironment(env);
    }

    @Bean
    public FirebaseApp firebaseApp(FirebaseEnv firebaseEnv) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        try (FileInputStream credentials = new FileInputStream(firebaseEnv.credentialsPath())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .setProjectId(firebaseEnv.projectId())
                    .build();
            return FirebaseApp.initializeApp(options);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao carregar credenciais do Firebase de " + firebaseEnv.credentialsPath(), e);
        }
    }

    @Bean
    public Firestore firestore(FirebaseApp firebaseApp) {
        return FirestoreClient.getFirestore(firebaseApp);
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }
}
```

- [ ] **Step 6: Verify it compiles**

Run: `./mvnw.cmd compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/config/FirebaseEnv.java src/main/java/com/smartirrigation/smartirrigation/config/FirebaseConfig.java src/test/java/com/smartirrigation/smartirrigation/config/FirebaseEnvTest.java
git commit -m "feat: read Firebase config from .env and expose Firestore/FirebaseAuth beans"
```

---

## Task 6: `CommandDispatchService`

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/service/CommandDispatchService.java`
- Test: `src/test/java/com/smartirrigation/smartirrigation/service/CommandDispatchServiceTest.java`

**Interfaces:**
- Produces: `record Command(String type, Map<String, Object> payload)`; `CommandDispatchService.enqueue(String deviceId, Command command)`; `CommandDispatchService.poll(String deviceId, long timeoutMillis) : CompletableFuture<Optional<Command>>`. Consumed by `CommandPollController` (Task 12) and every web command endpoint (Task 13 and the follow-up plan).

- [ ] **Step 1: Write the failing test**

```java
package com.smartirrigation.smartirrigation.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandDispatchServiceTest {

    @Test
    void poll_resolvesImmediately_whenCommandAlreadyEnqueued() throws Exception {
        CommandDispatchService service = new CommandDispatchService();
        CommandDispatchService.Command command =
                new CommandDispatchService.Command("DEFINIR_MODO", Map.of("modo", "automatico"));

        service.enqueue("esp32-01", command);
        Optional<CommandDispatchService.Command> result = service.poll("esp32-01", 1000).get(2, TimeUnit.SECONDS);

        assertTrue(result.isPresent());
        assertEquals("DEFINIR_MODO", result.get().type());
    }

    @Test
    void poll_resolvesWhenCommandArrivesWhileWaiting() throws Exception {
        CommandDispatchService service = new CommandDispatchService();
        CompletableFuture<Optional<CommandDispatchService.Command>> future = service.poll("esp32-01", 5000);

        service.enqueue("esp32-01", new CommandDispatchService.Command("LATITUDE", Map.of("latitude", -23.5)));

        Optional<CommandDispatchService.Command> result = future.get(2, TimeUnit.SECONDS);
        assertTrue(result.isPresent());
        assertEquals("LATITUDE", result.get().type());
    }

    @Test
    void poll_resolvesEmpty_afterTimeout() throws Exception {
        CommandDispatchService service = new CommandDispatchService();

        Optional<CommandDispatchService.Command> result = service.poll("esp32-01", 200).get(2, TimeUnit.SECONDS);

        assertTrue(result.isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd test -Dtest=CommandDispatchServiceTest`
Expected: FAIL — `CommandDispatchService` doesn't exist.

- [ ] **Step 3: Write `CommandDispatchService`**

```java
package com.smartirrigation.smartirrigation.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class CommandDispatchService {

    public record Command(String type, Map<String, Object> payload) {
    }

    private final Map<String, ConcurrentLinkedQueue<CompletableFuture<Optional<Command>>>> waiters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(1);

    public void enqueue(String deviceId, Command command) {
        ConcurrentLinkedQueue<CompletableFuture<Optional<Command>>> queue = waiters.get(deviceId);
        CompletableFuture<Optional<Command>> waiting = (queue == null) ? null : queue.poll();
        if (waiting != null && waiting.complete(Optional.of(command))) {
            return;
        }
        // Nao ha long-poll pendente agora; o proximo poll() buscara este comando via a fila abaixo.
        pending.computeIfAbsent(deviceId, id -> new ConcurrentLinkedQueue<>()).add(command);
    }

    private final Map<String, ConcurrentLinkedQueue<Command>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<Optional<Command>> poll(String deviceId, long timeoutMillis) {
        ConcurrentLinkedQueue<Command> pendingQueue = pending.get(deviceId);
        Command alreadyPending = (pendingQueue == null) ? null : pendingQueue.poll();
        if (alreadyPending != null) {
            return CompletableFuture.completedFuture(Optional.of(alreadyPending));
        }

        CompletableFuture<Optional<Command>> future = new CompletableFuture<>();
        waiters.computeIfAbsent(deviceId, id -> new ConcurrentLinkedQueue<>()).add(future);

        timeoutExecutor.schedule(() -> future.complete(Optional.empty()), timeoutMillis, TimeUnit.MILLISECONDS);
        return future;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw.cmd test -Dtest=CommandDispatchServiceTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/service/CommandDispatchService.java src/test/java/com/smartirrigation/smartirrigation/service/CommandDispatchServiceTest.java
git commit -m "feat: add in-memory command dispatch service backing the ESP32 long-poll"
```

---

## Task 7: Repository layer (Firestore Admin SDK)

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/repository/DeviceRepository.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/repository/TelemetryRepository.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/repository/IrrigationHistoryRepository.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/repository/UsuarioRepository.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/repository/CulturaRepository.java`

**Interfaces:**
- Consumes: `Firestore` bean from Task 5; `Device`, `Telemetry`, `IrrigationHistoryRecord`, `Usuario`, `CoeficienteCultura` from Task 4.
- Produces: `DeviceRepository.findById(String) : Optional<Device>`, `DeviceRepository.save(String, Device) : Device`; `TelemetryRepository.add(String deviceId, Telemetry t)`, `TelemetryRepository.findBetween(String deviceId, Instant from, Instant to, int limit) : List<Telemetry>`; `IrrigationHistoryRepository.add(...)`, `IrrigationHistoryRepository.findBetween(...)`; `UsuarioRepository.findById(String uid) : Optional<Usuario>`; `CulturaRepository.findByName(String cultura) : Optional<CoeficienteCultura>`, `findAll() : List<CoeficienteCultura>`. Consumed by `service/*` in Tasks 11 and 13, and by `security/FirebaseAuthFilter` (Task 8).

No dedicated automated test for this task (see Global Constraints — these are thin Firestore Admin SDK wrappers, verified by manual/integration testing once a real Firestore project is wired up). Compilation is the acceptance bar here.

- [ ] **Step 1: Write `DeviceRepository`**

```java
package com.smartirrigation.smartirrigation.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.smartirrigation.smartirrigation.model.Device;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Repository
public class DeviceRepository {

    private static final String COLLECTION = "devices";

    private final Firestore firestore;

    public DeviceRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Device> findById(String deviceId) {
        try {
            DocumentSnapshot snapshot = firestore.collection(COLLECTION).document(deviceId).get().get();
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            Device device = snapshot.toObject(Device.class);
            if (device != null) {
                device.setDeviceId(deviceId);
            }
            return Optional.ofNullable(device);
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao ler device " + deviceId + " do Firestore", e);
        }
    }

    public Device save(String deviceId, Device device) {
        device.setUpdatedAt(Timestamp.now());
        try {
            firestore.collection(COLLECTION).document(deviceId).set(device).get();
            device.setDeviceId(deviceId);
            return device;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao salvar device " + deviceId + " no Firestore", e);
        }
    }
}
```

- [ ] **Step 2: Write `TelemetryRepository`**

```java
package com.smartirrigation.smartirrigation.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.smartirrigation.smartirrigation.model.Telemetry;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class TelemetryRepository {

    private static final String COLLECTION = "devices";
    private static final String SUBCOLLECTION = "telemetry";

    private final Firestore firestore;

    public TelemetryRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public void add(String deviceId, Telemetry telemetry) {
        telemetry.setRecordedAt(Timestamp.now());
        try {
            firestore.collection(COLLECTION).document(deviceId)
                    .collection(SUBCOLLECTION).document()
                    .set(telemetry)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao gravar telemetria do device " + deviceId, e);
        }
    }

    public List<Telemetry> findBetween(String deviceId, Instant from, Instant to, int limit) {
        Query query = firestore.collection(COLLECTION).document(deviceId).collection(SUBCOLLECTION)
                .whereGreaterThanOrEqualTo("recordedAt", Timestamp.of(java.util.Date.from(from)))
                .whereLessThanOrEqualTo("recordedAt", Timestamp.of(java.util.Date.from(to)))
                .orderBy("recordedAt", Query.Direction.DESCENDING)
                .limit(limit);
        try {
            List<QueryDocumentSnapshot> docs = query.get().get().getDocuments();
            return docs.stream().map(d -> d.toObject(Telemetry.class)).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao consultar telemetria do device " + deviceId, e);
        }
    }
}
```

- [ ] **Step 3: Write `IrrigationHistoryRepository`**

```java
package com.smartirrigation.smartirrigation.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.smartirrigation.smartirrigation.model.IrrigationHistoryRecord;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class IrrigationHistoryRepository {

    private static final String COLLECTION = "devices";
    private static final String SUBCOLLECTION = "irrigationHistory";

    private final Firestore firestore;

    public IrrigationHistoryRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public void add(String deviceId, IrrigationHistoryRecord record) {
        record.setRecordedAt(Timestamp.now());
        try {
            firestore.collection(COLLECTION).document(deviceId)
                    .collection(SUBCOLLECTION).document()
                    .set(record)
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao gravar historico de irrigacao do device " + deviceId, e);
        }
    }

    public List<IrrigationHistoryRecord> findBetween(String deviceId, Instant from, Instant to) {
        Query query = firestore.collection(COLLECTION).document(deviceId).collection(SUBCOLLECTION)
                .whereGreaterThanOrEqualTo("recordedAt", Timestamp.of(java.util.Date.from(from)))
                .whereLessThanOrEqualTo("recordedAt", Timestamp.of(java.util.Date.from(to)))
                .orderBy("recordedAt", Query.Direction.DESCENDING);
        try {
            List<QueryDocumentSnapshot> docs = query.get().get().getDocuments();
            return docs.stream().map(d -> d.toObject(IrrigationHistoryRecord.class)).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao consultar historico de irrigacao do device " + deviceId, e);
        }
    }
}
```

- [ ] **Step 4: Write `UsuarioRepository`**

```java
package com.smartirrigation.smartirrigation.repository;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.smartirrigation.smartirrigation.model.Usuario;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Repository
public class UsuarioRepository {

    private static final String COLLECTION = "usuarios";

    private final Firestore firestore;

    public UsuarioRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<Usuario> findById(String uid) {
        try {
            DocumentSnapshot snapshot = firestore.collection(COLLECTION).document(uid).get().get();
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            return Optional.ofNullable(snapshot.toObject(Usuario.class));
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao ler usuario " + uid + " do Firestore", e);
        }
    }

    public Usuario save(String uid, Usuario usuario) {
        try {
            firestore.collection(COLLECTION).document(uid).set(usuario).get();
            return usuario;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao salvar usuario " + uid + " no Firestore", e);
        }
    }
}
```

- [ ] **Step 5: Write `CulturaRepository`**

```java
package com.smartirrigation.smartirrigation.repository;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.smartirrigation.smartirrigation.model.CoeficienteCultura;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Repository
public class CulturaRepository {

    private static final String COLLECTION = "coeficientes";

    private final Firestore firestore;

    public CulturaRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<CoeficienteCultura> findByName(String cultura) {
        try {
            DocumentSnapshot snapshot = firestore.collection(COLLECTION).document(cultura).get().get();
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            return Optional.ofNullable(snapshot.toObject(CoeficienteCultura.class));
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao ler cultura " + cultura + " do Firestore", e);
        }
    }

    public List<CoeficienteCultura> findAll() {
        try {
            List<QueryDocumentSnapshot> docs = firestore.collection(COLLECTION).get().get().getDocuments();
            return docs.stream().map(d -> d.toObject(CoeficienteCultura.class)).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao listar culturas", e);
        }
    }
}
```

- [ ] **Step 6: Verify it compiles**

Run: `./mvnw.cmd compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/repository
git commit -m "feat: add Firestore repositories for device, telemetry, history, usuario, cultura"
```

---

## Task 8: `FirebaseAuthFilter`

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/security/FirebaseAuthFilter.java`
- Test: `src/test/java/com/smartirrigation/smartirrigation/security/FirebaseAuthFilterTest.java`

**Interfaces:**
- Consumes: `FirebaseAuth` bean (Task 5), `UsuarioRepository` (Task 7).
- Produces: `FirebaseAuthFilter` (`OncePerRequestFilter`) that, for any path NOT under `/api/webhooks/**` or matching `/api/devices/*/config` or `/api/devices/*/commands/poll`, requires `Authorization: Bearer <idToken>`, verifies it, loads the role from `usuarios/{uid}.tipo`, and sets a Spring Security `Authentication` with authority `ROLE_ADMIN` (when `tipo` equals `"Administrador"`) or `ROLE_USER` otherwise. Consumed by `SecurityConfig` (Task 10).

- [ ] **Step 1: Write the failing test**

```java
package com.smartirrigation.smartirrigation.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.smartirrigation.smartirrigation.model.Usuario;
import com.smartirrigation.smartirrigation.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FirebaseAuthFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_setsAdminAuthority_whenTipoIsAdministrador() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        FirebaseToken token = mock(FirebaseToken.class);
        FilterChain chain = mock(FilterChain.class);

        when(firebaseAuth.verifyIdToken("valid-token")).thenReturn(token);
        when(token.getUid()).thenReturn("uid-123");
        Usuario usuario = new Usuario();
        usuario.setTipo("Administrador");
        when(usuarioRepository.findById("uid-123")).thenReturn(Optional.of(usuario));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices/esp32-01/status");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository).doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_returns401_whenAuthorizationHeaderMissing() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices/esp32-01/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository).doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_skipsDeviceWebhookPaths() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/telemetry");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(firebaseAuth);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd test -Dtest=FirebaseAuthFilterTest`
Expected: FAIL — `FirebaseAuthFilter` doesn't exist.

- [ ] **Step 3: Write `FirebaseAuthFilter`**

```java
package com.smartirrigation.smartirrigation.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.smartirrigation.smartirrigation.model.Usuario;
import com.smartirrigation.smartirrigation.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class FirebaseAuthFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> DEVICE_PATHS = List.of(
            "/api/webhooks/**",
            "/api/devices/*/config",
            "/api/devices/*/commands/poll"
    );

    private final FirebaseAuth firebaseAuth;
    private final UsuarioRepository usuarioRepository;

    public FirebaseAuthFilter(FirebaseAuth firebaseAuth, UsuarioRepository usuarioRepository) {
        this.firebaseAuth = firebaseAuth;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (isDevicePath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token de autenticacao ausente");
            return;
        }

        try {
            FirebaseToken token = firebaseAuth.verifyIdToken(header.substring("Bearer ".length()));
            String uid = token.getUid();
            String tipo = usuarioRepository.findById(uid).map(Usuario::getTipo).orElse("");

            GrantedAuthority authority = new SimpleGrantedAuthority(
                    "Administrador".equals(tipo) ? "ROLE_ADMIN" : "ROLE_USER");

            var authentication = new UsernamePasswordAuthenticationToken(uid, null, List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token de autenticacao invalido");
        }
    }

    private boolean isDevicePath(String uri) {
        return DEVICE_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw.cmd test -Dtest=FirebaseAuthFilterTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/security/FirebaseAuthFilter.java src/test/java/com/smartirrigation/smartirrigation/security/FirebaseAuthFilterTest.java
git commit -m "feat: verify Firebase ID tokens and map usuario.tipo to Spring Security roles"
```

---

## Task 9: `DeviceKeyFilter`

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/security/DeviceKeyFilter.java`
- Test: `src/test/java/com/smartirrigation/smartirrigation/security/DeviceKeyFilterTest.java`

**Interfaces:**
- Consumes: `FirebaseEnv` (Task 5) for the expected key.
- Produces: `DeviceKeyFilter` (`OncePerRequestFilter`) that, only for `/api/webhooks/**`, `/api/devices/*/config`, `/api/devices/*/commands/poll`, requires header `X-Device-Key` to equal `FirebaseEnv.deviceApiKey()`; other paths pass through untouched. Consumed by `SecurityConfig` (Task 10).

- [ ] **Step 1: Write the failing test**

```java
package com.smartirrigation.smartirrigation.security;

import com.smartirrigation.smartirrigation.config.FirebaseEnv;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;

class DeviceKeyFilterTest {

    private final FirebaseEnv env = new FirebaseEnv("proj", "./creds.json", "chave-secreta", "http://localhost:4200");

    @Test
    void doFilter_passesThrough_whenKeyMatches() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/telemetry");
        request.addHeader("X-Device-Key", "chave-secreta");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new DeviceKeyFilter(env).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_returns401_whenKeyMissingOrWrong() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/telemetry");
        request.addHeader("X-Device-Key", "chave-errada");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new DeviceKeyFilter(env).doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_ignoresNonDevicePaths() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/devices/esp32-01/status");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new DeviceKeyFilter(env).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd test -Dtest=DeviceKeyFilterTest`
Expected: FAIL — `DeviceKeyFilter` doesn't exist.

- [ ] **Step 3: Write `DeviceKeyFilter`**

```java
package com.smartirrigation.smartirrigation.security;

import com.smartirrigation.smartirrigation.config.FirebaseEnv;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class DeviceKeyFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> DEVICE_PATHS = List.of(
            "/api/webhooks/**",
            "/api/devices/*/config",
            "/api/devices/*/commands/poll"
    );

    private final FirebaseEnv firebaseEnv;

    public DeviceKeyFilter(FirebaseEnv firebaseEnv) {
        this.firebaseEnv = firebaseEnv;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!isDevicePath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getHeader("X-Device-Key");
        if (key == null || !key.equals(firebaseEnv.deviceApiKey())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "X-Device-Key ausente ou invalida");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isDevicePath(String uri) {
        return DEVICE_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw.cmd test -Dtest=DeviceKeyFilterTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/security/DeviceKeyFilter.java src/test/java/com/smartirrigation/smartirrigation/security/DeviceKeyFilterTest.java
git commit -m "feat: enforce X-Device-Key on ESP32-facing endpoints"
```

---

## Task 10: `SecurityConfig`, `WebConfig`, `OpenApiConfig`

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/security/SecurityConfig.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/config/WebConfig.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/config/OpenApiConfig.java`

**Interfaces:**
- Consumes: `FirebaseAuthFilter` (Task 8), `DeviceKeyFilter` (Task 9), `FirebaseAuth`/`UsuarioRepository`/`FirebaseEnv` beans.
- Produces: `SecurityFilterChain` bean wiring both filters ahead of `UsernamePasswordAuthenticationFilter`, permitting device paths and requiring authentication on everything else under `/api/**`; CORS configuration reading `myapp.cors.allowed-origins`.

- [ ] **Step 1: Write `SecurityConfig`**

```java
package com.smartirrigation.smartirrigation.security;

import com.google.firebase.auth.FirebaseAuth;
import com.smartirrigation.smartirrigation.config.FirebaseEnv;
import com.smartirrigation.smartirrigation.repository.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, FirebaseAuth firebaseAuth,
                                                     UsuarioRepository usuarioRepository, FirebaseEnv firebaseEnv) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/webhooks/**", "/api/devices/*/config", "/api/devices/*/commands/poll").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new DeviceKeyFilter(firebaseEnv), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new FirebaseAuthFilter(firebaseAuth, usuarioRepository), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

- [ ] **Step 2: Write `WebConfig`**

```java
package com.smartirrigation.smartirrigation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(FirebaseEnv firebaseEnv) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(firebaseEnv.corsAllowedOrigins().split(","))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
```

- [ ] **Step 3: Write `OpenApiConfig`**

```java
package com.smartirrigation.smartirrigation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartIrrigation API")
                        .version("v1")
                        .description("API ponte entre o front-end Angular, o Firebase e o ESP32 do SmartIrrigation."));
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./mvnw.cmd compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/security/SecurityConfig.java src/main/java/com/smartirrigation/smartirrigation/config/WebConfig.java src/main/java/com/smartirrigation/smartirrigation/config/OpenApiConfig.java
git commit -m "feat: wire device-key and firebase-auth filters into Spring Security, add CORS and OpenAPI config"
```

---

## Task 11: Device webhook + config DTOs, service, controllers

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/dto/TelemetryWebhookRequestDTO.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/dto/StatusWebhookRequestDTO.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/dto/HistoryWebhookRequestDTO.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/dto/ConfigIrrigacaoDTO.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/dto/DeviceConfigResponseDTO.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/service/DeviceService.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/controller/device/DeviceWebhookController.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/controller/device/DeviceConfigController.java`
- Test: `src/test/java/com/smartirrigation/smartirrigation/service/DeviceServiceTest.java`

**Interfaces:**
- Consumes: `DeviceRepository`, `TelemetryRepository`, `IrrigationHistoryRepository` (Task 7); `Device`, `Telemetry`, `IrrigationHistoryRecord`, `ConfigIrrigacao` (Task 4).
- Produces: `DeviceService.recordTelemetry(String deviceId, TelemetryWebhookRequestDTO)`, `DeviceService.updateStatus(String deviceId, StatusWebhookRequestDTO)`, `DeviceService.recordHistory(String deviceId, HistoryWebhookRequestDTO)`, `DeviceService.getConfig(String deviceId) : DeviceConfigResponseDTO`. Consumed by `DeviceWebhookController`, `DeviceConfigController`, and (for `getConfig`/status reads) by Task 13's `StatusController`.

- [ ] **Step 1: Write the failing test**

```java
package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.StatusWebhookRequestDTO;
import com.smartirrigation.smartirrigation.dto.TelemetryWebhookRequestDTO;
import com.smartirrigation.smartirrigation.model.Device;
import com.smartirrigation.smartirrigation.model.Telemetry;
import com.smartirrigation.smartirrigation.repository.DeviceRepository;
import com.smartirrigation.smartirrigation.repository.IrrigationHistoryRepository;
import com.smartirrigation.smartirrigation.repository.TelemetryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DeviceServiceTest {

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final TelemetryRepository telemetryRepository = mock(TelemetryRepository.class);
    private final IrrigationHistoryRepository irrigationHistoryRepository = mock(IrrigationHistoryRepository.class);
    private final DeviceService service = new DeviceService(deviceRepository, telemetryRepository, irrigationHistoryRepository);

    @Test
    void recordTelemetry_savesTemperatureHumiditySoilPercent() {
        TelemetryWebhookRequestDTO dto = new TelemetryWebhookRequestDTO();
        dto.setDeviceId("esp32-01");
        dto.setTemperature(24.5);
        dto.setHumidity(60.0);
        dto.setSoilPercent(45.0);
        dto.setTimestamp(1_700_000_000L);

        service.recordTelemetry("esp32-01", dto);

        ArgumentCaptor<Telemetry> captor = ArgumentCaptor.forClass(Telemetry.class);
        verify(telemetryRepository).add(eq("esp32-01"), captor.capture());
        assertEquals(24.5, captor.getValue().getTemperature());
        assertEquals(45.0, captor.getValue().getSoilPercent());
    }

    @Test
    void updateStatus_upsertsDeviceFields() {
        when(deviceRepository.findById("esp32-01")).thenReturn(Optional.of(new Device()));

        StatusWebhookRequestDTO dto = new StatusWebhookRequestDTO();
        dto.setDeviceId("esp32-01");
        dto.setModo("automatico");
        dto.setAutorizado(true);
        dto.setQuantidadeAgua(5.0);
        dto.setTempMin(18.0);
        dto.setTempMax(30.0);
        dto.setEmExecucao(false);
        dto.setLatitude(-23.5);

        service.updateStatus("esp32-01", dto);

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(eq("esp32-01"), captor.capture());
        assertEquals("automatico", captor.getValue().getModo());
        assertEquals(true, captor.getValue().getAutorizado());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd test -Dtest=DeviceServiceTest`
Expected: FAIL — DTOs and `DeviceService` don't exist.

- [ ] **Step 3: Write the webhook request DTOs**

```java
package com.smartirrigation.smartirrigation.dto;

public class TelemetryWebhookRequestDTO {
    private String deviceId;
    private Double temperature;
    private Double humidity;
    private Double soilPercent;
    private Long timestamp;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Double getHumidity() { return humidity; }
    public void setHumidity(Double humidity) { this.humidity = humidity; }
    public Double getSoilPercent() { return soilPercent; }
    public void setSoilPercent(Double soilPercent) { this.soilPercent = soilPercent; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
```

```java
package com.smartirrigation.smartirrigation.dto;

public class StatusWebhookRequestDTO {
    private String deviceId;
    private String modo;
    private Boolean autorizado;
    private Double quantidadeAgua;
    private Double tempMin;
    private Double tempMax;
    private Boolean emExecucao;
    private Double latitude;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }
    public Boolean getAutorizado() { return autorizado; }
    public void setAutorizado(Boolean autorizado) { this.autorizado = autorizado; }
    public Double getQuantidadeAgua() { return quantidadeAgua; }
    public void setQuantidadeAgua(Double quantidadeAgua) { this.quantidadeAgua = quantidadeAgua; }
    public Double getTempMin() { return tempMin; }
    public void setTempMin(Double tempMin) { this.tempMin = tempMin; }
    public Double getTempMax() { return tempMax; }
    public void setTempMax(Double tempMax) { this.tempMax = tempMax; }
    public Boolean getEmExecucao() { return emExecucao; }
    public void setEmExecucao(Boolean emExecucao) { this.emExecucao = emExecucao; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
}
```

```java
package com.smartirrigation.smartirrigation.dto;

public class HistoryWebhookRequestDTO {
    private String deviceId;
    private Double umidadeAtual;
    private String tempo;
    private Double volumePorPlanta;
    private Double volumeTotal;
    private String data;
    private String hora;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Double getUmidadeAtual() { return umidadeAtual; }
    public void setUmidadeAtual(Double umidadeAtual) { this.umidadeAtual = umidadeAtual; }
    public String getTempo() { return tempo; }
    public void setTempo(String tempo) { this.tempo = tempo; }
    public Double getVolumePorPlanta() { return volumePorPlanta; }
    public void setVolumePorPlanta(Double volumePorPlanta) { this.volumePorPlanta = volumePorPlanta; }
    public Double getVolumeTotal() { return volumeTotal; }
    public void setVolumeTotal(Double volumeTotal) { this.volumeTotal = volumeTotal; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public String getHora() { return hora; }
    public void setHora(String hora) { this.hora = hora; }
}
```

```java
package com.smartirrigation.smartirrigation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConfigIrrigacaoDTO {
    private Double kc;
    @JsonProperty("espacamento_plantas_m")
    private Double espacamentoPlantasM;
    @JsonProperty("espacamento_linhas_m")
    private Double espacamentoLinhasM;
    @JsonProperty("vazao_gotejador_lph")
    private Double vazaoGotejadorLph;
    @JsonProperty("num_gotejadores_planta")
    private Integer numGotejadoresPlanta;
    @JsonProperty("numero_total_plantas")
    private Integer numeroTotalPlantas;
    @JsonProperty("limiar_umidade_minima")
    private Double limiarUmidadeMinima;

    public Double getKc() { return kc; }
    public void setKc(Double kc) { this.kc = kc; }
    public Double getEspacamentoPlantasM() { return espacamentoPlantasM; }
    public void setEspacamentoPlantasM(Double espacamentoPlantasM) { this.espacamentoPlantasM = espacamentoPlantasM; }
    public Double getEspacamentoLinhasM() { return espacamentoLinhasM; }
    public void setEspacamentoLinhasM(Double espacamentoLinhasM) { this.espacamentoLinhasM = espacamentoLinhasM; }
    public Double getVazaoGotejadorLph() { return vazaoGotejadorLph; }
    public void setVazaoGotejadorLph(Double vazaoGotejadorLph) { this.vazaoGotejadorLph = vazaoGotejadorLph; }
    public Integer getNumGotejadoresPlanta() { return numGotejadoresPlanta; }
    public void setNumGotejadoresPlanta(Integer numGotejadoresPlanta) { this.numGotejadoresPlanta = numGotejadoresPlanta; }
    public Integer getNumeroTotalPlantas() { return numeroTotalPlantas; }
    public void setNumeroTotalPlantas(Integer numeroTotalPlantas) { this.numeroTotalPlantas = numeroTotalPlantas; }
    public Double getLimiarUmidadeMinima() { return limiarUmidadeMinima; }
    public void setLimiarUmidadeMinima(Double limiarUmidadeMinima) { this.limiarUmidadeMinima = limiarUmidadeMinima; }
}
```

```java
package com.smartirrigation.smartirrigation.dto;

public class DeviceConfigResponseDTO {
    private String modo;
    private Boolean autorizado;
    private Double quantidadeAgua;
    private Double latitude;
    private ConfigIrrigacaoDTO configIrrigacao;

    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }
    public Boolean getAutorizado() { return autorizado; }
    public void setAutorizado(Boolean autorizado) { this.autorizado = autorizado; }
    public Double getQuantidadeAgua() { return quantidadeAgua; }
    public void setQuantidadeAgua(Double quantidadeAgua) { this.quantidadeAgua = quantidadeAgua; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public ConfigIrrigacaoDTO getConfigIrrigacao() { return configIrrigacao; }
    public void setConfigIrrigacao(ConfigIrrigacaoDTO configIrrigacao) { this.configIrrigacao = configIrrigacao; }
}
```

- [ ] **Step 4: Write `DeviceService`**

```java
package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.*;
import com.smartirrigation.smartirrigation.exception.NotFoundException;
import com.smartirrigation.smartirrigation.model.Device;
import com.smartirrigation.smartirrigation.model.IrrigationHistoryRecord;
import com.smartirrigation.smartirrigation.model.Telemetry;
import com.smartirrigation.smartirrigation.repository.DeviceRepository;
import com.smartirrigation.smartirrigation.repository.IrrigationHistoryRepository;
import com.smartirrigation.smartirrigation.repository.TelemetryRepository;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final TelemetryRepository telemetryRepository;
    private final IrrigationHistoryRepository irrigationHistoryRepository;

    public DeviceService(DeviceRepository deviceRepository, TelemetryRepository telemetryRepository,
                          IrrigationHistoryRepository irrigationHistoryRepository) {
        this.deviceRepository = deviceRepository;
        this.telemetryRepository = telemetryRepository;
        this.irrigationHistoryRepository = irrigationHistoryRepository;
    }

    public void recordTelemetry(String deviceId, TelemetryWebhookRequestDTO dto) {
        Telemetry telemetry = new Telemetry();
        telemetry.setTemperature(dto.getTemperature());
        telemetry.setHumidity(dto.getHumidity());
        telemetry.setSoilPercent(dto.getSoilPercent());
        telemetry.setTimestamp(dto.getTimestamp());
        telemetryRepository.add(deviceId, telemetry);
    }

    public void updateStatus(String deviceId, StatusWebhookRequestDTO dto) {
        Device device = deviceRepository.findById(deviceId).orElseGet(Device::new);
        device.setModo(dto.getModo());
        device.setAutorizado(dto.getAutorizado());
        device.setQuantidadeAgua(dto.getQuantidadeAgua());
        device.setTempMin(dto.getTempMin());
        device.setTempMax(dto.getTempMax());
        device.setEmExecucao(dto.getEmExecucao());
        device.setLatitude(dto.getLatitude());
        deviceRepository.save(deviceId, device);
    }

    public void recordHistory(String deviceId, HistoryWebhookRequestDTO dto) {
        IrrigationHistoryRecord record = new IrrigationHistoryRecord();
        record.setUmidadeAtual(dto.getUmidadeAtual());
        record.setTempo(dto.getTempo());
        record.setVolumePorPlanta(dto.getVolumePorPlanta());
        record.setVolumeTotal(dto.getVolumeTotal());
        record.setData(dto.getData());
        record.setHora(dto.getHora());
        irrigationHistoryRepository.add(deviceId, record);
    }

    public DeviceConfigResponseDTO getConfig(String deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device nao encontrado: " + deviceId));

        DeviceConfigResponseDTO response = new DeviceConfigResponseDTO();
        response.setModo(device.getModo());
        response.setAutorizado(device.getAutorizado());
        response.setQuantidadeAgua(device.getQuantidadeAgua());
        response.setLatitude(device.getLatitude());

        ConfigIrrigacaoDTO configDto = new ConfigIrrigacaoDTO();
        configDto.setKc(device.getConfigIrrigacao().getKc());
        configDto.setEspacamentoPlantasM(device.getConfigIrrigacao().getEspacamentoPlantasM());
        configDto.setEspacamentoLinhasM(device.getConfigIrrigacao().getEspacamentoLinhasM());
        configDto.setVazaoGotejadorLph(device.getConfigIrrigacao().getVazaoGotejadorLph());
        configDto.setNumGotejadoresPlanta(device.getConfigIrrigacao().getNumGotejadoresPlanta());
        configDto.setNumeroTotalPlantas(device.getConfigIrrigacao().getNumeroTotalPlantas());
        configDto.setLimiarUmidadeMinima(device.getConfigIrrigacao().getLimiarUmidadeMinima());
        response.setConfigIrrigacao(configDto);

        return response;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw.cmd test -Dtest=DeviceServiceTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6: Write `DeviceWebhookController`**

```java
package com.smartirrigation.smartirrigation.controller.device;

import com.smartirrigation.smartirrigation.dto.HistoryWebhookRequestDTO;
import com.smartirrigation.smartirrigation.dto.StatusWebhookRequestDTO;
import com.smartirrigation.smartirrigation.dto.TelemetryWebhookRequestDTO;
import com.smartirrigation.smartirrigation.service.DeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Device Webhooks", description = "Endpoints que o firmware do ESP32 chama via POST")
public class DeviceWebhookController {

    private final DeviceService deviceService;

    public DeviceWebhookController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/telemetry")
    public ResponseEntity<Void> telemetry(@RequestBody TelemetryWebhookRequestDTO dto) {
        deviceService.recordTelemetry(dto.getDeviceId(), dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/status")
    public ResponseEntity<Void> status(@RequestBody StatusWebhookRequestDTO dto) {
        deviceService.updateStatus(dto.getDeviceId(), dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/history")
    public ResponseEntity<Void> history(@RequestBody HistoryWebhookRequestDTO dto) {
        deviceService.recordHistory(dto.getDeviceId(), dto);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 7: Write `DeviceConfigController`**

```java
package com.smartirrigation.smartirrigation.controller.device;

import com.smartirrigation.smartirrigation.dto.DeviceConfigResponseDTO;
import com.smartirrigation.smartirrigation.service.DeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices/{deviceId}")
@Tag(name = "Device Config", description = "Configuracao inicial buscada pelo ESP32 no boot")
public class DeviceConfigController {

    private final DeviceService deviceService;

    public DeviceConfigController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/config")
    public ResponseEntity<DeviceConfigResponseDTO> getConfig(@PathVariable String deviceId) {
        return ResponseEntity.ok(deviceService.getConfig(deviceId));
    }
}
```

- [ ] **Step 8: Verify full build passes**

Run: `./mvnw.cmd test -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/dto src/main/java/com/smartirrigation/smartirrigation/service/DeviceService.java src/main/java/com/smartirrigation/smartirrigation/controller/device src/test/java/com/smartirrigation/smartirrigation/service/DeviceServiceTest.java
git commit -m "feat: implement ESP32 webhook and config endpoints preserving the existing firmware contract"
```

---

## Task 12: `CommandPollController`

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/dto/CommandResponseDTO.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/controller/device/CommandPollController.java`

**Interfaces:**
- Consumes: `CommandDispatchService` (Task 6).
- Produces: `GET /api/devices/{deviceId}/commands/poll` returning `200` + `{type, payload}` when a command is available, or `204` after a 30s timeout — matching `CommandPoller::pollLoop` in the `.ino` exactly.

- [ ] **Step 1: Write `CommandResponseDTO`**

```java
package com.smartirrigation.smartirrigation.dto;

import java.util.Map;

public class CommandResponseDTO {
    private String type;
    private Map<String, Object> payload;

    public CommandResponseDTO() {
    }

    public CommandResponseDTO(String type, Map<String, Object> payload) {
        this.type = type;
        this.payload = payload;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
}
```

- [ ] **Step 2: Write `CommandPollController`**

```java
package com.smartirrigation.smartirrigation.controller.device;

import com.smartirrigation.smartirrigation.dto.CommandResponseDTO;
import com.smartirrigation.smartirrigation.service.CommandDispatchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/api/devices/{deviceId}")
@Tag(name = "Device Commands", description = "Long-polling de comandos consumido pelo ESP32")
public class CommandPollController {

    private static final long POLL_TIMEOUT_MILLIS = 30_000;

    private final CommandDispatchService commandDispatchService;

    public CommandPollController(CommandDispatchService commandDispatchService) {
        this.commandDispatchService = commandDispatchService;
    }

    @GetMapping("/commands/poll")
    public DeferredResult<ResponseEntity<CommandResponseDTO>> poll(@PathVariable String deviceId) {
        DeferredResult<ResponseEntity<CommandResponseDTO>> deferredResult =
                new DeferredResult<>(POLL_TIMEOUT_MILLIS + 5000, ResponseEntity.noContent().build());

        commandDispatchService.poll(deviceId, POLL_TIMEOUT_MILLIS).thenAccept(maybeCommand ->
                deferredResult.setResult(maybeCommand
                        .map(command -> ResponseEntity.ok(new CommandResponseDTO(command.type(), command.payload())))
                        .orElseGet(() -> ResponseEntity.noContent().build())));

        return deferredResult;
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./mvnw.cmd compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/dto/CommandResponseDTO.java src/main/java/com/smartirrigation/smartirrigation/controller/device/CommandPollController.java
git commit -m "feat: implement long-polling command endpoint for the ESP32"
```

---

## Task 13: Web vertical slice — status + config de irrigação

**Files:**
- Create: `src/main/java/com/smartirrigation/smartirrigation/dto/DeviceStatusResponseDTO.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/service/ConfigIrrigacaoService.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/controller/web/StatusController.java`
- Create: `src/main/java/com/smartirrigation/smartirrigation/controller/web/ConfigIrrigacaoController.java`
- Test: `src/test/java/com/smartirrigation/smartirrigation/service/ConfigIrrigacaoServiceTest.java`

**Interfaces:**
- Consumes: `DeviceRepository` (Task 7), `CommandDispatchService` (Task 6), `ConfigIrrigacaoDTO` (Task 11).
- Produces: `GET /api/devices/{deviceId}/status` (any authenticated user), `GET/PUT /api/devices/{deviceId}/config-irrigacao` (`GET` any user, `PUT` admin only) — the template every remaining web command endpoint in the follow-up plan replicates: read/update `devices/{deviceId}` in Firestore, then enqueue the matching command via `CommandDispatchService` so the change reaches the ESP32 on its next poll.

- [ ] **Step 1: Write the failing test**

```java
package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.ConfigIrrigacaoDTO;
import com.smartirrigation.smartirrigation.model.ConfigIrrigacao;
import com.smartirrigation.smartirrigation.model.Device;
import com.smartirrigation.smartirrigation.repository.DeviceRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConfigIrrigacaoServiceTest {

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final CommandDispatchService commandDispatchService = mock(CommandDispatchService.class);
    private final ConfigIrrigacaoService service = new ConfigIrrigacaoService(deviceRepository, commandDispatchService);

    @Test
    void getConfig_mapsDeviceFieldsToDto() {
        Device device = new Device();
        device.getConfigIrrigacao().setKc(1.4);
        when(deviceRepository.findById("esp32-01")).thenReturn(Optional.of(device));

        ConfigIrrigacaoDTO result = service.getConfig("esp32-01");

        assertEquals(1.4, result.getKc());
    }

    @Test
    void updateConfig_savesDeviceAndEnqueuesCommand() {
        Device device = new Device();
        when(deviceRepository.findById("esp32-01")).thenReturn(Optional.of(device));

        ConfigIrrigacaoDTO dto = new ConfigIrrigacaoDTO();
        dto.setKc(1.6);
        dto.setLimiarUmidadeMinima(40.0);

        service.updateConfig("esp32-01", dto);

        verify(deviceRepository).save(eq("esp32-01"), any(Device.class));
        verify(commandDispatchService).enqueue(eq("esp32-01"), argThat(command ->
                command.type().equals("CONFIG_IRRIGACAO")
                        && command.payload().get("limiar_umidade_minima").equals(40.0)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd test -Dtest=ConfigIrrigacaoServiceTest`
Expected: FAIL — `ConfigIrrigacaoService` doesn't exist.

- [ ] **Step 3: Write `DeviceStatusResponseDTO`**

```java
package com.smartirrigation.smartirrigation.dto;

public class DeviceStatusResponseDTO {
    private String modo;
    private Boolean autorizado;
    private Double quantidadeAgua;
    private Double tempMin;
    private Double tempMax;
    private Boolean emExecucao;
    private Double latitude;

    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }
    public Boolean getAutorizado() { return autorizado; }
    public void setAutorizado(Boolean autorizado) { this.autorizado = autorizado; }
    public Double getQuantidadeAgua() { return quantidadeAgua; }
    public void setQuantidadeAgua(Double quantidadeAgua) { this.quantidadeAgua = quantidadeAgua; }
    public Double getTempMin() { return tempMin; }
    public void setTempMin(Double tempMin) { this.tempMin = tempMin; }
    public Double getTempMax() { return tempMax; }
    public void setTempMax(Double tempMax) { this.tempMax = tempMax; }
    public Boolean getEmExecucao() { return emExecucao; }
    public void setEmExecucao(Boolean emExecucao) { this.emExecucao = emExecucao; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
}
```

- [ ] **Step 4: Write `ConfigIrrigacaoService`**

```java
package com.smartirrigation.smartirrigation.service;

import com.smartirrigation.smartirrigation.dto.ConfigIrrigacaoDTO;
import com.smartirrigation.smartirrigation.exception.NotFoundException;
import com.smartirrigation.smartirrigation.model.ConfigIrrigacao;
import com.smartirrigation.smartirrigation.model.Device;
import com.smartirrigation.smartirrigation.repository.DeviceRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ConfigIrrigacaoService {

    private final DeviceRepository deviceRepository;
    private final CommandDispatchService commandDispatchService;

    public ConfigIrrigacaoService(DeviceRepository deviceRepository, CommandDispatchService commandDispatchService) {
        this.deviceRepository = deviceRepository;
        this.commandDispatchService = commandDispatchService;
    }

    public ConfigIrrigacaoDTO getConfig(String deviceId) {
        Device device = findDevice(deviceId);
        ConfigIrrigacao config = device.getConfigIrrigacao();

        ConfigIrrigacaoDTO dto = new ConfigIrrigacaoDTO();
        dto.setKc(config.getKc());
        dto.setEspacamentoPlantasM(config.getEspacamentoPlantasM());
        dto.setEspacamentoLinhasM(config.getEspacamentoLinhasM());
        dto.setVazaoGotejadorLph(config.getVazaoGotejadorLph());
        dto.setNumGotejadoresPlanta(config.getNumGotejadoresPlanta());
        dto.setNumeroTotalPlantas(config.getNumeroTotalPlantas());
        dto.setLimiarUmidadeMinima(config.getLimiarUmidadeMinima());
        return dto;
    }

    public void updateConfig(String deviceId, ConfigIrrigacaoDTO dto) {
        Device device = findDevice(deviceId);
        ConfigIrrigacao config = device.getConfigIrrigacao();

        config.setKc(dto.getKc());
        config.setEspacamentoPlantasM(dto.getEspacamentoPlantasM());
        config.setEspacamentoLinhasM(dto.getEspacamentoLinhasM());
        config.setVazaoGotejadorLph(dto.getVazaoGotejadorLph());
        config.setNumGotejadoresPlanta(dto.getNumGotejadoresPlanta());
        config.setNumeroTotalPlantas(dto.getNumeroTotalPlantas());
        config.setLimiarUmidadeMinima(dto.getLimiarUmidadeMinima());

        deviceRepository.save(deviceId, device);

        Map<String, Object> payload = new HashMap<>();
        payload.put("kc", dto.getKc());
        payload.put("espacamento_plantas_m", dto.getEspacamentoPlantasM());
        payload.put("espacamento_linhas_m", dto.getEspacamentoLinhasM());
        payload.put("vazao_gotejador_lph", dto.getVazaoGotejadorLph());
        payload.put("num_gotejadores_planta", dto.getNumGotejadoresPlanta());
        payload.put("numero_total_plantas", dto.getNumeroTotalPlantas());
        payload.put("limiar_umidade_minima", dto.getLimiarUmidadeMinima());

        commandDispatchService.enqueue(deviceId, new CommandDispatchService.Command("CONFIG_IRRIGACAO", payload));
    }

    private Device findDevice(String deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device nao encontrado: " + deviceId));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw.cmd test -Dtest=ConfigIrrigacaoServiceTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6: Write `StatusController`**

```java
package com.smartirrigation.smartirrigation.controller.web;

import com.smartirrigation.smartirrigation.dto.DeviceConfigResponseDTO;
import com.smartirrigation.smartirrigation.dto.DeviceStatusResponseDTO;
import com.smartirrigation.smartirrigation.service.DeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices/{deviceId}")
@Tag(name = "Status", description = "Snapshot atual do device para o dashboard web")
public class StatusController {

    private final DeviceService deviceService;

    public StatusController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/status")
    public ResponseEntity<DeviceStatusResponseDTO> getStatus(@PathVariable String deviceId) {
        DeviceConfigResponseDTO config = deviceService.getConfig(deviceId);

        DeviceStatusResponseDTO status = new DeviceStatusResponseDTO();
        status.setModo(config.getModo());
        status.setAutorizado(config.getAutorizado());
        status.setQuantidadeAgua(config.getQuantidadeAgua());
        status.setLatitude(config.getLatitude());
        return ResponseEntity.ok(status);
    }
}
```

- [ ] **Step 7: Write `ConfigIrrigacaoController`**

```java
package com.smartirrigation.smartirrigation.controller.web;

import com.smartirrigation.smartirrigation.dto.ConfigIrrigacaoDTO;
import com.smartirrigation.smartirrigation.service.ConfigIrrigacaoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices/{deviceId}/config-irrigacao")
@Tag(name = "Config Irrigacao", description = "Parametros agronomicos usados no calculo de ETo")
public class ConfigIrrigacaoController {

    private final ConfigIrrigacaoService configIrrigacaoService;

    public ConfigIrrigacaoController(ConfigIrrigacaoService configIrrigacaoService) {
        this.configIrrigacaoService = configIrrigacaoService;
    }

    @GetMapping
    public ResponseEntity<ConfigIrrigacaoDTO> getConfig(@PathVariable String deviceId) {
        return ResponseEntity.ok(configIrrigacaoService.getConfig(deviceId));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateConfig(@PathVariable String deviceId, @RequestBody ConfigIrrigacaoDTO dto) {
        configIrrigacaoService.updateConfig(deviceId, dto);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 8: Enable method security**

Add `@EnableMethodSecurity` to `SecurityConfig` (Task 10) so `@PreAuthorize` is honored:

```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
```

(import `org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity`)

- [ ] **Step 9: Verify full build passes**

Run: `./mvnw.cmd test -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/smartirrigation/smartirrigation/dto/DeviceStatusResponseDTO.java src/main/java/com/smartirrigation/smartirrigation/service/ConfigIrrigacaoService.java src/main/java/com/smartirrigation/smartirrigation/controller/web src/main/java/com/smartirrigation/smartirrigation/security/SecurityConfig.java src/test/java/com/smartirrigation/smartirrigation/service/ConfigIrrigacaoServiceTest.java
git commit -m "feat: add status and config-irrigacao web endpoints, enqueueing commands to the device"
```

---

## Task 14: Wire configuration and `.env.example`

**Files:**
- Modify: `src/main/resources/application.properties`
- Modify: `.env.example` (rename from the current template content already in `.env`'s comments — create it if it doesn't exist)

**Interfaces:**
- Produces: `myapp.cors.allowed-origins` no longer needed as an `application.properties` key (moved to `.env`'s `MYAPP_CORS_ALLOWED_ORIGINS`, read by `FirebaseEnv`); documents `DEVICE_API_KEY` for whoever sets up the project next.

- [ ] **Step 1: Update `application.properties`**

```properties
spring.application.name=smart-irrigation
springdoc.swagger-ui.path=/swagger-ui.html
```

- [ ] **Step 2: Create/update `.env.example`**

```
# Copie este arquivo para ".env" (raiz do projeto) e preencha com valores reais.
# O .env nao deve ser commitado (ja esta no .gitignore).

# ID do projeto no Firebase/Google Cloud
FIRESTORE_PROJECT_ID=smart-irrigation-ifmaker

# Caminho para o JSON da service account.
# Firebase Console > Configuracoes do projeto > Contas de servico > Gerar nova chave privada
GOOGLE_APPLICATION_CREDENTIALS=./smart-irrigation-ifmaker-firebase.json

# Chave fixa que o ESP32 envia no header X-Device-Key em toda chamada HTTP.
# Gere um valor aleatorio longo (ex.: openssl rand -hex 32) e replique no firmware.
DEVICE_API_KEY=

# Origem(ns) permitidas para CORS (separadas por virgula), ex.: URL do Angular em dev/producao.
MYAPP_CORS_ALLOWED_ORIGINS=http://localhost:4200
```

- [ ] **Step 3: Verify the application boots**

Run: `./mvnw.cmd spring-boot:run` (with a real `.env`, `GOOGLE_APPLICATION_CREDENTIALS` pointing at a valid service account file, and `DEVICE_API_KEY` set)
Expected: Application starts without exceptions; `http://localhost:8080/swagger-ui.html` lists the device and web endpoints. Stop with Ctrl+C.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application.properties .env.example
git commit -m "chore: document DEVICE_API_KEY and CORS env vars, trim application.properties"
```

---

## Self-Review Notes

- **Spec coverage**: Sections 3 (arquitetura), 4 (pacotes), 5 (schema Firestore), 8 (segurança), 9 (`.env`), 10 (dependências) are fully covered by Tasks 1–10 and 14. Section 6.1 (device contract) is fully covered by Tasks 11–12. Section 6.2 (web endpoints) is covered for `status` and `config-irrigacao` in Task 13; the remaining rows (telemetry history, irrigation history, modo/autorização/quantidade-agua/latitude, comandos/irrigar-manual, culturas, usuarios/me) are explicitly deferred to a follow-up plan using the identical pattern (see Global Constraints).
- **Placeholder scan**: no TBD/TODO; every step has complete, concrete code.
- **Type consistency checked**: `CommandDispatchService.Command(String type, Map<String,Object> payload)` used identically in Tasks 6, 12, 13; `ConfigIrrigacaoDTO` field names/`@JsonProperty` keys match `ConfigIrrigacao` model `@PropertyName` keys and the firmware's snake_case expectations everywhere it appears (Tasks 4, 11, 13); `DeviceRepository.findById/save` signatures match between Task 7 (definition) and Tasks 11/13 (usage).
