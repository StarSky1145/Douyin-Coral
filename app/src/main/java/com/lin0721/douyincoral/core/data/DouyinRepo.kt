package com.lin0721.douyincoral.core.data

import com.lin0721.douyincoral.core.model.Aweme
import com.lin0721.douyincoral.core.model.AwemeParser
import com.lin0721.douyincoral.core.model.CommentPage
import com.lin0721.douyincoral.core.model.FeedPage
import com.lin0721.douyincoral.core.model.Profile
import com.lin0721.douyincoral.core.web.DouyinException
import com.lin0721.douyincoral.core.web.DouyinWebEngine
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 业务接口层。所有请求都从网页上下文发出去（签名、Cookie 都由页面负责），
 * 这里只负责拼参数、把 JSON 变成模型。
 */
class DouyinRepo(private val engine: DouyinWebEngine) {

    private val webParams = linkedMapOf<String, Any?>(
        "device_platform" to "webapp",
        "aid" to "6383",
        "channel" to "channel_pc_web",
    )

    suspend fun feed(count: Int = 8, cursor: Long = 0L): FeedPage {
        val json = engine.request(
            "/aweme/v1/web/tab/feed/",
            webParams + mapOf(
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

    suspend fun comments(awemeId: String, cursor: Long = 0L, count: Int = 20): CommentPage {
        val json = engine.request(
            "/aweme/v1/web/comment/list/",
            webParams + mapOf(
                "aweme_id" to awemeId,
                "cursor" to cursor,
                "count" to count,
                "item_type" to 0,
            ),
        )
        checkStatus(json)
        return AwemeParser.parseComments(json)
    }

    suspend fun publishComment(awemeId: String, text: String): Boolean {
        val body = "aweme_id=$awemeId&text=${URLEncoder.encode(text, "UTF-8")}&comment_id=0&text_extra=%5B%5D"
        val json = engine.request(
            "/aweme/v1/web/comment/publish/",
            webParams,
            method = "POST",
            body = body,
            timeoutMs = 25_000L,
        )
        checkStatus(json)
        return json.optJSONObject("comment") != null || json.optInt("status_code", -1) == 0
    }

    suspend fun like(awemeId: String, like: Boolean) {
        val json = engine.request(
            "/aweme/v1/web/commit/item/digg/",
            webParams + mapOf("aweme_id" to awemeId, "type" to if (like) 1 else 0),
            method = "POST",
            body = "aweme_id=$awemeId&type=${if (like) 1 else 0}",
        )
        checkStatus(json)
    }

    suspend fun collect(awemeId: String, collect: Boolean) {
        val json = engine.request(
            "/aweme/v1/web/commit/item/collect/",
            webParams + mapOf("aweme_id" to awemeId, "action" to if (collect) 1 else 0),
            method = "POST",
            body = "aweme_id=$awemeId&action=${if (collect) 1 else 0}",
        )
        checkStatus(json)
    }

    suspend fun follow(userId: String, secUid: String, follow: Boolean) {
        val json = engine.request(
            "/aweme/v1/web/commit/follow/user/",
            webParams + mapOf(
                "user_id" to userId,
                "sec_user_id" to secUid,
                "type" to if (follow) 1 else 0,
            ),
            method = "POST",
            body = "user_id=$userId&sec_user_id=$secUid&type=${if (follow) 1 else 0}",
        )
        checkStatus(json)
    }

    suspend fun selfProfile(): Profile? {
        val json = runCatching {
            engine.request("/aweme/v1/web/user/profile/self/", webParams, timeoutMs = 15_000L)
        }.getOrNull() ?: return null
        return AwemeParser.parseProfile(json)
    }

    suspend fun userProfile(secUid: String): Profile? {
        val json = engine.request(
            "/aweme/v1/web/user/profile/other/",
            webParams + mapOf("sec_user_id" to secUid, "publish_video_strategy_type" to 2),
        )
        checkStatus(json)
        return AwemeParser.parseProfile(json)
    }

    suspend fun userPosts(secUid: String, cursor: Long = 0L, count: Int = 18): FeedPage {
        val json = engine.request(
            "/aweme/v1/web/aweme/post/",
            webParams + mapOf(
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
        val json = engine.request(
            "/aweme/v1/web/aweme/collect/",
            webParams + mapOf("cursor" to cursor, "count" to count),
        )
        checkStatus(json)
        return AwemeParser.parseFeed(json)
    }

    suspend fun liked(cursor: Long = 0L, count: Int = 18): FeedPage {
        val json = engine.request(
            "/aweme/v1/web/aweme/favorite/",
            webParams + mapOf("max_cursor" to cursor, "count" to count),
        )
        checkStatus(json)
        return AwemeParser.parseFeed(json)
    }

    suspend fun detail(awemeId: String): Aweme? {
        val json = engine.request(
            "/aweme/v1/web/aweme/detail/",
            webParams + mapOf("aweme_id" to awemeId),
        )
        checkStatus(json)
        val obj = json.optJSONObject("aweme_detail") ?: return null
        return AwemeParser.parseAweme(obj)
    }

    private fun checkStatus(json: JSONObject) {
        val code = json.optInt("status_code", 0)
        if (code != 0) {
            val msg = json.optString("status_msg").ifBlank { "status_code=$code" }
            throw DouyinException(when {
                msg.contains("login", true) || code == 8 -> "需要登录后才能操作"
                else -> msg
            })
        }
    }
}
