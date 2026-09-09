# 노션 수정용 — 바꿀 절만 모음

각 블록을 노션의 해당 절과 통째로 바꾸시면 됩니다.

순서는 문서 앞에서 뒤 순서입니다.


============================================================================
  A. 저장 구조  —  제목부터 「각 저장소의 역할」까지 통째 교체
============================================================================

## 저장 구조 — 전량은 파일, 화면에 쓸 것만 DB

리뷰는 두 곳에 저장합니다. 나누는 것이 아니라 **전량을 파일에 두고 일부를 DB에 복사**합니다.

```
HDFS (Parquet)        리뷰 전량 1.5억 건
       │
       │ 배치가 읽어서 집계
       ↓
PostgreSQL            집계 결과
                      + 최신 패치의 패치 후 7일 리뷰 (약 54만 건)
       │
       │ 화면이 조회
       ↓
     사용자
```

### 규모 실측

전체 카탈로그 184,389개 게임을 조사한 결과입니다.

| 항목 | 값 |
| --- | --- |
| 전체 리뷰 | 약 1.5억 건 |
| Parquet 압축 후 (Snappy) | 약 29GB |
| MySQL InnoDB 로 넣었을 경우 | 55.8GB + 인덱스 |
| 파티션 키 | appid |

리뷰 1건당 실측 크기입니다.

| 압축 | 행당 | 1.5억 건 환산 |
| --- | --- | --- |
| 없음 | 235.2 B | 35.3 GB |
| Snappy (Spark 기본) | 146.5 B | 22.0 GB |
| ZSTD | 106.4 B | 16.0 GB |
| GZIP | 96.4 B | 14.5 GB |

### 게임 4.5% 가 리뷰 94.5% 를 갖고 있습니다

| 리뷰 수 | 게임 수 | 누적 리뷰 |
| --- | --- | --- |
| 100,000 이상 | 228 | 76.4M |
| 10,000 이상 | 1,757 | 120.6M |
| 1,000 이상 | 8,239 | 140.4M |
| 100 이상 | 27,832 | 146.7M |

중앙값이 4건이고 36% 는 리뷰가 아예 없습니다. **대부분의 게임은 저장할 리뷰가 거의 없습니다.**

### 왜 나누지 않고 복사하는가

집계는 항상 HDFS 전량을 읽습니다. 화면용 리뷰만 빼서 DB 로 옮기면 그 리뷰들이 집계에서 빠져 숫자가 틀어집니다.

```
잘못된 방식   HDFS 1.4946억(화면용 제외) + DB 54만
             → 집계할 때 두 곳을 합쳐야 함

맞는 방식     HDFS 1.5억 (전량)
             DB 는 그중 54만 건을 복사해 보관
             → 집계는 HDFS 만, DB 는 조회 전용
```

중복 비용은 작습니다. DB 적재분은 **전체의 0.36%**, 용량으로는 329MB 입니다.

### 각 저장소의 역할

| 저장소 | 역할 |
| --- | --- |
| HDFS | 원본 보관 + 배치 입력. 매일 전량 재집계 시 여기를 읽음. 화면은 접근 안 함 |
| PostgreSQL | 화면 조회 전용. 집계 결과 + 최신 패치 창 리뷰 54만 건. 응답 10~50ms |


============================================================================
  B. 토픽 분류  —  통째 교체 (빠진 절 4개 포함)
============================================================================

## 토픽 분류 — 임베딩 방식

리뷰를 불만·만족 요소 5종으로 분류합니다.

| key | 이름 |
| --- | --- |
| balance | 밸런스 / 너프 · 버프 |
| bug | 최적화 / 버그 / 크래시 |
| ui | 시스템 / UI · 편의성 |
| ops | 외부 / 운영 이슈 |
| bm | 과금 / 재화 (BM) |

### 키워드 사전이 아니라 임베딩을 쓰는 이유

키워드 매칭은 같은 단어의 다른 뜻을 구분하지 못합니다. 실측에서 이런 오탐이 나왔습니다.

```
'FPS게임의 전설답네요.'          fps = 프레임 아니고 장르     ✗ 오탐
'순정 FPS 당장 조인'                                        ✗ 오탐
'NO.1 FPS GAME IN MY LIFE'                                 ✗ 오탐
'纯粹的fps'                                                 ✗ 오탐

'아 진짜 렉'                     진짜 성능 불만               ✓
'그만팅겨스렉이게임'                                          ✓
```

짧은 리뷰 59건 중 키워드가 걸린 것을 열어보니 **진짜 불만은 2~3건**이었습니다. `fps` 하나가 "프레임"과 "1인칭 슈터" 두 뜻으로 쓰이는데 키워드 매칭은 문맥을 못 봅니다.

