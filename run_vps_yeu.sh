#!/bin/sh
# Chạy server cho VPS Linux yếu (1-2 nhân, 1-2 GB RAM). Đổi RAM_MAX theo máy: 1 GB -> 512m, 2 GB -> 768m.
RAM_MAX=${RAM_MAX:-512m}
cd "$(dirname "$0")"
exec java -server -Dfile.encoding=UTF-8 -Xms64m -Xmx$RAM_MAX -XX:+UseSerialGC -Xss512k \
  -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:+UseStringDeduplication \
  -XX:+ExitOnOutOfMemoryError -jar 20.jar
