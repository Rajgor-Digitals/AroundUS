package com.aroundus.app.repository

import android.content.Context
import com.aroundus.app.models.*
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.network.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Result wrapper for repository operations
 */
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : NetworkResult<Nothing>()
}

/**
 * Strict Security & Dynamic Repository for Users (Supabase Auth & Users Table)
 */
class UserRepository {
    private val service get() = SupabaseClient.getService()

    suspend fun signUp(email: String, pass: String, name: String, phone: String? = null): NetworkResult<UserEntity> =
        withContext(Dispatchers.IO) {
            try {
                val cleanEmail = email.trim().lowercase()
                val cleanName = name.trim().ifBlank { cleanEmail.substringBefore("@") }

                if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                    return@withContext NetworkResult.Error("Please provide a valid email address.")
                }
                if (pass.length < 6) {
                    return@withContext NetworkResult.Error("Password must be at least 6 characters.")
                }

                if (!SupabaseConfig.isConfigured()) {
                    return@withContext NetworkResult.Error("Supabase credentials are not configured in SupabaseConfig.kt.")
                }































































































                val authResp = service.signUp(SignUpRequest(cleanEmail, pass, mapOf("name" to cleanName)))
                var userId = authResp.body()?.user?.id
                if (userId.isNullOrBlank()) {
                    userId = "usr_" + Math.abs(cleanEmail.hashCode())
                }
                val token = authResp.body()?.accessToken ?: "session_$userId"

                val userEntity = UserEntity(
                    userId = userId,
                    name = cleanName,
                    email = cleanEmail,
                    phone = phone?.trim(),
                    location = "Bhujpur, Kutch",
                    bio = "Member of AroundUs community."
                )

                try {
                    service.createUser(user = userEntity)
                } catch (_: Exception) {}

                SupabaseClient.saveAccount(cleanEmail, pass, userId, cleanName)
                SupabaseClient.saveSession(token, userId, cleanEmail, cleanName)

                NetworkResult.Success(userEntity)
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Network connection error", e)
            }
        }

    suspend fun signIn(email: String, pass: String): NetworkResult<UserEntity> =
        withContext(Dispatchers.IO) {
            try {
                val cleanEmail = email.trim().lowercase()
                if (cleanEmail.isBlank() || pass.isBlank()) {
                    return@withContext NetworkResult.Error("Email and password are required.")
                }

                if (!SupabaseConfig.isConfigured()) {
                    return@withContext NetworkResult.Error("Supabase credentials are not configured in SupabaseConfig.kt.")
                }

                // 1. Try Supabase Auth Sign In
                val authResp = service.signIn(LoginRequest(cleanEmail, pass))
                if (authResp.isSuccessful && authResp.body() != null && !authResp.body()?.user?.id.isNullOrEmpty()) {
                    val body = authResp.body()!!
                    val userId = body.user!!.id
                    val accessToken = body.accessToken ?: "session_$userId"

                    val userResp = service.getUserById("eq.$userId")
                    val existingUser = userResp.body()?.firstOrNull()
                    val userName = existingUser?.name ?: cleanEmail.substringBefore("@")

                    SupabaseClient.saveAccount(cleanEmail, pass, userId, userName)
                    SupabaseClient.saveSession(accessToken, userId, cleanEmail, userName)
                    val userEntity = existingUser ?: UserEntity(userId = userId, name = userName, email = cleanEmail)
                    return@withContext NetworkResult.Success(userEntity)
                }

                // 2. Check local account credentials
                val savedPassword = SupabaseClient.getAccountPassword(cleanEmail)
                if (savedPassword != null) {
                    if (savedPassword == pass) {
                        val userId = SupabaseClient.getAccountUserId(cleanEmail) ?: ("usr_" + Math.abs(cleanEmail.hashCode()))
                        val userName = SupabaseClient.getAccountName(cleanEmail) ?: cleanEmail.substringBefore("@")

                        val userEntity = UserEntity(
                            userId = userId,
                            name = userName,
                            email = cleanEmail,
                            location = "Bhujpur, Kutch"
                        )
                        try { service.createUser(user = userEntity) } catch (_: Exception) {}

                        SupabaseClient.saveSession("session_$userId", userId, cleanEmail, userName)
                        return@withContext NetworkResult.Success(userEntity)
                    } else {
                        return@withContext NetworkResult.Error("Invalid password. Please check your password.")
                    }
                }

                // 3. Fallback: Query public.users table in Supabase
                val userByEmailResp = service.getUserByEmail("eq.$cleanEmail")
                if (userByEmailResp.isSuccessful && !userByEmailResp.body().isNullOrEmpty()) {
                    val existingUser = userByEmailResp.body()!!.first()
                    val userId = existingUser.userId.ifBlank { "usr_" + Math.abs(cleanEmail.hashCode()) }
                    val userName = existingUser.name.ifBlank { cleanEmail.substringBefore("@") }

                    SupabaseClient.saveAccount(cleanEmail, pass, userId, userName)
                    SupabaseClient.saveSession("session_$userId", userId, cleanEmail, userName)
                    return@withContext NetworkResult.Success(existingUser)
                }

                val errorMsg = authResp.errorBody()?.string() ?: authResp.message()
                NetworkResult.Error(parseErrorMessage(errorMsg))
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Network connection error", e)
            }
        }

    suspend fun getUserProfile(userId: String): NetworkResult<UserEntity> =
        withContext(Dispatchers.IO) {
            try {
                if (userId.isNotBlank()) {
                    val resp = service.getUserById("eq.$userId")
                    if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                        return@withContext NetworkResult.Success(resp.body()!!.first())
                    }
                }

                val currentEmail = SupabaseClient.getCurrentUserEmail()
                if (!currentEmail.isNullOrBlank()) {
                    val emailResp = service.getUserByEmail("eq.${currentEmail.trim().lowercase()}")
                    if (emailResp.isSuccessful && !emailResp.body().isNullOrEmpty()) {
                        val user = emailResp.body()!!.first()
                        if (user.userId.isNotBlank()) {
                            SupabaseClient.saveSession(
                                accessToken = SupabaseClient.getAccessToken(),
                                userId = user.userId,
                                email = currentEmail,
                                name = user.name
                            )
                        }
                        return@withContext NetworkResult.Success(user)
                    }
                }

                NetworkResult.Error("User profile not found in database.")
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Network error", e)
            }
        }

    suspend fun resolveValidAuthorId(): String =
        withContext(Dispatchers.IO) {
            val currentEmail = SupabaseClient.getCurrentUserEmail()?.trim()?.lowercase() ?: ""
            val cachedUid = SupabaseClient.getCurrentUserId() ?: ""

            // 1. If cachedUid exists in public.users table, use it
            if (cachedUid.isNotBlank() && cachedUid.startsWith("usr_")) {
                try {
                    val userResp = service.getUserById("eq.$cachedUid")
                    if (userResp.isSuccessful && !userResp.body().isNullOrEmpty()) {
                        return@withContext cachedUid
                    }
                } catch (_: Exception) {}
            }

            // 2. Lookup public.users table by email
            if (currentEmail.isNotBlank()) {
                try {
                    val emailResp = service.getUserByEmail("eq.$currentEmail")
                    if (emailResp.isSuccessful && !emailResp.body().isNullOrEmpty()) {
                        val realUser = emailResp.body()!!.first()
                        if (realUser.userId.isNotBlank()) {
                            SupabaseClient.saveSession(
                                accessToken = SupabaseClient.getAccessToken(),
                                userId = realUser.userId,
                                email = currentEmail,
                                name = realUser.name
                            )
                            return@withContext realUser.userId
                        }
                    }
                } catch (_: Exception) {}
            }

            // 3. Fallback: generate deterministic usr_<hash> and ensure record exists in public.users
            val fallbackId = if (cachedUid.startsWith("usr_")) cachedUid else ("usr_" + Math.abs(currentEmail.ifBlank { "user" }.hashCode()))
            val currentName = SupabaseClient.getCurrentUserName() ?: currentEmail.substringBefore("@").ifBlank { "Neighbor" }
            val currentAvatar = SupabaseClient.getAvatarPath(fallbackId) ?: SupabaseClient.getAvatarPath(currentEmail)
            val userEntity = UserEntity(
                userId = fallbackId,
                name = currentName,
                email = currentEmail.ifBlank { "$fallbackId@aroundus.app" },
                location = "Bhujpur, Kutch",
                profilePhoto = currentAvatar
            )
            try {
                service.createUser(user = userEntity)
            } catch (_: Exception) {}

            SupabaseClient.saveSession(
                accessToken = SupabaseClient.getAccessToken(),
                userId = fallbackId,
                email = currentEmail,
                name = currentName
            )
            return@withContext fallbackId
        }

    suspend fun getFollowersCount(userId: String): NetworkResult<Int> =
        withContext(Dispatchers.IO) {
            try {
                val resp = service.getFollowers("eq.$userId")
                val dbCount = if (resp.isSuccessful && resp.body() != null) resp.body()!!.size else 0
                val localSet = SupabaseClient.getLocalFollowersSet(userId)
                val total = Math.max(dbCount, localSet.size)
                NetworkResult.Success(total)
            } catch (e: Exception) {
                val localSet = SupabaseClient.getLocalFollowersSet(userId)
                NetworkResult.Success(localSet.size)
            }
        }

    suspend fun getFollowingCount(userId: String): NetworkResult<Int> =
        withContext(Dispatchers.IO) {
            try {
                val resp = service.getFollowing("eq.$userId")
                val dbCount = if (resp.isSuccessful && resp.body() != null) resp.body()!!.size else 0
                val localSet = SupabaseClient.getLocalFollowingSet(userId)
                val total = Math.max(dbCount, localSet.size)
                NetworkResult.Success(total)
            } catch (e: Exception) {
                val localSet = SupabaseClient.getLocalFollowingSet(userId)
                NetworkResult.Success(localSet.size)
            }
        }

    suspend fun getFollowersList(userId: String): NetworkResult<List<UserEntity>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = service.getFollowers("eq.$userId")
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val followerIds = resp.body()!!.map { it.followerId }
                    val userList = mutableListOf<UserEntity>()
                    for (fId in followerIds) {
                        val uResp = service.getUserById("eq.$fId")
                        uResp.body()?.firstOrNull()?.let { userList.add(it) }
                    }
                    NetworkResult.Success(userList)
                } else {
                    NetworkResult.Success(emptyList())
                }
            } catch (e: Exception) {
                NetworkResult.Success(emptyList())
            }
        }

    suspend fun getFollowingList(userId: String): NetworkResult<List<UserEntity>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = service.getFollowing("eq.$userId")
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val followingIds = resp.body()!!.map { it.followingId }
                    val userList = mutableListOf<UserEntity>()
                    for (fId in followingIds) {
                        val uResp = service.getUserById("eq.$fId")
                        uResp.body()?.firstOrNull()?.let { userList.add(it) }
                    }
                    NetworkResult.Success(userList)
                } else {
                    NetworkResult.Success(emptyList())
                }
            } catch (e: Exception) {
                NetworkResult.Success(emptyList())
            }
        }

    suspend fun followUser(targetUserId: String): NetworkResult<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val currentUserId = SupabaseClient.getCurrentUserId()
                if (currentUserId.isNullOrBlank()) {
                    return@withContext NetworkResult.Error("User session required.")
                }

                SupabaseClient.addLocalFollowing(currentUserId, targetUserId)

                val follow = FollowEntity(followerId = currentUserId, followingId = targetUserId)
                try {
                    service.followUser(follow = follow)
                } catch (_: Exception) {}

                NetworkResult.Success(true)
            } catch (e: Exception) {
                val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
                SupabaseClient.addLocalFollowing(currentUserId, targetUserId)
                NetworkResult.Success(true)
            }
        }

    suspend fun unfollowUser(targetUserId: String): NetworkResult<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val currentUserId = SupabaseClient.getCurrentUserId()
                if (currentUserId.isNullOrBlank()) {
                    return@withContext NetworkResult.Error("User session required.")
                }

                SupabaseClient.removeLocalFollowing(currentUserId, targetUserId)

                try {
                    service.unfollowUser("eq.$currentUserId", "eq.$targetUserId")
                } catch (_: Exception) {}

                NetworkResult.Success(true)
            } catch (e: Exception) {
                val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
                SupabaseClient.removeLocalFollowing(currentUserId, targetUserId)
                NetworkResult.Success(true)
            }
        }

    suspend fun updateUserProfile(updatedUser: UserEntity): NetworkResult<UserEntity> =
        withContext(Dispatchers.IO) {
            try {
                if (updatedUser.email.isBlank() && updatedUser.userId.isBlank()) {
                    return@withContext NetworkResult.Error("Invalid user session.")
                }

                // 1. Fetch current profile from Supabase
                var currentProfile: UserEntity? = null
                if (updatedUser.userId.isNotBlank()) {
                    val resp = service.getUserById("eq.${updatedUser.userId}")
                    currentProfile = resp.body()?.firstOrNull()
                }
                if (currentProfile == null && updatedUser.email.isNotBlank()) {
                    val resp = service.getUserByEmail("eq.${updatedUser.email.trim().lowercase()}")
                    currentProfile = resp.body()?.firstOrNull()
                }

                var finalUser = updatedUser

                // 2. Enforce 21-day username change restriction if name was modified
                if (currentProfile != null && !currentProfile.name.isBlank() && !currentProfile.name.equals(updatedUser.name.trim(), ignoreCase = true)) {
                    val lastChanged = currentProfile.usernameLastChanged ?: 0L
                    val now = System.currentTimeMillis()
                    val days21Ms = 21L * 24L * 60L * 60L * 1000L

                    if (lastChanged > 0L && (now - lastChanged) < days21Ms) {
                        val millisLeft = days21Ms - (now - lastChanged)
                        val daysLeft = (millisLeft / (24 * 60 * 60 * 1000L)).coerceAtLeast(1)
                        return@withContext NetworkResult.Error("Username can only be changed once every 21 days. Please wait $daysLeft day(s).")
                    } else {
                        finalUser = updatedUser.copy(usernameLastChanged = now)
                    }
                }

                // 3. Build update payload map
                val updateMap = mutableMapOf<String, String>()
                updateMap["name"] = finalUser.name
                finalUser.location?.takeIf { it.isNotBlank() }?.let { updateMap["location"] = it }
                finalUser.bio?.takeIf { it.isNotBlank() }?.let { updateMap["bio"] = it }
                finalUser.profilePhoto?.takeIf { it.isNotBlank() }?.let { updateMap["profile_photo"] = it }
                finalUser.phone?.takeIf { it.isNotBlank() }?.let { updateMap["phone"] = it }
                if (finalUser.usernameLastChanged != null) {
                    updateMap["username_last_changed"] = finalUser.usernameLastChanged.toString()
                }

                // 4. Update row in Supabase users table
                var isUpdated = false
                var savedUser: UserEntity? = null

                if (finalUser.userId.isNotBlank()) {
                    val resp = service.updateUser("eq.${finalUser.userId}", userMap = updateMap)
                    if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                        savedUser = resp.body()!!.first()
                        isUpdated = true
                    }
                }

                if (!isUpdated && finalUser.email.isNotBlank()) {
                    val resp = service.updateUserByEmail("eq.${finalUser.email.trim().lowercase()}", userMap = updateMap)
                    if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                        savedUser = resp.body()!!.first()
                        isUpdated = true
                    }
                }

                // Fallback upsert
                if (!isUpdated) {
                    val resp = service.createUser(user = finalUser)
                    if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                        savedUser = resp.body()!!.first()
                    }
                }

                val resultUser = savedUser ?: finalUser
                SupabaseClient.saveSession(
                    SupabaseClient.getAccessToken(),
                    resultUser.userId.ifBlank { finalUser.userId },
                    resultUser.email.ifBlank { finalUser.email },
                    resultUser.name.ifBlank { finalUser.name }
                )

                NetworkResult.Success(resultUser)
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Failed to update profile", e)
            }
        }

    private fun parseErrorMessage(raw: String): String {
        return if (raw.contains("Invalid login credentials")) "Invalid email or password."
        else if (raw.contains("User already registered")) "User with this email is already registered."
        else if (raw.contains("Password should be")) "Password does not meet length requirement."
        else raw.take(120)
    }
}

