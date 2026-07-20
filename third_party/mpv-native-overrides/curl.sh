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
	--with-mbedtls="$prefix_dir" \
	--with-nghttp2="$prefix_dir" \
	--with-zlib="$toolchain/sysroot/usr" \
	--without-nghttp3 \
	--without-ngtcp2 \
	--without-quiche \
	--without-libpsl \
	--without-libidn2 \
	--without-brotli \
	--without-zstd \
	--enable-threaded-resolver \
	--enable-alt-svc \
	--enable-hsts \
	--disable-ftp \
	--disable-file \
	--disable-ldap \
	--disable-ldaps \
	--disable-rtsp \
	--disable-dict \
	--disable-telnet \
	--disable-tftp \
	--disable-pop3 \
	--disable-imap \
	--disable-smb \
	--disable-smtp \
	--disable-gopher \
	--disable-mqtt \
	--disable-ipfs \
	--disable-websockets \
	--disable-manual \
	--disable-docs

make -j"$cores"
make DESTDIR="$prefix_dir" install
