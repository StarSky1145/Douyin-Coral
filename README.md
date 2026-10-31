# 珊瑚抖音 · Coral Douyin

第三方抖音客户端，Android + Jetpack Compose。

## 为什么不是一个普通客户端

抖音 web 的每个接口都要求 `a_bogus` 签名，而签名函数就活在他们自己的页面里。这个项目不逆向算法，
而是挂一个不可见的 WebView 常驻 `www.douyin.com`：

1. 真实 Chromium 自己过风控、自己维护 `ttwid` / `msToken`；
2. 把页面里的 `byted_acrawler.frontierSign` 借出来，替我们的请求签名；
3. 在页面上下文里 `fetch`，Cookie 与同源策略天然满足。

原生层只负责拼参数、解析 JSON、渲染 UI、播放与下载。

## 视觉

与音乐播放器 Melodia coral 同一套语言：暖白文字、深炭底色、单一珊瑚橙强调色。

## 致谢

架构思路参考了 MIT 许可的 [Jie009/DouYin-TV](https://github.com/Jie009/DouYin-TV)（隐藏 WebView 数据泵 + 原生播放）。
本项目代码为独立实现。

## 免责声明

第三方客户端，与抖音官方无关联，仅供技术学习。
