# B 站音乐 API 端点配方(实测记录)

> 本文是数据层的唯一事实来源。所有端点都在 2026-10-07 匿名实测通过(curl + App 双验证)。
> 来源:社区复刻文档 `pskdje/bilibili-API-collect`(原 SocialSisterYi 仓库 2026-01 被法务下架)+ 独立实测。
> **文档与实测有出入时,以本文为准**(出入处已标注 ⚠)。

## 全局要求

| 项 | 值 |
|---|---|
| User-Agent | 桌面 Chrome UA(`BiliConstants.UA`),缺失易被风控 |
| Referer | 按端点不同见下表;API 缺 Referer → 412,直链缺 Referer → 403 |
| buvid3/buvid4 | 搜索/播放必须带(否则 -412);来自 spi 接口,进程级缓存 |
| 登录 | 全部端点匿名可用;音质提升仅对**大会员**生效(普通登录仍 192K,实测确认) |

## 1. 获取 buvid — `/x/frontend/finger/spi`

```
GET https://api.bilibili.com/x/frontend/finger/spi
```
无参数。`data.b_3` → cookie `buvid3`,`data.b_4` → `buvid4`。一次性获取,进程内复用。

## 2. 搜索 — `/x/web-interface/search/type` ⚠ 非 wbi 版

```
GET https://api.bilibili.com/x/web-interface/search/type
    ?search_type=video&keyword=<kw>&page=1&page_size=30&platform=pc
Cookie: buvid3=..; buvid4=..
Referer: https://search.bilibili.com/
```

- ⚠ 文档称已废弃需 wbi 签名,**实测带有效 buvid 时旧端点仍可用**;`wbi/search/all/v2` 也可用但字段更绕。
- ⚠ `search_type=media_bili_mv / bilibili_music` 返回 `code:-1200 被降级过滤`,**不可用**。音乐区歌曲(sid)匿名搜不到 → 统一搜视频(bvid)。
- 响应:`data.result[i]`,字段:
  - `bvid` ✓、`title`(**含 `<em class="keyword">` 高亮标签,必须剥离**)、`author`、`pic`(`//` 开头需补 `https:`)、`duration`(`"3:46"` 字符串)
  - ⚠ **没有 cid** → 播放前必须调 view(§4)

## 3. 热歌榜 — `/x/centralization/interface/music/comprehensive/web/rank`

```
GET https://api.bilibili.com/x/centralization/interface/music/comprehensive/web/rank
Referer: https://music.bilibili.com/pc/music-center
```
响应 `data.list[i]`:`bvid`、`music_title`、`author`、`cover`、`cid`(字符串)。

- ⚠ **榜单给的 cid 是 music-center 内部 id,不是视频真实 cid** —— 直接拿去 playurl 返回 `code:-404 啥都木有`(实测踩坑,文档未载)。**cid 一律以 view 为准**,榜单 cid 只当展示用。
- 备选榜单:`/x/centralization/interface/music/hot/toplist`、`new/music?pn=1&ps=30`、`original/musics`;条目含 `can_listen:false` 的无版权不可播,需过滤。官方版权榜 `/x/copyright-music-publicity/toplist/music_list?list_id=76`。

## 4. 视频详情(补 cid)— `/x/web-interface/view`

```
GET https://api.bilibili.com/x/web-interface/view?bvid=<bvid>
Referer: https://www.bilibili.com/video/<bvid>
```
取 `data.cid`(多 P 视频用 `data.pages[0].cid`),`data.duration`(秒)。

## 5. 音频直链 — `/x/player/playurl` ⚠ 非 wbi 版

```
GET https://api.bilibili.com/x/player/playurl?bvid=<bvid>&cid=<cid>&fnval=4048&platform=pc
Cookie: buvid3=..; buvid4=..
Referer: https://www.bilibili.com/video/<bvid>
```

