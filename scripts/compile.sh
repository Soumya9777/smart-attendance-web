#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."
mkdir -p out
find src -name "*.java" > sources.txt
javac -d out @sources.txt
rm sources.txt
