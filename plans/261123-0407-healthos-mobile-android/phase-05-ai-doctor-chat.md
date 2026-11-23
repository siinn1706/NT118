---
title: "Phase 5: AI Doctor Chat"
status: done
priority: P1
effort: 4d
dependencies: [1, 2]
---

# Phase 5: AI Doctor Chat (Dev A)

## Goal

A chat with the HealthOS AI doctor that streams answers token by token, grounded in the user's own data and the RAG medical corpus, with Vietnamese voice input/output and safety handling.

## Backend contracts (verified)

- `POST /v1/conversations/ai` (get or create the AI conversation), `GET /v1/conversations/{id}/messages` (history), `POST /v1/conversations/{id}/messages/stream` → `text/event-stream` with events `delta {text, seq}`, `ping`, and a final event carrying AI metadata.
- `POST /v1/conversations/uploads/image` for attaching a photo.
- Emergency short-circuit exists server-side: when `medical_safety` detects an emergency, the stream emits a fixed emergency text first.

## Files to create

- `feature/chat/{ChatGraph.kt, ChatScreen.kt, MessageBubble.kt, ChatViewModel.kt, ChatRepository.kt, ChatApi.kt, VoiceInput.kt (SpeechRecognizer vi-VN), VoiceOutput.kt (TextToSpeech vi-VN), SuggestedPrompts.kt}`

## Tasks

1. Load history (paged), send via the SSE stream using `core/network/SseClient`; append deltas by `seq`, ignore `ping`, finalize on the final event; cancel on screen exit.
2. Markdown rendering for answers (bold, lists) and a visible "AI — không thay thế bác sĩ" disclaimer.
3. Context chips above the input: "Hôm nay tôi ăn gì?", "Nhịp tim của tôi", "Gợi ý bài tập" — prompts that make the backend pull the user's data (verify which context `ai_chat_context.py` injects).
4. Voice: hold-to-talk mic → `SpeechRecognizer` with `vi-VN` partial results into the input; speaker icon on each answer → TTS.
5. Safety: when the response contains the emergency marker or metadata flag, show a red banner with buttons "Gọi 115" (`ACTION_DIAL`) and "Mở SOS" (Phase 7 entry point).
6. Quick action on an answer: "Tạo nhắc nhở" → prefilled reminder sheet (`POST /v1/reminders`).
7. Image attach (photo of a rash, a label) via the upload endpoint.

## Verification

- Unit tests: SSE event parser and delta assembly (out-of-order `seq`, ping, abrupt close → error state with retry).
- On device: ask a question → first tokens < 3 s through the tunnel; voice question in Vietnamese is transcribed; emergency phrase ("đau ngực dữ dội, khó thở") shows the red banner.

## Risks

- Cloudflare tunnels can buffer SSE; if streaming arrives in one chunk, set `X-Accel-Buffering: no` / disable compression on that route, or fall back to WebSocket `/v1/chat/ws/{conversation_id}` with a WS ticket from `GET /v1/auth/ws-ticket`.
- [UNVERIFIED] the exact field that flags emergencies in the final event — read `ai_chat_stream.py` (`_ai_metadata_from_stream_final`).
- Voice (task 4) and image attach (task 7) are the first cuts if the phase overruns.

## Implementation notes (2026-11-23)

Verified against `siinn1706/NT208_HealthOS` (`backend/app/api/v1/endpoints/conversations.py`, `backend/app/services/ai_chat_stream.py`, `backend/app/services/medical_safety.py`, `backend/app/schemas/{chat,reminders}.py`):

- **SSE frame & emergency detection (verified)**:
  - `POST /v1/conversations/{id}/messages/stream` emits SSE events `start`, `delta { text, seq }`, `ping`, `done { message_id, status, final_length }`, `aborted`, and `error`.
  - `_ai_metadata_from_stream_final` records `safety_blocked` (and RAG/usage fields) on the DB row; when `medical_safety.classify_by_rules` short-circuits an emergency, the server persists the message with `content_type = "system"` and emits `build_emergency_reply(...)` without adding a separate `emergency` boolean to the `done` SSE event.
  - Implemented `EmergencyDetector` to flag emergencies from all three signals: `content_type == "system"`, `ai_metadata.safety_blocked` / `emergency`, and the backend's exact Vietnamese/English emergency reply phrases — so both live SSE streams and reloaded history reliably show the red "Gọi 115" / "Mở SOS" banner.
- **Attachments, voice & quick reminders**: Implemented image attachment upload (`POST /v1/conversations/uploads/image` with 1280 px downscaling), Vietnamese `SpeechRecognizer` (`vi-VN`) + `TextToSpeech`, and prefilled quick reminders (`POST /v1/reminders`). Added `RECORD_AUDIO` permission to `AndroidManifest.xml`.

Verified: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passed (including `ChatStreamParserTest`).

Still open: on-device streaming latency check through Cloudflare tunnel, live Vietnamese voice transcription, and emergency phrase check ("đau ngực dữ dội, khó thở").
