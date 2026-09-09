# 1단계 — 외부 API 호출 → 원본 데이터

모든 예시는 실제 호출 결과입니다. 호출일: 2026-09-05, 테스트 대상 appid: `2868840` (Slay the Spire 2).
원본 JSON 전체는 `scratchpad/api_samples/*.json`에 저장되어 있습니다 (재현 가능).

---

## ① IStoreQueryService/Query — 게임 목록 벌크 수집

**용도**: 스팀 전체 카탈로그를 1000개씩 순회하며 긁는다. `game`, `tag`, `game_tag`의 원본.

**호출**
```
GET https://api.steampowered.com/IStoreQueryService/Query/v1/?input_json=<URL인코딩된JSON>

input_json = {
  "query": {
    "start": 0, "count": 1000, "sort": 11,
    "filters": { "type_filters": { "include_games": true } }
  },
  "context": { "language": "koreana", "country_code": "KR", "steam_realm": 1 },
  "data_request": {
    "include_tag_count": 10, "include_basic_info": true,
    "include_release": true, "include_reviews": true
  }
}
```

**실제 응답 (store_items[0] 발췌, 게임 1건)**
```json
{
  "item_type": 0,
  "appid": 2638890,
  "name": "귀무자 Way of the Sword",
  "store_url_path": "app/2638890/귀무자_Way_of_the_Sword",
  "tagids": [19, 4182, 4106, 4608, 29482, ...],
  "tags": [
    { "tagid": 1716, "weight": 986 },
    { "tagid": 1666, "weight": 903 }
  ],
  "reviews": {
    "summary_filtered": {
      "review_count": 1930,
      "percent_positive": 86,
      "review_score": 8,
      "review_score_label": "매우 긍정적"
    }
  },
  "basic_info": {
    "short_description": "...",
    "publishers": [{ "name": "...", "creator_clan_account_id": 12345 }],
    "developers": [{ "name": "...", "creator_clan_account_id": 12345 }]
  },
  "release": {
    "steam_release_date": 1772733048,
    "is_early_access": true
  }
}
```

**필드 → 용도**

| 필드 | 타입 | 용도 |
|---|---|---|
| `appid` | int | `game.appid` |
| `name` | string | `game.name` |
| `store_url_path` | string | `game.store_url_path` |
| `release.steam_release_date` | unix초 | `game.release_date` (DATE로 변환) |
| `release.is_early_access` | bool | `game.is_early_access` |
| `basic_info.developers[].name` | string | `game.developer` |
| `basic_info.publishers[].name` | string | `game.publisher` |
| `basic_info.short_description` | string | `game.short_description` |
| `reviews.summary_filtered.review_count` | int | `game.review_count` |
| `reviews.summary_filtered.percent_positive` | 0~100 | `game.percent_positive` |
| `reviews.summary_filtered.review_score_label` | string | `game.review_score_label` |
| `tags[].tagid` + `.weight` | int | `game_tag.tag_id`, `game_tag.weight` — **★ 확인됨: 태그 가중치는 이 API가 직접 준다. SteamSpy 아님** |
| `tagids` | int[] | `tags[]`와 동일 id, weight 없는 단순 목록 (미사용) |

**측정된 특성**
- 총 게임 수 `metadata.total_matching_records` = **183,758개** (2026-09-05 기준)
- 1000개/콜, 전체 순회 시 약 184콜

---

## ② IStoreBrowseService/GetItems — 게임 상세 보강

**용도**: Query보다 리뷰 수 정확도가 높음(유료 게임 언더카운트 보정 확인됨). appid 지정 조회, 200개/콜.

**호출**
```
GET https://api.steampowered.com/IStoreBrowseService/GetItems/v1/?input_json=<URL인코딩된JSON>

input_json = {
  "ids": [{ "appid": 2868840 }],
  "context": { "language": "koreana", "country_code": "KR", "steam_realm": 1 },
  "data_request": {
    "include_basic_info": true, "include_reviews": true,
    "include_release": true, "include_tag_count": 10
  }
}
```

