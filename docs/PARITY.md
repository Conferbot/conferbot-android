# Web widget parity: Android

This file tracks how far this SDK matches the web widget: what's done, what's left, and how to pick the work up. Features are built in the React Native SDK first; its `docs/PARITY.md` and `src/core/*.ts` are the reference.

There are two UIs, and both need each feature:

- **Compose:** `ui/compose/`. `ConferBotWidget` is the floating launcher.
- **XML Views:** `ui/views/ChatActivity`, `MessageAdapter`, `XmlLook`. It has no launcher, so launcher and greeting work is Compose only.

Package root: `conferbot/src/main/java/com/conferbot/sdk/`.

## Done in this SDK

| Issue | Feature | Key files |
|---|---|---|
| #12-#14 | Customization basics, Compose + XML | `core/customization/Customizations.kt`, `ui/views/XmlLook.kt` |
| #15 | Chat background | `core/customization/ChatBackground.kt`, `ui/compose/chat/ChatBackgroundLayer.kt` |
| #16 | 27 themes, light/dark/auto, palette | `WidgetTheme.kt`, `WidgetThemesData.kt` (generated) |
| #18 | Launcher resolver | `Launcher.kt`, `ui/compose/ConferBotWidget.kt` |
| #19 | 56 icons | `LauncherIcons.kt`, `LauncherIconsData.kt`, `ui/compose/LauncherIconDraw.kt` |
| #20 | Launcher motion, passes the golden test | `LauncherMotion.kt`, `ui/compose/LauncherMotionClock.kt`, `src/test/resources/launcher-motion-golden.json` |
| #21 | User as `visitorContext` + identity hash | `models/Configuration.kt` (`toVisitorContext`), `core/state/ChatState.kt`, `core/Conferbot.kt` |

## Left in this SDK

1. **Greeting card** (see "Porting the greeting card" below):
   - `Greeting.kt` (pure) with tests.
   - A state holder using SharedPreferences under key `conferbot.greeting.<botId>`.
   - A Compose `GreetingCard` with the 11 styles, wired into `ConferBotWidget.kt` where the plain greeting bubble is now.
2. The rest of the list below.
3. **XML gaps:** initials avatar and the wave typing style.
4. **Test debt:**
   - #17: three test classes run the 2g test heap out of memory.
   - In `ChatStateTest`, 3 tests are stale: the transcript cap is now 500 (the test expects 200), `getRecordForServer` format, and a null field in "includes all required fields".
5. **#21 not yet checked on a device:** pass a user, send a message, then look for `visitorContext` on the response record.

## How to test

- **Unit tests:**
  - Run one class at a time: `sh ./gradlew :conferbot:testDebugUnitTest --tests 'com.conferbot.sdk.core.customization.*' --no-daemon -q`.
  - Results are in `conferbot/build/test-results/testDebugUnitTest/`.
- **Example app:**
  - To point it at a local server, add `baseUrl = "http://10.0.2.2:8001/api/v1/mobile/", socketUrl = "http://10.0.2.2:8001"` to `ExampleApplication.kt`.
  - Build with `sh ./gradlew :example:assembleDebug`. Package `com.conferbot.example`.
  - Force-stop before relaunching, or it resumes the XML chat.
  - Revert the example before committing.

## The goal

Every web widget feature works the same in the 4 mobile SDKs: customization, launcher, greeting and the live chat / agent features. The web widget (`conferbot-widget/src`) is the reference. Each feature is built and checked against the web in React Native first, then ported to Android, iOS and Flutter. The web source file it came from is named in a comment.

## Status across the SDKs (2026-10-15)

