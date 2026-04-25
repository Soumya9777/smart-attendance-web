#!/usr/bin/env bash
set -eu

cd "$(dirname "$0")/.."

echo "Compiling project..."
mkdir -p out
find src -name "*.java" > sources.txt
javac -d out @sources.txt
rm sources.txt

echo "Building executable JAR..."
jar cvfm smartattendance.jar src/Manifest.txt -C out .

echo "Creating distribution package..."
rm -rf dist
mkdir -p dist/SmartAttendanceApp
mv smartattendance.jar dist/SmartAttendanceApp/

# Copy necessary runtime assets
cp -R data dist/SmartAttendanceApp/data
cp nist.png dist/SmartAttendanceApp/

# Create a macOS/Linux launcher
cat << 'EOF' > dist/SmartAttendanceApp/start.command
#!/usr/bin/env bash
cd "$(dirname "$0")"
java -jar smartattendance.jar
EOF
chmod +x dist/SmartAttendanceApp/start.command

# Create a Windows launcher
cat << 'EOF' > dist/SmartAttendanceApp/start.bat
@echo off
cd /d "%~dp0"
java -jar smartattendance.jar
EOF

echo "Deployment package ready in dist/SmartAttendanceApp/"
