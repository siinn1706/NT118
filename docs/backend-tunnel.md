# Backend and tunnel for HealthOS Mobile

The app talks to the HealthOS Core API (FastAPI) running on a laptop, exposed to the phone through a named Cloudflare tunnel. The tunnel needs two public hostnames:

| Hostname (example) | Local target | Why |
|---|---|---|
| `api.<your-domain>` | `http://localhost:8000` (Core API) | Every app request, including the chat SSE stream |
| `app.<your-domain>` | `http://localhost:3000` (Next.js frontend) | The emergency card QR opens `<app>/e/{token}` |

A free `trycloudflare.com` quick tunnel changes its URL on every start, so use a named tunnel for anything you will rebuild the APK against.

## 1. Start HealthOS

In the HealthOS repository (see its README for first-time setup):

```powershell
docker compose -f infra/docker/docker-compose.dev.yml up -d
```

Then make sure these backend env values are set (in `backend/.env`, never committed):

- `APP_URL=https://app.<your-domain>` — used to build the emergency share link.
- SMTP settings — needed later for the SOS email (Safety Net phase).

Warm the AI worker once before a demo by analysing one meal photo.

## 2. Create the named tunnel (once)

```powershell
cloudflared tunnel login
```

```powershell
cloudflared tunnel create healthos-demo
```

```powershell
cloudflared tunnel route dns healthos-demo api.<your-domain>
```

```powershell
cloudflared tunnel route dns healthos-demo app.<your-domain>
```

Create `%USERPROFILE%\.cloudflared\config.yml`:

```yaml
tunnel: healthos-demo
credentials-file: C:\Users\<you>\.cloudflared\<tunnel-id>.json
ingress:
  - hostname: api.<your-domain>
    service: http://localhost:8000
  - hostname: app.<your-domain>
    service: http://localhost:3000
  - service: http_status:404
```

## 3. Run the tunnel

```powershell
cloudflared tunnel run healthos-demo
```

Check both hostnames from the phone's browser: `https://api.<your-domain>/v1/health` returns `{"status":"ok"}`.

## 4. Point the app at it

Copy `local.properties.example` to `local.properties` (already git-ignored) and set:

```properties
HEALTHOS_BASE_URL=https://api.<your-domain>/
```

Rebuild and install. In a debug build, open **Tôi → Design gallery → Ping backend** to confirm the round trip.

## Fallbacks

- Emulator without a tunnel: leave `HEALTHOS_BASE_URL` unset; the default `http://10.0.2.2:8000/` reaches the laptop's localhost. Debug builds allow cleartext HTTP for this.
- Phone on the same Wi-Fi as the laptop: `http://<laptop-LAN-IP>:8000/` (debug builds only).
- Venue network blocks the tunnel: run the laptop on the phone's hotspot.

## Pre-Demo Verification Checklist (15 min before presentation)

- [ ] **Start Docker stack:** `docker compose -f infra/docker/docker-compose.dev.yml up -d` and verify Postgres, Redis, Core API, AI Worker, and Next.js frontend are healthy.
- [ ] **Warm the AI worker:** run one test meal photo through `POST /v1/meals/analyze-photo` so the vision model is already loaded in memory (< 15 s turnaround during demo).
- [ ] **Verify SMTP:** confirm `SMTP_HOST`, `SMTP_USER`, `SMTP_PASSWORD` are loaded in `backend/.env` (never committed) and `POST /v1/emergency/sos` delivers an email to the test contact inbox.
- [ ] **Start named tunnel:** `cloudflared tunnel run healthos-demo`.
- [ ] **Health-check both hostnames from the Pixel 8 Pro:**
  - `https://api.<your-domain>/v1/health` returns `{"status":"ok"}`.
  - `https://app.<your-domain>/e/<active-token>` loads the public Emergency Health Card.
- [ ] **Fallback ready:** phone hotspot tested with laptop; pre-recorded clips of Meals Snap, AI Doctor streaming, Fall SOS, and Camera PPG ready on disk.
