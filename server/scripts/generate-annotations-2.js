/**
 * 批量生成高质量批注脚本（第二批补充词条）
 * 与第一批共用逻辑：每个词条只在首次出现的章节创建一条批注
 */
const { execSync } = require('child_process');

const BASE = 'http://localhost:8081';
const RATE_KEY = 'rate_limit:13:create_annotation';

// 《天龙八部》补充词条
const TLBB = [
  { phrase: '扫地僧', content: '少林藏经阁深藏不露的扫地老僧，武功修为冠绝全书，点化萧远山与慕容博皈依。' },
  { phrase: '段延庆', content: '四大恶人之首"恶贯满盈"，实为大理皇位正统继承人，身世成谜。' },
  { phrase: '四大恶人', content: '段延庆、叶二娘、南海鳄神、云中鹤，各怀悲惨过往的江湖怪客。' },
  { phrase: '叶二娘', content: '四大恶人之一，因失子之痛性情大变，实为玄慈方丈的旧情人。' },
  { phrase: '南海鳄神', content: '四大恶人之一，性格憨直，认段誉为师的桥段反差感十足。' },
  { phrase: '云中鹤', content: '四大恶人之一，轻功卓绝却品行不端，"无恶不作"。' },
  { phrase: '西夏', content: '西北政权，书中涉及西夏招驸马等情节，牵动多方势力。' },
  { phrase: '吐蕃', content: '鸠摩智的故国，与中原武林多有交集。' },
  { phrase: '契丹', content: '萧峰的民族归属。宋辽对立背景下，"契丹"二字重若千钧，是悲剧的导火索。' },
  { phrase: '大宋', content: '故事主要历史背景，宋辽对峙是家国悲剧的时代根源。' },
  { phrase: '结拜', content: '段誉、乔峰、虚竹义结金兰，是全书最温暖的兄弟情义。' },
  { phrase: '内力', content: '武学根基，金庸武学体系的核心概念，决定招式威力的上限。' },
  { phrase: '江湖', content: '武侠世界的代称，"有人的地方就有江湖"，恩怨情仇皆在其中。' },
  { phrase: '茶花', content: '曼陀山庄遍植茶花，是王语嫣居所的标志性意象，也暗合段誉的痴情。' },
  { phrase: '真气', content: '内功修炼的产物，运行于经脉之中，是施展绝技的根本。' },
  { phrase: '穴道', content: '中医经络概念，点穴制敌与解穴疗伤是武侠常见桥段。' },
  { phrase: '帮主', content: '丐帮领袖之位，乔峰曾任，其去留牵动丐帮与江湖大局。' },
  { phrase: '大侠', content: '江湖对德高望重者的尊称，"侠之大者，为国为民"是金庸的侠义内核。' },
  { phrase: '轻功', content: '武林中人腾挪飞跃之术，凌波微步、凌虚步等各有妙处。' },
  { phrase: '剑法', content: '以剑为兵的武学体系，六脉神剑以气化剑是其中至高境界。' },
];

// 《龙族》补充词条
const LZ = [
  { phrase: '路鸣泽', content: '路明非体内的另一人格，强大而神秘，是系列最大的伏笔与谜团之一。' },
  { phrase: '源稚生', content: '日本混血种，与上杉绘梨衣相关的重要角色，牵动日本篇章。' },
  { phrase: '上杉', content: '绘梨衣的姓氏，日本混血种家族，背后牵连龙族秘辛。' },
  { phrase: '死侍', content: '龙族中失去理智的堕落者，是屠龙者最常面对的敌人。' },
  { phrase: '龙王', content: '龙族的统治者，拥有至高言灵之力，是系列终极威胁。' },
  { phrase: '王座', content: '龙族权力的象征，诸王争夺的核心，象征至高统治。' },
  { phrase: '龙血', content: '混血种力量的来源，龙血浓度决定能力上限与失控风险。' },
  { phrase: '执行部', content: '卡塞尔学院负责屠龙任务的实战部门，学生在此接受历练。' },
  { phrase: '东京', content: '龙族故事的重要场景地，日本篇章的舞台，藏着龙族的秘密。' },
  { phrase: '梦境', content: '龙族系列常用叙事手法，梦境与现实交织，逐步揭示真相。' },
  { phrase: '序列', content: '言灵或血统的等级划分方式，决定混血种的能力定位。' },
  { phrase: '任务', content: '屠龙者的核心活动，是推动剧情的主要叙事单元。' },
  { phrase: '武器', content: '屠龙者赖以作战的炼金装备，对抗龙族的关键依仗。' },
  { phrase: '日本', content: '龙族系列重要篇章的发生地，绘梨衣与源稚生的故乡。' },
];

