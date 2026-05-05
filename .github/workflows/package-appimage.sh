#!/usr/bin/env bash
# Build an AppImage + .zsync for ArcherBC2 (AppImageUpdate-compatible).
#
# Usage: package-appimage.sh <source_dir> <arch_suffix> [app_version]
#   source_dir   — directory with profedit.jar, update.jar, skins/, etc.
#   arch_suffix  — x86_64 | aarch64
#   app_version  — version string; falls back to <source_dir>/version.txt or "0.0.0"
#
# When GITHUB_REPOSITORY is set the AppImage is built with gh-releases-zsync
# update info and a .zsync sidecar is generated alongside the AppImage.
set -euo pipefail

SOURCE_DIR="${1:?Usage: package-appimage.sh <source_dir> <arch_suffix> [app_version]}"
ARCH_SUFFIX="${2:?}"
APP_VERSION="${3:-}"

APP_NAME="ArcherBC2"
APP_ID="archerbc2"
APPIMAGE_TOOL_URL="https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-${ARCH_SUFFIX}.AppImage"

# Resolve version
if [ -z "$APP_VERSION" ]; then
  if [ -f "$SOURCE_DIR/version.txt" ]; then
    APP_VERSION="$(cat "$SOURCE_DIR/version.txt")"
  else
    APP_VERSION="0.0.0"
  fi
fi

APPIMAGE_FILENAME="${APP_NAME}-${APP_VERSION}-${ARCH_SUFFIX}.AppImage"

# zsync / update-info (only when running in GitHub Actions or GITHUB_REPOSITORY is set)
REPO_SLUG="${GITHUB_REPOSITORY:-}"
if [ -n "$REPO_SLUG" ]; then
  OWNER="${REPO_SLUG%%/*}"
  REPO="${REPO_SLUG##*/}"
  UPDATE_INFO="gh-releases-zsync|${OWNER}|${REPO}|latest|${APPIMAGE_FILENAME}.zsync"
  ZSYNC_URL="https://github.com/${REPO_SLUG}/releases/latest/download/${APPIMAGE_FILENAME}"
else
  UPDATE_INFO=""
  ZSYNC_URL=""
  echo "WARNING: GITHUB_REPOSITORY not set — building without zsync update info"
fi

mkdir -p artifacts/appimage

# ── appimagetool ──────────────────────────────────────────────────────────────
if [ ! -x /tmp/appimagetool ]; then
  echo "Downloading appimagetool (${ARCH_SUFFIX})..."
  curl -fsSL "$APPIMAGE_TOOL_URL" -o /tmp/appimagetool
  chmod +x /tmp/appimagetool
fi

# ── AppDir ────────────────────────────────────────────────────────────────────
APPDIR="$(mktemp -d /tmp/AppDir.XXXXXX)"
trap 'rm -rf "$APPDIR"' EXIT

APP_SHARE="$APPDIR/usr/share/$APP_ID"
mkdir -p "$APP_SHARE"
mkdir -p "$APPDIR/usr/share/applications"
mkdir -p "$APPDIR/usr/share/icons/hicolor/256x256/apps"

# Application payload
cp "$SOURCE_DIR/profedit.jar" "$APP_SHARE/"
[ -f "$SOURCE_DIR/update.jar" ]                    && cp "$SOURCE_DIR/update.jar" "$APP_SHARE/"
[ -d "$SOURCE_DIR/skins" ]                         && cp -r "$SOURCE_DIR/skins" "$APP_SHARE/"
[ -f "$SOURCE_DIR/a7p-associations.properties" ]   && cp "$SOURCE_DIR/a7p-associations.properties" "$APP_SHARE/"

# Icon — search common locations
ICON_SRC=""
for candidate in \
  "$SOURCE_DIR/icon.png" \
  "icon.png" \
  "assets/icon.png" \
  "../icon.png"
do
  if [ -f "$candidate" ]; then
    ICON_SRC="$candidate"
    break
  fi
done

ICON_DEST="$APPDIR/usr/share/icons/hicolor/256x256/apps/${APP_ID}.png"
if [ -n "$ICON_SRC" ]; then
  cp "$ICON_SRC" "$ICON_DEST"
  echo "icon: $ICON_SRC"
else
  # Minimal 1x1 transparent PNG fallback so appimagetool does not abort
  printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\nIDATx\x9cc\x00\x01\x00\x00\x05\x00\x01\r\n-\xb4\x00\x00\x00\x00IEND\xaeB`\x82' \
    > "$ICON_DEST"
  echo "WARNING: no icon found — using 1x1 placeholder"
fi

# Desktop file
cat > "$APPDIR/usr/share/applications/${APP_ID}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=${APP_NAME}
Exec=AppRun %f
Icon=${APP_ID}
Comment=A professional editor for A7P profiles
Categories=Utility;
MimeType=application/x-a7p;
X-AppImage-Version=${APP_VERSION}
EOF

# Root symlinks required by the AppImage spec
ln -sf "usr/share/applications/${APP_ID}.desktop" "$APPDIR/${APP_ID}.desktop"
ln -sf "usr/share/icons/hicolor/256x256/apps/${APP_ID}.png" "$APPDIR/${APP_ID}.png"
ln -sf "usr/share/icons/hicolor/256x256/apps/${APP_ID}.png" "$APPDIR/.DirIcon"

# AppRun
cat > "$APPDIR/AppRun" <<'APPRUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec java -jar "$HERE/usr/share/archerbc2/profedit.jar" "$@"
APPRUN
chmod +x "$APPDIR/AppRun"

# ── Build AppImage ─────────────────────────────────────────────────────────────
APPIMAGE_OUT="artifacts/appimage/${APPIMAGE_FILENAME}"
echo "Building ${APPIMAGE_FILENAME}..."
if [ -n "$UPDATE_INFO" ]; then
  ARCH="${ARCH_SUFFIX}" /tmp/appimagetool --updateinformation "$UPDATE_INFO" "$APPDIR" "$APPIMAGE_OUT"
else
  ARCH="${ARCH_SUFFIX}" /tmp/appimagetool "$APPDIR" "$APPIMAGE_OUT"
fi
echo "AppImage: $APPIMAGE_OUT"

# ── .zsync sidecar ────────────────────────────────────────────────────────────
if [ -n "$ZSYNC_URL" ]; then
  if command -v zsyncmake &>/dev/null; then
    zsyncmake -u "$ZSYNC_URL" -o "${APPIMAGE_OUT}.zsync" "$APPIMAGE_OUT"
    echo "zsync:    ${APPIMAGE_OUT}.zsync"
  else
    echo "WARNING: zsyncmake not found — install the 'zsync' package (apt install zsync)"
  fi
fi

echo ""
ls -lh artifacts/appimage/
