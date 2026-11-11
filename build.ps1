param([string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
Push-Location $PSScriptRoot
try {
    $tools = Join-Path $Sdk 'build-tools\35.0.0'
    $android = Join-Path $Sdk 'platforms\android-34\android.jar'
    $work = Join-Path ([System.IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Force $work, "$work\classes", "$work\dex", dist | Out-Null
    $sources = (Get-ChildItem src -Filter '*.java').FullName
    & javac -encoding UTF-8 --release 8 -classpath $android -d "$work\classes" $sources
    & jar cf "$work\classes.jar" -C "$work\classes" .
    & "$tools\d8.bat" --min-api 24 --lib $android --output "$work\dex" "$work\classes.jar"
    & "$tools\aapt2.exe" compile --dir res -o "$work\resources.zip"
    & "$tools\aapt2.exe" link -I $android --manifest AndroidManifest.xml -A assets -o "$work\unsigned.apk" "$work\resources.zip"
    Push-Location "$work\dex"
    try { & jar uf "$work\unsigned.apk" classes.dex }
    finally { Pop-Location }
    & "$tools\zipalign.exe" -f 4 "$work\unsigned.apk" "$work\aligned.apk"
    $signing = Join-Path $env:USERPROFILE '.android\fishzan-debug.jks'
    if (!(Test-Path -LiteralPath $signing)) {
        New-Item -ItemType Directory -Force (Split-Path $signing) | Out-Null
        & keytool -genkeypair -keystore $signing -storepass android -keypass android -alias fishzan -dname "CN=Fishzan Local Build" -keyalg RSA -keysize 2048 -validity 10000
    }
    & "$tools\apksigner.bat" sign --ks $signing --ks-key-alias fishzan --ks-pass pass:android --out dist\fishzan.apk "$work\aligned.apk"
    & "$tools\apksigner.bat" verify --verbose dist\fishzan.apk
    Compress-Archive -Path 'assets/templates/*' -DestinationPath 'dist/fishzan-templates.zip' -Force
    Compress-Archive -Path 'assets/templates/fanous/*' -DestinationPath 'dist/fanous-template.zip' -Force
    if (Test-Path -LiteralPath 'dist\fishzan.apk.idsig') {
        Remove-Item -LiteralPath 'dist\fishzan.apk.idsig'
    }
} finally {
    if ($work -and (Test-Path -LiteralPath $work)) {
        $resolved = (Resolve-Path -LiteralPath $work).Path
        $tempRoot = [System.IO.Path]::GetTempPath().TrimEnd('\') + '\'
        if ($resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
    Pop-Location
}

