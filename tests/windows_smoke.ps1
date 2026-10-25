param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$qaDir = Join-Path $projectRoot 'qa'
New-Item -ItemType Directory -Force -Path $qaDir | Out-Null
$frameworkDir = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
$wpfDir = Join-Path $frameworkDir 'WPF'
$references = @('/reference:System.dll', '/reference:System.Xaml.dll', "/reference:$wpfDir\PresentationFramework.dll", "/reference:$wpfDir\PresentationCore.dll", "/reference:$wpfDir\WindowsBase.dll")
$testExe = Join-Path $qaDir 'WindowsIntegration.exe'
$testSource = Join-Path $PSScriptRoot 'WindowsIntegration.cs'
& (Join-Path $frameworkDir 'csc.exe') '/nologo' '/target:exe' '/codepage:65001' "/out:$testExe" @references $testSource
if ($LASTEXITCODE -ne 0) { throw 'Integration harness compilation failed.' }
# Start hidden; poll the result/log files while the three-minute native UI test runs.
$appExe = Join-Path $projectRoot 'dist\Recall-Study.exe'
$testArguments = @('"' + $appExe + '"', '"' + $qaDir + '"')
$process = Start-Process -FilePath $testExe -ArgumentList $testArguments -WindowStyle Hidden -RedirectStandardOutput (Join-Path $qaDir 'windows-integration.log') -RedirectStandardError (Join-Path $qaDir 'windows-error.log') -PassThru
Write-Output "Windows integration running with PID $($process.Id). Results: $qaDir"