| Feature | RN | Android | iOS | Flutter |
|---|---|---|---|---|
| Customization basics (header, bubbles, avatar, typing styles, composer, footer) | done | done | done | done |
| Chat background (solid, gradient, image + mask, patterns) | done | done | done | done |
| 27 widget themes, light/dark/auto, theme palette | done | done | done | done |
| Launcher shape, size, offsets, gradient, image, avatar, label, glow | done | done | done | done |
| 56 launcher icons, drawn from the widget SVGs | done | done | done | done |
| Launcher motion (idle moments, message reaction, ripple) | done | done | done | done |
| Signed-in user as `visitorContext` + identity hash | done | done | done | done |
| Greeting card (rules, frequency, 11 styles) | done | todo | todo | todo |
| Launcher entrance / attention / idle call | todo | todo | todo | todo |
| Home screen | todo | todo | todo | todo |
| Mascots (incl. `mascotInGreeting`) | todo | todo | todo | todo |
| `widgetLanguage` (i18n) | todo | todo | todo | todo |
| Message sounds | todo | todo | todo | todo |
| Celebrations / reactions | todo | todo | todo | todo |
| Reply-time wording | todo | todo | todo | todo |

Live chat / agent features (handover, queue card, receipts, rating, files, prefill, max-wait timeout, agent bot replies, suggestion chips) are done in all 4.

Each SDK repo has its own `docs/PARITY.md`. The React Native one also covers the generators in `scripts/parity/` and the greeting card spec in `docs/greeting-card-native-spec.md`.

### Web sources for the features still to do

All paths are under `conferbot-widget/src/`.

- **Launcher entrance / attention / idle call (all default off):**
  - `ts/launcher/entrance.ts`, setting `launcherEntrance`: classic default; lightning, rocket, portal, ufo, drop, confetti, coin.
  - `ts/launcher/attention.ts`, setting `launcherAttention`: off, pulse, wobble, bounce, wave, float, shine, heartbeat, orbit.
  - `ts/launcher/idleCall.ts`, setting `launcherIdleCall`: off, psst, peek, wave, knock, tap; delay 12.
  - The CSS is in `css/`.
- **Home screen:** `ts/home/` (homeCopy, askBox, trust, pageAware, visitorMemory).
- **Mascots:** `ts/home/mascotMotion.ts`, `ts/launcher/launcherStyle.ts`, `ts/launcher/art3d.ts`, `ts/visibility/greeting.ts`. The `mascot` typing style is accepted but not drawn yet.
- **i18n:** `ts/i18n.ts` + `ts/i18n/{en,de,es,fr,nl}.ts`.
- **Sounds:** `ts/chat/messageSound.ts`.
- **Celebrations:** `ts/chat/delight/` (celebrate, reactions, rules) and `ts/chat/ratingFx.ts`.
- **Reply time:** `ts/chat/replyTime.ts`, `replyTimeView.ts`, `headerPresence.ts`.

### Porting the greeting card (from RN)

- **`src/core/greeting.ts`** is pure and tested. Port it with its tests.
  - `decideGreeting(c, facts)` picks the card:
    - The app counts as path "/". Rules for given pages, countries, sources, page attributes or browser-only behaviour never apply.
    - The rest are checked for audience (new/returning, signed in), schedule, `minVisits`, chatted, and their own limits (`maxShows` within `windowDays`, `quietDaysAfterDismiss`). They are ranked by priority, then by order.
    - A "show nothing" rule silences the card. A smart rule (sgpric, sgads0, sgretn, sgengd, sgcust, sgchko) gives way when the owner's own talking rule applies.
    - The legacy `proactivePrompt*` teaser has a delay of 8. Text is capped at 140 characters.
  - `frequencyAllows`:
    - page: 300s rest after a close or after the chat opens.
    - session: once per app session.
    - visitor: once every N days.
- **`FloatingWidget/useGreeting.ts`** holds the state, stored per bot as `{visits, chatted, shows, dismissed, closedAt, shownAt, last}`.
  - Visits are counted once per app session.
  - The X calls `dismiss()`, which records a dismissal of that rule. Tapping the card calls `closeForNow()`, which only rests it.
  - Opening the chat sets `chatted` and rests the card.
  - Signed in means the visitor context has an id or email.
- **`FloatingWidget/GreetingCard.tsx`** draws the 11 styles: card, note, imessage (default), postcard, sticky, typewriter, glass, notification, neon, voice, aurora. The geometry, colours, reveal timings and native font fallbacks are in RN's `docs/greeting-card-native-spec.md`.

## Conventions

- One commit per file: `feat|fix|style|test|docs|ci(scope): ... (#issue)`.
- One GitHub issue per bug, in this repo. Close it with the commit hashes.
- Update the README in the same batch.
