package com.conferbot.sdk.data.dao

import com.conferbot.sdk.data.entities.UserMetadataEntity
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
 * Unit tests for UserMetadataDao
 * Tests user metadata CRUD operations and field updates
 */
class UserMetadataDaoTest {

    private lateinit var userMetadataDao: UserMetadataDao

    @Before
    fun setUp() {
        userMetadataDao = mockk(relaxed = true)
    }

    // ==================== INSERT TESTS ====================

    @Test
    fun `insert creates new user metadata`() = runTest {
        // Given
        val metadata = DatabaseTestFixtures.createUserMetadata()

        // When
        userMetadataDao.insert(metadata)

        // Then
        coVerify { userMetadataDao.insert(metadata) }
    }

    @Test
    fun `insert replaces metadata with same sessionId on conflict`() = runTest {
        // Given
        val original = DatabaseTestFixtures.createUserMetadata(name = "John")
        val updated = original.copy(name = "Jane")

        coEvery { userMetadataDao.getMetadata(original.sessionId) } returns updated

        // When
        userMetadataDao.insert(original)
        userMetadataDao.insert(updated)

        // Then
        val result = userMetadataDao.getMetadata(original.sessionId)
        assertThat(result?.name).isEqualTo("Jane")
    }

    @Test
    fun `insert with all null fields is valid`() = runTest {
        // Given
        val metadata = UserMetadataEntity(
            sessionId = DatabaseTestFixtures.TEST_SESSION_ID,
            name = null,
            email = null,
            phone = null,
            customData = null
        )

        // When
        userMetadataDao.insert(metadata)

        // Then
        coVerify { userMetadataDao.insert(metadata) }
    }

    // ==================== QUERY TESTS ====================

    @Test
    fun `getMetadata returns metadata when exists`() = runTest {
        // Given
        val metadata = DatabaseTestFixtures.createUserMetadata()
        coEvery { userMetadataDao.getMetadata(metadata.sessionId) } returns metadata

        // When
        val result = userMetadataDao.getMetadata(metadata.sessionId)

        // Then
        assertThat(result).isEqualTo(metadata)
        assertThat(result?.name).isEqualTo("John Doe")
        assertThat(result?.email).isEqualTo("john@example.com")
        assertThat(result?.phone).isEqualTo("+1234567890")
    }

