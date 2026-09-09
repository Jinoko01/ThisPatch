# 1단계 — 외부 API 호출 → 원본 데이터

호출일 2026-09-05 · 테스트 appid `2868840` (Slay the Spire 2) · 모든 예시는 실제 응답입니다.

---

## 확정된 API 목록

| # | API | 키 필요 | 대상 테이블 |
| --- | --- | --- | --- |
| ① | IStoreQueryService/Query | 불필요 | game, game_tag |
| ② | IStoreBrowseService/GetItems | 불필요 | game, game_tag |
| ③ | appreviews | 불필요 | review |
| ④ | ISteamNews/GetNewsForApp | 불필요 | news |
| ⑤ | tagdata/populartags | 불필요 | tag |
| ⑥ | ISteamUser/GetPlayerSummaries | 필요 (무료) | steam |
| ⑦ | appdetails | 사용 안 함 | — |

---

## ① IStoreQueryService/Query

**용도** 스팀 전체 카탈로그를 1000개씩 순회 수집. game / tag / game_tag 의 원본.

**호출 방식** — GET 요청 하나입니다. 요청 본문(body)은 없습니다.

```
GET https://api.steampowered.com/IStoreQueryService/Query/v1/?input_json=<URL인코딩된JSON>
```

아래 JSON을 문자열로 만들어 URL 인코딩한 뒤 `input_json=` 뒤에 붙입니다.

```json
{
  "query": {
    "start": 0,
    "count": 1000,
    "sort": 11,
    "filters": { "type_filters": { "include_games": true } }
  },
  "context": { "language": "koreana", "country_code": "KR", "steam_realm": 1 },
  "data_request": {
    "include_tag_count": 10,
    "include_basic_info": true,
    "include_release": true,
    "include_reviews": true
  }
}
```

**실제로 만들어지는 URL**

```
https://api.steampowered.com/IStoreQueryService/Query/v1/?input_json=%7B%22query%22%3A%20%7B%22start%22%3A%200%2C%20%22count%22%3A%201000...
```

브라우저 주소창에 붙여넣어도 동작합니다. URL 전체 길이 약 570자로, count 를 늘려도 JSON 크기는 그대로라 길이 제한에 걸리지 않습니다.

**파라미터 의미**

| 위치 | 값 | 의미 |
| --- | --- | --- |
| query.start | 0 | 몇 번째부터. 0 → 1000 → 2000 으로 넘기며 순회 |
| query.count | 1000 | 한 번에 가져올 개수. 최대 1000 |
| query.sort | 11 | 정렬 방식 코드. 11 은 인기순 계열 |
| query.filters.type_filters.include_games | true | 게임만. DLC · 데모 · 사운드트랙 제외 |
| context.language | koreana | 이름과 설명을 한국어로 |
| context.country_code | KR | 가격을 원화 기준으로 |
| context.steam_realm | 1 | 글로벌 스팀. 2 는 중국 스팀 |
| data_request.include_tag_count | 10 | 태그 상위 10개까지 포함 |
| data_request.include_basic_info | true | 개발사 · 퍼블리셔 · 설명 포함 |
| data_request.include_release | true | 출시일 포함 |
| data_request.include_reviews | true | 리뷰 요약 포함 |

data_request 스위치를 끄면 응답이 가벼워집니다. 18만 개를 순회할 때 필요 없는 블록을 끄면 그만큼 빨라집니다.

**실제 응답** (store_items 1건 발췌)

