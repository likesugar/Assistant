#!/bin/bash
# Assistant CI 构建: aapt2 + ecj + d8(R8) + zipalign + apksigner
set -e
cd "$(dirname "$0")/.."
ROOT=$(pwd)
VC=${VC:-1}
VN=${VN:-1.0}
BT="$ANDROID_HOME/build-tools/34.0.0"
SDK="$ANDROID_HOME/platforms/android-36/android.jar"
PROJ="$ROOT"
V="$ROOT/ci/vendor"

# token 注入 (公开仓库不落盘, 由 Actions Secret 提供)
if [ -n "$GH_PERSONAL_TOKEN" ]; then
  sed -i "s/GITHUB_TOKEN_PLACEHOLDER/$GH_PERSONAL_TOKEN/" app/src/main/java/com/ziyuan/Assistant/SettingsPageView.java
fi

rm -rf build_out; mkdir -p build_out/gen build_out/classes build_out/dex
echo "[1/6] aapt2 compile..."
"$BT/aapt2" compile --dir app/src/main/res -o build_out/res.zip
echo "[2/6] aapt2 link..."
"$BT/aapt2" link -o build_out/app-unsigned.apk \
    -I "$SDK" --manifest app/src/main/AndroidManifest.xml \
    -R build_out/res.zip $(ls $V/mdc_res/*.zip | sed 's/^/-R /' | tr "\n" " ") \
    --java build_out/gen --auto-add-overlay --emit-ids build_out/ids.txt \
    --min-sdk-version 24 --target-sdk-version 36 \
    -c zh-rCN --preferred-density xxhdpi \
    -A app/src/main/assets \
    --version-code "$VC" --version-name "$VN"

python3 - <<'PYGEN'
import os
gen="build_out/gen"; rj=None
for root,_,fs in os.walk(gen):
    for f in fs:
        if f=="R.java": rj=os.path.join(root,f)
stub="package com.yausername.youtubedl_android;\npublic final class R {\n  public static final class raw {\n"
if os.path.exists("build_out/ids.txt"):
    for line in open("build_out/ids.txt"):
        line=line.strip(); idx=line.find(":raw/")
        if idx>=0:
            rest=line[idx+5:]; name,val=rest.split("=")
            stub+="    public static final int %s = %s;\n"%(name.strip(),val.strip())
            if name.strip()=="raw_ytdlp": stub+="    public static final int ytdlp = %s;\n"%val.strip()
stub+="  }\n}\n"
od="build_out/gen_stub/com/yausername/youtubedl_android"
os.makedirs(od,exist_ok=True); open(od+"/R.java","w").write(stub)
print("R stub written")
PYGEN
# sora 库 R: 主R换包名(资源值同表, 运行时命中) — 含 string/style/styleable 内部类
SORA_R=$(find build_out/gen -name R.java | head -1)
mkdir -p build_out/gen_stub/io/github/rosemoe/sora
sed 's/package com\.ziyuan\.Assistant;/package io.github.rosemoe.sora;/' "$SORA_R" > build_out/gen_stub/io/github/rosemoe/sora/R.java
echo "sora full R stub written"

echo "[3/6] ecj compile..."
RJ=$(find build_out/gen -name R.java)
mkdir -p build_out/classes
java -jar $V/tools/ecj.jar -source 1.8 -target 1.8 -encoding UTF-8 -proc:none -nowarn \
    -cp "$SDK:$V/mdc_all_classes.jar:$V/ffk_classes.jar:$(ls $V/ffk_sex/*.jar | tr '\n' ':')$(ls $V/ydl/*.jar | tr '\n' ':')app/libs/sora-editor-classes.jar:app/libs/collection-jvm.jar:app/libs/annotation-jvm.jar:app/libs/apksig.jar:app/libs/pinyin4j-2.5.0.jar" \
    -d build_out/classes "$RJ" $(find build_out/gen_stub -name R.java) \
    app/src/main/java/com/ziyuan/Assistant/*.java \
    app/src/main/java/com/ziyuan/Assistant/cal/*.java \
    app/src/main/java/com/ziyuan/Assistant/xhs/*.java \
    app/src/main/java/xyz/doikki/videoplayer/*/*.java \
    app/src/main/java/xyz/doikki/videocontroller/*.java \
    app/src/main/java/xyz/doikki/videocontroller/component/*.java \
    app/src/main/java/tv/danmaku/ijk/media/player/*.java \
    app/src/main/java/tv/danmaku/ijk/media/player/*/*.java

echo "[4/6] d8 dex..."
find build_out/classes -name "*.class" > build_out/classlist.txt
java -Xmx3g -cp "$BT/lib/d8.jar" com.android.tools.r8.R8 --release \
    --lib "$SDK" --min-api 24 --pg-conf ci/r8-rules.pro \
    --output build_out/dex @build_out/classlist.txt \
    $V/ydl/*.jar app/libs/apksig.jar app/libs/pinyin4j-2.5.0.jar \
    app/libs/sora-editor-classes.jar app/libs/collection-jvm.jar app/libs/annotation-jvm.jar \
    $V/mdc_all_classes.jar $V/ffk_classes.jar $V/ffk_sex/*.jar

echo "[5/6] pack + zipalign..."
cd build_out/dex && zip -q ../../app-unsigned.apk classes*.dex && cd ../..
mkdir -p build_out/lib/arm64-v8a
cp $V/lib/arm64-v8a/*.so build_out/lib/arm64-v8a/
(cd build_out && zip -q -9 -r app-unsigned.apk lib)
"$BT/zipalign" -f 4 build_out/app-unsigned.apk build_out/app-aligned.apk

echo "[6/6] sign..."
"$BT/apksigner" sign --ks $V/keystore/debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out Assistant.apk build_out/app-aligned.apk
"$BT/apksigner" verify Assistant.apk
echo "=== BUILD OK: Assistant.apk ==="
