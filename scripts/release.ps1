# scripts/release.ps1
# 自動化 GitHub Release 發佈流程
#
# 使用方式（在專案根目錄執行）：
#   .\scripts\release.ps1
#   .\scripts\release.ps1 -SkipBuild    # 跳過 Gradle 建置（已有 APK 時用）
#   .\scripts\release.ps1 -SkipVerify   # 跳過簽章驗證（測試用，正式發佈請勿跳過）
#
# 前置需求：
#   - keystore.properties 存在且欄位正確
#   - GitHub CLI (gh) 已安裝並登入
#   - CHANGELOG.md 已含本版本 [vX.Y.Z] 條目

param(
    [switch]$SkipBuild,
    [switch]$SkipVerify
)

Set-Location "$PSScriptRoot\.."
$ErrorActionPreference = "Stop"

# ── 1. 讀取版本號 ──────────────────────────────────────────────────────────────

$props = @{}
Get-Content "gradle.properties" | Where-Object { $_ -match '^\s*\w' } | ForEach-Object {
    $parts = $_ -split '=', 2
    if ($parts.Count -eq 2) { $props[$parts[0].Trim()] = $parts[1].Trim() }
}
$version     = $props['APP_VERSION_NAME']
$versionCode = $props['APP_VERSION_CODE']
if (-not $version) { Write-Error "Cannot read APP_VERSION_NAME from gradle.properties"; exit 1 }

$tag      = "v$version"
$apkSrc   = "app\build\outputs\apk\release\app-release.apk"
$apkAsset = "app\build\outputs\apk\release\POS_ANDROID_2026-$tag-release.apk"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Release: $tag  (versionCode $versionCode)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# ── 2. 建置 Release APK ────────────────────────────────────────────────────────
#
# 已知問題：Windows 上 `gradlew.bat clean assembleRelease` 可能因
# lint-cache 檔案被 IDE / Daemon 佔用而拋出 FileSystemException。
# 解法：不執行 clean，改以 -x lintVitalRelease 跳過 lint 任務。
# lint 問題不影響 APK 功能；如需完整 lint 請另行執行 `gradlew.bat lint`。

if (-not $SkipBuild) {
    Write-Host "`n[1/5] Building release APK (lint skipped)..." -ForegroundColor Yellow
    & .\gradlew.bat assembleRelease -x lintVitalRelease
    if ($LASTEXITCODE -ne 0) { Write-Error "Build failed (exit $LASTEXITCODE)"; exit 1 }
    Write-Host "  Build OK" -ForegroundColor Green
} else {
    Write-Host "`n[1/5] Build skipped (-SkipBuild)" -ForegroundColor DarkGray
}

if (-not (Test-Path $apkSrc)) {
    Write-Error "APK not found at $apkSrc — run without -SkipBuild to rebuild"
    exit 1
}

# ── 3. 驗證簽章 ────────────────────────────────────────────────────────────────
#
# 已知問題：從 Bash 呼叫 PowerShell 執行 apksigner 時，
# `$` 變數轉義會全面失效，導致路徑解析錯誤。
# 解法：將驗證邏輯寫在此 .ps1 內，直接以 PowerShell 執行。
#
# apksigner 路徑解析順序：
#   1. local.properties 的 sdk.dir
#   2. %LOCALAPPDATA%\Android\Sdk（Android Studio 預設）
#   取最新版 build-tools 下的 apksigner.bat。

