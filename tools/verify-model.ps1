param(
    [string]$ModelPath = (Join-Path $PSScriptRoot '..\app\src\main\assets\roadar_yolo26n.onnx')
)

$expectedSha256 = 'D4F3BEF0FEB16C7A3354342E8A8684A02578555EF6B531902ADCDCA19185F3CA'

if (-not (Test-Path -LiteralPath $ModelPath -PathType Leaf)) {
    Write-Error "Model not found at '$ModelPath'. Obtain the approved asset and copy it to app/src/main/assets/roadar_yolo26n.onnx."
    exit 1
}

$actualSha256 = (Get-FileHash -LiteralPath $ModelPath -Algorithm SHA256).Hash
if ($actualSha256 -ne $expectedSha256) {
    Write-Error "Model SHA-256 mismatch. Expected $expectedSha256; got $actualSha256."
    exit 1
}

Write-Output "Model SHA-256 verified: $actualSha256"
