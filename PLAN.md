# Nimble plan

Nimble is a Hermes-style agent harness for Android, rebuilt around Cactus Needle 3.
This file is the living design doc: update it as the project evolves.

## Decisions so far

| Topic | Decision |
|---|---|
| Distribution | APK built by GitHub Actions (sideload). Play Store later. |
| Weights | Downloaded in-app from Hugging Face, pinned revision + SHA-256. Not bundled. |
| Threads | Set manually in Settings (1..cores). |
| Voice | In v1: Whistle speech to text behind the mic button. |
| Cloud | None. Strictly offline apart from the one-time weight download. |
| UI | One minimal chat screen, mic on the right of the box (WhatsApp/Telegram style), settings top-right. |
| Size rung | Base Needle 3 for now (see below). |

### What a "size rung" is

Needle 3 is a stack of up to 20 layers, and Cactus can export a working model from
the first N layers ("rungs", 2–20). Fewer layers mean a smaller, faster file
(down toward ~8 MB) but lower accuracy. The useful move comes later: fine-tune on
Nimble's own tools, then ship the smallest rung that still passes our test suites.
Until then we use the published base model (`needle3.cact`).

## What Needle 3 can and cannot do

- Output is **only JSON tool calls**, grammar-constrained to the schema, with a
  calibrated `confidence`, a short `reasoning` and `suppressed_calls` for withheld calls.
- **No free text.** Off-topic input returns an empty call list.
- Multi-turn works (feed tool results back); context is small (~1k tokens).
- **Five tools or fewer per session** work best; changing toolsets needs `cactus_init`.
- The engine holds one global session; calls must be serialized.
- Android ships a **static library**; we link it into our own JNI `.so`.
- No on-device training; fine-tuning happens offline and ships a new `.cact`.

## Hermes → Nimble mapping

| Hermes | Nimble |
|---|---|
| One LLM with 60+ tools | Two-stage routing: keyword match or a Needle router session over domains, then a Needle session with only that domain's ≤5 tools |
| LLM writes the answer | Each tool returns its own `say` text (templates) |
| Approval system | Confidence gate (threshold in Settings) + per-tool `Risk.CONFIRM` + `suppressed_calls` → Run/Cancel in chat |
| `clarify` tool | (todo) Kotlin asks for missing required slots and feeds the answer back |
| MEMORY.md / USER.md | (todo) SQLite facts and aliases used to resolve arguments ("mom" → contact), not stuffed into the prompt |
| Skills | (todo) Routines: recorded tool-call sequences, exposed as `run_routine(name: enum)` |
| Self-improvement | (todo) Opt-in local log of corrections → export JSONL → fine-tune → new `.cact` |
| Session search | (todo) Room FTS + Needle embeddings |
| Cron | (todo) WorkManager + `schedule_task` |
| Gateway | Chat + voice now; widget, quick tile, share sheet, assistant role later |
| Terminal/browser/code/subagents | Dropped (need a big model) |

## Architecture (v0.1)

- `cpp/nimble_jni.c`: JNI over `cactus_load/init/complete/reset/set_threads/transcribe`.
  Strings cross as UTF-8 byte arrays; model files are memory-mapped.
- `engine/CactusEngine`: single-thread dispatcher; tracks loaded models and the active
  toolset; reloads models when the thread count changes.
- `harness/Harness`: route → act → gate → execute → feed back (max 3 steps).
- `tools/*`: four domains (alarms/timers, calls/messages, notes, phone controls).
- `models/ModelManager`: download with progress, SHA-256 check, delete.
- `ui/*`: chat and settings in Jetpack Compose. No other UI libraries.

## Roadmap

1. **v0.1 (this draft):** chat + voice, 4 domains, router, confidence gate, model downloads, threads.
2. **Validate on devices:** measure load time, `cactus_init` cost per toolset switch, latency
   and RAM on a low-end and a mid-range phone; tune the router.
3. **Port the grounding layer** from cactus-compute's Python (`grounding.py`): refuse
   ungrounded arguments, check years in dates.
4. **Memory and aliases**, slot clarification, chat history persistence.
5. **Routines** (the skills analog), scheduling, widget and quick-settings tile.
6. **Eval suites** per domain, shared `tools.json` between the app and a desktop Python harness.
7. **Fine-tune** on Nimble's tools (Cactus platform), pick the smallest passing rung.
8. Stable signing key for upgrade-in-place, then Play Store.

## Open risks

- The static engine's link dependencies are unverified until CI links it.
- The context size (~1k tokens) is inferred from fine-tuning defaults.
- Whether loading Whistle disturbs the active Needle session (handled by one retry with re-init).
