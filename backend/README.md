# Global leaderboard backend

A single-file Cloudflare Worker (free tier) that stores the shared Top 10 in
Workers KV. No build step, no local tooling — everything happens in the
browser dashboard.

## One-time setup (~5 minutes)

1. Create a free account at https://dash.cloudflare.com/sign-up (free plan is fine).
2. In the dashboard sidebar choose **Storage & Databases → KV**, click
   **Create namespace**, name it `dino-scores`, and save.
3. Sidebar: **Compute (Workers) → Workers & Pages → Create → Create Worker**.
   Name it something like `dino-leaderboard`, click **Deploy** (the hello-world
   version), then click **Edit code**.
4. Replace the entire file contents with `leaderboard-worker.js` from this
   folder, then click **Deploy**.
5. Back on the worker's page open **Settings → Bindings → Add → KV namespace**:
   - Variable name: `SCORES`
   - KV namespace: `dino-scores`
   Save — the worker redeploys automatically.
6. Copy the worker URL, e.g. `https://dino-leaderboard.<your-account>.workers.dev`.

Quick test in any browser: `<worker-url>/top` should return an empty page
(no scores yet) instead of an error.

## Wire the game to it

Put the URL (no trailing slash) into
`composeApp/src/commonMain/kotlin/com/orisgames/dino/game/GameConfig.kt`:

```kotlin
const val GLOBAL_LEADERBOARD_URL = "https://dino-leaderboard.<your-account>.workers.dev"
```

Then rebuild/redeploy (web: `wasmJsBrowserDistribution` → push to gh-pages;
Android: `assembleDebug`). With the URL left empty the game silently falls
back to the per-device Top 10.

## Protocol

Plain text, one `name|score` per line:

- `GET /top` → current top 10
- `POST /submit` with body `name|score` → validates, stores, returns updated top 10

## Notes / limits

- Names are public to every player; the worker strips markup characters and
  caps length at 12.
- There is no authentication — anyone with the URL can submit scores (fine
  for a friends-and-family game; scores above 50000 are rejected as a sanity
  cap).
- KV is eventually consistent: two players finishing in the same second can
  briefly overwrite each other's submission. Harmless at this scale.
