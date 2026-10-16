# AvSong — B 站音乐播放器(Android)

基于 B 站公开 API 的安卓音乐播放器:Kotlin + Jetpack Compose + Material 3 + Media3(ExoPlayer)。纯客户端、匿名使用、无后端。

**本文档只记现行规则与硬坑**,历史决策过程在 git log,API 配方在 docs/bilibili-api.md。会话收尾追加规范:只写与下次改代码有关的约束,写现在时不写演变史;超过三行的新知识进 docs/。

## 快速命令

```bash
./gradlew.bat assembleDebug          # 构建 APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew.bat installDebug           # 构建并安装到当前唯一设备
```

- 多设备时先 `adb devices`,用 `-s <serial>` 或设置 `ANDROID_SERIAL` 指定目标。
- 本机 Android SDK:`D:\sdk`(platform-tools、build-tools 35/36、platforms 34/35/36)。
- JDK 21(Temurin)已装,Gradle 8.14.3(wrapper 自带)。

### 测试设备

| 设备 | adb 地址 | 系统 |
|---|---|---|
| MuMu 12 模拟器 | `192.168.31.41:5555`(`adb connect` 后可用) | Android 12 / API 32 / x86_64 |
| AVD Medium_Phone | `emulator-5554` | Android 16 / API 36 |

- MuMu 端口可用 `"D:\MuMuPlayer\nx_main\MuMuManager.exe" info -v all` 查询(输出 JSON 里的 `adb_port`/`adb_host_ip`)。
- UI 自动化:`adb shell input tap/swipe/text`;状态验证:`adb shell dumpsys media_session | grep -m1 state=PlaybackState`(state=3 即 PLAYING)。
- 中文输入用 `adb shell input text` 传 URL 编码会失败,**改用英文关键词测试搜索**(如 `linkin`)。

### 测试机坑(实测踩过)

- **MuMu 上 `svc wifi disable` 会连带断掉 adb 通道**(设备 offline)。恢复:`"D:\MuMuPlayer\nx_main\MuMuManager.exe" sh -v 0 -c "svc wifi enable"`(宿主直连,不依赖 adb)。
- **封面旋转/弹幕动画让 uiautomator 拿不到 idle**,且静默留下旧 ui.xml(读起来像「部署回退了」)。先 `rm /sdcard/ui.xml` 再 dump 并确认成功输出,或先发 KEYCODE_MEDIA_PAUSE(127)停掉动画。
- MuMu 是 **360dp 窄屏(wm density 640 = 1dp=4px)**,adb 手势坐标按 4x 换算;`screencap` 滞后输入一帧,像素级验证用连拍。
- 并发会话(BiliTune)同机抢前台时,`dumpsys media_session` 要**按包名过滤**段落,否则抓到别人的会话。
- 长视频(如 65h 课程)的「下载」不要拿来当测试项,流式下不完。

## 架构速览

