#!/bin/bash

set -e

APPBASE="build/macos-x64/RuneLite.app"

build() {
    pushd native
    cmake -DCMAKE_OSX_ARCHITECTURES=x86_64 -B build-x64 .
    cmake --build build-x64 --config Release
    popd

    source .jdk-versions.sh

    rm -rf build/macos-x64
    mkdir -p build/macos-x64

    if ! [ -f mac64_jre.tar.gz ] ; then
        curl -Lo mac64_jre.tar.gz $MAC_AMD64_LINK
    fi

    echo "$MAC_AMD64_CHKSUM  mac64_jre.tar.gz" | shasum -c

    mkdir -p $APPBASE/Contents/{MacOS,Resources}

    cp native/build-x64/src/RuneLite $APPBASE/Contents/MacOS/
    cp target/RuneLite.jar $APPBASE/Contents/Resources/
    cp packr/macos-x64-config.json $APPBASE/Contents/Resources/config.json
    cp target/filtered-resources/Info.plist $APPBASE/Contents/
    cp osx/runelite.icns $APPBASE/Contents/Resources/icons.icns

    tar zxf mac64_jre.tar.gz
    jlink --module-path "jdk-$MAC_AMD64_VERSION/jmods" --add-modules java.base,java.compiler,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.se,java.security.jgss,java.security.sasl,java.smartcardio,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.accessibility,jdk.charsets,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.dynalink,jdk.httpserver,jdk.internal.vm.ci,jdk.internal.vm.compiler,jdk.internal.vm.compiler.management,jdk.jdwp.agent,jdk.jfr,jdk.jsobject,jdk.localedata,jdk.management,jdk.management.agent,jdk.management.jfr,jdk.naming.dns,jdk.naming.rmi,jdk.net,jdk.sctp,jdk.security.auth,jdk.security.jgss,jdk.unsupported,jdk.xml.dom,jdk.zipfs,jdk.jartool --no-man-pages --compress=2 --output $APPBASE/Contents/Resources/jre

    echo Setting world execute permissions on RuneLite
    pushd $APPBASE
    chmod g+x,o+x Contents/MacOS/RuneLite
    popd

    otool -l $APPBASE/Contents/MacOS/RuneLite
}

dmg() {
    SIGNING_IDENTITY="Developer ID Application"
    codesign -f -s "${SIGNING_IDENTITY}" --entitlements osx/signing.entitlements --options runtime $APPBASE || true

    # create-dmg exits with an error code due to no code signing, but is still okay
    # note we use Adam-/create-dmg as upstream does not support UDBZ
    create-dmg --format UDBZ $APPBASE . || true
    mv RuneLite\ *.dmg RuneLite-x64.dmg

    # dump for CI
    hdiutil imageinfo RuneLite-x64.dmg

    if ! hdiutil imageinfo RuneLite-x64.dmg | grep -q "Format: UDBZ" ; then
        echo "Format of resulting dmg was not UDBZ, make sure your create-dmg has support for --format"
        exit 1
    fi

    if ! hdiutil imageinfo RuneLite-x64.dmg | grep -q "Apple_HFS" ; then
        echo Filesystem of dmg is not Apple_HFS
        exit 1
    fi

    # Notarize app
    if xcrun notarytool submit RuneLite-x64.dmg --wait --keychain-profile "AC_PASSWORD" ; then
        xcrun stapler staple RuneLite-x64.dmg
    fi
}

while test $# -gt 0; do
  case "$1" in
    --build)
      build
      shift
      ;;
    --dmg)
      dmg
      shift
      ;;
    *)
      break
      ;;
  esac
done