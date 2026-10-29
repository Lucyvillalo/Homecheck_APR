param([Parameter(Mandatory=$true)][string]$GoogleServicesPath)
$ErrorActionPreference = 'Stop'
$config = Get-Content -LiteralPath $GoogleServicesPath -Raw | ConvertFrom-Json
$client = @($config.client | Where-Object { $_.client_info.android_client_info.package_name -eq 'com.casacheck.app' })
if ($client.Count -ne 1) { throw 'El archivo debe contener exactamente un cliente Android con paquete com.casacheck.app.' }
$settings = [ordered]@{
    applicationId = $client[0].client_info.mobilesdk_app_id
    apiKey = $client[0].api_key[0].current_key
    projectId = $config.project_info.project_id
}
foreach ($value in $settings.Values) { if ([string]::IsNullOrWhiteSpace($value)) { throw 'Configuración Firebase incompleta.' } }
$assetDirectory = Join-Path $PSScriptRoot '..\app\src\main\assets'
New-Item -ItemType Directory -Force -Path $assetDirectory | Out-Null
$destination = Join-Path $assetDirectory 'firebase-config.json'
[System.IO.File]::WriteAllText($destination, ($settings | ConvertTo-Json), [System.Text.UTF8Encoding]::new($false))
Write-Output 'Configuración de cliente Android preparada. Activa correo/contraseña y publica las reglas antes de probar la nube.'
