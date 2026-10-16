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
| #73 | Launcher entrance (12 effects), attention (8 modes) and idle call (5), ported from RN #57 (and as Flutter #73). `Keyframes.kt` evaluates the web's Web Animations tracks and passes RN's Chrome golden (447 cases, 19,275 samples; largest error 0.0007px, 0.005deg, 0.00005 scale and opacity, 0.03px `left`, 0.42px on the plane's path). The tracks are rebuilt from the same code and match the golden's. The rules are in the three controllers (Compose only) | `core/customization/Keyframes.kt`, `LauncherEntrance.kt`, `LauncherAttention.kt`, `LauncherIdleCall.kt`, `MascotArt.kt`, `FxPaint.kt`; `ui/compose/LauncherFxControllers.kt`, `LauncherFxViews.kt`, `FxSvg.kt`, `ConferBotWidget.kt`; `src/test/resources/launcherfx-golden.json` |
| #21 | User as `visitorContext` + identity hash | `models/Configuration.kt` (`toVisitorContext`), `core/state/ChatState.kt`, `core/Conferbot.kt` |
| #23 | Greeting card: rules, smart greetings, frequency, 11 styles (Compose) | `core/customization/Greeting.kt`, `ui/compose/GreetingState.kt`, `ui/compose/GreetingCard.kt`, `ui/compose/ConferBotWidget.kt` |
| #24-#28, #37, #59-#62 | Handover lifecycle: Connecting line and 4 outcomes, 20s waiting signal, leave the queue, live chat back after a restart, header keeps the bot with Online and the reply time, "Talk to a person" only when allowed (Compose + XML) | `core/livechat/HandoverState.kt`, `HandoverWaiting.kt`, `ReplyTime.kt`, `HandoverLifecycle.kt`, `core/agent/HandoverPayload.kt`, `core/NodeFlowEngine.kt` (`waitAt`, `resumeFrom`), `core/Conferbot.kt`, `ui/compose/chat/LiveChatCards.kt`, `ChatHeader.kt`, `messages/MessageBubble.kt`, `ui/views/ChatActivity.kt`, `MessageAdapter.kt` |
| #29-#34, #48, #54, #64-#67 | Flow nodes: the engine and every node handler run the shared flow-node spec. **156 of 156 fixtures pass** (`FlowFixturesTest`); none pending. See "Flow nodes" below. | `core/NodeFlowEngine.kt`, `core/flow/*` (FlowText, FlowLogic, FlowControl, FlowRich, FlowInputs, FlowCalendar, FlowRuntime), `core/privacy/Redact.kt`, `core/nodes/handlers/*` |
| #47 | Device language: every response record carries `visitorInsights: {v: 1, language: {browser}}` (the phone's language, BCP 47, "de-DE"), so the AI answers short messages in it | `core/state/DeviceLanguage.kt`, `core/state/ChatState.kt` `buildResponseData` |
| #46 | "Helpful?": a No is sent at once; reasons, an optional comment, "Talk to a person" (only while nobody is coming or here), "Ask another way" (focuses the composer); the rated line's thumbs change the vote; the vote is kept on the answer's record entry (`feedback`); only the newest unrated answer asks (Compose) | `core/agent/AnswerFeedback.kt` (`Helpful`), `ui/compose/messages/AgentReply.kt`, `core/Conferbot.kt` `sendAnswerFeedback`, `ChatState.setEntryField` |
| #44 | Back in the queue (`live-chat-requeued`): "Connecting you to another agent..." / "Connecting you to <team>...", the agent cleared, a flow on its handover step waits again. `live-chat-transferred` is kept in the record, not drawn (as the web). `isRepeatJoin` / `afterAI` were already in the handover port | `core/livechat/LiveEvents.kt`, `HandoverLifecycle.kt` `requeued` |
| #43 | Blocked visitor (`visitor-blocked`, or `connect_error` `visitor_blocked` at the handshake, which now carries `visitorId` in query and auth): "This conversation has been closed.", no input, no new chat, the flow stopped. The same `chat-ended` again draws nothing and opens no second rating; each end has its own record id. A chat ended with nothing going on: composer off and "Start a new chat" (Compose + XML) | `core/livechat/LiveEvents.kt`, `services/SocketClient.kt`, `LiveChatExtras.kt` `block`, `HandoverLifecycle.kt` `blocked`, `core/Conferbot.kt`, `ui/compose/chat/LiveChatCards.kt` `ClosedChatCard`, `ChatScreen.kt`, `ui/views/ChatActivity.kt` |
| #52 | Markdown as the web's `utils/agentHtml.ts`: tables (sideways scroll), h3-h6 (# and ## drawn as 4), quotes, pre, `~~strike~~` (from a person's `<blockquote>`, `<pre>`, `<s>`/`<del>` too); the "That took too long..." notice when the 45s AI wait runs out (not after the AI leaves or the chat ends); `isFallback` replies italic at 85% (Compose + XML) | `utils/ChatMarkdown.kt`, `ui/compose/messages/AgentReply.kt` `ChatMarkdownText`, `ui/views/ChatMarkdownSpans.kt`, `core/agent/AgentBot.kt` (`AWAIT_REPLY_MS`, `REPLY_TIMEOUT_TEXT`), `MessageBubble.kt`, `MessageAdapter.kt` |
| #42 | "Email me this chat" in the chat menu: one card (email filled in when known), the server's reason when it does not go, "Sent! Check your inbox at ...", the address kept as the visitor's. `POST {mobile base}/chat/transcript-email` (embed-server #193) | `core/livechat/EmailTranscript.kt`, `services/ApiClient.kt` `sendTranscriptEmail`, `LiveChatExtras.kt`, `LiveChatCards.kt` `EmailTranscriptCard`, `ChatHeader.kt` |
| #72 | A live chat's end asks the handover step's post-chat survey questions (`enablePostChatSurveyQuestions`, the builder's key) before the flow carries on from the step. RN's #66 (the end resetting the flow) never applied here: `NodeFlowEngine.handleChatEnded` already carried on | `core/nodes/handlers/SpecialNodeHandlers.kt` `onChatEnded` |
| #40, #75 | While you wait, ported from RN #44, #41 (Compose), a server-picked article the help centre cannot open fetched by id and shown on its own (#75): the help card 6s into a wait for a person (up to two help-centre articles for the visitor's last real question, only with the help centre on), the server's `queue-help` pick (or a waiting `queue-status`'s `help`) with "That solved it" (`queue-self-resolve {chatSessionId, articleId}`, then "You left the queue. Glad that helped.", recorded "Resolved while waiting"), the games offer 2.5s after `cb:waiting-for-human` with 3 retries (owner setting `waitingGames`; seven games with the web's rules), "Not now" / x for the app session, "play" typed while waiting opens the menu, a game saved when a person joins ("Finish my game"), reduced motion, cards never saved | `core/waiting/WaitArticles.kt`, `WaitingGames.kt`, `GamesLogic.kt`, `GamesCard.kt`, `WaitingExtras.kt`; `core/livechat/HandoverLifecycle.kt` (`WaitHooks`, `leftQueueHelped`), `LiveChatExtras.kt` (`onQueueStatus`), `core/Conferbot.kt` (`waiting`, `openWaitHelpItem`, "play" in `sendMessage`), `ui/compose/chat/WaitingCards.kt`, `MessageList.kt` |
| #41, #79 | "Your requests", ported from RN #45 (Compose), a sign-in or `identify(null)` sign-out dropping the last user's list at once (#79): the visitor's own conversations with the team (a chat this device started that they wrote in, matched with their visitor id, or a signed-in user with a valid `user.hash`), each one's state in words, "New reply from <name>" and the unread count (`Conferbot.requestsUnread` for a badge); one request's banner, conversation and reply box (a reply to a solved one opens it again; Not sent / Try again; closed for replies); team replies live while open, read again after a reconnect. In the chat menu once there is one; `Conferbot.openRequests()` and `RequestsPortalView` for an app's own entry | `core/requests/Requests.kt`, `RequestsPortal.kt`, `RequestsExtras.kt`; `core/Conferbot.kt` (`requests`, `openRequests`, `requestsUnread`), `ui/compose/requests/RequestsPortalView.kt`, `ui/compose/ChatScreen.kt`, `chat/ChatHeader.kt` |
| #78, #77 | Home screen, ported from RN #68, #69 (Compose), when the owner has it on (`enableInitialPage`; none at all when off): the salutation by the clock or "Welcome back, Jamie", the brand, the owner's words, "Ask anything" (`homeAskBox`: ask the agent, the articles that answer it, the server's `search-kb-articles` body search), "Pick up where you left off" (agent bots), "Your requests" (two rows, See all (n), above "Talk to us" when a reply is new), "Talk to us" (`homePeople` reply time, `hideOnlineStatus`, the team's faces, the agent's questions, the rating), the help card (recent articles unless `hideRecentArticles`, topics, See all; its own search with the ask box off), the customer logos; home, chat and help centre with the chat header's ‹ and the system back, in the owner's `viewTransition`. `{{first_name\|there}}` filled in the owner's texts, greeting card too (#77) | `core/home/Home.kt`, `ViewTransition.kt`, `HomeExtras.kt`; `core/customization/Personalize.kt`; `core/livechat/ReplyTime.kt` (`homeLine`, `homePeople`); `core/Conferbot.kt` (`home`, `homeView`, `showHome`, `showChat`, `showHelp`, `personalized`); `core/requests/RequestsExtras.kt` (`seen`); `ui/compose/home/HomeScreenView.kt`, `ViewSwitch.kt`; `ui/compose/ChatScreen.kt`, `chat/ChatHeader.kt` (`onHome`), `knowledgebase/KnowledgeBaseScreen.kt` (`initialCategoryId`, `backAtRoot`), `ConferBotWidget.kt` (a greeting send goes past the home) |
| #53, #36, #45 | Help centre: loads from `fetched-chatbot-data`, bodies on open, follows `enableKnowledgeBase` / `hideRecentArticles`, Chat / Articles tabs, AI sources and article cards open by id (Compose) | `core/helpcentre/HelpCentre.kt`, `services/KnowledgeBaseService.kt`, `ui/compose/ChatScreen.kt`, `ui/compose/knowledgebase/KnowledgeBaseScreen.kt`, `ui/compose/messages/AgentReply.kt` |

