#!/usr/bin/env bash
set -euo pipefail

ARCHIVE_PATH="build/iosApp.xcarchive"
EXPORT_PATH="build/output"

APP_PATH=$(find "$ARCHIVE_PATH/Products/Applications" -name "*.app" -maxdepth 1 | head -n 1)
APP_NAME=$(basename "$APP_PATH")
IPA_NAME="${APP_NAME%.app}.ipa"

echo "Found app: $APP_NAME"

rm -rf "$EXPORT_PATH/Payload"
mkdir -p "$EXPORT_PATH/Payload"
cp -r "$APP_PATH" "$EXPORT_PATH/Payload/"

cd "$EXPORT_PATH"
rm -f "$IPA_NAME"
zip -r "$IPA_NAME" Payload
rm -rf Payload

echo "✅ IPA created: $EXPORT_PATH/$IPA_NAME"
