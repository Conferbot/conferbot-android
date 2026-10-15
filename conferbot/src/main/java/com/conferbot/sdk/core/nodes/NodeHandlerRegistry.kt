package com.conferbot.sdk.core.nodes

import com.conferbot.sdk.core.nodes.handlers.*

/**
 * Registry of all node handlers
 * Maps node types to their handlers
 */
object NodeHandlerRegistry {

    private val handlers = mutableMapOf<String, NodeHandler>()

    init {
        registerAllHandlers()
    }

    private fun registerAllHandlers() {
        // ==================== DISPLAY NODE HANDLERS ====================

        // Legacy nodes (v1)
        register(TwoChoicesNodeHandler())
        register(ThreeChoicesNodeHandler())
        register(SelectOptionNodeHandler())
        register(UserRatingNodeHandler())
        register(QuizNodeHandler())
        register(UserInputNodeHandler())
        register(UserRangeNodeHandler())

        // Starter node
        register(WelcomeNodeHandler())

        // Send response nodes (v2)
        register(MessageNodeHandler())
        register(ImageNodeHandler())
        register(VideoNodeHandler())
        register(AudioNodeHandler())
        register(FileNodeHandler())

        // Ask question nodes (v2)
        register(AskNameNodeHandler())
        register(AskEmailNodeHandler())
        register(AskPhoneNodeHandler())
        register(AskNumberNodeHandler())
        register(AskUrlNodeHandler())
        register(AskFileNodeHandler())
        register(AskLocationNodeHandler())
        register(AskCustomNodeHandler())
        register(AskMultipleQuestionsNodeHandler())
        register(CalendarNodeHandler())
        register(NSelectOptionNodeHandler())
        register(NCheckOptionsNodeHandler())

        // Flow operation nodes (v2)
        register(NChoicesNodeHandler())
        register(ImageChoiceNodeHandler())
        register(RatingChoiceNodeHandler())
        register(YesOrNoChoiceNodeHandler())
        register(OpinionScaleChoiceNodeHandler())
        register(CarouselNodeHandler())

        // Special display nodes (v2)
        register(UserRedirectNodeHandler())
        register(HtmlNodeHandler())
        register(NavigateNodeHandler())

        // ==================== LOGIC NODE HANDLERS ====================

        register(ConditionNodeHandler())
        register(BooleanLogicNodeHandler())
        register(RandomFlowNodeHandler())
        register(MathOperationNodeHandler())
        register(VariableNodeHandler())
        register(JumpToNodeHandler())
        register(BusinessHoursNodeHandler())

        // ==================== INTEGRATION NODE HANDLERS ====================

        register(EmailNodeHandler())
        register(WebhookNodeHandler())
        register(GptNodeHandler())
        register(ZapierNodeHandler())
        register(GoogleSheetsNodeHandler())
        register(GmailNodeHandler())
        register(GoogleCalendarNodeHandler())
        register(GoogleMeetNodeHandler())
        register(GoogleDriveNodeHandler())
        register(GoogleDocsNodeHandler())
        register(SlackNodeHandler())
        register(DiscordNodeHandler())
        register(AirtableNodeHandler())
        register(HubspotNodeHandler())
        register(NotionNodeHandler())
        register(ZohoCrmNodeHandler())
        register(StripeNodeHandler())
        register(MailchimpNodeHandler())
        register(SegmentNodeHandler())
        register(SalesforceNodeHandler())

        // ==================== SPECIAL FLOW NODE HANDLERS ====================

        register(DelayNodeHandler())
        register(HumanHandoverNodeHandler())

        // ==================== RICH STEPS (web richNodes.ts) ====================

        register(DynamicChoicesNodeHandler())
        register(HiddenFieldsNodeHandler())
        register(ConsentNodeHandler())
        register(ProductCardNodeHandler())
        register(SliderNodeHandler())
        register(DateTimeNodeHandler())
        register(RankingNodeHandler())
        register(OtpNodeHandler())
        register(AddressNodeHandler())
        register(MatrixNodeHandler())
        register(AiExtractNodeHandler())
        register(AiRouteNodeHandler())
        register(KbAnswerNodeHandler())
        register(SignatureNodeHandler())
        register(PaymentNodeHandler())
        // Loops and sub-flows (loop, subflow, run-subflow, subflow-return) are run by the engine itself.
    }

    /**
     * Register a handler
     */
    fun register(handler: NodeHandler) {
        handlers[handler.nodeType] = handler
    }

    /**
     * Get handler for node type
     */
    fun getHandler(nodeType: String): NodeHandler? {
        return handlers[nodeType]
    }

    /**
     * Check if handler exists for node type
     */
    fun hasHandler(nodeType: String): Boolean {
        return handlers.containsKey(nodeType)
    }

    /**
     * Get all registered node types
     */
    fun getRegisteredTypes(): Set<String> {
        return handlers.keys.toSet()
    }

    /**
     * Get count of registered handlers
     */
    fun getHandlerCount(): Int {
        return handlers.size
    }
}
