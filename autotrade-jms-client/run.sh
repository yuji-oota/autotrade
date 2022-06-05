#!/usr/bin/bash
cd `dirname $0`
java -jar autotrade-jms-client.jar --key=$1 --value=$2
