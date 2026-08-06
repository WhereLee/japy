"""项目配置"""
import logging
import os
import sys
from pathlib import Path

# === 环境变量加载（12-factor）：读取仓库根 .env（gitignore，绝不入库）===
# rag/src/config.py 的上级两级是 rag/，再上级是仓库根
REPO_ROOT = Path(__file__).resolve().parent.parent.parent
_ENV_FILE = REPO_ROOT / ".env"
if _ENV_FILE.exists():
    for line in _ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, _, v = line.partition("=")
            os.environ.setdefault(k.strip(), v.strip())

# === 路径配置 ===
# config.py 在 src/ 下，项目根目录是 src/ 的上一级
PROJECT_ROOT = Path(__file__).resolve().parent.parent
NOVELS_RAW_DIR = PROJECT_ROOT / "novels"
INDEX_DIR = PROJECT_ROOT / "index"
MODEL_PATH = PROJECT_ROOT / "models" / "bge-base-zh-v1.5"
RERANKER_PATH = PROJECT_ROOT / "models" / "bge-reranker-v2-m3"
LOG_DIR = PROJECT_ROOT / "logs"

# === LLM 配置（密钥来自 .env，未配置则为空 → 调用方自行降级）===
# rag 优先用独立 RAG_API_KEY（未泄露原 key），无则回退 DEEPSEEK_API_KEY
LLM_API_KEY = os.getenv("RAG_API_KEY", "") or os.getenv("DEEPSEEK_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.deepseek.com")
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-v4-flash")
LLM_TIMEOUT = 120            # API 超时（秒）

# === 分块配置 ===
CHUNK_TARGET_SIZE = 500      # 目标块大小（字）
CHUNK_MAX_SIZE = 800         # 单块最大字数（超过则强制断句）
CHUNK_OVERLAP = 100          # 相邻块重叠字数

# === 检索配置 ===
TOP_K = 12                   # 最终返回的chunk数量（给 LLM 更多片段）
VECTOR_TOP_K = 24            # 向量检索候选数
BM25_TOP_K = 24              # BM25检索候选数
RRF_K = 60                    # RRF 融合常数

# === 日志配置 ===
def setup_logging():
    """初始化日志系统：控制台 INFO + 文件 DEBUG"""
    LOG_DIR.mkdir(exist_ok=True)
    log_file = LOG_DIR / "agent.log"

    logger = logging.getLogger("novel_agent")
    logger.setLevel(logging.DEBUG)

    # 文件 handler（详细日志）
    fh = logging.FileHandler(log_file, encoding="utf-8")
    fh.setLevel(logging.DEBUG)
    fh.setFormatter(logging.Formatter(
        "%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    ))

    # 控制台 handler（简洁输出）
    ch = logging.StreamHandler(sys.stderr)
    ch.setLevel(logging.WARNING)
    ch.setFormatter(logging.Formatter("[%(levelname)s] %(message)s"))

    logger.addHandler(fh)
    logger.addHandler(ch)
    return logger

logger = setup_logging()
