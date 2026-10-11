# B 站接口普查(候选清单 + 实测结果)

> 实测日期 **2026-10-09**,匿名 + 登录态(普通账号)双跑。目的是回答「还能接哪些接口、能加什么功能」。
> **分工**:本文是**候选清单**(可用/不可用/待评估);已采用端点的参数配方与踩坑结论在 [bilibili-api.md](bilibili-api.md),两者冲突时以那份为准。
> 通用要求(UA / Referer / buvid / 登录)完全沿用 bilibili-api.md §全局要求,本文不再重复。

## 一、已实现(现状基线)

| 端点 | 用途 | 实现位置 |
|---|---|---|
| `/x/frontend/finger/spi` | buvid3/buvid4 | `BiliHttp.kt` |
| `/x/web-interface/search/type?search_type=video` | 搜索 | `BiliSongRepository` |
| `/x/centralization/interface/music/comprehensive/web/rank` | 首页热歌榜 | 同上 |
| `/x/web-interface/view?bvid=\|aid=` | 补 cid / av→bv / 直达 | 同上 |
| `/x/player/playurl?fnval=4048` | 取音频直链 | 同上 |
| `/x/player/v2` | AI 字幕 + bgm_info | `LyricRepository` |
| `passport…/qrcode/generate\|poll` | 扫码登录 | `QrLogin` |
| `/x/web-interface/nav` | 登录资料 | 同上 |
| `/x/v3/fav/*`(6 个) | 收藏夹=歌单 | `FavoriteRepository` |
| 网易云公开接口 | 歌词兜底 | `LyricRepository` |

## 二、实测可用 · 未接入

### 2.1 榜单 / 发现

| 端点 | 匿名 | 关键字段 | 能做什么 |
|---|---|---|---|
| `GET /x/web-interface/ranking/v2?rid=3&type=all` | ✅ 96 条 | `aid,bvid,**cid(真实)**,title,pic,duration,pubdate,owner{mid,name,face},stat{view,like,reply,favorite,coin}` | **音乐区排行榜**。比现用热歌榜强:同一个响应自带 UP 主与播放/点赞数 |
| `…/ranking/v2?rid=0&type=all` | ✅ 100 条 | 同上 | 全站总榜 |
| `GET /x/web-interface/popular?pn=&ps=` | ✅ | `list[]` 同 view 系结构 | 综合热门 |
| `GET /x/web-interface/popular/precious?page=&page_size=` | ✅ | `list[]` + `achievement` | 入站必刷 |
| `GET /x/web-interface/popular/series/list` | ✅ | 每周必看期号表 | 每周必看 |
| `GET /x/centralization/interface/music/hot/toplist` | ✅ | `music_title,singer,album,mv_bvid,heat,can_listen` | music-center 热榜。⚠ `can_listen` 常为 false、`mv_bvid` 常空,**必须过滤** |
| `GET /x/copyright-music-publicity/toplist/music_list?list_id=N` | ✅ | `music_title,singer,album,mv_bvid,mv_aid,creation_bvid,creation_aid,**creation_first_cid**,creation_duration,creation_up,heat,can_listen` | ★ **官方版权榜**。唯一带完整「歌名/歌手/专辑」元数据的榜单(见 2.2) |
| `GET /x/web-interface/online` | ✅ | `region_count` | 在线人数 |

- 榜单 Referer 用 `https://www.bilibili.com/v/music/`;music-center 两个榜用 `https://music.bilibili.com/pc/music-center`。
- ⚠ `list_id` 实测 1/2/3/4/5/13/22/32/43/76/100/128 全部 code:0(各 20 条),但**没有目录接口**(`toplist/list`、`toplist/info` 均 404),只能靠已知 id 硬编码。

### 2.2 版权榜是唯一「真歌元数据」源(cid 可信)

实测对比同一批数据的两条路:

