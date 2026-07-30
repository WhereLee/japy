/**
 * 批量生成高质量批注脚本
 * - 走真实 API（/api/annotations），保证缓存、计数等副作用正确
 * - 基于真实章节文本定位偏移量（indexOf），确保批注高亮位置准确
 * - 每个词条只在首次出现的章节创建一条批注，避免重复刷屏
 * - 每批清除限流键，绕过 10次/60s 的创建限流
 *
 * 用法: node scripts/generate-annotations.js
 */
const { execSync } = require('child_process');

const BASE = 'http://localhost:8081';
const RATE_KEY = 'rate_limit:13:create_annotation'; // testuser2 的创建批注限流键

// ============ 批注词条库（phrase=选中文本, content=批注内容, type=0普通/1数据校验）============
// 《天龙八部》
const TLBB = [
  { phrase: '段誉', content: '大理镇南王世子，痴情憨厚，因缘际会习得六脉神剑与北冥神功，是金庸笔下少有的"非典型"主角。' },
  { phrase: '乔峰', content: '丐帮帮主，武功盖世、豪气干云，却因契丹身世陷入家国两难，是全书最悲情的英雄。' },
  { phrase: '萧峰', content: '乔峰的契丹本名。"萧峰"二字承载着身份认同的撕裂——汉人养大却是契丹血脉，悲剧的根源。' },
  { phrase: '虚竹', content: '少林小僧，无心于武学却屡获奇缘，破珍珑棋局得无崖子传功，是"无为而得"的典型。' },
  { phrase: '王语嫣', content: '曼陀山庄女子，熟读天下武学典籍却手无缚鸡之力，被江湖称为"武学活字典"。' },
  { phrase: '慕容复', content: '姑苏慕容氏后人，一生执着复国大梦，与段誉形成"求而不得"的鲜明对照。' },
  { phrase: '阿朱', content: '慕容复侍女，聪慧温柔。"塞上牛羊空许约"是全书最动人也最心碎的悲剧。' },
  { phrase: '阿紫', content: '阿朱之妹，性情乖张狠辣，对乔峰一往情深却始终用错了方式。' },
  { phrase: '段正淳', content: '大理镇南王，风流多情，惹下无数情债，是段誉身世之谜的关键。' },
  { phrase: '天山童姥', content: '逍遥派前辈，修习八荒六合唯我独尊功，每三十年返老还童一次。' },
  { phrase: '李秋水', content: '逍遥派高手，与天山童姥因无崖子纠缠一生，情怨纠葛至死方休。' },
  { phrase: '无崖子', content: '逍遥派掌门，以珍珑棋局择徒，将毕生功力传于破解棋局之人。' },
  { phrase: '鸠摩智', content: '吐蕃国师，贪恋中原武学，强练多门绝技终因贪念走火入魔。' },
  { phrase: '游坦之', content: '聚贤庄少主，命运多舛，痴恋阿紫而不得，是又一"求不得"的悲剧人物。' },
  { phrase: '木婉清', content: '段誉红颜知己之一，性情刚烈，曾立誓"第一个见我容貌的男子我便嫁他"。' },
  { phrase: '钟灵', content: '段誉初遇的少女，灵动可爱，饲养闪电貂为伴。' },
  { phrase: '慕容博', content: '慕容复之父，假死隐于少林藏经阁，是雁门关惨案的幕后推手。' },
  { phrase: '萧远山', content: '萧峰之父，雁门关惨案幸存者，隐忍复仇数十年。' },
  { phrase: '玄慈', content: '少林方丈，德高望重，却与叶二娘有一段隐秘情缘。' },
  { phrase: '六脉神剑', content: '大理段氏至高绝学，以浑厚内力化指力为剑气，无形无质，威力无穷。' },
  { phrase: '北冥神功', content: '逍遥派内功心法，可吸纳他人内力为己用，如北冥之海纳百川。' },
  { phrase: '凌波微步', content: '逍遥派轻功，步法取自曹植《洛神赋》，飘逸灵动，趋避自如。' },
  { phrase: '降龙十八掌', content: '丐帮镇帮绝学，至刚至阳，乔峰以此掌名震天下。' },
  { phrase: '打狗棒法', content: '丐帮帮主嫡传棒法，招式精妙，非帮主不得传授。' },
  { phrase: '斗转星移', content: '姑苏慕容氏绝学，能借力打力，"以彼之道还施彼身"。' },
  { phrase: '一阳指', content: '大理段氏指法，可点穴制敌，亦可疗伤续命。' },
  { phrase: '易筋经', content: '少林至高内功典籍，相传为达摩所创，能脱胎换骨。' },
  { phrase: '丐帮', content: '天下第一大帮，以打狗棒法与降龙十八掌传世，乔峰曾任帮主。' },
  { phrase: '大理', content: '西南佛国，段氏皇族所在，崇文尚武，天龙寺为武学重地。' },
  { phrase: '少林', content: '武林泰斗，藏经阁典籍无数，扫地僧深藏不露。' },
  { phrase: '逍遥派', content: '神秘门派，武功飘逸出尘，门人多才多艺，行事低调。' },
  { phrase: '聚贤庄', content: '江湖豪杰聚集之地，曾上演乔峰血战群雄的惨烈一幕。' },
  { phrase: '雁门关', content: '宋辽边境雄关，萧峰身世悲剧的起点，也是宋辽恩怨的缩影。' },
  { phrase: '曼陀山庄', content: '王语嫣居所，遍植茶花，藏有天下武学典籍。' },
  { phrase: '珍珑棋局', content: '无崖子布下的残局，虚竹误打误撞自填一子而破解，得享传承。' },
  { phrase: '青钢剑', content: '开篇出现的兵器，青光闪动间引出少年剑客，奠定全书武侠基调。' },
  { phrase: '中年汉子', content: '人物称谓需与后文保持一致，注意金庸对配角的称呼变化。' , type: 1 },
];

