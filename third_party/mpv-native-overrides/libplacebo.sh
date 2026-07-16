#!/bin/bash -e

. ../../include/path.sh

build=_build$ndk_suffix

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	rm -rf "$build"
	exit 0
else
	exit 255
fi

unset CC CXX
meson setup "$build" --cross-file "$prefix_dir/crossfile.txt" \
	-Dvk-proc-addr=enabled -Ddemos=false

ninja -C "$build" -j"$cores"
DESTDIR="$prefix_dir" ninja -C "$build" install

python3 - "$prefix_dir/lib/pkgconfig/libplacebo.pc" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
lines = []
for line in text.splitlines():
    if line.startswith("Libs:") and "-lc++" not in line.split():
        line += " -lc++"
    lines.append(line)
path.write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