| 榜单 | cid 字段 | 与 view 真实 cid 一致? |
|---|---|---|
| `copyright-music-publicity/toplist/music_list` | `creation_first_cid` | ✅ **2/2 一致,可直接 playurl**(省一次 view) |
| `centralization/music/comprehensive/web/rank`(现用) | `cid` | ❌ **5/6 不一致** → 印证 [bilibili-api.md §3](bilibili-api.md) 的「榜单 cid 陷阱」 |

代价:`can_listen` 过滤后命中率低(抽样 6 条仅 2 条可听)。适合做「官方曲库/热歌榜」而不是主力搜索源。

### 2.3 相似 / 推荐

| 端点 | 匿名 | 说明 |
|---|---|---|
| `GET /x/web-interface/archive/related?bvid=` | ✅ **40 条** | 相关推荐。字段与 ranking 同构(含 `bvid,cid,duration,owner,stat`)→ **做「相似歌曲 / 无限电台」的首选** |
| `GET /x/web-interface/view/detail?bvid=` | ✅ | 一次返回 `View,Card,Tags,Reply,Related,hot_share` 等,替代多次单点请求 |
| `GET /x/web-interface/wbi/index/top/feed/rcmd?ps=12&fresh_type=4` | ✅ | 首页推荐流。**注意:匿名也通,不需要 wbi 签名**(路径带 wbi 但未校验) |

### 2.4 专辑 / 合集 / 多P

| 端点 | 匿名 | 说明 |
|---|---|---|
| `GET /x/polymer/web-space/seasons_series_list?mid=&page_num=&page_size=` | ✅ | UP 的合集/系列列表。`items_lists.{seasons_list,series_list}`,meta 含 `season_id,title,total,cover` |
| `GET /x/polymer/web-space/seasons_archives_list?mid=&season_id=&page_num=&page_size=` | ✅ | 合集曲目(实测拿到 29 首)→ **做「专辑/歌单」的正路** |
| `GET /x/player/pagelist?bvid=` | ✅ | 多 P 视频 → 串烧/合辑按 P 拆歌 |

### 2.5 UP 主 / 歌手

| 端点 | 匿名 | 说明 |
|---|---|---|
| `GET /x/space/acc/info?mid=` | ✅ | 老端点匿名可用,`name,face,sign,level` |
| `GET /x/space/wbi/acc/info?mid=` | ❌ | **必须 wbi 签名**(裸调 `-352 风控校验失败`;签名后 code:0)。字段更全(含 `vip,official`) |
| `GET /x/relation/stat?vmid=` | ✅ | `follower, following` |
| `GET /x/web-interface/card?mid=` | ✅ | `card, following, archive_count, follower, like_num` |
| ❌ `GET /x/space/{wbi/}arc/search` | ❌ | **投稿列表拿不到**,见第三节 |

### 2.6 个人数据(需登录)

全部实测 code:0(用设备上的真实登录态):

| 端点 | 关键字段 | 能做什么 |
|---|---|---|
| `GET /x/web-interface/history/cursor?ps=20&max=0&view_at=0` | `title,cover,uri,history{bvid,cid},progress,view_at,author_name,author_mid,duration` | ★ **最近播放**;`progress` 是秒数 → 直接做「继续播放」 |
| `GET /x/v2/history/toview` | `list[]` | 稍后再看(实测 0 条,接口通) |
| `GET /x/web-interface/nav/stat` | `following,follower` | 我的关注/粉丝数 |
| `GET /x/relation/followings?vmid=&pn=&ps=` | `list[]`(20 条) | 我的关注列表 |
| `GET /x/polymer/web-dynamic/v1/feed/all?type=video&page=1` | `items[]`(20 条)+ `offset` | 关注动态 → 「关注的 UP 新歌」 |

### 2.7 互动状态(只读)

