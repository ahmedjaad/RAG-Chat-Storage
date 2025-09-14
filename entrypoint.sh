#!/bin/sh

java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000 -jar /app/app.jar