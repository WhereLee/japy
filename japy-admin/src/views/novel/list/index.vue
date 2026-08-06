<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="head">
        <span class="title">小说列表</span>
        <div class="right">
          <el-input v-model="keyword" placeholder="搜索书名/作者" clearable style="width: 200px" @keyup.enter="search" @clear="load" />
          <el-button type="primary" v-perm="'novel:upload'" @click="dialog = true">
            <el-icon style="margin-right: 4px"><Upload /></el-icon>上传小说
          </el-button>
        </div>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="书名" min-width="160">
        <template #default="{ row }">
          <div class="book-cell">
            <div class="mini-cover" :style="{ background: '#6366f1' }">{{ row.title.slice(0, 2) }}</div>
            <div>
              <div class="b-title">{{ row.title }}</div>
              <div class="b-author">{{ row.author }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="90" />
      <el-table-column prop="chapterCount" label="章节" width="70" />
      <el-table-column label="字数" width="100">
        <template #default="{ row }">{{ (row.totalChars / 1000).toFixed(1) }}k</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status !== 0">
            <el-button link type="success" size="small" v-perm="'novel:status'" @click="onStatus(row, 0)">上架</el-button>
          </template>
          <template v-else>
            <el-button link type="warning" size="small" v-perm="'novel:status'" @click="onStatus(row, 1)">下架</el-button>
          </template>
          <el-button link type="danger" size="small" v-perm="'novel:delete'" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>

    <!-- 上传对话框 -->
    <el-dialog v-model="dialog" title="上传小说（txt）" width="460px" :close-on-click-modal="false">
      <el-form :model="form" label-width="80px">
        <el-form-item label="txt 文件" required>
          <input ref="fileRef" type="file" accept=".txt" class="file-input" @change="onFile" />
        </el-form-item>
        <el-form-item label="书名" required>
          <el-input v-model="form.title" placeholder="如：星海征途" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="form.author" placeholder="佚名" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" placeholder="玄幻/武侠/科幻/都市…" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.intro" type="textarea" :rows="3" placeholder="一句话简介" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="onUpload">上传并入库</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { adminListNovels, uploadNovel, changeNovelStatus, deleteNovel } from '@/api/novel'

const rows = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
const keyword = ref('')
const loading = ref(false)

const dialog = ref(false)
const uploading = ref(false)
const fileRef = ref<HTMLInputElement>()
const form = reactive({ title: '', author: '', category: '', intro: '' })

const statusMap: Record<number, [string, string]> = {
  0: ['上架', 'success'],
  1: ['下架', 'warning'],
  2: ['草稿', 'info']
}
function statusText(s: number) { return statusMap[s]?.[0] ?? '未知' }
function statusType(s: number) { return statusMap[s]?.[1] ?? 'info' as any }

async function load() {
  loading.value = true
  try {
    const p = await adminListNovels({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    rows.value = p.list
    total.value = p.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.length && !input.files[0].name.endsWith('.txt')) {
    ElMessage.warning('仅支持 txt 文件')
    input.value = ''
  }
}

async function onUpload() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写书名')
    return
  }
  const file = fileRef.value?.files?.[0]
  if (!file) {
    ElMessage.warning('请选择 txt 文件')
    return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('title', form.title.trim())
    fd.append('author', form.author.trim())
    fd.append('category', form.category.trim())
    fd.append('intro', form.intro.trim())
    const novel = await uploadNovel(fd)
    ElMessage.success(`《${novel.title}》上传成功：${novel.chapterCount} 章，已自动上架`)
    dialog.value = false
    Object.assign(form, { title: '', author: '', category: '', intro: '' })
    if (fileRef.value) fileRef.value.value = ''
    load()
  } finally {
    uploading.value = false
  }
}

async function onStatus(row: any, status: number) {
  const action = { 0: '上架', 1: '下架' }[status]
  await ElMessageBox.confirm(`确定${action}《${row.title}》？`, '提示', { type: 'info' })
  await changeNovelStatus(row.id, status)
  ElMessage.success('已' + action)
  load()
}

async function onDelete(row: any) {
  await ElMessageBox.confirm(`删除《${row.title}》？用户端将不可见（可恢复需管理员处理）`, '警告', { type: 'warning' })
  await deleteNovel(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-card { border-radius: 12px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 600; }
.right { display: flex; align-items: center; gap: 10px; }
.book-cell { display: flex; align-items: center; gap: 10px; }
.mini-cover {
  width: 34px; height: 44px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 13px; font-weight: 700;
}
.b-title { font-size: 14px; font-weight: 600; color: #0f172a; }
.b-author { font-size: 12px; color: #64748b; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
.file-input { font-size: 13px; }
</style>
