package com.ks.newsapp.data

import com.couchbase.lite.*
import com.ks.newsapp.data.api.NewsApi
import com.ks.newsapp.data.models.Article
import com.ks.newsapp.data.models.NewsResponse
import com.ks.newsapp.data.models.Source
import javax.inject.Inject

class NewsRepositoryImpl @Inject constructor(
    private val newsApi: NewsApi,
    private val database: Database
) : NewsRepository {

    override suspend fun getNews(
        feed: Feed,
        country: String?,
        category: String?,
        keywords: String?,
        domains: String?,
        from: String?,
        to: String?,
        language: String?,
        page: Int
    ): Resource<NewsResponse> {
        return try {
            val response = when(feed) {
                Feed.TOP_NEWS -> {
                    newsApi.getTopNews(
                        country = country,
                        category = category,
                        keywords = keywords,
                        page = page
                    )
                }
                Feed.ALL_NEWS -> {
                    newsApi.getNews(
                        keywords = keywords,
                        domains = domains,
                        from = from,
                        to = to,
                        language = language,
                        page = page
                    )
                }
            }

            val result = response.body()

            if (response.isSuccessful && result != null) Resource.Success(result)
            else Resource.Error(response.message())

        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error")
        }
    }

    override fun getSavedArticles(): Resource<List<Article>> {
        try {
            val query = QueryBuilder.select(
                SelectResult.property("author"),
                SelectResult.property("content"),
                SelectResult.property("description"),
                SelectResult.property("publishedAt"),
                SelectResult.property("source"),
                SelectResult.property("title"),
                SelectResult.property("url"),
                SelectResult.property("urlToImage"),
                SelectResult.expression(Meta.id)
            ).from(DataSource.database(database))

            val results = query.execute()
            val articles = mutableListOf<Article>()

            results.forEach { articles.add(documentToArticle(it)) }
            return Resource.Success(articles.toList())

        } catch (e: CouchbaseLiteException) {
            return Resource.Error(e.message ?: "An unknown error has occurred trying to load data from database")
        }
    }

    private fun documentToArticle(document: Result): Article {
        val source = Source(
            id = document.getDictionary("source")?.getString("id") ?: "",
            name = document.getDictionary("source")?.getString("name") ?: ""
        )

        return Article(
            author = document.getString("author") ?: "",
            content = document.getString("content") ?: "",
            description = document.getString("description") ?: "",
            publishedAt = document.getString("publishedAt") ?: "",
            title = document.getString("title") ?: "",
            url = document.getString("url") ?: "",
            urlToImage = document.getString("urlToImage") ?: "",
            source = source,
            id = document.getString("id")
        )
    }

    override fun getSavedArticlesCount(): Resource<Int> {
        return try {
            Resource.Success(database.count.toInt())
        } catch (e: CouchbaseLiteException) {
            Resource.Error(e.message ?: "An unknown error has occurred trying to load data from database")
        }
    }

    override fun isArticleSaved(url: String): Resource<Boolean> {
        try {
            val results = QueryBuilder.select(SelectResult.property("url"))
                .from(DataSource.database(database)).execute()
            results.forEach {
                if(url == it.getString("url")) return Resource.Success(true)
            }
        } catch (e: CouchbaseLiteException) {
            return Resource.Error(e.message ?: "An unknown error has occurred trying to load data from database")
        }
        return Resource.Success(false)
    }

    override fun saveArticle(article: Article): Resource<String> {
        val isArticleSaved = isArticleSaved(article.url).data
        if (isArticleSaved != null && isArticleSaved) return Resource.Error("Article is already saved in the database")
        try {
            val source = MutableDictionary()
                .setString("id", article.source.id)
                .setString("name", article.source.name)

            val document = MutableDocument()
                .setString("author", article.author)
                .setString("content", article.content)
                .setString("description", article.description)
                .setString("publishedAt", article.publishedAt)
                .setDictionary("source", source)
                .setString("title", article.title)
                .setString("url", article.url)
                .setString("urlToImage", article.urlToImage)

            database.save(document)
            article.id = document.id
            return Resource.Success(document.id)

        } catch (e: CouchbaseLiteException) {
            return Resource.Error(e.message ?: "An unknown error has occurred trying to save data to database")
        }
    }

    override fun removeArticle(article: Article): Resource<String> {
        if (article.id == null) return Resource.Error("No ID is associated with the article")
        return try {
            val document = database.getDocument(article.id!!)
            document?.let { database.delete(it) }
            val id = article.id
            article.id = null
            Resource.Success(id!!)
        } catch (e: CouchbaseLiteException) {
            Resource.Error(e.message ?: "An unknown error has occurred trying to delete record from database")
        }
    }
}