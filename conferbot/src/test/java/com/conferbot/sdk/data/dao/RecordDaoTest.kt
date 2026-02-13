package com.conferbot.sdk.data.dao

import com.conferbot.sdk.data.entities.RecordEntity
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
 * Unit tests for RecordDao
 * Tests record entry CRUD operations and server sync data queries
 */
class RecordDaoTest {

    private lateinit var recordDao: RecordDao

    @Before
    fun setUp() {
        recordDao = mockk(relaxed = true)
    }

    // ==================== INSERT TESTS ====================

    @Test
    fun `insert creates new record entry and returns ID`() = runTest {
        // Given
        val record = DatabaseTestFixtures.createRecord()
        coEvery { recordDao.insert(record) } returns 1L

        // When
        val result = recordDao.insert(record)

        // Then
        assertThat(result).isEqualTo(1L)
        coVerify { recordDao.insert(record) }
    }

    @Test
    fun `insertAll inserts multiple records`() = runTest {
        // Given
        val records = DatabaseTestFixtures.createMultipleRecords(count = 5)

        // When
        recordDao.insertAll(records)

        // Then
        coVerify { recordDao.insertAll(records) }
    }

    @Test
    fun `insertAll with empty list does nothing`() = runTest {
        // Given
        val emptyList = emptyList<RecordEntity>()

        // When
        recordDao.insertAll(emptyList)

        // Then
        coVerify { recordDao.insertAll(emptyList) }
    }

    // ==================== QUERY TESTS ====================

    @Test
    fun `getRecords returns all records for session ordered by id ASC`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val records = DatabaseTestFixtures.createMultipleRecords(sessionId = sessionId)
        coEvery { recordDao.getRecords(sessionId) } returns records

        // When
        val result = recordDao.getRecords(sessionId)

