package com.aroundus.app

import com.aroundus.app.models.UserEntity
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.UserRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProfileSecurityTest {

    private val userRepository = UserRepository()

    private val userAId = "usr_user_a_111"
    private val userAEmail = "userA@aroundus.app"
    private val userAName = "User A"

    private val userBId = "usr_user_b_222"
    private val userBEmail = "userB@aroundus.app"
    private val userBName = "User B"

    @Before
    fun setUp() {
        SupabaseClient.clearTestSession()
    }

    @After
    fun tearDown() {
        SupabaseClient.clearTestSession()
    }

    @Test
    fun testOwnershipEvaluation_ownProfileMatches() {
        // Authenticated as User A
        SupabaseClient.setTestSession(userAId, userAEmail, userAName)
        assertTrue(SupabaseClient.isLoggedIn())

        // Target is User A's profile
        val targetUserId = userAId
        val loggedInUserId = SupabaseClient.getCurrentUserId() ?: ""
        val loggedInEmail = SupabaseClient.getCurrentUserEmail() ?: ""

        val isOwnProfile = SupabaseClient.isLoggedIn() && loggedInUserId.isNotBlank() && (
            targetUserId == loggedInUserId ||
            (loggedInEmail.isNotBlank() && targetUserId.equals(loggedInEmail, ignoreCase = true))
        )

        assertTrue("User A viewing User A profile must evaluate to own profile", isOwnProfile)
    }

    @Test
    fun testOwnershipEvaluation_otherUserProfileIsReadOnly() {
        // Authenticated as User A
        SupabaseClient.setTestSession(userAId, userAEmail, userAName)

        // Target is User B's profile
        val targetUserId = userBId
        val loggedInUserId = SupabaseClient.getCurrentUserId() ?: ""
        val loggedInEmail = SupabaseClient.getCurrentUserEmail() ?: ""

        val isOwnProfile = SupabaseClient.isLoggedIn() && loggedInUserId.isNotBlank() && (
            targetUserId == loggedInUserId ||
            (loggedInEmail.isNotBlank() && targetUserId.equals(loggedInEmail, ignoreCase = true))
        )

        assertFalse("User A viewing User B profile must evaluate to read-only (isOwnProfile = false)", isOwnProfile)
    }

    @Test
    fun testOwnershipEvaluation_unauthenticatedUserHasNoEditAccess() {
        // No session active
        SupabaseClient.clearTestSession()
        assertFalse(SupabaseClient.isLoggedIn())

        val targetUserId = userBId
        val loggedInUserId = SupabaseClient.getCurrentUserId() ?: ""
        val loggedInEmail = SupabaseClient.getCurrentUserEmail() ?: ""

        val isOwnProfile = SupabaseClient.isLoggedIn() && loggedInUserId.isNotBlank() && (
            targetUserId == loggedInUserId ||
            (loggedInEmail.isNotBlank() && targetUserId.equals(loggedInEmail, ignoreCase = true))
        )

        assertFalse("Unauthenticated user must never have edit access to any profile", isOwnProfile)
    }

    @Test
    fun testUserACannotUpdateUserBProfile() = runBlocking {
        // Log in as User A
        SupabaseClient.setTestSession(userAId, userAEmail, userAName)

        // User A attempts to update User B's profile by supplying User B's ID
        val maliciousPayload = UserEntity(
            userId = userBId,
            name = "Hacked by User A",
            email = userBEmail,
            bio = "Unauthorized Bio Change",
            location = "Hacked Location"
        )

        val result = userRepository.updateUserProfile(maliciousPayload)

        assertTrue("Update must be rejected as an Error", result is NetworkResult.Error)
        val errorMessage = (result as NetworkResult.Error).message
        assertTrue(
            "Error message must indicate security violation or unauthorized access. Actual: $errorMessage",
            errorMessage.contains("Security Violation", ignoreCase = true) ||
            errorMessage.contains("not authorized", ignoreCase = true) ||
            errorMessage.contains("Forbidden", ignoreCase = true)
        )
    }

    @Test
    fun testUserACannotChangeUserBProfilePhoto() = runBlocking {
        // Log in as User A
        SupabaseClient.setTestSession(userAId, userAEmail, userAName)

        // User A attempts to update User B's photo URL
        val result = userRepository.updateProfilePhotoUrl(
            userId = userBId,
            email = userBEmail,
            remoteUrl = "https://example.com/hacked_avatar.jpg"
        )

        assertTrue("Photo URL update on User B must fail", result is NetworkResult.Error)
        val errorMessage = (result as NetworkResult.Error).message
        assertTrue(
            "Error must indicate security violation. Actual: $errorMessage",
            errorMessage.contains("Security Violation", ignoreCase = true) ||
            errorMessage.contains("not authorized", ignoreCase = true) ||
            errorMessage.contains("Cannot modify", ignoreCase = true)
        )
    }

    @Test
    fun testUserACannotUploadPhotoForUserB() = runBlocking {
        // Log in as User A
        SupabaseClient.setTestSession(userAId, userAEmail, userAName)

        val fakeBytes = "fake_image_bytes".toByteArray()
        val result = userRepository.uploadRawBytesToStorage(
            bytes = fakeBytes,
            mimeType = "image/jpeg",
            ext = "jpg",
            userId = userBId // Trying to upload to User B's folder
        )

        assertTrue("Photo upload to User B's folder must fail", result is NetworkResult.Error)
        val errorMessage = (result as NetworkResult.Error).message
        assertTrue(
            "Error must indicate security violation. Actual: $errorMessage",
            errorMessage.contains("Security Violation", ignoreCase = true) ||
            errorMessage.contains("cannot upload", ignoreCase = true)
        )
    }

    @Test
    fun testUnauthenticatedUserCannotUpdateProfile() = runBlocking {
        // No session active
        SupabaseClient.clearTestSession()

        val payload = UserEntity(
            userId = userAId,
            name = "Anonymous Hacker",
            email = userAEmail
        )

        val result = userRepository.updateUserProfile(payload)

        assertTrue("Unauthenticated update must be rejected", result is NetworkResult.Error)
        val errorMessage = (result as NetworkResult.Error).message
        assertTrue(
            "Error message must specify Unauthorized/session required. Actual: $errorMessage",
            errorMessage.contains("Unauthorized", ignoreCase = true) ||
            errorMessage.contains("session required", ignoreCase = true)
        )
    }

    @Test
    fun testUnauthenticatedUserCannotUpdatePhoto() = runBlocking {
        // No session active
        SupabaseClient.clearTestSession()

        val result = userRepository.updateProfilePhotoUrl(
            userId = userAId,
            email = userAEmail,
            remoteUrl = "https://example.com/avatar.jpg"
        )

        assertTrue("Unauthenticated photo update must fail", result is NetworkResult.Error)
        val errorMessage = (result as NetworkResult.Error).message
        assertTrue(errorMessage.contains("Unauthorized", ignoreCase = true))
    }

    @Test
    fun testUserACannotTamperWithEmailAddress() = runBlocking {
        // Log in as User A
        SupabaseClient.setTestSession(userAId, userAEmail, userAName)

        // Attempt to change email to User B's email while using User A's ID
        val emailTamperPayload = UserEntity(
            userId = userAId,
            name = "User A",
            email = userBEmail // Tampering with email
        )

        val result = userRepository.updateUserProfile(emailTamperPayload)

        assertTrue("Tampering with email must be rejected", result is NetworkResult.Error)
        val errorMessage = (result as NetworkResult.Error).message
        assertTrue(
            "Error must mention email restriction. Actual: $errorMessage",
            errorMessage.contains("email", ignoreCase = true) &&
            (errorMessage.contains("Security Violation", ignoreCase = true) || errorMessage.contains("prohibited", ignoreCase = true))
        )
    }
}