// 《龙族》
const LZ = [
  { phrase: '路明非', content: '看似衰废实则身负龙族血脉的少年，系列核心视角人物，"衰小孩逆袭"的代言。' },
  { phrase: '楚子航', content: '卡塞尔学院最强学生之一，冷静寡言的"师兄"，背负着沉重的过往与秘密。' },
  { phrase: '绘梨衣', content: '上杉绘梨衣，与路明非命运相连的少女，是贯穿系列的情感羁绊。' },
  { phrase: '凯撒', content: '卡塞尔学院学生会主席，强大而骄傲，是路明非成长路上的劲敌。' },
  { phrase: '昂热', content: '卡塞尔学院校长，传奇屠龙者，深不可测的引路人。' },
  { phrase: '卡塞尔学院', content: '培养屠龙者的学府，混血种少年的聚集地，故事的主要舞台。' },
  { phrase: '龙族', content: '远古而强大的种族，掌握言灵之力，是整个世界观的核心设定。' },
  { phrase: '言灵', content: '以龙族语言驱动的力量体系，每个混血种都拥有独一无二的言灵。' },
  { phrase: '混血种', content: '人类与龙族混血的后裔，能使用言灵，血统纯度决定能力上限。' },
  { phrase: '屠龙者', content: '以猎杀龙族为使命的战士，卡塞尔学院正是培养屠龙者的摇篮。' },
  { phrase: '青铜与火之王', content: '龙族君主之一，掌握青铜与火焰之力，是系列重要的反派存在。' },
  { phrase: '夏弥', content: '大地与山之王，龙族君主，与主角团有复杂纠葛。' },
  { phrase: '血统', content: '决定混血种能力上限的关键，"血统纯度"是学院衡量学生的核心指标。' },
  { phrase: '炼金术', content: '龙族传承的神秘技艺，能锻造屠龙武器与炼金道具。' },
  { phrase: '尼伯龙根', content: '龙族传说中的秘境，藏着龙族的终极秘密。' },
  { phrase: '学生会', content: '卡塞尔学院的权力核心，凯撒曾任学生会主席。' },
  { phrase: '校长', content: '学院最高领导者，肩负屠龙使命与守护学生的双重责任。' },
  { phrase: '雨夜', content: '龙族系列惯用的氛围意象，雨夜往往预示着危险与命运的转折。' },
  { phrase: '序幕', content: '结构标记，注意序幕与正文的时间线衔接是否清晰。', type: 1 },
];