- ⚠ wbi 版端点(`/x/player/wbi/playurl`)实测该 IP 间歇 412(风控,与签名无关);**旧端点稳定 code:0 且返回结构相同**,主用旧端点。
- 音频在 `data.dash.audio[]`(**未排序**):`id`(音质档)、`baseUrl`(直链)、`backupUrl[]`、`bandwidth`、`codecs`。
- 音质档:`30216`=64K、`30232`=132K、**`30280`=192K(免费最高)**;`30250` 杜比、`30251` Hi-Res 仅大会员。
- ⚠ **fnval 用 4048**(不是 16):16 只回普通 dash.audio;4048 才会带出 `dash.flac`/`dash.dolby`(大会员)。普通账号 4048 也安全(实测仍只回 30216/30232/30280)。
- **选路优先级**:`dash.flac.audio`(无损)> `dash.dolby.audio` 最高带宽 > `dash.audio` 按 bandwidth 取最大。
- ⚠ **`dash.dolby.audio` 常为 JSON `null`(不是缺字段也不是空数组)**,DTO 必须声明为可空 `List<AudioStream>?`,否则 kotlinx `explicitNulls` 解析整个 playurl 抛异常→Source error(实测踩过)。
- 直链有效期 ~3h(URL 里 `deadline`)。过期 403 → 重新调本端点。

## 6. 拉流(ExoPlayer)

```
GET <baseUrl>
Referer: https://www.bilibili.com/        ← 硬性要求,否则 403
User-Agent: <桌面 Chrome UA>
```
无需 Cookie。`platform=html5` 取到的流免 Referer(音质受限,未采用)。

## 7. wbi 签名(备用,当前未启用)

1. `GET /x/web-interface/nav`(未登录 `code:-101` 但 `data.wbi_img` 照样返回,实测确认)
2. `img_key = img_url` 文件名去 `.png`;`sub_key` 同理
3. `mixin_key = (img_key+sub_key)` 按 `MIXIN_KEY_ENC_TAB` 置换表取字符、截 32 位(表在 `remote/WbiSign.kt`)
4. 参数加 `wts`(Unix 秒)后按 key ASCII 排序,值过滤 `!'()*`,URL 编码(`+`→`%20`、`*`→`%2A`、`%7E`→`~`)拼 `query + mixin_key` 做 MD5 → `w_rid`
5. 密钥每日更替,缓存 ~12h

## 8. 实测坑清单(速查)

| 症状 | 原因 | 对策 |
|---|---|---|
| API 412 | 缺 buvid 或 UA 不像浏览器 | spi 取 buvid + 桌面 UA |
| 搜索 -1200 | 用了音乐专用 search_type | 只用 `video` |
| playurl -404 | 用了榜单给的 cid | cid 一律经 view 重取 |
| 直链 403 | 缺 Referer 或链接过期 | Referer 必带;bili:// 现取 |
| 标题带 `<em>` | 搜索高亮标签 | 正则剥离 |
| 封面不显示 | `//` 或 `http://` 协议头 | 统一补成 `https://` |
| 二维码 generate 404 | 用了 api.bilibili.com 域 | **登录端点在 passport.bilibili.com**(见 §9) |

## 9. 扫码登录 — `passport.bilibili.com` ⚠ 域名不同

```
GET https://passport.bilibili.com/x/passport-login/web/qrcode/generate
→ data.url(二维码内容)、data.qrcode_key
Referer: https://www.bilibili.com/
```

```
GET https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=<key>&source=main-fe-header
→ data.code: 0 成功 / 86101 未扫码 / 86090 已扫码待确认 / 86038 已过期
成功时 SESSDATA/bili_jct/DedeUserID 在【Set-Cookie 响应头】里,不在 body
```