**실제 응답 (appid 2868840, 전체)**
```json
{
  "appid": 2868840,
  "name": "Slay the Spire 2",
  "is_early_access": true,
  "reviews": {
    "summary_filtered":   { "review_count": 182020, "percent_positive": 61, "review_score": 5, "review_score_label": "복합적" },
    "summary_unfiltered": { "review_count": 208024, "percent_positive": 57, "review_score": 5, "review_score_label": "복합적" },
    "summary_language_specific": { "review_count": 5066, "percent_positive": 83, "review_score": 8, "review_score_label": "매우 긍정적" }
  },
  "basic_info": {
    "short_description": "로그라이크 덱 빌딩 게임의 상징이 돌아왔습니다! ...",
    "publishers": [{ "name": "Mega Crit", "creator_clan_account_id": 33974508 }],
    "developers": [{ "name": "Mega Crit", "creator_clan_account_id": 33974508 }]
  },
  "tags": [
    { "tagid": 1716, "weight": 986 },
    { "tagid": 1666, "weight": 903 }
  ],
  "release": { "steam_release_date": 1772733048, "is_early_access": true }
}
```

**Query와 다른 점**: `reviews`에 요약이 **3종류** 들어옴. Query는 `summary_filtered` 하나뿐.

### 리뷰 요약 3종의 차이 (실측값 = Slay the Spire 2)

| 종류 | 실측 | 뜻 |
|---|---|---|
| `summary_filtered` | **182,020건 · 61%** | 스팀이 **"오프토픽 리뷰 폭탄"으로 판정한 구간을 제외**한 수치. 스토어 페이지에 표시되는 공식 점수 |
| `summary_unfiltered` | **208,024건 · 57%** | 아무것도 제외하지 않은 **전체** |
| `summary_language_specific` | **5,066건 · 83%** | 요청 시 `context.language`로 지정한 언어(여기선 `koreana`)의 리뷰만 |

**스팀의 "오프토픽 필터"란**: 게임 자체가 아니라 **게임 외적인 이유**로 몰린 리뷰를 자동 감지해 점수 계산에서 빼는 기능입니다. DRM 도입, EULA 변경, 스토어 독점 계약, 퍼블리셔 논란 같은 게 대상입니다. 리뷰 자체는 삭제되지 않고 **점수 계산에서만 빠집니다.**

**중요**: 게임플레이·밸런스·패치 관련 불만은 오프토픽이 **아니므로 필터링되지 않습니다.** 우리가 분석하려는 패치 반응은 양쪽 수치에 다 들어있습니다.

**두 수치의 차이 = 오프토픽 폭탄 규모**
```
208,024 − 182,020 = 26,004건 (12.5%)
```
이 게임은 리뷰 8건 중 1건이 "게임 외적 이유"로 판정됐다는 뜻입니다. **이 격차 자체가 파생 지표로 쓸 만합니다** — 게임 외적 논란을 겪은 게임인지 알려줍니다.

### 우리는 뭘 써야 하나

**`summary_unfiltered`를 기준으로 쓰는 걸 권합니다.**

이유는 **우리 `review` 테이블과 숫자가 맞아야** 하기 때문입니다. 우리는 `appreviews` API로 리뷰를 직접 긁는데, 이건 **오프토픽 필터와 무관하게 전부 반환**합니다. 그래서 우리 DB 리뷰 행 수는 unfiltered 쪽에 가깝습니다.

```
game.review_count 에 filtered(182,020) 를 넣으면
  → 우리 review 테이블은 208,024 행
  → 화면에서 "리뷰 182,020건"인데 실제 집계는 208,024건 기준
  → 숫자가 안 맞아 보임
```

| 스키마 컬럼 | 넣을 값 |
|---|---|
| `game.review_count` | `summary_unfiltered.review_count` ← **기준값** |
| `game.review_count_unfiltered` | → **이름 바꾸는 게 나음.** `review_count_steam_display` 등으로 하고 `summary_filtered` 저장 |
| `game.percent_positive` | `summary_unfiltered.percent_positive` |
| `game.review_score_label` | `summary_filtered.review_score_label` (스팀이 표시하는 배지 그대로) |
| `summary_language_specific` | **저장 안 함** — 우리 `review` 테이블에 `language` 컬럼이 있어서 언제든 계산 가능 |

