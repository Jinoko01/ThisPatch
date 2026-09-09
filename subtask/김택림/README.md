# 빅데이터 프로젝트 · Hadoop 학습 TIL

확인 기준: 2026-08-28

## 하둡 학습 일정

| 일자 | 학습 내용 | TIL |
| --- | --- | --- |
| 2026-08-18 (화) | Hadoop 설치, `Wordcount`, `Wordcount1char` | [화요일 TIL](./2026-08-18_화요일.md) |
| 2026-08-19 (수) | `Wordcountsort`, `InvertedIndex` | [수요일 TIL](./2026-08-19_수요일.md) |
| 2026-08-20 (목) | `MatrixAdd`, `MatrixMulti` | [목요일 TIL](./2026-08-20_목요일.md) |
| 2026-08-21 (금) | `AllPairPartition`, `AllPairPartitionSelf`, `CommonItemCount` | [금요일 TIL](./2026-08-21_금요일.md) |

## Hadoop 실습 요약

| 실습 | 학습 내용 |
| --- | --- |
| `Wordcount` | 단어별 빈도 계산으로 MapReduce 기본 흐름 학습 |
| `Wordcount1char` | 단어의 첫 글자별 빈도 계산 |
| `Wordcountsort` | Custom Partitioner를 이용한 데이터 분배 |
| `InvertedIndex` | 파일명과 위치 정보를 활용한 역색인 |
| `MatrixAdd` | 행·열 좌표별 행렬 덧셈 |
| `MatrixMulti` | 행렬 곱셈을 위한 중간 데이터 생성 |
| `AllPairPartition` | 두 테이블의 전체 조합을 위한 파티셔닝 |
| `AllPairPartitionSelf` | 하나의 테이블 내 전체 조합과 중복 제거 |
| `CommonItemCount` | 공통 item을 공유하는 record 쌍의 개수 계산 |

## 핵심 학습 내용

- HDFS의 기본 구조와 Linux 경로와의 차이
- Mapper–Shuffle/Sort–Reducer 처리 흐름
- Hadoop `Writable` 타입과 `Configuration` 사용
- Partitioner를 통한 Reducer별 데이터 분배
- 여러 MapReduce Job을 연결하는 다단계 처리
- HDFS 입력·출력 경로 관리

## 실습 환경

- Ubuntu, Hadoop 3.2.2, Java 8, Ant, SSH
- HDFS localhost 단일 노드 구성
- 기본 파일 시스템: `hdfs://localhost:9000`
- 실제 소스 위치: `/home/hadoop/bigdata-dist-skeleton/project/src`

## 이번 주 프로젝트 TIL 요약

이번 주에는 여러 분산 처리 프로젝트 후보를 비교한 뒤 Steam 데이터를 활용한 주제로 방향을 좁히고, API 수집 가능성과 서비스 기능을 검증했다.

| 일자 | 주제 | 요약 |
| --- | --- | --- |
| 2026-08-24 (월) | 분산 프로젝트 주제 탐색 | 공급망·유통망·교통망·오픈소스·논문·Steam 리뷰 등 후보를 비교하고, Steam 리뷰 기반 게임 평가 요소 분석을 유력 후보로 검토 |
| 2026-08-25 (화) | 프로젝트 주제 구체화 | Steam 게임·리뷰·패치·태그 데이터를 연결한 분석 서비스 아이디어 검토 |
| 2026-08-26 (수) | Steam Review API POC + 데이터 수집 범위 확장 | Review API의 리뷰 필드·약 980만 건 규모·cursor pagination과 약 100페이지 장기 호출, News API·SteamSpy 데이터를 확인 |
| 2026-08-27 (목) | 서비스 기능 구체화 | 유사 장르 Benchmark, 플레이타임·환불 Review 분석, 유사 패치 기반 QA 기능 정리 |

### 일자별 TIL

- [2026-08-24 월요일 TIL](./2026-08-24_월요일.md)
- [2026-08-25 화요일 TIL](./2026-08-25_화요일.md)
- [2026-08-26 수요일 TIL](./2026-08-26_수요일.md)
- [2026-08-27 목요일 TIL](./2026-08-27_목요일.md)

## 회고

간단한 WordCount에서 시작해 Partitioner, 역색인, 행렬 연산, 전체 조합 파티셔닝, 공통 item 집계까지 MapReduce의 핵심 구조를 단계적으로 익혔다. 프로젝트 주제 탐색에서는 데이터 확보 가능성과 분산처리의 필요성을 함께 비교했고, 이후 Steam 데이터 수집 POC와 서비스 기능 구체화로 발전시켰다.