| 端点 | 匿名 | 说明 |
|---|---|---|
| `GET /x/web-interface/archive/relation?bvid=` | ✅ | `attention,favorite,season_fav,like,dislike,coin` → **播放页回显「已赞/已投币」** |
| `GET /x/web-interface/archive/has/like?bvid=` | ✅ | 单独查点赞态 |

- **写接口(点赞/投币/分享/稍后再看增删)未实测** —— 会真实改动账号数据,故意没测。已知要求:form POST + `csrf=bili_jct`,Referer `https://api.bilibili.com/`,Origin `https://www.bilibili.com`(与收藏夹写接口同规)。

### 2.8 评论 / 搜索增强 / 其它

| 端点 | 匿名 | 说明 |
|---|---|---|
| `GET /x/v2/reply/main?type=1&oid=<aid>&mode=3&next=0` | ✅ | 热评。`replies[].content.message / member.uname / like` → 播放页「热评」 |
| `GET /x/web-interface/search/square?limit=10` | ✅ | 热搜榜 `trending.list[].keyword` → 搜索页热词 |
| `GET /x/web-interface/search/default` | ✅ | 默认搜索词(占位符文案) |
| `GET /x/web-interface/search/type` + `order=click\|pubdate\|dm\|stow` + `duration=1`(≤10min) + `tids=3`(音乐区) | ✅ | **排序与时长筛选**,现用搜索未启用 → 可做「按播放量/最新/只要短音频」 |
| `GET /x/player/videoshot?bvid=&cid=` | ✅ | `image,index,img_x_len…` 雪碧图 → 进度条预览/封面墙 |
| `GET /x/tag/archive/tags?bvid=` | ✅ | 视频标签 |

## 三、实测不可用(黑名单,别再试)

| 端点 | 结果 | 结论 |
|---|---|---|
| `GET /x/space/wbi/arc/search` | **`-412 request was banned`** | ⚠ **UP 主投稿列表被 WAF 封死**:裸调/签名/加 `web_location`/加 `dm_img_*` 指纹参数四种组合全部 412(带登录态也一样)。→ **「歌手作品列表」只能靠搜索近似**(见 4) |
| `GET /x/web-interface/ranking/v2` 子分区 `rid=28/31/30/59/193/29/130/194` | `-400` | 排行榜**只支持 rid=0 和 3**,音乐子分区全不可用 |
| `GET /x/web-interface/archive/stat?bvid=` | 404 | 已下线 → 用 `ranking/v2` / `view/detail` 里的 `stat` |
| `GET /x/web-interface/view/conclusion/get` | `-403` | AI 摘要不可用 |
| `GET /x/web-interface/newlist?rid=3` | code:0 但 0 条 | 分区最新投稿已废 |
| `GET /x/web-interface/dynamic/region?rid=3` | `-404` | 同上下线 |
| `GET /x/centralization/interface/music/{new/music,original/musics}` | **404** | 想扩音乐榜单只能走版权榜的 list_id |
| `GET /x/space/archive/cursor` | 404 | 已下线 |
| `s.search.bilibili.com/main/{hotword,suggest}` | `data: null` | 热搜词/联想都不可用(印证 bilibili-api.md §12) |

### 三之二、音频区(AM)真实状态 ⚠ 修正

初版本文把音频区写成「整条线已死、端点 404」,**这是推断过度**(搜索为空 → 误推端点下线)。逐端点复测后的准确结论:

