# AvSong — B 站音乐播放器(Android)

基于 B 站公开 API 的安卓音乐播放器:Kotlin + Jetpack Compose + Material 3 + Media3(ExoPlayer)。纯客户端、匿名使用、无后端。

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

## 架构速览

```
app/src/main/java/dev/avsong/
├── AvSongApp.kt            # Application:PlayerController 实例 + BiliSongRepository 单例
├── MainActivity.kt         # 单 Activity,请求通知权限,edge-to-edge
├── domain/                 # Song 模型、SongRepository 接口、分页
├── data/
│   ├── BiliConstants.kt    # UA / Referer / host 常量
│   ├── BiliHttp.kt         # OkHttp 单例:自动注入 UA/Referer/buvid+登录 Cookie
│   ├── BiliSongRepository.kt  # ★ 核心数据源(object 单例):搜索/榜单/取流/直达
│   ├── FavoriteRepository.kt  # 收藏夹=歌单(读/写,写带 csrf=bili_jct)
│   ├── SearchHistoryStore.kt  # 本地搜索历史(DataStore,去重/上限20)
│   ├── FakeSongRepository.kt  # 直链测试音频(回退验证 UI 链路)
│   ├── auth/
│   │   ├── AuthStore.kt    # DataStore 持久化 SESSDATA 等 + 内存 StateFlow
│   │   └── QrLogin.kt      # 扫码登录 ⚠ 端点在 passport.bilibili.com 域
│   ├── lyric/
│   │   └── LyricRepository.kt # ★ 歌词多源:B站AI字幕→bgm增强→网易云多候选+时长校验
│   └── remote/
│       ├── BiliDtos.kt     # kotlinx.serialization DTO(playurl 含 flac/dolby)
│       ├── PlayerV2Dtos.kt # /x/player/v2 字幕+bgm_info DTO
│       ├── FavDtos.kt      # 收藏夹 DTO
│       └── WbiSign.kt      # wbi 签名(备用,当前匿名链路不需要)
├── player/
│   ├── MusicService.kt     # MediaSessionService + 播放现场持久化(重启恢复续播)
│   ├── PlayerController.kt # UI↔服务桥:异步绑定 MediaController + PlayerState 流
│   ├── PlaybackPersistenceStore.kt # 队列/进度/模式 DataStore 快照
│   ├── BiliDataSources.kt  # ★ bili:// → ResolvingDataSource:现取直链+CDN备用换源
│   └── BiliHttpDataSourceFactory.kt  # 带 Referer 的 DefaultHttpDataSource
└── ui/
    ├── AvSongApp.kt        # Scaffold + NavHost(四顶级 tab)+ MiniPlayerBar 叠 AppBottomBar + Snackbar + 收藏弹窗
    ├── AppBottomBar.kt     # 底部导航:首页/搜索/歌单/我的(TopTab 枚举,悬浮圆角胶囊,选中项黄色圆形块,点击无涟漪)
    ├── MainViewModel.kt    # 搜索/榜单/歌词/历史状态 + 播放控制 + aid 缓存
    ├── ViewModelFactory.kt # remember*ViewModel 系列(Application 依赖注入)
    ├── MiniPlayerBar.kt    # 迷你播放条(与 AppBottomBar 熔接:上圆角/下圆角、中缝贴合;播放中封面匀速旋转 6s/圈)
    ├── common/             # ★ 共享歌曲列表组件(SectionHeader/SongList/SongRow/CoverArt/LoadingHint/StateHint)
    ├── home/               # 首页 tab(黄色大标题块 + 热歌榜 + 下拉刷新)
    ├── search/             # 搜索 tab(胶囊搜索框 + 历史 chips + BV直达 + 分页)
    ├── login/              # 登录页(二维码 + 轮询状态机 + 退出)
    ├── library/            # 歌单 tab(UP合集 + 收藏夹 = 歌单中心)+ 我的 tab(账户卡)/ 收藏夹内容页 / 合集内容页 / 播放页收藏弹窗
    ├── queue/              # QueueContent 队列列表(迷你条/播放页的队列抽屉共用:跳转/移除;无独立队列页)
    └── player/             # 全屏播放页(全幅封面背景;星=收藏,加号=当前歌追加队尾,点击封面↔歌词)
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

## 代码约定

- 无 DI 框架:Application 持单例,ViewModel 经 `ViewModelProvider.Factory` 注入;新增仓库实现改 `AvSongApp` 一处即可。
- 序列化统一 kotlinx.serialization + `ignoreUnknownKeys`(B 站响应字段多且常变)。
- UI 状态:`StateFlow<MainUiState>` 单状态树;播放器状态经 `PlayerController.playerState()` 流合并。
- 注释解释"为什么"(尤其是实测踩坑结论),不复述代码。

## 文档索引

- [docs/bilibili-api.md](docs/bilibili-api.md) — B 站端点配方 + 实测记录(改数据层前必读)
- [docs/bilibili-api-catalog.md](docs/bilibili-api-catalog.md) — 接口普查:可用/不可用候选 + 功能映射(想加功能先看这份)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 模块设计、播放链路时序、版本决策
- [docs/ROADMAP.md](docs/ROADMAP.md) — 待办与已知缺口

## 当前状态(2026-10-07)

核心链路全部在 MuMu(API 32)+ AVD(API 36)实测通过:热歌榜加载、搜索(视频源)、点歌播放、迷你条、全屏播放页、后台通知、媒体键控制、bili:// 按需取链自愈。

扫码登录已用真实账号完整验证(扫码→Cookie 提取→用户名显示→重启保持)。播放错误现在有全局 Snackbar + 重试按钮。实测确认:普通登录用户音质仍 192K 封顶,只有大会员更高(登录页文案已修正)。歌词多源(B站AI字幕→bgm增强→网易云多候选+时长校验)MuMu 实测两条路径均通过,并支持双语(tlyric 翻译行合并)。播放队列页(查看/跳转/移除/上下移重排)通过。收藏夹=歌单(读列表/内容分页/收藏弹窗回显/加入/移出,CSRF 写接口)通过。搜索增强(BV/av 直达、分页上拉、本地历史 chips)通过。播放健壮性(现场恢复:force-stop 重启自动续播、CDN 备用换源、FLAC/杜比选路)通过。Coil 封面磁盘缓存(重启零网络请求)通过。以上功能均借鉴 BiliTune(见 docs 与 git 历史)。待办见 docs/ROADMAP.md。

UI 视觉(2026-10-07 三次改版,参考黑黄设计稿,AVD+MuMu 截图验收):**固定深色主题**(`AvSongTheme` 不再走系统深浅色/动态取色),近黑底 `#070707` + 柠檬黄 `#F7F051` 强调 + 深灰卡片(surfaceVariant/surfaceContainerHigh)。B 站粉蓝品牌色已弃用。用户明确要求**纯色扁平:禁止渐变、阴影(dropShadow)、边框**,新增视觉元素前先看这条。导航:顶部大色块导航已移除,改**底部四 tab**(`AppBottomBar`:首页/搜索/歌单/我的,选中项图标/文字变黄加粗——黄色圆形垫底已按用户要求移除);迷你条与 tab 栏**熔接为一个圆角容器**(2026-10-11:迷你条只出 24dp 上圆角、tab 只出下圆角、中缝零间距,经二者的 `shape`/`outerPadding` 参数在 AvSongApp 按同屏与否动态传形;非 tab 页只有迷你条,保持全圆角胶囊;播放页两者都隐藏。同日按用户反馈整体收一档:迷你条封面 40dp/圆钮 38dp/内边距 5dp,tab 去掉垫底后图标 22dp、行内边距收紧)。迷你条的队列钮不再跳队列页,改为**底部 4/5 屏抽屉**(ModalBottomSheet + `fillMaxHeight(0.8f)`,内嵌共用 `QueueContent`:点非当前曲直接切播、点当前曲无操作(经 `index != currentIndex` 判断)、抽屉始终不自动关闭,由用户手动下滑/点遮罩关闭;抽屉传 `showReorder=false` 隐藏上下移(播放队列现在只有抽屉一个入口,重排按钮暂无处显示但参数保留)、`isPlaying` 让当前曲行首显示 `PlayingEqualizer` 音波条(暂停中回退为播放三角);徽标会向上溢出文字右上角,**DrawerTab 的 Row 不能加 clip**(会裁掉徽标上半截);⚠ 开随机(shuffle)时必须把 repeatMode 从 REPEAT_MODE_ONE 拉回 ALL —— ExoPlayer 里 ONE 优先级高于 shuffle,否则变「当前曲无限循环」,`cyclePlayMode` 和播放页 `toggleShuffle` 都做了该防护;`QueueContent` 加 `listState` 参数,抽屉打开即 `scrollToItem(currentIndex)` 定位到当前曲;该版本 ModalBottomSheet 是 ExperimentalMaterial3Api 且无 shadowElevation 参数,用 tonalElevation=0;播放页底部控件也接入同一抽屉:左端=模式单键(图标随模式变,顺序播放时灰色),右端原循环键移除、改为队列抽屉入口,Routes.QUEUE 整页与 QueueScreen composable 已删除)。抽屉头部为双 tab「当前播放(数字徽标)/历史播放」:徽标=队列数,贴在文字右上角(`DrawerTab` 组件);历史播放=本地播放记录(`PlayHistoryStore` DataStore,起播即记、同曲去重置顶、上限 50),**不用 B 站 history 接口**——那是观看历史,app 内播放不会上报成 B 站播放;MainViewModel 在 playerState 采集里当前曲变化时记快照(PlayerState 的 mediaId/title/artist/artworkUri 足够回放),`playFromHistory` 回放策略=已在队列跳播、队列空单曲入列、否则追加队尾起播,复用 `SongList` 渲染(当前曲遮罩音波条/高亮自动生效)。当前播放 tab 的列表上方有**功能条**(仅此 tab):模式为**单键循环切换**(一个胶囊显示当前模式「列表循环/单曲循环/随机播放/顺序播放」,点击经 MainViewModel `cyclePlayMode` 按序轮转;顺序=REPEAT_OFF+关随机;PlayerController 保留 `setRepeatMode`/`setShuffle` 供其调用;抽屉 Tab 文字对齐用「Box 只包文字、徽标钉 TopEnd」结构)+ 下载(解析 `audioUrlBlocking` → BiliHttp(自带 Referer)流式存到 `getExternalFilesDir(MUSIC)/*.m4a`,反馈走 **Toast**——抽屉是独立窗口 Snackbar 会被盖住,VM 用 `downloadMessage` 流 + `consumeDownloadMessage` 防重)+ 收藏(复用 FavoritePickerDialog)+ 一键清空(`clearQueue`,同时停止播放、徽标随队列空消失,**点击先弹 AlertDialog 模态确认「清空播放队列?/将移除全部 N 首/取消|清空」防误触**——初版用 DropdownMenu 气泡,用户要求改模态)。首页=热歌榜单源:黄色 Discover 卡(英文 Discover 标题靠左上,删过又按用户要求加回;无刷新按钮,刷新只走下拉手势)+ 榜单前三首轮播卡(封面铺卡、可横滑、右缘露边、卡片下沿压出黄块到深色底;卡上不放歌名,右下播放键随播放状态切 PlayArrow/Pause)+ 第 4 首起的列表(行尾只留时长,无播放钮);五源切换已从 UI 摘除(VM 机制保留,见 2026-10-11 节);搜索=无边框填充 TextField+历史 chips+BV直达+分页;歌单=歌单中心(UP 合集 + 收藏夹,2026-10-11 起);我的=账户卡(登录态/去登录)。播放页(2026-10-11 起为**底部上滑覆盖面板**,非导航页、也非 ModalBottomSheet——实测透明容器+全屏内容的 ModalBottomSheet 窗口**不接收任何输入**(按钮/滑动/返回全部穿透),改用组合内 `AnimatedVisibility` + `slideInVertically/slideOutVertically` overlay(AvSongApp 里 Scaffold 外层包 Box、面板在其后绘制,输入必然可达),`BackHandler(enabled=showPlayerSheet)` 拦截系统返回收起面板;迷你条黄钮与播放页底端右键都拉起它):全幅封面背景 + 55% 黑纱 + 两张深灰圆角卡(信息卡可点击切歌词)+ 黄色实心播放键;底部控制条:左端播放模式单键(cyclePlayMode,图标随模式变,顺序播放灰色)、右端队列抽屉入口;顶栏裸图标(左上收起箭头/右上心形收藏,无圆形背景,「加入歌单」按钮已移除);信息卡/控制卡为**毛玻璃卡**(FrostedCard:封面原图 `blur(18.dp)` 铺满卡内 + 38% 黑纱,近似磨砂;无封面时回退纯色);进度条为**音波样式**(WaveformSeekBar:44 根稳定伪随机高度竖条,已播黄/未播灰,点击与横向拖动直接 seek,**已播部分用 `clipRect` 按进度像素级裁剪**——边界条平滑填充,不做整格跳变(用户反馈一格一格很怪异);拖动中显示预览位、松手才真正 seek);控制键为**裸白色图标**(用户要求全项目图标不带黑色圆底,CircleButton 组件已删,控制行用 `CompositionLocalProvider(LocalContentColor provides White)` 统一注入白色,模式键激活态仍单独 primary 高亮);信息卡/控制卡为**半透明深灰卡**(`surfaceContainerHigh.copy(alpha = 0.72f)`,0.55 被用户反馈「太透」后加深;封面隐约透出)——演变史:毛玻璃(封面 blur+黑纱)被否(「太丑」)→ 实心纯色卡 → 半透明无 blur;音波条未播部分用实心 `outline` 灰;⚠ 播放面板根节点必须消费点击(`clickable(null-indication){}`),否则点击/拖动会穿透到面板后面的内容——实测踩过(误切歌、误导航);当前曲在队列列表的 `primaryContainer` 深黄高亮。共享列表组件在 `ui/common/`,改样式一处生效。⚠ MuMu 截屏可能滞后输入一帧(像素级验证会误判「点了没反应」),UI 状态验证以 `uiautomator dump` 的节点存在性为准。