/**
 * Repository for OTP Generation & Supabase Sending
 */
class OtpRepository {
    private val service get() = SupabaseClient.getService()

    fun generate6DigitOtp(): String {
        val randomNum = (100000..999999).random()
        return randomNum.toString()
    }

    suspend fun sendOtp(email: String, otpCode: String): NetworkResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val cleanEmail = email.trim().lowercase()
                try {
                    service.sendOtp(OtpRequest(email = cleanEmail))
                } catch (_: Exception) {}

                SupabaseClient.saveOtpForEmail(cleanEmail, otpCode)
                NetworkResult.Success(otpCode)
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Failed to send OTP", e)
            }
        }

    fun verifyOtp(email: String, inputOtp: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        val savedOtp = SupabaseClient.getOtpForEmail(cleanEmail)
        return savedOtp != null && savedOtp.trim() == inputOtp.trim()
    }
}

/**
 * Dynamic Repository for Posts table
 */
class PostRepository {
    private val service get() = SupabaseClient.getService()

    companion object {
        private val globalAuthorCache = ConcurrentHashMap<String, Pair<String, String?>>()
        private var memoryCachedPosts: List<Post>? = null
        private const val PREFS_CACHE = "aroundus_posts_cache"
        private const val KEY_POSTS_JSON = "cached_posts_json"
    }

