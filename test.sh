#!/bin/bash

# Remove old class files to avoid version conflicts
if [ -d "classes" ]; then
    echo "Cleaning old class files..."
    rm -rf classes/*
fi

# Create classes directory if it doesn't exist
if [ ! -d "classes" ]; then
    mkdir classes
fi

# Compile all Java source files including test
javac -d classes -cp "lib/*:classes" -sourcepath src src/splendor/main/SplendorGame.java src/splendor/test/AutomatedGameTest.java

if [ $? -eq 0 ]; then
    echo "Compilation complete. Running automated test..."
    echo ""
    java -cp "classes:lib/*" splendor.test.AutomatedGameTest "$@"
else
    echo "Compilation failed. Please check for errors."
    exit 1
fi
