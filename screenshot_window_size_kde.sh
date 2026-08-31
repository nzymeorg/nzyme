set -euo pipefail

WIDTH=1920
HEIGHT=1280

for i in 3 2 1; do
    echo "Click target window... $i"
    sleep 1
done

SCRIPT=$(mktemp /tmp/kwin-resize-XXXXXX.js)
cat > "$SCRIPT" <<EOF
const c = workspace.activeWindow || workspace.activeClient;
if (c) {
    let q = Object.assign({}, c.frameGeometry);
    q.width = ${WIDTH};
    q.height = ${HEIGHT};
    c.frameGeometry = q;
}
EOF

ID=$(qdbus6 org.kde.KWin /Scripting org.kde.kwin.Scripting.loadScript "$SCRIPT" "resize-$$-$RANDOM")
qdbus6 org.kde.KWin "/Scripting/Script${ID}" org.kde.kwin.Script.run
qdbus6 org.kde.KWin "/Scripting/Script${ID}" org.kde.kwin.Script.stop
rm -f "$SCRIPT"