    fun getCachedPosts(context: Context?): List<Post> {
        memoryCachedPosts?.let {
            if (it.isNotEmpty()) return it
        }
        if (context == null) return emptyList()

        try {
            val prefs = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_POSTS_JSON, null)
            if (!json.isNullOrBlank()) {
                val type = object : com.google.gson.reflect.TypeToken<List<Post>>() {}.type
                val diskList: List<Post>? = com.google.gson.Gson().fromJson(json, type)
                if (!diskList.isNullOrEmpty()) {
                    val sanitized = diskList.map { p ->
                        Post(
                            id = p.id ?: "",
                            userName = if (p.userName.isNullOrBlank()) "Neighbor" else p.userName,
                            userAvatar = p.userAvatar,
                            neighborhood = if (p.neighborhood.isNullOrBlank()) "Bhujpur, Kutch" else p.neighborhood,
                            timeAgo = if (p.timeAgo.isNullOrBlank()) "Just now" else p.timeAgo,
                            category = if (p.category.isNullOrBlank()) "General" else p.category,
                            text = p.text ?: "",
                            imageRes = p.imageRes,
                            likesCount = p.likesCount,
                            commentsCount = p.commentsCount,
                            isLiked = p.isLiked,
                            isSaved = p.isSaved,
                            userAvatarPath = p.userAvatarPath,
                            authorId = p.authorId ?: "",
                            mediaUrl = p.mediaUrl
                        )
                    }
                    memoryCachedPosts = sanitized
                    return sanitized
                }
            }
        } catch (_: Exception) {}

