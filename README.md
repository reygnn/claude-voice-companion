# Claude Voice Companion

A conversational voice app for Android — like talking to a thoughtful friend over a glass of wine. No commands, no "Hey Claude." Just speak, listen, and think.

## What it does

Tap the microphone, say what's on your mind, and Claude responds — both in text and out loud. The conversation flows naturally: Claude starts speaking while still thinking, you can interrupt anytime, and your history is saved locally for later.

## Architecture

The app is built around three pillars:

**Ear** — `VoiceInputManager` handles speech recognition via Android's `SpeechRecognizer`. On-device recognition is preferred, with automatic cloud fallback. Supports configurable silence detection (2–3 seconds) and partial results displayed in real-time.

**Brain** — `AnthropicStreamingClient` connects to the Claude API with SSE streaming. Responses arrive word-by-word, enabling the voice to start before the full answer is ready. Full conversation history is sent with each request for natural context.

**Voice** — `VoiceOutputManager` uses Android TTS with sentence-level streaming. Text is split at sentence boundaries and queued for playback. Interrupting is instant — tap the mic and Claude stops mid-sentence.

The `ConversationOrchestrator` ties all three together as a state machine:

```
Idle → Listening → Transcribing → Claude Thinking → Claude Speaking → Idle
                                                         ↑
                              Interrupt (tap mic) ───────┘
```

## Tech Stack

- **Language:** Kotlin, Coroutines, Flows
- **UI:** Jetpack Compose, Material 3
- **Target:** Android 16 (API 36) — no legacy support
- **DI:** Hilt 2.57.2
- **Persistence:** Room (conversations + messages)
- **Networking:** OkHttp with SSE streaming
- **API Key Storage:** EncryptedSharedPreferences
- **Testing:** MockK + kotlinx-coroutines-test + Turbine

## Project Structure

```
app/src/main/kotlin/com/claudecompanion/
├── di/                     # Hilt modules
├── data/
│   ├── local/              # Room DB, DAOs, ApiKeyStore
│   ├── remote/             # Anthropic streaming client
│   └── repository/         # ConversationRepository
├── domain/model/           # Message, Conversation, Role
├── voice/
│   ├── VoiceInputManager   # Speech recognition
│   ├── VoiceOutputManager  # TTS with sentence streaming
│   └── ConversationOrchestrator  # State machine
└── ui/
    ├── theme/              # Wine-red & gold dark theme
    ├── conversation/       # Main screen + ViewModel
    ├── settings/           # API key, voice config
    ├── history/            # Past conversations
    └── components/         # VoiceVisualizer, MicrophoneButton
```

## Setup

1. Clone the repo
2. Open in Android Studio with Android 16 SDK (API 36)
3. Sync Gradle
4. Build and install on a Pixel or emulator running Android 16
5. Open the app → Settings (gear icon) → enter your [Anthropic API key](https://console.anthropic.com/account/keys)
6. Tap the microphone and start talking

## Design

Dark leather background, gold accents, wine-red highlights. No chat bubbles — this isn't a messenger. A central voice visualizer animates with waveforms while you speak, pulses while Claude thinks, and breathes gently at rest.

## Anti-Features

- ❌ No always-listening / wake word
- ❌ No ads, no tracking
- ❌ No social media sharing
- ❌ No gamification
- ❌ No chat bubbles

## Testing

Tests follow a single-dispatcher convention via `MainDispatcherRule`. No separate `TestScope` or `StandardTestDispatcher`. MockK for all dependencies, Turbine for Flow testing.

```bash
./gradlew testDebugUnitTest
```

## License

MIT
