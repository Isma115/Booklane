param(
    [string]$StateDirectory = '.local/runtime'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '.local'))
$statePath = [IO.Path]::GetFullPath((Join-Path $projectRoot $StateDirectory))
if (-not $statePath.StartsWith($localRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'StateDirectory must be a child directory of Booklane/.local.'
}
for ($cursor = $statePath; $cursor.Length -gt $projectRoot.Length; $cursor = Split-Path -Parent $cursor) {
    if (Test-Path -LiteralPath $cursor) {
        if ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'Local configuration paths must not be junctions or symbolic links.'
        }
    }
}
foreach ($name in @('java', 'javac', 'node', 'npm', 'docker')) {
    Get-Command $name -ErrorAction Stop | Out-Null
}
$javaVersion = @(& java --version)
if ($LASTEXITCODE -ne 0 -or $javaVersion[0] -notmatch '^openjdk 21\.') { throw 'Select the Booklane JDK 21 before initialization.' }
$compiler = & javac --version
if ($LASTEXITCODE -ne 0 -or $compiler -notmatch '^javac 21\.') { throw 'A JDK 21 compiler is required.' }
$nodeVersion = [version]((& node --version).TrimStart('v'))
if ($LASTEXITCODE -ne 0 -or $nodeVersion.Major -ne 24 -or $nodeVersion -lt [version]'24.15.0') { throw 'Node 24.15.0 or newer in major 24 is required.' }
$npmVersion = & npm --version
if ($LASTEXITCODE -ne 0 -or $npmVersion -ne '11.17.0') { throw 'npm 11.17.0 is required by the lockfile toolchain.' }
$dockerOs = & docker info --format '{{.OSType}}'
if ($LASTEXITCODE -ne 0 -or $dockerOs -ne 'linux') { throw 'Start Docker Desktop with its Linux engine first.' }
& docker compose version --short | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose is required.' }
$catalogPath = Join-Path $projectRoot 'infra/demo/users.json'
if (-not (Test-Path -LiteralPath $catalogPath -PathType Leaf)) { throw 'The shared demo user catalog is missing.' }

