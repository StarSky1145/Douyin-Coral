package com.lin0721.douyincoral.core.data

import com.lin0721.douyincoral.core.DouyinException
import com.lin0721.douyincoral.core.model.Aweme
import com.lin0721.douyincoral.core.model.AwemeParser
import com.lin0721.douyincoral.core.model.CommentPage
import com.lin0721.douyincoral.core.model.FeedPage
import com.lin0721.douyincoral.core.model.Profile
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 业务接口层。请求交给 [DouyinClient]（Cookie、签名、UA 都在那边处理），
 * 这里只负责拼参数、把 JSON 变成模型。
 */
class DouyinRepo(private val client: DouyinClient) {

    suspend fun feed(count: Int = 8, cursor: Long = 0L): FeedPage {
        val json = client.request(
            "/aweme/v1/web/tab/feed/",
            mapOf(
                "count" to count,
                "refresh_index" to 1,
                "video_type_select" to 1,
                "aweme_pc_rec_raw" to 1,
                "pc_client_type" to 1,
                "max_cursor" to cursor,
            ),
        )
        checkStatus(json)
        val page = AwemeParser.parseFeed(json)
        if (page.items.isEmpty() && json.optInt("status_code", 0) != 0) {
            throw DouyinException("接口返回：${json.optString("status_msg").ifBlank { "空数据" }}")
        }
        return page
    }

    suspend fun search(keyword: String, offset: Long = 0L, count: Int = 10): FeedPage {
        val json = client.request(
            "/aweme/v1/web/general/search/single/",
            mapOf(
                "keyword" to keyword,
                "search_channel" to "aweme_general",
                "search_source" to "normal_search",
                "query_correct_type" to 1,
                "is_filter_search" to 0,
                "offset" to offset,
                "count" to count,
            ),
        )
        checkStatus(json)
        return AwemeParser.parseSearch(json)
    }

    suspend fun comments(awemeId: String, cursor: Long = 0L, count: Int = 20): CommentPage {
        val params = mapOf(
            "aweme_id" to awemeId,
            "cursor" to cursor,
            "count" to count,
            "item_type" to 0,
        )
        var json = client.request("/aweme/v1/web/comment/list/", params)
        if (json.length() == 0 || json.optJSONArray("comments") == null) {
            // 空 body 是风控的典型回法，换 POST 再试一次
            json = client.request(
                "/aweme/v1/web/comment/list/",
                params,
                method = "POST",
                body = params.entries.joinToString("&") { "${it.key}=${it.value}" },
            )
        }
        if (json.length() == 0) throw DouyinException("评论被抖音拦了，过一会儿再试")
        checkStatus(json)
        return AwemeParser.parseComments(json)
    }

    suspend fun publishComment(awemeId: String, text: String): Boolean {
        val body = "aweme_id=$awemeId&text=${URLEncoder.encode(text, "UTF-8")}&comment_id=0&text_extra=%5B%5D"
        val json = client.request(
            "/aweme/v1/web/comment/publish/",
            emptyMap(),
            method = "POST",
            body = body,
            timeoutMs = 25_000L,
        )
        checkStatus(json)
        return json.optJSONObject("comment") != null || json.optInt("status_code", -1) == 0
    }

    suspend fun like(awemeId: String, like: Boolean) {
        val type = if (like) 1 else 0
        val json = client.request(
            "/aweme/v1/web/commit/item/digg/",
            mapOf("aweme_id" to awemeId, "type" to type),
            method = "POST",
            body = "aweme_id=$awemeId&type=$type",
        )
        checkStatus(json)
    }

    suspend fun collect(awemeId: String, collect: Boolean) {
        val action = if (collect) 1 else 0
        // 官方包里收藏走 /aweme/v1/aweme/collect/，网页端对应路径是 /aweme/v1/web/aweme/collect/；
        // 老的 /commit/item/collect/ 在新版本里已经不认了，留作兜底。
        val paths = listOf("/aweme/v1/web/aweme/collect/", "/aweme/v1/web/commit/item/collect/")
        var last: Exception? = null
        for (path in paths) {
            try {
                val json = client.request(
                    path,
                    mapOf("aweme_id" to awemeId, "action" to action),
                    method = "POST",
                    body = "aweme_id=$awemeId&action=$action",
                )
                checkStatus(json)
                return
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: DouyinException("收藏失败")
    }

    suspend fun follow(userId: String, secUid: String, follow: Boolean) {
        val type = if (follow) 1 else 0
        val json = client.request(
            "/aweme/v1/web/commit/follow/user/",
            mapOf("user_id" to userId, "sec_user_id" to secUid, "type" to type),
            method = "POST",
            body = "user_id=$userId&sec_user_id=$secUid&type=$type",
        )
        checkStatus(json)
    }

    suspend fun selfProfile(): Profile? {
        val json = runCatching {
            client.request("/aweme/v1/web/user/profile/self/", emptyMap(), timeoutMs = 15_000L)
        }.getOrNull() ?: return null
        return AwemeParser.parseProfile(json)
    }

    suspend fun userProfile(secUid: String): Profile? {
        val json = client.request(
            "/aweme/v1/web/user/profile/other/",
            mapOf("sec_user_id" to secUid, "publish_video_strategy_type" to 2),
        )
        checkStatus(json)
        return AwemeParser.parseProfile(json)
    }

    suspend fun userPosts(secUid: String, cursor: Long = 0L, count: Int = 18): FeedPage {
        val json = client.request(
            "/aweme/v1/web/aweme/post/",
            mapOf(
                "sec_user_id" to secUid,
                "max_cursor" to cursor,
                "count" to count,
                "locate_query" to false,
                "show_live_replay_strategy" to 1,
            ),
        )
        checkStatus(json)
        return AwemeParser.parseFeed(json)
    }

    suspend fun favorites(cursor: Long = 0L, count: Int = 18): FeedPage {
        val json = client.request(
            "/aweme/v1/web/aweme/collect/",
            mapOf("cursor" to cursor, "count" to count),
        )
        checkStatus(json)
        return AwemeParser.parseFeed(json)
    }

    suspend fun liked(cursor: Long = 0L, count: Int = 18): FeedPage {
        val json = client.request(
            "/aweme/v1/web/aweme/favorite/",
            mapOf("max_cursor" to cursor, "count" to count),
        )
        checkStatus(json)
        return AwemeParser.parseFeed(json)
    }

    suspend fun detail(awemeId: String): Aweme? {
        val json = client.request(
            "/aweme/v1/web/aweme/detail/",
            mapOf("aweme_id" to awemeId),
        )
        checkStatus(json)
        val obj = json.optJSONObject("aweme_detail") ?: return null
        return AwemeParser.parseAweme(obj)
    }

    private fun checkStatus(json: JSONObject) {
        val code = json.optInt("status_code", 0)
        if (code != 0) {
            val msg = json.optString("status_msg").ifBlank { "status_code=$code" }
            throw DouyinException(
                when {
                    msg.contains("login", true) || code == 8 -> "需要登录后才能操作"
                    else -> msg
                },
            )
        }
    }
}