| 端点 | 实测 | 状态 |
|---|---|---|
| `GET /audio/music-service-c/s?keyword=` | code:0,`result: []`(中英文关键词皆空) | ❌ **发现入口停摆** |
| `GET /audio/music-service-c/web/menu/hit?pn=&ps=` | code:0,11 个歌单,但 `ctime` 停在 2017、`snum:0` | ⚠ 元数据在、曲目空 |
| `GET /audio/music-service-c/web/menu/info?sid=<menuId>` | **code:0**(实测 menuId=10624) | ✅ 活着 |
| `GET /audio/music-service-c/web/menu/song?sid=&pn=&ps=` | **code:0**(该歌单 0 曲目) | ✅ 活着 |
| `GET /audio/music-service-c/web/song/info?sid=` | **业务码 `4511001 音频未找到或已下架`**(不是 HTTP 404) | ✅ 端点存在 |
| `GET /audio/music-service-c/url?songid=&quality=2` | **业务码 `72000000`**(api 域与 www 域都通) | ⚠ 端点存在,**能否取流未验证**(缺真实可播 sid) |
| `/web/tag`、`/web/rank/list`、`/web/song/recommend`、`/web/song/hot`、`/web/url` | HTTP **404** | ❌ 已下线 |

**结论**:音频区**不能用来发现新内容**(搜索空、歌单曲目被清空),但**读端点还在**。若拿到具体 au 音频 id(来源:关注动态里的音频投稿,动态 `type=12`)仍可能取流——这一条**未实测**,`72000000` 也可能表示权限不足。BiliTune 走的正是这条路(`/audio/music-service-c/url?songid=`),但它同样没有发现入口,只处理动态里已有的音频投稿。

## 四、wbi 签名实测结论

- ✅ **项目内 `remote/WbiSign.kt` 的签名正确**:同一端点 `space/wbi/acc/info` 裸调 `-352 风控校验失败` → 签名后 `code:0`。这是本项目第一次实测验证 wbi(此前只当备用)。
- ⚠ **但签名解不开 WAF**:`space/wbi/arc/search` 签名后依然 `-412`,说明那层是 IP/指纹级风控,不是签名问题。
- ⚠ **`WbiSign.mixinKeyEncTab` 第 33 位起与上游置换表不符**(前 32 位一致)。当前 `getMixinKey` 只取前 32 位,所以**行为正确**,但表本身是坏的:以后若改逻辑(例如不截断、或换实现复用该表)会静默算出错误签名。**参照实现**:BiliTune 的 `bili_wbi_signer.dart` 用的是完整 64 项上游表,可直接照抄。

## 五、功能 → 端点映射(按性价比排序)

1. **无限电台 / 相似歌曲** → `archive/related`(40 条)加进 `bili://` 队列。改动最小,复用现有队列与 resolvers。
2. **最近播放 + 继续播放** → `history/cursor`,`progress` 直接喂 `seekTo`。需登录,但账号侧已就绪。
3. **音乐区排行榜 tab** → `ranking/v2?rid=3`,响应自带 `owner` 与 `stat`,UI 可显示播放量/UP 主。
4. **官方曲库 / 版权歌单** → `copyright-music-publicity` 按 `list_id` 切榜,`creation_first_cid` 可直接播(省一次 view)。
5. **专辑 / 合集播放** → `seasons_series_list` + `seasons_archives_list`(UP 合集),`pagelist`(多 P)。
6. **歌手页** → `space/wbi/acc/info`(签名) + `relation/stat` + `card` + 合集列表;**投稿列表用 `search/type?keyword=<UP名>` 近似**(会被同名视频污染)。
7. **播放页热评** → `reply/main`。
8. **搜索体验** → `search/square`(热搜 chips)+ `search/default`;给现用搜索加 `order`/`duration`/`tids` 筛选。
9. **互动回显** → `archive/relation`(只读,显示已赞/已投币,不做写)。
10. **稍后再看 = 云端歌单** → `toview`(读已通,写未测)。

## 六、参考项目 BiliTune 接口清单(2026-10-09 扫源码)

`github.com/WEP-56/BiliTune`(Flutter,README 里是「B 站音乐播放器」)。⚠ **它的 `docs/B站API使用文档.md` 列了 80+ 端点,但代码实际只调用 26 个**——文档是参考资料,不能当实现清单看。以下按**代码**为准(`lib/core/network/bili_api_endpoints.dart`)。

### 6.1 双方对比

