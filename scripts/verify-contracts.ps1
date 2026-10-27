# Windows PowerShell 5.1 turns redirected native stderr into errors; check exit codes below.
$ErrorActionPreference = 'Continue'
$projectRoot = Split-Path -Parent $PSScriptRoot
Get-Command npm -ErrorAction Stop | Out-Null
Push-Location $projectRoot -ErrorAction Stop
try {
    & .\mvnw.cmd -B -ntp verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed' }
    $python = Join-Path $projectRoot '.local/contracts-venv/Scripts/python.exe'
    if (-not (Test-Path -LiteralPath $python)) {
        Get-Command python -ErrorAction Stop | Out-Null
        & python -m venv .local/contracts-venv
        if ($LASTEXITCODE -ne 0) { throw 'Python 3.11+ is required for contract verification' }
    }
    & $python -m pip install -r scripts/requirements-contracts.txt
    if ($LASTEXITCODE -ne 0) { throw 'Contract validator installation failed' }
    & $python scripts/verify-contracts.py
    if ($LASTEXITCODE -ne 0) { throw 'Contract validation failed' }
    Push-Location frontend -ErrorAction Stop
    try {
        & npm run lint
        if ($LASTEXITCODE -ne 0) { throw 'TypeScript check failed' }
        & npm test
        if ($LASTEXITCODE -ne 0) { throw 'Angular contract tests failed' }
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw 'Angular build failed' }
    } finally { Pop-Location }
} finally { Pop-Location }