```json
{
  "item_type": 0,
  "appid": 2638890,
  "name": "귀무자 Way of the Sword",
  "store_url_path": "app/2638890/귀무자_Way_of_the_Sword",
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

**필드 매핑**

| 응답 필드 | 타입 | 저장 위치 |
| --- | --- | --- |
| appid | int | game.appid |
| name | string | game.name |
| store_url_path | string | game.store_url_path |
| release.steam_release_date | unix초 | game.release_date (DATE 변환) |
| release.is_early_access | bool | game.is_early_access |
| basic_info.developers[].name | string 배열 | game.developer (쉼표로 합쳐 저장) |
| basic_info.publishers[].name | string 배열 | game.publisher (쉼표로 합쳐 저장) |
| basic_info.short_description | string | game.short_description |
| reviews.summary_filtered.review_count | int | game.review_count |
| reviews.summary_filtered.percent_positive | 0~100 | game.percent_positive |
| reviews.summary_filtered.review_score_label | string | game.review_score_label |
| tags[].tagid | int | game_tag.tag_id |
| tags[].weight | int | game_tag.weight |

**측정 결과**

- 전체 게임 수 `metadata.total_matching_records` = **183,758개**
- 1000개/콜 → 전체 순회 약 **184콜**
- 태그 가중치는 이 API가 직접 제공 (SteamSpy 사용하지 않음)

---

### 개발사 · 퍼블리셔 처리 방침

`developers` 와 `publishers` 는 **배열**입니다. 여러 곳이 올 수 있습니다.

**측정 결과 (게임 5,749개 조사)**

| 항목 | 1곳 | 2곳 | 3곳 이상 | 2곳 이상 합계 |
| --- | --- | --- | --- | --- |
| 개발사 | 91.3% | 6.5% | 1.7% | **8.2%** |
| 퍼블리셔 | 88.5% | 8.1% | 1.7% | **9.7%** |

퍼블리셔가 아예 없는(0곳) 게임도 1.8% 있습니다. 최대 13곳까지 확인됐습니다.

**실제 사례**

```
Black Ops 7              개발사 8곳 (Treyarch, Raven, Beenox, High Moon, ...)
ARK: Survival Ascended   개발사 3곳
Civilization VI          개발사 3곳 (Firaxis + Aspyr Mac + Aspyr Linux)
```

**결정 — 별도 테이블로 분리하지 않고 쉼표로 합쳐 저장**

```sql
developer  VARCHAR(500)  NULL  COMMENT '쉼표 구분. developers[] 전체'
publisher  VARCHAR(500)  NULL  COMMENT '쉼표 구분. 1.8%는 퍼블리셔 없음'
```

기존 `VARCHAR(255)` 에서 500 으로 늘립니다. 5,749개 표본 기준 최대 244자였으나 18만 개 전체에서는 더 길 수 있습니다. MySQL VARCHAR 는 실제 길이만큼만 저장하므로 늘려도 비용이 없습니다.

**왜 개발사 테이블을 분리하지 않는가**

분리해서 "이 개발사의 다른 게임" 을 조회하려면 **같은 개발사인지 판정할 키**가 필요한데, 신뢰할 만한 키가 없습니다.

`creator_clan_account_id` 는 스튜디오 ID 가 아니라 **스팀 커뮤니티 허브 ID** 입니다. 보통 퍼블리셔 단위로 묶여 있습니다.

```
ID 36135791 → DICE, Criterion Games, Maxis, Visceral Games, Crytek,
              EA Canada, Electronic Arts ... (총 37개 이름)  = EA 허브

ID 2428135  → 2K Games, 2K Boston, 2K Marin, Gearbox Software ...
ID 32941731 → Team17, Beehive Studios, Expression Games ...
```

이름도 불안정합니다.

```
같은 ID 인데 이름 여러 개  352건 / 고유 ID 2,489개
  CAPCOM → 'Capcom', 'CAPCOM Co., Ltd.', 'CAPCOM CO., LTD',
           'CAPCOM CO., LTD.', 'Capcom U.S.A, Inc.', 'Capcom U.S.A., Inc.'

같은 이름인데 ID 다름     48건 / 고유 이름 3,232개
  'Saber Interactive', 'SEGA', 'Bethesda Softworks' 등