`is_diagnosable` (리뷰 1,000건 이상) 판정도 unfiltered 기준으로 하면 우리가 실제 수집하는 양과 일치합니다.

**필드 → 용도**: ①과 동일 (같은 스키마 응답). 다만 이 API로 **appid 목록을 지정 조회**하므로, ①에서 받은 appid 전체를 배치로 다시 조회해 리뷰 수를 보정하는 용도로 씀.

---

## ③ appreviews — 리뷰 원문

**용도**: `review` 테이블 전량. `(appid, language)` 파티션 + 커서 순회 필수.

**호출**
```
GET https://store.steampowered.com/appreviews/2868840
    ?json=1&language=koreana&purchase_type=all&review_type=all
    &filter=recent&num_per_page=100&cursor=*
```

**실제 응답 (reviews[0], 리뷰 1건)**
```json
{
  "success": 1,
  "query_summary": {
    "num_reviews": 2,
    "total_positive": 4410,
    "total_negative": 858,
    "total_reviews": 5268
  },
  "reviews": [
    {
      "recommendationid": "234499291",
      "author": {
        "steamid": "76561199485804057",
        "num_games_owned": 23,
        "num_reviews": 5,
        "playtime_forever": 3684,
        "playtime_last_two_weeks": 1329,
        "playtime_at_review": 3624,
        "last_played": 1788582368
      },
      "language": "koreana",
      "review": "아니 이새끼들은 사마귀년 무작위라고 하는데...",
      "timestamp_created": 1788577765,
      "timestamp_updated": 1788577765,
      "voted_up": false,
      "votes_up": 0,
      "votes_funny": 0,
      "weighted_vote_score": 0.5,
      "comment_count": 0,
      "steam_purchase": true,
      "received_for_free": false,
      "refunded": false,
      "written_during_early_access": true,
      "primarily_steam_deck": false
    }
  ]
}
```

**필드 → 용도**

| 필드 | 타입 | 용도 |
|---|---|---|
| `recommendationid` | 숫자문자열 | `review.recommendation_id` (BIGINT로 저장, 실측 9자리) |
| `author.steamid` | 17자리 문자열 | `review.steam_id` |
| `author.playtime_at_review` | 분 | `review.playtime_at_review` |
| `language` | string | `review.language` |
| `review` | string | `review.review_text` (최대 7,798자 실측) |
| `timestamp_created` / `timestamp_updated` | unix초 | `review.timestamp_created/updated` — 둘이 다르면 `is_edited=true` |
| `voted_up` | bool | `review.voted_up` |
| `votes_up` / `votes_funny` | int | 그대로 |
| `weighted_vote_score` | string 또는 float 혼재 | `review.weighted_vote_score` — **파싱 시 타입 분기 필수** |
| `comment_count`, `steam_purchase`, `received_for_free`, `refunded`, `written_during_early_access`, `primarily_steam_deck` | — | 그대로 |

**여기서 안 쓰는 필드** — `author.num_games_owned`, `author.num_reviews`, `author.playtime_forever`, `author.playtime_last_two_weeks`, `author.last_played`, `persona_status`, `profile_url`, `avatar` : 지금 스키마에 저장 대상 아님. 필요하면 추가 논의.

---

## ④ ISteamNews/GetNewsForApp — 공지·패치노트

**용도**: `news` 테이블. `feed_type=1`이 공식 공지(패치노트 후보), `0`은 외부 기사.

**호출**
```
GET https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/
    ?appid=2868840&count=2&maxlength=300
```