```
app/src/main/java/dev/avsong/
├── AvSongApp.kt            # Application:PlayerController 实例 + 各仓库单例
├── MainActivity.kt         # 单 Activity,请求通知权限,edge-to-edge
├── domain/                 # Song 模型、SongRepository 接口、分页
├── data/
│   ├── BiliConstants.kt    # UA / Referer / host 常量
│   ├── BiliHttp.kt         # OkHttp 单例:自动注入 UA/Referer/buvid+登录 Cookie
│   ├── BiliSongRepository.kt  # ★ 核心数据源(object 单例):搜索/榜单/联想/取流/直达
│   ├── CollectionRepository.kt # UP 合集解析/翻页/追合集合并
│   ├── FavoriteRepository.kt  # 收藏夹=歌单(读/写,写带 csrf=bili_jct;aid/check 双缓存)
│   ├── ReplyRepository.kt  # 热评(播放页弹幕数据源)
│   ├── LocalPlaylistStore.kt # 本地自建歌单(DataStore JSON,纯本地不走 B 站)
│   ├── PlayHistoryStore.kt # 本地播放历史(DataStore)
│   ├── SearchHistoryStore.kt # 本地搜索历史(DataStore,去重/上限20)
│   ├── DownloadedStore.kt  # ★ 已下载元数据(DataStore JSON,自愈剪枝+孤儿扫描)
│   ├── DownloadManager.kt  # 下载落盘(bvid 前缀文件名,流式写)
│   ├── FakeSongRepository.kt  # 直链测试音频(回退验证 UI 链路)
│   ├── auth/               # AuthStore / QrLogin / CookieRefresh(§15)
│   ├── lyric/LyricRepository.kt # ★ 歌词多源:B站AI字幕→bgm→网易云+时长校验
│   └── remote/             # DTO(BiliDtos 含 flac/dolby)+ WbiSign(备用)
├── player/
│   ├── MusicService.kt     # MediaSessionService + 现场持久化 + 交叉淡入淡出
│   ├── PlayerController.kt # UI↔服务桥:withController {} + PlayerState 流 + 队列操作
│   ├── PlaybackPersistenceStore.kt # 队列/进度/模式快照(⚠ PersistedItem 带 uri)
│   ├── BiliDataSources.kt  # ★ bili:// → ResolvingDataSource:现取直链+CDN换源+file://分发
│   └── BiliHttpDataSourceFactory.kt  # 带 Referer 的 DefaultHttpDataSource
└── ui/
    ├── AvSongApp.kt        # Scaffold + NavHost + 迷你条/底栏熔接 + 播放面板 + 各弹窗接线
    ├── AppBottomBar.kt     # 底部导航:首页/搜索/歌单/我的(悬浮圆角胶囊,无涟漪)
    ├── MainViewModel.kt    # 全局 UI 状态 + 播放控制 + 下载/队列提示流
    ├── ViewModelFactory.kt # remember*ViewModel 系列(Application 依赖注入)
    ├── MiniPlayerBar.kt    # 迷你条(与底栏熔接;播放键环形进度;封面旋转)
    ├── common/             # ★ 共享组件:SongList/SongRow/CoverArt/SkeletonList/ThemedNotice/RefreshIndicator
    ├── theme/              # Theme.kt(双主题 seed 派生)/ ThemeStore / Type.kt(Noto Sans SC)
    ├── home/  search/  login/
    ├── library/            # 歌单中心 + 收藏夹/合集/本地歌单详情(共用 PlaylistDetailContent)+ 收藏弹窗
    ├── history/PlayHistoryScreen.kt # 播放历史独立页
    ├── downloads/          # 已下载离线曲库
    ├── settings/           # PlaybackSettingsScreen / SettingsStore / AboutScreen
    ├── queue/QueueScreen.kt # QueueContent(仅队列抽屉用,无独立队列页)
    └── player/             # 播放页面板 + LyricView + ReplyDanmaku(热评弹幕)
```

数据流:`UI → MainViewModel → PlayerController → MediaController ↔ MusicService(ExoPlayer)`;
播放 URI 是 `bili://<bvid>`,ExoPlayer 打开时回调 `BiliDataSources` resolver → `BiliSongRepository.audioUrlsBlocking()` → view 补 cid → playurl(fnval=4048)取最高音质直链(FLAC>杜比>192K),错误重试时轮换 backupUrl。

## 关键陷阱(改代码前必读)