| 端点 | BiliTune | AvSong |
|---|---|---|
| `/x/web-interface/nav` | ✅ | ✅ |
| `/x/web-interface/view` | ✅ | ✅ |
| `/x/v3/fav/*` | ✅ 8 个 | ✅ 6 个 |
| passport qrcode generate/poll | ✅ | ✅ |
| **`/x/web-interface/wbi/search/type`** | ✅ **wbi 版** | ⚠ 非 wbi 版 |
| **`/x/player/wbi/v2`** | ✅ **wbi 版** | ⚠ 非 wbi 版 |
| **`/x/player/wbi/playurl`** | ✅ **wbi 版** | ⚠ 非 wbi 版 |
| `/x/web-interface/wbi/index/top/feed/rcmd` | ✅ 首页推荐 | ➖ 未接入 |
| `/x/web-interface/popular` | ✅ | ➖ |
| `/x/web-interface/ranking/v2` | ✅ | ➖ |
| `/x/web-interface/history/cursor` | ✅ 最近播放 | ➖ |
| `s.search…/hotword`、`/suggest` | ✅ 有方法(实测 data:null 已废) | ➖ |
| passport `cookie/info` + `cookie/refresh` | ✅ **登录态续期** | ➖ **缺** |
| `/audio/music-service-c/web/song/info` + `/url?songid=` | ✅ 音频区 | ➖ |
| `/x/v3/fav/folder/info`、`resource/batch-del`、`folder/edit` | ✅ | ➖(我们用 `folder/update`) |
| `/x/centralization/…/music/comprehensive/web/rank` | ➖ **没有** | ✅ |
| `/x/copyright-music-publicity/toplist/music_list` | ➖ **没有** | ✅(刚发现) |
| `/x/polymer/web-space/seasons_{series_list,archives_list}` | ➖ | ✅(catalog 记录) |
| `/x/v2/reply/main` | ➖(文档列了 `/x/v2/reply`) | ✅(catalog 记录) |
| 网易云歌词 | ➖ **代码里已无**(只剩 LRCLIB + B 站字幕 + 音频区) | ✅ 主力兜底 |

**结论**:它不是「接口更多」,而是**接入取向不同**——BiliTune 是**通用 B 站播放器**(首页推荐/热门/排行/历史/动态,偏视频),AvSong 在**音乐垂直**上更深(音乐榜、版权曲库、合集专辑、热评)。真正的能力缺口只有三处:① **wbi 签名版端点**(它全量签名,我们用旧端点);② **登录态续期**(cookie/refresh);③ **LRCLIB 歌词源**。

### 6.2 值得抄的三样

1. **wbi 签名全覆盖**(`bili_wbi_signer.dart`):密钥缓存 12h、`params` 直接签名。它的完整 64 项置换表可用来修我们的表尾。
2. **LRCLIB 歌词源**(`/api/search` + `/api/get`,免 key):我们只有网易云,多一个源能提高命中率;它把源做成了用户可选(`LyricsSourcePreference.lrclibOnly/lrclibFirst/...`)。
3. **Cookie 续期链路**:`cookie/info` 检查 → RSA 加密 `refresh_{ts}` 得 correspondPath → 从 `www.bilibili.com/correspond/1/{path}` 的 HTML 里抠 `refresh_csrf` → `cookie/refresh` → `confirm/refresh`。SESSDATA 过期后不用重新扫码。

另有两个小工具值得抄:**b23.tv 短链解析**(`redirect: manual` 取 location,见其附录 C —— 我们目前只认识 BV/av 正则,不认短链)、**B 站图片 CDN 参数**(`@480w_270h_1c.webp`,见附录 B)。

### 6.3 ⚠ 它的「功能很多」是文档错觉

