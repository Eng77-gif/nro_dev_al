@ECHO OFF
REM Chạy server cho VPS yếu (1-2 nhân, 1-2 GB RAM).
REM RAM_MAX: RAM tối đa cho Java. VPS 1 GB dùng 512m, VPS 2 GB dùng 768m-1g, VPS 4 GB trở lên dùng run.bat.
SET RAM_MAX=512m
java -server -Dfile.encoding=UTF-8 -Xms64m -Xmx%RAM_MAX% -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:+UseStringDeduplication -XX:+ExitOnOutOfMemoryError -jar 20.jar
PAUSE
