本项目在架构上参考了 Jie009/DouYin-TV（MIT License, https://github.com/Jie009/DouYin-TV）
的「隐藏 WebView 数据泵」思路，代码为独立实现；0.3.0 起数据泵已移除，仅保留内嵌网页登录。

a_bogus 签名的算法流程参考 Johnserf-Seed/f2（Apache License 2.0,
https://github.com/Johnserf-Seed/f2）中 f2/utils/abogus.py 的公开实现，
本项目用 JavaScript 重新实现（app/src/main/assets/sign/abogus.js），未搬运原始代码。
