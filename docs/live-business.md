# 服务器接入与联调

## 环境

- 正常安装的 `debug` 和 `release` 使用 `https://api.semi-overt.com/api/v1`；`preview` 使用离线演示。编辑器、开屏与背景资源包含在 APK 内。
- 注册验证使用生产网站同源的公开 Turnstile site key；SMTP、Turnstile secret 和图片存储凭据只由服务器持有。
- Android 使用现有 Rust 会话接口。访问令牌留在内存；持久化刷新 Cookie 由 Android Keystore 加密，存于不参与备份的目录。应用内串行刷新同一设备会话。
- 先部署后端增量再开放 Android 写作。草稿详情仅向作者返回 `version`；旧版后端仍可保存新草稿，但 Android 不会向它送审。增量同时提供拒审列表及清空简介，无数据库迁移；原有 Web 调用保持兼容。

## 业务规则

| 页面 | 行为 |
| --- | --- |
| 首页、分类、搜索 | 游客和用户共用生产内容；分类和搜索支持接着加载。网络错误可重试，空结果与加载失败分开。 |
| 阅读、作者 | 使用服务端 ID 进入详情和作者页，支持 Markdown、旧 HTML 和真实媒体；只读作者页使用公开 API 响应。 |
| 账号、资料 | 验码和登录以服务器响应为准。登录后可更换头像、封面并更新昵称和简介；封面取景位置只保存到当前设备。 |
| 编辑、审核 | 草稿先落本机，再在停输入一段时间后同步。提交前保存同一版本。遇到跨端冲突，可以读取云端、另存新稿或导出。待审时可查看、撤回；退回后可修改再送审；拒审时可查看结果及原因。管理员仍在 Web 审核。 |
| 创作记录 | 月历使用服务器的每日字数和日期。可选的本机 Diff 只记录该账号在当前设备成功同步的正文变化，默认关闭。清除本机快照不影响云端草稿或日历。 |

旧 `preview` 演示身份、试用资料和未登录草稿不会被自动上传或关联到真实账号。要保留试用草稿，可以从 `preview` 导出 Markdown。

## 测试

`LiveBusinessIntegrationTest` 需要隔离的 Rust API 和 Mailpit，使用独立 SQLite、上传目录和 SMTP。服务端启用真实邮箱验证码，测试邮箱由本地 Mailpit 接收；只对隔离进程关闭 Turnstile。测试后端准备一个不会处理生产数据的 `fixture_admin` 审核账号，测试密码为 `fixture-reviewer-2026`。未传入本地地址时，联调用例会跳过。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
adb reverse tcp:65027 tcp:65027
adb reverse tcp:65029 tcp:65029
adb shell am instrument -w -e class com.semiovert.app.LiveBusinessIntegrationTest `
  -e fixtureEndpoint http://127.0.0.1:65027/api/v1 `
  -e fixtureMail http://127.0.0.1:65029 `
  com.semiovert.app.test/com.semiovert.app.BusinessTestRunner
```

在专用模拟器安装 Debug APK 和测试 APK。`adb reverse` 将模拟器的两个回环端口连接到主机的隔离服务。同一邮箱的注册和重置验证码共享冷却时间，联调会按服务器的 `Retry-After` 等待。正式签名、分发渠道和正式包真机验收需先确认。
