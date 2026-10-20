package com.aroundus.app.network

/**
 * Supabase Project Credentials and Endpoint Configurations.
 * Replace SUPABASE_URL and SUPABASE_ANON_KEY with your project details from Supabase Dashboard -> Project Settings -> API.
 */
object SupabaseConfig {

    // TODO: Replace with your actual Supabase URL
    var SUPABASE_URL: String = "https://joiztgzldejxrgxgitgf.supabase.co"

    // TODO: Replace with your actual Supabase Anon/Public Key
    var SUPABASE_ANON_KEY: String = "sb_publishable_t2wHlcRyrL2ZWhX6Gk9PDw_VPxFU8_1"

    /**
     * Checks whether valid Supabase credentials have been configured by the developer.
     */
    fun isConfigured(): Boolean {
        return SUPABASE_URL.startsWith("https://") &&
                !SUPABASE_URL.contains("your-project-id") &&
                SUPABASE_ANON_KEY != "YOUR_SUPABASE_ANON_KEY" &&
                SUPABASE_ANON_KEY.isNotBlank()
    }
}
