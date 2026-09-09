#!/usr/bin/env bash
# End-to-end bootstrap for freshly installed Ubuntu environments.
# This script must be run with sudo privileges.

set -euo pipefail

# 단계 #0: 루트 권한 확인
if [[ "$(id -u)" -ne 0 ]]; then
  echo "[오류] root 권한이 필요합니다. 예: sudo ./install-all.sh" >&2
  exit 1
fi

read -r -p "root 계정에서 작업을 진행할까요? (Y/N): " CONTINUE
CONTINUE=${CONTINUE^^}
if [[ "${CONTINUE}" != "Y" ]]; then
  echo "[안내] 작업을 취소했습니다."
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"

TARGET_USER=${1:-hadoop}
TARGET_GROUP=${TARGET_USER}
USER_HOME=$(getent passwd "${TARGET_USER}" | cut -d: -f6 || true)
if [[ -z "${USER_HOME}" ]]; then
  USER_HOME="/home/${TARGET_USER}"
fi
PROJECT_ROOT="${USER_HOME}/bigdata-dist-skeleton"
HADOOP_VERSION="3.2.2"
HADOOP_ARCHIVE="hadoop-${HADOOP_VERSION}.tar.gz"
# 로컬 설치파일 사용: 이 스크립트와 같은 폴더(bigdata-dist-skeleton)에
# hadoop-3.2.2.tar.gz 를 두면 다운로드 없이 해당 파일을 사용합니다.
HADOOP_URL="https://archive.apache.org/dist/hadoop/common/hadoop-${HADOOP_VERSION}/${HADOOP_ARCHIVE}"
JAVA_HOME_PATH="/usr/lib/jvm/java-8-openjdk-amd64"
HADOOP_INSTALL_DIR="/usr/local/hadoop"
HADOOP_DATA_DIR="${HADOOP_INSTALL_DIR}/data"

# 단계 #1: 대상 사용자 생성 (또는 재사용)
echo "----- [단계 #1] 사용자 '${TARGET_USER}' 확인 및 생성 -----"
if ! id "${TARGET_USER}" >/dev/null 2>&1; then
  adduser --disabled-password --gecos "" "${TARGET_USER}"
fi
echo "${TARGET_USER}:${TARGET_USER}" | chpasswd
if ! getent group "${TARGET_GROUP}" >/dev/null 2>&1; then
  TARGET_GROUP="${TARGET_USER}"
fi
USER_HOME=$(getent passwd "${TARGET_USER}" | cut -d: -f6 || true)
if [[ -z "${USER_HOME}" ]]; then
  USER_HOME="/home/${TARGET_USER}"
fi
PROJECT_ROOT="${USER_HOME}/bigdata-dist-skeleton"

# 단계 #2: 필수 패키지 설치
echo "----- [단계 #2] 필수 패키지 설치 -----"
apt-get update -y
apt-get install -y openjdk-8-jdk ant ssh curl rsync

# 단계 #3: Java 8 기본 대체 설정
if [[ -d "${JAVA_HOME_PATH}" ]]; then
  update-alternatives --set java "${JAVA_HOME_PATH}/jre/bin/java" || true
  update-alternatives --set javac "${JAVA_HOME_PATH}/bin/javac" || true
fi

# 단계 #4: Hadoop 다운로드 및 설치
echo "----- [단계 #4] Hadoop ${HADOOP_VERSION} 설치 -----"
mkdir -p "${USER_HOME}/downloads"
ARCHIVE_PATH="${USER_HOME}/downloads/${HADOOP_ARCHIVE}"
if [[ ! -f "${ARCHIVE_PATH}" ]]; then
  LOCAL_ARCHIVE="${SCRIPT_DIR}/${HADOOP_ARCHIVE}"
  if [[ -f "${LOCAL_ARCHIVE}" ]]; then
    echo "  - 기존 아카이브를 발견하여 '${LOCAL_ARCHIVE}'에서 복사합니다."
    cp "${LOCAL_ARCHIVE}" "${ARCHIVE_PATH}"
  fi
fi
if [[ ! -f "${ARCHIVE_PATH}" ]]; then
  echo "  - 사전 준비된 아카이브가 없어 원격에서 다운로드합니다."
  curl -L "${HADOOP_URL}" -o "${ARCHIVE_PATH}"
fi
if [[ ! -d "${HADOOP_INSTALL_DIR}" ]]; then
  tar -xzf "${ARCHIVE_PATH}" -C /usr/local
  mv "/usr/local/hadoop-${HADOOP_VERSION}" "${HADOOP_INSTALL_DIR}"
