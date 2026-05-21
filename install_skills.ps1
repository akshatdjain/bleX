$source = "C:\Users\Inno\antigravity-skills\skills"
$target = "C:\Users\Inno\.gemini\antigravity\skills"

if (!(Test-Path $target)) {
    New-Item -ItemType Directory -Path $target -Force
}

Get-ChildItem -Path $source | ForEach-Object {
    $d = Join-Path $target $_.Name
    if (!(Test-Path $d)) {
        try {
            New-Item -ItemType SymbolicLink -Path $d -Target $_.FullName -ErrorAction Stop
        } catch {
            Write-Host "Failed to create link for $($_.Name): $($_.Exception.Message)"
        }
    }
}
