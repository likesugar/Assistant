#!/bin/bash
# 小工具首页复刻（com.ziyuan.Assistant）：aapt2 + ecj + d8 + zipalign + apksigner
set -e
cd /home/z/my-project/android_build
BT=/home/z/my-project/android_build/build-tools/34.0.0
SDK=/home/z/my-project/android_build/platforms/android-36/android.jar
PROJ="/home/z/my-project/xgjhome"
YT=$(ls /home/z/my-project/android_build/ydl/*.jar | tr "\n" ":"); YT=${YT%:}
VC=${1:-1}
VN=${2:-"1.0"}

rm -rf build_out_xgj
mkdir -p build_out_xgj/gen build_out_xgj/classes build_out_xgj/dex

echo "[1/6] aapt2 compile..."
"$BT/aapt2" compile --dir "$PROJ/app/src/main/res" -o build_out_xgj/res.zip

echo "[2/6] aapt2 link..."
"$BT/aapt2" link -o build_out_xgj/app-unsigned.apk \
    -I "$SDK" \
    --manifest "$PROJ/app/src/main/AndroidManifest.xml" \
    -R build_out_xgj/res.zip $(ls /home/z/my-project/android_build/mdc/mdc_res/*.zip | sed 's/^/-R /' | tr "\n" " ") \
    --java build_out_xgj/gen --auto-add-overlay --emit-ids /home/z/my-project/android_build/build_out_xgj/ids.txt \
    --min-sdk-version 24 --target-sdk-version 36 \
    -c zh-rCN --preferred-density xxhdpi \
    -A "$PROJ/app/src/main/assets" \
    --version-code "$VC" --version-name "$VN"


# 为库类生成 R 桩(库 classes 引用自身包名的 R)
python3 - <<'PYGEN'
import re, os
gen = "/home/z/my-project/android_build/build_out_xgj/gen"
rj = None
for root, _, files in os.walk(gen):
    for f in files:
        if f == "R.java":
            rj = os.path.join(root, f)
if os.path.exists("/home/z/my-project/android_build/build_out_xgj/ids.txt"):
    stub = "package com.yausername.youtubedl_android;\npublic final class R {\n"
    stub += "  public static final class raw {\n"
    for line in open("/home/z/my-project/android_build/build_out_xgj/ids.txt"):
        line = line.strip()
        idx = line.find(":raw/")
        if idx >= 0:
            rest = line[idx + 5:]
            name, val = rest.split("=")
            stub += "    public static final int %s = %s;\n" % (name.strip(), val.strip())
            if name.strip() == "raw_ytdlp":
                stub += "    public static final int ytdlp = %s;\n" % val.strip()  # 库内别名: init_ytdlp 引用 R.raw.ytdlp
    stub += "  }\n}\n"
    outdir = "/home/z/my-project/android_build/build_out_xgj/gen_stub/com/yausername/youtubedl_android"
    os.makedirs(outdir, exist_ok=True)
    open(outdir + "/R.java", "w").write(stub)
    print("R stub written")
PYGEN

echo "[3a/6] R classes (for kotlinc)..."
RJ=$(find build_out_xgj/gen -name R.java)
mkdir -p build_out_xgj/gen_classes
java -jar tools/ecj.jar -source 1.8 -target 1.8 -encoding UTF-8 -proc:none -nowarn \
    -cp "$SDK" -d build_out_xgj/gen_classes "$RJ" $(find build_out_xgj/gen_stub /home/z/my-project/android_build/stubs -name R.java)
# sora 库内 R: 主R换包名(资源值同表, 运行时命中)
mkdir -p build_out_xgj/gen_stub/io/github/rosemoe/sora
sed 's/package com\.ziyuan\.Assistant;/package io.github.rosemoe.sora;/' "$RJ" > build_out_xgj/gen_stub/io/github/rosemoe/sora/R_sora.java
python3 - <<'PYR'
import re
s=open('build_out_xgj/gen_stub/io/github/rosemoe/sora/R_sora.java').read()
s=s.replace('public static final class', 'public static final class', 1)
open('build_out_xgj/gen_stub/io/github/rosemoe/sora/R.java','w').write(s)
PYR
rm -f build_out_xgj/gen_stub/io/github/rosemoe/sora/R_sora.java

echo "[3b/6] kotlin compile (skip if no kt)..."
KTS=$(ls $PROJ/app/src/main/java/com/ziyuan/Assistant/*.kt 2>/dev/null) || true
KT=/home/z/my-project/android_build/kotlin-compiler/kotlinc
if [ -n "$KTS" ]; then
java -jar $KT/lib/kotlin-compiler.jar -jvm-target 1.8 -nowarn \
    -cp "$SDK:build_out_xgj/gen_classes" -d build_out_xgj/classes $KTS
fi

echo "[3/6] ecj compile..."
RJ=$(find build_out_xgj/gen -name R.java)
java -jar tools/ecj.jar -source 1.8 -target 1.8 -encoding UTF-8 -proc:none -nowarn \
    -cp "$SDK:build_out_xgj/classes:$YT:/home/z/my-project/xgjhome/app/libs/sora-editor-classes.jar:/home/z/my-project/xgjhome/app/libs/apksig.jar:/home/z/my-project/xgjhome/app/libs/pinyin4j-2.5.0.jar:/home/z/my-project/android_build/mdc/classes_out/all-classes.jar:/home/z/my-project/android_build/ffk_out/classes.jar:/home/z/my-project/android_build/ffk_out/sex/smart-exception-java-0.2.1.jar:/home/z/my-project/android_build/ffk_out/sex/smart-exception-common-0.2.1.jar" -d build_out_xgj/classes \
    "$RJ" \
    $(find build_out_xgj/gen_stub -name R.java) \
    "$PROJ"/app/src/main/java/com/ziyuan/Assistant/*.java \
    $PROJ/app/src/main/java/com/ziyuan/Assistant/cal/*.java \
    $PROJ/app/src/main/java/com/ziyuan/Assistant/xhs/*.java \
    $PROJ/app/src/main/java/xyz/doikki/videoplayer/*/*.java \
    $PROJ/app/src/main/java/xyz/doikki/videocontroller/*.java \
    $PROJ/app/src/main/java/xyz/doikki/videocontroller/component/*.java \
    $PROJ/app/src/main/java/tv/danmaku/ijk/media/player/*.java \
    $PROJ/app/src/main/java/tv/danmaku/ijk/media/player/*/*.java

echo "[4/6] d8 dex..."
mkdir -p build_out_xgj/classes_kstd
unzip -oq $KT/lib/kotlin-stdlib.jar -d build_out_xgj/classes_kstd 2>/dev/null || true
find build_out_xgj/classes -name "*.class" > build_out_xgj/classlist.txt; find build_out_xgj/gen_classes/io -name "*.class" >> build_out_xgj/classlist.txt

java -Xmx3g -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --release \
    --lib "$SDK" --min-api 24 \
    --pg-conf /home/z/my-project/android_build/r8-rules.pro \
    --output build_out_xgj/dex \
    @build_out_xgj/classlist.txt $(for j in /home/z/my-project/android_build/ydl/*.jar /home/z/my-project/xgjhome/app/libs/sora-editor-classes.jar /home/z/my-project/xgjhome/app/libs/apksig.jar /home/z/my-project/xgjhome/app/libs/pinyin4j-2.5.0.jar ; do echo -n "$j "; done)/home/z/my-project/android_build/mdc/classes_out/all-classes.jar /home/z/my-project/android_build/ffk_out/classes.jar /home/z/my-project/android_build/ffk_out/sex/smart-exception-java-0.2.1.jar /home/z/my-project/android_build/ffk_out/sex/smart-exception-common-0.2.1.jar

echo "[5/6] dex + zipalign..."
for d in build_out_xgj/dex/*.dex; do
    (cd build_out_xgj/dex && zip -q ../app-unsigned.apk "$(basename $d)")
done
mkdir -p build_out_xgj/lib/arm64-v8a
if [ -d "$PROJ/lib/arm64-v8a" ]; then cp "$PROJ"/lib/arm64-v8a/*.so build_out_xgj/lib/arm64-v8a/ 2>/dev/null || true; cp -n /home/z/my-project/android_build/ffk_out/jni/arm64-v8a/*.so build_out_xgj/lib/arm64-v8a/ 2>/dev/null || true; (cd build_out_xgj && zip -q -9 -r app-unsigned.apk lib); fi
# 极限压缩: so用xz字典压缩进包不可行(需解压到盘), 保持store; 其余资源已在aapt2压缩
"$BT/zipalign" -f 4 build_out_xgj/app-unsigned.apk build_out_xgj/app-aligned.apk

echo "[6/6] sign + verify..."
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out "/home/z/my-project/小工具首页_v$VN.apk" build_out_xgj/app-aligned.apk
"$BT/apksigner" verify "/home/z/my-project/小工具首页_v$VN.apk"

echo "=== DONE: /home/z/my-project/小工具首页_v$VN.apk ==="