1. **B 站 API 一切规则见 [docs/bilibili-api.md](docs/bilibili-api.md)** —— 端点、参数、Header、响应路径、实测坑(榜单 cid 陷阱、-412、Referer)。
2. **直链 ~3h 过期**:绝不缓存 audioUrl;播放永远走 `bili://`。新页面加"播放"按钮时,直接 `song.toMediaItem()`,不要自己先取链。
3. **Referer 是硬要求**:`api.bilibili.com` 接口缺 Referer → 412;`*.bilivideo.com` 直链缺 Referer → 403。`BiliHttp` 和 `BiliHttpDataSourceFactory` 已处理,新 HTTP 出口务必复用。
4. **主线程禁止同步连 Media3 服务**:`PlayerController.ensureConnected` 若在主线程 `.get()` 会 ANR(踩过)。控制指令一律走 `withController {}`。
5. **路径含中文**:项目目录 `F:\自己写的项目\AvSong` 触发 AGP 检查,已用 `android.overridePathCheck=true` 绕过;若出现诡异的资源编码问题,先考虑挪到纯英文路径。
6. **版本矩阵锁死**:AGP 8.13.2 + Gradle 8.14.3 + Kotlin 2.2.21 + **Compose BOM 2026.06.01**。升级 Compose BOM 到 2026.08+ 会要求 AGP 9.1+(见 docs/ARCHITECTURE.md)。
7. **搜索结果无 `<em>` 标签剥离会显示乱码**:标题必须过 `stripEmTags()`(已在仓库层做)。
8. **榜单条目自带 cid 但不可信**:必须经 view 重取(见 docs/bilibili-api.md 陷阱节)。
9. **登录端点域名是 `passport.bilibili.com`**,不是 api 域(用 api 域 generate 返回 404,实测踩过);成功时 Cookie 在 Set-Cookie 响应头,不在 body。配方见 docs/bilibili-api.md §9。
10. **playurl 用 fnval=4048**(不是 16)才带出 flac/dolby;⚠ `dash.dolby.audio` 实测常为 JSON `null`,DTO 必须声明 `List<AudioStream>?`(可空),否则 kotlinx explicitNulls 解析整个响应抛异常→Source error(踩过,崩溃)。
11. **ExoPlayer 只能在主线程操作**:`MusicService.restore()` 若在 IO 协程里直接 `setMediaItems` 会崩(`Player is accessed on the wrong thread`)。读盘走 IO,操作播放器用 `withContext(Dispatchers.Main)`。
12. **收藏操作要 aid 不是 bvid**:搜索结果/榜单/收藏夹内容尽量带 `Song.aid`,缺失时经 `FavoriteRepository.fetchAid()`(view 接口)补;写接口必须带 `csrf=bili_jct`。
13. **队列重排用 `moveMediaItem`,别手动 seekTo**:Media3 的 `moveMediaItem` 会自动保持"当前播放项"不变;若再手动 `seekTo(新索引)` 反而会跳歌(实测踩过)。见 `PlayerController.moveItem`。
14. **收藏夹清空后 `resource/list` 回 `medias:null`**(不是 `[]`):DTO 声明可空 + `.orEmpty()`,否则 kotlinx explicitNulls 抛异常(和陷阱 10 的 dolby null 同根因)。
15. **离线曲播放三处联动坑**:① `BiliDataSources` 的 resolver 下游必须包 `DefaultDataSource.Factory(context, http)` 按 scheme 分发(裸 `DefaultHttpDataSource` 只认 http);② Media3 1.x 取 uri 用 `MediaItem.localConfiguration?.uri`(裸 `m.uri` 编译不过);③ 现场快照 `PersistedItem` 必须存 `uri`,否则重启后离线曲被还原成 bili:// 断网即挂。下载曲的 `Song` 保持 `bvid/mediaId=真实bvid`、只把 `audioUrl` 填 `file://…`。
16. **Cookie 续期三个实测坑**(前两个都表现为 correspond 404,极易误判):① `correspondPath` 必须用 `cookie/info` 返回的 **timestamp** 做 RSA,**本地时间戳一律 404**;② JDK 的 `OAEPWithSHA-256AndMGF1Padding` 默认 MGF1 是 SHA-1,必须显式 `MGF1ParameterSpec.SHA256`,否则密文错 → 404;③ correspond 响应是 gzip。另:`ac_time_value` 在轮询响应的 **`data.refresh_token` 平级字段**(不在 `data.url` 里,照 BiliTune 只解析 url 会拿到 null)。配方见 docs/bilibili-api.md §15。

## 代码约定

- 无 DI 框架:Application 持单例,ViewModel 经 `ViewModelProvider.Factory` 注入;新增仓库实现改 `AvSongApp` 一处即可。
- 序列化统一 kotlinx.serialization + `ignoreUnknownKeys`(B 站响应字段多且常变)。
- UI 状态:`StateFlow<MainUiState>` 单状态树;播放器状态经 `PlayerController.playerState()` 流合并。
- 注释解释"为什么"(尤其是实测踩坑结论),不复述代码。
- **改共享调用点(如 AvSongApp 的 composable 接线)先 grep 现有参数**:签名常带默认空实现,编译不报错但表现为「点了没反应」;并行会话可能同时改同一文件,构建失败先看错误文件是不是自己改的,改完 grep 关键 UI 文本确认区块没被覆盖丢失。

