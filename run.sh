#!/bin/bash
set -e
shopt -s nullglob

CP="classes"
for jar in lib/*.jar; do
    CP="$CP:$jar"
done

java -cp "$CP" splendor.main.SplendorGame
