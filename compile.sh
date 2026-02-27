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

# Compile all Java source files using sourcepath
javac -d classes -cp "lib/*:classes" -sourcepath src src/splendor/main/SplendorGame.java

if [ $? -eq 0 ]; then
    echo "Compilation complete. Class files are in the classes directory."
else
    echo "Compilation failed. Please check for errors."
    exit 1
fi