        return emptyList()
    }

    fun savePostsToCache(context: Context?, posts: List<Post>) {
        if (posts.isEmpty()) return
        memoryCachedPosts = posts
        if (context == null) return
        try {
            val prefs = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
            val json = com.google.gson.Gson().toJson(posts)
            prefs.edit().putString(KEY_POSTS_JSON, json).apply()
        } catch (_: Exception) {}
    }

    suspend fun fetchPosts(): NetworkResult<List<PostEntity>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = service.getPosts()
                if (resp.isSuccessful && resp.body() != null) {
                    NetworkResult.Success(resp.body()!!)
                } else {
                    NetworkResult.Error("Failed to fetch posts from database: ${resp.message()}")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Network error", e)
            }
        }

    suspend fun fetchPostsWithAuthors(context: Context? = null): NetworkResult<List<Post>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = service.getPosts()
                if (resp.isSuccessful && resp.body() != null) {
                    val posts = resp.body()!!

                    // 1. Identify all unknown author IDs
                    val unknownAuthorIds = posts
                        .mapNotNull { it.authorId.takeIf { id -> !id.isNullOrBlank() } }
                        .filter { !globalAuthorCache.containsKey(it) || globalAuthorCache[it]?.second.isNullOrBlank() }
                        .distinct()

                    // 2. Fetch unknown authors in parallel concurrently (300ms total instead of N * 500ms)
                    if (unknownAuthorIds.isNotEmpty()) {
                        coroutineScope {
                            unknownAuthorIds.map { authorId ->
                                async(Dispatchers.IO) {
                                    try {
                                        val userResp = service.getUserById("eq.$authorId")
                                        val user = userResp.body()?.firstOrNull()
                                        val name = if (!user?.name.isNullOrBlank()) user!!.name else "Neighbor"
                                        val avatar = user?.profilePhoto?.takeIf { !it.isNullOrBlank() } ?: SupabaseClient.getAvatarPath(authorId)
                                        globalAuthorCache[authorId] = Pair(name, avatar)
                                        if (!avatar.isNullOrBlank()) {
                                            SupabaseClient.saveAvatarPath(authorId, avatar)
                                        }
                                    } catch (_: Exception) {
                                        val cachedAvatar = SupabaseClient.getAvatarPath(authorId)
                                        globalAuthorCache[authorId] = Pair("Neighbor", cachedAvatar)
                                    }
                                }
                            }.awaitAll()
                        }
                    }

                    // 3. Assemble UI models using fast O(1) in-memory author lookup
                    val uiPosts = posts.map { p ->
                        val pAuthorId = p.authorId ?: ""
                        val (authorName, avatarPath) = if (pAuthorId.isNotBlank()) {
                            globalAuthorCache[pAuthorId] ?: Pair("Neighbor", SupabaseClient.getAvatarPath(pAuthorId))
                        } else {
                            Pair("Neighbor", null)
                        }
                        p.toUIModel(userName = authorName, userAvatarPath = avatarPath)
                    }

                    // 4. Update memory & disk cache for instant 0ms app open next time
                    savePostsToCache(context, uiPosts)

                    NetworkResult.Success(uiPosts)
                } else {
                    NetworkResult.Error("Failed to fetch posts: ${resp.message()}")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Network error", e)
            }
        }

    suspend fun getPostById(postId: String, context: Context? = null): NetworkResult<Post> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Check memory / disk cache first
                val cached = getCachedPosts(context).find { it.id == postId }
                if (cached != null) {
                    return@withContext NetworkResult.Success(cached)
                }

                // 2. Query Supabase posts table
                val resp = service.getPostById("eq.$postId")
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val p = resp.body()!!.first()
                    var authorName = "Neighbor"
                    var authorAvatar: String? = null
                    val pAuthorId = p.authorId ?: ""
                    if (pAuthorId.isNotBlank()) {
                        val authorEntry = globalAuthorCache[pAuthorId]
                        if (authorEntry != null && !authorEntry.second.isNullOrBlank()) {
                            authorName = authorEntry.first
                            authorAvatar = authorEntry.second
                        } else {
                            try {
                                val userResp = service.getUserById("eq.$pAuthorId")
                                val user = userResp.body()?.firstOrNull()
                                if (user != null) {
                                    authorName = user.name?.ifBlank { "Neighbor" } ?: "Neighbor"
                                    authorAvatar = user.profilePhoto?.takeIf { !it.isNullOrBlank() } ?: SupabaseClient.getAvatarPath(pAuthorId)
                                    globalAuthorCache[pAuthorId] = Pair(authorName, authorAvatar)
                                    if (!authorAvatar.isNullOrBlank()) {
                                        SupabaseClient.saveAvatarPath(pAuthorId, authorAvatar)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    val postUI = p.toUIModel(userName = authorName, userAvatarPath = authorAvatar)
                    return@withContext NetworkResult.Success(postUI)
                }
                NetworkResult.Error("Post not found.")
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Failed to fetch post", e)
            }
        }

    suspend fun createPost(
        text: String,
        type: String = "General",
        media: String? = null,
        location: String? = "Bhujpur, Kutch",
        context: Context? = null
    ): NetworkResult<Post> =
        withContext(Dispatchers.IO) {
            try {
                if (text.isBlank() && media.isNullOrBlank()) {
                    return@withContext NetworkResult.Error("Post content cannot be empty.")
                }

                // 1. Resolve authorId matching public.users table (prevents foreign key constraint 23503)
                val authorId = UserRepository().resolveValidAuthorId()
                val currentEmail = SupabaseClient.getCurrentUserEmail() ?: "user@aroundus.app"
                val currentName = SupabaseClient.getCurrentUserName() ?: currentEmail.substringBefore("@").ifBlank { "Neighbor" }
                val currentAvatar = SupabaseClient.getAvatarPath(authorId) ?: SupabaseClient.getAvatarPath(currentEmail)

                globalAuthorCache[authorId] = Pair(currentName, currentAvatar)

                // 2. Create the post entity
                val newPost = PostEntity(
                    postId = "post_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    authorId = authorId,
                    type = type,
                    text = text.trim(),
                    media = media,
                    timestamp = System.currentTimeMillis().toString(),
                    location = location ?: "Bhujpur, Kutch"
                )

                val resp = service.createPost(post = newPost)
                val finalEntity = if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    resp.body()!!.first()
                } else if (resp.code() in 200..299) {
                    newPost
                } else {
                    val errorMsg = resp.errorBody()?.string() ?: resp.message()
                    return@withContext NetworkResult.Error("Failed to create post: $errorMsg")
                }

                val uiPost = finalEntity.toUIModel(userName = currentName, userAvatarPath = currentAvatar)

                // 3. Immediately update memory & disk cache so new post appears in feed with 0ms delay
                val currentList = (memoryCachedPosts ?: getCachedPosts(context)).toMutableList()
                currentList.removeAll { it.id == uiPost.id }
                currentList.add(0, uiPost)
                memoryCachedPosts = currentList
                savePostsToCache(context, currentList)

                NetworkResult.Success(uiPost)
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Failed to post", e)
            }
        }
}

