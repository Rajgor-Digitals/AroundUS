package com.aroundus.app

import com.aroundus.app.models.UserEntity
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.resolveBestAvatarPath
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProfilePhotoSyncTest {

    private val gson = Gson()
    private val userRepository = UserRepository()

    @Before
    fun setUp() {
        SupabaseClient.clearTestSession()
    }

    @After
    fun tearDown() {
        SupabaseClient.clearTestSession()
    }

    @Test
    fun testUserEntityDeserializer_prefersRemoteUrlOverNullSiblingColumns() {
        val json = """
            {
                "user_id": "usr_123",
                "name": "Test User",
                "email": "test@aroundus.app",
                "profile": "https://example.supabase.co/storage/v1/object/public/avatars/usr_123/a.jpg",
                "profile_photo": null
            }
        """.trimIndent()

        val user = gson.fromJson(json, UserEntity::class.java)
        assertEquals("usr_123", user.userId)
        assertEquals(
            "https://example.supabase.co/storage/v1/object/public/avatars/usr_123/a.jpg",
            user.profilePhoto
        )
        assertEquals(user.profilePhoto, user.profile)
    }

    @Test
    fun testUserEntityDeserializer_prefersHttpUrlOverLocalPath() {
        val json = """
            {
                "user_id": "usr_456",
                "name": "Neighbor",
                "email": "neighbor@aroundus.app",
                "profile": "/data/user/0/com.aroundus.app/files/avatar_usr_456.jpg",
                "profile_photo": "https://example.supabase.co/storage/v1/object/public/avatars/usr_456/b.jpg"
            }
        """.trimIndent()

        val user = gson.fromJson(json, UserEntity::class.java)
        assertEquals(
            "https://example.supabase.co/storage/v1/object/public/avatars/usr_456/b.jpg",
            user.profilePhoto
        )
    }

    @Test
    fun testUserEntityRoundTripSerialization() {
        val entity = UserEntity(
            userId = "usr_789",
            name = "Shrikant",
            email = "shrikant@aroundus.app",
            profilePhoto = "https://example.com/avatar.jpg"
        )
        val serialized = gson.toJson(entity)
        val deserialized = gson.fromJson(serialized, UserEntity::class.java)
        assertEquals(entity.userId, deserialized.userId)
        assertEquals(entity.name, deserialized.name)
        assertEquals(entity.email, deserialized.email)
        assertEquals(entity.profilePhoto, deserialized.profilePhoto)
    }

    @Test
    fun testResolveBestAvatarPath_ignoresForeignLocalPathsForOtherUsers() {
        val foreignLocal = "/data/user/0/com.aroundus.app/files/avatar_other.jpg"
        val resolvedNoFallback = resolveBestAvatarPath(
            primary = foreignLocal,
            cached = null,
            isCurrentUser = false
        )
        assertNull("Foreign local file path should not be used for another user", resolvedNoFallback)

        val remoteUrl = "https://example.supabase.co/storage/v1/object/public/avatars/other/photo.jpg"
        val resolvedWithRemoteCache = resolveBestAvatarPath(
            primary = foreignLocal,
            cached = remoteUrl,
            isCurrentUser = false
        )
        assertEquals(remoteUrl, resolvedWithRemoteCache)
    }

    @Test
    fun testResolveBestAvatarPath_allowsLocalPathForCurrentUser() {
        val localPath = "/data/user/0/com.aroundus.app/files/avatar_me.jpg"
        val resolved = resolveBestAvatarPath(
            primary = null,
            cached = null,
            isCurrentUser = true,
            localFallbackPath = localPath
        )
        assertEquals(localPath, resolved)
    }

    @Test
    fun testUpdateProfilePhotoUrl_rejectsNonHttpUrl() = runBlocking {
        SupabaseClient.setTestSession("usr_a", "a@aroundus.app", "User A")
        val res = userRepository.updateProfilePhotoUrl(
            userId = "usr_a",
            email = "a@aroundus.app",
            remoteUrl = "/data/user/0/local.jpg"
        )
        assertTrue(res is NetworkResult.Error)
        assertTrue((res as NetworkResult.Error).message.contains("Invalid remote URL", ignoreCase = true))
    }
}
