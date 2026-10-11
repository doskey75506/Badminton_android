# 羽毛球俱乐部

完全离线的羽毛球俱乐部活动 App（Android / Kotlin / Jetpack Compose）。不需要网络、不注册账号，所有数据保存在手机本地。支持多俱乐部、多场地、中英文界面。

主要功能：

- **成员管理**：录入姓名、性别、初始积分
- **今晚活动**：一键签到，自动为每个空闲场地生成均衡对阵（单打 1v1 / 双打 2v2）
- **进行中比赛**：中途换人、录入比分（自动更新积分）、作废误生成的比赛
- **四套独立积分榜**：开放单打 / 女子单打 / 开放双打 / 女子双打
- **历史查询**：按时间范围、按球员筛选，查看比分与积分变化

## 安装

要求 Android 8.0（API 26）及以上。

1. 把 `app-release.apk` 传到手机（微信 / QQ / 数据线 / 网盘均可）
2. 在手机上点击该 APK 安装
3. 首次安装需在系统弹窗中允许"安装未知应用"（不同品牌路径略有差异，一般在 设置 → 安全 → 安装未知应用）

应用不上架应用商店，也不会自动更新；有新版本时重复上述步骤覆盖安装即可，数据不受影响。

## 使用

首次启动先创建俱乐部，之后所有功能分四个页面（底部/侧边菜单切换）：

### 今晚

1. 顶部可切换日期、设置场地数量（1–12）
2. 勾选今晚到场的人。中途有人来 / 走随时改勾选，不影响已生成的比赛
3. 点击「为 N 个空闲场地生成对阵」，系统按 **休息时间均衡 + 双方积分均衡** 自动配对，比赛自动开始
4. 比赛结束后输入双方比分（0–30，不能平局）提交，积分立即更新
5. 「换人」可把场上任一队员替换为场下空闲队员；「作废」用于清除误生成、未录比分的比赛

积分均衡的依据是各场地所选"比赛类型"（默认开放双打）；生成时未上场且已签到的人才会入池。

### 成员

录入新成员（姓名必填且唯一，初始积分默认 1000，女生成员请设置性别为"女"——女子场次只从女性成员中匹配）。列表中可查看 / 删除成员。

### 积分榜

切换四套积分榜查看排名。每人每套积分独立计算，互不影响。

### 历史

按"全部 / 今天 / 7 天 / 30 天 / 自定义"筛选比赛记录，可只看某位球员，显示每场的比分、胜负、时长和积分变化。

## 语言

菜单中可随时切换 中文 / English。

## 数据

- 数据保存在手机的应用私有目录（`/data/data/com.badminton.club/files/data/`），JSON 格式：`clubs.json` + 每俱乐部的 `members.json` / `attendance.json` / `settings.json` / `matches.json`
- **无网络权限**，数据不会上传，卸载应用会一并删除。重要数据请及时另存（可从应用私有目录导出，需 root 或开发调试方式）
- 更换手机需手动迁移，应用内暂无备份 / 恢复功能

## 从源码构建

需要 JDK 17 和 Android SDK（platform 35 + build-tools 35.0.0）。本仓库已带 Gradle wrapper：

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/jdk-17.0.20.1+1/Contents/Home   # 按本机 JDK 路径调整
export ANDROID_HOME=~/android-sdk                                                    # 按本机 SDK 路径调整

./gradlew :app:assembleRelease   # 产物：app/build/outputs/apk/release/app-release.apk
./gradlew :core:test             # 运行核心逻辑单元测试
```

- `core/`：纯 Kotlin 逻辑模块（配对、积分、存储），不依赖 Android
- `app/`：Jetpack Compose 界面
- Release APK 使用 `keystore.properties` + `badminton-keystore.jks` 签名（均不入库）。**请妥善备份 keystore**：丢失后无法对已安装用户做覆盖升级，只能卸载重装

## 与 Python 版的关系

本 App 移植自原 Streamlit 网页版（见旧仓库 `github.com/doskey75506/Badminton` 的 `main` 分支），功能完整对等。本仓库只包含 Android 代码。
