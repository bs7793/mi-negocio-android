package superapps.minegocio.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
}
