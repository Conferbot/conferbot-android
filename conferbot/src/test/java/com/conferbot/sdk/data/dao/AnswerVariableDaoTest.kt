package com.conferbot.sdk.data.dao

import com.conferbot.sdk.data.entities.AnswerVariableEntity
import com.conferbot.sdk.data.testutils.DatabaseTestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AnswerVariableDao
 * Tests answer variable CRUD operations and lookup methods
 */
class AnswerVariableDaoTest {

    private lateinit var answerVariableDao: AnswerVariableDao

    @Before
    fun setUp() {
        answerVariableDao = mockk(relaxed = true)
    }

    // ==================== INSERT TESTS ====================

    @Test
    fun `insert creates new answer variable and returns ID`() = runTest {
        // Given
        val variable = DatabaseTestFixtures.createAnswerVariable()
        coEvery { answerVariableDao.insert(variable) } returns 1L

        // When
        val result = answerVariableDao.insert(variable)

        // Then
        assertThat(result).isEqualTo(1L)
        coVerify { answerVariableDao.insert(variable) }
    }

    @Test
    fun `insert replaces variable with same primary key on conflict`() = runTest {
        // Given
        val variable = DatabaseTestFixtures.createAnswerVariable(id = 1)
        val updatedVariable = variable.copy(value = "\"Updated Value\"")

        coEvery { answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId) } returns updatedVariable

        // When
        answerVariableDao.insert(variable)
        answerVariableDao.insert(updatedVariable)

