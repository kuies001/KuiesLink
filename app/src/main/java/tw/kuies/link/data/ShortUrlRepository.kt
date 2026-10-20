package tw.kuies.link.data

interface ShortUrlRepository {
    suspend fun shorten(url: String): ShortenResult
}

class OkHttpShortUrlRepository(
    private val api: ShortUrlApiClient,
) : ShortUrlRepository {
    override suspend fun shorten(url: String): ShortenResult = api.shorten(url)
}
