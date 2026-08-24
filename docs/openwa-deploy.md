# Deploying OpenWA on an always-free VM

Goal: a server that holds a WhatsApp session so the Android app can send a photo to any number with
one tap and no WhatsApp screen. Everything below is free of charge and stays free.

Sources: [OpenWA](https://github.com/rmyndharis/OpenWA) README, `.env.example`, `docker-compose.yml`
and `src/modules/message/message.controller.ts` as of 2026-08-24.

---

## 0. Read this before spending an evening on it

**Cold first contact may silently fail.** OpenWA documents this as WhatsApp behaviour, not an OpenWA
bug: *"First message to a brand-new contact sometimes never arrives. The API returns success because
the message leaves OpenWA, but WhatsApp's server-side reach-out / trust policy drops it at delivery"*
([#830](https://github.com/rmyndharis/OpenWA/issues/830)). Sending a photo to a number that has never
messaged you is exactly that case. Test with two or three real numbers before building anything on it.

**The paired number can be banned.** OpenWA drives a reverse-engineered WhatsApp client. Its own
README says: never connect a primary personal or business number, use a number you can afford to
lose. Bans cannot be appealed through OpenWA.

**Warm the number up.** For the first few days behave like a person: exchange messages with saved
contacts, join a group, set a profile photo. Do not send to strangers on day one. OpenWA ships
`SEND_PACING_WARMUP_SCHEDULE` and `RATE_LIMIT_*` — leave them on.

If either of the first two is unacceptable, stop here: the app as shipped (photo attached, you pick
the chat in WhatsApp) has none of these risks.

---

## 1. Create the VM

**Oracle Cloud Always Free** — 2 OCPU / 12 GB RAM ARM (Ampere A1), 200 GB block storage, no expiry,
no charge. A card is needed for identity verification only.

1. Sign up at <https://signup.cloud.oracle.com>, choose a home region close to you
   (**Mumbai** or **Hyderabad** for India — the region cannot be changed later)
2. Console → **Compute → Instances → Create instance**
3. Image and shape → **Change shape** → **Ampere** → `VM.Standard.A1.Flex` → 2 OCPU, 12 GB
4. Image: **Canonical Ubuntu 24.04** (arm64 build)
5. Networking: keep the default VCN, **assign a public IPv4 address**
6. Add SSH keys → **Generate a key pair for me** → download both files
7. Create

If you get **"Out of Capacity"**, ARM is exhausted in that availability domain. Retry in a few hours,
try another availability domain, or use the fallback below. Do not switch to a paid shape.

**Fallback: Google Cloud e2-micro** — free forever in `us-central1`, `us-east1`, `us-west1`, but only
1 GB RAM. Enough for OpenWA with `ENGINE_TYPE=baileys` (~30–80 MB), not for the Chromium engine.
Baileys carries a higher ban risk, which matters more here than the RAM saving.

```bash
chmod 600 ~/Downloads/ssh-key-*.key
ssh -i ~/Downloads/ssh-key-*.key ubuntu@<PUBLIC_IP>
```

---

## 2. Reach the server privately (do this instead of opening a port)

The API is protected by a static API key. Sent over plain HTTP across the internet, that key is
readable by anyone on the path, and an exposed OpenWA instance is a hijackable WhatsApp session.

**Tailscale** puts the phone and the VM on a private network — no public port, no TLS certificate, no
domain. Free for personal use.

On the VM:

```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
```

Open the printed URL, sign in. Note the VM's tailnet address (`tailscale ip -4`), e.g. `100.x.y.z`.

On the phone: install **Tailscale** from Play Store, sign in with the same account.

Leave Oracle's security list closed. Nothing about OpenWA needs to face the internet.

---

## 3. Install Docker

```bash
sudo apt-get update && sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu && newgrp docker
```

OpenWA publishes multi-arch images (`linux/amd64`, `linux/arm64`), so ARM is a first-class target.

---

## 4. Start OpenWA

```bash
git clone https://github.com/rmyndharis/OpenWA.git
cd OpenWA
cp .env.minimal .env
```

Edit `.env`:

```ini
NODE_ENV=production
PORT=2785

# Chromium engine: lower ban risk, ~300-500 MB RAM. Fine on 12 GB, not on a 1 GB e2-micro.
ENGINE_TYPE=whatsapp-web.js
PUPPETEER_HEADLESS=true

# Re-authenticate the session automatically after a restart.
AUTO_START_SESSIONS=true

# SQLite + local files: no extra containers.
DATABASE_TYPE=sqlite
STORAGE_TYPE=local

# Bootstrap key. Generate with: openssl rand -hex 32
API_MASTER_KEY=<paste-64-hex-chars>
```

Start it:

```bash
docker compose up -d
docker compose logs -f openwa-api   # Ctrl-C once it reports listening
```

The dashboard and the API share port 2785. From the phone or laptop **on Tailscale**, open
`http://100.x.y.z:2785`.

---

## 5. Pair the WhatsApp number

Use the **spare SIM**, not your main number.

Dashboard route: **Sessions → Create**, then **Start**, then scan the QR with
WhatsApp → *Linked devices → Link a device*.

Same thing over the API, if you prefer:

```bash
KEY=<your API key>
BASE=http://100.x.y.z:2785/api

curl -X POST $BASE/sessions -H "X-API-Key: $KEY" \
  -H 'Content-Type: application/json' -d '{"name":"phone"}'          # returns sessionId

curl -X POST $BASE/sessions/<sessionId>/start -H "X-API-Key: $KEY"
curl $BASE/sessions/<sessionId>/qr -H "X-API-Key: $KEY"              # scan this
```

Create a scoped API key for the Android app in **Dashboard → API keys** rather than shipping
`API_MASTER_KEY` to the phone.

---

## 6. Prove a send works before any app code

`chatId` is `<country code><number>@c.us` — no `+`, no spaces.

```bash
curl -X POST $BASE/sessions/<sessionId>/messages/send-text \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"chatId":"919551563593@c.us","text":"test"}'
```

Then an image (this is the call the app will make):

```bash
B64=$(base64 -w0 photo.jpg)
curl -X POST $BASE/sessions/<sessionId>/messages/send-image \
  -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d "{\"chatId\":\"919551563593@c.us\",\"base64\":\"$B64\",\"mimetype\":\"image/jpeg\",\"caption\":\"\"}"
```

**Check the recipient's phone, not the HTTP status.** A `200` means OpenWA accepted it; per #830 the
message can still be dropped by WhatsApp when the recipient has never messaged you. Try three
numbers: one that has chatted with the paired number, and two that never have.

---

## 7. What the app needs

Once step 6 delivers reliably, three values go into the app's settings screen:

| Field | Example |
|---|---|
| Server URL | `http://100.x.y.z:2785` |
| API key | the scoped key from the dashboard |
| Session id | from step 5 |

Send then becomes one `POST .../messages/send-image` with the photo base64-encoded — no WhatsApp
screen, any number, your own WhatsApp identity. The phone must be on Tailscale for the server to be
reachable.

---

## Keeping it alive

- Oracle reclaims **idle** always-free instances; OpenWA running continuously counts as active
- `docker compose logs -f openwa-api` for session drops; `AUTO_START_SESSIONS=true` re-links after reboot
- Re-pairing is needed if WhatsApp unlinks the device (a normal occurrence)
- Back up `./data` — it holds the session, so losing it means scanning the QR again