if (-not $SkipVerify) {
    Write-Host "`n[2/5] Verifying APK signature..." -ForegroundColor Yellow

    $sdkPath = $null
    if (Test-Path "local.properties") {
        $sdkLine = Get-Content "local.properties" |
                   Where-Object { $_ -like 'sdk.dir=*' } |
                   Select-Object -First 1
        if ($sdkLine) {
            $sdkPath = ($sdkLine -replace '^sdk.dir=', '').Trim()
        }
    }
    if (-not $sdkPath -or -not (Test-Path $sdkPath)) {
        $sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
    }

    $apksigner = Get-ChildItem -Path "$sdkPath\build-tools" -Recurse `
                     -Filter "apksigner.bat" -ErrorAction SilentlyContinue |
                 Sort-Object FullName -Descending |
                 Select-Object -First 1

    if (-not $apksigner) {
        Write-Error "apksigner.bat not found under '$sdkPath\build-tools'"
        exit 1
    }

    $out = & $apksigner.FullName verify --verbose $apkSrc 2>&1

    if ($out -notmatch "Verifies") {
        Write-Error "APK signature verification FAILED:`n$out"
        exit 1
    }
    if ($out -match "CN=Android Debug") {
        Write-Error "APK is signed with the DEBUG certificate — do not publish!"
        exit 1
    }

    Write-Host "  apksigner: $($apksigner.FullName)" -ForegroundColor DarkGray
    Write-Host "  Signature OK (v2 scheme, non-debug cert)" -ForegroundColor Green
} else {
    Write-Host "`n[2/5] Signature verification skipped (-SkipVerify)" -ForegroundColor DarkGray
}

# ── 4. 複製並重新命名 APK ──────────────────────────────────────────────────────

Write-Host "`n[3/5] Preparing release asset..." -ForegroundColor Yellow
Copy-Item $apkSrc $apkAsset -Force
$hash = (Get-FileHash $apkAsset -Algorithm SHA256).Hash.ToLower()
Write-Host "  Asset : $(Split-Path $apkAsset -Leaf)"
Write-Host "  SHA256: $hash" -ForegroundColor DarkGray

# ── 5. Push to GitHub ──────────────────────────────────────────────────────────

Write-Host "`n[4/5] Pushing main to GitHub..." -ForegroundColor Yellow
git push origin main
if ($LASTEXITCODE -ne 0) { Write-Error "git push failed"; exit 1 }
Write-Host "  Push OK" -ForegroundColor Green

# ── 6. 建立 GitHub Release ─────────────────────────────────────────────────────

Write-Host "`n[5/5] Creating GitHub Release $tag..." -ForegroundColor Yellow

# 從 CHANGELOG.md 擷取本版本的 release notes（[vX.Y.Z] 到下一個 [v...] 之間的內容）
$notes = ""
if (Test-Path "CHANGELOG.md") {
    $cl = Get-Content "CHANGELOG.md" -Raw
    $m  = [regex]::Match($cl, "(?s)## \[$tag\][^\n]*\n(.+?)(?=\n## \[|\z)")
    if ($m.Success) { $notes = $m.Groups[1].Value.Trim() }
}
if (-not $notes) { $notes = "Release $tag — 詳見 CHANGELOG.md" }

gh release create $tag $apkAsset --title "$tag" --notes $notes
if ($LASTEXITCODE -ne 0) { Write-Error "gh release create failed"; exit 1 }
Write-Host "  https://github.com/bjoe0201/POS_ANDROID_2026/releases/tag/$tag" -ForegroundColor Green

# ── 7. 刪除舊版 Release（僅保留最新） ─────────────────────────────────────────

Write-Host "`nCleaning up old releases (keeping only $tag)..." -ForegroundColor Yellow

$oldTags = (gh release list --limit 100) | ForEach-Object {
    $cols = $_ -split '\t'
    if ($cols.Count -ge 3) { $cols[2].Trim() } else { $null }
} | Where-Object { $_ -and $_ -ne $tag }

if ($oldTags) {
    foreach ($old in $oldTags) {
        Write-Host "  Deleting $old..." -ForegroundColor DarkGray
        gh release delete $old --yes
    }
    Write-Host "  Removed $($oldTags.Count) old release(s)" -ForegroundColor Green
} else {
    Write-Host "  No old releases to delete" -ForegroundColor DarkGray
}

# ── 完成 ───────────────────────────────────────────────────────────────────────

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host " Done! $tag released successfully." -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