## 文档索引

- [docs/bilibili-api.md](docs/bilibili-api.md) — B 站端点配方 + 实测记录(改数据层前必读)
- [docs/bilibili-api-catalog.md](docs/bilibili-api-catalog.md) — 接口普查:可用/不可用候选 + 功能映射(想加功能先看这份)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 模块设计、播放链路时序、版本决策
- [docs/ROADMAP.md](docs/ROADMAP.md) — 待办与已知缺口

## 功能与设计要点(现行规则)

已实测通过:搜索/榜单/点播/迷你条/播放面板/后台通知/媒体键/bili:// 自愈/扫码登录/歌词多源/收藏夹/合集/本地歌单/下载离线播放/队列抽屉。新功能验收记录写 git log,不追加本文档。

### 全局视觉约束(用户明确要求,新增视觉元素前先看)

- **纯色扁平:禁渐变、禁阴影(elevation/dropShadow)、禁边框**。显式例外仅三处:首页精选卡底部黑纱渐变、歌单卡封面底部黑纱渐变(保证白色图标可读)、会员徽章描边。
- 图标一律裸图标无圆底(CircleButton 已删);色板/历史 chip/× 等小控件 `clickable(indication = null)`——裸 Icon+clickable 的默认涟漪是方形底,难看。
- 黄色品牌色仅深色模式;浅色 primary 用压深变体(柠檬黄浅色 `#7E7000`)。深浅两态都由 seed 色相派生(`ThemeStore.ThemeState(dark, seed, customHue)`),换配色只动 `ui/theme/Theme.kt`。
- 字体 Noto Sans SC 变量字体(`res/font/`,17.7MB):**必须单文件同时覆盖 CJK+拉丁**——Latin-only 字体缺 CJK 字形,系统回退字体不认 wght 轴,`FontWeight.Bold` 对中文完全失效(踩过)。换字体=替换 TTF + `Type.kt` 一处。
- 反馈提示统一 `ThemedNoticeHost`(`ui/common/ThemedNotice.kt`,透明 Dialog 窗口,FLAG_NOT_FOCUSABLE|NOT_TOUCHABLE,不压暗不挡触摸),系统 Toast 已清零。当初不用 Snackbar/Toast 就是因为队列抽屉是独立窗口会盖住它们——别换回去。
- 状态栏图标明暗由 MainActivity `isAppearanceLightStatusBars` 跟随主题(enableEdgeToEdge 默认判定只看系统,不够用)。

### 主题与设置

- 色板+色相轮盘走 ModalBottomSheet,**不自动关**(逐色预览);轮盘拖动 `previewHue` 只改内存、松手 `persistHue` 落盘(每帧写盘太重)。
- 设置卡在「我的」页,展开后进 `PlaybackSettingsScreen`(音质/历史上限/倍速/睡眠/热评);选项用 `SettingsExpandableItem` **行内展开**(用户否掉了弹抽屉样式),同一时间只展开一项。
- 倍速持久化在 `SettingsStore.playbackSpeed`;⚠ app 启动 collect settings **必须 `launch(Dispatchers.Main)`**——appScope 是 Dispatchers.Default,MediaController 跨线程调 `setPlaybackSpeed` 直接 IllegalStateException 崩(踩过)。
- 睡眠定时只存内存不持久化(睡醒残留倒计时比丢设置更糟);到点只 pause 不 toggle,否则恰好暂停时被反手起播。
- 硬币数是 nav 的 `data.money`,`data.coin` 是 B 币余额常缺省(docs §9);进「我的」tab 静默刷一次资料(只刷一次防 collect 循环)。
- **会话失效三分**(`QrLogin.fetchProfileResult()`):`Ok` 更新资料 / `Invalid`(-101 或 isLogin=false)清会话退未登录 / `Error`(网络、-412)保留会话下次再试。⚠ 网络失败绝不能判为未登录,会把还能用的登录误踢掉。

### 播放与队列