// 《我的隔壁有女鬼》补充词条
const NG = [
  { phrase: '邻居', content: '都市恐怖的核心设定，"邻居"的亲近与未知制造出日常恐惧。' },
  { phrase: '道士', content: '驱邪除魔的传统角色，恐怖小说中常见的解围力量。' },
  { phrase: '阳气', content: '传统观念中克制阴邪的力量，活人靠阳气抵御鬼魅侵扰。' },
  { phrase: '魂魄', content: '灵异叙事的核心概念，魂魄离体或附身是常见设定。' },
  { phrase: '轮回', content: '因果循环的宗教观念，为鬼怪故事提供宿命论框架。' },
  { phrase: '因果', content: '善恶有报的传统叙事逻辑，恐怖故事常以因果收束全篇。' },
  { phrase: '房间', content: '封闭空间意象，私密房间中的异象最能激发恐惧。' },
  { phrase: '电话', content: '现代恐怖常用道具，深夜来电是经典悬念钩子。' },
];

const NOVEL_DICTS = [
  { match: '天龙八部', dict: TLBB },
  { match: '龙族', dict: LZ },
  { match: '我的隔壁有女鬼', dict: NG },
];

function clearRateLimit() {
  try { execSync(`redis-cli DEL "${RATE_KEY}"`, { stdio: 'ignore' }); } catch (e) {}
}
async function login() {
  const res = await fetch(`${BASE}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'testuser2', password: 'Test123456!' }) });
  const d = await res.json();
  if (d.code !== 200) throw new Error('登录失败: ' + d.msg);
  return d.data.accessToken;
}
async function getNovels(t) { return (await (await fetch(`${BASE}/api/novels`, { headers: { Authorization: `Bearer ${t}` } })).json()).data; }
async function getChapters(t, id) { return (await (await fetch(`${BASE}/api/novels/${id}`, { headers: { Authorization: `Bearer ${t}` } })).json()).data.chapters; }
async function getContent(t, id) { return (await (await fetch(`${BASE}/api/chapters/${id}`, { headers: { Authorization: `Bearer ${t}` } })).json()).data.content; }
async function create(t, p) { return (await fetch(`${BASE}/api/annotations`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${t}` }, body: JSON.stringify(p) })).json(); }

(async () => {
  const token = await login();
  console.log('登录成功（第二批）');
  const novels = await getNovels(token);
  let created = 0, failed = 0, sinceClear = 0;

  for (const novel of novels) {
    const entry = NOVEL_DICTS.find(e => novel.title.includes(e.match));
    if (!entry) continue;
    console.log(`\n处理《${novel.title}》`);
    const chapters = await getChapters(token, novel.id);
    const used = new Set();
    for (const chapter of chapters) {
      let content;
      try { content = await getContent(token, chapter.id); } catch (e) { continue; }
      if (!content) continue;
      for (const item of entry.dict) {
        if (used.has(item.phrase)) continue;
        const idx = content.indexOf(item.phrase);
        if (idx === -1) continue;
        if (sinceClear >= 8) { clearRateLimit(); sinceClear = 0; }
        const r = await create(token, { chapterId: chapter.id, anchorStart: idx, anchorEnd: idx + item.phrase.length, selectedText: item.phrase, content: item.content, type: item.type || 0 });
        sinceClear++;
        if (r.code === 200) { created++; used.add(item.phrase); console.log(`  ✓ [${chapter.title}] "${item.phrase}" → id=${r.data.id}`); }
        else if (r.code === 429) { clearRateLimit(); sinceClear = 0; failed++; }
        else { failed++; console.log(`  ✗ [${r.code}] ${r.msg}: "${item.phrase}"`); }
      }
    }
    const missed = entry.dict.filter(d => !used.has(d.phrase));
    if (missed.length) console.log(`  未命中: ${missed.map(m => m.phrase).join('、')}`);
  }
  clearRateLimit();
  console.log(`\n===== 第二批完成: 成功 ${created} 条, 失败 ${failed} 条 =====`);
})();