fi
chown -R "${TARGET_USER}:${TARGET_USER}" "${HADOOP_INSTALL_DIR}"
HADOOP_ENV_FILE="${HADOOP_INSTALL_DIR}/etc/hadoop/hadoop-env.sh"
if [[ -f "${HADOOP_ENV_FILE}" ]]; then
  if ! grep -q "export JAVA_HOME=${JAVA_HOME_PATH}" "${HADOOP_ENV_FILE}"; then
    echo "export JAVA_HOME=${JAVA_HOME_PATH}" >> "${HADOOP_ENV_FILE}"
  fi
  if ! grep -q "export HADOOP_NICENESS=0" "${HADOOP_ENV_FILE}"; then
    echo "export HADOOP_NICENESS=0" >> "${HADOOP_ENV_FILE}"
  fi
fi

# 단계 #5: 사용자 bashrc에 환경 변수 추가
echo "----- [단계 #5] 환경 변수 설정 -----"
ENV_BLOCK="# >>> bigdata-dist skeleton <<<"
if ! sudo -u "${TARGET_USER}" grep -q "${ENV_BLOCK}" "${USER_HOME}/.bashrc"; then
  cat <<EOF >> "${USER_HOME}/.bashrc"
${ENV_BLOCK}
export JAVA_HOME=${JAVA_HOME_PATH}
export HADOOP_HOME=${HADOOP_INSTALL_DIR}
export PATH=\$HADOOP_HOME/bin:\$HADOOP_HOME/sbin:\$PATH
export HADOOP_NICENESS=0
# <<< bigdata-dist skeleton <<<
EOF
fi

# 단계 #6: 프로젝트 디렉터리 준비
echo "----- [단계 #6] 프로젝트 디렉터리 준비 -----"
mkdir -p "${PROJECT_ROOT}"
chown -R "${TARGET_USER}:${TARGET_GROUP}" "${PROJECT_ROOT}"

# 단계 #7: Hadoop 설정 구성
echo "----- [단계 #7] Hadoop 설정 구성 -----"
CORE_SITE_PATH="${HADOOP_INSTALL_DIR}/etc/hadoop/core-site.xml"
HDFS_SITE_PATH="${HADOOP_INSTALL_DIR}/etc/hadoop/hdfs-site.xml"
MAPRED_SITE_PATH="${HADOOP_INSTALL_DIR}/etc/hadoop/mapred-site.xml"
YARN_SITE_PATH="${HADOOP_INSTALL_DIR}/etc/hadoop/yarn-site.xml"
mkdir -p "${HADOOP_DATA_DIR}/name" "${HADOOP_DATA_DIR}/data"
chown -R "${TARGET_USER}:${TARGET_GROUP}" "${HADOOP_DATA_DIR}"
cat <<'EOF' > "${CORE_SITE_PATH}"
<?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
  <property>
    <name>fs.defaultFS</name>
    <value>hdfs://localhost:9000</value>
  </property>
</configuration>
EOF
cat <<EOF > "${HDFS_SITE_PATH}"
<?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
  <property>
    <name>dfs.replication</name>
    <value>1</value>
  </property>
  <property>
    <name>dfs.namenode.name.dir</name>
    <value>file://${HADOOP_DATA_DIR}/name</value>
  </property>
  <property>
    <name>dfs.datanode.data.dir</name>
    <value>file://${HADOOP_DATA_DIR}/data</value>
  </property>
</configuration>
EOF
cat <<'EOF' > "${MAPRED_SITE_PATH}"
<?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
  <property>
    <name>mapreduce.framework.name</name>
    <value>local</value>
  </property>
</configuration>
EOF
cat <<'EOF' > "${YARN_SITE_PATH}"
<?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<configuration>
  <property>
    <name>yarn.nodemanager.aux-services</name>
    <value>mapreduce_shuffle</value>
  </property>
</configuration>
EOF

# 단계 #8: 스크립트 위치에서 프로젝트 파일 동기화
echo "----- [단계 #8] 프로젝트 파일 동기화 -----"
copy_targets=("project" "solutions")
for dir in "${copy_targets[@]}"; do
  src_path="${SCRIPT_DIR}/${dir}"
  dest_path="${PROJECT_ROOT}/${dir}"
  if [[ -d "${src_path}" ]]; then
    echo "  - '${dir}' 디렉터리를 동기화합니다."
    mkdir -p "${dest_path}"
    rsync -a --delete "${src_path}/" "${dest_path}/"
  else
    echo "  - '${dir}' 디렉터리가 없어 건너뜁니다. (skeleton 구성이라면 정상입니다.)"
  fi
done
# install-all.sh와 README 등 기타 파일은 skeleton/complete 루트에 직접 복사하지 않음
chown -R "${TARGET_USER}:${TARGET_GROUP}" "${PROJECT_ROOT}"