- 点歌行为:**列表点行 = `playNow`(插当前曲后一位立即播,已在队列跳播,不换队列)**;「播放全部/随机播放」= 整队替换。两种意图各有入口,别混。
- 播放队列唯一入口 = 迷你条/播放页的队列抽屉(ModalBottomSheet,双 tab「当前播放/历史播放」),无独立队列页。历史播放 = 本地 `PlayHistoryStore`,**不用 B 站 history 接口**(那是观看历史,app 播放不上报)。
- 队列补全(`playWithFill`):已加载页立即播,剩余页后台逐批 `appendSongsToQueue`。⚠ 三重护栏缺一不可:查真 `_loadingAll` flow(有 UiState 同名字段永远 false,勿用)、`PlayerController.session` 代次(playQueue/clearQueue 自增,代次变了补全立即中止)、append 按 mediaId 去重(幂等)。978 首合集曾并发涨到 1716 首的教训。
- **补全进行中的点击不能被护栏静默吞掉**:详情页 onPlay 三分流——搜索过滤子集(引用不等)→ 整队播;`loadingAll` 进行中 → 单首 playNow;否则 playWithFill。onShuffle 同理(进行中 = 已加载部分 shuffled 整队替换)。
- 歌单详情头部「播放」= 整队替换(`playWithFill(replace=true)`);**点行仍是插播不换队**(用户定的交互)。
- 显式队列操作按 mediaId 去重:playNext 已在队列 = 重插到当前曲后;addToQueue 已在队列 = 不加。连点长按堆出一串重复歌的教训。
- ⚠ 开 shuffle 必须把 repeatMode 从 ONE 拉回 ALL——ExoPlayer 里 ONE 优先级高于 shuffle,否则「当前曲无限循环」(`cyclePlayMode` 和 `toggleShuffle` 都有此防护,新入口也要做)。
- 交叉淡入淡出手写(Media3 1.x 无 crossfade API):曲末 6s 渐弱→自动切换→2.5s 渐强;⚠ 渐强必须挂 `onMediaItemTransition(reason=AUTO)`——`onPlayWhenReadyChanged` 自动切歌时不触发(踩过,表现为切歌后没声音);单曲循环同一首不算切歌;手动切歌/暂停/seek 立即回满音量。
- 歌单卡片播放三态高亮:MainViewModel `markPlayingPlaylist("folder:<id>"/"collection:<seasonId>"/"local:<id>")`,player 状态必须**以参数传入**组合(无参普通函数不会重组,实测)。⚠ B 站接口无播放数,卡片徽标放**项数**(media_count/total)。

### 播放页与迷你条

- 播放页 = 组合内 `AnimatedVisibility` 上滑覆盖面板(AvSongApp 里 Scaffold 外层 Box),**非导航页、非 ModalBottomSheet**(实测透明容器+全屏内容的 ModalBottomSheet 不接收任何输入);`BackHandler` 收起;**根节点必须 `clickable(null-indication){}` 消费点击**,否则点击/拖动穿透到后面内容(误切歌、误导航)。
- 信息卡/控制卡 = blur(3dp) 封面 + `surfaceContainerHigh.copy(alpha = dark 0.85f / 浅 0.92f)`;进度条 = WaveformSeekBar 44 根伪随机竖条,已播部分 `clipRect` 像素级裁剪,拖动中只显示预览、松手才 seek;控制键裸白图标(浅色下经 `CompositionLocalProvider(LocalContentColor)` 注入 onSurface)。
- **爱心(收藏态)= 双区合并判定** `favCheckedBili || 本地歌单任一含当前曲`(本地半区从 localPlaylistsState 实时派生;弹窗 onDismissWithState 只回写 B 站半区)。(2026-10-15 补:**乐观更新**——点击即置 favCheckedBili=false 爱心秒灭,`unfavoriteEverywhere` 完成后经 `unfavResult`(bvid→B 站半区是否仍亮)StateFlow 校正,B 站失败回滚为亮;**爱心键 combinedClickable 必须 indication=null**——默认方形涟漪在爱心上很难看(用户实测点名,与全局「小控件裸点无涟漪」约定一致)) 亮爱心点击 = **一键取消收藏**(`unfavoriteEverywhere(bvid)`:B 站所有收藏夹 + 所有本地歌单,需登录;未登录 B 站半区失败本地半区仍生效);灭 = 弹「收藏到」双区弹窗;**长按 = 无论状态都弹管理弹窗**(combinedClickable 需 ExperimentalFoundationApi)。
- **歌单中心定版(2026-10-15 用户两度改版,参考网易云设计图)= 两级 tab + 列表行**——顶部**两个大 tab**(左对齐文字式:「网络歌单」「本地歌单」)+ 右上「+」(网络=新建收藏夹,本地=新建本地歌单);网络歌单 tab 内按**分区标题**(合集/我创建的/收藏的)分组,内容一律**列表行 PlaylistRow**(56dp 方图 + 标题 + 「N 首」副标题 + 右 chevron;正在播本歌单时 chevron 位换音波条/播放三角,点击=暂停/续播;长按弹菜单)。旧的两列网格 PlaylistCard/formatPlaylistCount/GridCells 已删,`PlaylistScreen` 改 `LazyColumn`。⚠ 大块替换务必人工核对闭合括号层数(本次 end_marker 定位少一层 `}` 导致组件定义悬空、unresolved 全报);LazyColumn 版要 import `foundation.lazy.LazyColumn/items`与 `automirrored.filled.KeyboardArrowRight`(文件原是 grid 版 import,只换结构会漏)。