- 二维码约 180s 过期;轮询 1.5~2s 一次。
- 登录后 `GET api.bilibili.com/x/web-interface/nav`(此时 code:0)取 `data.uname`、`data.vip.label.text`、`data.vip.vipStatus`。
- Cookie 持久化:DataStore(`data/auth/AuthStore.kt`);注入:`BiliHttp.authCookie` → 拦截器拼进所有请求 Cookie 头(与 buvid 合并)。
- ⚠ 实测:generate 端点在 **api.bilibili.com 域返回 404**,必须走 passport 域。
- 登录后 playurl 的 `dash.audio` 会出现更高档(30232→30280→30251 Hi-Res/30250 杜比,FLAC 在 `data.dash.flac`)。`maxByOrNull { bandwidth }` 能自动选中 audio 里最高档;**但 FLAC/Hi-Res 可能需要更大的 fnval 位域(如 4048)才返回,待登录后实测**。
- ✅ 实测(普通登录用户,非大会员):playurl 返回档位与匿名相同,上限仍 30280=192K。**音质提升仅对大会员生效**,登录本身不解锁 320K。
- 实现:`data/auth/QrLogin.kt`(generate/poll/fetchProfile)。

## 10. 歌词(第三方:网易云公开接口)

B 站视频源无官方歌词接口(`/x/music/lyric` 仅音乐区 sid,匿名搜不到 sid)。采用 MusicFree/listen1 同款方案——用"歌名+歌手"搜网易云:

```
GET https://music.163.com/api/search/get/web?s=<title artist>&type=1&offset=0&limit=5
→ result.songs[0].id
GET https://music.163.com/api/song/lyric?id=<sid>&lv=1&kv=1&tv=-1
→ lrc.lyric(LRC 格式文本)
```
Header 只需桌面 UA + `Referer: https://music.163.com/`。**不要带 B 站 Cookie**(独立 OkHttpClient,防凭据外泄)。

- 实测通过(2026-10-07):"I'm Every Woman Whitney Houston" → id 3819491 → 完整 LRC。
- ⚠ 中文歌名 + adb input 注入测试时编码易错,真机手输正常。
- ⚠ 公开接口无 SLA,可能限流/失效;失败时负缓存(当次进程不重试),UI 显示"暂无歌词"。
- ⚠ 匹配是"歌名+歌手"模糊搜索,翻唱/纯音乐可能拿错版本;无匹配时回退只搜歌名。
- 实现:`data/lyric/LyricRepository.kt`(搜索→取词→LRC 解析→缓存);UI 在播放页点封面切换,当前行高亮、点击 seek。

## 10b. 歌词多源(借鉴 BiliTune,2026-10-07 升级)

`LyricRepository.fetch(bvid, title, artist, durationSec)` 源链:
1. **B 站视频字幕**:`/x/player/v2?bvid=&cid=`(⚠ **非-wbi 版实测可用**,返回 `data.subtitle.subtitles[]` + `data.bgm_info`)→ 选中文轨(`lan` 含 zh)→ 拉 `subtitle_url`(指向 `aisubtitle.hdslb.com`,带 auth_key,需 Referer=视频页)→ 解析 `body[].from/to/content`(秒浮点)→ LyricLine
   - ⚠ **AI 字幕需登录 Cookie**(匿名 subtitles 常为空);BiliHttp 已注入登录态,实测命中
2. **bgm_info 增强**:`music_title`("发现《XXX》" 清洗取 XXX)、`music_singer` 作为优先搜索词
3. **网易云多候选**:标题(bgm→原→清洗去【】[]去尾巴)× 歌手(bgm→原→逗号前→空)组合,首个时长差 ≤15s 的命中取词
- 缓存键改为 bvid(含负缓存);网易云用独立 client 不带 B 站 Cookie。
- 实测:有 AI 字幕的歌走源1;无字幕自动降级源3,请求链 player/v2→(空)→163 search→lyric 全 200。

## 11. 收藏夹(= 歌单)— `/x/v3/fav/*`

登录后使用。读接口带登录 Cookie 可看私密夹;写接口(form POST)必须带 `csrf=bili_jct`。实测(2026-10-07)全部 code:0。