首页下拉刷新(2026-10-08 补,MuMu API32 + AVD API36 实测:空态下拉 → `spi`/`rank` 重新请求 → 列表恢复):内容区用 material3 的 `PullToRefreshBox`(1.4.0 起稳定 API,无需 opt-in),`onRefresh` 接 `MainViewModel.loadRanking()`;黄色标题块留在刷新区之外,下拉时不动。两条硬约束:① 指示器必须自绘纯色黄圆(箭头随进度旋转 → 刷新中转圈),不能用 `PullToRefreshDefaults.Indicator`——它带 elevation 阴影,违反「禁阴影」设计约束;② 空态/短内容必须由可滚动容器承载(空态是单条 `LazyColumn` + `fillParentMaxSize`),静态 `Column` 不产生 nested scroll 事件,提示文案里的「下拉重试」会变成摆设。

首页发现区五源(2026-10-09 加,MuMu API32 实测五源全通;**2026-10-11 起五源入口从 UI 摘除,首页只展示热歌榜**;`MainViewModel` 的 `HomeSource`/`homeCache`/`selectHomeSource` 机制原样保留,恢复五源只需在 UI 接线):「热歌榜 / 推荐 / 热门 / 排行 / 最近」「热歌榜 / 推荐 / 热门 / 排行 / 最近」,五个源共用 `MainUiState` 的 `homeXxx` 一套状态,`HomeSource` 枚举携带 label/title/subtitle。配方见 [docs/bilibili-api.md](docs/bilibili-api.md) §13:推荐=`rcmd` 推荐流(**内容是全站泛推荐,非音乐垂直**)、热门=`popular`(**唯一支持翻页的源**,`no_more=true` 才是没有下一页)、排行=`ranking/v2?rid=3`(音乐区,**本接口只认 rid=0/3**,子分区全 -400)、最近=`history/cursor`(需登录,`ps` 上限 30,非视频条目按 `history.bvid` 空过滤)。三条硬约束:① **cid 一律不用**,四源都经 `bili://` 现取(与搜索一致,规避榜单 cid 陷阱);② 这些接口的 `duration` 是**秒**,不是搜索那种 `"3:46"`;③ 入状态前 `distinctBy { it.bvid }` —— 列表 key 是 bvid,历史里同视频多看几个分P会有重复 key,**LazyColumn 重复 key 直接崩**。「最近」带**续播**:`progress` 秒数进 `Song.resumePositionMs` → `playQueue(items, index, startPositionMs)`,实测点「听到 00:26」的歌起播在 26s;进度提示走独立的 `Song.progressHint`(只在列表行拼在歌手名后),**不能拼进 artist**,否则播放器和通知栏也会挂着「听到 00:26」。切源走内存缓存 `homeCache`(切回不重复请求),下拉刷新走 `loadHome(force=true)` 强制重取。

