package superapps.minegocio.ui.categoriesscreen

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import superapps.minegocio.BuildConfig

internal object SupabaseProvider {
    val supabaseUrl: String
        get() = BuildConfig.SUPABASE_URL.trimEnd('/')

    val anonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY

    val functionsUrl: String
        get() = BuildConfig.SUPABASE_FUNCTIONS_URL.trimEnd('/')

    val restUrl: String
        get() = "$supabaseUrl/rest/v1"

    val client: SupabaseClient by lazy {
        assertConfigured()
        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = anonKey,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    fun assertConfigured() {
        require(supabaseUrl.isNotBlank() && anonKey.isNotBlank()) {
            "Set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties"
        }
    }
}
