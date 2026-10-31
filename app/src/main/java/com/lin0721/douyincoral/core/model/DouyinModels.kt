package com.lin0721.douyincoral.core.model

import org.json.JSONArray
import org.json.JSONObject

data class Author(
    val uid: String,
    val secUid: String,
    val nickname: String,
    val avatar: String,
    val signature: String = "",
    val followerCount: Long = 0,
    val followingCount: Long = 0,
    val awemeCount: Long = 0,
)

data class VideoInfo(
    val playUrl: String,
    val cover: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
) {
    val aspectRatio: Float
        get() = if (height > 0 && width > 0) width.toFloat() / height.toFloat() else 9f / 16f
}

data class Aweme(
    val id: String,
    val desc: String,
    val author: Author,
    val video: VideoInfo,
    val images: List<String>,
    val diggCount: Long,
    val commentCount: Long,
    val collectCount: Long,
    val shareCount: Long,
    val musicTitle: String,
    val musicAuthor: String,
    val liked: Boolean,
    val collected: Boolean,
    val createTime: Long,
) {
    val isImages: Boolean get() = images.isNotEmpty()
    val shareText: String get() = "https://www.douyin.com/video/$id"
}

data class FeedPage(val items: List<Aweme>, val hasMore: Boolean, val cursor: Long)

data class Comment(
    val cid: String,
    val text: String,
    val nickname: String,
    val avatar: String,
    val diggCount: Long,
    val createTime: Long,
    val replyCount: Long,
)

data class CommentPage(val items: List<Comment>, val hasMore: Boolean, val cursor: Long)

data class Profile(
    val uid: String,
    val secUid: String,
    val nickname: String,
    val avatar: String,
    val signature: String,
    val followerCount: Long,
    val followingCount: Long,
    val awemeCount: Long,
    val likedCount: Long,
    val isSelf: Boolean,
)

internal fun JSONObject.str(key: String): String = optString(key, "")

internal fun JSONObject.long(key: String): Long = optLong(key, 0L)

internal fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

internal fun JSONObject.array(key: String): JSONArray? = optJSONArray(key)

internal fun JSONArray.strings(): List<String> = buildList {
    for (i in 0 until length()) {
        val s = optString(i, "")
        if (s.isNotEmpty()) add(s)
    }
}

internal fun JSONObject.firstUrl(key: String): String {
    val list = optJSONObject(key)?.optJSONArray("url_list") ?: return ""
    for (i in 0 until list.length()) {
        val s = list.optString(i, "")
        if (s.isNotEmpty()) return s
    }
    return ""
}

object AwemeParser {

    fun parseAuthor(json: JSONObject?): Author {
        if (json == null) return Author("", "", "", "")
        return Author(
            uid = json.str("uid"),
            secUid = json.str("sec_uid"),
            nickname = json.str("nickname"),
            avatar = json.firstUrl("avatar_thumb").ifEmpty { json.firstUrl("avatar_larger") },
            signature = json.str("signature"),
            followerCount = json.long("follower_count"),
            followingCount = json.long("following_count"),
            awemeCount = json.long("aweme_count"),
        )
    }

    fun parseProfile(json: JSONObject?): Profile? {
        val user = json?.obj("user") ?: return null
        return Profile(
            uid = user.str("uid"),
            secUid = user.str("sec_uid"),
            nickname = user.str("nickname"),
            avatar = user.firstUrl("avatar_larger").ifEmpty { user.firstUrl("avatar_thumb") },
            signature = user.str("signature"),
            followerCount = user.long("follower_count"),
            followingCount = user.long("following_count"),
            awemeCount = user.long("aweme_count"),
            likedCount = user.long("total_favorited"),
            isSelf = user.optBoolean("is_self", false),
        )
    }

    fun parseAweme(json: JSONObject): Aweme? {
        val id = json.str("aweme_id")
        if (id.isEmpty()) return null
        val video = json.obj("video")
        val playList = video?.obj("play_addr")?.optJSONArray("url_list")
        var playUrl = ""
        if (playList != null) {
            for (i in 0 until playList.length()) {
                val u = playList.optString(i, "")
                if (u.isNotEmpty()) {
                    playUrl = u
                    break
                }
            }
        }
        if (playUrl.isEmpty()) {
            val rates = video?.optJSONArray("bit_rate")
            if (rates != null && rates.length() > 0) {
                playUrl = rates.optJSONObject(0)?.firstUrl("play_addr") ?: ""
            }
        }
        val images = json.optJSONArray("images")?.let { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    val url = arr.optJSONObject(i)?.firstUrl("url_list") ?: ""
                    if (url.isNotEmpty()) add(url)
                }
            }
        } ?: emptyList()

        val stats = json.obj("statistics")
        val music = json.obj("music")
        return Aweme(
            id = id,
            desc = json.str("desc"),
            author = parseAuthor(json.obj("author")),
            video = VideoInfo(
                playUrl = playUrl.replace("playwm", "play"),
                cover = video?.firstUrl("cover") ?: json.firstUrl("video_cover"),
                durationMs = video?.long("duration") ?: 0L,
                width = video?.obj("play_addr")?.optInt("width", 0) ?: 0,
                height = video?.obj("play_addr")?.optInt("height", 0) ?: 0,
            ),
            images = images,
            diggCount = stats?.long("digg_count") ?: 0L,
            commentCount = stats?.long("comment_count") ?: 0L,
            collectCount = stats?.long("collect_count") ?: 0L,
            shareCount = stats?.long("share_count") ?: 0L,
            musicTitle = music?.str("title").orEmpty(),
            musicAuthor = music?.str("author").orEmpty(),
            liked = json.optBoolean("user_digged", false) || json.optBoolean("is_digg", false),
            collected = json.optBoolean("is_collect", false),
            createTime = json.long("create_time"),
        )
    }

    fun parseFeed(json: JSONObject): FeedPage {
        val list = json.optJSONArray("aweme_list")
        val items = buildList {
            if (list != null) {
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    parseAweme(item)?.let { add(it) }
                }
            }
        }
        return FeedPage(
            items = items,
            hasMore = json.optInt("has_more", 0) == 1,
            cursor = json.long("max_cursor").takeIf { it > 0 } ?: json.long("cursor"),
        )
    }

    fun parseComments(json: JSONObject): CommentPage {
        val list = json.optJSONArray("comments")
        val items = buildList {
            if (list != null) {
                for (i in 0 until list.length()) {
                    val c = list.optJSONObject(i) ?: continue
                    add(
                        Comment(
                            cid = c.str("cid"),
                            text = c.str("text"),
                            nickname = c.obj("user")?.str("nickname").orEmpty(),
                            avatar = c.obj("user")?.firstUrl("avatar_thumb").orEmpty(),
                            diggCount = c.long("digg_count"),
                            createTime = c.long("create_time"),
                            replyCount = c.long("reply_comment_total"),
                        ),
                    )
                }
            }
        }
        return CommentPage(
            items = items,
            hasMore = json.optInt("has_more", 0) == 1,
            cursor = json.long("cursor"),
        )
    }
}
