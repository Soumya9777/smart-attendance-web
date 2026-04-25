#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."
mkdir -p data

if [ -f data/attendance-keystore.p12 ]; then
  echo "HTTPS certificate already exists: data/attendance-keystore.p12"
  echo "Delete that file first if your Wi-Fi IP address changed."
  exit 0
fi

IP_ADDRESS="${1:-}"
if [ -z "$IP_ADDRESS" ]; then
  IP_ADDRESS="$(ipconfig getifaddr en0 2>/dev/null || true)"
fi
if [ -z "$IP_ADDRESS" ]; then
  IP_ADDRESS="$(ipconfig getifaddr en1 2>/dev/null || true)"
fi
if [ -z "$IP_ADDRESS" ]; then
  echo "Could not detect Wi-Fi IP address."
  echo "Run again like: scripts/setup-https.sh 192.168.1.10"
  exit 1
fi

keytool -genkeypair \
  -alias attendance \
  -keyalg RSA \
  -keysize 2048 \
  -validity 365 \
  -keystore data/attendance-keystore.p12 \
  -storetype PKCS12 \
  -storepass attendance123 \
  -keypass attendance123 \
  -dname "CN=$IP_ADDRESS, OU=NIST, O=NIST, L=Berhampur, ST=Odisha, C=IN" \
  -ext "SAN=IP:$IP_ADDRESS,DNS:localhost"

echo "HTTPS certificate created for: $IP_ADDRESS"
echo "Restart the app and use: https://$IP_ADDRESS:8080/student-login"