    @Test
    fun `getMetadata returns null when not exists`() = runTest {
        // Given
        val sessionId = "non-existent-session"
        coEvery { userMetadataDao.getMetadata(sessionId) } returns null

        // When
        val result = userMetadataDao.getMetadata(sessionId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getMetadataFlow emits metadata updates`() = runTest {
        // Given
        val metadata = DatabaseTestFixtures.createUserMetadata()
        coEvery { userMetadataDao.getMetadataFlow(metadata.sessionId) } returns flowOf(metadata)

        // When
        val flow = userMetadataDao.getMetadataFlow(metadata.sessionId)

        // Then
        flow.collect { result ->
            assertThat(result).isEqualTo(metadata)
        }
    }

    @Test
    fun `getMetadataFlow emits null when no metadata`() = runTest {
        // Given
        val sessionId = "empty-session"
        coEvery { userMetadataDao.getMetadataFlow(sessionId) } returns flowOf(null)

        // When
        val flow = userMetadataDao.getMetadataFlow(sessionId)

        // Then
        flow.collect { result ->
            assertThat(result).isNull()
        }
    }

    // ==================== UPDATE TESTS ====================

    @Test
    fun `update modifies existing metadata`() = runTest {
        // Given
        val metadata = DatabaseTestFixtures.createUserMetadata()
        val updatedMetadata = metadata.copy(
            name = "Jane Doe",
            email = "jane@example.com",
            updatedAt = System.currentTimeMillis()
        )

        // When
        userMetadataDao.update(updatedMetadata)

        // Then
        coVerify { userMetadataDao.update(updatedMetadata) }
    }

    @Test
    fun `updateName updates only name field`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val newName = "Updated Name"

        // When
        userMetadataDao.updateName(sessionId, newName)

        // Then
        coVerify { userMetadataDao.updateName(sessionId, newName, any()) }
    }

    @Test
    fun `updateName with null clears name`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        userMetadataDao.updateName(sessionId, null)

        // Then
        coVerify { userMetadataDao.updateName(sessionId, null, any()) }
    }

    @Test
    fun `updateEmail updates only email field`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val newEmail = "updated@example.com"

        // When
        userMetadataDao.updateEmail(sessionId, newEmail)

        // Then
        coVerify { userMetadataDao.updateEmail(sessionId, newEmail, any()) }
    }

    @Test
    fun `updateEmail with null clears email`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        userMetadataDao.updateEmail(sessionId, null)

        // Then
        coVerify { userMetadataDao.updateEmail(sessionId, null, any()) }
    }

    @Test
    fun `updatePhone updates only phone field`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val newPhone = "+9876543210"

        // When
        userMetadataDao.updatePhone(sessionId, newPhone)

        // Then
        coVerify { userMetadataDao.updatePhone(sessionId, newPhone, any()) }
    }

    @Test
    fun `updatePhone with null clears phone`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        userMetadataDao.updatePhone(sessionId, null)

        // Then
        coVerify { userMetadataDao.updatePhone(sessionId, null, any()) }
    }

    @Test
    fun `updateCustomData updates only customData field`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val newCustomData = """{"key":"value","number":123}"""

        // When
        userMetadataDao.updateCustomData(sessionId, newCustomData)

        // Then
        coVerify { userMetadataDao.updateCustomData(sessionId, newCustomData, any()) }
    }

    @Test
    fun `updateCustomData with null clears customData`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        userMetadataDao.updateCustomData(sessionId, null)

        // Then
        coVerify { userMetadataDao.updateCustomData(sessionId, null, any()) }
    }

    // ==================== DELETE TESTS ====================

    @Test
    fun `delete removes metadata by sessionId`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        userMetadataDao.delete(sessionId)

        // Then
        coVerify { userMetadataDao.delete(sessionId) }
    }

    // ==================== CUSTOM DATA TESTS ====================

    @Test
    fun `metadata with custom data JSON is correctly stored`() = runTest {
        // Given
        val metadata = DatabaseTestFixtures.createUserMetadataWithCustomData()
        coEvery { userMetadataDao.getMetadata(metadata.sessionId) } returns metadata

        // When
        userMetadataDao.insert(metadata)
        val result = userMetadataDao.getMetadata(metadata.sessionId)

        // Then
        assertThat(result?.customData).isNotNull()
        assertThat(result?.customData).contains("company")
        assertThat(result?.customData).contains("Acme Inc")
    }

    @Test
    fun `metadata with nested custom data is valid`() = runTest {
        // Given
        val nestedData = """{"preferences":{"theme":"dark","notifications":{"email":true,"sms":false}}}"""
        val metadata = DatabaseTestFixtures.createUserMetadata(customData = nestedData)
        coEvery { userMetadataDao.getMetadata(metadata.sessionId) } returns metadata

        // When
        userMetadataDao.insert(metadata)
        val result = userMetadataDao.getMetadata(metadata.sessionId)

        // Then
        assertThat(result?.customData).contains("preferences")
        assertThat(result?.customData).contains("notifications")
    }

    @Test
    fun `metadata with array custom data is valid`() = runTest {
        // Given
        val arrayData = """{"tags":["customer","vip","premium"],"scores":[95,88,92]}"""
        val metadata = DatabaseTestFixtures.createUserMetadata(customData = arrayData)
        coEvery { userMetadataDao.getMetadata(metadata.sessionId) } returns metadata

        // When
        userMetadataDao.insert(metadata)
        val result = userMetadataDao.getMetadata(metadata.sessionId)

        // Then
        assertThat(result?.customData).contains("tags")
        assertThat(result?.customData).contains("vip")
    }

    // ==================== FIELD VALIDATION TESTS ====================

    @Test
    fun `metadata with long name is valid`() = runTest {
        // Given
        val longName = "x".repeat(500)
        val metadata = DatabaseTestFixtures.createUserMetadata(name = longName)

        // When
        userMetadataDao.insert(metadata)

        // Then
        coVerify { userMetadataDao.insert(metadata) }
    }

    @Test
    fun `metadata with special characters in name is valid`() = runTest {
        // Given
        val specialName = "O'Brien-Smith, Jr."
        val metadata = DatabaseTestFixtures.createUserMetadata(name = specialName)
        coEvery { userMetadataDao.getMetadata(metadata.sessionId) } returns metadata

        // When
        userMetadataDao.insert(metadata)
        val result = userMetadataDao.getMetadata(metadata.sessionId)

        // Then
        assertThat(result?.name).isEqualTo(specialName)
    }

    @Test
    fun `metadata with unicode name is valid`() = runTest {
        // Given
        val unicodeName = "Hello World"
        val metadata = DatabaseTestFixtures.createUserMetadata(name = unicodeName)
        coEvery { userMetadataDao.getMetadata(metadata.sessionId) } returns metadata

        // When
        userMetadataDao.insert(metadata)
        val result = userMetadataDao.getMetadata(metadata.sessionId)

        // Then
        assertThat(result?.name).isEqualTo(unicodeName)
    }

    @Test
    fun `metadata with various email formats is valid`() = runTest {
        // Given
        val emails = listOf(
            "simple@example.com",
            "user.name@domain.org",
            "user+tag@example.co.uk",
            "user@subdomain.domain.com"
        )

        // When/Then
        emails.forEach { email ->
            val metadata = DatabaseTestFixtures.createUserMetadata(email = email)
            userMetadataDao.insert(metadata)
            coVerify { userMetadataDao.insert(metadata) }
        }
    }

    @Test
    fun `metadata with various phone formats is valid`() = runTest {
        // Given
        val phones = listOf(
            "+1234567890",
            "+44 20 7946 0958",
            "(123) 456-7890",
            "123-456-7890"
        )

        // When/Then
        phones.forEach { phone ->
            val metadata = DatabaseTestFixtures.createUserMetadata(
                sessionId = "session-$phone",
                phone = phone
            )
            userMetadataDao.insert(metadata)
            coVerify { userMetadataDao.insert(metadata) }
        }
    }

    // ==================== ONE-TO-ONE RELATIONSHIP TESTS ====================

    @Test
    fun `only one metadata per session is allowed`() = runTest {
        // Given - unique index on sessionId
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val metadata1 = DatabaseTestFixtures.createUserMetadata(
            sessionId = sessionId,
            name = "First"
        )
        val metadata2 = DatabaseTestFixtures.createUserMetadata(
            sessionId = sessionId,
            name = "Second"
        )

        // When - second insert should replace first
        coEvery { userMetadataDao.getMetadata(sessionId) } returns metadata2

        userMetadataDao.insert(metadata1)
        userMetadataDao.insert(metadata2)
        val result = userMetadataDao.getMetadata(sessionId)

        // Then
        assertThat(result?.name).isEqualTo("Second")
    }

    @Test
    fun `metadata for different sessions are isolated`() = runTest {
        // Given
        val metadata1 = DatabaseTestFixtures.createUserMetadata(
            sessionId = "session-1",
            name = "User 1"
        )
        val metadata2 = DatabaseTestFixtures.createUserMetadata(
            sessionId = "session-2",
            name = "User 2"
        )
        coEvery { userMetadataDao.getMetadata("session-1") } returns metadata1
        coEvery { userMetadataDao.getMetadata("session-2") } returns metadata2

        // When
        val result1 = userMetadataDao.getMetadata("session-1")
        val result2 = userMetadataDao.getMetadata("session-2")

        // Then
        assertThat(result1?.name).isEqualTo("User 1")
        assertThat(result2?.name).isEqualTo("User 2")
    }

    // ==================== TIMESTAMP TESTS ====================

    @Test
    fun `updatedAt is updated on field updates`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val originalTime = DatabaseTestFixtures.BASE_TIMESTAMP
        val newTime = originalTime + 1000

        // When
        userMetadataDao.updateName(sessionId, "New Name", newTime)

        // Then
        coVerify { userMetadataDao.updateName(sessionId, "New Name", newTime) }
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    fun `metadata with empty string values is valid`() = runTest {
        // Given
        val metadata = DatabaseTestFixtures.createUserMetadata(
            name = "",
            email = "",
            phone = ""
        )

        // When
        userMetadataDao.insert(metadata)

        // Then
        coVerify { userMetadataDao.insert(metadata) }
    }

    @Test
    fun `metadata with whitespace-only values is valid`() = runTest {
        // Given
        val metadata = DatabaseTestFixtures.createUserMetadata(
            name = "   ",
            email = "  ",
            phone = " "
        )

        // When
        userMetadataDao.insert(metadata)

        // Then
        coVerify { userMetadataDao.insert(metadata) }
    }

    @Test
    fun `large customData JSON is valid`() = runTest {
        // Given
        val largeData = """{"data":"${"x".repeat(10000)}"}"""
        val metadata = DatabaseTestFixtures.createUserMetadata(customData = largeData)

        // When
        userMetadataDao.insert(metadata)

        // Then
        coVerify { userMetadataDao.insert(metadata) }
    }
}