## Left in this SDK

1. **Greeting card (#23): done, merged into main.**
   - Checked on the emulator (Pixel, 1080x2400) against the local embed-server: all 11 styles show, with the bot's avatar, name, "Online" and the AI agent's 3 suggested questions. `GreetingTest` has 11 tests.
   - **Why "no card" before (#56):** not a code fault. The tap at (538,968) hits "Identify Test User", not "Open Chat (Compose)". A signed-in visitor matches the smart "customers" rule (`sgcust`, show nothing), which silences the default message too, as on the web. Test without identifying, or clear the app's data.
   - Fixed on the way (#55): settings arriving again (the theme palette, a socket reconnect) no longer re-count and replay a card already showing. Also: the phone-text tail was drawn in px (hidden under the bubble), note/sticky now use the system "casual" hand, note chips are plain words with a wavy underline, postcard/notification names are upper case, the stamp's heart is the text glyph, typewriter padding is the base 14.
   - **Gaps left:** the word fade-up, the "Online" ping and the figure gestures are not drawn; glass and notification have no live blur (more opaque instead); the card uses the system font, not `c.font`; a rule's `action` (flow, ai, link) is not mapped to the tap yet (RN does not either); mascots in the card wait for the mascot work.
2. **Help centre (#53, #36, #45): done.**
   - The data comes from `fetched-chatbot-data.knowledgeBaseData` (no bodies). Bodies come from `get-kb-article` (ack). Search adds `search-kb-articles` hits after the local ones. The old `get-knowledge-base-data` had no server handler and is gone.
   - It shows only while `enableKnowledgeBase` is on and an article is published (`HelpCentre.visible`). Without a home screen, recent articles head the Articles tab unless `hideRecentArticles` is set; with one (#78) the help centre is its own view, opened from the home (no Chat / Articles tabs then).
   - AI sources with an `articleId`, and article cards without a `url`, call `Conferbot.openHelpArticle`. Back returns to the chat (web `agentSources.ts`, `agentRich.ts`).
   - Checked on the emulator against the local embed-server (bot 6ab686f8d47ff029b76892f7, 26 articles): the list, an article's body and an AI source opening it. An injected `agent-message` was used for the source, not a real AI reply. `HelpCentreTest` has 11 tests on a captured payload (`src/test/resources/help-centre-payload.json`).
   - **Gaps left:** no help centre in the XML chat. No public `REST /mobile/chatbot/:id/kb` fallback (the socket path is used, as on the web). Article HTML is shown as plain text.
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
   - **Gaps left:** the XML chat's Connecting line has no dots (the words only). `queue-help` is done with while you wait (#40). #63: Clear History draws flow messages twice (found during the check), and "Start a new chat" goes the same way.
4. **Flow nodes (#29-#34, #48, #54, #64-#67): done, ported from RN, with a Compose card for every step.** See "Flow nodes" below.
5. **Live chat extras (#42-#44, #46, #47, #52, #72): done, ported from RN.** See the table above and "Porting the live chat extras" in RN's `docs/PARITY.md`.
   - Tests: `DeviceLanguageTest` 5, `AnswerFeedbackTest` 9, `LiveEventsTest` 7, `EmailTranscriptTest` 4, `SocketHandshakeTest` 2, `ChatMarkdownTest` 8, `HandoverLifecycleTest` +4, `NodeFlowEngineTest` +2, `AgentBotParserTest` +1.
   - Checked against the local embed-server: a socket handshake with `visitorId` in query and auth connects. The transcript route was not called (the running server predates it, and no real email is sent); its answers are covered by `EmailTranscriptTest` on the server's own shapes.
   - **Gaps left:** the XML chat has no "Helpful?" row and no chat menu (its footer shows the email card when `Conferbot.liveChat.openEmailTranscript()` is called), and draws a table as rows of text. A vote's comment is not kept in the local message store (it is on the record entry and the server). Nothing checked on a device yet (no emulator run in this batch).
6. **Launcher entrance / attention / idle call (#73): done, ported from RN #57.**
   - `Keyframes.kt` is RN's `keyframes.ts`: offsets spread evenly, per-property keyframes, each keyframe's easing on its step, the options' easing on the iteration, delay / iterations / fill, the later animation winning; a property missing at 0% starts from the element's own value but eases with the 0% keyframe's easing; `rotate: "y 180deg"` is drawn as `scaleX(cos a)`.
   - The golden is RN's `scripts/parity/launcherfx/golden.json` at RN 60209c8, copied to `src/test/resources/launcherfx-golden.json`. `LauncherFxGoldenTest` checks the evaluator on every sample (within 0.01px / 0.01deg, 0.001 scale and opacity, 0.05px `left` on Chrome's 1/64 px grid, 0.6px on the flattened plane path) and rebuilds every case's tracks from `LauncherAttention`, `LauncherIdleCall` and `LauncherEntrance` (seed 4242), numbers compared within 1e-9 (inside strings too: the JVM's sin / cos can differ from V8's in the last digit).
   - The rules (`LauncherFxControllers.kt`, plain Kotlin on an `FxScheduler`; `LauncherFxControllersTest` 13 on a fake clock): entrance decided when the settings arrive, once per app session per bot and entrance, held invisible (still tappable) until `revealAt`, the greeting hidden until `settleAt` then faded up; attention 1.5s after the launcher (or card) shows, paused during an idle / message moment, over once the chat opens, not within 30 minutes of an opening, holding the idle moments and the three load ripples; idle call after the delay, every 4s while blocked (chat open, app paused, keyboard up, entrance busy, greeting up under 15s), twice per session 3 minutes apart, then 3 days of rest in SharedPreferences (`conferbot_idlecall_rest_<bot>`), also after an opening; closing the greeting stops calls. Reduced motion is the animator duration scale at 0.
   - Drawing: one frame clock while an effect plays, read only in `graphicsLayer` / draw lambdas (redraws, no recomposition). Sprites and the mascot are SVG markup drawn by `FxSvg` on the platform canvas; full-screen canvases behind and in front of the launcher hold the stage.
   - **Gaps left:** web filters (brightness, glows, blur, the mascot's bevel) are not drawn, as in RN and Flutter; the waving hand is the system emoji; the keyboard is seen only when the app lets Compose see the IME insets. Not checked on a device yet (no emulator run in this batch).
7. **While you wait (#40): done in Compose, ported from RN #44, #41.** See "Porting while you wait" in RN's `docs/PARITY.md`; the four core files are RN's, with their tests.
   - **Where the wait starts and stops** (`HandoverLifecycle` through `WaitHooks`): starts on a flow handover with no `assignedAIAgents` (`flowWaitStarted`, which also covers a restored chat still in the queue, since `waitAt` puts the step back in its waiting state), and on every AI escalation (`agent-left` handover); not on a requeue. Stops on `agent-accepted`, `handover-waiting`, `no-agents-available`, `chat-ended`, `ai-agent-inactive-close`, a blocked visitor, leaving the queue, and `cb:human-joined` / `cb:wait-over`.
   - **The question** is read from `ChatState.getRecordForServer()`: the newest entry whose `type` or `shape` is a typed message (`user-message`, `user-input-response`, `user-live-message`, `user-input`). RN reads `type || shape`; this record leaves `type` empty on some answers, so both are looked at.
   - **The help centre** is `fetched-chatbot-data`'s `knowledgeBaseData` as sent (raw maps, `WaitingExtras.setKnowledgeBase`), for the articles and the quiz; `HelpCentre.parse` still feeds the Articles tab.
   - **Where the cards sit:** `WaitingExtras` keeps each card with the id of the item that was last on screen when it was drawn (`PlacedCard.after`). `MessageList` draws it after that item; a card whose item is not on screen is drawn at the end. Nothing goes into `_record`, the Room store or the server record.
   - **Threads:** socket callbacks and the handover's signals are posted to the main thread (`post`); timers are `Conferbot`'s coroutine timer on `Dispatchers.Main`.
   - **Articles:** a row calls `Conferbot.openWaitHelpItem`, which opens the article in the chat's help centre (`openHelpArticle`, the Articles tab) while it can (`HelpCentre.waitArticleRoute`, rule 5); a page opens in the browser. **#75:** an article the help centre cannot open (off, nothing published, or not in its data, e.g. the server's `queue-help` pick) is fetched by id with socket `get-kb-article {chatbotId, articleId}` (ack `{success, article}`, 8s, the help centre's own body fetch; RN uses `GET /mobile/knowledge-base/article/:id`, iOS #83 the same socket call) by `WaitingExtras.openArticleById` into `waiting.article` (`WaitArticleView`: loading, the article, or failed with Try again; an answer for an article closed or replaced meanwhile, or after `reset`, is dropped) and drawn on its own over the chat by `ui/compose/chat/WaitArticleSheet.kt` (the help centre's `ArticleDetailView`; Back, the system back and the breadcrumb close it).
   - **The look** (`WaitingCards.kt`): the help card in the bot bubble's colours (radius 14, padding 10, rows at 10% of the text colour, "Read", the pill button with a 1pt line at 45%); the games card on the theme's card tokens (`rememberStepLook`) with `pickAccent` and the theme font, max width 320, radius 18, a 1.5pt accent line on a win. Motion: the card rises 8dp in 450ms, the hands shake three times in 900ms, a 1.5% cheer, 34 confetti pieces on a win, the scramble's nudge; all off when the animator duration scale is 0 (also passed to `GamesCard` so the bot moves at 250ms and the hands show at once). Memory cards turn without the web's 3D flip, as in RN.
   - Tests: `GamesLogicTest` 17, `WaitingGamesTest` 14, `WaitArticlesTest` 12, `GamesCardTest` 25, `WaitingExtrasTest` 16 (the handover and the cards together on a fake clock and socket).
   - **Gaps left:** the XML chat draws neither card, and there the offer is never made and "play" is sent as words (`canDraw` is the Compose list being on screen; #74). The scramble takes taps only (no hardware keys wired in the view; `GameInput.Key` exists). Not checked on a device (no emulator run in this batch) nor against the local server from this SDK; the server side was checked from RN.
8. **Your requests (#41): done in Compose, ported from RN #45.** See "Porting Your requests" in RN's `docs/PARITY.md`; the two core files are RN's `requests.ts` and `requestsPortal.ts` with their tests, `RequestsExtras` is RN's `useRequestsPortal`.
   - **Who:** every call carries `{visitorId, sessions, user?}`. `visitorId` is `ChatState.visitorId` (what the response records carry), else the handshake's (the user's id, else this device's). `sessions` are the last 30 chats on this device the visitor wrote in (a `user-*` entry in `ChatState.record`), newest first. `user` is `{id, email, hash}` of the identified `ConferBotUser`; the server lists their verified conversations only with a valid hash. Nothing is asked with no chat on the device and no hash.
   - **Memory:** per bot in SharedPreferences (`conferbot_requests`, key `conferbot_requests_<botId>`): `{sessions, seen}` as JSON (Gson), the newest 60 read marks kept; a read mark only moves forward and is the later of now and the request's `lastAt`.
   - **Calls** (`RequestsApiClient`, 10s answer timeout through `SocketClient.emitWithAck`): `my-requests` -> `{ok, requests}`; `my-request-open {..who, chatSessionId}` -> `{ok, request}` (joins its live room); `my-request-close {chatSessionId}` (no answer); `my-request-reply {..who, chatSessionId, text, entryId}` -> `{ok, request}` or `{ok: false, reason: locked | not_found | empty | error}`, no answer is `offline`. Entry ids are `request-reply-<base36 time><6 base36 random>`, so a retry is one message. `my-request-update {chatSessionId}` re-reads the open request (or the list while the portal is closed); every socket `connect` re-reads the open one.
   - **When the list is read:** on connect, after `identify`, when the chat opens or closes (at most every 15s), when the portal closes, and on `my-request-update` while it is closed.
   - **Threads:** socket answers are posted to the main thread (`Conferbot`'s scope); the portal is a `StateFlow<RequestsPortalState>`. `RequestsExtras.attach()` moves the one update subscription to the SDK's current socket (called at `initialize`).
   - **The look** (`RequestsPortalView`): over the whole chat, its header too; the widget theme's panel, card and ink tokens (else the SDK theme), the theme font, `Requests.accent` (brand colour unless a theme palette, header, launcher, the first not too light, else #4F46E5) for Back, the visitor's bubbles and Send. RN's sizes: a 56dp bar, 40dp state icons (Material outlined icons in place of the web's SVGs), bubbles 86% wide with the speaker's corner at 4, links underlined and tappable, the reply box 46-120dp with a 1.5dp line (the accent when focused) and `imePadding()`. It slides in 14dp over 220ms, not with the animator duration scale at 0. The system back steps back (`BackHandler`, an `OnBackPressedCallback`); the portal closes when the chat leaves the screen.
   - **Sign-in and sign-out (#79, as Flutter #79 and iOS #86):** `Conferbot.identify(user)` and `identify(null)` (sign out) call `RequestsExtras.onUserChanged`: the list and the unread count go at once, the portal closes and its drafts go, and the list is read again for whoever is signed in now (even within 15s). Each read carries the identity it was asked for (`who`, bumped on every change); an answer for an earlier identity is not shown, and the list is then asked again for the current one. Signed out with no chat on this device, nothing is asked.
   - Tests: `RequestsTest` 20, `RequestsPortalTest` 16, `RequestsExtrasTest` 12.
   - **Checked against the local embed-server** (:8001, bot 6ab686f8d47ff029b76892f7) from a temporary JVM test driving `RequestsApiClient`, `RequestsExtras` and the portal over a real socket.io-client: test conversations and tickets inserted for two test visitors (open with a team reply, solved, locked; another visitor's), listed in order with the unread count, opened, replied (the same entry id twice is one message), a solved one reopened, `locked` and `empty` refused; a second socket of the same visitor heard `my-request-update` and stopped after `my-request-close`; the other visitor, the right sessions with the wrong visitor id, a claimed foreign session id and a user with a forged hash all saw nothing of the first visitor's. The reply path only notifies agents present on the ticket (none here): no email or Slack. Everything inserted (responses, tickets, synced messages) was deleted after.
   - **Gaps left:** no XML entry or portal (#76; the core and `Conferbot.openRequests()` work there, only the screens are Compose). The home screen's card (`ON_HOME`, "See all (n)") is done with the home (#78); the chat menu entry stays for bots without a home. English only. Not checked on a device (no emulator run in this batch).
9. **Home screen (#78) and greet by name (#77): done in Compose, ported from RN #68, #69.** See "Porting the home screen" in RN's `docs/PARITY.md`; `Home.kt`, `ViewTransition.kt` and `Personalize.kt` are RN's `home.ts`, `viewTransition.ts` and `personalize.ts` with their tests, `HomeExtras` is RN's `useHomeScreen`, `ReplyTime.homeLine` / `homePeople` are RN's.
   - **The view** is `Conferbot.homeView` (`StateFlow<HomeView>`: `Home.safeView` of the settings and the view chosen): it starts on the home and keeps the last view while the app runs. `showChat(text)` goes to the conversation and sends `text` 400ms later, once the chat has a session (as the greeting card's send). `showHelp(categoryId)` remembers the view it came from; the help centre's Back goes there. The system back (`BackHandler` in `ChatScreen`, only with a home): help centre -> where it came from, chat -> home, home -> the chat closes; the requests portal and an open article step back first (their own handlers).
   - **Memory:** `{at, name?, topic?, session?}` per bot in SharedPreferences (`conferbot_home`, key `conferbot_memory_<botId>`), forgotten after 60 days. A typed message in a live or agent chat is remembered (`sendMessage`); the name a flow stored (`ChatState.userMetadata`) is the first name.
   - **The help centre data** is `fetched-chatbot-data`'s `knowledgeBaseData` as sent (raw maps, `HomeExtras.setKnowledgeBase`), drafts filtered as RN. `search-kb-articles {chatbotId, query, limit: 10}` (5s, cached per answered query, 280ms after the last key, only when an article has no body).
   - **Greet by name:** `Personalize.customizations` runs on the bot's settings before anything reads them (`applyLook`, and the first assignment in `handleFetchedChatbotData`), on `chatIconCtaText`, `initialPageTitle`, `initialPageDescription`, `initialPageCardButtonText` and each greeting rule's text; the vars are the `ConferBotUser`'s `{...metadata, name}` and the name a flow asked for. `identify()` fills them again. Values stay as typed.
   - **Receipts** count the chat as visible only on the conversation view (`liveChat.reportReceipts` with `homeView == CHAT`). The requests list is read again when the home comes into view (at most every 15s); a request that is the chat on screen opens the conversation.
   - **The look** (`HomeScreenView`, RN's sizes from web `polish.css` Home and `polish-home.css`): the page in `Home.palette` (the theme's `bg2` / `card` / `card2` / `ink` / `muted` / `line`, else the classic widget's), padding 22/16/24, the band a 150deg CSS-style gradient of the launcher's two colours (white ink, #0f172a when both are light) from the top to the middle of "Talk to us" (at most 72dp into it), scrolling with the content (a `Column` with `verticalScroll`, not a `LazyColumn`: the page is short and the band is measured from the card); cards radius 18 with a soft shadow, the theme font, `Requests.accent`. Each part rises 8dp in 450ms once (100, 160, 220, 300ms in), none with the animator duration scale at 0. Icons are Material ones in place of the web's SVGs; the logos are drawn at 55% (no greyscale filter) and show once one has loaded.
   - **View transitions** (`ViewSwitch`): only the new view moves, in `graphicsLayer`: fade 340ms after 40ms from 103.5%, slide 420ms from the sweep's side, the other kinds slide, classic / none at once, reduced motion a 200ms linear fade.
   - Tests: `HomeTest` 40, `HomeExtrasTest` 10, `ViewTransitionTest` 4, `PersonalizeTest` 6, `ReplyTimeTest` +2, `GreetingTest` +1.
   - **Gaps left:** no home screen in the XML chat (#80). The help centre view is the existing `KnowledgeBaseScreen` with its Material top bar, not the theme's colours. The mascot slot (`HomeScreenView(mascot = ...)`, `HomeModel.mascot`) is empty until the mascot work. Not in an app, on purpose (as RN): `pageAware.ts`, the three-article cap, `keepsAgentChat`, the logos' greyscale. English only. Not checked on a device (no emulator run in this batch).
10. **XML gaps:** initials avatar, the wave typing style, the step cards (XML draws the rich steps with its generic cards), the while-you-wait help card and games (#74), "Your requests" (#76) and the home screen (#80).
11. The rest of the list below.
12. **Test debt:**
   - #17: three test classes run the 2g test heap out of memory.
   - `SessionPersistenceManagerTest` (27) and 2 of `MemoryManagerTest` already failed before the flow work: their setup touches `ChatState` without `Dispatchers.setMain`.
13. **#21 not yet checked on a device:** pass a user, send a message, then look for `visitorContext` on the response record.

## Flow nodes

The engine (`core/NodeFlowEngine.kt`) and the handlers (`core/nodes/handlers/*`) follow the shared flow-node spec in conferbot-react-native `scripts/parity/flows/` (`nodes.spec.json`, 156 fixtures made by running the web widget's own code). The ports are of RN's `src/core/NodeFlowEngine.ts`, `src/core/flow/*` and `src/core/nodes/handlers/*`.

- **The test:** `FlowFixturesTest` runs each fixture (one JUnit case each) through the real engine and handlers with `FlowDriver`:
  - The clock is virtual (`FlowDriver.VirtualClock`, the engine's `FlowClock`). A settle runs every timeout in time order and stops when only repeating timers are left; a `wait` input runs those too.
  - The bot API answers from `server.http` (`ScriptedApi`). A fake socket answers `execute-integration` and `initiate-handover` from `server.integration` / `server.handover`; `response-record` and `join-chat-room-visitor` are transport, not counted.
  - The engine runs on `Dispatchers.Unconfined`, so everything happens on the test thread.
  - It compares what the README says to match: path (node, type, via), ports, variables (key and value; `*_at` times as timestamps), end, errors (count and node), answers (shape and value fields), rendered plain text, emits, unsupported, http (path and body keys), flowVariables and the typing pace (±50 ms). Objects compare by keys and values, whatever the key order.
  - `TIMING_SKIP` is RN's: 7 cases where the web card's own UI timers (focus, debounce, animation) move the web clock before the next action. Everything else in them is compared.
  - `PENDING_TYPES` is empty.
- **The fixtures** are a copy in `conferbot/src/test/resources/flows/`. After they are regenerated in RN (`node gen.js`), run `scripts/sync-flow-fixtures.sh` and commit the copy (`CONFERBOT_DEV` overrides the folder holding `sdk/`).
- **One case:** `FLOW_CASE=<id prefix> sh ./gradlew :conferbot:testDebugUnitTest --tests '*FlowFixturesTest' --rerun --no-daemon`. `FLOW_DUMP=1` prints each outcome.
- **The rules, briefly:**
  - Start at the first welcome-node. A step that does not branch takes its first edge, whatever the handle. A missing exact handle, a jump to a missing node or no edge ends the chat; an edge to a missing node stops it where it is. Unknown types show nothing and continue on their first edge.
  - The node type is `node.type` (a legacy user-input's `data.type` is its kind).
  - Questions register `{nodeId, answerVariable, null}` when shown and fill it by node; no `answerVariable`, nothing stored. Logic and integrations save by name. Text fills `{x}`, `${x}`, `{{x}}`, `${ANSWERS.x}`, `$${x}`; unknown shows nothing.
  - Typing pace is the web's `paceFor`; images and videos move on at load or after 4000 ms (`mediaSettled`).
  - Masking (`core/privacy/Redact.kt`) applies to answers and the visitor's record entries unless `redactSensitive` is false; the transcript keeps what was typed.
  - GPT is `POST /api/v1/bot/:id/gpt` `{nodeId, transcript}`; AI route / extract and KB answer are `ai/route`, `ai/extract`, `kb/answer`; OTP `otp/send|verify`; payment `pay/start|status`; address `geo/search`; uploads `media`. The origin is the socket server (`Conferbot.serverOrigin`).
  - stripe-node shows the link and moves on after 500 ms; payment-node is the gate.
  - The handover step asks its pre-chat questions in the composer, then sends the record and `initiate-handover`; on `no-agents-available` it says the fallback and, with `chatLeaveMessage` off, carries on.
- **Step cards (#48), Compose:** every waiting step has its web card (`ui/compose/steps/`): consent with links, OTP (digit boxes, resend countdown, skip, change email), address (suggestions, confirm, by hand), signature (Canvas pad, trimmed PNG, typed), matrix grid, ranking, slider, date/time, calendar slots, payment (Pay now / I've paid / Cancel), carousel and product card (link buttons open, Continue when only links), navigate (links open, Done), image choice with its images.
  - The engine stamps each waiting UI state with `NodeUIState.step` (`StepInfo`: type, settings, the handler's extras, the card's refusal, the visit). Handlers add what only they know: the OTP stage, where the code went and the resend wait; the address results; the payment's Checkout, amount and whether it is open; the calendar's open days.
  - The cards answer with `core/flow/FlowStepAnswer` through `Conferbot.answerStep` / `NodeFlowEngine.submitStepResponse`; the step's own reply lines come back on `NodeFlowEngine.visitorLines` and are the visitor's bubbles.
  - A refusal keeps the card's draft (state is kept per visit) and shows its reason on the card, not in the banner. The composer is hidden under OTP, address and signature. The card stays in view as it grows.
  - Tested: `FlowStepViewAnswerTest` plays each step's fixtures as its card sends them (same builders, same card state, only what the card offers) and the QA tour; plus the card state (RankingOrder, MatrixPicks), OTP helpers, the month grid, date rules, base64 and the signature trim. No Robolectric in this project, so the composables themselves were checked on the emulator.
  - **XML gaps:** the XML Views chat still draws these steps with its generic cards (choices, the range, the calendar, text input).
- **Left:** webhook-node still runs from the device, in the background (the spec says webOnly); hidden-fields has no page in an app unless `NodeFlowEngine.Config.pageUrl` is set; the google-calendar "book" picker waits for the server.

## How to test

- **Unit tests:**
  - Run one class at a time: `sh ./gradlew :conferbot:testDebugUnitTest --tests 'com.conferbot.sdk.core.customization.*' --no-daemon -q`.
  - Results are in `conferbot/build/test-results/testDebugUnitTest/`.
  - The flow-node fixtures: `--tests '*FlowFixturesTest'` (see "Flow nodes"); the step cards' answers: `--tests '*FlowStepViewAnswerTest'`.
  - The launcher entrance, attention and idle call: `LauncherFxGoldenTest` (RN's Chrome golden) and `LauncherFxControllersTest` (the rules on a fake clock).
  - While you wait: `GamesLogicTest`, `WaitingGamesTest`, `WaitArticlesTest`, `GamesCardTest`, `WaitingExtrasTest` (`--tests 'com.conferbot.sdk.core.waiting.*'`, one class at a time).
  - The home screen: `HomeTest`, `HomeExtrasTest`, `ViewTransitionTest` (`--tests 'com.conferbot.sdk.core.home.*'`, one class at a time), `PersonalizeTest`, `ReplyTimeTest`.
  - The live chat extras: `DeviceLanguageTest`, `AnswerFeedbackTest`, `LiveEventsTest`, `EmailTranscriptTest`, `SocketHandshakeTest`, `ChatMarkdownTest`, `HandoverLifecycleTest`, `NodeFlowEngineTest`.
  - "Email me this chat" on a device: the route sends a real email, so stub `Conferbot.liveChat`'s poster (or point the app at a server whose mailer is off), or check its validation answers only (a bad address is a 400).
- **Example app:**
  - To point it at a local server, add `baseUrl = "http://10.0.2.2:8001/api/v1/mobile/", socketUrl = "http://10.0.2.2:8001"` to `ExampleApplication.kt`.
  - Build with `sh ./gradlew :example:assembleDebug`. Package `com.conferbot.example`.
  - Force-stop before relaunching, or it resumes the XML chat.
  - The launcher activity is `.ui.xml.MainActivity`. On a 1080x2400 Pixel, "Open Chat (Compose)" is at (540,833); (538,968) is "Identify Test User", which signs the visitor in and so silences the greeting.
  - `uiautomator dump` hangs on the Compose screen (the launcher's motion never idles); wait with a sleep instead.
  - The emulator may come up with no network: `adb reverse tcp:8001 tcp:8001` and `http://127.0.0.1:8001` instead of 10.0.2.2. A temp bot copied from 6ab686f8d47ff029b76892f7 needs `version: "v2"` and no `creationType` (an `ai_agent` copy loads as an agent bot).
  - Step cards on a device: the rich steps call the bot server (`otp/send`, `pay/start`, `ai/*`, `kb/answer`, `geo/search`, `media`), so stub them in a temporary example-app hook (a `FlowApi` put in front of the engine's) to send no real e-mail, OTP, payment or AI request. `adb shell input text` drops characters into Compose fields when sent in one go: type a character at a time.
  - Android's regex engine refuses a literal `}` that the JVM tests accept (#70): escape it in every pattern.
  - Revert the example before committing.

## The goal

Every web widget feature works the same in the 4 mobile SDKs: customization, launcher, greeting and the live chat / agent features. The web widget (`conferbot-widget/src`) is the reference. Each feature is built and checked against the web in React Native first, then ported to Android, iOS and Flutter. The web source file it came from is named in a comment.

## Status across the SDKs (2026-10-16)

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
| Device language on every response record | done | done (#47) | todo | todo |
| "Helpful?": a No sent at once, reasons, comment, Talk to a person, Ask another way, vote changes | done | done in Compose (#46) | todo | todo |
| Requeued / transfer lines, the same end once, blocked visitor, closed chat with a new chat | done | done (#43, #44) | todo | todo |
| Markdown tables, h3-h6, quote, pre, strikethrough; reply timeout notice; muted fallback | done | done (#52) | todo | todo |
| Email me this chat | done | done (#42) | todo | todo |
| Launcher entrance / attention / idle call | done | done (#73) | todo | done |
| Home screen: on/off, ask box, welcome back, requests card, Talk to us, help card, trust, view transitions | done | done in Compose (#78); XML none (#80) | todo | todo |
| Greet by name (`{{first_name\|there}}` in the owner's texts) | done | done (#77) | todo | todo |
| Mascots (incl. `mascotInGreeting`) | todo | todo | todo | todo |
| `widgetLanguage` (i18n) | todo | todo | todo | todo |
| Message sounds | todo | todo | todo | todo |
| Celebrations / reactions | todo | todo | todo | todo |
| Reply-time wording (header; home card `homePeople`) | done | done (#28, #78) | todo | todo |
| Flow nodes: shared spec, 156 fixtures | 156/156 | 156/156 | todo | todo |
| Flow step cards (every waiting step answered as on the web) | done | done in Compose (#48); XML generic | done | done |
| Handover lifecycle (Connecting line, outcomes, waiting signals, leave queue, restore) | done | done (#24-#27, #37) | todo | todo |
| While you wait: help articles at 6s, `queue-help` + "That solved it", the waiting games at 2.5s | done | done in Compose (#40); XML none | todo | todo |
| "Your requests": list, one request, reply, live updates, unread | done | done in Compose (#41; home card #78); XML none (#76) | todo | todo |

Live chat / agent features (handover, queue card, receipts, rating, files, prefill, max-wait timeout, agent bot replies, suggestion chips) are done in all 4.

Each SDK repo has its own `docs/PARITY.md`. The React Native one also covers the generators in `scripts/parity/` and the greeting card spec in `docs/greeting-card-native-spec.md`.

### Web sources for the features still to do

All paths are under `conferbot-widget/src/`.

- **Launcher entrance / attention / idle call (all default off):**
  - `ts/launcher/entrance.ts`, setting `launcherEntrance`: classic default; lightning, rocket, portal, ufo, drop, confetti, coin.
  - `ts/launcher/attention.ts`, setting `launcherAttention`: off, pulse, wobble, bounce, wave, float, shine, heartbeat, orbit.
  - `ts/launcher/idleCall.ts`, setting `launcherIdleCall`: off, psst, peek, wave, knock, tap; delay 12.
  - The CSS is in `css/`.
- **Home screen (done in RN and Android):** `ts/home/` (firstImpression, homeCopy, homeUtils, askBox, trust, visitorMemory, requests/card; pageAware is not for apps), `ts/layout/viewTransition.ts`, `ts/layout/ownerLayout.ts` (greet by name), `css/polish.css`, `polish-home.css`.
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
