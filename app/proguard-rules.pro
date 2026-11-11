# QuickJS 用 JNI 调本地方法，类名和方法名都不能被混淆
-keep class app.cash.quickjs.** { *; }

# 登录 WebView 的 JS 接口
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
