# Canal 1.1.8 固定镜像；仅替换其内置解析器的 CHECK 克隆缺陷。
FROM canal/canal-server:v1.1.8@sha256:5dd84c4d8f6a6755bfb154ed8df1f85ec88c08c11501868d7b0de7b59db7f833
USER root
COPY patch/SQLCheck.java /tmp/canal-patch/SQLCheck.java
RUN set -eu; \
    mkdir -p /tmp/canal-patch/classes; \
    /usr/java/latest/bin/javac -encoding UTF-8 \
      -cp /home/admin/canal-server/lib/polardbx-parser-5.4.19.jar \
      -d /tmp/canal-patch/classes /tmp/canal-patch/SQLCheck.java; \
    cd /tmp/canal-patch/classes; \
    /usr/java/latest/bin/jar uf /home/admin/canal-server/lib/polardbx-parser-5.4.19.jar \
      com/alibaba/polardbx/druid/sql/ast/statement/SQLCheck.class; \
    rm -rf /tmp/canal-patch
