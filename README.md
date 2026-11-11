# NexFleet — Identidade

Microsserviço de autenticação e usuários do NexFleet, plataforma de gestão de frotas. Parte da v1.0.0; o sistema completo está no repositório guarda-chuva [nexfleet](https://github.com/mateusblm/nexfleet).

## Arquitetura

A Identidade faz o login, emite o **JWT** (com o perfil `jwt-rsa`, assinado por chave RSA e com a chave pública em `/.well-known/jwks.json`, usada pelo BFF e pelos demais serviços para validar o token) e gerencia os usuários com os perfis `ADMINISTRADOR` e `GESTOR_FROTA`. Persiste no **Azure SQL** (schema `identidade`, login próprio, migrações Flyway). Rotas em `/api/v1/identidade/{login,me,usuarios}`, chamadas pelo BFF.

```text
Navegador → shell + microfrontends (Static Web Apps)
          → Azure API Management (APIM) → Gateway → BFF
              ├─ MS1 Frota ........ MongoDB Atlas
              ├─ MS2 Operações .... Azure SQL (schema operacoes)
              ├─ Identidade, Indicadores ... Azure SQL (um schema e um login por serviço)
              └─ Azure Function HTTP calcular-eficiencia
Operações → outbox → Service Bus (nuvem) / RabbitMQ (local) → Indicadores + Function (notificação)
```

**Clean Architecture + Vertical Slice.** Fatias em `br.com.nexfleet.identidade`, cada uma com `api`, `application` e `infrastructure`:

| Fatia | Responsabilidade |
|---|---|
| `autenticar` | login, usuário atual e JWKS |
| `gerenciarusuarios` | criar, editar, consultar, inativar e excluir usuários |
| `consultarservico` | informações do serviço |

Domínio em `usuario/domain`; pacotes técnicos `config` (segurança, JWT e o seed local do perfil `local-seed`) e `observability`.

**Testes:** unitários e de API (ex.: `AutenticarTest`, `GerenciarUsuariosTest`, `RsaLoginHttpTest`), de integração com SQL Server real em Testcontainers (`JdbcUsuariosIT`, `IdentidadeHttpIT`, `SqlServerIT`) e de arquitetura com ArchUnit (`ArchitectureTest`: domínio e aplicação sem framework; `api` sem acesso direto à persistência).

## Tecnologias

- Java 21, Spring Boot 4.1.1 (Web MVC, Security, OAuth2 Resource Server, JDBC, Flyway, Actuator)
- SQL Server / Azure SQL (`mssql-jdbc`, `flyway-sqlserver`)
- springdoc-openapi 3.2.0 (OpenAPI em `/v3/api-docs`)
- Testcontainers (SQL Server)
- Micrometer + Prometheus
- JUnit 5, ArchUnit 1.4.1
- Maven Wrapper (Maven 3.9.16)
- Docker (`eclipse-temurin:21`), GitHub Actions (testes em todo push; imagem no Docker Hub em tag `v*`)

## Como rodar localmente

```bash
./mvnw -B -ntp verify                                     # unitários, ArchUnit e ITs (Testcontainers; exige Docker)
docker build -t mateusblm/nexfleet-identidade:local .     # imagem (porta 8080)
```

Variáveis de ambiente: `PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `SPRING_PROFILES_ACTIVE` (`jwt-rsa`; localmente também `local-seed`), `AUTH_JWT_PRIVATE_KEY` (caminho da chave RSA, gerada por `scripts/iniciar-local.sh` no guarda-chuva), `JWT_SECRET` (só sem `jwt-rsa`) e, para o seed local, `LOCAL_USER_EMAIL`, `LOCAL_USER_PASSWORD`, `LOCAL_USER_PROFILE`.

**Ambiente completo** (todos os serviços, SQL Server, MongoDB e RabbitMQ locais) sobe pelo repositório guarda-chuva [nexfleet](https://github.com/mateusblm/nexfleet), com os repositórios clonados lado a lado (`scripts/clonar-tudo.sh`):

```bash
scripts/iniciar-local.sh                                     # 1ª vez: gera o .env e a chave RSA local
docker compose -f compose.yaml -f compose.build.yaml up -d --build
scripts/seed-demo.sh                                         # dados de demonstração
```

Sem o override `compose.build.yaml`, o `docker compose up -d` usa as imagens publicadas no Docker Hub.

## URLs na nuvem

A Identidade não tem URL pública direta: é acessada só pelo APIM (via Gateway e BFF, ex.: `/api/v1/auth/login`). O OpenAPI aparece na Swagger UI abaixo, opção "Identidade".

Imagem Docker: [`mateusblm/nexfleet-identidade:v1.0.0`](https://hub.docker.com/r/mateusblm/nexfleet-identidade)

| Recurso | Endereço |
|---|---|
| Frontend | https://white-mushroom-0cadda010.3.azurestaticapps.net |
| API (APIM) | https://apim-nexfleet-homolog-ukzjodf42myfe.azure-api.net |
| Swagger UI (BFF, Frota, Operações, Identidade, Indicadores) | https://apim-nexfleet-homolog-ukzjodf42myfe.azure-api.net/swagger-ui.html |
| Dados agregados (exige token) | https://apim-nexfleet-homolog-ukzjodf42myfe.azure-api.net/aggregated-data |
| AsyncAPI dos eventos | https://white-mushroom-0cadda010.3.azurestaticapps.net/docs/eventos/ |
| Repositório guarda-chuva | https://github.com/mateusblm/nexfleet |

> **Partida a frio:** os Container Apps escalam a zero e o Azure SQL pausa sem uso. A primeira chamada após um período de inatividade pode levar de 1 a 2 minutos (ou responder 503/504); tente de novo em seguida.

## Equipe

- Vinícius dos Reis Góes
- Mateus Burlamaqui
- Lucas Roussenq
- Erik Carvalho
