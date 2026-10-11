# 架构与设计决策

## 技术选型(2026-10 定)

| 层 | 选择 | 理由 |
|---|---|---|
| 语言/UI | Kotlin + Jetpack Compose + Material 3 | 官方全家桶;Material You 动态取色 |
| 播放 | Media3(ExoPlayer + MediaSessionService) | 后台播放/通知栏/锁屏/蓝牙/音频焦点全托管,音乐 App 的命门 |
| 网络 | OkHttp + kotlinx.serialization(手写仓库,无 Retrofit) | B 站接口动态 query 多、需逐端点换 Referer,手写更直接;依赖已在 catalog 中备用 |
| DI | 无框架(Application 单例 + ViewModelFactory) | 项目规模不需要 Hilt;`BiliSongRepository` 是 object 单例 |
| 图片 | Coil 3(coil-compose + coil-network-okhttp) | 封面加载;hdslb.com 图片实测无需 Referer 即可显示 |

## 版本矩阵(勿随意动)

```
AGP 8.13.2 / Gradle 8.14.3(wrapper)/ Kotlin 2.2.21
Compose BOM 2026.06.01(=Compose 1.11.4)/ Material3 / Media3 1.11.1
minSdk 26 / target+compile 36 / JDK 17 目标(构建用 JDK 21)
```

- ⚠ **Compose BOM ≥ 2026.08 要求 AGP ≥ 9.1**(实测报 `Dependency 'androidx.compose.ui:ui-android:1.12.1' requires Android Gradle plugin 9.1.0`)。要升 BOM 必须连 AGP 9 + Gradle 9.6 一起升,且 AGP 9 有 built-in Kotlin 等破坏性变更。
- 项目路径含中文:AGP 默认拒绝构建,靠 `gradle.properties` 里 `android.overridePathCheck=true` 放行。目前构建/资源/签名均正常;若未来出现诡异的编码问题,第一嫌疑是路径,先挪到纯英文目录再排查别的。

## 播放链路时序(核心设计)

```
用户点歌
  └─ MainViewModel.playQueue(songs, index)
      └─ Song.toMediaItem(): uri = "bili://<bvid>"(fake 歌自带 https 直链则原样)
          └─ PlayerController.playQueue() → MediaController.setMediaItems/prepare/play
              └─ MusicService 内 ExoPlayer 开始加载
                  └─ ProgressiveMediaSource → ResolvingDataSource(BiliDataSources)
                      ├─ resolveDataSpec: bili:// → BiliSongRepository.audioUrlBlocking(bvid)
                      │     ├─ (缺 buvid 则 spi 先取)
                      │     ├─ view?bvid= → 真实 cid(⚠ 不信榜单 cid)
                      │     └─ playurl?fnval=16 → dash.audio 按 bandwidth 取最高 → baseUrl
                      └─ 换成 https 直链后交给 DefaultHttpDataSource(带 Referer+UA)拉流
```

**为什么用 bili:// 自定义 scheme 而不是入队前预取直链:**
1. B 站直链 ~3h 过期,预取的队列过夜必 403;resolver 在每次打开数据源时现取,天然自愈。
2. 整队 30 首预取 = 30×2 次 HTTP 串行等待(旧实现,实测点歌后要等数秒);现在点歌只解析当前首,秒开。
3. ExoPlayer 加载失败自动重试 → 再次回调 resolver → 中途过期也能救回。

## 状态管理

- 单一 `StateFlow<MainUiState>`(query/search/ranking/player 四分区),`PlayerController.playerState()` 的 callbackFlow 合并进来。
- 播放器事件(Media3 `Player.Listener.onEvents`)推全量快照,UI 无脑渲染。
- 进度条:PlayerScreen 本地 ticker(500ms 采样 `controller.currentPosition`),避免每秒全树重组;`LaunchedEffect` 以 `currentMediaId + isPlaying` 为 key。

## PlayerController 线程模型(ANR 教训)

- `MediaController.Builder.buildAsync().get()` **绝不能在 main 调**(首次绑定服务要等 MusicService 冷启动,主线程 `.get()` 直接 ANR,实测踩过)。
- 规则:已连接 → 同步执行;未连接 → 投递到内部 `scope(Dispatchers.Main.immediate)` 异步绑定后执行。UI 点击不等待。
- `playerState()` 流在 ViewModel 的 viewModelScope 里 collect,首帧会自动触发连接。

## 前台服务

- `MusicService : MediaSessionService`,manifest `foregroundServiceType="mediaPlayback"` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限;通知完全由 Media3 生成(MEDIA_CONTROL 渠道),不要手写 MediaStyle 通知。
- `onTaskRemoved`:非播放态且队列为空才 `stopSelf()`。
- Android 13+ 通知权限在 MainActivity 启动时请求;拒绝不影响播放,只影响通知可见。

## 已知边界

- 匿名 192K 封顶;登录/大会员音质未做。
- 搜索只覆盖视频源(MV/现场/投稿);音乐区歌曲(sid)匿名无搜索接口。
- 多 P 视频一律取第一 P。
- 无播放历史/收藏/本地缓存(见 ROADMAP)。
