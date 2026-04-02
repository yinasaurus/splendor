#!/bin/bash
set -e
shopt -s nullglob

if [ -d "classes" ]; then
    echo "Cleaning old class files..."
    rm -rf classes/*
fi
mkdir -p classes

CP="classes"
for jar in lib/*.jar; do
    CP="$CP:$jar"
done

TMP_LIST=$(mktemp 2>/dev/null || echo .compile_sources.tmp)
trap 'rm -f "$TMP_LIST"' EXIT
find src -name '*.java' -type f | sort > "$TMP_LIST"
if [ ! -s "$TMP_LIST" ]; then
    echo "No Java sources found under src/"
    exit 1
fi

javac -d classes -cp "$CP" -sourcepath src @"$TMP_LIST"
echo "Compilation complete. Class files are in the classes directory."