- **歌单详情多选(2026-10-15 四改定版)= 原地多选,页面结构不变**——用户三次否掉(常驻勾选圈/换顶栏「已选N/M」界面):最终形态=头部「多选」胶囊是**开关**(点亮变黄+文案「完成」),进入后**只有行前出现勾选圈 + 底部浮出操作栏**,顶栏/头部封面/播放下载胶囊全部保持原样,看起来就是「同一页多了一列勾选框」。操作栏=计数行(已选 N 首 + 全选/反选)+ 等宽按钮行(下载/收藏/下一首/入队/移除);MultiSelectTopBar 已删。⚠ 教训:**批量操作不要换页面骨架**(换顶栏/隐藏头部会被感知成跳转新页面);行内勾选圈与行点击分离用「外层 Row 无点击 + 内层 Row combinedClickable」。

- **歌单详情多选模式(2026-10-15 加)**:`PlaylistDetailContent` 内置完整多选态——入口=顶栏 ⋮「批量管理」(队列抽屉另有并行会话同日做的队列版多选,两套入口共享 BatchFavoriteDialog)。多选态:顶栏=「已选 N/M+全选/反选」,行序号位变圆形勾选框(点行=切换选中,不触发播放),头部/进度行/定位钮隐藏,底部浮 BatchActionBar(下载/收藏/下一首/入队/移除,纯色圆角胶囊)。要点:① 范围=已加载部分(不为多选拉全量);② 批量收藏=BatchFavoriteDialog 勾夹组合 →`MainViewModel.favoriteSongs(songs, folderIds)`(逐首 fetchAid→modify,300ms 间隔防 -412,resource/deal 幂等;与 favoriteFromQueue 同构,Song 版);③ 批量下一首播放=`playNextBatch`(按列表顺序逐首 playNext,保持相对顺序);④ 批量下载走现成 downloadAll(Manager 去重);⑤ 移除键按歌单类型传(onRemove/removeLabel)。三个详情页签名新增 onPlayNextBatch/onAddToQueueBatch/onFavoriteBatch/onDownloadAll;LibraryViewModel 三处 favRepo 由 private 改 val(详情页弹窗要用)。
- **歌单详情头部三胶囊(2026-10-15 用户改版)= 播放(黄)/下载/多选**——「随机播放」胶囊删除(用户:完全不需要;随机仍可从队列抽屉模式胶囊开)。DetailHeader 签名相应变为onPlayAll/onDownloadAll/onEnterSelect;PlaylistDetailContent 参数加 onDownloadAllParam(整队下载)。⚠ 多选底部 BatchActionBar 必须纵向紧凑(计数行+**等宽**按钮行,Modifier.weight(1f)):首版横排+横向滚动在 360dp 窄屏把「下载/移除」挤出可视区(用户实测只见三个键),且移除键文案别做removePrefix 拼接,直接传「移除」。
- **歌单详情简介上屏 + 头部下载(2026-10-15)**:① 收藏夹简介在 `resource/list` 响应的`data.info.intro`(**每页都带**,FavResourceInfo DTO;不需要为简介另发请求);合集简介在`seasons_archives_list` 的 `meta.intro`(SeasonMeta)。链路:FavResourceListData.info →FolderContents.intro → FolderContentViewModel.UiState;SeasonMeta.intro → Meta.intro →CollectionContentViewModel.UiState。详情页 subtitle 渲染(合集=UP 名 · 简介拼接,收藏夹=简介)。本地歌单无简介(纯本地无此概念)。② 头部「下载」胶囊 = `PlaylistDetailContent.onDownloadAllParam`(整队入下载队列),三详情页接各自 onDownloadAll —— 首版忘了传参,点了没反应(共享组件签名带默认值的老坑:编译不报错、静默无动作)。
- 热评弹幕:`ReplyRepository` 拉 `/x/v2/reply/main?type=1&oid=<aid>&mode=3`(docs §16),3 轨道飘过,`SettingsStore.danmakuEnabled` 开关(默认开);不消费点击,不挡封面/歌词手势。**固定首页 20 条不翻页**(用户实测拍板:mode=3 第 3 页起混入大量 0 赞低质评论,「热评」语义不成立;翻页游标必须用响应 cursor.next,next=1 自增会原样返回第一页)。评论点赞 = POST /x/v2/reply/action(csrf),回显零成本——响应每条自带 action 字段(0/1),乐观翻转+失败回滚。
- 迷你条与底栏**熔接为一个圆角容器**:迷你条 24dp 上圆角、底栏下圆角、中缝贴合,经 `shape`/`outerPadding` 参数按同屏与否动态传形;播放页两者都隐藏。全局列表 `contentPadding bottom ≥ 120dp` 避让,否则最后一排被裁半截。
- 迷你条播放键带环形进度(Canvas 弧),**图标 18dp + 环 22dp(用户多轮收敛的最终尺寸,别再放大)**;缓冲中(`PlayerState.isBuffering`)换 18dp 小转圈防连点。