**실제 응답 (newsitems, 2건)**
```json
{
  "appnews": {
    "appid": 2868840,
    "newsitems": [
      {
        "gid": "1840944183781963",
        "title": "Slay the Spire 2 updates are slowing down again...",
        "url": "https://steamstore-a.akamaihd.net/news/externalpost/PCGamesN/...",
        "is_external_url": true,
        "author": "editor@pcgamesn.com",
        "contents": "In its August Slay the Spire 2 Neowsletter...",
        "feedlabel": "PCGamesN",
        "date": 1786787414,
        "feedname": "PCGamesN",
        "feed_type": 0
      },
      {
        "gid": "1840944183781031",
        "title": "The Neowsletter - August 2026",
        "url": "https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/...",
        "is_external_url": true,
        "author": "demileaf",
        "contents": "{STEAM_CLAN_IMAGE}/44971832/...\nHi Slayers, the August Neowsletter is here!...",
        "feedlabel": "Community Announcements",
        "date": 1786751537,
        "feedname": "steam_community_announcements",
        "feed_type": 1
      }
    ],
    "count": 130
  }
}
```

**필드 → 용도**

| 필드 | 타입 | 용도 |
|---|---|---|
| `gid` | 숫자 문자열 | `news.gid` (PK) |
| `title` | string | `news.title` |
| `contents` | string, HTML 포함 | `news.contents` (최대 42,471자 실측) — **`{STEAM_CLAN_IMAGE}` 같은 플레이스홀더가 그대로 들어옴. 파싱 시 치환 필요** |
| `url` | string | `news.url` |
| `is_external_url` | bool | `news.is_external_url` |
| `author` | string | `news.author` |
| `date` | unix초 | `news.published_at` |
| `feed_type` | 0/1 | `news.feed_type` — **1만 패치노트 후보로 사용** |
| `feedlabel`, `feedname` | string | 미저장 (현재 스키마에 없음) |

### 수집 방침: 전부 가져오고, 분류는 나중에 (결정됨)

**`feed_type`으로 거르지 않고 전부 저장합니다.** 패치노트만 모으면 그 게임사가 **어떻게 운영하는지**를 알 수 없기 때문입니다.

```
공식 패치노트     →  뭘 바꿨나
로드맵 · 예고 공지  →  미리 알리는 편인가
사과문 · 해명       →  문제 생겼을 때 대응하는가
이벤트 · 세일 공지  →  운영 활동 빈도
외부 기사          →  외부에서 어떻게 다뤄지나
```

앞서 조사한 사례에서 이게 결정적이었습니다.

```
Arrowhead        사과 + 「60일 계획」 발표 → 상향 전환   → 회복
Dreamsite Games  후속 패치 4회                          → 회복 없음
```

**"패치를 몇 번 냈나"는 패치노트만 봐도 알지만, "사과했나 / 계획을 공유했나"는 다른 공지를 봐야 알 수 있습니다.**

**수집 단계**: `feed_type` 0·1 구분 없이 전량 저장. `count`를 크게 잡아 과거 이력까지 확보.
**분류 단계**: 저장된 `news`를 나중에 처리해서 종류를 판정 (패치노트 / 로드맵 / 사과 / 이벤트 / 외부기사).

**⚠ 스키마 영향**: 지금 `news` 테이블에는 **분류 결과를 담을 컬럼이 없습니다.** `parse_ok`(파싱 성공 여부)는 다른 의미입니다. 2단계에서 `content_type` 같은 분류 결과 컬럼을 추가해야 합니다.

**샘플에서 확인된 것**: 최신 2건이 둘 다 `feed_type=0`(PCGamesN 기사, 커뮤니티 뉴스레터)이었습니다. `count=130`이 전체 보유량이므로, 수집 시 `count`를 충분히 크게 잡아야 과거 패치노트까지 확보됩니다.

---

## ⑤ tagdata/populartags/koreana·english — 태그 사전 시드

**용도**: `tag.name_ko`, `tag.name_en`. 1회성 시드 (전체 태그 목록).

**호출**
```
GET https://store.steampowered.com/tagdata/populartags/koreana
GET https://store.steampowered.com/tagdata/populartags/english
```

**실제 응답 (앞부분)**
```json
[
  { "tagid": 19, "name": "액션" },
  { "tagid": 4182, "name": "..." }
]
```

**측정**: 총 **430개** 태그 (koreana). `korean`이 아니라 `koreana`가 정확한 언어 코드 (이전에 확인됨).

---

