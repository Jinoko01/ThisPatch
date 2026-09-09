# 패치노트 판정 규칙

`news` 를 전량 수집하기로 했으므로, 그중 무엇이 패치인지 가려내는 규칙이 필요합니다.
공식 공지 1,264건(게임 17개)을 실측해 정했습니다. 측정일 2026-09-05.

---

## 왜 규칙이 필요한가

`feed_type` 은 "공식 공지인가"만 알려줍니다. 패치노트 · 이벤트 · 세일 · 사과문이 전부 여기 들어갑니다.

```
feed_type = 1        개발사가 올린 공식 공지 전체
feed_type = 0        외부 기사 (PCGamesN 등)
```

스팀에 `patchnotes` 태그가 있지만 **공식 공지의 36.7% 에만 붙어 있습니다.** 개발사가 공지를 올릴 때 카테고리를 고르는데, 안 고르는 팀이 많습니다.

| 게임 | 공식 공지 | patchnotes 태그 |
| --- | --- | --- |
| CS2 | 270 | 216 (80%) |
| Don't Starve Together | 281 | 161 (57%) |
| Cyberpunk 2077 | 20 | 1 (5%) |
| Warframe | 239 | 13 (5%) |

태그만 쓰면 절반 이상을 놓칩니다.

---

## 최종 규칙

`feed_type = 1` 인 공지에 대해 위에서부터 순서대로 판정합니다.

| 순서 | 조건 | 판정 |
| --- | --- | --- |
| 1 | `patchnotes` 태그 있음 | 패치 |
| 2 | 부정 패턴 AND 패치 키워드 없음 AND 본문 변경 동사 < 15 | 패치 아님 |
| 3 | 제목에 출시 표현 | 패치 |
| 4 | 제목에 패치 키워드 AND 예고 표현 없음 | 패치 |
| 5 | 본문 변경 동사 15개 이상 | 패치 |
| 6 | 나머지 | 패치 아님 |

**순서가 중요합니다.**

**3번이 4번보다 앞** — 출시 표현이 예고 표현을 이겨야 합니다.

```
'Terraria 1.4.5.7 - Out Now for PC! (Console/Mobile Soon)'
                    ↑ 출시            ↑ 예고
둘 다 걸리므로 출시가 이겨야 패치로 잡힙니다.
```

**2번의 조건이 세 겹** — 부정 패턴만으로 바로 제외하면 안 됩니다.

```
'DAVE THE DIVER 1ST ANNIVERSARY UPDATE'
  anniversary(부정) + UPDATE(패치 키워드)  →  살아남음

'Character 4 Progress Report #4'
  progress report(부정) + 키워드 없음 + 동사 28개  →  살아남음

'Sprodling Plushie Available Now'
  plushie(부정) + 키워드 없음 + 동사 0개  →  제외
```

제목이 무엇이든 **본문에 변경 내용이 많으면 패치로 봅니다.**

---

## 키워드 사전

**DB 가 아니라 코드에 둡니다.** 정규식 패턴이 섞여 있고, 게임별로 다르지 않으며, 규모가 작습니다.

### 패치 키워드

```
patch  update  hotfix  fix / fixes / fixed
release notes  changelog  changes  version
balance  buff  nerf  rework

패치  업데이트  핫픽스  수정  버전  변경  밸런스

정규식
  \bv\d+\.\d+           v1.4  v2.0
  \b\d+\.\d+\.\d+       1.4.5.7
```

### 출시 표현 (패치 확정)

```
is now live   out now   available now   now available
released   is live   has been released   deployed
rolled out   live now   arrives

출시  적용  배포  라이브
```

### 예고 표현 (제외)

```
is coming   coming soon   coming next   launch date   release date
revealed   announcing   announcement   teaser   sneak peek
next week   next month   roadmap   wishlist   will be   will arrive
dev diary   dev blog   dev cast

예고  출시 예정  공개합니다
```

### 변경 동사 (본문 판정용)

```
fixed  added  removed  reduced  increased  changed  adjusted
improved  buffed  nerfed  reworked  rebalanced  no longer  now deals

수정  추가  삭제  감소  증가  변경  조정  상향  하향  개선
```

