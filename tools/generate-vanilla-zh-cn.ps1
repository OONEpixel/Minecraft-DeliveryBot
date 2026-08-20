param(
    [Parameter(Mandatory = $true)]
    [string] $LanguageJson,

    [string] $Output = ''
)

if ([string]::IsNullOrWhiteSpace($Output)) {
    $Output = Join-Path $PSScriptRoot '..\src\main\resources\vanilla-zh-cn.yml'
}

$language = Get-Content -Raw -Encoding UTF8 -LiteralPath $LanguageJson | ConvertFrom-Json
$records = foreach ($property in $language.PSObject.Properties) {
    if ($property.Name -match '^(block|item)\.minecraft\.([a-z0-9_]+)$') {
        [PSCustomObject]@{
            Kind = $Matches[1]
            Material = $Matches[2].ToUpperInvariant()
            Chinese = [string] $property.Value
        }
    }
}

# Many placeable items have both block and item entries. Prefer the item translation because it
# describes the inventory stack players actually request (for example WHEAT -> 小麦, not 小麦植株).
$selected = $records |
    Group-Object Material |
    ForEach-Object {
        $_.Group | Sort-Object @{ Expression = { if ($_.Kind -eq 'item') { 0 } else { 1 } } } | Select-Object -First 1
    } |
    Sort-Object Material

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add('# Generated from Mojang Minecraft 1.21 zh_cn.json. Regenerate with tools/generate-vanilla-zh-cn.ps1.')
$lines.Add('names:')
foreach ($entry in $selected) {
    $escaped = ($entry.Material + '=' + $entry.Chinese).Replace("'", "''")
    $lines.Add("  - '$escaped'")
}

$absoluteOutput = [System.IO.Path]::GetFullPath($Output)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($absoluteOutput)) | Out-Null
[System.IO.File]::WriteAllLines($absoluteOutput, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Output "Generated $($selected.Count) vanilla Chinese names at $absoluteOutput"
