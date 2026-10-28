$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$baseCompose = Join-Path $projectRoot 'infra/compose.yaml'
$devCompose = Join-Path $projectRoot 'infra/compose.dev.yaml'
foreach ($required in @('kafka.env', 'postgres.env', 'provisioner.env', 'broker.env')) {
    $path = Join-Path $projectRoot ('.local/runtime/' + $required)
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing .local/runtime/$required. Run scripts/init-local.ps1 first."
    }
}

$projectName = 'booklane-t006-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
if ($projectName -notmatch '^booklane-t006-[0-9a-f]{12}$') {
    throw 'Refusing to use an unexpected Compose project name.'
}
$marker = 'booklane-t006-' + [Guid]::NewGuid().ToString('N')
$composeBase = @('compose', '-p', $projectName, '-f', $baseCompose)
$composeDev = @('compose', '-p', $projectName, '-f', $baseCompose, '-f', $devCompose)

function Invoke-Compose {
    param([string[]]$ComposeArgs, [switch]$Dev)
    $arguments = if ($Dev) { $composeDev } else { $composeBase }
    & docker @arguments @ComposeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($ComposeArgs -join ' ') failed (exit $LASTEXITCODE)."
    }
}

function Invoke-KafkaVerify {
    param([string]$Mode)
    $arguments = @()
    $arguments += $composeBase
    $arguments += @('run', '--rm', '--no-deps', '-e', "T006_PERSISTENCE_MARKER=$marker", 'kafka-verify', $Mode)
    & docker @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Kafka verification mode '$Mode' failed."
    }
}

function Get-ServiceContainerId {
    param([string]$Service)
    $ids = & docker @composeBase 'ps' '-a' '-q' $Service 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $ids) {
        return $null
    }
    return ($ids | Select-Object -First 1).Trim()
}

