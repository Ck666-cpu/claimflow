# Pre-submission check for ClaimFlow. Run from the repo root:
#   powershell -ExecutionPolicy Bypass -File .\scripts\check-submission.ps1
#   ... -Build                 also runs mvn verify + npm run build
#   ... -Forbid word1,word2    words that must not appear in tracked files (e.g. the client's name)
param([switch]$Build, [string[]]$Forbid = @())

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$fails = 0; $warns = 0
function Ok($m)   { Write-Host "OK    $m" -ForegroundColor Green }
function Warn($m) { Write-Host "WARN  $m" -ForegroundColor Yellow; $script:warns++ }
function Fail($m) { Write-Host "FAIL  $m" -ForegroundColor Red; $script:fails++ }

Write-Host "`n== Repository ==" -ForegroundColor Cyan
git rev-parse --is-inside-work-tree *> $null
if ($LASTEXITCODE -ne 0) { Fail "Not a git repository: run this from the claimflow repo root"; exit 1 }
$commits = [int](git rev-list --count HEAD)
if ($commits -ge 8) { Ok "$commits commits" } elseif ($commits -ge 3) { Warn "Only $commits commits: the brief asks for a meaningful history" } else { Fail "Only $commits commit(s)" }
$dirty = git status --porcelain
if ($dirty) { Warn "Uncommitted changes:"; $dirty | ForEach-Object { Write-Host "      $_" } } else { Ok "Working tree clean" }
$branch = git rev-parse --abbrev-ref HEAD
Ok "Branch: $branch"

Write-Host "`n== Deliverables ==" -ForegroundColor Cyan
foreach ($f in @('README.md','AI_JOURNAL.md','docker-compose.yml','docs/decisions.md','docs/api/claims.http','pom.xml','web/package.json')) {
  if (Test-Path $f) { Ok "$f" } else { Fail "$f missing" }
}
foreach ($f in @('docs/PRD.md','docs/events.md','docs/api/openapi.yaml','docs/prompts')) {
  if (Test-Path $f) { Ok "$f" } else { Warn "$f missing (recommended)" }
}

Write-Host "`n== Unfinished text ==" -ForegroundColor Cyan
$todo = Select-String -Path AI_JOURNAL.md -Pattern 'TODO\(me\)|ASK ME:' -ErrorAction SilentlyContinue
if ($todo) { Fail "AI_JOURNAL.md still has $($todo.Count) TODO(me)/ASK ME marker(s)"; $todo | ForEach-Object { Write-Host "      line $($_.LineNumber): $($_.Line.Trim())" } } else { Ok "AI_JOURNAL.md has no TODO markers" }
if (Select-String -Path README.md -Pattern 'Fill in at the end' -Quiet) { Fail "README.md still has the 'Fill in at the end' placeholder" } else { Ok "README.md placeholder replaced" }
if (Select-String -Path docs/PRD.md -Pattern '_fill in' -Quiet -ErrorAction SilentlyContinue) { Warn "docs/PRD.md still has a '_fill in' row" }

Write-Host "`n== Things that must not be in the repo ==" -ForegroundColor Cyan
$tracked = git ls-files
if ($Forbid.Count -eq 0) { Warn "No -Forbid words given: pass the client name to check branding" }
foreach ($w in $Forbid) {
  $hits = git grep -n -i -I -F -e $w 2>$null
  if ($hits) { Fail "Forbidden word '$w' found in tracked files:"; $hits | Select-Object -First 10 | ForEach-Object { Write-Host "      $_" } } else { Ok "No '$w' in tracked files" }
}
$docx = $tracked | Where-Object { $_ -match '\.(docx|pdf)$' }
if ($docx) { Fail "Document files tracked (the brief is confidential):"; $docx | ForEach-Object { Write-Host "      $_" } } else { Ok "No .docx/.pdf tracked" }
$junk = $tracked | Where-Object { $_ -match '(^|/)(node_modules|target|dist|\.angular|\.idea)/' }
if ($junk) { Fail "Build output or IDE files tracked ($(@($junk).Count) files), e.g. $(@($junk)[0])" } else { Ok "No node_modules/target/dist/.idea tracked" }
$envs = $tracked | Where-Object { $_ -match '(^|/)\.env' }
if ($envs) { Fail ".env files tracked: $envs" } else { Ok "No .env files tracked" }
$secrets = git grep -n -I -E "(AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY|ghp_[A-Za-z0-9]{30,}|sk-[A-Za-z0-9]{20,})" 2>$null
if ($secrets) { Fail "Possible secrets:"; $secrets | ForEach-Object { Write-Host "      $_" } } else { Ok "No obvious secrets" }
$big = git ls-files -s | ForEach-Object { ($_ -split '\s+')[3] } | Where-Object { (Test-Path $_) -and ((Get-Item $_).Length -gt 5MB) }
if ($big) { Warn "Large files (>5 MB): $big" } else { Ok "No files over 5 MB" }
$hosting = $tracked | Where-Object { $_ -match '^\.github/workflows/' }
if ($hosting) { Warn "GitHub workflows present: make sure none deploys or hosts the app: $hosting" }

if ($Build) {
  Write-Host "`n== Build ==" -ForegroundColor Cyan
  docker info *> $null
  if ($LASTEXITCODE -ne 0) { Warn "Docker is not running: integration tests may fail" }
  mvn -q verify
  if ($LASTEXITCODE -eq 0) { Ok "mvn verify passed" } else { Fail "mvn verify failed" }
  Push-Location web
  if (-not (Test-Path node_modules)) { npm install --no-audit --no-fund }
  npm run build
  if ($LASTEXITCODE -eq 0) { Ok "npm run build passed" } else { Fail "npm run build failed" }
  Pop-Location
}

Write-Host "`n== Recent history ==" -ForegroundColor Cyan
git log --oneline -15

Write-Host ""
if ($fails -eq 0) { Write-Host "READY: $warns warning(s), 0 failures" -ForegroundColor Green }
else { Write-Host "NOT READY: $fails failure(s), $warns warning(s)" -ForegroundColor Red }
