# 🌌 Big Data & Distributed AI Architecture

빅데이터 파이프라인의 **데이터 유입 ──▶ 분산 저장 ──▶ 연산 및 분산 알고리즘 ──▶ AI 서빙 및 오케스트레이션** 흐름 순서대로 정리한 학습 노트입니다.  
각 문서는 단일 주제에 대한 통찰과 오개념 교정 과정을 담고 있습니다.

---

## 🗺️ 파이프라인 아키텍처 흐름도

```
[0. 기초 철학]    Python GIL 한계 ──▶ 분산 처리(Scale-Out)의 본질 ──▶ CAP 정리 (CP vs AP)
                          │
                          ▼
[1. 수집 & 경로]   데이터 유입 ──▶ 대용량 배치(ELT) vs 실시간 스트리밍(ETL) 선택
                          │
                          ▼
[2. 분산 저장]    디스크 I/O 최적화 ──▶ Parquet (Predicate Pushdown & Z-Order) ──▶ Hadoop HDFS(저장소)
                          │
                          ▼
[3. 분산 연산]    MapReduce 진화 ──▶ 셔플 & Combiner ──▶ 로컬 셔플/태스크 실행 ──▶ PySpark ──▶ 2-Phase 행렬곱 ──▶ 격자 분할 조인
                          │
                          ▼
[4. AI & 서빙]    오프라인 K-Means 배치 + 온라인 1-NN 서빙 ──▶ In-Mapper Top-K 검색 최적화
                          │
                          ▼
[5. 통합 관리]    파이프라인 지휘 ──▶ Airflow (DAG 하네스) & Docker 인프라 격리
```

---

## 📅 주간 TIL 목차 (8월 4주차)

| 일자 | 구분 | 주요 학습 내용 | 바로가기 |
| :---: | :--- | :--- | :---: |
| **8/24 (월)** | 분산 기초 & 분산 저장소 | Python GIL, 의사 분산(Scale-Out), CAP 정리(CP vs AP), HDFS 저장/연산 분리 | [**20260824.md**](20260824.md) |
| **8/25 (화)** | 수집/처리 & MapReduce 셔플 | 배치 ELT vs 스트리밍 ETL, PySpark 진화, MapReduce 셔플/Combiner, Mapper-Reducer 태스크 I/O | [**20260825.md**](20260825.md) |
| **8/26 (수)** | 저장 최적화 & 분산 알고리즘 | Parquet Predicate Pushdown, Z-Order, PySpark Shuffle, 2-Phase 행렬곱, 격자 분할 조인 | [**20260826.md**](20260826.md) |
| **8/27 (목)** | AI 서빙 & 오케스트레이션 | 오프라인 K-Means + 온라인 1-NN 서빙, In-Mapper Top-K, Airflow DAG & Docker | [**20260827.md**](20260827.md) |

---

## 📚 세부 학습 주제 목록

| 번호 | 단계 | 학습 주제 | 일자 | 핵심 키워드 |
| :---: | :--- | :--- | :---: | :--- |
| **01** | **기초 배경** | [01. Python의 단일 코어 동작과 GIL](20260824.md#1-python의-단일-코어-동작과-gil) | 8/24 (월) | `CPython`, `GIL`, `Single Core` |
| **02** | **분산 원리** | [02. 분산 처리의 본질과 단일 노드 의사 분산](20260824.md#2-분산-처리의-본질과-단일-노드-의사-분산) | 8/24 (월) | `Scale-Out`, `Pseudo-Distributed`, `CPU vs GPU` |
| **03** | **시스템 제약**| [03. CAP 정리에서 CP와 AP의 선택](20260824.md#3-cap-정리에서-cp와-ap의-선택) | 8/24 (월) | `CAP Theorem`, `Consistency`, `Availability` |
| **04** | **생태계 저장**| [04. Hadoop 생태계: HDFS 저장과 MapReduce 연산의 분리](20260824.md#4-hadoop-생태계-hdfs-저장과-mapreduce-연산의-분리) | 8/24 (월) | `Apache Foundation`, `HDFS`, `Decoupling` |
| **05** | **수집 & 흐름**| [05. 대용량 배치 ELT vs 실시간 스트리밍 ETL](20260825.md#1-대용량-배치-elt-vs-실시간-스트리밍-etl) | 8/25 (화) | `Medallion`, `Bronze/Silver/Gold`, `In-Flight` |
| **06** | **연산 원리** | [06. Hadoop MapReduce에서 PySpark로의 진화](20260825.md#2-hadoop-mapreduce에서-pyspark로의-진화) | 8/25 (화) | `Map-Shuffle-Reduce`, `Catalyst Optimizer` |
| **07** | **셔플 메커니즘**| [07. MapReduce 셔플의 해시 분배 규칙과 Combiner](20260825.md#3-mapreduce-셔플의-해시-분배-규칙과-combiner) | 8/25 (화) | `Hash Partitioner`, `Sort`, `Combiner` |
| **08** | **실행 메커니즘**| [08. Mapper와 Reducer의 태스크 실행과 로컬 셔플 I/O](20260825.md#4-mapper와-reducer의-태스크-실행과-로컬-셔플-io) | 8/25 (화) | `Task Lifecycle`, `Phase Separation`, `Local Disk I/O` |
| **09** | **저장 최적화**| [09. Parquet의 Predicate Pushdown 원리](20260826.md#1-parquet의-predicate-pushdown-원리) | 8/26 (수) | `Columnar`, `Footer Metadata`, `I/O Skip` |
| **10** | **저장 심화** | [10. Parquet 행 정합성과 다차원 정렬/Z-Order](20260826.md#2-parquet-행-정합성과-다차원-정렬z-order) | 8/26 (수) | `Row Group Index`, `Partitioning`, `Z-Order` |
| **11** | **연산 최적화**| [11. PySpark의 Driver/Executor와 Shuffle](20260826.md#3-pyspark의-driverexecutor와-shuffle) | 8/26 (수) | `Driver/Executor`, `Narrow/Wide`, `Shuffle` |
| **12** | **분산 행렬곱**| [12. 2-Phase 분산 행렬 곱셈과 MapReduce 체이닝](20260826.md#4-2-phase-분산-행렬-곱셈과-mapreduce-체이닝) | 8/26 (수) | `2-Phase`, `Job Chaining`, `Tensor Parallelism` |
| **13** | **격자 분할 조인**| [13. 키 없는 조인의 활용과 2차원 격자 분할](20260826.md#5-키-없는-조인의-활용과-2차원-격자-분할) | 8/26 (수) | `Non-Equi Join`, `Grid Partitioning`, `All-Pairs` |
| **14** | **AI 배치/서빙**| [14. 오프라인 K-Means 배치와 온라인 1-NN 초고속 서빙](20260827.md#1-오프라인-k-means-배치와-온라인-1-nn-초고속-서빙) | 8/27 (목) | `Offline Clustering`, `1-NN Serving`, `O(K) Latency` |
| **15** | **분산 Top-K** | [15. In-Mapper Top-K와 cleanup() 우선순위 큐](20260827.md#2-in-mapper-top-k와-cleanup-우선순위-큐) | 8/27 (목) | `In-Mapper Combining`, `cleanup()`, `Min-Heap` |
| **16** | **오케스트레이션**| [16. Airflow DAG 하네스와 Docker](20260827.md#3-airflow-dag-하네스와-docker) | 8/27 (목) | `DAG Harness`, `Retry`, `Docker Container` |