# 단계 #9: 대상 사용자의 무비밀번호 SSH 설정
echo "----- [단계 #9] 무비밀번호 SSH 설정 -----"
sudo -u "${TARGET_USER}" bash <<'EOF'
set -euo pipefail
mkdir -p ~/.ssh
chmod 700 ~/.ssh
if [[ ! -f ~/.ssh/id_rsa ]]; then
  ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa
fi
cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
ssh -o StrictHostKeyChecking=no localhost "exit" || true
EOF

# 단계 #10: SSH 서비스 기동
echo "----- [단계 #10] SSH 서비스 기동 -----"
if pgrep -x sshd >/dev/null 2>&1; then
  echo "  - sshd 프로세스가 이미 실행 중입니다. 건너뜁니다."
else
  ssh_started=false
  if command -v systemctl >/dev/null 2>&1; then
    systemctl enable ssh >/dev/null 2>&1 || true
    if systemctl start ssh >/dev/null 2>&1; then
      ssh_started=true
    fi
  fi
  if [[ "${ssh_started}" != "true" ]] && command -v service >/dev/null 2>&1; then
    if service ssh start >/dev/null 2>&1; then
      ssh_started=true
    fi
  fi
  if [[ "${ssh_started}" != "true" ]] && [[ -x /etc/init.d/ssh ]]; then
    if /etc/init.d/ssh start >/dev/null 2>&1; then
      ssh_started=true
    fi
  fi
  if [[ "${ssh_started}" != "true" ]] && [[ -x /usr/sbin/sshd ]]; then
    echo "  - sshd 서비스가 감지되지 않아 직접 기동합니다."
    /usr/sbin/sshd -D &
    sleep 1
    if pgrep -x sshd >/dev/null 2>&1; then
      ssh_started=true
    fi
  fi
  if [[ "${ssh_started}" != "true" ]]; then
    echo "  [경고] sshd 서비스를 시작하지 못했습니다. 이후 단계에서 ssh localhost 연결이 실패할 수 있습니다."
  fi
fi

# 단계 #11: 프로젝트 설정 (환경 구성, HDFS 포맷, 빌드 등)
echo "----- [단계 #11] 프로젝트 환경 구성 및 빌드 -----"
sudo -u "${TARGET_USER}" bash <<EOF
set -euo pipefail
cd "${PROJECT_ROOT}"
export JAVA_HOME=${JAVA_HOME_PATH}
export HADOOP_HOME=${HADOOP_INSTALL_DIR}
export PATH=\$HADOOP_HOME/bin:\$HADOOP_HOME/sbin:\$PATH
export HADOOP_NICENESS=0
source ~/.bashrc
chmod +x project/set_hadoop_env.sh
./project/set_hadoop_env.sh
if [[ ! -d ${HADOOP_INSTALL_DIR}/data/name/current ]]; then
  hdfs namenode -format -force -nonInteractive || true
fi
start-dfs.sh || true
sleep 3
cd project
ant package
EOF

# 단계 #12: 사용자 안내
echo "----- [단계 #12] 마무리 안내 -----"
echo "[완료] 아래 명령으로 '${TARGET_USER}' 계정으로 전환한 뒤 환경을 확인하세요."
echo "  su - ${TARGET_USER}"
echo "  source ~/.bashrc"
echo "  start-dfs.sh    # 데몬이 종료돼 있다면 재기동"
echo "  jps             # NameNode / DataNode / SecondaryNameNode 확인"
echo "  hdfs dfs -ls /  # 기본 디렉터리 확인"
echo "  hdfs dfs -mkdir -p /user/${TARGET_USER}/input/wordcount"
echo "  hdfs dfs -mkdir -p /user/${TARGET_USER}/output"
echo "  # 입력 데이터 파일 업로드 (필수)"
echo "  hdfs dfs -put ${PROJECT_ROOT}/project/data/wordcount-data.txt /user/${TARGET_USER}/input/wordcount/"
echo "  # WordCount 실행 (출력 디렉터리가 이미 있으면 먼저 삭제)"
echo "  hdfs dfs -rm -r /user/${TARGET_USER}/output/wordcount 2>/dev/null || true"
echo "  hadoop jar ${PROJECT_ROOT}/project/ssafy.jar wordcount /user/${TARGET_USER}/input/wordcount /user/${TARGET_USER}/output/wordcount"
echo "  # 결과 확인:"
echo "  hdfs dfs -cat /user/${TARGET_USER}/output/wordcount/part-r-00000"
echo "  # 다른 실습 문제는 Driver.java에 클래스를 등록하고 다시 빌드해야 실행 가능합니다."

