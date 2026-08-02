# -*- coding: utf-8 -*-
"""moments 冒烟测试：注册→登录→发动态→点赞→评论楼中楼→通知→主页"""
import json
import urllib.request
import sys

BASE = "http://localhost:8083"

def req(method, path, token=None, body=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"http_error": e.code, "body": e.read().decode("utf-8", "replace")}

def ok(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + ((" | " + str(detail)) if detail and not cond else ""))
    return cond

def register_or_login(username, password, nickname):
    r = req("POST", "/auth/register", body={"username": username, "password": password, "nickname": nickname})
    if r["code"] == 200:
        return True
    return r["code"] == 400 and "已存在" in r["msg"]

ok("注册 alice", register_or_login("alice", "123456", "小艾"))
ok("注册 bob", register_or_login("bob", "123456", "小波"))
ok("重复注册被拒", (r := req("POST", "/auth/register", body={"username": "alice", "password": "123456", "nickname": "x"}))["code"] == 400, r)

TA = req("POST", "/auth/login", body={"username": "alice", "password": "123456"})["data"]["token"]
TB = req("POST", "/auth/login", body={"username": "bob", "password": "123456"})["data"]["token"]
ok("登录成功", bool(TA and TB))

ok("发动态", (r := req("POST", "/api/moments", TA, {"content": "今天天气不错，出来晒晒太阳。"}))["code"] == 200, r)
MID = r["data"]["id"]
ok("空内容被拒", req("POST", "/api/moments", TA, {"content": ""})["code"] == 400)

ok("未登录看时间线", (r := req("GET", "/api/moments?page=1&size=5"))["code"] == 200 and len(r["data"]["list"]) >= 1, r)
ok("未登录无token也返回liked=false", r["data"]["list"][0].get("liked") is None or r["data"]["list"][0]["liked"] is False)

ok("bob点赞", (r := req("POST", f"/api/moments/{MID}/like", TB))["data"]["liked"] is True, r)
ok("赞列表含bob", (r := req("GET", f"/api/moments/{MID}/likes"))["data"]["total"] == 1 and r["data"]["list"][0]["nickname"] == "小波", r)
ok("bob(点赞者)看到liked=true", (r := req("GET", "/api/moments?page=1&size=5", TB))["data"]["list"][0]["liked"] is True, r)
ok("alice(未点赞)看到liked=false", (r := req("GET", "/api/moments?page=1&size=5", TA))["data"]["list"][0]["liked"] is False, r)
ok("bob取消点赞", req("POST", f"/api/moments/{MID}/like", TB)["data"]["liked"] is False)
ok("bob再点赞", req("POST", f"/api/moments/{MID}/like", TB)["data"]["liked"] is True)

ok("bob评论", (r := req("POST", "/api/comments", TB, {"momentId": MID, "content": "确实不错！"}))["code"] == 200, r)
CID = r["data"]["id"]
ok("alice楼中楼回复", (r := req("POST", "/api/comments", TA, {"momentId": MID, "parentId": CID, "replyTo": "小波", "content": "谢谢小波～"}))["code"] == 200, r)
ok("回复不存在的评论被拒", req("POST", "/api/comments", TA, {"momentId": MID, "parentId": 99999, "content": "x"})["code"] == 400)
ok("评论列表含楼中楼", (r := req("GET", f"/api/comments?momentId={MID}"))["code"] == 200 and len(r["data"]["list"]) == 1 and len(r["data"]["list"][0]["replies"]) == 1, r)

ok("alice收到被赞+被评论通知", (r := req("GET", "/api/notifications", TA))["code"] == 200 and r["data"]["total"] >= 2, r)
ok("alice未读数>=2", req("GET", "/api/notifications/unread-count", TA)["data"]["count"] >= 2)
ok("全部已读", req("PUT", "/api/notifications/read-all", TA)["code"] == 200)
ok("已读后未读=0", req("GET", "/api/notifications/unread-count", TA)["data"]["count"] == 0)

ok("alice个人主页含动态", (r := req("GET", "/api/users/2?page=1&size=5"))["code"] == 200 and r["data"]["moments"]["total"] >= 1, r)
ok("bob删自己的评论", req("DELETE", f"/api/comments/{CID}", TB)["code"] == 200)
ok("alice不能删bob的评论(已删)", req("DELETE", f"/api/comments/{CID}", TA)["code"] == 400)
ok("bob不能删alice的动态", req("DELETE", f"/api/moments/{MID}", TB)["code"] == 400)
ok("alice删自己的动态", req("DELETE", f"/api/moments/{MID}", TA)["code"] == 200)

print("\n=== 冒烟测试结束 ===")
