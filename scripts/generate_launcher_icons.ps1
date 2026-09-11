Add-Type -AssemblyName System.Drawing

# Stessa geometria di ic_launcher_background.xml / ic_launcher_foreground.xml (viewport 108x108),
# renderizzata qui in PNG per i dispositivi pre-Android 8 (nessuna adaptive icon).
$bg = [System.Drawing.Color]::FromArgb(255, 0x3F, 0x51, 0xB5)
$fg = [System.Drawing.Color]::FromArgb(255, 0xFF, 0xFF, 0xFF)
$accent = [System.Drawing.Color]::FromArgb(255, 0xFF, 0xC1, 0x07)

$bars = @(
    @(26,42,34,66),
    @(38,34,46,74),
    @(50,40,58,68),
    @(66,44,86,52),
    @(66,58,82,66)
)

function Get-StarPoints($offsetX, $offsetY) {
    $big = @(
        @(19,9),@(20.25,6.25),@(23,5),@(20.25,3.75),@(19,1),@(17.75,3.75),@(15,5),@(17.75,6.25)
    )
    $small = @(
        @(11.5,9.5),@(9,4),@(6.5,9.5),@(1,12),@(6.5,14.5),@(9,20),@(11.5,14.5),@(17,12)
    )
    return @{
        big = $big | ForEach-Object { [System.Drawing.PointF]::new($_[0] + $offsetX, $_[1] + $offsetY) }
        small = $small | ForEach-Object { [System.Drawing.PointF]::new($_[0] + $offsetX, $_[1] + $offsetY) }
    }
}

function New-LauncherBitmap($sizePx, [bool]$round) {
    $scale = $sizePx / 108.0
    $bmp = New-Object System.Drawing.Bitmap $sizePx, $sizePx, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    if ($round) {
        $clip = New-Object System.Drawing.Drawing2D.GraphicsPath
        $clip.AddEllipse(0, 0, $sizePx, $sizePx)
        $g.SetClip($clip)
    }

    $g.FillRectangle((New-Object System.Drawing.SolidBrush $bg), 0, 0, $sizePx, $sizePx)

    $brushFg = New-Object System.Drawing.SolidBrush $fg
    foreach ($b in $bars) {
        $x = $b[0] * $scale
        $y = $b[1] * $scale
        $w = ($b[2] - $b[0]) * $scale
        $h = ($b[3] - $b[1]) * $scale
        $g.FillRectangle($brushFg, $x, $y, $w, $h)
    }

    $stars = Get-StarPoints 62 6
    $brushAccent = New-Object System.Drawing.SolidBrush $accent
    $bigScaled = $stars.big | ForEach-Object { [System.Drawing.PointF]::new($_.X * $scale, $_.Y * $scale) }
    $smallScaled = $stars.small | ForEach-Object { [System.Drawing.PointF]::new($_.X * $scale, $_.Y * $scale) }
    $g.FillPolygon($brushAccent, $bigScaled)
    $g.FillPolygon($brushAccent, $smallScaled)

    $g.Dispose()
    return $bmp
}

$sizes = @{ mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }
$resDir = Join-Path $PSScriptRoot "..\app\src\main\res"

foreach ($dpi in $sizes.Keys) {
    $px = $sizes[$dpi]
    $dir = Join-Path $resDir "mipmap-$dpi"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    $square = New-LauncherBitmap $px $false
    $square.Save((Join-Path $dir "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $square.Dispose()

    $round = New-LauncherBitmap $px $true
    $round.Save((Join-Path $dir "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $round.Dispose()

    Write-Host "Generata mipmap-$dpi ($px x $px)"
}