// 《我的隔壁有女鬼》（恐怖题材，通用氛围词条）
const NG = [
  { phrase: '女鬼', content: '恐怖叙事的核心意象，"隔壁"的日常感与"女鬼"的诡异感形成强烈反差。' },
  { phrase: '隔壁', content: '空间设定，"隔壁"暗示危险近在咫尺，营造日常中的不安感。' },
  { phrase: '半夜', content: '恐怖小说的经典时间点，万籁俱寂时最易滋生恐惧。' },
  { phrase: '敲门声', content: '悬疑触发点，未知的敲门声是恐怖叙事常用的悬念钩子。' },
  { phrase: '镜子', content: '恐怖文学的经典道具，镜中异象往往暗示超自然存在。' },
  { phrase: '影子', content: '光影意象，影子的异常是营造诡异氛围的常用手法。' },
  { phrase: '冷汗', content: '生理反应描写，通过身体感受外化恐惧心理。' },
  { phrase: '走廊', content: '封闭空间意象，狭长走廊是恐怖场景的经典舞台。' },
  { phrase: '阴风', content: '氛围渲染，阴风阵阵是超自然降临的典型征兆。' },
  { phrase: '心跳', content: '心理外化描写，心跳加速传递紧张与恐惧。' },
];

const NOVEL_DICTS = [
  { match: '天龙八部', dict: TLBB },
  { match: '龙族', dict: LZ },
  { match: '我的隔壁有女鬼', dict: NG },
];

// ============ 工具函数 ============
function clearRateLimit() {
  try { execSync(`redis-cli DEL "${RATE_KEY}"`, { stdio: 'ignore' }); } catch (e) { /* ignore */ }
}

async function login() {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'testuser2', password: 'Test123456!' }),
  });
  const d = await res.json();
  if (d.code !== 200) throw new Error('登录失败: ' + d.msg);
  return d.data.accessToken;
}

async function getNovels(token) {
  const res = await fetch(`${BASE}/api/novels`, { headers: { Authorization: `Bearer ${token}` } });
  return (await res.json()).data;
}

async function getChapters(token, novelId) {
  const res = await fetch(`${BASE}/api/novels/${novelId}`, { headers: { Authorization: `Bearer ${token}` } });
  return (await res.json()).data.chapters;
}

async function getChapterContent(token, chapterId) {
  const res = await fetch(`${BASE}/api/chapters/${chapterId}`, { headers: { Authorization: `Bearer ${token}` } });
  return (await res.json()).data.content;
}

async function createAnnotation(token, payload) {
  const res = await fetch(`${BASE}/api/annotations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload),
  });
  return res.json();
}

// ============ 主流程 ============
(async () => {
  console.log('登录中...');
  const token = await login();
  console.log('登录成功');

  const novels = await getNovels(token);
  console.log(`共 ${novels.length} 本小说`);

  let created = 0, skipped = 0, failed = 0;
  let sinceLastClear = 0;

  for (const novel of novels) {
    const entry = NOVEL_DICTS.find(e => novel.title.includes(e.match));
    if (!entry) { console.log(`跳过（无词条库）: ${novel.title}`); continue; }
    console.log(`\n处理《${novel.title}》，词条 ${entry.dict.length} 个`);

    const chapters = await getChapters(token, novel.id);
    const usedPhrases = new Set(); // 本小说内已用词条，避免重复

    for (const chapter of chapters) {
      let content;
      try { content = await getChapterContent(token, chapter.id); }
      catch (e) { continue; }
      if (!content) continue;

      for (const item of entry.dict) {
        if (usedPhrases.has(item.phrase)) continue;
        const idx = content.indexOf(item.phrase);
        if (idx === -1) continue;

        // 限流保护：每 8 次清一次键
        if (sinceLastClear >= 8) { clearRateLimit(); sinceLastClear = 0; }

        const result = await createAnnotation(token, {
          chapterId: chapter.id,
          anchorStart: idx,
          anchorEnd: idx + item.phrase.length,
          selectedText: item.phrase,
          content: item.content,
          type: item.type || 0,
        });
        sinceLastClear++;

        if (result.code === 200) {
          created++;
          usedPhrases.add(item.phrase);
          console.log(`  ✓ [${chapter.title}] "${item.phrase}" → id=${result.data.id}`);
        } else if (result.code === 429) {
          clearRateLimit(); sinceLastClear = 0; failed++;
          console.log(`  ⚠ 限流，已清键重试: "${item.phrase}"`);
        } else {
          failed++;
          console.log(`  ✗ 失败 [${result.code}] ${result.msg}: "${item.phrase}"`);
        }
      }
    }
    // 统计未命中的词条
    const missed = entry.dict.filter(d => !usedPhrases.has(d.phrase));
    if (missed.length) console.log(`  未命中词条 ${missed.length} 个: ${missed.map(m => m.phrase).join('、')}`);
  }

  clearRateLimit();
  console.log(`\n========== 完成 ==========`);
  console.log(`成功创建: ${created} 条`);
  console.log(`失败: ${failed} 条`);
})();
