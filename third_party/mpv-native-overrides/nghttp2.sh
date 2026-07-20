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

export CFLAGS="${CFLAGS:-} -fPIC"
mkdir -p "$build"
cd "$build"

../configure \
	--host="$ndk_triple" \
	--prefix=/usr/local \
	--disable-shared \
	--enable-static \
	--disable-app \
	--disable-examples \
	--disable-hpack-tools

make -j"$cores"
make DESTDIR="$prefix_dir" install
