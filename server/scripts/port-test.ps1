$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = "http://localhost:8081"

function Login($u, $p) {
    $body = @{ username = $u; password = $p } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$base/auth/login" -Method POST -ContentType "application/json" -Body $body
    return $r.data.accessToken
}

$pass = 0; $fail = 0
function Check($name, $cond, $detail) {
    if ($cond) { Write-Output ("PASS  " + $name + "  " + $detail); $script:pass++ }
    else { Write-Output ("FAIL  " + $name + "  " + $detail); $script:fail++ }
}

redis-cli DEL "rate_limit:ip:127.0.0.1:login" "rate_limit:ip:127.0.0.1:register" | Out-Null

Write-Output "========== ADMIN =========="
$admin = Login "testuser2" "Test123456!"
$ah = @{ Authorization = "Bearer $admin" }
Check "admin-login" ($admin -ne $null) ""

$novels = Invoke-RestMethod -Uri "$base/api/novels"
Check "novel-list" ($novels.code -eq 200 -and $novels.data.Count -ge 1) ("count=" + $novels.data.Count)

$novel1 = Invoke-RestMethod -Uri "$base/api/novels/1"
Check "novel-detail" ($novel1.code -eq 200) ("id=" + $novel1.data.id)

$admUsers = Invoke-RestMethod -Uri "$base/admin/users?page=1&size=5" -Headers $ah
Check "admin-users" ($admUsers.code -eq 200 -and $admUsers.data.total -ge 1) ("total=" + $admUsers.data.total)

$admAnn = Invoke-RestMethod -Uri "$base/admin/annotations?page=1&size=5" -Headers $ah
$annOk = $admAnn.code -eq 200 -and $admAnn.data.records.Count -ge 1 -and $admAnn.data.records[0].userNickname -ne $null
Check "admin-annotations-join" $annOk ("total=" + $admAnn.data.total + " firstNick=" + $admAnn.data.records[0].userNickname)

$admRep = Invoke-RestMethod -Uri "$base/admin/reports?page=1&size=5" -Headers $ah
$repOk = $admRep.code -eq 200 -and $admRep.data.records.Count -ge 1 -and $admRep.data.records[0].reporterNickname -ne $null
Check "admin-reports-join" $repOk ("total=" + $admRep.data.total)

$admCom = Invoke-RestMethod -Uri "$base/admin/comments?page=1&size=5" -Headers $ah
$comOk = $admCom.code -eq 200 -and $admCom.data.records.Count -ge 1 -and $admCom.data.records[0].userNickname -ne $null
Check "admin-comments-join" $comOk ("total=" + $admCom.data.total)

$dash = Invoke-RestMethod -Uri "$base/admin/dashboard" -Headers $ah
Check "admin-dashboard" ($dash.code -eq 200) ("userCount=" + $dash.data.userCount)

Write-Output "========== USER =========="
redis-cli DEL "rate_limit:ip:127.0.0.1:register" | Out-Null
$regBody = @{ username = "portuser"; nickname = "PortTester"; password = "Port123456!Aa" } | ConvertTo-Json
$reg = Invoke-RestMethod -Uri "$base/auth/register" -Method POST -ContentType "application/json" -Body $regBody
if ($reg.code -eq 200) { $userToken = $reg.data.accessToken; Check "user-register" $true ("userId=" + $reg.data.userId) }
else {
    redis-cli DEL "rate_limit:ip:127.0.0.1:login" | Out-Null
    $userToken = Login "portuser" "Port123456!Aa"
    Check "user-login-existing" ($userToken -ne $null) ("regMsg=" + $reg.msg)
}
$uh = @{ Authorization = "Bearer $userToken" }

$me = Invoke-RestMethod -Uri "$base/api/users/me" -Headers $uh
Check "user-me" ($me.code -eq 200) ("nickname=" + $me.data.nickname)

$annList = Invoke-RestMethod -Uri "$base/api/annotations?chapterId=3" -Headers $uh
Check "annotation-list" ($annList.code -eq 200) ("count=" + $annList.data.Count)

$targetAnn = $annList.data[0].id
$like = Invoke-RestMethod -Uri "$base/api/annotations/$targetAnn/like" -Method POST -Headers $uh
Check "annotation-like" ($like.code -eq 200) ("annId=" + $targetAnn)

$cmtBody = @{ annotationId = $targetAnn; content = "port test comment valuable" } | ConvertTo-Json
$cmt = Invoke-RestMethod -Uri "$base/api/comments" -Method POST -ContentType "application/json" -Headers $uh -Body $cmtBody
Check "comment-create" ($cmt.code -eq 200) ("commentId=" + $cmt.data.id)

$unread = Invoke-RestMethod -Uri "$base/api/notifications/unread-count" -Headers $uh
Check "notification-unread" ($unread.code -eq 200) ("unread=" + $unread.data.count)

$profile = Invoke-RestMethod -Uri "$base/api/users/me/profile" -Headers $uh
Check "user-profile" ($profile.code -eq 200) ("annotationCount=" + $profile.data.annotationCount)

$repBody = @{ targetType = "annotation"; targetId = $targetAnn; reason = "port test report" } | ConvertTo-Json
$rep = Invoke-RestMethod -Uri "$base/api/reports" -Method POST -ContentType "application/json" -Headers $uh -Body $repBody
Check "report-create" ($rep.code -eq 200) ("reportId=" + $rep.data.id + " msg=" + $rep.msg)

Write-Output "=========================================="
Write-Output ("RESULT  PASS=" + $pass + "  FAIL=" + $fail)
