# 珊瑚抖音 · Coral Douyin

第三方抖音客户端，Android + Jetpack Compose。

## 为什么不是一个普通客户端

抖音 web 的每个接口都要求 `a_bogus` 签名。0.3.0 起不再挂常驻 WebView 借页面签名，
而是自己算：

1. `assets/sign/abogus.js` 是一段纯 JS 的签名实现（SM3 + RC4 + 自定义 base64），
   由 QuickJS 在应用内执行，输入查询串 / UA / 环境指纹，输出 `a_bogus`；
2. 登录仍然交给内嵌 WebView（桌面 UA），成功后把整组 Cookie 落进 SharedPreferences；
3. OkHttp 直连 `www.douyin.com/aweme/v1/web/…`，头里带 Cookie、Referer、UA，参数里带
   `msToken` 与 `a_bogus`；返回网页而不是 JSON 时直接报「被风控要求验证」。

签名算法参考 Johnserf-Seed/f2 的公开实现，本项目用 JavaScript 重写，见 [NOTICE](NOTICE.md)。

## 视觉

版式对齐抖音：纯黑底、纯白字、玫红强调色（`#FE2C55`）。

- 顶部「直播 / 推荐 / 关注」页签，推荐页有下划线指示；
- 底部「首页 / 朋友 / ➕ / 消息 / 我」五格，➕ 为白底黑加号；
- 视频页右栏：作者头像（未关注带 ➕）、点赞、评论、收藏、分享、旋转唱片；
- 底部细进度条可点击跳转，双击点赞，单击暂停；
- 「我」页：资料头 + 关注/粉丝/获赞 + 作品/喜欢/收藏三栏网格。

## 能力

| 模块 | 状态 |
| --- | --- |
| 推荐流 / 竖向翻页 | 接 web feed，QuickJS 签名 + OkHttp 直连 |
| 点赞 / 收藏 / 关注 | 状态本地先行，失败回滚 |
| 评论列表与发布 | 支持一级评论 |
| 分享面板 | 复制链接、保存视频（CDN 多线路探测） |
| 个人主页 | 他人主页与自己的作品 / 喜欢 / 收藏 |
| 直播 / 朋友流 / 消息 / 发布 | 版式已就位，数据未接 |

## 致谢

a_bogus 签名流程参考 Apache-2.0 许可的 [Johnserf-Seed/f2](https://github.com/Johnserf-Seed/f2)，
用 JavaScript 重新实现；早期版本的「隐藏 WebView 数据泵」思路参考 MIT 许可的
[Jie009/DouYin-TV](https://github.com/Jie009/DouYin-TV)。本项目代码为独立实现。

## 免责声明

第三方客户端，与抖音官方无关联，仅供技术学习。
