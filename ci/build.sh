#!/bin/bash
# Assistant CI 构建: aapt2 + ecj + d8(R8) + zipalign + apksigner
set -e
cd "$(dirname "$0")/.."
export ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
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
    --min-sdk-version 26 --target-sdk-version 36 \
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
    -cp "$SDK:$V/mdc_all_classes.jar:$V/ffk_classes.jar:$(ls $V/media3/*/classes.jar | tr '\n' ':')$V/media3/exifinterface/classes.jar:$V/media3/concurrent-futures.jar:$(ls $V/ffk_sex/*.jar | tr '\n' ':')$(ls $V/ydl/*.jar | tr '\n' ':')app/libs/sora-editor-classes.jar:app/libs/collection-jvm.jar:app/libs/annotation-jvm.jar:app/libs/xz-1.9.jar:app/libs/apksig.jar:app/libs/pinyin4j-2.5.0.jar" \
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
    --lib "$SDK" --lib $(ls $V/media3/*/classes.jar | tr '\n' ' ') --lib $V/media3/exifinterface/classes.jar --lib $V/media3/concurrent-futures.jar --lib $V/media3/annotation_stub.jar --lib $V/media3/lib/guava.jar --lib $V/media3/lib/errorprone.jar --lib $V/media3/lib/jsr305.jar --lib $V/media3/lib/checker-qual.jar --lib $V/media3/lib/kotlin-annotations.jar --lib $V/media3/lib/kotlin-stdlib-1.9.24.jar --lib $V/media3/lib/versionedparcelable.jar --lib $V/media3/lib/commons-compress-1.26.1.jar --lib $V/media3/lib/commons-io-2.16.1.jar --lib $V/media3/lib/commons-codec-1.16.1.jar --lib $V/media3/lib/commons-lang3-3.14.0.jar --lib $V/media3/lib/xz-1.9.jar --lib $V/media3/lib/collection-jvm-1.5.0.jar --lib $V/media3/lib/zstd-jni-1.5.5-11.jar --lib $V/media3/lib/brotli-dec-0.1.2.jar --lib $V/media3/lib/asm-9.7.jar --lib $V/media3/lib/jetbrains-annotations-24.1.0.jar --lib $V/media3/lib/collection-annotations.jar --min-api 26 --pg-conf ci/r8-rules.pro \
    --output build_out/dex @build_out/classlist.txt \
    $V/ydl/*.jar app/libs/apksig.jar app/libs/pinyin4j-2.5.0.jar \
    app/libs/sora-editor-classes.jar app/libs/collection-jvm.jar app/libs/annotation-jvm.jar app/libs/xz-1.9.jar \
    $V/mdc_all_classes.jar $V/ffk_classes.jar $V/ffk_sex/*.jar

echo "[4.5/6] 剥离 MethodParameters + d8 脱糖编 Media3 内核 (min-api 24, 多dex)..."
# 两个关键点：
# 1) 仓库内 jar 保持原始(供 ecj/R8 解析)；仅构建时剥离出临时副本供 d8 用。
#    d8 8.2.2 解析带 MethodParameters 的 AIDL Binder 类(如 BundleableByteArray$InProcessBinder)会内部 NPE，
#    该属性是编译期元数据，剥离后不影响运行期逻辑。
# 2) 必须用 d8(而非旧 dx)：dx 不做 Java8 lambda 脱糖，media3/guava 的 invokedynamic 被原样保留，
#    运行期调 LambdaMetafactory.metafactory 导致 NoSuchMethodError 闪退。
#    d8 --min-api 24 会把 lambda 全部改写为合成类，与设备 API 无关。
ASMJAR=$V/media3/lib/asm-9.7.jar
rm -rf build_out/striptool build_out/m3jar build_out/m3
mkdir -p build_out/striptool build_out/m3jar build_out/m3 build_out/dex
javac -cp "$ASMJAR" -d build_out/striptool ci/StripAttrs.java
java -cp "build_out/striptool:$ASMJAR" StripAttrs $V/media3 build_out/m3jar
java -Xmx3g -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --min-api 24 --lib "$SDK" --output build_out/m3 \
    $(ls build_out/m3jar/media3-*/classes.jar) \
    build_out/m3jar/exifinterface/classes.jar \
    build_out/m3jar/concurrent-futures.jar \
    build_out/m3jar/annotation_stub.jar \
    build_out/m3jar/lib/guava.jar \
    build_out/m3jar/lib/kotlin-stdlib-1.9.24.jar \
    build_out/m3jar/lib/versionedparcelable.jar \
    build_out/m3jar/lib/commons-compress-1.26.1.jar \
    build_out/m3jar/lib/commons-io-2.16.1.jar \
    build_out/m3jar/lib/commons-codec-1.16.1.jar \
    build_out/m3jar/lib/commons-lang3-3.14.0.jar \
    build_out/m3jar/lib/xz-1.9.jar \
    build_out/m3jar/lib/collection-jvm-1.5.0.jar \
    build_out/m3jar/lib/zstd-jni-1.5.5-11.jar \
    build_out/m3jar/lib/brotli-dec-0.1.2.jar \
    build_out/m3jar/lib/asm-9.7.jar \
    build_out/m3jar/lib/jetbrains-annotations-24.1.0.jar \
    build_out/m3jar/lib/collection-annotations.jar
# d8 产出 classes.dex/classes2.dex/...，重命名为连续的 classes2.dex/classes3.dex/...
# 避免与 App 主 dex classes.dex 冲突；ART 按 classes.dex→classes2→classes3 顺序加载，缺失编号即停止
i=2
for f in $(ls build_out/m3/classes*.dex | sort -V); do
    mv "$f" "build_out/dex/classes$i.dex"
    i=$((i+1))
done
echo "Media3 二级 dex 数量: $((i-2))"

echo "[5/6] pack + zipalign..."
cd build_out/dex && zip ../app-unsigned.apk classes*.dex && cd ../..
mkdir -p build_out/lib/arm64-v8a
cp $V/lib/arm64-v8a/*.so build_out/lib/arm64-v8a/
(cd build_out && zip -q -9 -r app-unsigned.apk lib)
"$BT/zipalign" -f 4 build_out/app-unsigned.apk build_out/app-aligned.apk

echo "[6/6] sign..."
"$BT/apksigner" sign --ks $V/keystore/debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out Assistant.apk build_out/app-aligned.apk
"$BT/apksigner" verify Assistant.apk
unzip -l build_out/app-aligned.apk | grep -F "classes.dex" || echo "NO DEX IN ALIGNED"
unzip -l Assistant.apk | grep -F "classes.dex" || echo "NO DEX IN SIGNED"
echo "=== BUILD OK: Assistant.apk ==="