**다국어 처리도 임베딩이 유리합니다.** 언어마다 키워드 사전을 따로 만들 필요가 없습니다.

### 방식 — centroid 코사인 유사도

```
① 토픽마다 대표 리뷰 30~50건을 손으로 고름
② 임베딩해서 평균  →  centroid 5개
③ 각 리뷰를 임베딩  →  centroid 와 코사인 유사도
④ 임계값을 넘는 토픽을 전부 할당 (다중 라벨)
```

다국어 모델을 쓰면 **한국어 예시로 만든 centroid 가 영어 · 중국어 리뷰에도 통합니다.** 언어별로 centroid 를 따로 만들 필요가 없습니다.

`topic_keyword` 사전은 폐기하지 않고 **검증기로 남깁니다.** 명백한 모순(예: 성능 키워드가 가득한데 balance 로만 분류)을 걸러내는 용도입니다.

### 대상 — 30바이트 초과 리뷰만

```
전체 리뷰            1.5억 건
30바이트 초과        약 8,250만 건 (55%)
```

리뷰 중앙값이 34바이트입니다. `great`, `nice`, `good game` 같은 것은 분류할 내용이 없어 임베딩 대상에서 제외합니다. **대표 리뷰 선정 필터와 같은 기준이라 통일됩니다.**

### 벡터는 저장하지 않습니다

```
리뷰 텍스트  →  임베딩 (메모리)
             →  centroid 와 코사인 유사도
             →  토픽 할당
             →  결과만 저장. 벡터는 버림
```

전체를 저장하면 768차원 기준 600GB 가 됩니다. **분류에만 쓰면 저장할 이유가 없습니다.**

```
/review_topic/appid=730/part-0000.parquet
   review_id · topics
   234499291 · ['balance', 'bm']
   234494067 · ['bug']
   234491102 · []                 ← 미분류
```

### 소요 시간 — RTX 4070 기준

| 모델 | 파라미터 | 차원 | 초당 | 8,250만 건 |
| --- | --- | --- | --- | --- |
| multilingual-e5-small | 118M | 384 | ~5,000 | 약 4.6시간 |
| multilingual-e5-base | 278M | 768 | ~2,000 | 약 11.5시간 |
| bge-m3 | 568M | 1024 | ~1,000 | 약 23시간 |

**e5-small 로 시작합니다.** 5개 토픽 분류는 미세한 판단이 아니라 작은 모델로 충분합니다. 품질이 부족하면 base 로 올립니다.

위 수치는 추정입니다. 리뷰 1만 건으로 실측하면 정확한 값이 나옵니다.

```python
model = SentenceTransformer("intfloat/multilingual-e5-small", device="cuda")
t0 = time.time()
vecs = model.encode(texts, batch_size=256, normalize_embeddings=True)
print(f"{len(texts)/(time.time()-t0):.0f} 건/초")
```

### VRAM 제약

RTX 4070 은 12GB 입니다.

```
multilingual-e5-small (fp16)     0.24 GB    여유
Qwen2.5-7B (fp16)               14 GB      들어가지 않음
Qwen2.5-7B (4bit)               ~4.5 GB    가능
```

**임베딩 배치와 AI 요약 배치를 동시에 돌릴 수 없습니다.** 순차로 실행합니다. 그리고 시연 중에 배치가 돌면 VRAM 부족으로 요약이 실패하니 야간에 돌립니다.

### 분류 결과의 실측 특성

```
분류 커버리지    42.1%   (나머지는 5개 토픽에 안 걸림)
평균 매칭 수     1.8개   (다중 라벨)
```

42.1% 는 낮아 보이지만 신호는 분명합니다.

```
실제 패치      토픽 구성 10~28%p 이동
단순 공지      토픽 구성  2~5%p 이동
```

미분류 리뷰는 짧고 추천 수가 적은 쪽에 몰려 있어 **분류 결과에 편향을 만들지 않습니다.**


============================================================================
  C. 테이블별 범위 표  —  표만 교체
============================================================================

### 테이블별로 필요한 범위가 갈립니다

| 테이블 | 범위 | 이유 |
| --- | --- | --- |
| game_daily_stat | 전체 기간 | Daily 탭이 과거 패치 차트를 그림 |
| patch_review_stat | **모든 패치** | 유사 사례 검색이 과거 패치 수치를 사용 |
| game_playtime_band_stat | **최신 패치만** | Topics 탭이 최신만 지원 |
| language_stat | **최신 패치만** | Lang 탭이 최신만 지원 |
| review_repr | 최신 패치만 | — |
| patch_summary | 최신 패치만 | — |

`patch_review_stat` 만 모든 패치를 담습니다. 유사 사례 검색이 "Path of Exile 3.15 패치는 −40.8%p 였다" 같은 과거 수치를 쓰기 때문입니다. 대신 수치만 담는 가벼운 테이블입니다.