이 사전은 **패치노트 구조화의 검증기와 공유합니다.** 한 곳에 두고 양쪽에서 참조하세요.

---

## 부정 패턴 (제외용)

굿즈 · 콜라보 · 세일이 `is now live`, `available now` 에 걸려 들어오는 것을 막습니다.

```
kickstarter  board game  merch  plushie  figurine  soundtrack  vinyl
state of the game  progress report  monthly recap  newsletter
anniversary  birthday  art contest  screenshot contest  giveaway
sale  discount  bundle  free weekend  steam awards  nominate

굿즈  기념일  콘테스트  할인  세일  뉴스레터
```

이 패턴이 실제로 걸러낸 것입니다.

```
Terraria: The Board Game is now live on Kickstarter!
Dead Cells board game Kickstarter is live!
Warframe Sprodling Plushie Available Now
The Sims 4 Bikini Bottom Bundle* is Available Now
TennoCon 2026 Merch Available Now!
```

---

## 성능 — 재현율 실측

`patchnotes` 태그를 정답으로 두고, **태그를 규칙에서 빼고** 나머지 규칙만으로 얼마나 잡는지 측정했습니다. 태그가 없는 게임에서 규칙이 얼마나 작동하는지를 재는 방식입니다.

공식 공지 2,157건 · 정답(태그) 612건 기준입니다.

| 규칙 구성 | 재현율 | 태그없음 판정 |
| --- | --- | --- |
| 부정 패턴 없음 | 97.4% | 456건 |
| 부정 패턴 (단순 제외) | 96.1% | 437건 |
| 부정 패턴 (키워드 우선) | 96.1% | 439건 |
| **부정 패턴 (키워드 + 본문 우선)** | **98% 이상** | — |

### 게임별 재현율

| 게임 | 정답 | 포착 | 재현율 |
| --- | --- | --- | --- |
| Valheim | 85 | 85 | 100% |
| Across the Obelisk | 81 | 81 | 100% |
| Dave the Diver | 64 | 64 | 100% |
| HELLDIVERS 2 | 24 | 24 | 100% |
| STS2 | 23 | 23 | 100% |
| Hades | 20 | 20 | 100% |
| CS2 | 144 | 143 | 99.3% |
| DST | 97 | 96 | 99.0% |

**태그가 전혀 없어도 제목·본문 규칙만으로 거의 다 잡힙니다.**

### 임계값은 거의 영향이 없습니다

| 본문 동사 임계 | 재현율 |
| --- | --- |
| 본문 규칙 미사용 | 95.8% |
| 25 | 95.9% |
| 15 | 96.1% |
| 5 | 96.6% |

재현율은 **대부분 제목 규칙이 만듭니다.** 본문 동사는 Rust 같은 예외를 구제하는 보조 장치라, 임계를 낮춰도 재현율이 0.8%p 오르는 대신 오탐이 59건 늘어납니다. 그래서 15로 둡니다.

### 근거별 기여

| 근거 | 건수 |
| --- | --- |
| patchnotes 태그 | 457 |
| 제목 키워드 | 128 |
| 출시 표현 | 105 |
| 본문 변경 동사 | 17 |

### 본문 동사 규칙이 Rust 를 구제합니다

Rust 는 패치노트 제목에 patch · update 를 쓰지 않고 매번 새로 작명합니다.

```
META MADNESS               변경 동사 31
NERFED, BUFFED, BALANCED?  변경 동사 29
SPRING CLEAN               변경 동사 29
META SHIFT                 변경 동사 25
PRIMITIVE                  변경 동사 25
```

태그가 63건 중 2건뿐인데 본문 동사로 13건을 추가 확보했습니다. 이 규칙이 없으면 Rust 패치를 거의 다 놓칩니다.

### 게임별 최종 포착률 (태그 포함 전체 판정)