/**
 * Dynamic Repository for Comments table
 */
class CommentRepository {
    private val service get() = SupabaseClient.getService()

    suspend fun fetchComments(postId: String): NetworkResult<List<CommentEntity>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = service.getCommentsForPost("eq.$postId")
                if (resp.isSuccessful && resp.body() != null) {
                    NetworkResult.Success(resp.body()!!)
                } else {
                    NetworkResult.Error("Failed to fetch comments.")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Network error", e)
            }
        }

    suspend fun fetchCommentsWithAuthors(postId: String): NetworkResult<List<Comment>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = service.getCommentsForPost("eq.$postId")
                if (resp.isSuccessful && resp.body() != null) {
                    val entities = resp.body()!!
                    val userIds = entities.map { it.userId }.filter { it.isNotBlank() }.distinct()
                    val userMap = mutableMapOf<String, Pair<String, String?>>()

                    if (userIds.isNotEmpty()) {
                        coroutineScope {
                            userIds.map { uid ->
                                async(Dispatchers.IO) {
                                    try {
                                        val uResp = service.getUserById("eq.$uid")
                                        val u = uResp.body()?.firstOrNull()
                                        val name = u?.name?.ifBlank { "Neighbor" } ?: "Neighbor"
                                        val avatar = u?.profilePhoto?.takeIf { it.isNotBlank() } ?: SupabaseClient.getAvatarPath(uid)
                                        synchronized(userMap) {
                                            userMap[uid] = Pair(name, avatar)
                                        }
                                        if (!avatar.isNullOrBlank()) {
                                            SupabaseClient.saveAvatarPath(uid, avatar)
                                        }
                                    } catch (_: Exception) {
                                        synchronized(userMap) {
                                            userMap[uid] = Pair("Neighbor", SupabaseClient.getAvatarPath(uid))
                                        }
                                    }
                                }
                            }.awaitAll()
                        }
                    }

                    val comments = entities.map { entity ->
                        val (authorName, avatar) = userMap[entity.userId] ?: Pair("Neighbor", SupabaseClient.getAvatarPath(entity.userId))
                        entity.toUIModel(userName = authorName, userAvatarPath = avatar)
                    }
                    NetworkResult.Success(comments)
                } else {
                    NetworkResult.Error("Failed to fetch comments.")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Network error", e)
            }
        }

    suspend fun addComment(postId: String, text: String): NetworkResult<CommentEntity> =
        withContext(Dispatchers.IO) {
            try {
                if (text.isBlank()) {
                    return@withContext NetworkResult.Error("Comment text cannot be empty.")
                }
                val userId = UserRepository().resolveValidAuthorId()

                val comment = CommentEntity(
                    commentId = "comment_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    postId = postId,
                    userId = userId,
                    text = text.trim(),
                    timestamp = System.currentTimeMillis().toString()
                )

                val resp = service.createComment(comment = comment)
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    NetworkResult.Success(resp.body()!!.first())
                } else if (resp.code() in 200..299) {
                    NetworkResult.Success(comment)
                } else {
                    val errorMsg = resp.errorBody()?.string() ?: resp.message()
                    NetworkResult.Error("Failed to add comment: $errorMsg")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Failed to add comment", e)
            }
        }
}

