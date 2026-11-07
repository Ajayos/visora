#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
root="$PWD"
ndk="${ANDROID_NDK_HOME:-$root/.tools/android-sdk/ndk/27.2.12479018}"
case "$(uname -s)" in Darwin) host=darwin-x86_64;; Linux) host=linux-x86_64;; *) echo 'Android NDK host unsupported'; exit 1;; esac
toolchain="$ndk/toolchains/llvm/prebuilt/$host/bin"
export CC="$toolchain/armv7a-linux-androideabi28-clang" AR="$toolchain/llvm-ar" RANLIB="$toolchain/llvm-ranlib" STRIP="$toolchain/llvm-strip"
work="$root/.tools/ssh-build"
mkdir -p "$work"
cd "$work"
cat > localoptions.h.next <<'OPTIONS'
#define DROPBEAR_SVR_PASSWORD_AUTH 0
#define DROPBEAR_SVR_PAM_AUTH 0
#define DROPBEAR_SVR_LOCALTCPFWD 0
#define DROPBEAR_SVR_REMOTETCPFWD 0
#define DROPBEAR_SVR_LOCALSTREAMFWD 0
#define DROPBEAR_SVR_REMOTESTREAMFWD 0
#define DROPBEAR_SVR_AGENTFWD 0
#define DROPBEAR_X11FWD 0
#define DROPBEAR_SFTPSERVER 0
#define DEFAULT_PATH "/system/bin:/system/xbin"
#define COMPAT_USER_SHELLS "/system/bin/sh"
OPTIONS
if ! cmp -s localoptions.h.next localoptions.h; then mv localoptions.h.next localoptions.h; else rm localoptions.h.next; fi
if [ ! -f Makefile ]; then
ac_cv_func_getusershell=no CFLAGS='-Os -fPIE' LDFLAGS='-pie' "$root/third_party/dropbear/configure" --host=arm-linux-androideabi --disable-zlib --disable-syslog --enable-bundled-libtom --disable-lastlog --disable-utmp --disable-utmpx --disable-wtmp --disable-wtmpx --disable-loginfunc
fi
make -j4 PROGRAMS='dropbear dropbearkey'
mkdir -p "$root/app/src/main/jniLibs/armeabi-v7a"
"$STRIP" --strip-unneeded dropbear dropbearkey
cp dropbear "$root/app/src/main/jniLibs/armeabi-v7a/libvisora_ssh.so"
cp dropbearkey "$root/app/src/main/jniLibs/armeabi-v7a/libvisora_sshkey.so"

"$CC" -Os -fPIE -pie -fstack-protector-strong -Wl,-z,relro,-z,now "$root/scripts/ssh_exec.c" -o "$root/app/src/main/jniLibs/armeabi-v7a/libvisora_sshrun.so"
"$STRIP" --strip-unneeded "$root/app/src/main/jniLibs/armeabi-v7a/libvisora_sshrun.so"