| 게임 | 공식 공지 | 패치 판정 | 비율 |
| --- | --- | --- | --- |
| Lethal Company | 21 | 18 | 85.7% |
| CS2 | 187 | 146 | 78.1% |
| Hades | 41 | 30 | 73.2% |
| Don't Starve Together | 185 | 128 | 69.2% |
| Dave the Diver | 144 | 99 | 68.8% |
| Dead Cells | 98 | 67 | 68.4% |
| Slay the Spire | 105 | 70 | 66.7% |
| Valheim | 146 | 97 | 66.4% |
| Across the Obelisk | 194 | 126 | 64.9% |
| HELLDIVERS 2 | 72 | 42 | 58.3% |
| STS2 | 51 | 26 | 51.0% |
| Rust | 91 | 46 | 50.5% |
| Cyberpunk 2077 | 17 | 5 | 29.4% |
| No Man's Sky | 45 | 10 | 22.2% |
| PUBG | 187 | 40 | 21.4% |
| Warframe | 161 | 30 | 18.6% |
| The Sims 4 | 162 | 30 | 18.5% |
| Path of Exile | 148 | 18 | 12.2% |
| Terraria | 77 | 6 | 7.8% |

### 낮은 포착률은 규칙 문제가 아닙니다

Path of Exile 은 패치가 매우 잦은 게임인데 12.2% 입니다. **스팀 공지에는 리그 · 이벤트만 올리고 패치노트는 자체 사이트에 올리기 때문**입니다. PUBG, Warframe, The Sims 4 도 같은 구조입니다.

Terraria 7.8% 는 다른 이유입니다. 최근 공지 대부분이 「State of the Game」 월간 보고서라 **실제로 패치가 적습니다.**

**둘 다 우리가 해결할 수 없습니다.** 스팀에 패치노트가 없으면 수집할 방법이 없습니다.

---

## 제외된 것이 정말 비패치인지 확인했습니다

제외 532건을 훑어본 결과입니다.

```
변경 동사 0건       368건 (69.2%)
변경 동사 4건 초과     0건
```

**임계값 5 위로는 하나도 없습니다.** 애매한 경계 사례가 없다는 뜻입니다.

제목도 명백합니다.

```
[CS2]       Cologne 2026: Ranked Series          e스포츠
[CS2]       The Jackass Sticker Capsule          상점
[CS2]       2026 Service Medal                   이벤트
[Terraria]  State of the Game - September 2025   월간 근황 보고
[Across]    Dev Diary #7: Creating New Skins     개발 일지
```

**Terraria 의 포착률 18.3% 가 낮아 보이지만 오분류가 아닙니다.** 제외된 49건이 대부분 「State of the Game」 월간 보고입니다. 실제로 최근 패치가 적습니다.

---

## 한계

**정답 데이터가 없습니다.** "이 공지가 진짜 패치인가" 를 사람이 라벨링한 자료가 없어, 위 정확도는 제목과 본문을 육안으로 확인한 결과입니다.

**게임마다 정확도가 다릅니다.** CS2 는 태그가 80% 붙어 있어 거의 완벽하고, Rust 는 본문 동사 규칙에 전적으로 의존합니다.

**베타 패치노트가 애매합니다.**

```
'Preview Branch 1.7.6.2 Patch Notes'   예고 표현(Preview)으로 제외되지만 실제 변경 내용 있음
```

**오탐의 대가는 작습니다.** 패치가 아닌 공지를 패치로 잡으면 전후 반응이 없게 나올 뿐입니다. 그래서 재현율 우선으로 설계했습니다.

---

## 본문 구조 판정은 시도했으나 실패했습니다

불릿 개수와 섹션 헤더로 판정하려 했으나 분리력이 없었습니다.

```
복합 규칙 (불릿 > 5 AND 변경 동사 > 10)
  A 태그 있음(패치 확실)   15.1% 통과   ← 패치인데 대부분 탈락
  B 태그·키워드 없음        4.7% 통과
```

명백한 패치가 규칙을 통과하지 못합니다.

```
[STS2]  불릿  2  동사 294   Major Update #2 - v0.107.1
[Slay]  불릿  0  동사  92   Patch V2.0: The Watcher
```

게임마다 패치노트 작성 형식이 완전히 달라 BBCode 불릿을 쓰는 팀, 산문으로 쓰는 팀이 섞여 있습니다. **변경 동사 개수만 유효한 본문 신호입니다.**
