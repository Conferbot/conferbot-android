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
| #23 | Greeting card: rules, smart greetings, frequency, 11 styles (Compose) | `core/customization/Greeting.kt`, `ui/compose/GreetingState.kt`, `ui/compose/GreetingCard.kt`, `ui/compose/ConferBotWidget.kt` |
| #24-#28, #37, #59-#62 | Handover lifecycle: Connecting line and 4 outcomes, 20s waiting signal, leave the queue, live chat back after a restart, header keeps the bot with Online and the reply time, "Talk to a person" only when allowed (Compose + XML) | `core/livechat/HandoverState.kt`, `HandoverWaiting.kt`, `ReplyTime.kt`, `HandoverLifecycle.kt`, `core/agent/HandoverPayload.kt`, `core/NodeFlowEngine.kt` (`waitAt`, `resumeFrom`), `core/Conferbot.kt`, `ui/compose/chat/LiveChatCards.kt`, `ChatHeader.kt`, `messages/MessageBubble.kt`, `ui/views/ChatActivity.kt`, `MessageAdapter.kt` |
| #53, #36, #45 | Help centre: loads from `fetched-chatbot-data`, bodies on open, follows `enableKnowledgeBase` / `hideRecentArticles`, Chat / Articles tabs, AI sources and article cards open by id (Compose) | `core/helpcentre/HelpCentre.kt`, `services/KnowledgeBaseService.kt`, `ui/compose/ChatScreen.kt`, `ui/compose/knowledgebase/KnowledgeBaseScreen.kt`, `ui/compose/messages/AgentReply.kt` |

## Left in this SDK