============================================================================
  D. AI 요약 생성  —  통째 교체
============================================================================

## AI 요약 생성 — 배치로 미리 전량 생성

8.2만 개는 미리 다 만들 수 있는 규모입니다.

```
로컬 LLM 초당 1.3~2.7건
8.2만 개  →  8.5~17시간 (1회성)
```

이후 갱신은 **그날 새 패치가 나온 게임만** 대상이라 하루에 수십~수백 개 수준입니다.

**온디맨드 생성과 source_hash 캐시 무효화는 필요 없습니다.** 247만 개를 미리 못 만들어서 도입하려던 장치였는데, 최신 패치만 다루기로 하면서 전제가 사라졌습니다.

```sql
CREATE TABLE patch_summary (
    appid        BIGINT      NOT NULL,
    gid          VARCHAR(20) NOT NULL,   -- 어느 패치 기준인지 기록용
    scope        VARCHAR(20) NOT NULL,   -- review_tab / band / language
    scope_key    VARCHAR(20) NOT NULL,   -- '' / Q1~Q4 / 언어코드
    summary      TEXT        NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (appid, scope, scope_key)
);
```


============================================================================
  E. 갱신 주기  —  통째 교체
============================================================================

## 갱신 주기

| 대상 | 주기 | 방식 |
| --- | --- | --- |
| 집계 테이블 | 매일 전량 재계산 | 수정일 기준이라 과거 숫자가 계속 변함 |
| patch_review | 매일 전량 교체 | appid 단위 삭제 후 삽입 |
| AI 요약 | **대표 리뷰가 바뀐 것만** | source_ids 비교 후 재생성 |

### AI 요약은 왜 조건부로 도는가

매일 전량 재수집하므로 `patch_review` 가 매일 갱신됩니다. 그러면 대표 리뷰 상위 5건도 바뀔 수 있고, 바뀌면 요약도 다시 만들어야 합니다.

```
패치 후 0~7일   새 리뷰 유입 + 좋아요 증가   →  자주 바뀜
패치 후 7일~    창은 닫혔지만 좋아요는 계속   →  가끔 바뀜
새 패치         완전히 새로 뽑음             →  전부 바뀜
```

**새 패치가 나오지 않아도 요약이 바뀝니다.**

그렇다고 매일 전부 다시 만들 수는 없습니다.

```
8.2만 개 × 매일  =  8.5시간 × 매일
```

임베딩 배치(4.6시간)도 같은 GPU 를 쓰므로 감당이 안 됩니다.

### source_ids 로 비교합니다

```sql
CREATE TABLE patch_summary (
    appid        BIGINT      NOT NULL,
    scope        VARCHAR(20) NOT NULL,   -- review_tab / band / language
    scope_key    VARCHAR(20) NOT NULL,   -- '' / Q1~Q4 / 언어코드
    gid          VARCHAR(20) NOT NULL,
    summary      TEXT        NOT NULL,
    source_ids   VARCHAR(80) NOT NULL,   -- 대표 리뷰 5건의 review_id
    generated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (appid, scope, scope_key)
);
```

해시 대신 **리뷰 ID 5개를 그대로 저장**합니다. 디버깅할 때 어떤 리뷰로 만든 요약인지 바로 보이고, 길이도 80바이트면 충분합니다.

### 매일 도는 순서

```
① patch_review 갱신 완료
② 게임마다 대표 리뷰 5건을 다시 뽑음          SQL LIMIT 5
③ source_ids 와 비교
     같으면  →  건너뜀
     다르면  →  LLM 호출해서 요약 재생성
```

**②는 인덱스로 5행 읽는 쿼리입니다.** 8,239 게임 × 10조합 = 8.2만 번 실행해도 몇 분입니다. 비싼 것은 ③의 LLM 호출뿐입니다.

### 하루 재생성량 추정

```
패치 주기 평균 13.5일  →  하루 약 610개 게임이 새 패치
                          요약 10개씩 전부 재생성  →  6,100개

패치 후 7일 이내 게임도 대표 리뷰가 흔들림
                          →  추가 수천 개
```

**하루 1~2만 개, 1~2시간**으로 예상됩니다. 임베딩 배치와 순차로 돌리면 하룻밤에 들어갑니다.

정확한 값은 운영해보며 재야 합니다. 재생성 건수가 예상보다 많으면 대표 리뷰 순위가 자주 뒤집힌다는 뜻이니, 그때는 정렬에 안정성을 더하는 것을 검토합니다. 예를 들어 좋아요 차이가 1~2표뿐일 때는 기존 순위를 유지하는 식입니다.

### 스테이징 테이블 교체는 필요 없습니다

`patch_review` 는 `appid` 단위로 삭제 후 삽입합니다. 게임 하나에 평균 66행이라 대량 삭제가 아니고, PostgreSQL 이 부풀지 않습니다.

