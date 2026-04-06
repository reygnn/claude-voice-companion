# TODO

## v0.1 — Initial Release
- [X] Proof of Concept

## v0.2 — Polish

- [ ] Voice selection in Settings (let user pick from available TTS voices)
- [ ] Language switcher (English / German / auto-detect)
- [ ] Export conversation as Markdown
- [ ] Bluetooth headset support
- [ ] Audio focus management (pause music when Claude speaks)

## v0.3 — Quality of Life

- [ ] Foreground service for active conversations (prevent Android from killing the app)
- [ ] Notification: resume conversation from notification shade
- [ ] Haptic feedback patterns for different states
- [ ] Silence duration slider in Settings
- [ ] System prompt editor ("How should Claude talk to you?")

## v0.4 — Big Ideas

- [ ] Multiple personalities (different system prompts as profiles)
- [ ] Automatic summarization of old conversations for context management
- [ ] Homescreen widget: "Continue conversation"
- [ ] Wear OS companion
- [ ] Offline mode with local LLM fallback

## Maybe — Gemini Support

- [ ] Abstract `LlmStreamingClient` interface
- [ ] `GeminiStreamingClient` implementation
- [ ] Provider picker in Settings

> **Note:** Gemini support is under consideration. The architecture supports it cleanly —
> the Orchestrator only knows `StreamEvent`, so any LLM backend that emits
> `TextDelta / Done / Error` will work. We might build it. We might not.
> Bribes will not accelerate the timeline. The developer does not accept bribes.
> Not even good wine. Okay, *maybe* good wine. But probably not. Don't try it.

## Never — Anti-Features

- ❌ Always-listening / wake word
- ❌ Ads or tracking
- ❌ Social media sharing
- ❌ Gamification (streaks, badges)
- ❌ Chat bubbles