`docs/参考项目功能汇总.md`(559 行)列了 30 项功能 + 弹幕/频谱/车载歌词/共享歌单等,**但那是它给 `biu`(Electron)和 `BBPlayer`(React Native)两个别的项目做的调研汇总,不是 BiliTune 自己的实现**。该文件里只有 P0 的 7 项打了 ✅,P1~P3(下载/推荐/历史/点赞投币/本地音乐/频谱/桌面歌词/响度均衡/共享歌单…)全部无勾。扫代码逐项核对,确认如下。

**BiliTune 实际实现**(代码 + 端点表双重确认):

| 能力 | 证据 |
|---|---|
| 搜索(wbi 版)+ 本地搜索历史 | `search/type`、`app_local_store.readSearchHistory` |
| 首页推荐 / 热门 / 排行榜 | `rcmd`、`popular`、`ranking/v2` |
| 最近播放 | `history/cursor` |
| 收藏夹 8 端点(含新建/编辑/删除/批删/信息) | `bili_api_endpoints.dart` |
| 播放模式(顺序/列表循环/单曲/随机) | `PlayRepeatMode{off,all,one}` + `shuffle` |
| 播放速率 | `PlaybackSettings.playbackSpeed` |
| **沉浸式播放器** | `immersive_player.dart` **3625 行**,4 种自绘视觉主题(Fume/Partita/Tilt + 基础),含自绘频谱条 `_AudioBars`、模糊封面背景 |
| 音频区(au)播放 + 音频区歌词 | `_resolveAudioAreaSource`、`_audioAreaLyrics` |
| 歌词:B站字幕 + **LRCLIB** + 音频区 | `_lrclibLyrics`(代码里已无网易云) |
| 扫码登录 + **Cookie 续期** | `cookie/info` + `cookie/refresh` |
| **下载离线** | 17 个文件,并发 3 可配,`downloadedTracks` 把已完成任务变成 `Track(sourceUrl=file://…)` 伪歌单,可直接播本地 |
| 设置页 | `settings_page.dart` **1700 行**:音质偏好/倍速/歌词源/沉浸主题/历史条数/下载目录/并发/缓存清理 |
| Windows 桌面端 | 全局热键、开机自启、GitHub Releases 自动更新、desktop/mobile 双外壳 |

**它的参考文档写了、但代码里没有的**:关注动态(`feed/all` 不在端点表)、稍后再看(无 `toview`)、点赞/投币/三连(无写接口)、弹幕、频谱可视化、逐字歌词(Karaoke)、桌面歌词、车载/状态栏歌词、本地音乐扫描(「本地音乐目录」只是下载目录的设置标签)、定时关闭、均衡器、响度均衡、短信/密码登录、极验验证码、播放上报心跳。

**双方功能差集**:

| BiliTune 有 · AvSong 无 | AvSong 有 · BiliTune 无 |
|---|---|
| 下载离线(最大缺口,ROADMAP 第 2 项) | **双语歌词**(网易云 tlyric 翻译行合并) |
| 沉浸式播放器(4 种自绘主题) | 音乐垂直榜单(music-center 热歌榜 / 版权曲库榜) |
| 播放速率 0.5~2.0x(ROADMAP 第 4 项) | 合集/专辑(seasons) |
| 更多发现入口(推荐/热门/排行/最近播放) | CDN 备用换源自愈 + 播放现场恢复 |
| Cookie 续期 | 队列重排(Media3 `moveMediaItem`) |
| LRCLIB 歌词源 | 歌词多候选 + 时长校验 |
| 设置页 / 音质偏好 | |

⚠ **不要抄沉浸式播放器**:它靠模糊封面背景 + 自绘渐变叠层撑观感,与本项目「纯色扁平:禁止渐变、阴影、边框」的硬约束直接冲突。收益/成本也最差(3625 行)。

⚠ 反向提醒:它文档里 `space/wbi/arc/search`(UP 投稿)、`archive/stat`、`ranking/v2` 的 `rid=28` 子分区都被写成「可用」,但这三处**代码没调用、实测也全部失败**(见第三节)。不要照抄它的文档。


