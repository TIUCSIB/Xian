# ROADMAP

> 完成一项就把它从列表里删掉,并在 AGENTS.md「当前状态」更新一句。保持本文件短。

## 已完成(2026-10-07)

- [x] 骨架:AGP8.13/Gradle8.14/Compose BOM 2026.06/Media3 1.11,中文路径绕过
- [x] 数据层:spi buvid、搜索(video)、热歌榜、view 补 cid、playurl 取 192K 直链
- [x] 播放:MediaSessionService 前台服务、通知栏/媒体键、迷你条、全屏播放页、进度 ticker
- [x] 直链过期自愈:`bili://` scheme + ResolvingDataSource 现取现用
- [x] 双设备实测:MuMu(API32)+ AVD(API36),榜单/搜索/播放/后台/通知全通过
- [x] 扫码登录:passport 域 generate/poll、Set-Cookie 提取、DataStore 持久化、nav 资料、登录页 UI(二维码+状态机+退出)。**已真实账号验证**:扫码成功、用户名显示、重启后登录态保持
- [x] 播放失败可见化:PlayerState.error(取 playerError.cause)+ 全局 Snackbar(带"重试"动作 → prepare+play 重新走 resolver 取新链)
- [x] 音质文案修正:实测普通登录用户仍 192K 封顶(非 320K),仅大会员更高;登录页文案已按此更新
- [x] 歌词:网易云公开接口(歌名+歌手搜索→LRC→解析→高亮滚动→点击 seek),播放页点封面切换。MuMu 实测通过(配方见 docs/bilibili-api.md §10)
- [x] 播放队列页:PlayerState 队列快照 + QueueScreen(当前项高亮/点击跳转/X 移除),播放页右上入口。MuMu 实测通过(跳转✓ 移除 20→19✓)
- [x] 收藏夹=歌单(借鉴 BiliTune):FavoriteRepository(读/写,写带 csrf)+ 我的音乐页(我创建的/订阅的)+ 收藏夹内容页(分页/播放全部/移除)+ 播放页收藏弹窗(状态回显/勾选加入移出/内联新建)。MuMu 实测读写全通过。配方见 docs/bilibili-api.md §11
- [x] 时长格式修复:formatClock 支持小时(65h 合集不再显示 3926:17)
- [x] 歌词多源升级(借鉴 BiliTune):B站AI字幕(登录态命中)→ bgm_info 增强 → 网易云多候选+时长校验,配方见 docs/bilibili-api.md §10b。MuMu 实测两条路径均通过
- [x] 搜索体验包(借鉴 BiliTune):BV/av/分享链接直达、结果分页上拉加载、本地搜索历史 chips(DataStore)。⚠ 搜索建议端点实测 code:3 不稳定,未做。配方见 §12
- [x] 播放健壮性包(借鉴 BiliTune):① 现场恢复(队列+进度+模式 DataStore 快照,force-stop 重启自动续播,MuMu 实测通过);② CDN 备用换源(resolver 时间窗口启发式,错误快速重试才轮换 backupUrl);③ FLAC/杜比选路(fnval=4048,优先级 flac>dolby>最高带宽,含 dolby.audio=null 崩溃修复)
- [x] Coil 磁盘缓存:AvSongApp 实现 SingletonImageLoader.Factory,diskCache(cacheDir/image_cache,2% 上限)。实测重启封面零网络请求(缓存命中)
- [x] 双语歌词:网易云 tlyric 字段按时间戳就近合并进 LyricLine.sub,LyricView 原文下方渲染翻译行。Coldplay《Yellow》实测中英对照正确
- [x] 队列重排:QueueScreen 上下移按钮 + PlayerController.moveItem(Media3 moveMediaItem,自动保持当前播放项)。⚠ 手动 seekTo 会跳歌(见 AGENTS 陷阱#13)。真拖拽未做(复杂度高、收益低,BiliTune 也未做)
- [x] 首页发现区五源(2026-10-09):热歌榜(原有)+ **推荐**(rcmd)/**热门**(popular,可翻页)/**排行**(ranking/v2 rid=3 音乐区)/**最近播放**(history/cursor,需登录)。胶囊切换 + 内存缓存 + 下拉刷新;**最近播放支持续播**(progress → setMediaItems 起播位置)。MuMu 五源逐个实测通过(翻页到 60+ 条、续播起播在 26s)。配方见 docs/bilibili-api.md §13
- [x] UP 主合集=歌单(2026-10-10):「我的」页「合集」区,粘贴 `space.bilibili.com/<mid>/lists/<sid>` 链接添加,DataStore 本地持久化,合集内容页(只读分页/播放全部/单曲点播)。978 首大合集实测:meta 回显、翻页加载、整队入列连播全通过。B 站无「订阅合集」列表接口(自建合集才走 seasons_series_list),故本地订阅方案。⚠ type=series(系列)未接。配方见 docs/bilibili-api.md §14

## 下一步(按推荐顺序)

1. **大会员账号实测音质升级**(需有大会员的账号):登录后 playurl 是否返回 30251/FLAC;选路代码已就位(§5),只缺真实 VIP 验证
2. **音频下载离线听**(借鉴 BiliTune:任务队列+媒体目录+已下载伪歌单;工程量最大)
3. **搜索建议**(s.search.bilibili.com/main/suggest 实测 code:3,需逆向签名参数,暂缓)
4. **播放速率调节**(0.5–2.0x,ExoPlayer setPlaybackSpeed + 持久化)
5. **沉浸页/横屏**(借鉴 BiliTune 桌面端观感,纯安卓可做全屏封面旋转)

## 技术债

- [ ] `PlayerController.withController` 未连接时指令会排队,快速连点可能乱序——低频,暂不动
- [ ] `WbiSign` 已实现但全链路未使用(匿名不需要);做登录时一并启用并补单测
- [ ] `FakeSongRepository` 仅测试链路;若 CI 化考虑加 fake 驱动 UI 测试
- [ ] release 构建未验证(minify 关闭);上架前开 R8 + 测 proguard 规则
- [ ] 无应用图标自适应蒙版细节(目前音符前景 + 纯色底,够用)

## 长期想法(不承诺)

- 歌单/收藏本地持久化(DataStore)
- 定时关闭、睡眠模式
- 车机/Android Auto(Media3 天然支持,需 manifest 加 browsable 意图)
- 视频源 MV 画面播放(ExoPlayer 已能解码,缺全屏画面层)
