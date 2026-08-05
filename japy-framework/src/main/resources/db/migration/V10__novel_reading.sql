-- ============================================================
-- V10: 小说阅读模块（数据模型 + 样章 seed）
-- 设计依据（调研 novel 开源项目 + 主流阅读器）：
--   1) novel: 小说主表，冗余 chapter_count/total_chars 避免 COUNT/SUM 大查询
--   2) novel_chapter: 章节表，UNIQUE(novel_id, chapter_no) 保证章节号稳定，
--      冗余 chars/paragraph_count 供前端估算分页与百分比进度
--   3) novel_paragraph: 段落粒度存储（事实源），阅读时按序拼接整章，
--      为后续 RAG 引用/检索设计（段落是检索最小单元）
--   4) novel_read_progress: 阅读进度（用户×小说唯一），存章内字符偏移
--      （比行号稳定，跨字号/跨设备一致）
-- ============================================================

-- 1. 小说主表
CREATE TABLE jf_novel (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(100) NOT NULL,
    author        VARCHAR(50)  NOT NULL DEFAULT '佚名',
    intro         TEXT,                       -- 简介
    cover         VARCHAR(300),               -- 封面 URL
    category      VARCHAR(30)  DEFAULT '玄幻', -- 分类
    status        SMALLINT DEFAULT 0,         -- 0连载 1完结
    chapter_count INT         DEFAULT 0,      -- 冗余：章节数
    total_chars   BIGINT      DEFAULT 0,      -- 冗余：总字数
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE jf_novel IS '小说主表';

-- 2. 章节表
CREATE TABLE jf_novel_chapter (
    id               BIGSERIAL PRIMARY KEY,
    novel_id         BIGINT NOT NULL,
    chapter_no       INT    NOT NULL,
    title            VARCHAR(200) NOT NULL,
    chars            INT    DEFAULT 0,        -- 本章字数（冗余）
    paragraph_count  INT    DEFAULT 0,        -- 本章段数（冗余）
    create_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chapter_no UNIQUE (novel_id, chapter_no)
);
CREATE INDEX idx_jf_chapter_novel ON jf_novel_chapter (novel_id);
COMMENT ON TABLE jf_novel_chapter IS '小说章节表';

-- 3. 段落表（事实源：章节内容按段落存储）
CREATE TABLE jf_novel_paragraph (
    id         BIGSERIAL PRIMARY KEY,
    novel_id   BIGINT NOT NULL,
    chapter_no INT    NOT NULL,
    para_seq   INT    NOT NULL,
    content    TEXT   NOT NULL,
    chars      INT    DEFAULT 0,
    CONSTRAINT uk_paragraph_seq UNIQUE (novel_id, chapter_no, para_seq)
);
CREATE INDEX idx_jf_paragraph_novel ON jf_novel_paragraph (novel_id);
COMMENT ON TABLE jf_novel_paragraph IS '小说段落表（段落为检索最小单元）';

-- 4. 阅读进度表
CREATE TABLE jf_novel_read_progress (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    novel_id    BIGINT NOT NULL,
    chapter_id  BIGINT NOT NULL,
    char_offset INT    DEFAULT 0,             -- 章内字符偏移（跨设备稳定）
    percent     NUMERIC(5,2) DEFAULT 0,       -- 章内百分比 0-100
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_progress UNIQUE (user_id, novel_id)
);
CREATE INDEX idx_jf_progress_user ON jf_novel_read_progress (user_id);
COMMENT ON TABLE jf_novel_read_progress IS '小说阅读进度表';

-- ============================================================
-- 样章 seed（演示数据，自写内容，幂等）
-- ============================================================
INSERT INTO jf_novel (id, title, author, intro, category, status, chapter_count, total_chars)
SELECT 1, '星海征途', '路遥知马力', '人类文明进入星际纪元。一艘名为"晨星号"的科考船在深空边缘接收到一段未知信号，由此揭开了沉睡亿万年的文明之谜……', '科幻', 1, 5, 2350
WHERE NOT EXISTS (SELECT 1 FROM jf_novel WHERE id = 1);

INSERT INTO jf_novel_chapter (id, novel_id, chapter_no, title, chars, paragraph_count) VALUES
(101, 1, 1, '第一章 深空信号', 420, 4),
(102, 1, 2, '第二章 晨星号', 470, 4),
(103, 1, 3, '第三章 解读', 480, 4),
(104, 1, 4, '第四章 异象', 480, 4),
(105, 1, 5, '第五章 启程', 500, 4)
ON CONFLICT (novel_id, chapter_no) DO NOTHING;

INSERT INTO jf_novel_paragraph (novel_id, chapter_no, para_seq, content, chars) VALUES
(1, 1, 1, '公元 2387 年，人类已经殖民了太阳系内的全部行星。火星的红色沙丘上矗立着千米级的天文望远镜阵列，木卫二的冰层下藏着深海探测站，而地球本身，则成了一个被环状空间站环绕的蓝色明珠。', 96),
(1, 1, 2, '晨星号科考船正沿着海王星轨道外侧的柯伊伯带缓缓航行。这艘船已经在这片被称为"静谧深渊"的区域巡航了整整四百天，任务只有一个：扫描这段广袤的星域，寻找任何异常的信号。', 90),
(1, 1, 3, '"舰长，收到一段未加密的信号。"年轻的通信官林澈盯着屏幕，声音里带着难以抑制的紧张，"频段很奇怪，不是我们认识的任何一种调制方式。"', 64),
(1, 1, 4, '舰长沈安国走到主屏幕前。那段信号在屏幕上以波纹的形式展开，规律的起伏像是某种古老的密码。他沉默了几秒，缓缓开口："记录坐标，全频段存档。这可能是我们这一代人最重要的发现。"', 88),
(1, 2, 1, '信号的分析工作比想象中艰难得多。晨星号上的量子计算核心连续运转了七十二个小时，尝试了上千种解码模型，最终得出的结论让所有人都沉默了：这不是噪声，而是一段经过精心构造的数学语言。', 92),
(1, 2, 2, '"用质数作为基础编码单位，"首席科学家叶澜推了推眼镜，"发送方在刻意用我们必然能识别的方式传递信息。这是一封写给整个宇宙的信。"', 67),
(1, 2, 3, '她调出信号的三维频谱图。如果从侧面观察，那些波纹会组成某种几何图形——一个正十二面体，棱线处有着规则的能量衰减。叶澜的手指在投影上划过："这可能是星图，指向某个坐标。"', 90),
(1, 2, 4, '晨星号的会议室里坐满了人。导航官在星图上标注出那个坐标：距离地球约 1.4 万光年，位于人马座方向的深空。那里什么也没有——至少按照人类现有的天文档案，那里什么都没有。', 86),
(1, 3, 1, '接下来的三周，晨星号上所有人都陷入了近乎疯狂的解码工作中。质数数列每隔一段就会插入一组看似无意义的噪声数据，叶澜坚持认为那是某种校验码，而林澈则尝试用不同的进制去解读。', 89),
(1, 3, 2, '转折出现在第四十二天。林澈在值夜班时无意间把信号的时间轴压缩了一千倍，原本规律的波纹瞬间变成了一连串清晰的脉冲——那是二进制，1 和 0 组成的序列。', 72),
(1, 3, 3, '"它一直在说话，"林澈的声音有些发颤，"只是说得太慢了。一千年才说完一句话，我们却以为它是沉默的。"', 52),
(1, 3, 4, '会议室再次灯火通明。那段二进制的开头部分已经被翻译出来，只有六个字——重复了七遍的六个字："我们一直在等。"', 46),
(1, 4, 1, '消息传回地球的那天，国际深空协调委员会召开了一场闭门会议。七个小时的争论之后，他们给出了一致的结论：派一艘船去看看。那艘船只能是晨星号，因为它距离信号源的方向最近。', 92),
(1, 4, 2, '但沈安国在启程前收到了一个加密文件。那是委员会单独发给舰长的绝密指令，内容只有一行：如果信号指引的坐标处出现了"不该出现的东西"，立刻返航，不要接触。', 84),
(1, 4, 3, '他把那份指令看了很久，最终关掉了终端。船上的每一个人都以为这是一次纯粹的科学考察，只有他知道，这次旅程的终点，可能藏着人类文明从未面对过的选择。', 78),
(1, 4, 4, '晨星号调整了航向。曲率引擎启动的瞬间，舷窗外的群星被拉成了一道道银白色的光带。林澈看着窗外，轻声问："我们会找到什么？"叶澜没有回答，她只是盯着屏幕上那段仍在缓慢流淌的信号，那串二进制依然在重复着同一句话。', 108),
(1, 5, 1, '航行的第十一天，晨星号的传感器捕捉到了一种此前从未记录过的现象：前方的空间出现了某种规则的涟漪，像是平静湖面上投下的石子激起的水波，只是这些波纹横跨了整整两个天文单位。', 90),
(1, 5, 2, '叶澜盯着数据，呼吸变得急促："这不是引力透镜，也不是暗物质扰动……这是空间本身的振动。有人在这里建造了什么东西，一个巨大的、我们无法理解的结构。"', 76),
(1, 5, 3, '沈安国站在舰桥中央，眼前是全息投影展开的未知结构轮廓。它像一枚漂浮在虚空中的巨大齿轮，缓慢地旋转着，中心处有一个漆黑的入口，仿佛在邀请他们进入。他想起了那份加密指令。', 88),
(1, 5, 4, '"保持通讯静默，"他最终说道，"全速前进。我们去看一眼，然后带答案回去。"晨星号驶向那道虚空之门，身后的地球，正在等待着来自深空的第一声回响。', 78)
ON CONFLICT (novel_id, chapter_no, para_seq) DO NOTHING;
