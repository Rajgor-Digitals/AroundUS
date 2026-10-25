package com.aroundus.app.network

import com.aroundus.app.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit Service Interface for Supabase PostgREST & Auth APIs
 */
@JvmSuppressWildcards
interface SupabaseService {

    // --- SUPABASE STORAGE ---
    // Storage uploads use raw OkHttp calls (binary PUT) instead of Retrofit/Gson.
    // See UserRepository.uploadProfilePhotoToStorage() for implementation.

    // --- AUTHENTICATION ---

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body request: SignUpRequest
    ): Response<AuthResponse>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(
        @Body request: LoginRequest
    ): Response<AuthResponse>

    @POST("auth/v1/otp")
    suspend fun sendOtp(
        @Body request: OtpRequest
    ): Response<Unit>

    @GET("auth/v1/user")
    suspend fun getAuthUser(
        @Header("Authorization") authHeader: String
    ): Response<AuthUser>

    @POST("auth/v1/token?grant_type=pkce")
    suspend fun exchangePkceCode(
        @Body request: Map<String, String>
    ): Response<AuthResponse>

    @POST("auth/v1/recover")
    suspend fun recoverPassword(
        @Query("redirect_to") redirectTo: String? = "aroundus://auth/callback",
        @Body request: RecoverPasswordRequest
    ): Response<Unit>

    @PUT("auth/v1/user")
    suspend fun updateAuthUser(
        @Header("Authorization") authHeader: String,
        @Body request: UpdateAuthUserRequest
    ): Response<AuthUser>

    // --- USERS TABLE ---

    @GET("rest/v1/users")
    suspend fun getUsers(
        @Query("select") select: String = "*"
    ): Response<List<UserEntity>>

    @GET("rest/v1/users")
    suspend fun getUserById(
        @Query("user_id") queryFilter: String, // e.g. "eq.123"
        @Query("select") select: String = "*"
    ): Response<List<UserEntity>>

    @GET("rest/v1/users")
    suspend fun getUserByEmail(
        @Query("email") queryFilter: String, // e.g. "eq.user@example.com"
        @Query("select") select: String = "*"
    ): Response<List<UserEntity>>

    @POST("rest/v1/users")
    suspend fun createUser(
        @Header("Prefer") prefer: String = "return=representation,resolution=merge-duplicates",
        @Body user: UserEntity
    ): Response<List<UserEntity>>

    @PATCH("rest/v1/users")
    suspend fun updateUser(
        @Query("user_id") queryFilter: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body userMap: Map<String, String>
    ): Response<List<UserEntity>>

    @PATCH("rest/v1/users")
    suspend fun updateUserByEmail(
        @Query("email") queryFilter: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body userMap: Map<String, String>
    ): Response<List<UserEntity>>

    // --- POSTS TABLE ---

    @GET("rest/v1/posts")
    suspend fun getPosts(
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.desc"
    ): Response<List<PostEntity>>

    @GET("rest/v1/posts")
    suspend fun getPostById(
        @Query("post_id") queryFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<PostEntity>>

    @POST("rest/v1/posts")
    suspend fun createPost(
        @Header("Prefer") prefer: String = "return=representation",
        @Body post: PostEntity
    ): Response<List<PostEntity>>

    @DELETE("rest/v1/posts")
    suspend fun deletePost(
        @Query("post_id") queryFilter: String
    ): Response<Unit>

    @PATCH("rest/v1/posts")
    suspend fun updatePost(
        @Query("post_id") queryFilter: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body postMap: Map<String, String>
    ): Response<List<PostEntity>>

    // --- COMMENTS TABLE ---

    @GET("rest/v1/comments")
    suspend fun getCommentsForPost(
        @Query("post_id") queryFilter: String, // e.g. "eq.post_123"
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.asc"
    ): Response<List<CommentEntity>>

    @GET("rest/v1/comments")
    suspend fun getAllComments(
        @Query("select") select: String = "comment_id,post_id"
    ): Response<List<CommentEntity>>

    @POST("rest/v1/comments")
    suspend fun createComment(
        @Header("Prefer") prefer: String = "return=representation",
        @Body comment: CommentEntity
    ): Response<List<CommentEntity>>

    @DELETE("rest/v1/comments")
    suspend fun deleteComment(
        @Query("comment_id") commentIdFilter: String // e.g. "eq.$commentId"
    ): Response<Unit>

    // --- POST LIKES TABLE ---

    @GET("rest/v1/post_likes")
    suspend fun getAllLikes(
        @Query("select") select: String = "like_id,post_id,user_id"
    ): Response<List<PostLikeEntity>>

    @GET("rest/v1/post_likes")
    suspend fun getLikesForPost(
        @Query("post_id") queryFilter: String, // e.g. "eq.$postId"
        @Query("select") select: String = "like_id,post_id,user_id"
    ): Response<List<PostLikeEntity>>

    @GET("rest/v1/post_likes")
    suspend fun getUserLikes(
        @Query("user_id") queryFilter: String, // e.g. "eq.$userId"
        @Query("select") select: String = "like_id,post_id,user_id"
    ): Response<List<PostLikeEntity>>

    @POST("rest/v1/post_likes")
    suspend fun insertLike(
        @Header("Prefer") prefer: String = "return=representation",
        @Body like: PostLikeEntity
    ): Response<List<PostLikeEntity>>

    @DELETE("rest/v1/post_likes")
    suspend fun deleteLike(
        @Query("post_id") postFilter: String, // e.g. "eq.$postId"
        @Query("user_id") userFilter: String  // e.g. "eq.$userId"
    ): Response<Unit>

    // --- GROUPS TABLE ---

    @GET("rest/v1/groups")
    suspend fun getGroups(
        @Query("select") select: String = "*"
    ): Response<List<GroupEntity>>

    @POST("rest/v1/groups")
    suspend fun createGroup(
        @Header("Prefer") prefer: String = "return=representation",
        @Body group: GroupEntity
    ): Response<List<GroupEntity>>

    @PATCH("rest/v1/groups")
    suspend fun updateGroup(
        @Query("group_id") queryFilter: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body groupMap: Map<String, String>
    ): Response<List<GroupEntity>>

    @GET("rest/v1/groups")
    suspend fun getGroupById(
        @Query("group_id") queryFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<GroupEntity>>

    // --- MESSAGES TABLE ---

    @GET("rest/v1/messages")
    suspend fun getAllMessages(
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.asc"
    ): Response<List<MessageEntity>>

    @GET("rest/v1/messages")
    suspend fun getIncomingMessages(
        @Query("receiver_id") queryFilter: String, // e.g. "eq.$currentUserId"
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.desc",
        @Query("limit") limit: Int = 20
    ): Response<List<MessageEntity>>

    @GET("rest/v1/messages")
    suspend fun getMessagesForConversation(
        @Query("or") orFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.asc"
    ): Response<List<MessageEntity>>

    @GET("rest/v1/messages")
    suspend fun getMessagesForGroup(
        @Query("group_id") queryFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.asc"
    ): Response<List<MessageEntity>>

    @POST("rest/v1/messages")
    suspend fun sendMessage(
        @Header("Prefer") prefer: String = "return=representation",
        @Body message: MessageEntity
    ): Response<List<MessageEntity>>

    // --- EVENTS TABLE ---

    @GET("rest/v1/events")
    suspend fun getEvents(
        @Query("select") select: String = "*",
        @Query("order") order: String = "date.asc"
    ): Response<List<EventEntity>>

    @POST("rest/v1/events")
    suspend fun createEvent(
        @Header("Prefer") prefer: String = "return=representation",
        @Body event: EventEntity
    ): Response<List<EventEntity>>

    @PATCH("rest/v1/events")
    suspend fun updateEvent(
        @Query("event_id") queryFilter: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body eventMap: Map<String, String>
    ): Response<List<EventEntity>>

    // --- FOLLOWS TABLE ---

    @GET("rest/v1/follows")
    suspend fun getFollowers(
        @Query("following_id") queryFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<FollowEntity>>

    @GET("rest/v1/follows")
    suspend fun getFollowing(
        @Query("follower_id") queryFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<FollowEntity>>

    @POST("rest/v1/follows")
    suspend fun followUser(
        @Header("Prefer") prefer: String = "return=representation,resolution=merge-duplicates",
        @Body follow: FollowEntity
    ): Response<List<FollowEntity>>

    @DELETE("rest/v1/follows")
    suspend fun unfollowUser(
        @Query("follower_id") followerFilter: String,
        @Query("following_id") followingFilter: String
    ): Response<Unit>

    // --- LISTINGS TABLE (BUY & SELL MARKETPLACE) ---

    @GET("rest/v1/listings")
    suspend fun getListings(
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("listing_status") statusFilter: String? = "eq.active"
    ): Response<List<ListingEntity>>

    @GET("rest/v1/listings")
    suspend fun getAllListings(
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<ListingEntity>>

    @GET("rest/v1/listings")
    suspend fun getListingById(
        @Query("listing_id") queryFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<ListingEntity>>

    @GET("rest/v1/listings")
    suspend fun getListingsBySeller(
        @Query("seller_id") queryFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<ListingEntity>>

    @POST("rest/v1/listings")
    suspend fun createListing(
        @Header("Prefer") prefer: String = "return=representation",
        @Body listing: ListingEntity
    ): Response<List<ListingEntity>>

    @PATCH("rest/v1/listings")
    suspend fun updateListing(
        @Query("listing_id") queryFilter: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body listingMap: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<ListingEntity>>

    @DELETE("rest/v1/listings")
    suspend fun deleteListing(
        @Query("listing_id") queryFilter: String
    ): Response<Unit>

    // --- SAVED LISTINGS TABLE ---

    @GET("rest/v1/saved_listings")
    suspend fun getSavedListings(
        @Query("user_id") queryFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<SavedListingEntity>>

    @POST("rest/v1/saved_listings")
    suspend fun saveListing(
        @Header("Prefer") prefer: String = "return=representation,resolution=merge-duplicates",
        @Body saved: SavedListingEntity
    ): Response<List<SavedListingEntity>>

    @DELETE("rest/v1/saved_listings")
    suspend fun deleteSavedListing(
        @Query("user_id") userFilter: String,
        @Query("listing_id") listingFilter: String
    ): Response<Unit>
}