1. **Greeting card (#23): done, merged into main.**
   - Checked on the emulator (Pixel, 1080x2400) against the local embed-server: all 11 styles show, with the bot's avatar, name, "Online" and the AI agent's 3 suggested questions. `GreetingTest` has 11 tests.
   - **Why "no card" before (#56):** not a code fault. The tap at (538,968) hits "Identify Test User", not "Open Chat (Compose)". A signed-in visitor matches the smart "customers" rule (`sgcust`, show nothing), which silences the default message too, as on the web. Test without identifying, or clear the app's data.
   - Fixed on the way (#55): settings arriving again (the theme palette, a socket reconnect) no longer re-count and replay a card already showing. Also: the phone-text tail was drawn in px (hidden under the bubble), note/sticky now use the system "casual" hand, note chips are plain words with a wavy underline, postcard/notification names are upper case, the stamp's heart is the text glyph, typewriter padding is the base 14.
   - **Gaps left:** the word fade-up, the "Online" ping and the figure gestures are not drawn; glass and notification have no live blur (more opaque instead); the card uses the system font, not `c.font`; a rule's `action` (flow, ai, link) is not mapped to the tap yet (RN does not either); mascots in the card wait for the mascot work.
2. **Help centre (#53, #36, #45): done.**
   - The data comes from `fetched-chatbot-data.knowledgeBaseData` (no bodies). Bodies come from `get-kb-article` (ack). Search adds `search-kb-articles` hits after the local ones. The old `get-knowledge-base-data` had no server handler and is gone.
   - It shows only while `enableKnowledgeBase` is on and an article is published (`HelpCentre.visible`). The app has no home screen, so recent articles head the Articles tab unless `hideRecentArticles` is set.
   - AI sources with an `articleId`, and article cards without a `url`, call `Conferbot.openHelpArticle`. Back returns to the chat (web `agentSources.ts`, `agentRich.ts`).
   - Checked on the emulator against the local embed-server (bot 6ab686f8d47ff029b76892f7, 26 articles): the list, an article's body and an AI source opening it. An injected `agent-message` was used for the source, not a real AI reply. `HelpCentreTest` has 11 tests on a captured payload (`src/test/resources/help-centre-payload.json`).
   - **Gaps left:** no help centre in the XML chat. No "While you wait" articles. No public `REST /mobile/chatbot/:id/kb` fallback (the socket path is used, as on the web). Article HTML is shown as plain text.
3. **Handover lifecycle (#24-#28, #37, #59-#62): done, ported from RN.**
   - An AI escalating (`agent-left` with `reason: escalated`, `handover: true` or an AI leaver) draws "Connecting you with the team…" with its dots; `visitor_inactive` draws nothing. The line settles in place: "Connected with the team", "The team couldn't join just now", "You left the queue", "Chat ended before the team joined". A person after an AI handover gets "They can see your conversation so far". A repeat accept is not announced.
   - `cb:waiting-for-human` 20s into a wait (held while the chat is closed), `cb:human-joined`, `cb:wait-over`: `Conferbot.onWidgetSignal(name) { }`.
   - The wait card's Cancel and `Conferbot.leaveQueue()` send `queue-self-resolve` and wait for the answer (`not_waiting` asks `live-chat-state`; live means stay). `queue-left` from another device leaves too. `endChat()` no longer sends the agent-only `end-chat` (#60). The handover survey's rating goes as `submit-chat-rating` (#61).
   - A restored session (`restorePersistedSession`) joins the room, asks `live-chat-state` and comes back live with the person (and the join line it missed), back on the handover step's wait without a second request (`NodeFlowEngine.waitAt`), carries on, or runs the step again. Settled lines are saved (a message saved again now replaces itself, #62).
   - The header keeps the bot's name and logo; its Online line shows the reply time ("Typically replies in a few minutes", "Online · team replies by email") until a person is in the chat, then plain "Online". An agent bot shows no reply-time line.
   - "Talk to a person" is offered only for an agent bot, only while `talkToPersonAllowed`; it goes on tap and does not come back; the owner's quick actions stay.
   - `initiate-handover` carries the step's `enableHandoverEmail`, sound settings, `assignmentMethod` / `requiredSkills` (#59).
   - Tests: `HandoverStateTest` 16, `HandoverWaitingTest` 5, `HandoverLifecycleTest` 12, `ReplyTimeTest` 7, `HandoverPayloadTest` 3, `LiveChatTest` +1.
   - Checked on the emulator against the local embed-server with a node script playing the agent (temp bot, handover email off, deleted after): wait card and Cancel (the ticket closed `self_service`, `handoverData.enableHandoverEmail: false`), header reply time, agent joined with the header still the bot, the Connecting line, its settled outcome with the note, and a restart while live (live again, one ticket, agent messages arrive).
   - **Gaps left:** the XML chat's Connecting line has no dots (the words only). `queue-help` (wait articles) is #40. No requeued / transferred lines (#44). #63: Clear History draws flow messages twice (found during the check).
4. The rest of the list below.
5. **XML gaps:** initials avatar and the wave typing style.
6. **Test debt:**
   - #17: three test classes run the 2g test heap out of memory.
7. **#21 not yet checked on a device:** pass a user, send a message, then look for `visitorContext` on the response record.

## How to test

- **Unit tests:**
  - Run one class at a time: `sh ./gradlew :conferbot:testDebugUnitTest --tests 'com.conferbot.sdk.core.customization.*' --no-daemon -q`.
  - Results are in `conferbot/build/test-results/testDebugUnitTest/`.
- **Example app:**
  - To point it at a local server, add `baseUrl = "http://10.0.2.2:8001/api/v1/mobile/", socketUrl = "http://10.0.2.2:8001"` to `ExampleApplication.kt`.
  - Build with `sh ./gradlew :example:assembleDebug`. Package `com.conferbot.example`.
  - Force-stop before relaunching, or it resumes the XML chat.
  - The launcher activity is `.ui.xml.MainActivity`. On a 1080x2400 Pixel, "Open Chat (Compose)" is at (540,833); (538,968) is "Identify Test User", which signs the visitor in and so silences the greeting.
  - `uiautomator dump` hangs on the Compose screen (the launcher's motion never idles); wait with a sleep instead.
  - The emulator may come up with no network: `adb reverse tcp:8001 tcp:8001` and `http://127.0.0.1:8001` instead of 10.0.2.2. A temp bot copied from 6ab686f8d47ff029b76892f7 needs `version: "v2"` and no `creationType` (an `ai_agent` copy loads as an agent bot).
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
| Greeting card (rules, frequency, 11 styles) | done | done (#23) | todo | todo |
| Launcher entrance / attention / idle call | todo | todo | todo | todo |
| Home screen | todo | todo | todo | todo |
| Mascots (incl. `mascotInGreeting`) | todo | todo | todo | todo |
| `widgetLanguage` (i18n) | todo | todo | todo | todo |
| Message sounds | todo | todo | todo | todo |
| Celebrations / reactions | todo | todo | todo | todo |
| Reply-time wording (header) | done | done (#28) | todo | todo |
| Handover lifecycle (Connecting line, outcomes, waiting signals, leave queue, restore) | done | done (#24-#27, #37) | todo | todo |

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