```
# 我创建的收藏夹
GET api.bilibili.com/x/v3/fav/folder/created/list?up_mid=<mid>&type=2&pn=1&ps=20  (展示用,带封面)
GET api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=<mid>&type=2        (轻量,⚠ 实测 cover 全为 null)
→ data.list[i]:{id, fid, mid, attr, title, media_count, fav_state, cover} + data.has_more(仅 list)
  · attr==0 公开,非0 私密(显示锁)
  · 加 &rid=<aid>&biz_type=2 查询时,fav_state:1=已收藏 0=未收藏(用于弹窗回显勾选;此场景用 list-all 即可,只需要 id/title)
  · 2026-10-11 实测:歌单网格卡片全无封面,根因是 list-all 不回 cover;换 created/list(网页 favlist 同款)后正常

# 我订阅的收藏夹
GET api.bilibili.com/x/v3/fav/folder/collected/list?up_mid=<mid>&pn=1&ps=20
→ data.list[i] 同上 + upper.name(创建者)

# 收藏夹内容
GET api.bilibili.com/x/v3/fav/resource/list?media_id=<folderId>&pn=1&ps=20&keyword=
→ data.medias[i]:{id(=aid!), bvid, title, cover, duration, upper.name} + data.has_more
  · ⚠ medias 里没有独立 aid 字段,id 就是 aid(收藏/取消要用)

# 写(form POST,带 csrf)
POST /x/v3/fav/folder/add          title/intro/privilege(0公开1私密) → data.id
POST /x/v3/fav/folder/update       pubid/title/intro
POST /x/v3/fav/folder/del          pubid
POST /x/v3/fav/resource/deal       rid=<aid>&type=2&add_media_ids=<ids>&del_media_ids=<ids>
                                   (一次请求可同时加进多个夹/从多个夹移出)
```

- 收藏操作要 **aid**(bvid 不行)。搜索结果/榜单/收藏夹内容尽量带上 aid;缺失时经 `/x/web-interface/view` 补(`FavoriteRepository.fetchAid`)。
- 写接口 Referer 用 `https://api.bilibili.com/`,Origin 用 `https://www.bilibili.com`。
- 实现:`data/FavoriteRepository.kt`(object 依赖 AuthStore 取 bili_jct/mid);UI 在 `ui/library/`(列表页/内容页/播放页收藏弹窗)。

## 12. 搜索增强(借鉴 BiliTune)

- **BV/av 直达**:输入框内容正则匹配 `BV[0-9A-Za-z]{10}` 或 `av\d+` → 直接调 `/x/web-interface/view?bvid=|aid=` 解析成单首(不走 search)。av→bvid 也经 view 的 `data.bvid`。分享链接文本同样命中(BV 正则会从长文本里提取)。
- **分页**:`/x/web-interface/search/type` 的 `page` 参数已支持;`data.numPages` 判 hasMore。UI 滚到底部触发 `loadMoreSearch` 追加去重。
- **本地搜索历史**:纯本地 DataStore(单分隔串保序,去重、最新在前、上限 20),不碰官方云端"最近搜索"。
- ⚠ **搜索建议端点 `s.search.bilibili.com/main/suggest` 实测返回 code:3 空**(需特定上下文/签名,不稳定),暂不做下拉建议。
- 实现:`data/BiliSongRepository.resolveDirect()`、`data/SearchHistoryStore.kt`、`ui/search/SearchScreen`(HistoryChips + 分页 SongList)。

## 13. 发现区四源(首页 chips,2026-10-09 加)

首页顶部 5 个胶囊切换数据源。热歌榜沿用 §3,其余四个实测配方:

```
# 推荐(rcmd 推荐流)⚠ 路径带 wbi 但实测匿名无需签名
GET /x/web-interface/wbi/index/top/feed/rcmd?ps=20&fresh_type=4
Referer: https://www.bilibili.com
→ data.item[](注意是 item 不是 list):bvid,cid,title,pic,duration(秒),owner{mid,name,face}
· 内容是全站泛推荐(旅游/时政/娱乐都有),不是音乐垂直;每次请求结果会变

# 热门(全站综合热门,可翻页)
GET /x/web-interface/popular?pn=1&ps=20
→ data.list[] 同上字段 + data.no_more(⚠ true 才是没有下一页)

# 排行(音乐区排行榜)
GET /x/web-interface/ranking/v2?rid=3&type=all
Referer: https://www.bilibili.com/v/music/
→ data.list[] 同上字段,约 96~100 条
· ⚠ 本接口只认 rid=0(全站)/3(音乐),音乐子分区 28/31/30/59/193/29/130/194 一律 -400

# 最近播放(播放历史,需登录)
GET /x/web-interface/history/cursor?ps=30&max=0&view_at=0
Referer: https://www.bilibili.com/account/history
→ data.list[]:title,cover,history{bvid,cid,page,business},progress,duration,author_name,view_at
· ⚠ ps 上限 30,传 50 → code:-400
· ⚠ progress 为 -1 表示无进度;只有 progress ∈ (0, duration) 才作为起播位置
· ⚠ 条目含直播/番剧等非视频(`history.bvid` 为空),必须过滤掉
· 未登录 → code:-101(仓库抛 NotLoggedInException,UI 显示「点这里扫码登录」)
```

**三条通用结论**:

1. **cid 一律不用**:这三个 view 系接口虽然回传 cid,但播放永远经 `bili://` → view 现取(§4/§5),与搜索路径一致,天然规避 §8 的「榜单 cid 陷阱」。
2. **duration 是秒**(整数),与搜索接口的 `"3:46"` 字符串不同,不要走 `parseDuration`。
3. **列表 key 用 bvid,LazyColumn 重复 key 会崩**:历史记录里同一视频多看几个分P会有多条同 bvid 记录 → 入状态前统一 `distinctBy { it.bvid }`。

**续播**:`history.progress` 秒数写进 `Song.resumePositionMs` → `PlayerController.playQueue(items, index, startPositionMs)` 的第三参(`setMediaItems` 起播位置),不额外 seek。「听到 00:26」这类提示单独走 `Song.progressHint`,**不要拼进 artist** —— 否则播放器/通知栏也会挂着它。

## 14. UP 主合集(= 歌单,2026-10-10 加)

「我的」页支持粘贴合集链接当歌单。两个实测端点,均匿名可用:

```
# 合集内容(一次给 meta + 当前页曲目,翻页只换 page_num)
GET /x/polymer/web-space/seasons_archives_list?mid=<UP的mid>&season_id=<合集id>&page_num=1&page_size=20
Referer: https://space.bilibili.com/<mid>/
→ data.meta:{season_id,mid,name,title,total,cover}
   data.archives[]:{aid,bvid,title,pic,duration(秒)}
   data.page:{page_size,total}
· ⚠ archives 条目**没有 owner 字段**,cid 也可能是 null —— UP 名按 mid 另取,cid 不用
· 链接形态:space.bilibili.com/<mid>/lists/<sid>?type=season(分享文本直接正则提取)
· ⚠ type=series 是「系列」,走另一套接口(/x/series/archives),未接,解析时明确报错

# UP 名(补 owner 用)
GET /x/web-interface/card?mid=<mid>
→ data.name
· ⚠ 对新版大 mid(如 3493093607213343)实测返回 code:0 但 name=null;
  老端点 /x/space/acc/info 对其 -799 限频,wbi 版 space/wbi/acc/info -352 风控。
  → UP 名只能 best-effort:拿得到就显示,拿不到 UI 留空(副标题只显示时长,不要渲染悬空的「· 」)
· 合集不存在/已删除:code:0 但 data 为 null → 当错误处理
```

**产品结论**:B 站没有「我订阅的合集」列表接口(自己 mid 的 `seasons_series_list` 返回的是**自建合集**,实测确认),所以订阅合集走本地方案:`CollectionStore`(DataStore)保存 (mid, seasonId, title, cover, total),最新在前,按 (mid, seasonId) 去重;添加时先拉一页 meta 校验合集存在并取标题/封面/总数。实现:`data/CollectionRepository.kt` + `ui/library/CollectionContentScreen.kt`。

**三条坑**:① 曲目 `duration` 是秒;② 列表 key 用 bvid,加载合并时 `distinctBy { it.bvid }`(大合集翻页可能重复);③ `hasMore = total > pageNum * pageSize`,别依赖 archives 条数。