function Wait-ServiceHealthy {
    param([string]$Service)
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $id = Get-ServiceContainerId $Service
        if ($id) {
            $status = & docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $id 2>$null
            if ($status -eq 'healthy') {
                return
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Service $Service did not become healthy."
}

function Wait-OneShot {
    param([string]$Service)
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $id = Get-ServiceContainerId $Service
        if ($id) {
            $status = & docker inspect --format '{{.State.Status}}' $id 2>$null
            if ($status -eq 'exited') {
                $code = & docker inspect --format '{{.State.ExitCode}}' $id 2>$null
                if ($code -ne '0') {
                    & docker @composeBase 'logs' '--no-color' $Service
                    throw "Service $Service exited with code $code."
                }
                return
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Service $Service did not complete."
}

function Invoke-AppPsql {
    param([string]$Sql)
    $command = 'PGPASSWORD="$BOOKLANE_DB_PASSWORD" psql -h 127.0.0.1 -U booklane_booking -d booklane -v ON_ERROR_STOP=1 -tA -f -'
    $arguments = @()
    $arguments += $composeBase
    $arguments += @('exec', '-T', 'postgres', 'bash', '-c', $command)
    $result = $Sql | & docker @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw 'Application database authentication or query failed.'
    }
    return (($result | Out-String).Trim())
}

function Test-TcpPort {
    param([string]$HostName, [int]$Port)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne(3000)) {
            return $false
        }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

$exitCode = 0
try {
    Write-Output 'Validating Compose files and loopback exposure.'
    $baseConfig = (& docker compose -f $baseCompose config 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'The base Compose file is invalid.' }
    if ($baseConfig -match 'published:') { throw 'The base Compose file publishes a host port.' }
    $devConfig = (& docker compose -f $baseCompose -f $devCompose config 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'The development Compose overlay is invalid.' }
    if ($devConfig -notmatch 'host_ip:\s*127\.0\.0\.1') { throw 'The development overlay does not bind host ports to loopback.' }
    if ($devConfig -match 'host_ip:\s*0\.0\.0\.0') { throw 'The development overlay publishes a non-loopback host port.' }
    if ($baseConfig -notmatch 'postgres:[^@\s]+@sha256:' -or $baseConfig -notmatch 'apache/kafka:[^@\s]+@sha256:') {
        throw 'Compose images are not pinned by digest.'
    }
    Write-Output 'PASS: base Compose publishes nothing; dev binds only loopback; images are pinned.'

    Write-Output 'Starting the isolated infrastructure project.'
    Invoke-Compose -ComposeArgs @('up', '-d')
    Wait-ServiceHealthy 'postgres'
    Wait-ServiceHealthy 'kafka'
    Wait-OneShot 'kafka-acl-bootstrap'
    Wait-OneShot 'kafka-provisioner'
    Write-Output 'PASS: PostgreSQL and Kafka are authenticated and Kafka provisioning completed.'

    Write-Output 'Checking PostgreSQL application authentication and schema preparation.'
    $identity = Invoke-AppPsql "SELECT current_user, current_setting('is_superuser');"
    if ($identity -ne "booklane_booking|off") {
        throw 'The application database role is not the expected non-superuser login.'
    }
    $extension = Invoke-AppPsql "SELECT extname FROM pg_extension WHERE extname = 'btree_gist';"
    if ($extension -ne 'btree_gist') {
        throw 'btree_gist is not available to the application role.'
    }
    $schemaOwner = Invoke-AppPsql "SELECT nspowner::regrole::text FROM pg_namespace WHERE nspname = 'booking';"
    if ($schemaOwner -ne 'booklane_booking') {
        throw 'The booking schema is not owned by the application role.'
    }
    $wrongSql = 'SELECT 1;'
    $wrongCommand = 'PGPASSWORD=booklane-t006-invalid psql -h 127.0.0.1 -U booklane_booking -d booklane -v ON_ERROR_STOP=1 -tA -f -'
    $wrongArgs = @()
    $wrongArgs += $composeBase
    $wrongArgs += @('exec', '-T', 'postgres', 'bash', '-c', $wrongCommand)
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $null = $wrongSql | & docker @wrongArgs 2>&1
        $wrongExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($wrongExit -eq 0) {
        throw 'PostgreSQL accepted an invalid application password.'
    }
    Write-Output 'PASS: application login is non-superuser, owns booking schema, has btree_gist and rejects a wrong password.'

    $schemaSql = "CREATE TABLE IF NOT EXISTS booking.t006_marker (marker text PRIMARY KEY, created_at timestamptz NOT NULL DEFAULT now()); INSERT INTO booking.t006_marker (marker) VALUES ('$marker') ON CONFLICT DO NOTHING;"
    $null = Invoke-AppPsql $schemaSql
    $stored = Invoke-AppPsql "SELECT marker FROM booking.t006_marker WHERE marker = '$marker';"
    if ($stored -ne $marker) {
        throw 'The PostgreSQL persistence marker was not written.'
    }

    Invoke-KafkaVerify -Mode 'full'
    Invoke-KafkaVerify -Mode 'persistence-write'

    Write-Output 'Restarting PostgreSQL and Kafka without removing volumes.'
    Invoke-Compose -ComposeArgs @('restart', 'postgres', 'kafka')
    Wait-ServiceHealthy 'postgres'
    Wait-ServiceHealthy 'kafka'
    $storedAfterRestart = Invoke-AppPsql "SELECT marker FROM booking.t006_marker WHERE marker = '$marker';"
    if ($storedAfterRestart -ne $marker) {
        throw 'The PostgreSQL persistence marker did not survive the restart.'
    }
    Invoke-KafkaVerify -Mode 'persistence-check'
    Write-Output 'PASS: PostgreSQL and Kafka values survived a restart with the same volumes.'

    Write-Output 'Checking the development host listeners.'
    Invoke-Compose -Dev -ComposeArgs @('up', '-d')
    Wait-ServiceHealthy 'postgres'
    Wait-ServiceHealthy 'kafka'
    $kafkaPort = (& docker @composeDev 'port', 'kafka', '19092' | Out-String).Trim()
    $postgresPort = (& docker @composeDev 'port', 'postgres', '5432' | Out-String).Trim()
    if ($kafkaPort -ne '127.0.0.1:19092') {
        throw 'Kafka dev port is not published only on loopback:19092.'
    }
    if ($postgresPort -ne '127.0.0.1:15432') {
        throw 'PostgreSQL dev port is not published only on loopback:15432.'
    }
    if (-not (Test-TcpPort '127.0.0.1' 19092)) {
        throw 'The Kafka host listener is not reachable on 127.0.0.1:19092.'
    }
    if (-not (Test-TcpPort '127.0.0.1' 15432)) {
        throw 'The PostgreSQL host port is not reachable on 127.0.0.1:15432.'
    }
    $listenerConfig = (& docker @composeDev 'exec', '-T', 'kafka', 'bash', '-c', 'grep listeners= /opt/kafka/config/server.properties' | Out-String)
    if ($LASTEXITCODE -ne 0 -or $listenerConfig -notmatch 'HOST://127\.0\.0\.1:19092' -or $listenerConfig -notmatch 'CLIENT://kafka:9092') {
        throw 'Kafka advertised listeners do not match the dev configuration.'
    }
    Invoke-KafkaVerify -Mode 'persistence-check'
    Write-Output 'PASS: dev publishes only loopback ports and advertises host/container Kafka listeners.'
}
catch {
    Write-Output ('FAIL: ' + $_.Exception.Message)
    $exitCode = 1
}
finally {
    Write-Output 'Removing the isolated verification project and volumes.'
    try {
        Invoke-Compose -ComposeArgs @('down', '-v', '--remove-orphans')
    } catch {
        Write-Output ('Cleanup warning: ' + $_.Exception.Message)
    }
}

if ($exitCode -ne 0) {
    exit $exitCode
}
Write-Output 'PASS: T006 infrastructure verification completed.'