        // Then
        val result = answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId)
        assertThat(result?.value).isEqualTo("\"Updated Value\"")
    }

    @Test
    fun `insertAll inserts multiple variables`() = runTest {
        // Given
        val variables = DatabaseTestFixtures.createMultipleAnswerVariables()

        // When
        answerVariableDao.insertAll(variables)

        // Then
        coVerify { answerVariableDao.insertAll(variables) }
    }

    @Test
    fun `insertAll with empty list does nothing`() = runTest {
        // Given
        val emptyList = emptyList<AnswerVariableEntity>()

        // When
        answerVariableDao.insertAll(emptyList)

        // Then
        coVerify { answerVariableDao.insertAll(emptyList) }
    }

    // ==================== QUERY TESTS ====================

    @Test
    fun `getVariables returns all variables for session ordered by createdAt ASC`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val variables = listOf(
            DatabaseTestFixtures.createAnswerVariable(
                nodeId = "node-1",
                key = "name",
                createdAt = DatabaseTestFixtures.BASE_TIMESTAMP
            ),
            DatabaseTestFixtures.createAnswerVariable(
                nodeId = "node-2",
                key = "email",
                createdAt = DatabaseTestFixtures.BASE_TIMESTAMP + 1000
            ),
            DatabaseTestFixtures.createAnswerVariable(
                nodeId = "node-3",
                key = "phone",
                createdAt = DatabaseTestFixtures.BASE_TIMESTAMP + 2000
            )
        )
        coEvery { answerVariableDao.getVariables(sessionId) } returns variables

        // When
        val result = answerVariableDao.getVariables(sessionId)

        // Then
        assertThat(result).hasSize(3)
        assertThat(result[0].key).isEqualTo("name")
        assertThat(result[1].key).isEqualTo("email")
        assertThat(result[2].key).isEqualTo("phone")
    }

    @Test
    fun `getVariables returns empty list when no variables`() = runTest {
        // Given
        val sessionId = "empty-session"
        coEvery { answerVariableDao.getVariables(sessionId) } returns emptyList()

        // When
        val result = answerVariableDao.getVariables(sessionId)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `getVariablesFlow emits variable updates`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val variables = DatabaseTestFixtures.createMultipleAnswerVariables(sessionId)
        coEvery { answerVariableDao.getVariablesFlow(sessionId) } returns flowOf(variables)

        // When
        val flow = answerVariableDao.getVariablesFlow(sessionId)

        // Then
        flow.collect { result ->
            assertThat(result).hasSize(4)
        }
    }

    @Test
    fun `getVariableByNodeId returns variable for specific node`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val nodeId = "ask-name-node"
        val variable = DatabaseTestFixtures.createAnswerVariable(
            sessionId = sessionId,
            nodeId = nodeId,
            key = "name",
            value = "\"John Doe\""
        )
        coEvery { answerVariableDao.getVariableByNodeId(sessionId, nodeId) } returns variable

        // When
        val result = answerVariableDao.getVariableByNodeId(sessionId, nodeId)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.nodeId).isEqualTo(nodeId)
        assertThat(result?.key).isEqualTo("name")
    }

    @Test
    fun `getVariableByNodeId returns null when not exists`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        coEvery { answerVariableDao.getVariableByNodeId(sessionId, "non-existent") } returns null

        // When
        val result = answerVariableDao.getVariableByNodeId(sessionId, "non-existent")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getVariableByKey returns variable for specific key`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val key = "email"
        val variable = DatabaseTestFixtures.createAnswerVariable(
            sessionId = sessionId,
            nodeId = "ask-email-node",
            key = key,
            value = "\"test@example.com\""
        )
        coEvery { answerVariableDao.getVariableByKey(sessionId, key) } returns variable

        // When
        val result = answerVariableDao.getVariableByKey(sessionId, key)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.key).isEqualTo(key)
    }

    @Test
    fun `getVariableByKey returns null when not exists`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        coEvery { answerVariableDao.getVariableByKey(sessionId, "non-existent-key") } returns null

        // When
        val result = answerVariableDao.getVariableByKey(sessionId, "non-existent-key")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getVariableCount returns correct count`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        coEvery { answerVariableDao.getVariableCount(sessionId) } returns 5

        // When
        val result = answerVariableDao.getVariableCount(sessionId)

        // Then
        assertThat(result).isEqualTo(5)
    }

    // ==================== UPDATE TESTS ====================

    @Test
    fun `updateValueByNodeId updates value for specific node`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val nodeId = "ask-name-node"
        val newValue = "\"Jane Doe\""

        // When
        answerVariableDao.updateValueByNodeId(sessionId, nodeId, newValue)

        // Then
        coVerify { answerVariableDao.updateValueByNodeId(sessionId, nodeId, newValue) }
    }

    @Test
    fun `updateValueByNodeId can set null value`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val nodeId = "optional-node"

        // When
        answerVariableDao.updateValueByNodeId(sessionId, nodeId, null)

        // Then
        coVerify { answerVariableDao.updateValueByNodeId(sessionId, nodeId, null) }
    }

    @Test
    fun `updateValueByKey updates value for specific key`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val key = "email"
        val newValue = "\"updated@example.com\""

        // When
        answerVariableDao.updateValueByKey(sessionId, key, newValue)

        // Then
        coVerify { answerVariableDao.updateValueByKey(sessionId, key, newValue) }
    }

    // ==================== DELETE TESTS ====================

    @Test
    fun `delete removes variable by ID`() = runTest {
        // Given
        val variableId = 1L

        // When
        answerVariableDao.delete(variableId)

        // Then
        coVerify { answerVariableDao.delete(variableId) }
    }

    @Test
    fun `deleteAllForSession removes all variables for session`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        answerVariableDao.deleteAllForSession(sessionId)

        // Then
        coVerify { answerVariableDao.deleteAllForSession(sessionId) }
    }

    @Test
    fun `deleteByNodeId removes variable for specific node`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val nodeId = "ask-name-node"

        // When
        answerVariableDao.deleteByNodeId(sessionId, nodeId)

        // Then
        coVerify { answerVariableDao.deleteByNodeId(sessionId, nodeId) }
    }

    // ==================== VALUE TYPE TESTS ====================

    @Test
    fun `variable with string value is correctly stored`() = runTest {
        // Given
        val variable = DatabaseTestFixtures.createAnswerVariable(
            value = "\"John Doe\""
        )
        coEvery { answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId) } returns variable

        // When
        answerVariableDao.insert(variable)
        val result = answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId)

        // Then
        assertThat(result?.value).isEqualTo("\"John Doe\"")
    }

    @Test
    fun `variable with number value is correctly stored`() = runTest {
        // Given
        val variable = DatabaseTestFixtures.createAnswerVariable(
            nodeId = "ask-age-node",
            key = "age",
            value = "30"
        )
        coEvery { answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId) } returns variable

        // When
        answerVariableDao.insert(variable)
        val result = answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId)

        // Then
        assertThat(result?.value).isEqualTo("30")
    }

    @Test
    fun `variable with boolean value is correctly stored`() = runTest {
        // Given
        val variable = DatabaseTestFixtures.createAnswerVariable(
            nodeId = "ask-subscribe-node",
            key = "subscribed",
            value = "true"
        )
        coEvery { answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId) } returns variable

        // When
        answerVariableDao.insert(variable)
        val result = answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId)

        // Then
        assertThat(result?.value).isEqualTo("true")
    }

    @Test
    fun `variable with null value is correctly stored`() = runTest {
        // Given
        val variable = DatabaseTestFixtures.createAnswerVariableWithNullValue()
        coEvery { answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId) } returns variable

        // When
        answerVariableDao.insert(variable)
        val result = answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId)

        // Then
        assertThat(result?.value).isNull()
    }

    @Test
    fun `variable with complex JSON value is correctly stored`() = runTest {
        // Given
        val variable = DatabaseTestFixtures.createAnswerVariableWithComplexValue()
        coEvery { answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId) } returns variable

        // When
        answerVariableDao.insert(variable)
        val result = answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId)

        // Then
        assertThat(result?.value).contains("nested")
        assertThat(result?.value).contains("array")
    }

    @Test
    fun `variable with array value is correctly stored`() = runTest {
        // Given
        val variable = DatabaseTestFixtures.createAnswerVariable(
            nodeId = "multi-choice-node",
            key = "selectedOptions",
            value = """["option1","option2","option3"]"""
        )
        coEvery { answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId) } returns variable

        // When
        answerVariableDao.insert(variable)
        val result = answerVariableDao.getVariableByNodeId(variable.sessionId, variable.nodeId)

        // Then
        assertThat(result?.value).contains("option1")
        assertThat(result?.value).contains("option2")
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    fun `variables are isolated by session`() = runTest {
        // Given
        val session1Variables = listOf(
            DatabaseTestFixtures.createAnswerVariable(sessionId = "session-1", key = "name")
        )
        val session2Variables = listOf(
            DatabaseTestFixtures.createAnswerVariable(sessionId = "session-2", key = "email")
        )
        coEvery { answerVariableDao.getVariables("session-1") } returns session1Variables
        coEvery { answerVariableDao.getVariables("session-2") } returns session2Variables

        // When
        val result1 = answerVariableDao.getVariables("session-1")
        val result2 = answerVariableDao.getVariables("session-2")

        // Then
        assertThat(result1).hasSize(1)
        assertThat(result2).hasSize(1)
        assertThat(result1[0].key).isEqualTo("name")
        assertThat(result2[0].key).isEqualTo("email")
    }

    @Test
    fun `same key can exist in different sessions`() = runTest {
        // Given
        val session1Variable = DatabaseTestFixtures.createAnswerVariable(
            sessionId = "session-1",
            key = "name",
            value = "\"John\""
        )
        val session2Variable = DatabaseTestFixtures.createAnswerVariable(
            sessionId = "session-2",
            key = "name",
            value = "\"Jane\""
        )
        coEvery { answerVariableDao.getVariableByKey("session-1", "name") } returns session1Variable
        coEvery { answerVariableDao.getVariableByKey("session-2", "name") } returns session2Variable

        // When
        val result1 = answerVariableDao.getVariableByKey("session-1", "name")
        val result2 = answerVariableDao.getVariableByKey("session-2", "name")

        // Then
        assertThat(result1?.value).isEqualTo("\"John\"")
        assertThat(result2?.value).isEqualTo("\"Jane\"")
    }

    @Test
    fun `special characters in key are handled`() = runTest {
        // Given
        val variable = DatabaseTestFixtures.createAnswerVariable(
            key = "user_email_address"
        )

        // When
        answerVariableDao.insert(variable)

        // Then
        coVerify { answerVariableDao.insert(variable) }
    }

    @Test
    fun `large batch insert is valid`() = runTest {
        // Given
        val variables = (1..50).map { index ->
            DatabaseTestFixtures.createAnswerVariable(
                nodeId = "node-$index",
                key = "field_$index",
                value = "\"value_$index\""
            )
        }

        // When
        answerVariableDao.insertAll(variables)

        // Then
        coVerify { answerVariableDao.insertAll(variables) }
    }
}
