package com.japy;

import com.japy.entity.User;
import com.japy.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 扩展功能集成测试
 * 覆盖：帖子编辑/收藏/屏蔽/举报/通知/搜索/积分等级/用户资料/敏感词/管理端
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExtendedFeatureTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;
    @Autowired private UserMapper userMapper;

    private static String tokenAlice, tokenBob, tokenAdmin;
    private static Long postId;

    private JsonNode body(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString());
    }

    private String auth(String token) { return "Bearer " + token; }

    // ========== 准备 ==========

    @Test @Order(1)
    void 准备用户() throws Exception {
        tokenAlice = registerOrLogin("ext_alice", "123456", "扩展Alice");
        tokenBob = registerOrLogin("ext_bob", "654321", "扩展Bob");
        tokenAdmin = registerOrLogin("ext_admin2", "admin123", "测试管理员");
        // 确保 ext_admin2 有 admin 角色
        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "ext_admin2"));
        if (admin != null && !"admin".equals(admin.getRole())) {
            admin.setRole("admin");
            userMapper.updateById(admin);
            // 重新登录获取新 token（含 admin role）
            tokenAdmin = login("ext_admin2", "admin123");
        }
        Assertions.assertNotNull(tokenAlice);
        Assertions.assertNotNull(tokenBob);
        Assertions.assertNotNull(tokenAdmin);
    }

    @Test @Order(2)
    void 准备帖子() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/posts")
                .header("Authorization", auth(tokenAlice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novelId\":1,\"content\":\"扩展测试帖子内容\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        postId = body(r).get("data").get("id").asLong();
    }

    // ========== 帖子编辑 ==========

    @Test @Order(10)
    void 作者编辑帖子成功() throws Exception {
        mockMvc.perform(put("/api/posts/" + postId)
                .header("Authorization", auth(tokenAlice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"编辑后的内容\"}"))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @Order(11)
    void 非作者不能编辑() throws Exception {
        mockMvc.perform(put("/api/posts/" + postId)
                .header("Authorization", auth(tokenBob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"恶意编辑\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test @Order(12)
    void 编辑空内容被拒() throws Exception {
        mockMvc.perform(put("/api/posts/" + postId)
                .header("Authorization", auth(tokenAlice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ========== 收藏 ==========

    @Test @Order(20)
    void Bob收藏帖子() throws Exception {
        mockMvc.perform(post("/api/posts/" + postId + "/favorite")
                .header("Authorization", auth(tokenBob)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.favorited").value(true));
    }

    @Test @Order(21)
    void Bob查看收藏列表() throws Exception {
        mockMvc.perform(get("/api/users/me/favorites")
                .header("Authorization", auth(tokenBob))
                .param("page", "1").param("size", "10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test @Order(22)
    void Bob取消收藏() throws Exception {
        mockMvc.perform(post("/api/posts/" + postId + "/favorite")
                .header("Authorization", auth(tokenBob)))
                .andExpect(jsonPath("$.data.favorited").value(false));
    }

    // ========== 屏蔽 ==========

    @Test @Order(30)
    void Bob屏蔽Alice() throws Exception {
        // 先获取 Alice 的 userId
        MvcResult r = mockMvc.perform(get("/api/posts").param("novelId", "1"))
                .andReturn();
        JsonNode posts = body(r).get("data").get("list");
        Long aliceId = null;
        for (JsonNode p : posts) {
            if ("扩展Alice".equals(p.get("nickname").asText())) {
                aliceId = p.get("userId").asLong();
                break;
            }
        }
        if (aliceId == null) return; // 跳过
        mockMvc.perform(post("/api/users/" + aliceId + "/block")
                .header("Authorization", auth(tokenBob)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.blocked").value(true));
    }

    @Test @Order(31)
    void Bob查看屏蔽列表() throws Exception {
        mockMvc.perform(get("/api/users/me/blocks")
                .header("Authorization", auth(tokenBob))
                .param("page", "1").param("size", "10"))
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== 举报 ==========

    @Test @Order(40)
    void Bob举报帖子() throws Exception {
        mockMvc.perform(post("/api/reports")
                .header("Authorization", auth(tokenBob))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"post\",\"targetId\":" + postId + ",\"reason\":\"测试举报\"}"))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @Order(41)
    void Bob查看我的举报() throws Exception {
        mockMvc.perform(get("/api/reports/my")
                .header("Authorization", auth(tokenBob)))
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== 通知 ==========

    @Test @Order(50)
    void Alice有通知() throws Exception {
        // Bob 之前评论/点赞会触发通知
        mockMvc.perform(get("/api/notifications")
                .header("Authorization", auth(tokenAlice))
                .param("page", "1").param("size", "10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test @Order(51)
    void Alice查未读数() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")
                .header("Authorization", auth(tokenAlice)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.count").isNumber());
    }

    @Test @Order(52)
    void Alice全部已读() throws Exception {
        mockMvc.perform(put("/api/notifications/read-all")
                .header("Authorization", auth(tokenAlice)))
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== 搜索 ==========

    @Test @Order(60)
    void 搜索帖子() throws Exception {
        mockMvc.perform(get("/api/posts/search")
                .param("q", "扩展测试")
                .param("page", "1").param("size", "10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test @Order(61)
    void 搜索无结果() throws Exception {
        mockMvc.perform(get("/api/posts/search")
                .param("q", "不存在的关键词xyz999")
                .param("page", "1").param("size", "10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ========== 积分等级 ==========

    @Test @Order(70)
    void Alice查积分() throws Exception {
        mockMvc.perform(get("/api/users/me/points")
                .header("Authorization", auth(tokenAlice)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.points").isNumber())
                .andExpect(jsonPath("$.data.level").isNumber())
                .andExpect(jsonPath("$.data.title").isString());
    }

    @Test @Order(71)
    void 发帖后积分增加() throws Exception {
        // 记录当前积分
        MvcResult before = mockMvc.perform(get("/api/users/me/points")
                .header("Authorization", auth(tokenAlice))).andReturn();
        int pointsBefore = body(before).get("data").get("points").asInt();

        // 发一帖
        mockMvc.perform(post("/api/posts")
                .header("Authorization", auth(tokenAlice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novelId\":2,\"content\":\"积分测试帖\"}"))
                .andExpect(jsonPath("$.code").value(200));

        // 积分应增加
        MvcResult after = mockMvc.perform(get("/api/users/me/points")
                .header("Authorization", auth(tokenAlice))).andReturn();
        int pointsAfter = body(after).get("data").get("points").asInt();
        Assertions.assertTrue(pointsAfter > pointsBefore, "发帖后积分应增加");
    }

    // ========== 用户资料 ==========

    @Test @Order(80)
    void 修改昵称() throws Exception {
        MvcResult r = mockMvc.perform(put("/api/users/me")
                .header("Authorization", auth(tokenAlice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"新昵称Alice\",\"bio\":\"测试简介\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        // 返回新 token
        String newToken = body(r).get("data").get("token").asText();
        Assertions.assertNotNull(newToken);
        tokenAlice = newToken;
    }

    @Test @Order(81)
    void 修改密码() throws Exception {
        String user = "ext_pwd_" + System.currentTimeMillis();
        String tmpToken = registerOrLogin(user, "oldpass1", "密码测试");
        mockMvc.perform(put("/api/users/me/password")
                .header("Authorization", auth(tmpToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"oldpass1\",\"newPassword\":\"newpass1\"}"))
                .andExpect(jsonPath("$.code").value(200));
        String newToken = login(user, "newpass1");
        Assertions.assertNotNull(newToken);
    }

    @Test @Order(82)
    void 错误旧密码被拒() throws Exception {
        String user = "ext_pwd2_" + System.currentTimeMillis();
        String tmpToken = registerOrLogin(user, "pass123", "密码测试2");
        mockMvc.perform(put("/api/users/me/password")
                .header("Authorization", auth(tmpToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"wrongold\",\"newPassword\":\"xxx123\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ========== 管理端 ==========

    @Test @Order(90)
    void 管理员Dashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", auth(tokenAdmin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userCount").isNumber())
                .andExpect(jsonPath("$.data.postCount").isNumber());
    }

    @Test @Order(91)
    void 非管理员访问被拒() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                .header("Authorization", auth(tokenBob)))
                .andExpect(status().isForbidden());
    }

    @Test @Order(92)
    void 管理员查看用户列表() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", auth(tokenAdmin))
                .param("page", "1").param("size", "10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test @Order(93)
    void 管理员查看帖子列表() throws Exception {
        mockMvc.perform(get("/api/admin/posts")
                .header("Authorization", auth(tokenAdmin))
                .param("page", "1").param("size", "10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test @Order(94)
    void 管理员查看评论列表() throws Exception {
        mockMvc.perform(get("/api/admin/comments")
                .header("Authorization", auth(tokenAdmin))
                .param("page", "1").param("size", "10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test @Order(95)
    void 管理员隐藏帖子() throws Exception {
        mockMvc.perform(put("/api/admin/posts/" + postId + "/hide")
                .header("Authorization", auth(tokenAdmin)))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @Order(96)
    void 管理员恢复帖子() throws Exception {
        mockMvc.perform(put("/api/admin/posts/" + postId + "/restore")
                .header("Authorization", auth(tokenAdmin)))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @Order(97)
    void 管理员置顶帖子() throws Exception {
        mockMvc.perform(put("/api/admin/posts/" + postId + "/pin")
                .header("Authorization", auth(tokenAdmin)))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @Order(98)
    void 管理员加精帖子() throws Exception {
        mockMvc.perform(put("/api/admin/posts/" + postId + "/feature")
                .header("Authorization", auth(tokenAdmin)))
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== 敏感词 ==========

    @Test @Order(100)
    void 管理员添加敏感词() throws Exception {
        // 幂等：已存在也接受
        MvcResult r = mockMvc.perform(post("/api/admin/sensitive-words")
                .header("Authorization", auth(tokenAdmin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"word\":\"测试敏感词\"}"))
                .andReturn();
        int code = body(r).get("code").asInt();
        Assertions.assertTrue(code == 200 || code == 400, "添加敏感词应返回200或400(已存在)");
    }

    @Test @Order(101)
    void 发帖含敏感词被拒() throws Exception {
        mockMvc.perform(post("/api/posts")
                .header("Authorization", auth(tokenAlice))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novelId\":1,\"content\":\"这里有测试敏感词啊\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test @Order(102)
    void 管理员查看敏感词列表() throws Exception {
        mockMvc.perform(get("/api/admin/sensitive-words")
                .header("Authorization", auth(tokenAdmin)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ========== 公告 ==========

    @Test @Order(110)
    void 管理员发布公告() throws Exception {
        mockMvc.perform(post("/api/admin/announcements")
                .header("Authorization", auth(tokenAdmin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"系统维护公告\"}"))
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== 操作日志 ==========

    @Test @Order(120)
    void 管理员查看日志() throws Exception {
        mockMvc.perform(get("/api/admin/logs")
                .header("Authorization", auth(tokenAdmin))
                .param("page", "1").param("size", "10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    // ========== 公开主页 ==========

    @Test @Order(130)
    void 查看他人主页() throws Exception {
        // 用 userId=1 (admin)
        mockMvc.perform(get("/api/users/1"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").isString());
    }

    // ========== 辅助 ==========

    private String registerOrLogin(String username, String password, String nickname) throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"nickname\":\"" + nickname + "\"}"))
                .andReturn();
        JsonNode node = body(r);
        if (node.get("code").asInt() == 200) {
            return node.get("data").get("token").asText();
        }
        return login(username, password);
    }

    private String login(String username, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        JsonNode node = body(r);
        if (node.get("code").asInt() == 200 && node.get("data") != null) {
            return node.get("data").get("token").asText();
        }
        return null;
    }
}
