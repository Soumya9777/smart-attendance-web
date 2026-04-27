#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."
CP="lib/*:out"
mkdir -p out
find src -name "*.java" > sources.txt
javac -d out -cp "$CP" @sources.txt
rm sources.txt
