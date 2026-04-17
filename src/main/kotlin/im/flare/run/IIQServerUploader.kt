package im.flare.run

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object IIQServerUploader {

    private val gson = Gson()
    private val DATE_FORMAT = SimpleDateFormat("M/d/yy, h:mm a", Locale.US)

    data class ExistingRule(val id: String, val name: String, val createdMs: Long?, val modifiedMs: Long?)
    data class UploadResult(val endpoint: String, val response: Response)

    fun fetchRuleXml(config: IIQServerConnection, id: String): String? {
        val client = buildClient(config.ignoreSslCertificate)
        val baseUrl = config.url.trimEnd('/')

        val request = Request.Builder()
            .url("$baseUrl/rest/debug/Rule/$id")
            .addHeader("Authorization", Credentials.basic(config.username, config.password))
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            val result = gson.fromJson(bodyStr, RuleDetailResult::class.java)
            return result.objects?.firstOrNull()?.xml
        }
    }

    fun findExisting(config: IIQServerConnection, name: String): ExistingRule? {
        val client = buildClient(config.ignoreSslCertificate)
        val baseUrl = config.url.trimEnd('/')
        return searchRule(client, config, baseUrl, name)
    }

    fun listRules(config: IIQServerConnection, query: String? = null): List<ExistingRule> {
        val client = buildClient(config.ignoreSslCertificate)
        val baseUrl = config.url.trimEnd('/')

        val urlBuilder = "$baseUrl/rest/debug/Rule".toHttpUrl().newBuilder()
        if (!query.isNullOrBlank()) {
            urlBuilder.addQueryParameter("query", query)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("Authorization", Credentials.basic(config.username, config.password))
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val bodyStr = response.body?.string() ?: return emptyList()
            val result = gson.fromJson(bodyStr, SearchResult::class.java)
            return result.objects?.mapNotNull { obj ->
                val id = obj.id ?: return@mapNotNull null
                val name = obj.name ?: return@mapNotNull null
                ExistingRule(
                    id = id,
                    name = name,
                    createdMs = obj.created?.let { parseDate(it) },
                    modifiedMs = obj.modified?.let { parseDate(it) }
                )
            } ?: emptyList()
        }
    }

    fun upload(config: IIQServerConnection, xml: String, existingId: String?): UploadResult {
        val client = buildClient(config.ignoreSslCertificate)
        val baseUrl = config.url.trimEnd('/')

        val endpoint = if (existingId != null) "$baseUrl/rest/debug/Rule/$existingId"
                       else "$baseUrl/rest/debug/Rule"

        val body = FormBody.Builder()
            .add("xml", xml)
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", Credentials.basic(config.username, config.password))
            .post(body)
            .build()

        return UploadResult(endpoint, client.newCall(request).execute())
    }

    private fun searchRule(
        client: OkHttpClient,
        config: IIQServerConnection,
        baseUrl: String,
        name: String
    ): ExistingRule? {
        val url = "$baseUrl/rest/debug/Rule".toHttpUrl().newBuilder()
            .addQueryParameter("query", name)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", Credentials.basic(config.username, config.password))
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyStr = response.body?.string() ?: return null
            val result = gson.fromJson(bodyStr, SearchResult::class.java)
            val match = result.objects?.find { it.name == name } ?: return null
            val id = match.id ?: return null
            return ExistingRule(
                id = id,
                name = match.name ?: name,
                createdMs = match.created?.let { parseDate(it) },
                modifiedMs = match.modified?.let { parseDate(it) }
            )
        }
    }

    private fun parseDate(dateStr: String): Long? = runCatching {
        DATE_FORMAT.parse(dateStr)?.time
    }.getOrNull()

    private fun buildClient(ignoreSsl: Boolean): OkHttpClient {
        if (!ignoreSsl) return OkHttpClient()

        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAll), SecureRandom())
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private data class SearchResult(
        @SerializedName("objects") val objects: List<RuleObject>?
    )

    private data class RuleObject(
        @SerializedName("id") val id: String?,
        @SerializedName("name") val name: String?,
        @SerializedName("created") val created: String?,
        @SerializedName("modified") val modified: String?
    )

    private data class RuleDetailResult(
        @SerializedName("objects") val objects: List<RuleDetail>?
    )

    private data class RuleDetail(
        @SerializedName("xml") val xml: String?
    )
}
