package com.japy.service;

import com.japy.entity.PointsLog;
import com.japy.entity.User;
import com.japy.mapper.PointsLogMapper;
import com.japy.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointsService {

    private final PointsLogMapper pointsLogMapper;
    private final UserMapper userMapper;

    // 每日上限
    private static final int DAILY_CONTRIBUTION_CAP = 10;  // 发帖+发评论
    private static final int DAILY_RECOGNITION_CAP = 20;   // 被赞+被加精

    // 等级阈值
    private static final int[] LEVEL_THRESHOLDS = {0, 30, 100, 300, 800};
    private static final String[] LEVEL_TITLES = {"新读者", "书友", "活跃书友", "资深书友", "学者"};

    /**
     * 加分（带上限检查）
     * @param action post/comment/liked/comment_liked/featured/penalty
     */
    public void earn(Long userId, String action, int points) {
        if (userId == null) return;

        // 惩罚不走上限
        if (!"penalty".equals(action)) {
            if ("post".equals(action) || "comment".equals(action)) {
                int today = pointsLogMapper.todayContributionPoints(userId);
                if (today >= DAILY_CONTRIBUTION_CAP) return;
                points = Math.min(points, DAILY_CONTRIBUTION_CAP - today);
            } else {
                int today = pointsLogMapper.todayRecognitionPoints(userId);
                if (today >= DAILY_RECOGNITION_CAP) return;
                points = Math.min(points, DAILY_RECOGNITION_CAP - today);
            }
        }

        if (points == 0) return;

        // 记录流水
        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setPoints(points);
        pointsLogMapper.insert(log);

        // 更新用户积分和等级
        User user = userMapper.selectById(userId);
        if (user == null) return;
        int newPoints = Math.max(0, (user.getPoints() == null ? 0 : user.getPoints()) + points);
        user.setPoints(newPoints);
        user.setLevel(calcLevel(newPoints));
        userMapper.updateById(user);
    }

    public static int calcLevel(int points) {
        int level = 0;
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (points >= LEVEL_THRESHOLDS[i]) { level = i; break; }
        }
        return level;
    }

    public static String levelTitle(int level) {
        return level >= 0 && level < LEVEL_TITLES.length ? LEVEL_TITLES[level] : "学者";
    }

    public static int nextThreshold(int level) {
        return level + 1 < LEVEL_THRESHOLDS.length ? LEVEL_THRESHOLDS[level + 1] : -1;
    }
}
