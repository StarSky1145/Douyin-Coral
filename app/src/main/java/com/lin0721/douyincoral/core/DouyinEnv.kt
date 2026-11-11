package com.lin0721.douyincoral.core

/**
 * 伪装成桌面浏览器的环境常量。
 *
 * 登录、签名、请求三处必须使用同一套 UA 与屏幕参数：签名里带的主机环境
 * 和请求头对不上时，抖音会直接把这单请求判成爬虫。
 */
object DouyinEnv {

    /** 登录 WebView 与接口请求共用的 UA（桌面版，手机网页版没有登录入口） */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36"

    const val HOME_URL = "https://www.douyin.com/"
    const val REFERER = "https://www.douyin.com/"

    /** 浏览器环境指纹：窗口/屏幕尺寸，格式由签名脚本约定 */
    const val FINGERPRINT =
        "1600|900|1624|980|0|0|0|0|1920|1080|1920|1040|1600|900|24|24|Win32"

    /** 直播间与主页的 ttwid 注册入口，免登录可用 */
    const val TTWID_URL = "https://ttwid.bytedance.com/ttwid/union/register/"
}
