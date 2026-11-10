package com.sangam.messenger.data

import com.sangam.messenger.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import kotlin.time.Duration.Companion.seconds

object Supabase {

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /**
     * Supabase publishable keys may only travel in the `apikey` header. The
     * supabase-kt 2.6.1 auth module also puts the key into
     * `Authorization: Bearer ...` while nobody is signed in, which the
     * backend rejects with 401. This plugin removes that header when (and
     * only when) it carries the publishable key. Real login session
     * tokens (JWTs) are left untouched.
     */
    private val stripApiKeyAuthorization = createClientPlugin(
        name = "StripApiKeyAuthorization"
    ) {
        onRequest { request, _ ->
            if (request.headers[HttpHeaders.Authorization] ==
                "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
            ) {
                request.headers.remove(HttpHeaders.Authorization)
            }
        }
    }

    @OptIn(SupabaseInternal::class)
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            // Photo uploads on slower connections need more than the
            // 10-second default, or they fail with "HTTP request ... failed".
            requestTimeout = 60.seconds
            install(Auth)
            install(Postgrest)
            install(Storage)
            httpConfig {
                install(stripApiKeyAuthorization)
            }
        }
    }
}