UP 主合集=歌单(2026-10-10 加,MuMu 实测全链路通过:粘链接添加→meta 回显→978 首大合集翻页→点歌整队入列播放):「合集」区(2026-10-10 加在「我的」页;2026-10-11 起随歌单中心整体移到「歌单」tab 顶部、收藏夹区上方),右上「+」弹对话框粘贴 `space.bilibili.com/<mid>/lists/<sid>?type=season` 链接。配方见 [docs/bilibili-api.md](docs/bilibili-api.md) §14:`seasons_archives_list` 一次给 meta+曲目(翻页换 page_num),UP 名经 `card` 接口 best-effort 补齐(**新版大 mid 的 card 返回 name=null,老端点 -799、wbi 版 -352,拿不到就留空,UI 副标题禁止渲染悬空「· 」**)。B 站没有「订阅的合集」列表接口——自己 mid 查 `seasons_series_list` 返回的是自建合集(实测确认),所以走本地订阅:`CollectionStore`(DataStore)按 (mid,seasonId) 去重持久化,最新在前。**type=series(系列)是另一套接口,未接**,解析时明确报错。添加流程:先 `CollectionRepository.parse()` 正则提取(纯函数,对话框内就地校验),再拉一页 meta(pageSize=1)确认合集存在并取标题/封面/总数入库;合集不存在时 code:0 但 data=null,要当错误处理。曲目条目**没有 owner、cid 可能 null**,hasMore 按 `total > pageNum*pageSize` 算。


