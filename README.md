# ICEIBank — Sprint 1

Backend em **Java 17 + Spring Boot 3** (API REST/MVC com relógio de Lamport,
autenticação JWT) e um frontend web estático que consome essa API.

## Pré-requisitos

- JDK 17 ou superior (`java -version`)
- Maven (`mvn -version`) — ou use o Maven Wrapper da sua IDE
- Uma IDE com suporte a Spring Boot (IntelliJ IDEA, VS Code + Extension Pack
  for Java, ou Eclipse/STS) facilita bastante rodar 3 instâncias ao mesmo tempo

## Estrutura

```
iceibank/
├── agencia/                  # backend Spring Boot (o mesmo código roda como as 3 agências)
│   ├── pom.xml
│   └── src/main/java/com/iceibank/agencia/
│       ├── AgenciaApplication.java
│       ├── config/            # particionamento (AgenciaProperties) + segurança (SecurityConfig)
│       ├── model/              # Conta
│       ├── dto/                 # records de request/response
│       ├── service/            # RelogioLamport, RegistroEventos, ContaService, TransferenciaService, AuthService
│       ├── security/           # JwtUtil, JwtAuthFilter
│       ├── controller/         # AuthController, ContasController, TransferenciasController
│       ├── exception/          # exceções de negócio + handler global
│       └── tools/              # MesclarLogs.java (Parte E)
├── frontend/                 # HTML/CSS/JS puro (Parte G)
├── evidencias/sprint1/       # prints de evidência (ver README das seções 4.2, 2.1, 11.2, 12.2 do roteiro)
├── RESPOSTAS.md
└── .gitignore
```

## Como rodar as 3 agências

Cada agência é o **mesmo código**, identificado pela variável de ambiente
`AGENCIA_ID` (0, 1 ou 2). A porta é calculada como `4000 + OFFSET + AGENCIA_ID`
(o `OFFSET` é opcional, use-o só se estiver em uma máquina compartilhada de
laboratório — veja a seção 4.3 do roteiro).

Abra 3 terminais na pasta `agencia/`:

**PowerShell (Windows):**
```powershell
# Terminal 1
$env:AGENCIA_ID=0; mvn spring-boot:run

# Terminal 2
$env:AGENCIA_ID=1; mvn spring-boot:run

# Terminal 3
$env:AGENCIA_ID=2; mvn spring-boot:run
```

**Linux/macOS (bash):**
```bash
AGENCIA_ID=0 mvn spring-boot:run   # terminal 1
AGENCIA_ID=1 mvn spring-boot:run   # terminal 2
AGENCIA_ID=2 mvn spring-boot:run   # terminal 3
```

> Pela IDE: crie 3 "Run Configurations" para `AgenciaApplication`, cada uma
> com a variável de ambiente `AGENCIA_ID` diferente (0, 1, 2).

As agências sobem em `http://localhost:4000`, `4001` e `4002`.

## Testando via PowerShell (Invoke-RestMethod)

```powershell
# 1. Criar a conta 0 na Agência 0 (0 % 3 == 0) — precisa de senha agora (Parte F)
Invoke-RestMethod -Uri "http://localhost:4000/contas" -Method Post -ContentType "application/json" `
  -Body '{"id":0,"nomeAluno":"Ana","senha":"123456","saldoInicial":100}'

# 2. Login
$resp = Invoke-RestMethod -Uri "http://localhost:4000/auth/login" -Method Post -ContentType "application/json" `
  -Body '{"id":0,"senha":"123456"}'
$token = $resp.token

# 3. Consultar saldo (autenticado)
Invoke-RestMethod -Uri "http://localhost:4000/contas/0" -Method Get -Headers @{ Authorization = "Bearer $token" }

# 4. Depositar
Invoke-RestMethod -Uri "http://localhost:4000/contas/0/depositar" -Method Post -ContentType "application/json" `
  -Headers @{ Authorization = "Bearer $token" } -Body '{"valor":25}'

# 5. Sem token -> deve retornar 401
Invoke-RestMethod -Uri "http://localhost:4000/contas/0" -Method Get
```

## Script de linha do tempo unificada (Parte E)

A partir da pasta `agencia/`, depois de já ter gerado alguns eventos:

```bash
mvn compile exec:java -Dexec.mainClass="com.iceibank.agencia.tools.MesclarLogs"
```

Ou, pela IDE, rode diretamente o método `main` de `MesclarLogs.java`.

## Frontend (Parte G)

O frontend é HTML/CSS/JS puro — não precisa de build. Basta abrir
`frontend/index.html` diretamente no navegador (duplo clique, ou "Open with
Live Server" no VS Code). A tela permite escolher a qual agência o frontend
vai se conectar (útil para testar transferências entre agências diferentes).

## Notas importantes

- **Persistência**: as contas ficam em memória. Reiniciar uma agência apaga
  suas contas — é esperado neste sprint (seção 7.2 do roteiro).
- **Chave JWT**: por padrão vem um valor de exemplo em `application.properties`.
  Para trocar, defina a variável de ambiente `JWT_SECRET` (mínimo de 32
  caracteres) antes de rodar.
- **Limitação conhecida (Parte D)**: se a chamada entre agências falhar no
  meio de uma transferência, o débito já aplicado não é revertido
  automaticamente — isso é intencional (ver seção 2 do roteiro; resolvido de
  verdade no Sprint 4, com 2PC/Saga).
