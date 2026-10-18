#!/usr/bin/env bash
# Arranca un emulador Android, ejecuta las pruebas instrumentadas y guarda los logs de cada fase.
set -u
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
IMG="system-images;android-34;google_apis;x86_64"
export ANDROID_AVD_HOME="$HOME/.android/avd"
mkdir -p "$ANDROID_AVD_HOME"
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a phases.log; }

log "SDK en $SDK"
log "liberando disco: $(df -h / | tail -1)"
sudo rm -rf /usr/share/dotnet /opt/ghc /usr/local/.ghcup /usr/local/share/boost /usr/share/swift /opt/hostedtoolcache/CodeQL 2>/dev/null || true
log "tras liberar: $(df -h / | tail -1)"
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDM="$SDK/cmdline-tools/latest/bin/avdmanager"
[ -x "$SDKM" ] || { log "sin sdkmanager"; echo none > device.exit; exit 0; }

log "instalando imagen y emulador"
yes | "$SDKM" --install "$IMG" "emulator" "platform-tools" > sdk.log 2>&1
log "sdkmanager terminó con $?"

log "creando AVD en $ANDROID_AVD_HOME"
echo no | "$AVDM" create avd -n smoke -k "$IMG" --device "pixel_6" --force >> sdk.log 2>&1
log "avdmanager terminó con $? ($(ls "$ANDROID_AVD_HOME"))"
# Partición de datos pequeña: la app de pruebas apenas ocupa nada.
CFG="$ANDROID_AVD_HOME/smoke.avd/config.ini"
sed -i '/^disk.dataPartition.size/d' "$CFG" 2>/dev/null
echo "disk.dataPartition.size=2048M" >> "$CFG"

log "arrancando emulador (kvm: $(ls -l /dev/kvm 2>&1))"
"$SDK/emulator/emulator" -avd smoke -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim \
  -no-snapshot -camera-back none -camera-front none -memory 3072 -partition-size 2048 > emulator.log 2>&1 &
ADB="$SDK/platform-tools/adb"
timeout 600 "$ADB" wait-for-device || { log "el emulador no aparece"; tail -20 emulator.log | tee -a phases.log; echo none > device.exit; exit 0; }
for i in $(seq 1 120); do
  [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 5
done
log "arranque completado: $("$ADB" shell getprop sys.boot_completed | tr -d '\r') tras $((i*5)) s"
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
"$ADB" shell input keyevent 82 || true
"$ADB" logcat -c || true
# El log se va guardando mientras corren las pruebas (si el emulador o la app se caen, no se pierde).
"$ADB" logcat -v time > logcat-live.txt 2>&1 &
LOGCAT_PID=$!

log "ejecutando pruebas"
timeout 1200 ./gradlew :androidApp:connectedDebugAndroidTest --stacktrace \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true > device.log 2>&1
code=$?
echo "$code" > device.exit
log "pruebas terminadas con código $code"
"$ADB" logcat -d > logcat.txt 2>/dev/null || true
kill $LOGCAT_PID 2>/dev/null || true
grep -E "AndroidRuntime|FATAL|Process: dev.surco|Caused by|at dev\.surco|ANR in|Low memory|lowmemorykiller|SURCO" logcat-live.txt | tail -400 > logcat-errors.txt || true
mkdir -p device-shots
"$ADB" pull /sdcard/Android/data/dev.surco/files/shots device-shots >/dev/null 2>&1 || true
log "capturas: $(find device-shots -name '*.png' | wc -l)"
"$ADB" emu kill || true
exit 0
