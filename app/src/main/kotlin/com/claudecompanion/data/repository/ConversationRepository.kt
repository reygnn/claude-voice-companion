package com.claudecompanion.data.repository

import com.claudecompanion.data.local.*
import com.claudecompanion.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    fun getAllConversations(): Flow<List<Conversation>> =
        conversationDao.getAllConversations().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getConversation(id: Long): Conversation? =
        conversationDao.getById(id)?.toDomain()

    suspend fun getMessages(conversationId: Long): List<Message> =
        messageDao.getMessagesForConversationSync(conversationId).map { it.toDomain() }

    fun observeMessages(conversationId: Long): Flow<List<Message>> =
        messageDao.getMessagesForConversation(conversationId).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun createConversation(systemPrompt: String = DEFAULT_SYSTEM_PROMPT): Long {
        val now = Instant.now()
        return conversationDao.insert(
            ConversationEntity(
                title = "New conversation",
                createdAt = now,
                lastMessageAt = now,
                systemPrompt = systemPrompt
            )
        )
    }

    suspend fun addMessage(conversationId: Long, role: Role, content: String): Long {
        val now = Instant.now()
        val msgId = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                role = role.value,
                content = content,
                timestamp = now
            )
        )
        // Update conversation timestamp & title if first user message
        conversationDao.getById(conversationId)?.let { conv ->
            val title = if (conv.title == "New conversation" && role == Role.USER) {
                content.take(50).let { if (content.length > 50) "$it…" else it }
            } else conv.title
            conversationDao.update(conv.copy(lastMessageAt = now, title = title))
        }
        return msgId
    }

    suspend fun deleteConversation(id: Long) {
        conversationDao.getById(id)?.let { conversationDao.delete(it) }
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = id, title = title, createdAt = createdAt,
        lastMessageAt = lastMessageAt, systemPrompt = systemPrompt
    )

    private fun MessageEntity.toDomain() = Message(
        id = id, role = Role.entries.first { it.value == role },
        content = content, timestamp = timestamp
    )
}
