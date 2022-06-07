#!/usr/bin/bash
cd `dirname $0`
./gradlew
./autotrade-local/run.sh