[IO.Directory]::CreateDirectory($statePath) | Out-Null
# Only the script-owned state directory is restricted, not the rest of .local.
if ($env:OS -eq 'Windows_NT') {
    $owner = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $system = [Security.Principal.SecurityIdentifier]::new('S-1-5-18')
    $acl = Get-Acl -LiteralPath $statePath
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($rule in @($acl.Access)) { $acl.RemoveAccessRuleSpecific($rule) }
    foreach ($identity in @($owner, $system)) {
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
            $identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
    }
    [IO.DirectoryInfo]::new($statePath).SetAccessControl($acl)
}
$secretPath = Join-Path $statePath 'secrets.json'
$keys = @(
    'BOOKLANE_INTERNAL_PASSWORD', 'BOOKLANE_DB_PASSWORD', 'BOOKLANE_POSTGRES_PASSWORD',
    'BOOKLANE_KAFKA_API_PASSWORD', 'BOOKLANE_KAFKA_BOOKING_PASSWORD',
    'BOOKLANE_KAFKA_PROVISIONER_PASSWORD', 'BOOKLANE_KAFKA_BROKER_PASSWORD'
)
$managedFiles = @('secrets.json', 'api.env', 'booking.env', 'postgres.env', 'kafka.env', 'provisioner.env', 'broker.env')
foreach ($file in $managedFiles) {
    $path = Join-Path $statePath $file
    if (Test-Path -LiteralPath $path) {
        if ((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'Managed configuration files must not be symbolic links.'
        }
    }
}
$utf8 = [Text.UTF8Encoding]::new($false)
if (Test-Path -LiteralPath $secretPath) {
    try {
        if ((Get-Item -LiteralPath $secretPath).Length -gt 16384) { throw 'size' }
        $document = Get-Content -LiteralPath $secretPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $rootNames = @($document.PSObject.Properties.Name)
        if ($document.schemaVersion -isnot [int] -or $document.schemaVersion -ne 1 -or
                $rootNames.Count -ne 2 -or @(Compare-Object @('schemaVersion', 'secrets') $rootNames).Count -ne 0) { throw 'schema' }
        $secrets = $document.secrets
        $names = @($secrets.PSObject.Properties.Name)
        if ($names.Count -ne $keys.Count -or @(Compare-Object $keys $names).Count -ne 0) { throw 'keys' }
        foreach ($key in $keys) {
            if ($secrets.$key -isnot [string] -or $secrets.$key -cnotmatch '^[0-9a-f]{64}$') { throw 'format' }
        }
    } catch {
        throw 'Local secrets are invalid or incomplete. Restore the canonical file; keys were not regenerated.'
    }
} else {
    if (@(Get-ChildItem -LiteralPath $statePath -Filter '*.env' -File).Count -gt 0) {
        throw 'Canonical secrets are missing but derived configuration exists. Restore secrets.json; initialization will not rotate keys.'
    }
    $values = [ordered]@{}
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        foreach ($key in $keys) {
            $bytes = New-Object byte[] 32
            $random.GetBytes($bytes)
            $values[$key] = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
        }
    } finally { $random.Dispose() }
    $document = [ordered]@{ schemaVersion = 1; secrets = $values }
    $json = $document | ConvertTo-Json -Depth 4
    $stream = [IO.File]::Open($secretPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    $writer = [IO.StreamWriter]::new($stream, $utf8)
    try { $writer.Write($json) } finally { $writer.Dispose() }
    $secrets = $values
}
$derived = [ordered]@{
    'api.env' = [ordered]@{
        BOOKLANE_USERS_FILE = $catalogPath
        BOOKLANE_INTERNAL_PASSWORD = $secrets.BOOKLANE_INTERNAL_PASSWORD
        BOOKLANE_KAFKA_API_PASSWORD = $secrets.BOOKLANE_KAFKA_API_PASSWORD
    }
    'booking.env' = [ordered]@{
        BOOKLANE_USERS_FILE = $catalogPath
        BOOKLANE_INTERNAL_PASSWORD = $secrets.BOOKLANE_INTERNAL_PASSWORD
        BOOKLANE_DB_PASSWORD = $secrets.BOOKLANE_DB_PASSWORD
        BOOKLANE_KAFKA_BOOKING_PASSWORD = $secrets.BOOKLANE_KAFKA_BOOKING_PASSWORD
    }
    'postgres.env' = [ordered]@{
        POSTGRES_DB = 'booklane'
        POSTGRES_USER = 'booklane_owner'
        POSTGRES_PASSWORD = $secrets.BOOKLANE_POSTGRES_PASSWORD
        BOOKLANE_DB_PASSWORD = $secrets.BOOKLANE_DB_PASSWORD
    }
    'kafka.env' = [ordered]@{
        BOOKLANE_KAFKA_BROKER_PASSWORD = $secrets.BOOKLANE_KAFKA_BROKER_PASSWORD
        BOOKLANE_KAFKA_PROVISIONER_PASSWORD = $secrets.BOOKLANE_KAFKA_PROVISIONER_PASSWORD
        BOOKLANE_KAFKA_API_PASSWORD = $secrets.BOOKLANE_KAFKA_API_PASSWORD
        BOOKLANE_KAFKA_BOOKING_PASSWORD = $secrets.BOOKLANE_KAFKA_BOOKING_PASSWORD
    }
    'provisioner.env' = [ordered]@{
        BOOKLANE_KAFKA_PROVISIONER_PASSWORD = $secrets.BOOKLANE_KAFKA_PROVISIONER_PASSWORD
    }
    'broker.env' = [ordered]@{
        BOOKLANE_KAFKA_BROKER_PASSWORD = $secrets.BOOKLANE_KAFKA_BROKER_PASSWORD
    }
}
foreach ($file in $derived.Keys) {
    $lines = @('# Generated from secrets.json. Do not commit or edit.')
    foreach ($entry in $derived[$file].GetEnumerator()) { $lines += $entry.Key + '=' + $entry.Value }
    [IO.File]::WriteAllText((Join-Path $statePath $file), ($lines -join "`n") + "`n", $utf8)
}
Write-Output 'Local configuration prepared. Credentials were preserved and have not been printed.'
