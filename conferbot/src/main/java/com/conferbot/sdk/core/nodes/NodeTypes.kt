package com.conferbot.sdk.core.nodes

/**
 * All 58 node types supported by Conferbot
 * Organized by category matching the web widget
 */
object NodeTypes {

    // ==================== DISPLAY NODES (32 types) ====================

    // Legacy nodes (v1)
    const val TWO_CHOICES = "two-choices-node"
    const val THREE_CHOICES = "three-choices-node"
    const val SELECT_OPTION = "select-option-node"
    const val USER_RATING = "user-rating-node"
    const val QUIZ = "quiz-node"
    const val USER_INPUT = "user-input-node"
    const val USER_RANGE = "user-range-node"

    // Starter node
    const val WELCOME = "welcome-node"

    // Send response nodes (v2)
    const val MESSAGE = "message-node"
    const val IMAGE = "image-node"
    const val VIDEO = "video-node"
    const val AUDIO = "audio-node"
    const val FILE = "file-node"

    // Ask question nodes (v2)
    const val ASK_NAME = "ask-name-node"
    const val ASK_EMAIL = "ask-email-node"
    const val ASK_PHONE = "ask-phone-number-node"
    const val ASK_NUMBER = "ask-number-node"
    const val ASK_URL = "ask-url-node"
    const val ASK_FILE = "ask-file-node"
    const val ASK_LOCATION = "ask-location-node"
    const val ASK_CUSTOM = "ask-custom-question-node"
    const val ASK_MULTIPLE = "ask-multiple-questions-node"
    const val CALENDAR = "calendar-node"
    const val N_SELECT_OPTION = "n-select-option-node"
    const val N_CHECK_OPTIONS = "n-check-options-node"

    // Flow operation nodes (v2)
    const val N_CHOICES = "n-choices-node"
    const val IMAGE_CHOICE = "image-choice-node"
    const val RATING_CHOICE = "rating-choice-node"
    const val YES_OR_NO_CHOICE = "yes-or-no-choice-node"
    const val OPINION_SCALE_CHOICE = "opinion-scale-choice-node"

    // Special display nodes (v2)
    const val USER_REDIRECT = "user-redirect-node"
    const val HTML = "html-node"
    const val NAVIGATE = "navigate-node"

    // ==================== LOGIC NODES (7 types) ====================

    const val CONDITION = "condition-node"
    const val BOOLEAN_LOGIC = "boolean-logic-node"
    const val RANDOM_FLOW = "random-flow-node"
    const val MATH_OPERATION = "math-operation-node"
    const val VARIABLE = "variable-node"
    const val JUMP_TO = "jump-to-node"
    const val BUSINESS_HOURS = "business-hours-node"

    // ==================== INTEGRATION NODES (17 types) ====================

    const val EMAIL = "email-node"
    const val WEBHOOK = "webhook-node"
    const val GPT = "gpt-node"
    const val ZAPIER = "zapier-node"
    const val GOOGLE_SHEETS = "google-sheets-node"
    const val GMAIL = "gmail-node"
    const val GOOGLE_CALENDAR = "google-calendar-node"
    const val GOOGLE_MEET = "google-meet-node"
    const val GOOGLE_DRIVE = "google-drive-node"
    const val GOOGLE_DOCS = "google-docs-node"
    const val SLACK = "slack-node"
    const val DISCORD = "discord-node"
    const val AIRTABLE = "airtable-node"
    const val HUBSPOT = "hubspot-node"
    const val NOTION = "notion-node"
    const val ZOHO_CRM = "zohocrm-node"
    const val STRIPE = "stripe-node"

    // ==================== SPECIAL FLOW NODES (2 types) ====================

    const val DELAY = "delay-node"
    const val HUMAN_HANDOVER = "human-handover-node"

    // ==================== CATEGORY LISTS ====================

    val DISPLAY_NODES = listOf(
        TWO_CHOICES, THREE_CHOICES, SELECT_OPTION, USER_RATING, QUIZ, USER_INPUT, USER_RANGE,
        MESSAGE, IMAGE, VIDEO, AUDIO, FILE,
        ASK_NAME, ASK_EMAIL, ASK_PHONE, ASK_NUMBER, ASK_URL, ASK_FILE, ASK_LOCATION,
        ASK_CUSTOM, ASK_MULTIPLE, CALENDAR, N_SELECT_OPTION, N_CHECK_OPTIONS,
        N_CHOICES, IMAGE_CHOICE, RATING_CHOICE, YES_OR_NO_CHOICE, OPINION_SCALE_CHOICE,
        USER_REDIRECT, HTML, NAVIGATE
    )

    val LOGIC_NODES = listOf(
        CONDITION, BOOLEAN_LOGIC, RANDOM_FLOW, MATH_OPERATION, VARIABLE, JUMP_TO, BUSINESS_HOURS
    )

    val INTEGRATION_NODES = listOf(
        EMAIL, WEBHOOK, GPT, ZAPIER, GOOGLE_SHEETS, GMAIL, GOOGLE_CALENDAR,
        GOOGLE_MEET, GOOGLE_DRIVE, GOOGLE_DOCS, SLACK, DISCORD, AIRTABLE,
        HUBSPOT, NOTION, ZOHO_CRM, STRIPE
    )

    val SPECIAL_FLOW_NODES = listOf(DELAY, HUMAN_HANDOVER)

    val ALL_NODES = DISPLAY_NODES + LOGIC_NODES + INTEGRATION_NODES + SPECIAL_FLOW_NODES

    // ==================== CATEGORY HELPERS ====================

    fun isDisplayNode(type: String): Boolean = type in DISPLAY_NODES
    fun isLogicNode(type: String): Boolean = type in LOGIC_NODES
    fun isIntegrationNode(type: String): Boolean = type in INTEGRATION_NODES
    fun isSpecialFlowNode(type: String): Boolean = type in SPECIAL_FLOW_NODES

    /**
     * Check if node requires user input/interaction
     */
    fun requiresUserInteraction(type: String): Boolean {
        return when (type) {
            // Ask question nodes
            ASK_NAME, ASK_EMAIL, ASK_PHONE, ASK_NUMBER, ASK_URL, ASK_FILE,
            ASK_LOCATION, ASK_CUSTOM, ASK_MULTIPLE -> true
            // Choice nodes
            TWO_CHOICES, THREE_CHOICES, N_CHOICES, SELECT_OPTION, N_SELECT_OPTION,
            N_CHECK_OPTIONS, IMAGE_CHOICE, RATING_CHOICE, YES_OR_NO_CHOICE,
            OPINION_SCALE_CHOICE -> true
            // Rating/Quiz
            USER_RATING, QUIZ, USER_INPUT, USER_RANGE -> true
            // Calendar
            CALENDAR -> true
            // Human handover pre-chat questions
            HUMAN_HANDOVER -> true
            // Everything else auto-proceeds
            else -> false
        }
    }

    /**
     * Check if node type captures user metadata
     */
    fun capturesMetadata(type: String): Boolean {
        return type in listOf(ASK_NAME, ASK_EMAIL, ASK_PHONE)
    }

    /**
     * Check if node is a message display (no interaction)
     */
    fun isMessageOnly(type: String): Boolean {
        return type in listOf(MESSAGE, IMAGE, VIDEO, AUDIO, FILE, HTML)
    }
}
