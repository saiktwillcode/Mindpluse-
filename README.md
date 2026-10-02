# MindPulse AI (Java backend)
Requires JDK 17+ and Maven.

    mvn spring-boot:run
    open http://localhost:8080

The UI is served by Spring Boot from `src/main/resources/static/index.html` and calls the API on the same origin.
If the API is unreachable the page falls back to its in-browser engine and says so.

## API
- `POST /api/analyze`  body `{"text":"I have three assignments due..."}` -> scores, emotions, context, evidence, explanation
- `GET  /api/analysis/history`  last 50 analyses (in memory)
- `GET  /api/health`

    curl -X POST localhost:8080/api/analyze -H 'Content-Type: application/json' -d '{"text":"I am not stressed anymore."}'

Engine lives in `NlpService.analyze()`; swap it for a transformer/LLM call and the frontend keeps working.

## Authentication (JWT)
All `/api/**` endpoints except `/api/auth/*` and `/api/health` need `Authorization: Bearer <token>`.
- `POST /api/auth/register` `{name,email,password}` creates a STUDENT account (password 8-72 chars, stored as a BCrypt hash)
- `POST /api/auth/login` `{email,password}` returns `{token,name,role}` (8h expiry)
- `GET /api/dashboard/faculty` requires role FACULTY or ADMIN; returns aggregates only, hidden until 5+ responses
- Demo accounts (password `Demo@1234`): `student@demo.edu`, `faculty@demo.edu`, `admin@demo.edu`

Set a signing key before real use: `export MINDPULSE_JWT_SECRET="<32+ random characters>"`.
Users are in memory for now, so registrations reset on restart.

## Login screen
The site opens on a login screen (Student / Faculty / Admin tabs). With the Java server running it uses JWT auth; without a server (e.g. the hosted demo) it falls back to simulated demo accounts in the browser and says so.
