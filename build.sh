#!/usr/bin/env bash
# 本机 mvn 命令因 MAVEN_HOME/classpath 问题无法直接运行，这里显式指定 classpath 调起 Maven。
# 用法: ./build.sh [maven 参数...]      例: ./build.sh -DskipTests install
set -u
export PATH="/usr/bin:/bin:$PATH"
MVN_HOME="D:/Maven/apache-maven-3.9.4"
cd "$(dirname "$0")" || exit 1
java -classpath "${MVN_HOME}/boot/plexus-classworlds-2.7.0.jar" \
     "-Dclassworlds.conf=${MVN_HOME}/bin/m2.conf" \
     "-Dmaven.home=${MVN_HOME}" \
     "-Dmaven.multiModuleProjectDirectory=$(cygpath -w "$PWD")" \
     org.codehaus.plexus.classworlds.launcher.Launcher "$@"