首页一分为二版式(2026-10-11 参考设计稿改版,MuMu API32 实测:轮播横滑/点卡起播/时长显示全通过):精选区 = 黄色圆角块(16dp 圆角)与 `HorizontalPager` 同层叠放 —— 黄块高度只包到卡片 `CARD_OVER_YELLOW=0.82f` 处,pager 以固定偏移(`PAGER_START`=黄边距+内边距,与标题左缘对齐)绝对定位,卡片下沿自然压到深色底,右缘露下一张卡边(卡片可越出黄块右界)。卡尺寸 `CARD_H=190dp`、`CARD_WIDTH_FRACTION=0.48f`(用户两轮要求卡改小、尤其宽度;窄卡+宽露边是必然结果,露边≈卡宽属预期)。四条实现要点:① `FEATURED_COUNT=3`,轮播 = `homeSongs.take(3)`,列表 = `drop(3)` + `SongList.rankOffset=3` 接名次;**队列仍传整榜**(onPlay 包装成 `onPlay(homeSongs, index + 3)`),歌单页/迷你条上下文不缺歌;② 榜单接口条目顶层无时长,`RankItem` 补了 `related_archive.duration`(秒)做行尾时长,没有该对象的条目不显示;③ `SongList`/`SongRow` 新增 `showPlayButton`(默认 true,搜索页不受影响)与 `rankOffset`(默认 0)参数;④ 卡上不放歌名药丸(用户要求已删),右下 40dp 播放键是唯一控件:图标随 `state.player` 切换,`mediaId == currentMediaId` 时整卡点击走 `onTogglePlayPause`(HomeScreen 新增 `onPlayPause` 参数由 AvSongApp 接 `viewModel::playPause`),否则走 `onPlay` 入列整榜 —— 播放中点卡=暂停,暂停中点卡=续播,别让按钮停在旧状态;⑤ 下方列表的当前播放行:歌名 primary 高亮 + 播放中**整封面**盖 45% 黑色遮罩(跟随封面 12dp 圆角,`matchParentSize`,用户反馈小徽章太突兀后改整盖)内嵌居中 `PlayingEqualizer` 音波条(4 根错相位竖条,`SongList` 新增 `currentMediaId`/`isPlaying` 参数按 `song.mediaId` 匹配;Canvas 读动画值只重绘不重组;搜索页未传参不受影响,队列页自己的 primaryContainer 高亮未动)。两个实测坑:热歌榜是**实时榜**,每次下拉刷新顺序都会变,LazyColumn 按 mediaId key 锚定,刷新后原地那首歌直接显示新名次(实测 4→8→17,不是滚动错位);MuMu 测试机是 **360dp 窄屏(wm density 640 = 4x)**,adb 手势坐标按 1dp=4px 换算,且精选区与列表之间的空档里拖动会触发下拉刷新而非滚动。

