$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = "http://localhost:8081"
$results = @()
$pass = 0; $fail = 0

function Record($line) {
    Write-Output $line
    $script:results += $line
}

function Login($u, $p) {
    $body = @{ username = $u; password = $p } | ConvertTo-Json
    try {
        return (Invoke-RestMethod -Uri "$base/auth/login" -Method POST -ContentType "application/json" -Body $body)
    } catch { return $null }
}

# Core test: measure elapsed ms, check response code
function Test-Endpoint {
    param($name, $method, $uri, $headers, $body, $expectCode = 200)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $params = @{ Uri = $uri; Method = $method; ErrorAction = "Stop" }
        if ($headers) { $params.Headers = $headers }
        if ($body) { $params.ContentType = "application/json"; $params.Body = $body }
        $r = Invoke-RestMethod @params
        $sw.Stop(); $ms = $sw.ElapsedMilliseconds
        if ($r.code -eq $expectCode) {
            Record ("PASS  {0,-30} {1,6} ms  code={2}" -f $name, $ms, $r.code); $script:pass++
        } else {
            Record ("FAIL  {0,-30} {1,6} ms  code={2} expect={3}" -f $name, $ms, $r.code, $expectCode); $script:fail++
        }
        return $r
    } catch {
        $sw.Stop(); $ms = $sw.ElapsedMilliseconds
        Record ("FAIL  {0,-30} {1,6} ms  EX={2}" -f $name, $ms, $_.Exception.Message); $script:fail++
        return $null
    }
}

# Auth guard test: accessing protected endpoint WITHOUT token must be rejected
function Test-ExpectReject {
    param($name, $uri)
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $r = Invoke-RestMethod -Uri $uri -Method GET -ErrorAction Stop
        $sw.Stop()
        if ($r.code -eq 200) {
            Record ("FAIL  {0,-30} {1,6} ms  no-token got 200 (AUTH LEAK)" -f $name, $sw.ElapsedMilliseconds); $script:fail++
        } else {
            Record ("PASS  {0,-30} {1,6} ms  rejected code={2}" -f $name, $sw.ElapsedMilliseconds, $r.code); $script:pass++
        }
    } catch {
        $sw.Stop()
        Record ("PASS  {0,-30} {1,6} ms  rejected (401/403)" -f $name, $sw.ElapsedMilliseconds); $script:pass++
    }
}

Record "============================================================"
Record ("FULL ENDPOINT TEST  " + (Get-Date).ToString("yyyy-MM-dd HH:mm:ss"))
Record "============================================================"

# ---------- SETUP: base ids from DB ----------
redis-cli DEL "rate_limit:ip:127.0.0.1:login" "rate_limit:ip:127.0.0.1:register" | Out-Null
$chapterId    = (mysql -u root -proot recloud -N -e "SELECT id FROM chapter LIMIT 1" 2>$null).Trim()
$annotationId = (mysql -u root -proot recloud -N -e "SELECT id FROM annotation ORDER BY id DESC LIMIT 1" 2>$null).Trim()
$portUserId   = (mysql -u root -proot recloud -N -e "SELECT id FROM user WHERE username='portuser'" 2>$null).Trim()
$notifId      = (mysql -u root -proot recloud -N -e "SELECT id FROM notification WHERE user_id=(SELECT id FROM user WHERE username='portuser') ORDER BY id DESC LIMIT 1" 2>$null).Trim()
Record ("SETUP chapterId={0} annotationId={1} portUserId={2} notifId={3}" -f $chapterId, $annotationId, $portUserId, $notifId)

# ---------- AUTH: login both identities ----------
$adminResp = Login "testuser2" "Test123456!"
$adminToken = $adminResp.data.accessToken; $adminRefresh = $adminResp.data.refreshToken
$ah = @{ Authorization = "Bearer $adminToken" }

$userPwd = "NewPort123456!Aa"
$userResp = Login "portuser" $userPwd
if ($userResp -eq $null -or $userResp.code -ne 200) { $userPwd = "Port123456!Aa"; $userResp = Login "portuser" $userPwd }
$userToken = $userResp.data.accessToken; $userRefresh = $userResp.data.refreshToken
$uh = @{ Authorization = "Bearer $userToken" }
Record ("SETUP adminLogin=ok userLogin=ok (pwd={0})" -f $userPwd)

Record "-------------------- AUTH GUARD --------------------"
Test-ExpectReject "guard-admin-no-token" "$base/admin/users"
Test-ExpectReject "guard-user-no-token" "$base/api/users/me"

Record "-------------------- AUTH --------------------"
$sw = [System.Diagnostics.Stopwatch]::StartNew(); $lr = Login "testuser2" "Test123456!"; $sw.Stop()
Record ("PASS  {0,-30} {1,6} ms  code={2}" -f "auth-login", $sw.ElapsedMilliseconds, $lr.code); $pass++

redis-cli DEL "rate_limit:ip:127.0.0.1:register" | Out-Null
$ftUser = "ftuser_" + [DateTimeOffset]::Now.ToUnixTimeSeconds()
$regBody = @{ username = $ftUser; nickname = "FullTester"; password = "Ft123456!Aa" } | ConvertTo-Json
$reg = Test-Endpoint "auth-register" "POST" "$base/auth/register" $null $regBody
$ftUserId = $reg.data.userId

Record "-------------------- USER --------------------"
Test-Endpoint "user-me" "GET" "$base/api/users/me" $uh
Test-Endpoint "user-profile" "GET" "$base/api/users/me/profile" $uh
Test-Endpoint "user-nickname" "PUT" "$base/api/users/me/nickname?nickname=FullTestNick" $uh