```sql
BEGIN;
DELETE FROM patch_review WHERE appid = ?;
INSERT INTO patch_review (...) VALUES ...;
COMMIT;
```

### 과거 대표 리뷰는 남지 않습니다

새 패치가 나오면 이전 패치의 창 리뷰가 통째로 교체됩니다. 나중에 과거 패치 대표 리뷰가 필요해지면 HDFS 원본에서 다시 뽑아야 합니다. 되돌릴 수 없는 결정은 아니지만 재계산이 필요합니다.


============================================================================
  F. 스키마 영향  —  새로 추가 (지금 노션에 없음)
============================================================================

## 스키마 영향

### game_playtime_band_stat — 최신 패치만

```sql
CREATE TABLE game_playtime_band_stat (
    appid           BIGINT      NOT NULL,
    band            SMALLINT    NOT NULL,   -- 1~4
    phase           VARCHAR(10) NOT NULL,   -- before / after
    gid             VARCHAR(20) NOT NULL,   -- 어느 패치 기준인지 기록용
    band_min_hours  NUMERIC(8,2),
    band_max_hours  NUMERIC(8,2),
    median_playtime NUMERIC(8,2),
    review_count    INTEGER     NOT NULL,
    positive_rate   NUMERIC(5,2),
    min_sample_ok   BOOLEAN     NOT NULL,
    PRIMARY KEY (appid, band, phase)
);
```

Topics 탭이 최신 패치만 지원하므로 `gid` 는 PK 에서 빠집니다. **게임당 8행(4밴드 × 2phase)으로 고정**됩니다.

행 수는 8,239 × 8 = 약 6.6만 행입니다. 패치별로 쌓던 이전 계산(165만 행)에서 25배 줄었습니다.

밴드 경계는 패치마다 다시 계산하므로 새 패치가 나오면 덮어씁니다.

### language_stat — 최신 패치만

같은 이유로 `gid` 가 PK 에서 빠집니다.

```sql
PRIMARY KEY (appid, language)
gid VARCHAR(20) NOT NULL   -- 기록용
```

8,239 × 5언어 = 약 4.1만 행입니다.

### patch_review_stat — 모든 패치 유지

유사 사례 검색이 과거 패치 수치를 쓰므로 이 테이블만 패치별로 쌓습니다.

```sql
PRIMARY KEY (gid)
appid, before_count, after_count, before_positive, after_positive,
delta, outcome, patch_interval_days, next_notice_ratio, min_sample_ok
```

### patch_review — 신설

최신 패치의 패치 후 7일 리뷰를 전량 담습니다. 대표 리뷰 · 밴드별 · 언어별 · 리뷰 검색이 모두 이 한 테이블에서 나옵니다.

```sql
CREATE TABLE patch_review (
    appid       BIGINT      NOT NULL,
    review_id   BIGINT      NOT NULL,
    gid         VARCHAR(20) NOT NULL,   -- 어느 패치 기준인지 기록용
    review_text TEXT        NOT NULL,
    voted_up    BOOLEAN     NOT NULL,
    votes_up    INTEGER     NOT NULL,
    is_edited   BOOLEAN     NOT NULL,
    playtime_at_review INTEGER     NOT NULL,
    review_language    VARCHAR(20) NOT NULL,
    band        SMALLINT,               -- 1~4. 밴드 계산 결과
    topics      VARCHAR(100),           -- 쉼표 구분. HDFS 에서 그대로 가져옴
    created_at  BIGINT      NOT NULL,
    updated_at  BIGINT      NOT NULL,
    PRIMARY KEY (appid, review_id)
);

CREATE INDEX idx_pr_votes    ON patch_review (appid, votes_up DESC);
CREATE INDEX idx_pr_band     ON patch_review (appid, band, votes_up DESC);
CREATE INDEX idx_pr_language ON patch_review (appid, review_language, votes_up DESC);
```

**`topics` 컬럼이 있어 조인이 필요 없습니다.** HDFS 에서 대표 리뷰를 뽑을 때 토픽이 함께 딸려옵니다. 목업의 리뷰 카드에 붙는 태그가 이 컬럼입니다.

```js
{ kind: '단점', helpful: '412', tags: ['밸런스/너프', '상위 난이도'] }
                                 ↑ topics 컬럼
```

**`review_repr` 테이블은 만들지 않습니다.** 대표 리뷰를 미리 골라 저장하는 대신 창 리뷰를 전량 넣고 정렬로 뽑습니다. 인덱스 세 개면 세 화면이 모두 즉시 응답합니다.

**새 패치가 나오면 그 게임의 행을 통째로 교체합니다.** `appid` 단위 삭제 후 삽입이므로 게임당 행 수가 누적되지 않습니다.
