package com.conferbot.sdk.ui.views

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.conferbot.sdk.R
import com.conferbot.sdk.models.RecordItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for chat messages
 */
class MessageAdapter : ListAdapter<RecordItem, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_BOT = 2
        private const val VIEW_TYPE_AGENT = 3
        private const val VIEW_TYPE_SYSTEM = 4
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is RecordItem.UserMessage -> VIEW_TYPE_USER
            is RecordItem.BotMessage -> VIEW_TYPE_BOT
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
            if (message is RecordItem.UserMessage) {
                messageText.text = message.text
                timeText.text = formatTime(message.time)
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
                // Load bot avatar if needed
            }
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
                // Load agent avatar if needed
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