「歌单」tab(2026-10-11 起为**歌单中心** = UP 主合集 + B 站收藏夹,位于搜索与我的之间;2026-10-08~10 它曾是播放队列壳页,用户拍板「播放队列只留抽屉」后整个替换,`ui/queue/PlaylistScreen.kt` 已删除):实现在 `ui/library/PlaylistScreen.kt`,黄标题块右上「+」=新建收藏夹。**版式(用户当日指定)= 两列 `LazyVerticalGrid` 卡片,上方正方形封面 + 下方歌单名(2 行省略)**:封面左上角半透明黑纱数字徽标、右下角**裸白色播放图标(30dp,无底色,可点区 40dp)**——图标对比度靠封面**底部向上渐变黑纱**(0.45 透明 → 底 55% 黑;这是用户明确要求的渐变例外,全局「禁渐变」不含此遮罩;初版黄圆底被用户否掉)。两条硬约束:① **B 站收藏夹/合集的列表接口没有播放数字段**(`cnt_info.play` 只是单视频的),徽标放的是**项数**(media_count/total,≥1万缩写 x.x万),别当播放数用;② 播放钮 = **直接播整个歌单**:MainViewModel `playFolderPlaylist`/`playCollectionPlaylist` 取第一页整队入列(不自动接后续页),失败/空歌单走 `queueNotice`→Toast。合集卡**长按 = 移除本地订阅**(可重加,无确认);收藏夹卡无长按(删除是 B 站远端破坏性操作,不开放)。合集区(标题行带计数与「添加合集」+)在前、收藏夹(「我创建的/收藏的」)在后;点卡片进 FolderContentScreen/CollectionContentScreen(路由不变);未登录时收藏夹必拉取失败,错误区给「重试 + 去登录」。⚠ 最后一排卡片的播放钮会被迷你条熔接容器裁掉下半截(网格视口底 ≈ 迷你条顶),自动化测试点它要取可视区中部坐标。播放队列的唯一直路口 = 迷你条/播放页的队列抽屉(QueueContent 仅剩这一个调用方)。
