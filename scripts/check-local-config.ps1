$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$testRelative = '.local/T005-init-verification-' + [Guid]::NewGuid().ToString('N')
$testRoot = Join-Path $projectRoot $testRelative
$initializer = Join-Path $PSScriptRoot 'init-local.ps1'
& $initializer -StateDirectory $testRelative | Out-Null
$source = Join-Path $testRoot 'secrets.json'
$before = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
$first = Get-Content -LiteralPath $source -Raw -Encoding UTF8 | ConvertFrom-Json
$derivedBefore = @{}
foreach ($name in @('api', 'booking', 'postgres', 'kafka', 'provisioner', 'broker')) {
    $derivedBefore[$name] = (Get-FileHash -LiteralPath (Join-Path $testRoot ($name + '.env'))).Hash
}
& $initializer -StateDirectory $testRelative | Out-Null
if ((Get-FileHash -LiteralPath $source).Hash -ne $before) { throw 'Canonical credentials changed on second init.' }
foreach ($name in $derivedBefore.Keys) {
    if ((Get-FileHash -LiteralPath (Join-Path $testRoot ($name + '.env'))).Hash -ne $derivedBefore[$name]) {
        throw 'Derived configuration changed on second init.'
    }
}
$api = Get-Content -LiteralPath (Join-Path $testRoot 'api.env') -Raw
$booking = Get-Content -LiteralPath (Join-Path $testRoot 'booking.env') -Raw
$broker = @(Get-Content -LiteralPath (Join-Path $testRoot 'broker.env') | Where-Object { $_ -and -not $_.StartsWith('#') })
if ($broker.Count -ne 1 -or $broker[0] -cnotmatch '^BOOKLANE_KAFKA_BROKER_PASSWORD=[0-9a-f]{64}$') {
    throw 'ACL bootstrap received unexpected credentials.'
}
if ($api -match 'BOOKLANE_DB_PASSWORD|POSTGRES_PASSWORD|PROVISIONER|BROKER|KAFKA_BOOKING_PASSWORD') { throw 'API received excessive credentials.' }
if ($booking -match 'POSTGRES_PASSWORD|PROVISIONER|BROKER|KAFKA_API_PASSWORD') { throw 'Booking received excessive credentials.' }
foreach ($key in $first.secrets.PSObject.Properties.Name) {
    $value = $first.secrets.$key
    if ($value -cnotmatch '^[0-9a-f]{64}$') { throw 'Invalid random secret format.' }
}
if (@($first.secrets.PSObject.Properties.Value | Sort-Object -Unique).Count -ne 7) { throw 'Technical accounts reused a secret.' }
if ($env:OS -eq 'Windows_NT') {
    $allowed = @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value, 'S-1-5-18')
    foreach ($rule in (Get-Acl -LiteralPath $source).Access) {
        if ($rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin $allowed) {
            throw 'A secret file grants access beyond its owner and SYSTEM.'
        }
    }
}
$original = Get-Content -LiteralPath $source -Raw -Encoding UTF8
$utf8 = [Text.UTF8Encoding]::new($false)
try {
    [IO.File]::WriteAllText($source, '{"schemaVersion":1,"secrets":{"invalid":"SENSITIVE_INIT_MARKER"}}', $utf8)
    $failure = $null
    try { & $initializer -StateDirectory $testRelative | Out-Null } catch { $failure = $_.ToString() }
    if (-not $failure -or $failure.Contains('SENSITIVE_INIT_MARKER')) { throw 'Incomplete secrets did not fail safely.' }
    [IO.File]::WriteAllText($source, '{"SENSITIVE_INIT_MARKER": invalid-json}', $utf8)
    $failure = $null
    try { & $initializer -StateDirectory $testRelative | Out-Null } catch { $failure = $_.ToString() }
    if (-not $failure -or $failure.Contains('SENSITIVE_INIT_MARKER')) { throw 'Malformed JSON was exposed or accepted.' }
} finally { [IO.File]::WriteAllText($source, $original, $utf8) }
$renamed = Join-Path $testRoot 'saved-secrets.json'
Move-Item -LiteralPath $source -Destination $renamed
try {
    $failure = $null
    try { & $initializer -StateDirectory $testRelative | Out-Null } catch { $failure = $_.ToString() }
    if (-not $failure -or (Test-Path -LiteralPath $source)) { throw 'Missing canonical source silently rotated credentials.' }
} finally { Move-Item -LiteralPath $renamed -Destination $source }
$failure = $null
try { & $initializer -StateDirectory '../outside-booklane' | Out-Null } catch { $failure = $_.ToString() }
if (-not $failure) { throw 'A state path outside .local was accepted.' }
& $initializer -StateDirectory $testRelative | Out-Null
if ((Get-FileHash -LiteralPath $source).Hash -ne $before) { throw 'Recovery changed canonical credentials.' }
Write-Output 'PASS: init preserves secrets, scopes credentials, restricts ACL, rejects corruption/missing source and contains paths.'
Write-Output 'Test artifacts remain under .local; no secrets were printed.'
