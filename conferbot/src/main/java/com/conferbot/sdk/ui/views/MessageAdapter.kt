package com.conferbot.sdk.ui.views

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.Html
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.conferbot.sdk.R
import com.conferbot.sdk.core.nodes.NodeTypes
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.models.RecordItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Callback interface for user interactions with node UI elements
 */
interface NodeInteractionListener {
    /**
     * Called when user responds to an interactive node
     * @param nodeId The ID of the node
     * @param nodeType The type of the node
     * @param response The user's response (varies by node type)
     * @param answerKey The key to store the answer under
     */
    fun onNodeResponse(nodeId: String, nodeType: String, response: Any, answerKey: String)

    /**
     * Called when user selects a choice that has a specific target port
     * @param nodeId The ID of the node
     * @param choiceId The ID of the selected choice
     * @param targetPort The target port/path to follow
     */
    fun onChoiceWithTarget(nodeId: String, choiceId: String, targetPort: String?)

    /**
     * Called when a file needs to be uploaded
     * @param nodeId The ID of the node
     * @param answerKey The key to store the answer under
     */
    fun onFileUploadRequested(nodeId: String, answerKey: String)
}

/**
 * Adapter for chat messages with full nodeData rendering support
 */
class MessageAdapter(
    private var interactionListener: NodeInteractionListener? = null,
    private var primaryColor: Int = Color.parseColor("#6366F1")
) : ListAdapter<RecordItem, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
        private const val VIEW_TYPE_BOT_NODE = 3
        private const val VIEW_TYPE_AGENT = 4
        private const val VIEW_TYPE_SYSTEM = 5
    }

    // Track which nodes have been responded to
    private val respondedNodes = mutableSetOf<String>()

    fun setInteractionListener(listener: NodeInteractionListener) {
        this.interactionListener = listener
    }

    fun setPrimaryColor(color: Int) {
        this.primaryColor = color
    }

    /**
     * Mark a node as responded to (disables further interaction)
     */
    fun markNodeResponded(nodeId: String) {
        respondedNodes.add(nodeId)
        // Find and update the item
        currentList.forEachIndexed { index, item ->
            if (item.id == nodeId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return when (item) {
            is RecordItem.UserMessage -> VIEW_TYPE_USER
            is RecordItem.UserInputResponse -> VIEW_TYPE_USER
            is RecordItem.BotMessage -> {
                // Check if this has nodeData that needs special rendering
                if (item.nodeData != null && item.nodeData.isNotEmpty()) {
                    VIEW_TYPE_BOT_NODE
                } else {
                    VIEW_TYPE_BOT
                }
            }
            is RecordItem.AgentMessage -> VIEW_TYPE_AGENT
            else -> VIEW_TYPE_SYSTEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_message_user, parent, false)
                UserMessageViewHolder(view)
            }
            VIEW_TYPE_BOT -> {
                val view = inflater.inflate(R.layout.item_message_bot, parent, false)
                BotMessageViewHolder(view)
            }
            VIEW_TYPE_BOT_NODE -> {
                // Create a dynamic container for node content
                val container = FrameLayout(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                BotNodeViewHolder(container, primaryColor, interactionListener, respondedNodes)
            }
            VIEW_TYPE_AGENT -> {
                val view = inflater.inflate(R.layout.item_message_agent, parent, false)
                AgentMessageViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_message_bot, parent, false)
                SystemMessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    abstract class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(message: RecordItem)

        protected fun formatTime(date: Date): String {
            val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return format.format(date)
        }
    }

    class UserMessageViewHolder(itemView: View) : MessageViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)

        override fun bind(message: RecordItem) {
            when (message) {
                is RecordItem.UserMessage -> {
                    messageText.text = message.text
                    timeText.text = formatTime(message.time)
                }
                is RecordItem.UserInputResponse -> {
                    messageText.text = message.text
                    timeText.text = formatTime(message.time)
                }
                else -> {}
            }
        }
    }

    class BotMessageViewHolder(itemView: View) : MessageViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val avatarImage: ImageView? = itemView.findViewById(R.id.avatarImage)

        override fun bind(message: RecordItem) {
            if (message is RecordItem.BotMessage) {
                messageText.text = message.text ?: ""
                timeText.text = formatTime(message.time)
            }
        }
    }

    /**
     * ViewHolder for bot messages with nodeData - renders interactive node UI
     */
    class BotNodeViewHolder(
        private val container: FrameLayout,
        private val primaryColor: Int,
        private val listener: NodeInteractionListener?,
        private val respondedNodes: Set<String>
    ) : MessageViewHolder(container) {

        private val context: Context get() = container.context

        override fun bind(message: RecordItem) {
            container.removeAllViews()

            if (message !is RecordItem.BotMessage || message.nodeData == null) {
                return
            }

            val nodeData = message.nodeData
            val nodeType = nodeData["type"]?.toString() ?: nodeData["nodeType"]?.toString() ?: ""
            val nodeId = message.id
            val isResponded = respondedNodes.contains(nodeId)

            // Parse and render based on node type
            val nodeView = when (nodeType) {
                // Display nodes
                NodeTypes.MESSAGE -> renderMessageNode(nodeData)
                NodeTypes.IMAGE -> renderImageNode(nodeData)
                NodeTypes.VIDEO -> renderVideoNode(nodeData)
                NodeTypes.AUDIO -> renderAudioNode(nodeData)
                NodeTypes.FILE -> renderFileNode(nodeData)

                // Choice nodes
                NodeTypes.TWO_CHOICES,
                NodeTypes.THREE_CHOICES,
                NodeTypes.N_CHOICES -> renderChoicesNode(nodeData, nodeId, isResponded)
                NodeTypes.YES_OR_NO_CHOICE -> renderYesNoChoiceNode(nodeData, nodeId, isResponded)
                NodeTypes.SELECT_OPTION,
                NodeTypes.N_SELECT_OPTION -> renderDropdownNode(nodeData, nodeId, isResponded)
                NodeTypes.N_CHECK_OPTIONS -> renderMultipleChoiceNode(nodeData, nodeId, isResponded)
                NodeTypes.IMAGE_CHOICE -> renderImageChoiceNode(nodeData, nodeId, isResponded)

                // Rating nodes
                NodeTypes.USER_RATING,
                NodeTypes.RATING_CHOICE -> renderRatingNode(nodeData, nodeId, isResponded)
                NodeTypes.OPINION_SCALE_CHOICE -> renderOpinionScaleNode(nodeData, nodeId, isResponded)

                // Input nodes
                NodeTypes.USER_INPUT,
                NodeTypes.ASK_NAME,
                NodeTypes.ASK_EMAIL,
                NodeTypes.ASK_PHONE,
                NodeTypes.ASK_NUMBER,
                NodeTypes.ASK_URL,
                NodeTypes.ASK_CUSTOM -> renderTextInputNode(nodeData, nodeId, nodeType, isResponded)
                NodeTypes.ASK_FILE -> renderFileUploadNode(nodeData, nodeId, isResponded)
                NodeTypes.USER_RANGE -> renderRangeNode(nodeData, nodeId, isResponded)
                NodeTypes.CALENDAR -> renderCalendarNode(nodeData, nodeId, isResponded)

                // Quiz
                NodeTypes.QUIZ -> renderQuizNode(nodeData, nodeId, isResponded)

                // Special nodes
                NodeTypes.HTML -> renderHtmlNode(nodeData)
                NodeTypes.USER_REDIRECT -> renderRedirectNode(nodeData)
                NodeTypes.HUMAN_HANDOVER -> renderHumanHandoverNode(nodeData, nodeId, isResponded)

                // Default: try to render as message
                else -> renderGenericNode(nodeData, message.text)
            }

            container.addView(nodeView)
        }

        // ==================== MESSAGE/DISPLAY NODES ====================

        private fun renderMessageNode(nodeData: Map<String, Any>): View {
            val text = extractText(nodeData)
            return createBotBubble(text)
        }

        private fun renderImageNode(nodeData: Map<String, Any>): View {
            val url = nodeData["imageUrl"]?.toString()
                ?: nodeData["url"]?.toString()
                ?: nodeData["image"]?.toString()
                ?: ""
            val caption = nodeData["caption"]?.toString()

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                // Image
                val imageView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(200)
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    adjustViewBounds = true
                }

                if (url.isNotEmpty()) {
                    Glide.with(context)
                        .load(url)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(imageView)
                }

                addView(imageView)

                // Caption
                if (!caption.isNullOrEmpty()) {
                    addView(TextView(context).apply {
                        text = caption
                        setTextColor(Color.GRAY)
                        textSize = 12f
                        setPadding(dpToPx(8), dpToPx(4), dpToPx(8), 0)
                    })
                }
            }
        }

        private fun renderVideoNode(nodeData: Map<String, Any>): View {
            val url = nodeData["videoUrl"]?.toString()
                ?: nodeData["url"]?.toString()
                ?: ""
            val caption = nodeData["caption"]?.toString()

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                // Video placeholder with play icon
                val videoContainer = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(180)
                    )
                    setBackgroundColor(Color.parseColor("#1A1A1A"))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (url.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    }
                }

                val playIcon = ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        dpToPx(48),
                        dpToPx(48),
                        Gravity.CENTER
                    )
                    setImageResource(android.R.drawable.ic_media_play)
                    setColorFilter(Color.WHITE)
                }

                videoContainer.addView(playIcon)
                addView(videoContainer)

                if (!caption.isNullOrEmpty()) {
                    addView(TextView(context).apply {
                        text = caption
                        setTextColor(Color.GRAY)
                        textSize = 12f
                        setPadding(dpToPx(8), dpToPx(4), dpToPx(8), 0)
                    })
                }
            }
        }

        private fun renderAudioNode(nodeData: Map<String, Any>): View {
            val url = nodeData["audioUrl"]?.toString()
                ?: nodeData["url"]?.toString()
                ?: ""

            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                setBackgroundResource(R.drawable.bg_bot_bubble)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (url.isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                }

                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
                    setImageResource(android.R.drawable.ic_media_play)
                    setColorFilter(primaryColor)
                })

                addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(4), 1f).apply {
                        marginStart = dpToPx(12)
                        marginEnd = dpToPx(12)
                    }
                    max = 100
                    progress = 0
                })

                addView(TextView(context).apply {
                    text = "0:00"
                    setTextColor(Color.GRAY)
                    textSize = 12f
                })
            }
        }

        private fun renderFileNode(nodeData: Map<String, Any>): View {
            val url = nodeData["fileUrl"]?.toString()
                ?: nodeData["url"]?.toString()
                ?: ""
            val fileName = nodeData["fileName"]?.toString()
                ?: nodeData["name"]?.toString()
                ?: "File"

            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                setBackgroundResource(R.drawable.bg_bot_bubble)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (url.isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                }

                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
                    setImageResource(android.R.drawable.ic_menu_save)
                    setColorFilter(primaryColor)
                })

                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dpToPx(12)
                    }
                    text = fileName
                    setTextColor(Color.BLACK)
                    textSize = 14f
                })

                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
                    setImageResource(android.R.drawable.stat_sys_download)
                    setColorFilter(Color.GRAY)
                })
            }
        }

        // ==================== CHOICE NODES ====================

        private fun renderChoicesNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            isResponded: Boolean
        ): View {
            val questionText = extractText(nodeData)
            val choices = extractChoices(nodeData)
            val answerKey = nodeData["answerKey"]?.toString() ?: "choice"

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                // Question text
                if (questionText.isNotEmpty()) {
                    addView(createBotBubble(questionText))
                    addView(Space(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(8)
                        )
                    })
                }

                // Choice buttons
                choices.forEachIndexed { index, choice ->
                    addView(createChoiceButton(
                        text = choice.text,
                        isEnabled = !isResponded,
                        onClick = {
                            listener?.onNodeResponse(
                                nodeId,
                                NodeTypes.N_CHOICES,
                                mapOf("id" to choice.id, "text" to choice.text, "index" to index),
                                answerKey
                            )
                            choice.targetPort?.let { port ->
                                listener?.onChoiceWithTarget(nodeId, choice.id, port)
                            }
                        }
                    ))
                }
            }
        }

        private fun renderYesNoChoiceNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            isResponded: Boolean
        ): View {
            val questionText = extractText(nodeData)
            val yesText = nodeData["yesText"]?.toString() ?: "Yes"
            val noText = nodeData["noText"]?.toString() ?: "No"
            val answerKey = nodeData["answerKey"]?.toString() ?: "yesNo"

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                if (questionText.isNotEmpty()) {
                    addView(createBotBubble(questionText))
                    addView(Space(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(8)
                        )
                    })
                }

                // Horizontal button container
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )

                    addView(createChoiceButton(
                        text = yesText,
                        isEnabled = !isResponded,
                        onClick = {
                            listener?.onNodeResponse(nodeId, NodeTypes.YES_OR_NO_CHOICE, true, answerKey)
                            listener?.onChoiceWithTarget(nodeId, "yes", "source-0")
                        }
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dpToPx(4)
                        }
                    })

                    addView(createChoiceButton(
                        text = noText,
                        isEnabled = !isResponded,
                        onClick = {
                            listener?.onNodeResponse(nodeId, NodeTypes.YES_OR_NO_CHOICE, false, answerKey)
                            listener?.onChoiceWithTarget(nodeId, "no", "source-1")
                        }
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = dpToPx(4)
                        }
                    })
                })
            }
        }

        private fun renderDropdownNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            isResponded: Boolean
        ): View {
            val questionText = extractText(nodeData)
            val options = extractOptions(nodeData)
            val answerKey = nodeData["answerKey"]?.toString() ?: "selection"

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                if (questionText.isNotEmpty()) {
                    addView(createBotBubble(questionText))
                    addView(Space(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(8)
                        )
                    })
                }

                // Spinner/Dropdown
                val spinner = Spinner(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    isEnabled = !isResponded

                    val items = listOf("Select an option") + options.map { it.text }
                    adapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_spinner_dropdown_item,
                        items
                    )

                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            if (position > 0 && !isResponded) {
                                val selectedOption = options[position - 1]
                                listener?.onNodeResponse(
                                    nodeId,
                                    NodeTypes.N_SELECT_OPTION,
                                    mapOf("id" to selectedOption.id, "text" to selectedOption.text),
                                    answerKey
                                )
                            }
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                }

                addView(spinner)
            }
        }

        private fun renderMultipleChoiceNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            isResponded: Boolean
        ): View {
            val questionText = extractText(nodeData)
            val options = extractOptions(nodeData)
            val answerKey = nodeData["answerKey"]?.toString() ?: "selections"
            val selectedIds = mutableSetOf<String>()

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                if (questionText.isNotEmpty()) {
                    addView(createBotBubble(questionText))
                    addView(Space(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(8)
                        )
                    })
                }

                // Checkboxes
                options.forEach { option ->
                    addView(CheckBox(context).apply {
                        text = option.text
                        isEnabled = !isResponded
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                selectedIds.add(option.id)
                            } else {
                                selectedIds.remove(option.id)
                            }
                        }
                    })
                }

                // Submit button
                addView(Button(context).apply {
                    text = "Submit"
                    isEnabled = !isResponded
                    setBackgroundColor(primaryColor)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(8)
                    }
                    setOnClickListener {
                        if (selectedIds.isNotEmpty()) {
                            val selectedOptions = options.filter { it.id in selectedIds }
                            listener?.onNodeResponse(
                                nodeId,
                                NodeTypes.N_CHECK_OPTIONS,
                                selectedOptions.map { mapOf("id" to it.id, "text" to it.text) },
                                answerKey
                            )
                        }
                    }
                })
            }
        }

        private fun renderImageChoiceNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            isResponded: Boolean
        ): View {
            val questionText = extractText(nodeData)
            val images = extractImageOptions(nodeData)
            val answerKey = nodeData["answerKey"]?.toString() ?: "imageChoice"

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                if (questionText.isNotEmpty()) {
                    addView(createBotBubble(questionText))
                    addView(Space(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(8)
                        )
                    })
                }

                // Image grid (2 columns)
                var currentRow: LinearLayout? = null
                images.forEachIndexed { index, image ->
                    if (index % 2 == 0) {
                        currentRow = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = dpToPx(8)
                            }
                        }
                        addView(currentRow)
                    }

                    currentRow?.addView(createImageChoiceItem(
                        imageUrl = image.imageUrl,
                        label = image.label,
                        isEnabled = !isResponded,
                        onClick = {
                            listener?.onNodeResponse(
                                nodeId,
                                NodeTypes.IMAGE_CHOICE,
                                mapOf("id" to image.id, "label" to image.label, "imageUrl" to image.imageUrl),
                                answerKey
                            )
                            image.targetPort?.let { port ->
                                listener?.onChoiceWithTarget(nodeId, image.id, port)
                            }
                        }
                    ))
                }
            }
        }

        // ==================== RATING NODES ====================

        private fun renderRatingNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            isResponded: Boolean
        ): View {
            val questionText = extractText(nodeData)
            val maxRating = (nodeData["maxRating"] as? Number)?.toInt()
                ?: (nodeData["maxValue"] as? Number)?.toInt()
                ?: 5
            val ratingType = nodeData["ratingType"]?.toString() ?: "star"
            val answerKey = nodeData["answerKey"]?.toString() ?: "rating"

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                if (questionText.isNotEmpty()) {
                    addView(createBotBubble(questionText))
                    addView(Space(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(8)
                        )
                    })
                }

                when (ratingType.lowercase()) {
                    "smiley", "emoji" -> addView(
                        createSmileyRating(nodeId, answerKey, isResponded)
                    )
                    else -> addView(
                        createStarRating(maxRating, nodeId, answerKey, isResponded)
                    )
                }
            }
        }

        private fun renderOpinionScaleNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            isResponded: Boolean
        ): View {
            val questionText = extractText(nodeData)
            val minValue = (nodeData["minValue"] as? Number)?.toInt() ?: 0
            val maxValue = (nodeData["maxValue"] as? Number)?.toInt() ?: 10
            val answerKey = nodeData["answerKey"]?.toString() ?: "opinionScale"

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                if (questionText.isNotEmpty()) {
                    addView(createBotBubble(questionText))
                    addView(Space(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(8)
                        )
                    })
                }

                // Number buttons row
                addView(HorizontalScrollView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )

                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER

                        for (value in minValue..maxValue) {
                            addView(Button(context).apply {
                                text = value.toString()
                                layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)).apply {
                                    marginEnd = dpToPx(4)
                                }
                                isEnabled = !isResponded
                                setOnClickListener {
                                    listener?.onNodeResponse(
                                        nodeId,
                                        NodeTypes.OPINION_SCALE_CHOICE,
                                        value,
                                        answerKey
                                    )
                                }
                            })
                        }
                    })
                })
            }
        }

        // ==================== INPUT NODES ====================

        private fun renderTextInputNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            nodeType: String,
            isResponded: Boolean
        ): View {
            val questionText = extractText(nodeData)
            val placeholder = nodeData["placeholder"]?.toString() ?: getPlaceholderForType(nodeType)
            val answerKey = nodeData["answerKey"]?.toString() ?: getAnswerKeyForType(nodeType)

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                if (questionText.isNotEmpty()) {
                    addView(createBotBubble(questionText))
                    addView(Space(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(8)
                        )
                    })
                }

                val editText = EditText(context).apply {
                    hint = placeholder
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    isEnabled = !isResponded
                    inputType = getInputTypeForNode(nodeType)
                    setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                }

                addView(editText)

                addView(Button(context).apply {
                    text = "Submit"
                    isEnabled = !isResponded
                    setBackgroundColor(primaryColor)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(8)
                    }
                    setOnClickListener {
                        val text = editText.text.toString().trim()
                        if (text.isNotEmpty()) {
                            listener?.onNodeResponse(nodeId, nodeType, text, answerKey)
                        }
                    }
                })
            }
        }

        private fun renderFileUploadNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            isResponded: Boolean
        ): View {
            val questionText = extractText(nodeData)
            val maxSizeMb = (nodeData["maxSizeMb"] as? Number)?.toInt() ?: 5
            val answerKey = nodeData["answerKey"]?.toString() ?: "file"

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                if (questionText.isNotEmpty()) {
                    addView(createBotBubble(questionText))
                    addView(Space(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(8)
                        )
                    })
                }

                // Upload area
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
                    setBackgroundResource(R.drawable.bg_bot_bubble)
                    isClickable = !isResponded
                    isFocusable = !isResponded
                    setOnClickListener {
                        if (!isResponded) {
                            listener?.onFileUploadRequested(nodeId, answerKey)
                        }
                    }

                    addView(ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
                        setImageResource(android.R.drawable.ic_menu_upload)
                        setColorFilter(primaryColor)
                    })

                    addView(TextView(context).apply {
                        text = "Tap to upload file"
                        setTextColor(Color.BLACK)
                        textSize = 14f
                        gravity = Gravity.CENTER
                        setPadding(0, dpToPx(8), 0, 0)
                    })

                    addView(TextView(context).apply {
                        text = "Max ${maxSizeMb}MB"
                        setTextColor(Color.GRAY)
                        textSize = 12f
                        gravity = Gravity.CENTER
                    })
                })
            }
        }

        private fun renderRangeNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            isResponded: Boolean
        ): View {
            val questionText = extractText(nodeData)
            val minValue = (nodeData["minValue"] as? Number)?.toInt() ?: 0
            val maxValue = (nodeData["maxValue"] as? Number)?.toInt() ?: 100
            val defaultValue = (nodeData["defaultValue"] as? Number)?.toInt() ?: (minValue + maxValue) / 2
            val answerKey = nodeData["answerKey"]?.toString() ?: "range"

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                if (questionText.isNotEmpty()) {
                    addView(createBotBubble(questionText))
                    addView(Space(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(8)
                        )
                    })
                }

                val valueText = TextView(context).apply {
                    text = "Selected: $defaultValue"
                    gravity = Gravity.CENTER
                    setTextColor(Color.BLACK)
                    textSize = 16f
                }

                // Min/Max labels with SeekBar
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL

                    addView(TextView(context).apply {
                        text = minValue.toString()
                        setTextColor(Color.GRAY)
                    })

                    addView(SeekBar(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        max = maxValue - minValue
                        progress = defaultValue - minValue
                        isEnabled = !isResponded
                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                valueText.text = "Selected: ${progress + minValue}"
                            }
                            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                        })
                    })

                    addView(TextView(context).apply {
                        text = maxValue.toString()
                        setTextColor(Color.GRAY)
                    })
                })

                addView(valueText)

                addView(Button(context).apply {
                    text = "Submit"
                    isEnabled = !isResponded
                    setBackgroundColor(primaryColor)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(8)
                    }
                    setOnClickListener {
                        val currentValue = valueText.text.toString().replace("Selected: ", "").toIntOrNull() ?: defaultValue
                        listener?.onNodeResponse(nodeId, NodeTypes.USER_RANGE, currentValue, answerKey)
                    }
                })
            }
        }

        private fun renderCalendarNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            isResponded: Boolean
        ): View {
            val questionText = extractText(nodeData)
            val showTime = (nodeData["showTimeSelection"] as? Boolean) ?: false
            val answerKey = nodeData["answerKey"]?.toString() ?: "calendar"

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                if (questionText.isNotEmpty()) {
                    addView(createBotBubble(questionText))
                    addView(Space(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(8)
                        )
                    })
                }

                val dateEditText = EditText(context).apply {
                    hint = "YYYY-MM-DD"
                    isEnabled = !isResponded
                    isFocusable = false
                    isClickable = true
                    setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                    setOnClickListener {
                        // In production, this would show a DatePickerDialog
                        // For now, just allow text input
                        isFocusableInTouchMode = true
                        requestFocus()
                    }
                }

                val timeEditText = if (showTime) {
                    EditText(context).apply {
                        hint = "HH:MM"
                        isEnabled = !isResponded
                        isFocusable = false
                        isClickable = true
                        setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = dpToPx(8)
                        }
                    }
                } else null

                addView(dateEditText)
                timeEditText?.let { addView(it) }

                addView(Button(context).apply {
                    text = "Confirm"
                    isEnabled = !isResponded
                    setBackgroundColor(primaryColor)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(8)
                    }
                    setOnClickListener {
                        val date = dateEditText.text.toString().trim()
                        val time = timeEditText?.text?.toString()?.trim() ?: ""
                        if (date.isNotEmpty()) {
                            listener?.onNodeResponse(
                                nodeId,
                                NodeTypes.CALENDAR,
                                mapOf("date" to date, "time" to time),
                                answerKey
                            )
                        }
                    }
                })
            }
        }

        // ==================== QUIZ NODE ====================

        private fun renderQuizNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            isResponded: Boolean
        ): View {
            val questionText = extractText(nodeData)
            @Suppress("UNCHECKED_CAST")
            val options = (nodeData["options"] as? List<String>)
                ?: (nodeData["choices"] as? List<Map<String, Any>>)?.map { it["text"]?.toString() ?: "" }
                ?: emptyList()
            val correctIndex = (nodeData["correctAnswerIndex"] as? Number)?.toInt()
                ?: (nodeData["correctIndex"] as? Number)?.toInt()
                ?: -1
            val answerKey = nodeData["answerKey"]?.toString() ?: "quiz"

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }

                if (questionText.isNotEmpty()) {
                    addView(createBotBubble(questionText))
                    addView(Space(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(8)
                        )
                    })
                }

                options.forEachIndexed { index, option ->
                    addView(createChoiceButton(
                        text = option,
                        isEnabled = !isResponded,
                        onClick = {
                            listener?.onNodeResponse(
                                nodeId,
                                NodeTypes.QUIZ,
                                mapOf(
                                    "index" to index,
                                    "text" to option,
                                    "isCorrect" to (index == correctIndex)
                                ),
                                answerKey
                            )
                        }
                    ))
                }
            }
        }

        // ==================== SPECIAL NODES ====================

        private fun renderHtmlNode(nodeData: Map<String, Any>): View {
            val htmlContent = nodeData["htmlContent"]?.toString()
                ?: nodeData["html"]?.toString()
                ?: ""

            return TextView(context).apply {
                text = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_COMPACT)
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                setBackgroundResource(R.drawable.bg_bot_bubble)
            }
        }

        private fun renderRedirectNode(nodeData: Map<String, Any>): View {
            val url = nodeData["url"]?.toString() ?: ""

            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
                setBackgroundResource(R.drawable.bg_bot_bubble)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (url.isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                }

                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
                    setImageResource(android.R.drawable.ic_menu_directions)
                    setColorFilter(primaryColor)
                })

                addView(TextView(context).apply {
                    text = "Opening link..."
                    setTextColor(Color.BLACK)
                    textSize = 14f
                    setPadding(dpToPx(8), 0, 0, 0)
                })
            }
        }

        private fun renderHumanHandoverNode(
            nodeData: Map<String, Any>,
            nodeId: String,
            isResponded: Boolean
        ): View {
            val state = nodeData["state"]?.toString() ?: "waiting"
            val message = nodeData["handoverMessage"]?.toString()
                ?: nodeData["message"]?.toString()
                ?: "Connecting you to an agent..."
            val agentName = nodeData["agentName"]?.toString()

            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                setBackgroundResource(R.drawable.bg_bot_bubble)
                gravity = Gravity.CENTER

                when (state.lowercase()) {
                    "waiting", "waiting_for_agent" -> {
                        addView(ProgressBar(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
                        })
                        addView(TextView(context).apply {
                            text = message
                            setTextColor(Color.BLACK)
                            textSize = 14f
                            gravity = Gravity.CENTER
                            setPadding(0, dpToPx(8), 0, 0)
                        })
                    }
                    "connected", "agent_connected" -> {
                        addView(ImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
                            setImageResource(android.R.drawable.ic_dialog_info)
                            setColorFilter(Color.parseColor("#4CAF50"))
                        })
                        addView(TextView(context).apply {
                            text = "${agentName ?: "Agent"} has joined the chat"
                            setTextColor(Color.BLACK)
                            textSize = 14f
                            gravity = Gravity.CENTER
                            setPadding(0, dpToPx(8), 0, 0)
                        })
                    }
                    "no_agents", "no_agents_available" -> {
                        addView(ImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
                            setImageResource(android.R.drawable.ic_dialog_alert)
                            setColorFilter(Color.parseColor("#FF9800"))
                        })
                        addView(TextView(context).apply {
                            text = message
                            setTextColor(Color.BLACK)
                            textSize = 14f
                            gravity = Gravity.CENTER
                            setPadding(0, dpToPx(8), 0, 0)
                        })
                    }
                }
            }
        }

        private fun renderGenericNode(nodeData: Map<String, Any>, fallbackText: String?): View {
            val text = extractText(nodeData).ifEmpty { fallbackText ?: "" }
            return createBotBubble(text)
        }

        // ==================== HELPER METHODS ====================

        private fun createBotBubble(text: String): View {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, dpToPx(48), dpToPx(8))
                }
                setBackgroundResource(R.drawable.bg_bot_bubble)
                setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))

                addView(TextView(context).apply {
                    this.text = text
                    setTextColor(Color.BLACK)
                    textSize = 14f
                })
            }
        }

        private fun createChoiceButton(
            text: String,
            isEnabled: Boolean,
            onClick: () -> Unit
        ): Button {
            return Button(context).apply {
                this.text = text
                this.isEnabled = isEnabled
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(4)
                }
                if (isEnabled) {
                    setBackgroundColor(Color.WHITE)
                    setTextColor(primaryColor)
                } else {
                    setBackgroundColor(Color.LTGRAY)
                    setTextColor(Color.GRAY)
                }
                setOnClickListener { if (isEnabled) onClick() }
            }
        }

        private fun createStarRating(
            maxRating: Int,
            nodeId: String,
            answerKey: String,
            isResponded: Boolean
        ): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                val stars = mutableListOf<ImageView>()
                for (i in 1..maxRating) {
                    val star = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                            marginEnd = dpToPx(4)
                        }
                        setImageResource(android.R.drawable.btn_star_big_off)
                        isClickable = !isResponded
                        isFocusable = !isResponded
                        setOnClickListener {
                            if (!isResponded) {
                                // Update star display
                                stars.forEachIndexed { index, starView ->
                                    starView.setImageResource(
                                        if (index < i) android.R.drawable.btn_star_big_on
                                        else android.R.drawable.btn_star_big_off
                                    )
                                }
                                listener?.onNodeResponse(nodeId, NodeTypes.USER_RATING, i, answerKey)
                            }
                        }
                    }
                    stars.add(star)
                    addView(star)
                }
            }
        }

        private fun createSmileyRating(
            nodeId: String,
            answerKey: String,
            isResponded: Boolean
        ): LinearLayout {
            val smileys = listOf(
                "\uD83D\uDE22" to 1, // Sad
                "\uD83D\uDE15" to 2, // Confused
                "\uD83D\uDE10" to 3, // Neutral
                "\uD83D\uDE42" to 4, // Slight smile
                "\uD83D\uDE04" to 5  // Happy
            )

            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                smileys.forEach { (emoji, rating) ->
                    addView(TextView(context).apply {
                        text = emoji
                        textSize = 32f
                        layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
                        gravity = Gravity.CENTER
                        isClickable = !isResponded
                        isFocusable = !isResponded
                        setOnClickListener {
                            if (!isResponded) {
                                listener?.onNodeResponse(nodeId, NodeTypes.RATING_CHOICE, rating, answerKey)
                            }
                        }
                    })
                }
            }
        }

        private fun createImageChoiceItem(
            imageUrl: String,
            label: String,
            isEnabled: Boolean,
            onClick: () -> Unit
        ): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dpToPx(4)
                }
                isClickable = isEnabled
                isFocusable = isEnabled
                setOnClickListener { if (isEnabled) onClick() }

                val imageView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(100)
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                if (imageUrl.isNotEmpty()) {
                    Glide.with(context)
                        .load(imageUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(imageView)
                }

                addView(imageView)

                addView(TextView(context).apply {
                    text = label
                    setTextColor(Color.BLACK)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                })
            }
        }

        // ==================== DATA EXTRACTION HELPERS ====================

        private fun extractText(nodeData: Map<String, Any>): String {
            return nodeData["text"]?.toString()
                ?: nodeData["questionText"]?.toString()
                ?: nodeData["message"]?.toString()
                ?: nodeData["question"]?.toString()
                ?: ""
        }

        data class ChoiceData(
            val id: String,
            val text: String,
            val targetPort: String?
        )

        @Suppress("UNCHECKED_CAST")
        private fun extractChoices(nodeData: Map<String, Any>): List<ChoiceData> {
            val choices = nodeData["choices"] as? List<Map<String, Any>>
                ?: nodeData["options"] as? List<Map<String, Any>>
                ?: nodeData["buttons"] as? List<Map<String, Any>>
                ?: return emptyList()

            return choices.mapIndexed { index, choice ->
                ChoiceData(
                    id = choice["id"]?.toString() ?: "choice-$index",
                    text = choice["text"]?.toString() ?: choice["label"]?.toString() ?: "",
                    targetPort = choice["targetPort"]?.toString() ?: choice["port"]?.toString()
                )
            }
        }

        data class OptionData(
            val id: String,
            val text: String
        )

        @Suppress("UNCHECKED_CAST")
        private fun extractOptions(nodeData: Map<String, Any>): List<OptionData> {
            val options = nodeData["options"] as? List<Map<String, Any>>
                ?: nodeData["choices"] as? List<Map<String, Any>>
                ?: return emptyList()

            return options.mapIndexed { index, option ->
                OptionData(
                    id = option["id"]?.toString() ?: "option-$index",
                    text = option["text"]?.toString() ?: option["label"]?.toString() ?: ""
                )
            }
        }

        data class ImageOptionData(
            val id: String,
            val imageUrl: String,
            val label: String,
            val targetPort: String?
        )

        @Suppress("UNCHECKED_CAST")
        private fun extractImageOptions(nodeData: Map<String, Any>): List<ImageOptionData> {
            val images = nodeData["images"] as? List<Map<String, Any>>
                ?: nodeData["imageOptions"] as? List<Map<String, Any>>
                ?: nodeData["options"] as? List<Map<String, Any>>
                ?: return emptyList()

            return images.mapIndexed { index, image ->
                ImageOptionData(
                    id = image["id"]?.toString() ?: "image-$index",
                    imageUrl = image["imageUrl"]?.toString() ?: image["url"]?.toString() ?: "",
                    label = image["label"]?.toString() ?: image["text"]?.toString() ?: "",
                    targetPort = image["targetPort"]?.toString()
                )
            }
        }

        private fun getPlaceholderForType(nodeType: String): String {
            return when (nodeType) {
                NodeTypes.ASK_NAME -> "Enter your name"
                NodeTypes.ASK_EMAIL -> "Enter your email"
                NodeTypes.ASK_PHONE -> "Enter your phone number"
                NodeTypes.ASK_NUMBER -> "Enter a number"
                NodeTypes.ASK_URL -> "Enter a URL"
                else -> "Type here..."
            }
        }

        private fun getAnswerKeyForType(nodeType: String): String {
            return when (nodeType) {
                NodeTypes.ASK_NAME -> "name"
                NodeTypes.ASK_EMAIL -> "email"
                NodeTypes.ASK_PHONE -> "phone"
                NodeTypes.ASK_NUMBER -> "number"
                NodeTypes.ASK_URL -> "url"
                else -> "input"
            }
        }

        private fun getInputTypeForNode(nodeType: String): Int {
            return when (nodeType) {
                NodeTypes.ASK_EMAIL -> android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                NodeTypes.ASK_PHONE -> android.text.InputType.TYPE_CLASS_PHONE
                NodeTypes.ASK_NUMBER -> android.text.InputType.TYPE_CLASS_NUMBER
                NodeTypes.ASK_URL -> android.text.InputType.TYPE_TEXT_VARIATION_URI
                else -> android.text.InputType.TYPE_CLASS_TEXT
            }
        }

        private fun dpToPx(dp: Int): Int {
            return (dp * context.resources.displayMetrics.density).toInt()
        }
    }

    class AgentMessageViewHolder(itemView: View) : MessageViewHolder(itemView) {
        private val agentName: TextView = itemView.findViewById(R.id.agentName)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val avatarImage: ImageView = itemView.findViewById(R.id.avatarImage)

        override fun bind(message: RecordItem) {
            if (message is RecordItem.AgentMessage) {
                agentName.text = message.agentDetails.name
                messageText.text = message.text
                timeText.text = formatTime(message.time)
            }
        }
    }

    class SystemMessageViewHolder(itemView: View) : MessageViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)

        override fun bind(message: RecordItem) {
            if (message is RecordItem.SystemMessage) {
                messageText.text = message.text
                timeText.text = formatTime(message.time)
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<RecordItem>() {
        override fun areItemsTheSame(oldItem: RecordItem, newItem: RecordItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RecordItem, newItem: RecordItem): Boolean {
            return oldItem == newItem
        }
    }
}