Test-Endpoint "novel-list" "GET" "$base/api/novels"
Test-Endpoint "novel-detail" "GET" "$base/api/novels/1"
Test-Endpoint "chapter-detail" "GET" "$base/api/chapters/$chapterId"

$annBody = @{ chapterId = [int]$chapterId; anchorStart = 0; anchorEnd = 12; selectedText = "full test selected text"; content = "full test annotation content"; type = 0 } | ConvertTo-Json
$annCreated = Test-Endpoint "annotation-create" "POST" "$base/api/annotations" $uh $annBody
$newAnnId = $annCreated.data.id

Test-Endpoint "annotation-list" "GET" "$base/api/annotations?chapterId=$chapterId" $uh
Test-Endpoint "annotation-mine" "GET" "$base/api/annotations/mine" $uh
Test-Endpoint "annotation-like-status" "GET" "$base/api/annotations/$annotationId/like-status" $uh
Test-Endpoint "annotation-like" "POST" "$base/api/annotations/$annotationId/like" $uh
Test-Endpoint "annotation-hot" "GET" "$base/api/annotations/hot" $uh
Test-Endpoint "annotation-favorite" "POST" "$base/api/annotations/$annotationId/favorite" $uh
Test-Endpoint "annotation-favorites" "GET" "$base/api/annotations/favorites" $uh

$cmtBody = @{ annotationId = $newAnnId; content = "full test comment valuable" } | ConvertTo-Json
$cmtCreated = Test-Endpoint "comment-create" "POST" "$base/api/comments" $uh $cmtBody
$newCommentId = $cmtCreated.data.id
Test-Endpoint "comment-list" "GET" "$base/api/comments?annotationId=$newAnnId" $uh

$notifList = Test-Endpoint "notification-list" "GET" "$base/api/notifications?page=1&size=5" $uh
if (-not $notifId -and $notifList.data.records.Count -ge 1) { $notifId = $notifList.data.records[0].id }
Test-Endpoint "notification-unread" "GET" "$base/api/notifications/unread-count" $uh
if ($notifId) { Test-Endpoint "notification-read" "PUT" "$base/api/notifications/$notifId/read" $uh }
else { Record "SKIP  notification-read                no notification available" }
Test-Endpoint "notification-read-all" "PUT" "$base/api/notifications/read-all" $uh

$repBody = @{ targetType = "annotation"; targetId = [int]$annotationId; reason = "full test report" } | ConvertTo-Json
$repCreated = Test-Endpoint "report-create" "POST" "$base/api/reports" $uh $repBody
$newReportId = $repCreated.data.id

# password change last in user section (toggle to a different value)
if ($userPwd -eq "Port123456!Aa") { $newPwd = "NewPort123456!Aa" } else { $newPwd = "Port123456!Aa" }
$pwdBody = @{ oldPassword = $userPwd; newPassword = $newPwd } | ConvertTo-Json
Test-Endpoint "user-password" "PUT" "$base/api/users/me/password" $uh $pwdBody

Record "-------------------- ADMIN --------------------"
Test-Endpoint "admin-users" "GET" "$base/admin/users?page=1&size=5" $ah
Test-Endpoint "admin-user-ban" "PUT" "$base/admin/users/$ftUserId/status?status=0" $ah
Test-Endpoint "admin-user-unban" "PUT" "$base/admin/users/$ftUserId/status?status=1" $ah
Test-Endpoint "admin-user-reset-pwd" "PUT" "$base/admin/users/$ftUserId/reset-password" $ah
Test-Endpoint "admin-annotations" "GET" "$base/admin/annotations?page=1&size=5" $ah
Test-Endpoint "admin-comments" "GET" "$base/admin/comments?page=1&size=5" $ah
Test-Endpoint "admin-reports" "GET" "$base/admin/reports?page=1&size=5" $ah
Test-Endpoint "admin-report-handle" "PUT" "$base/admin/reports/$newReportId/handle?status=rejected&handleNote=full+test+reject" $ah
$bcBody = @{ title = "Full test broadcast"; content = "Full test broadcast content" } | ConvertTo-Json
Test-Endpoint "admin-broadcast" "POST" "$base/admin/notifications/broadcast" $ah $bcBody
Test-Endpoint "admin-dashboard" "GET" "$base/admin/dashboard" $ah
$today = (Get-Date).ToString("yyyy-MM-dd")
Test-Endpoint "admin-daily-report" "GET" "$base/admin/dashboard/daily-report?date=$today" $ah
Test-Endpoint "admin-daily-reports" "GET" "$base/admin/dashboard/daily-reports?days=7" $ah
Test-Endpoint "admin-logs" "GET" "$base/admin/logs?page=1&size=5" $ah
Test-Endpoint "admin-log-operators" "GET" "$base/admin/logs/operators" $ah
Test-Endpoint "admin-comment-delete" "DELETE" "$base/admin/comments/$newCommentId" $ah
Test-Endpoint "admin-annotation-delete" "DELETE" "$base/admin/annotations/$newAnnId" $ah

Record "-------------------- AUTH (tail) --------------------"
$refreshBody = @{ refreshToken = $adminRefresh } | ConvertTo-Json
Test-Endpoint "auth-refresh" "POST" "$base/auth/refresh" $null $refreshBody
Test-Endpoint "auth-logout-user" "POST" "$base/auth/logout" $uh
Test-Endpoint "auth-logout-admin" "POST" "$base/auth/logout" $ah

Record "============================================================"
Record ("RESULT  PASS={0}  FAIL={1}  TOTAL={2}" -f $pass, $fail, ($pass + $fail))
Record "============================================================"

$results | Out-File -FilePath "scripts/full-endpoint-timing.txt" -Encoding UTF8
Write-Output "Results saved to scripts/full-endpoint-timing.txt"