## ⑥ ISteamUser/GetPlayerSummaries — ⚠ 키 없이 400 에러 확인

**용도(가정)**: `steam.persona_name`, `steam.avatar_url`, `steam.profile_url`.

**호출 결과**
```
GET https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?steamids=76561197960435530
→ HTTP 400 Bad Request (API 키 필수, 키 없이 호출 불가)
```

**결정: API 키를 발급받아 이 엔드포인트만 씁니다.**

**비용 없음 · 무료**입니다.

```
발급   https://steamcommunity.com/dev/apikey
조건   스팀 계정 로그인 (계정에 구매 이력 필요할 수 있음)
비용   무료
한도   일 100,000 콜
사용   쿼리 파라미터로 &key=XXXXXXXX 추가
```

우리는 **로그인한 회원의 프로필 표시용으로만** 쓰므로 호출량이 극히 적습니다 (회원 수 × 로그인 횟수). 일 10만 콜 한도에 전혀 안 걸립니다.

**⚠ 키 관리 주의**
```
절대 git 에 커밋하지 말 것
→ .env 또는 환경변수로 주입
→ .gitignore 에 .env 추가 확인
→ application.yml 에 하드코딩 금지
```

**나머지 5개 API는 여전히 키 없이 동작합니다.** 대량 수집(리뷰 2억 건, 게임 18만 개)에는 키가 필요 없고, 키는 회원 프로필 조회에만 씁니다.

---

## ⑦ appdetails (소유권 검증) — ❌ 사용 안 함 (결정됨)

예전에 "게임 소유권을 검증해서 기획안 진단을 해금한다"는 구상으로 `appdetails`의 `website` / `support_info.email` 커버리지를 조사했었습니다 (98% 노출 확인). **이 기능은 폐기합니다.**

**이유**: 스팀 OpenID 로그인은 **"이 스팀 계정이 본인이다"만 증명**하고, **"이 게임의 개발사다"는 증명하지 못합니다.** 검증할 방법이 없습니다.

**대체**: `my_game`은 **즐겨찾기(북마크)** 기능으로 갑니다. 회원이 관심 게임을 등록해두는 목록일 뿐, 권한 해금과 무관합니다.

**⚠ 스키마 영향** — `my_game`에서 다음 컬럼 삭제 대상:
```sql
is_verified    BOOLEAN      -- 삭제
verify_method  VARCHAR(20)  -- 삭제
verified_at    TIMESTAMP    -- 삭제
```
남는 건 `member_id`, `appid`, `added_at` 뿐입니다.

**연쇄 확인 필요**: 기획안 진단 기능에 "내 게임만 가능" 같은 권한 제한이 걸려 있었다면, 그 전제가 사라집니다. 전체 게임 대상으로 열지, 아니면 `plan_type`(free/standard/pro) 같은 다른 기준으로 제한할지 2단계에서 정해야 합니다.

---

## 1단계 결론

**API 6개 확정** (5개 키 불필요 + 1개 키 필요)

| # | API | 키 | 대상 테이블 |
|---|---|---|---|
| ① | IStoreQueryService/Query | 불필요 | `game`, `game_tag` |
| ② | IStoreBrowseService/GetItems | 불필요 | `game`, `game_tag` |
| ③ | appreviews | 불필요 | `review` |
| ④ | ISteamNews/GetNewsForApp | 불필요 | `news` (전량 수집) |
| ⑤ | tagdata/populartags | 불필요 | `tag` |
| ⑥ | ISteamUser/GetPlayerSummaries | **필요(무료)** | `steam` |
| ⑦ | ~~appdetails~~ | — | **사용 안 함** |

### 2단계로 넘길 스키마 변경 사항

1. `game.review_count` ← `summary_unfiltered` 기준으로 변경, `review_count_unfiltered` 컬럼명 재검토
2. `news`에 **분류 결과 컬럼 추가** (`content_type` 등) — 수집은 전량, 분류는 후처리
3. `my_game`에서 `is_verified` / `verify_method` / `verified_at` **삭제**
4. 기획안 진단 기능의 **권한 기준 재정의** (소유권 검증 폐기에 따른 연쇄)