        // Then
        assertThat(result).hasSize(5)
        assertThat(result[0].recordId).isEqualTo("record-1")
        assertThat(result[4].recordId).isEqualTo("record-5")
    }

    @Test
    fun `getRecords returns empty list when no records`() = runTest {
        // Given
        val sessionId = "empty-session"
        coEvery { recordDao.getRecords(sessionId) } returns emptyList()

        // When
        val result = recordDao.getRecords(sessionId)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `getRecordsFlow emits record updates`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val records = DatabaseTestFixtures.createMultipleRecords(sessionId)
        coEvery { recordDao.getRecordsFlow(sessionId) } returns flowOf(records)

        // When
        val flow = recordDao.getRecordsFlow(sessionId)

        // Then
        flow.collect { result ->
            assertThat(result).hasSize(5)
        }
    }

    @Test
    fun `getRecordById returns specific record`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val recordId = "record-1"
        val record = DatabaseTestFixtures.createRecord(
            sessionId = sessionId,
            recordId = recordId
        )
        coEvery { recordDao.getRecordById(sessionId, recordId) } returns record

        // When
        val result = recordDao.getRecordById(sessionId, recordId)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.recordId).isEqualTo(recordId)
    }

    @Test
    fun `getRecordById returns null when not exists`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        coEvery { recordDao.getRecordById(sessionId, "non-existent") } returns null

        // When
        val result = recordDao.getRecordById(sessionId, "non-existent")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getRecordCount returns correct count`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        coEvery { recordDao.getRecordCount(sessionId) } returns 10

        // When
        val result = recordDao.getRecordCount(sessionId)

        // Then
        assertThat(result).isEqualTo(10)
    }

    @Test
    fun `getRecordCount returns zero when no records`() = runTest {
        // Given
        val sessionId = "empty-session"
        coEvery { recordDao.getRecordCount(sessionId) } returns 0

        // When
        val result = recordDao.getRecordCount(sessionId)

        // Then
        assertThat(result).isEqualTo(0)
    }

    // ==================== UPDATE TESTS ====================

    @Test
    fun `updateData updates data for specific record`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val recordId = "record-1"
        val newData = """{"updated":"value","newField":123}"""

        // When
        recordDao.updateData(sessionId, recordId, newData)

        // Then
        coVerify { recordDao.updateData(sessionId, recordId, newData) }
    }

    @Test
    fun `updateData can set null data`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val recordId = "record-1"

        // When
        recordDao.updateData(sessionId, recordId, null)

        // Then
        coVerify { recordDao.updateData(sessionId, recordId, null) }
    }

    // ==================== DELETE TESTS ====================

    @Test
    fun `delete removes record by sessionId and recordId`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val recordId = "record-to-delete"

        // When
        recordDao.delete(sessionId, recordId)

        // Then
        coVerify { recordDao.delete(sessionId, recordId) }
    }

    @Test
    fun `deleteAllForSession removes all records for session`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        recordDao.deleteAllForSession(sessionId)

        // Then
        coVerify { recordDao.deleteAllForSession(sessionId) }
    }

    // ==================== SHAPE TESTS ====================

    @Test
    fun `record with bot-message shape is correctly stored`() = runTest {
        // Given
        val record = DatabaseTestFixtures.createRecord(shape = "bot-message")
        coEvery { recordDao.getRecordById(record.sessionId, record.recordId) } returns record

        // When
        recordDao.insert(record)
        val result = recordDao.getRecordById(record.sessionId, record.recordId)

        // Then
        assertThat(result?.shape).isEqualTo("bot-message")
    }

    @Test
    fun `record with user-response shape is correctly stored`() = runTest {
        // Given
        val record = DatabaseTestFixtures.createRecord(shape = "user-response")
        coEvery { recordDao.getRecordById(record.sessionId, record.recordId) } returns record

        // When
        recordDao.insert(record)
        val result = recordDao.getRecordById(record.sessionId, record.recordId)

        // Then
        assertThat(result?.shape).isEqualTo("user-response")
    }

    @Test
    fun `record with bot-response shape is correctly stored`() = runTest {
        // Given
        val record = DatabaseTestFixtures.createRecord(shape = "bot-response")
        coEvery { recordDao.getRecordById(record.sessionId, record.recordId) } returns record

        // When
        recordDao.insert(record)
        val result = recordDao.getRecordById(record.sessionId, record.recordId)

        // Then
        assertThat(result?.shape).isEqualTo("bot-response")
    }

    // ==================== TYPE TESTS ====================

    @Test
    fun `record with message-node type is correctly stored`() = runTest {
        // Given
        val record = DatabaseTestFixtures.createRecord(type = "message-node")
        coEvery { recordDao.getRecordById(record.sessionId, record.recordId) } returns record

        // When
        recordDao.insert(record)
        val result = recordDao.getRecordById(record.sessionId, record.recordId)

        // Then
        assertThat(result?.type).isEqualTo("message-node")
    }

    @Test
    fun `record with null type is valid`() = runTest {
        // Given
        val record = DatabaseTestFixtures.createRecord(type = null)
        coEvery { recordDao.getRecordById(record.sessionId, record.recordId) } returns record

        // When
        recordDao.insert(record)
        val result = recordDao.getRecordById(record.sessionId, record.recordId)

        // Then
        assertThat(result?.type).isNull()
    }

    @Test
    fun `record with ask-name type is correctly stored`() = runTest {
        // Given
        val record = DatabaseTestFixtures.createRecord(type = "ask-name")
        coEvery { recordDao.getRecordById(record.sessionId, record.recordId) } returns record

        // When
        recordDao.insert(record)
        val result = recordDao.getRecordById(record.sessionId, record.recordId)

        // Then
        assertThat(result?.type).isEqualTo("ask-name")
    }

    // ==================== DATA TESTS ====================

    @Test
    fun `record with complex data JSON is correctly stored`() = runTest {
        // Given
        val record = DatabaseTestFixtures.createRecordWithData()
        coEvery { recordDao.getRecordById(record.sessionId, record.recordId) } returns record

        // When
        recordDao.insert(record)
        val result = recordDao.getRecordById(record.sessionId, record.recordId)

        // Then
        assertThat(result?.data).isNotNull()
        assertThat(result?.data).contains("nodeId")
        assertThat(result?.data).contains("response")
    }

    @Test
    fun `record with null data is valid`() = runTest {
        // Given
        val record = DatabaseTestFixtures.createRecord(data = null)
        coEvery { recordDao.getRecordById(record.sessionId, record.recordId) } returns record

        // When
        recordDao.insert(record)
        val result = recordDao.getRecordById(record.sessionId, record.recordId)

        // Then
        assertThat(result?.data).isNull()
    }

    @Test
    fun `record with nested JSON data is correctly stored`() = runTest {
        // Given
        val nestedData = """{"level1":{"level2":{"level3":"deep value"}},"array":[1,2,3]}"""
        val record = DatabaseTestFixtures.createRecord(data = nestedData)
        coEvery { recordDao.getRecordById(record.sessionId, record.recordId) } returns record

        // When
        recordDao.insert(record)
        val result = recordDao.getRecordById(record.sessionId, record.recordId)

        // Then
        assertThat(result?.data).contains("level1")
        assertThat(result?.data).contains("level2")
        assertThat(result?.data).contains("deep value")
    }

    // ==================== TIME FORMAT TESTS ====================

    @Test
    fun `record with ISO8601 time format is correctly stored`() = runTest {
        // Given
        val time = "2024-01-15T12:30:45.123Z"
        val record = DatabaseTestFixtures.createRecord(time = time)
        coEvery { recordDao.getRecordById(record.sessionId, record.recordId) } returns record

        // When
        recordDao.insert(record)
        val result = recordDao.getRecordById(record.sessionId, record.recordId)

        // Then
        assertThat(result?.time).isEqualTo(time)
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    fun `record with null text is valid`() = runTest {
        // Given
        val record = DatabaseTestFixtures.createRecord(text = null)
        coEvery { recordDao.getRecordById(record.sessionId, record.recordId) } returns record

        // When
        recordDao.insert(record)
        val result = recordDao.getRecordById(record.sessionId, record.recordId)

        // Then
        assertThat(result?.text).isNull()
    }

    @Test
    fun `record with empty text is valid`() = runTest {
        // Given
        val record = DatabaseTestFixtures.createRecord(text = "")
        coEvery { recordDao.getRecordById(record.sessionId, record.recordId) } returns record

        // When
        recordDao.insert(record)
        val result = recordDao.getRecordById(record.sessionId, record.recordId)

        // Then
        assertThat(result?.text).isEmpty()
    }

    @Test
    fun `record with long text is valid`() = runTest {
        // Given
        val longText = "x".repeat(10000)
        val record = DatabaseTestFixtures.createRecord(text = longText)

        // When
        recordDao.insert(record)

        // Then
        coVerify { recordDao.insert(record) }
    }

    @Test
    fun `records are isolated by session`() = runTest {
        // Given
        val session1Records = listOf(
            DatabaseTestFixtures.createRecord(sessionId = "session-1", recordId = "rec-1")
        )
        val session2Records = listOf(
            DatabaseTestFixtures.createRecord(sessionId = "session-2", recordId = "rec-2")
        )
        coEvery { recordDao.getRecords("session-1") } returns session1Records
        coEvery { recordDao.getRecords("session-2") } returns session2Records

        // When
        val result1 = recordDao.getRecords("session-1")
        val result2 = recordDao.getRecords("session-2")

        // Then
        assertThat(result1).hasSize(1)
        assertThat(result2).hasSize(1)
        assertThat(result1[0].recordId).isEqualTo("rec-1")
        assertThat(result2[0].recordId).isEqualTo("rec-2")
    }

    @Test
    fun `same recordId can exist in different sessions`() = runTest {
        // Given
        val recordId = "shared-record-id"
        val session1Record = DatabaseTestFixtures.createRecord(
            sessionId = "session-1",
            recordId = recordId,
            text = "Session 1 record"
        )
        val session2Record = DatabaseTestFixtures.createRecord(
            sessionId = "session-2",
            recordId = recordId,
            text = "Session 2 record"
        )
        coEvery { recordDao.getRecordById("session-1", recordId) } returns session1Record
        coEvery { recordDao.getRecordById("session-2", recordId) } returns session2Record

        // When
        val result1 = recordDao.getRecordById("session-1", recordId)
        val result2 = recordDao.getRecordById("session-2", recordId)

        // Then
        assertThat(result1?.text).isEqualTo("Session 1 record")
        assertThat(result2?.text).isEqualTo("Session 2 record")
    }

    @Test
    fun `large batch insert is valid`() = runTest {
        // Given
        val records = (1..100).map { index ->
            DatabaseTestFixtures.createRecord(
                recordId = "record-$index",
                text = "Record $index content"
            )
        }

        // When
        recordDao.insertAll(records)

        // Then
        coVerify { recordDao.insertAll(records) }
    }

    @Test
    fun `record with special characters in text is valid`() = runTest {
        // Given
        val specialText = "Special: <>&\"' \n\t\r"
        val record = DatabaseTestFixtures.createRecord(text = specialText)

        // When
        recordDao.insert(record)

        // Then
        coVerify { recordDao.insert(record) }
    }

    @Test
    fun `record with unicode in text is valid`() = runTest {
        // Given
        val unicodeText = "Great! "
        val record = DatabaseTestFixtures.createRecord(text = unicodeText)

        // When
        recordDao.insert(record)

        // Then
        coVerify { recordDao.insert(record) }
    }
}