### 首页

- 版式:黄色圆角块与 `HorizontalPager` 同层叠放——黄块只包到卡片 0.82 高度,卡下沿压深色底、右缘露下一张卡边。`FEATURED_COUNT=3`:轮播 = take(3),列表 = drop(3) + rankOffset=3 接名次;**onPlay 仍传整榜**(包装成 `onPlay(homeSongs, index + 3)`),迷你条/歌单上下文才不缺歌。
- 精选卡自动轮播 8s/页:⚠ 末页吸附位被夹住时 currentPage 永远到不了 size-1,按「到末页回卷」写必卡死——用「上轮请求页这轮未到达」检测回卷第 0 页;别监听 interactionSource 判用户操作(程序化滚动也发事件,会把自己误判成用户操作永久暂停)。
- 播放键三态:正在播本曲=暂停图标、暂停中/非本曲=播放图标;当前曲点卡走 onPlayPause,**别让按钮停在旧状态**。
- 定位当前播放悬浮钮:榜内无当前曲不出现;`locateInHome` 找不到**静默返回不弹提示**(按钮本就只在榜内含当前曲时出现)。
- 下拉刷新 `PullToRefreshBox`:指示器必须自绘纯色圆(`ui/common/RefreshIndicator.kt`,组件库 Indicator 带 elevation 阴影违反禁阴影);空态必须由可滚动容器承载(静态 Column 不产生 nested scroll,「下拉重试」是摆设)。
- 五源机制(MainViewModel `HomeSource`/`homeCache`)保留但 UI 已摘除,首页只展示热歌榜;恢复只需 UI 接线。配方 docs §13(⚠ 五源接口 duration 是秒、入状态前 `distinctBy { it.bvid }` 防 LazyColumn 重复 key 崩溃)。

### 歌单中心与详情页

