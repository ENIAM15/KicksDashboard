@echo off
echo ===================================================
echo   Instalando Consen Dashboard en tu telefono
echo   Origen: Google Play Store (com.android.vending)
echo ===================================================

adb uninstall com.consen.autodash
adb install -r -i "com.android.vending" app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo [!] Fallo con app-debug.apk, intentando con ConsenAuto.apk en raiz...
    adb install -r -i "com.android.vending" ConsenAuto.apk
)

echo.
echo Otorgando permisos de ubicacion precisa para velocimetro y clima...
adb shell pm grant com.consen.autodash android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.consen.autodash android.permission.ACCESS_COARSE_LOCATION

echo Habilitando listener de notificaciones para sincronizar Spotify...
adb shell cmd notification allow_listener com.consen.autodash/com.consen.autodash.SpotifyMediaListener

echo Reiniciando servicio de Android Auto...
adb shell am force-stop com.google.android.projection.gearhead

echo.
echo ===================================================
echo   LISTO! Consen Dashboard instalado correctamente.
echo   Conecta tu telefono al auto y abre la app.
echo ===================================================
pause
