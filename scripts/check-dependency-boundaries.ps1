$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$probeRoot = Join-Path $root '.local/dependency-probes'
New-Item -ItemType Directory -Force $probeRoot | Out-Null
$cases = @(
    @{ Name = 'api-mvc'; Source = 'services/booklane-api/pom.xml'; Group = 'org.springframework'; Artifact = 'spring-webmvc'; Version = '' },
    @{ Name = 'api-jdbc'; Source = 'services/booklane-api/pom.xml'; Group = 'org.springframework'; Artifact = 'spring-jdbc'; Version = '' },
    @{ Name = 'api-jpa'; Source = 'services/booklane-api/pom.xml'; Group = 'org.hibernate.orm'; Artifact = 'hibernate-core'; Version = '' },
    @{ Name = 'api-booking'; Source = 'services/booklane-api/pom.xml'; Group = 'com.booklane'; Artifact = 'booklane-booking'; Version = '0.1.0-SNAPSHOT' },
    @{ Name = 'booking-api'; Source = 'services/booklane-booking/pom.xml'; Group = 'com.booklane'; Artifact = 'booklane-api'; Version = '0.1.0-SNAPSHOT' },
    @{ Name = 'protocol-spring'; Source = 'modules/contracts-java/pom.xml'; Group = 'org.springframework'; Artifact = 'spring-core'; Version = '' }
)
foreach ($case in $cases) {
    [xml]$source = Get-Content -LiteralPath (Join-Path $root $case.Source) -Raw
    $ns = [Xml.XmlNamespaceManager]::new($source.NameTable)
    $ns.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
    $plugin = $source.SelectSingleNode('//m:plugin[m:artifactId="maven-enforcer-plugin"]', $ns).OuterXml
    $plugin = $plugin.Replace(' xmlns="http://maven.apache.org/POM/4.0.0"', '')
    $versionElement = if ($case.Version) { '<version>' + $case.Version + '</version>' } else { '' }
    # Probe a copied rule in an isolated POM; never edit the working module or compile invalid source.
    $pom = @"
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent><groupId>com.booklane</groupId><artifactId>booklane</artifactId><version>0.1.0-SNAPSHOT</version><relativePath>../../pom.xml</relativePath></parent>
  <artifactId>boundary-$($case.Name)</artifactId>
  <dependencies><dependency><groupId>$($case.Group)</groupId><artifactId>$($case.Artifact)</artifactId>$versionElement</dependency></dependencies>
  <build><plugins>$plugin</plugins></build>
</project>
"@
    $pomPath = Join-Path $probeRoot ($case.Name + '.xml')
    [IO.File]::WriteAllText($pomPath, $pom, [Text.UTF8Encoding]::new($false))
    $output = & (Join-Path $root 'mvnw.cmd') -B -ntp -f $pomPath validate 2>&1
    $code = $LASTEXITCODE
    $output | Out-File -LiteralPath (Join-Path $probeRoot ($case.Name + '.log')) -Encoding utf8
    $text = $output -join "`n"
    $rejectedArtifact = [regex]::Escape($case.Group + ':' + $case.Artifact) + ':jar:[^\r\n]*banned via the exclude/include list'
    if ($code -eq 0 -or $text -notmatch 'bannedDependencies' -or $text -notmatch $rejectedArtifact) {
        throw "Probe $($case.Name) did not fail on its forbidden dependency. Inspect .local/dependency-probes."
    }
    Write-Output "PASS: $($case.Name) rejected $($case.Group):$($case.Artifact)"
}