- 「歌单」tab = 四类歌单:本地歌单 / 合集 / 我创建的 / 收藏的;两列网格卡片(PlaylistCard)。合集 = 本地订阅(`CollectionStore`)∪ B 站「我追的合集」(collected/list 的 type==21 条目,⚠ **`platform=web` 必带**,不带则合集条目不返回);B 站没有「订阅合集列表」接口(自己 mid 查返回的是自建合集)。配方 docs §14。
- 取消订阅 = **先 POST unfav 真取消 B 站侧**(未追过报错则忽略)再删本地订阅,否则会"复活";删除/取消成功后 `onBack()`,歌单页在返回回调里 `refreshIfLoaded()` 拉新。删除失败不再静默(就地红字/弹窗报 `actionError`)。
- 详情页(收藏夹/合集/本地)共用 `ui/library/PlaylistDetailContent.kt`:沉浸头部(blur 封面垫底;收藏夹封面接口不回,用第一首 coverUrl 兜底)+ 播放/随机双胶囊 + 行尾 ⋯ 菜单(下一首播放/加入队列/下载/收藏/移出)+ 长按同款菜单 + 歌单内搜索 + 定位钮 + 补全进度行。本地歌单薄包装(loading/hasMore 恒 false),专属「从歌单移除」经 `removeSongLabel` 参数;合集条目无 owner,UP 名为空时副标题不渲染。
- 歌单内搜索:进入搜索态即 `loadAll()` 拉全量(978 首大合集必须全量才搜得到);点过滤子集播过滤子集——**用引用相等(`playing === state.songs`)区分普通列表与过滤列表,别按下标**(实测点第 3 首播出全量第 3 首的坑)。
- 定位钮:目标未加载时先逐次 loadMore 再滚(LazyColumn 只有可见项,长列表里未加载的行定位不到)。
- 曲目行当前曲:**播放中**显示音波条,**暂停中回退为播放三角**——只判 isCurrent 不看 isPlaying 会装成在播(踩过)。

### 本地歌单与下载

- `LocalPlaylistStore` 纯 DataStore,不走 B 站接口,未登录可用;`LocalTrack` 存**完整快照含 aid**;歌单内按 bvid 去重。**不含手机本地音频文件**(那是「本地曲库」,未立项),存的是 B 站歌曲快照。
- 「存入本地歌单」**统一走 FavoritePickerDialog 双区**(上半 B 站收藏夹、下半本地歌单,勾选=存入/移出、就地新建)——独立的存歌单菜单项已删(与收藏重复,用户要求收敛)。
- 播放本地歌单 `playLocalPlaylist`:快照全量内存、无补全循环;**离线联动 = 按 bvid 查 `DownloadedStore` 元数据拿 fileName→`file://` 填 audioUrl**——⚠ 别用 fileOf 拼文件名(标题快照与下载时不一致)。
- 已下载:文件名 `<bvid>_<安全标题>.m4a`(防同名覆盖);`scanOrphans()` 开页双向同步(剪「有记录无文件」、补「有文件无记录」);下载页顶栏放大镜 = 本地过滤搜索(纯内存不走网络)。
- 未做(等真需要):下载并发队列/进度/取消、下载目录可选、共享目录。

### 搜索页

- 联想 = `androidx.compose.ui.window.Popup` 浮层(独立窗口,不占布局不推走历史/结果,点外部自动关)——**别用 Column+zIndex**(浮层与 when 内容非同父时 zIndex 无效,平铺进 Column 又推走内容,两版都被否);**仅 Idle 态显示**(任何态显示会挂在结果页不消失);250ms 防抖 + mapLatest;阈值 ≥1 字。配方 docs §12。
- 头部计数「已加载 N / 共 N」诚实显示:⚠ B 站 `numResults` 封顶 1000,无真实总数,勿接。
- 搜索中 `Loading(previous)` 携带旧结果不闪白;首搜显示 `SkeletonList` 骨架(纯 alpha 呼吸,合规禁渐变)。
- 历史 chip 连点竞态:`searchGeneration` 代次守卫 + job 取消,旧响应后到被丢弃;清空历史 = 模态确认;键盘 imeAction=Search 直搜。
- **合集不可搜索**(`search_type=season/series` 全 -1200,docs §13b),别再探。
- 直达解析(`resolveDirect`)支持 BV/av 号与 **b23.tv 短链**(GET 跟 302 抠最终 URL 的 BV/av;⚠ 翻页/短链都别自增游标,见 docs)。解析失败静默降级普通搜索。
- 勿复活清单(用户明确撤掉):搜索筛选 chips、播放页速度键(倍速在设置卡)、首页标题行更新时间/播放全部钮、回顶部悬浮钮、首页五源切换入口、QueueContent 上下移重排按钮(参数保留无处显示)。
