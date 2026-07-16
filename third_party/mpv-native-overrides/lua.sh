#!/bin/bash -e

. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	make clean
	exit 0
else
	exit 255
fi

$0 clean

mycflags=(
	-fPIC
	-Dgetlocaledecpoint\\\(\\\)=\\\(46\\\)
	-Dlua_fseek
)

make CC="$CC" AR="$AR rc" RANLIB="$RANLIB" \
	MYCFLAGS="${mycflags[*]}" \
	PLAT=linux LUA_T= LUAC_T= -j"$cores"

# GNU install accepts the old TO_BIN=/dev/null trick, BSD install does not.
# Disabling INSTALL_EXEC is portable and avoids installing the unused lua tools.
make INSTALL="${INSTALL:-install}" INSTALL_EXEC=true \
	INSTALL_TOP="$prefix_dir" TO_BIN= install

mkdir -p "$prefix_dir/lib/pkgconfig"
make pc >"$prefix_dir/lib/pkgconfig/lua.pc"
cat >>"$prefix_dir/lib/pkgconfig/lua.pc" <<'EOF'
Name: Lua
Description:
Version: ${version}
Libs: -L${libdir} -llua
Cflags: -I${includedir}
EOF
