package superapps.minegocio.ui.categoriesscreen

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
private data class CategoryInsertPayload(
    val name: String,
    val description: String? = null,
)

class CategoriesRepository(
    private val supabase: SupabaseClient = SupabaseProvider.client,
) {
    suspend fun fetchCategories(): List<Category> = withContext(Dispatchers.IO) {
        supabase.from("categories").select(
            columns = Columns.list("id", "name", "description"),
        ) {
            order(column = "created_at", order = Order.DESCENDING)
        }.decodeList<Category>()
    }

    suspend fun createCategory(name: String, description: String?): Category = withContext(Dispatchers.IO) {
        supabase.from("categories").insert(
            value = CategoryInsertPayload(
                name = name.trim(),
                description = description?.trim().takeUnless { it.isNullOrBlank() },
            ),
        ) {
            select(columns = Columns.list("id", "name", "description"))
        }.decodeSingle<Category>()
    }
}