```

ID 로 묶으면 EA 산하 스튜디오가 한 덩어리가 되고, 이름으로 묶으면 CAPCOM 이 6개로 쪼개집니다. 정확한 스튜디오 단위 분석이 불가능하므로 테이블 분리의 이점이 사라집니다.

화면 표시가 길어지는 문제는 프론트에서 처리합니다. 예) `Treyarch 외 7곳`

---

## ② IStoreBrowseService/GetItems

**용도** appid 지정 조회로 리뷰 수 보강. 200개/콜.

**호출 방식** — ① 과 동일하게 GET 이며, JSON 을 URL 인코딩해 `input_json=` 에 넣습니다.

```
GET https://api.steampowered.com/IStoreBrowseService/GetItems/v1/?input_json=<URL인코딩된JSON>
```

```json
{
  "ids": [{ "appid": 2868840 }],
  "context": { "language": "koreana", "country_code": "KR", "steam_realm": 1 },
  "data_request": {
    "include_basic_info": true,
    "include_reviews": true,
    "include_release": true,
    "include_tag_count": 10
  }
}
```

**실제 응답**

```json
{
  "appid": 2868840,
  "name": "Slay the Spire 2",
  "is_early_access": true,
  "reviews": {
    "summary_filtered": {
      "review_count": 182020,
      "percent_positive": 61,
      "review_score_label": "복합적"
    },
    "summary_unfiltered": {
      "review_count": 208024,
      "percent_positive": 57,
      "review_score_label": "복합적"
    },
    "summary_language_specific": {
      "review_count": 5066,
      "percent_positive": 83,
      "review_score_label": "매우 긍정적"
    }
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

**Query 와 다른 점** — 리뷰 요약이 3종류로 나옵니다.

---

### 리뷰 요약 3종의 차이

| 종류 | 실측값 | 의미 |
| --- | --- | --- |
| summary_filtered | 182,020건 · 61% | 오프토픽 리뷰 폭탄 구간 제외. 스토어 표시 점수 |
| summary_unfiltered | 208,024건 · 57% | 아무것도 제외하지 않은 전체 |
| summary_language_specific | 5,066건 · 83% | 요청 언어(koreana)의 리뷰만 |

**스팀의 오프토픽 필터란**

게임 자체가 아니라 게임 외적인 이유로 몰린 리뷰를 자동 감지해 점수 계산에서 제외하는 기능입니다. DRM 도입, EULA 변경, 스토어 독점 계약, 퍼블리셔 논란 등이 대상입니다. 리뷰 자체는 삭제되지 않고 점수 계산에서만 빠집니다.

> 게임플레이 · 밸런스 · 패치 관련 불만은 오프토픽이 아니므로 필터링되지 않습니다. 우리가 분석하려는 패치 반응은 양쪽 수치에 모두 포함되어 있습니다.

**두 수치의 차이 = 오프토픽 폭탄 규모**

```
208,024 − 182,020 = 26,004건 (12.5%)
```

이 게임은 리뷰 8건 중 1건이 게임 외적 이유로 판정됐다는 뜻입니다. 이 격차 자체를 파생 지표로 쓸 수 있습니다.

---

### 우리는 무엇을 쓸 것인가

**summary_unfiltered 를 기준값으로 사용합니다.**

우리는 appreviews API로 리뷰를 직접 수집하는데, 이 API는 오프토픽 필터와 무관하게 전부 반환합니다. 따라서 우리 review 테이블의 행 수는 unfiltered 쪽에 가깝습니다.

```
game.review_count 에 filtered(182,020) 를 넣으면
→ 우리 review 테이블은 208,024 행
→ 화면에는 "리뷰 182,020건" 표시
→ 실제 집계 기준과 숫자가 불일치
```

| 스키마 컬럼 | 넣을 값 |
| --- | --- |
| game.review_count | summary_unfiltered.review_count (기준값) |
| game.review_count_unfiltered | 컬럼명 재검토 후 summary_filtered 저장 |
| game.percent_positive | summary_unfiltered.percent_positive |
| game.review_score_label | summary_filtered.review_score_label |
| summary_language_specific | 저장하지 않음 |

summary_language_specific 을 저장하지 않는 이유는 review 테이블에 language 컬럼이 있어 언제든 계산 가능하기 때문입니다.

is_diagnosable (리뷰 1,000건 이상) 판정도 unfiltered 기준으로 하면 실제 수집량과 일치합니다.

---

## ③ appreviews

**용도** review 테이블 전량. (appid, language) 파티션 + 커서 순회 필수.

**호출**

```
GET https://store.steampowered.com/appreviews/2868840
    ?json=1
    &language=koreana
    &purchase_type=all
    &review_type=all
    &filter=recent
    &num_per_page=100
    &cursor=*
```

**실제 응답**

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

**필드 매핑**

| 응답 필드 | 타입 | 저장 위치 |
| --- | --- | --- |
| recommendationid | 숫자문자열 | review.recommendation_id (BIGINT 저장) |
| author.steamid | 17자리 문자열 | review.steam_id |
| author.playtime_at_review | 분 | review.playtime_at_review |
| language | string | review.language |
| review | string | review.review_text |
| timestamp_created | unix초 | review.timestamp_created |
| timestamp_updated | unix초 | review.timestamp_updated |
| voted_up | bool | review.voted_up |
| votes_up | int | review.votes_up |
| votes_funny | int | review.votes_funny |
| weighted_vote_score | string 또는 float | review.weighted_vote_score |
| comment_count | int | review.comment_count |
| steam_purchase | bool | review.steam_purchase |
| received_for_free | bool | review.received_for_free |
| refunded | bool | review.refunded |
| written_during_early_access | bool | review.written_during_early_access |
| primarily_steam_deck | bool | review.primarily_steam_deck |

**파싱 주의사항**

- recommendationid 는 문자열로 오지만 실측 9자리 숫자 → BIGINT 저장 가능
- weighted_vote_score 는 string 과 float 이 혼재 → 타입 분기 파싱 필수
- review_text 최대 7,798자 실측 → TEXT 타입 필요
- timestamp_created 와 timestamp_updated 가 다르면 is_edited = true

**저장하지 않는 필드**

author.num_games_owned, author.num_reviews, author.playtime_forever, author.playtime_last_two_weeks, author.last_played, persona_status, profile_url, avatar

---

## ④ ISteamNews/GetNewsForApp

**용도** news 테이블. 공지 및 패치노트 전량.

**호출**

```
GET https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/
    ?appid=2868840
    &count=200
    &maxlength=300
```

**실제 응답**

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
        "contents": "{STEAM_CLAN_IMAGE}/44971832/... Hi Slayers, the August Neowsletter is here!...",
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

**필드 매핑**

| 응답 필드 | 타입 | 저장 위치 |
| --- | --- | --- |
| gid | 숫자문자열 | news.gid (PK) |
| title | string | news.title |
| contents | string (HTML 포함) | news.contents |
| url | string | news.url |
| is_external_url | bool | news.is_external_url |
| author | string | news.author |
| date | unix초 | news.published_at |
| feed_type | 0 또는 1 | news.feed_type |
| feedlabel | string | 저장 안 함 |
| feedname | string | 저장 안 함 |

**파싱 주의사항**

- contents 최대 42,471자 실측
- contents 에 `{STEAM_CLAN_IMAGE}` 같은 플레이스홀더가 그대로 포함됨 → 치환 필요
- feed_type 1 = 공식 공지, 0 = 외부 기사

---

### 수집 방침 — 전량 수집 후 분류

**feed_type 으로 거르지 않고 전부 저장합니다.**

패치노트만 모으면 그 게임사가 어떻게 운영하는지를 알 수 없기 때문입니다.

| 공지 종류 | 알 수 있는 것 |
| --- | --- |
| 공식 패치노트 | 무엇을 바꿨는가 |
| 로드맵 · 예고 공지 | 미리 알리는 편인가 |
| 사과문 · 해명 | 문제 발생 시 대응하는가 |
| 이벤트 · 세일 공지 | 운영 활동 빈도 |
| 외부 기사 | 외부에서 어떻게 다뤄지는가 |

**근거 사례**

```
Arrowhead         사과 + 60일 계획 발표 → 상향 전환   → 회복
Dreamsite Games   후속 패치 4회                      → 회복 없음
```

패치를 몇 번 냈는지는 패치노트만 봐도 알 수 있지만, 사과했는지 계획을 공유했는지는 다른 공지를 봐야 알 수 있습니다.

**단계 분리**

- 수집 단계 — feed_type 구분 없이 전량 저장. count 를 크게 잡아 과거 이력까지 확보
- 분류 단계 — 저장된 news 를 후처리하여 종류 판정 (패치노트 / 로드맵 / 사과 / 이벤트 / 외부기사)

**스키마 영향**

현재 news 테이블에는 분류 결과를 담을 컬럼이 없습니다. parse_ok 는 파싱 성공 여부라 의미가 다릅니다. 2단계에서 content_type 같은 분류 결과 컬럼을 추가해야 합니다.

**샘플 확인 결과**

최신 2건이 모두 feed_type 0 이었습니다. count 는 이 게임의 전체 보유 공지 수가 130건임을 의미하므로, 수집 시 count 를 충분히 크게 잡아야 과거 패치노트까지 확보됩니다.

---

## ⑤ tagdata/populartags

**용도** tag 테이블 시드. 1회성 수집.

**호출**

```
GET https://store.steampowered.com/tagdata/populartags/koreana
GET https://store.steampowered.com/tagdata/populartags/english
```

**실제 응답**

```json
[
  { "tagid": 19, "name": "액션" },
  { "tagid": 4182, "name": "..." }
]
```

**필드 매핑**

| 응답 필드 | 저장 위치 |
| --- | --- |
| tagid | tag.tag_id |
| name (koreana 호출) | tag.name_ko |
| name (english 호출) | tag.name_en |

**측정 결과**

- 총 430개 태그
- 언어 코드는 `korean` 이 아니라 `koreana` 가 정확함

---

## ⑥ ISteamUser/GetPlayerSummaries

**용도** steam 테이블. 로그인 회원의 닉네임 및 아바타.

**호출 결과 — API 키 필수 확인됨**

```
GET https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?steamids=76561197960435530
→ HTTP 400 Bad Request (키 없이 호출 불가)
```

**결정 — API 키를 발급받아 이 엔드포인트에만 사용**

| 항목 | 내용 |
| --- | --- |
| 발급처 | https://steamcommunity.com/dev/apikey |
| 비용 | 무료 |
| 조건 | 스팀 계정 로그인 |
| 한도 | 일 100,000 콜 |
| 사용법 | 쿼리 파라미터에 &key=XXXXXXXX 추가 |

로그인 회원의 프로필 표시용으로만 사용하므로 호출량이 매우 적습니다. 일 10만 콜 한도에 도달하지 않습니다.

**키 관리 주의**

- git 에 절대 커밋하지 않을 것
- .env 또는 환경변수로 주입
- .gitignore 에 .env 포함 확인
- application.yml 에 하드코딩 금지

나머지 5개 API 는 키 없이 동작합니다. 대량 수집(리뷰 2억 건, 게임 18만 개)에는 키가 필요 없습니다.

---

## ⑦ appdetails — 사용하지 않음

**폐기된 구상** 게임 소유권을 검증하여 기획안 진단 기능을 해금하는 방식. appdetails 의 website 및 support_info.email 커버리지 98% 를 조사했었음.

**폐기 이유**

스팀 OpenID 로그인은 이 스팀 계정이 본인임을 증명할 뿐, 이 게임의 개발사임은 증명하지 못합니다. 검증할 방법이 없습니다.

**대체 방안**

my_game 을 즐겨찾기(북마크) 기능으로 변경합니다. 회원이 관심 게임을 등록해두는 목록이며 권한 해금과 무관합니다.

**스키마 영향 — my_game 삭제 대상 컬럼**

```sql
is_verified    BOOLEAN
verify_method  VARCHAR(20)
verified_at    TIMESTAMP
```

남는 컬럼은 member_id, appid, added_at 입니다.

**연쇄 확인 필요**

기획안 진단 기능에 내 게임만 가능 같은 권한 제한이 있었다면 그 전제가 사라집니다. 전체 게임 대상으로 열지, plan_type (free / standard / pro) 등 다른 기준으로 제한할지 2단계에서 결정해야 합니다.

---

## 저장 위치 — 어떤 데이터가 어디로 가는가

수집한 원본이 전부 MySQL 로 가는 것은 아닙니다. 세 층으로 나뉩니다.

### 1층 · HDFS (Parquet) — 원본 대용량

| 데이터 | 규모 | 이유 |
| --- | --- | --- |
| 리뷰 전량 | 2억 건 · 약 29GB (Snappy) | MySQL 에 넣으면 55.8GB + 인덱스. 매일 전량 재집계가 불가능 |

`appid` 로 파티셔닝합니다. 게임 단위 집계가 노드 간 셔플 없이 끝납니다.

### 2층 · MySQL — 원본 소규모

| 테이블 | 출처 | 예상 행 수 |
| --- | --- | --- |
| game | Query + GetItems | 183,758 |
| tag | tagdata/populartags | 430 |
| game_tag | Query/GetItems tags[] | 약 180만 (게임당 평균 10개) |
| news | ISteamNews | 게임당 수십~수백 건 |
| member | 서비스 자체 | 회원 수 |
| steam | OpenID | 회원 수 |
| my_game | 사용자 행동 | 회원 × 관심게임 |

### 3층 · MySQL — 집계 결과

2단계에서 다룹니다. game_daily_stat, patch_review_stat, language_stat, game_playtime_band_stat, game_playtime_topic_stat, review_topic, patch_change 등.

---

## 원본 테이블 적재 규칙

### game

**출처** Query (전체 순회) + GetItems (리뷰 수 보강)
**PK** appid
**수집 주기** 매일 1회 전량

재수집 시 review_count · percent_positive 가 계속 변하므로 UPSERT 합니다.

```sql
INSERT INTO game (...) VALUES (...)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  review_count = VALUES(review_count),
  percent_positive = VALUES(percent_positive),
  updated_at = CURRENT_TIMESTAMP;
```

**변환이 필요한 필드**

| 원본 | 변환 | 결과 |
| --- | --- | --- |
| release.steam_release_date | unix초 → DATE | 1772733048 → 2026-03-03 |
| basic_info.developers[] | 배열 → 쉼표 문자열 | ["A","B"] → "A, B" |
| basic_info.publishers[] | 배열 → 쉼표 문자열 | 없으면 NULL (1.8%) |

### tag

**출처** tagdata/populartags/koreana + english (2회 호출)
**PK** tag_id
**수집 주기** 1회 시드 후 가끔 (태그는 거의 안 변함)

한국어와 영어를 각각 호출해 tag_id 로 합칩니다.

### game_tag

**출처** Query/GetItems 의 tags[]
**PK** (appid, tag_id)
**수집 주기** game 갱신 시 함께

태그와 가중치가 바뀌므로, 게임 단위로 **전체 삭제 후 재삽입**이 안전합니다.

```sql
DELETE FROM game_tag WHERE appid = ?;
INSERT INTO game_tag (appid, tag_id, weight, tag_rank) VALUES ...;
```

tag_rank 는 응답 배열 순서(weight 내림차순)를 1부터 매깁니다.

### news

**출처** ISteamNews/GetNewsForApp
**PK** gid
**수집 주기** 매일 (게임별 신규 공지 확인)

공지는 사실상 불변이지만 내용이 수정될 수 있으므로 gid 기준 UPSERT 합니다.

**주의** 재수집 시 분류 결과 컬럼(content_type, parse_ok 등)은 UPDATE 절에서 제외해야 합니다. 후처리로 채운 값이 원본 재수집 때 날아갑니다.

```sql
INSERT INTO news (...) VALUES (...)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  contents = VALUES(contents)
  -- content_type, parse_ok 는 여기 넣지 말 것
```

### 리뷰 (HDFS)

**출처** appreviews · (appid, language) 파티션 순회
**파티션 키** appid
**수집 주기** 미정 (아래 참조)

**변환이 필요한 필드**

| 원본 | 변환 |
| --- | --- |
| recommendationid | 문자열 → BIGINT (실측 9자리) |
| weighted_vote_score | string/float 혼재 → 실수 파싱 |
| timestamp_created ≠ timestamp_updated | → is_edited = true |
| timestamp_updated − timestamp_created | → edit_gap_days |

---

## 미결 사항 2건

### 1. MySQL 의 review 테이블은 존재하는가

리뷰 원본이 HDFS 로 가면서 MySQL 의 review 테이블 위치가 애매해졌습니다. 세 선택지가 있습니다.

| 안 | 내용 | 비용 |
| --- | --- | --- |
| A | MySQL 에 review 테이블 없음. HDFS 만 | 화면에서 원본 리뷰를 못 보여줌 |
| B | 대표 리뷰만 별도 테이블 (review_repr) | 배치가 게임·언어별 상위 10건 추출. 약 75만 행 |
| C | 시연 대상 게임만 전량 적재 | 4~5개 게임 약 200만 행 |

목업에 원본 리뷰를 실시간으로 보여주는 화면이 없으므로 **B 를 권합니다.** 배치가 미리 뽑아두면 화면은 조인 한 번으로 끝납니다.

### 2. 리뷰 재수집 전략

리뷰는 신규 추가뿐 아니라 **수정(4.5% 실측)과 삭제**가 일어납니다.

- 신규 → 추가
- 수정 → timestamp_updated 와 voted_up 이 바뀜. 갱신 필요
- 삭제 → API 응답에서 사라짐. 전량 재수집해야만 감지 가능

전량 재수집은 5.7시간 걸립니다. 매일 돌릴지, 주 1회로 할지, 증분만 받을지 정해야 합니다.

---

## 2단계로 넘길 스키마 변경 사항

| # | 대상 | 변경 내용 |
| --- | --- | --- |
| 1 | game | review_count 를 summary_unfiltered 기준으로 변경 |
| 2 | game | review_count_unfiltered 컬럼명 변경 (summary_filtered 를 넣기로 해 이름이 정반대) |
| 3 | game | developer / publisher 를 VARCHAR(255) → VARCHAR(500), 쉼표 구분 저장 |
| 4 | news | 분류 결과 컬럼 추가 (content_type 등). 수집은 전량, 분류는 후처리 |
| 5 | my_game | is_verified / verify_method / verified_at 삭제 |
| 6 | 기획안 진단 | 권한 기준 재정의 (소유권 검증 폐기에 따른 연쇄) |