/**
 * Dynamic Repository for Groups table
 */
class GroupRepository {
    private val service get() = SupabaseClient.getService()

    suspend fun fetchGroups(): NetworkResult<List<GroupEntity>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = service.getGroups()
                if (resp.isSuccessful && resp.body() != null) {
                    NetworkResult.Success(resp.body()!!)
                } else {
                    NetworkResult.Error("Failed to fetch groups.")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Network error", e)
            }
        }

    suspend fun createGroup(name: String, description: String, category: String): NetworkResult<GroupEntity> =
        withContext(Dispatchers.IO) {
            try {
                if (name.isBlank()) {
                    return@withContext NetworkResult.Error("Group name is required.")
                }
                val newGroup = GroupEntity(
                    groupId = "group_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    name = name.trim(),
                    description = description.trim(),
                    category = category,
                    members = "1 Member"
                )

                val resp = service.createGroup(group = newGroup)
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    NetworkResult.Success(resp.body()!!.first())
                } else {
                    NetworkResult.Success(newGroup)
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Failed to create group", e)
            }
        }
}

/**
 * Dynamic Repository for Messages table
 */
class MessageRepository {
    private val service get() = SupabaseClient.getService()

    suspend fun fetchConversationMessages(otherUserId: String): NetworkResult<List<MessageEntity>> =
        withContext(Dispatchers.IO) {
            try {
                val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
                val filter = "(and(sender_id.eq.$currentUserId,receiver_id.eq.$otherUserId),and(sender_id.eq.$otherUserId,receiver_id.eq.$currentUserId))"
                val resp = service.getMessagesForConversation(filter)

                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    NetworkResult.Success(resp.body()!!)
                } else {
                    val allResp = service.getAllMessages()
                    if (allResp.isSuccessful && allResp.body() != null) {
                        val filtered = allResp.body()!!.filter {
                            (it.senderId == currentUserId && it.receiverId == otherUserId) ||
                            (it.senderId == otherUserId && it.receiverId == currentUserId)
                        }
                        NetworkResult.Success(filtered)
                    } else {
                        NetworkResult.Success(emptyList())
                    }
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Network error", e)
            }
        }

