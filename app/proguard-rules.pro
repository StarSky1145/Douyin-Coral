# WebView 的 JS 接口必须保留，否则混淆后 bridge 失效
-keepclassmembers class com.lin0721.douyincoral.core.web.** {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.lin0721.douyincoral.core.web.Bridge { *; }
