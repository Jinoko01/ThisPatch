# SSAFY Bigdata Distributed Processing Skeleton

이 디렉터리는 SSAFY 분산 처리 교육용 Skeleton 프로젝트입니다. 학습자는 `project/` 아래 템플릿을 수정하여 MapReduce 과제를 진행합니다.

## 1. 사전 요구 사항
- Ubuntu 22.04/24.04 혹은 대응되는 Linux 환경
- `sudo` 권한
- Java 8 (OpenJDK 8 권장), Ant, SSH
- 최소 8GB RAM, 10GB 이상의 디스크 여유
- 인터넷 연결 (패키지 및 Hadoop 다운로드용)

## 2. 자동 설치 절차
루트 권한에서 `install-all.sh`를 실행하면 Skeleton 프로젝트에 필요한 환경을 자동 구성합니다.

```bash
sudo ./install-all.sh        # 기본 계정: hadoop
```

스크립트 주요 동작:
- 대상 사용자 생성 및 패스워드 설정 (`hadoop/hadoop`)
- OpenJDK 8, Ant, SSH, rsync 설치
- Hadoop 3.3.6 다운로드 후 `/usr/local/hadoop`에 배치
- 사용자 `~/.bashrc`에 `JAVA_HOME`, `HADOOP_HOME`, `PATH`, `HADOOP_NICENESS=0` 추가
- `core-site.xml`, `hdfs-site.xml` 등 기본 설정 자동 구성
- `project/` 디렉터리 동기화 (Skeleton 전용)
- 패스워드 없는 SSH 구성 및 SSH 서비스 기동(미동작 시 `sshd` 직접 기동, 실행 중이면 건너뜀)
- HDFS 포맷 및 데몬 기동
- Ant 빌드(`project/ssafy.jar`) 수행

> ⚠️ 설치 시 SSH 서버가 꺼져 있으면 자동으로 기동되며, 이미 실행 중이면 건너뜁니다. 만약 서비스가 시작되지 않으면 `/usr/sbin/sshd`를 수동으로 실행하세요.

설치가 끝나면 다음 명령으로 환경을 점검하고 워드카운트를 실행하세요.

```bash
su - hadoop
source ~/.bashrc
start-dfs.sh
jps
hdfs dfs -mkdir -p /user/hadoop/input/wordcount
hdfs dfs -mkdir -p /user/hadoop/output
hdfs dfs -put ~/bigdata-dist-skeleton/project/data/wordcount-data.txt /user/hadoop/input/wordcount/
hadoop jar ~/bigdata-dist-skeleton/project/ssafy.jar wordcount \
  /user/hadoop/input/wordcount /user/hadoop/output/wordcount
```

> 다른 과제를 실행하려면 `project/src/Driver.java`에 해당 클래스를 등록하고 `cd project && ant package`로 다시 빌드한 뒤 `hadoop jar ... <job>` 형태로 실행하세요.

## 3. 수동 설치 요약
자동 스크립트를 사용할 수 없을 때는 아래 단계로 직접 환경을 구성하십시오.

1. 필수 패키지 설치
   ```bash
   sudo apt-get update
   sudo apt-get install -y openjdk-8-jdk ant ssh curl rsync
   ```
2. Hadoop 3.3.6 다운로드 및 설치
   ```bash
   curl -L https://archive.apache.org/dist/hadoop/common/hadoop-3.3.6/hadoop-3.3.6.tar.gz -o /tmp/hadoop.tar.gz
   sudo tar -xzf /tmp/hadoop.tar.gz -C /usr/local
   sudo mv /usr/local/hadoop-3.3.6 /usr/local/hadoop
   sudo chown -R $(whoami):$(whoami) /usr/local/hadoop
   ```
3. 환경 변수 설정 (`~/.bashrc`)
   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
   export HADOOP_HOME=/usr/local/hadoop
   export PATH=$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$PATH
   ```
4. `project/set_hadoop_env.sh` 실행 후 HDFS 초기화와 기동 (이때 `.bashrc`에 `HADOOP_NICENESS=0`이 추가됩니다)
   ```bash
   hdfs namenode -format -force -nonInteractive
   start-dfs.sh
   ```
5. Ant 빌드
   ```bash
   cd project
   ant package
   ```

## 4. 디렉터리 구성
- `project/src` : MapReduce 기본 드라이버 및 예제 코드
- `project/template` : 과제용 템플릿 파일
- `project/data` : 실습용 입력 데이터
- `project/datagen` : 데이터 생성 유틸리티
- `project/set_hadoop_env.sh` : 사용자 환경 변수 설정 스크립트

## 5. 자주 묻는 질문
- `ssh: connect to host localhost port 22: Connection refused`  
  → `sudo service ssh start` 후 다시 실행하십시오.
- `hdfs: command not found`  
  → `source ~/.bashrc`로 환경 변수를 재적용한 뒤 수행하십시오.
- Ant 빌드 오류 발생  
  → `JAVA_HOME`, `HADOOP_HOME`, `/usr/local/hadoop/share/hadoop` 경로 권한을 점검하십시오.
- `start-dfs.sh` 실행 시 `Cannot set priority of namenode process` 경고가 반복될 때  
  → `export HADOOP_NICENESS=0`이 적용됐는지 확인한 뒤 `stop-dfs.sh && start-dfs.sh`를 실행하십시오. 경고 자체는 무해하지만 데몬이 뜨지 않으면 환경 변수를 명시적으로 적용하세요.

## 6. 참고
- 다른 Hadoop 버전을 사용하려면 `install-all.sh`의 `HADOOP_VERSION`과 `project/build.xml`의 `lib.dir` 기본값을 함께 수정하세요.
- 이미 다른 버전이 `/usr/local/hadoop`에 설치되어 있다면, 덮어쓰기 전에 백업 또는 제거 후 스크립트를 실행하는 것이 안전합니다.
- Hadoop 아카이브는 다운로드 시간이 길고, 동시에 여러 사용자가 스크립트를 실행하면 네트워크 부하가 커질 수 있습니다. 교육 환경에서는 시간대를 나누거나 사전에 아카이브를 공용 저장소에 배포해 두고 로컬 복사본을 활용하는 방식을 권장합니다.
- **Hadoop 다운로드**: `hadoop 3.2.2` 폴더는 프로젝트 구조상 별도의 git에 포함되어 있으므로 아래 링크에서 다운로드하세요.
  - SSAFY링크: https://lab.ssafy.com/s14-common-files/bigdata-datasets/-/blob/master/hadoop-3.2.2.tar.gz
  - https://archive.apache.org/dist/hadoop/common/hadoop-3.2.2/hadoop-3.2.2.tar.gz