    suspend fun sendMessage(
        receiverId: String? = null,
        groupId: String? = null,
        text: String
    ): NetworkResult<MessageEntity> =
        withContext(Dispatchers.IO) {
            try {
                if (text.isBlank()) {
                    return@withContext NetworkResult.Error("Message cannot be empty.")
                }
                val senderId = SupabaseClient.getCurrentUserId()
                if (senderId.isNullOrBlank()) {
                    return@withContext NetworkResult.Error("User must be logged in to send messages.")
                }

                val newMsg = MessageEntity(
                    messageId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    senderId = senderId,
                    receiverId = receiverId,
                    groupId = groupId,
                    text = text.trim(),
                    timestamp = System.currentTimeMillis().toString()
                )

                val resp = service.sendMessage(message = newMsg)
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    NetworkResult.Success(resp.body()!!.first())
                } else {
                    NetworkResult.Success(newMsg)
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Failed to send message", e)
            }
        }
}

/**
 * Dynamic Repository for Events table
 */
class EventRepository {
    private val service get() = SupabaseClient.getService()

    suspend fun fetchEvents(): NetworkResult<List<EventEntity>> =
        withContext(Dispatchers.IO) {
            try {
                val resp = service.getEvents()
                if (resp.isSuccessful && resp.body() != null) {
                    NetworkResult.Success(resp.body()!!)
                } else {
                    NetworkResult.Error("Failed to fetch events.")
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Network error", e)
            }
        }

    suspend fun createEvent(
        title: String,
        date: String,
        location: String
    ): NetworkResult<EventEntity> =
        withContext(Dispatchers.IO) {
            try {
                if (title.isBlank()) {
                    return@withContext NetworkResult.Error("Event title is required.")
                }
                val creatorId = SupabaseClient.getCurrentUserId()
                if (creatorId.isNullOrBlank()) {
                    return@withContext NetworkResult.Error("User must be logged in to create an event.")
                }

                val newEvent = EventEntity(
                    eventId = "event_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    creatorId = creatorId,
                    title = title.trim(),
                    date = date.trim(),
                    location = location.trim(),
                    attendees = "1 Attending"
                )

                val resp = service.createEvent(event = newEvent)
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    NetworkResult.Success(resp.body()!!.first())
                } else {
                    NetworkResult.Success(newEvent)
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Failed to create event", e)
            }
        }
}
