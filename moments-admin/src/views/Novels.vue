<template>
  <div class="page-card">
    <div class="toolbar">
      <div style="display:flex;gap:10px;align-items:center;">
        <el-upload
          :show-file-list="false" :auto-upload="false" accept=".txt"
          :on-change="onFileChange" :limit="1"
        >
          <el-button :icon="FolderOpened">选择 txt 文件</el-button>
        </el-upload>
        <span v-if="file" style="font-size:13px;color:#606266;">
          {{ file.name }}（{{ fmtNum(file.size) }} B）
        </span>
      </div>
      <div style="display:flex;gap:10px;align-items:center;">
        <el-input v-model="author" placeholder="作者（可选）" style="width:150px" />
        <el-button type="primary" :loading="uploading" :disabled="!file" @click="upload">上传并入库</el-button>
      </div>
    </div>

    <el-progress
      v-if="uploading" :percentage="progress" :stroke-width="14" striped striped-flow
      style="margin-bottom:14px;"
    >
      <span style="font-size:12px;color:#606266;">{{ progressText }}</span>
    </el-progress>

    <div class="toolbar">
      <el-input
        v-model="keyword" placeholder="搜索书名 / 作者" clearable style="width:240px"
        @keyup.enter="load(1)" @clear="load(1)"
      >
        <template #append><el-button :icon="Search" @click="load(1)" /></template>
      </el-input>
      <span style="color:#8a919f;font-size:13px;">上传同名小说将覆盖重新入库</span>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="书名" min-width="160" />
      <el-table-column prop="author" label="作者" width="110" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'">
            {{ ['建设中', '已入库', '入库失败'][row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="章节" width="80" align="center"><template #default="{ row }">{{ fmtNum(row.chapterCount) }}</template></el-table-column>
      <el-table-column label="段落" width="80" align="center"><template #default="{ row }">{{ fmtNum(row.paragraphCount) }}</template></el-table-column>
      <el-table-column label="总字数" width="100" align="center"><template #default="{ row }">{{ fmtNum(row.totalChars) }}</template></el-table-column>
      <el-table-column label="入库时间" width="165">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">详情</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="load"
      />
    </div>

    <!-- 章节详情 -->
    <el-dialog v-model="detailVisible" :title="detail?.title || '章节详情'" width="820px">
      <template v-if="detail">
        <el-descriptions :column="4" border size="small" style="margin-bottom:14px;">
          <el-descriptions-item label="章节数">{{ fmtNum(detail.chapterCount) }}</el-descriptions-item>
          <el-descriptions-item label="段落数">{{ fmtNum(detail.paragraphCount) }}</el-descriptions-item>
          <el-descriptions-item label="总字数">{{ fmtNum(detail.totalChars) }}</el-descriptions-item>
          <el-descriptions-item label="作者">{{ detail.author || '佚名' }}</el-descriptions-item>
          <el-descriptions-item label="源文件名">{{ detail.sourceName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="编码">{{ detail.sourceEncoding || '-' }}</el-descriptions-item>
          <el-descriptions-item label="大小">{{ fmtNum(detail.sourceSize) }} B</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="detail.status === 1 ? 'success' : 'warning'">
              {{ ['建设中', '已入库', '入库失败'][detail.status] }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-descriptions :column="1" border size="small" style="margin-bottom:14px;">
          <el-descriptions-item label="落盘目录">{{ detail.dirPath || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail.chapters" size="small" max-height="380" stripe>
          <el-table-column prop="chapterNo" label="章" width="60" align="center" />
          <el-table-column prop="title" label="章节标题" min-width="200" />
          <el-table-column label="字数" width="90" align="center"><template #default="{ row }">{{ fmtNum(row.chars) }}</template></el-table-column>
          <el-table-column label="段落" width="80" align="center"><template #default="{ row }">{{ fmtNum(row.paragraphCount) }}</template></el-table-column>
          <el-table-column label="最长段落" width="90" align="center"><template #default="{ row }">{{ fmtNum(row.maxParaChars) }}</template></el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderOpened, Search } from '@element-plus/icons-vue'
import http from '../api'
import { fmtNum, fmtTime } from '../utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)
const keyword = ref('')

const file = ref(null)
const author = ref('')
const uploading = ref(false)
const progress = ref(0)
const progressText = ref('')

const detailVisible = ref(false)
const detail = ref(null)

function onFileChange(f) {
  if (f.raw && !f.raw.name.toLowerCase().endsWith('.txt')) {
    ElMessage.warning('仅支持 txt 文件')
    file.value = null
    return
  }
  file.value = f.raw
}

async function upload() {
  if (!file.value) return
  uploading.value = true
  progress.value = 0
  progressText.value = '上传中…'
  try {
    const form = new FormData()
    form.append('file', file.value)
    if (author.value.trim()) form.append('author', author.value.trim())
    const data = await http.post('/api/admin/novels/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: e => {
        progress.value = e.total ? Math.round((e.loaded / e.total) * 100) : 0
      }
    })
    progress.value = 100
    progressText.value = '入库完成'
    ElMessage.success(`入库成功：${data.chapterCount} 章 / ${fmtNum(data.paragraphCount)} 段，耗时 ${data.costMs}ms`)
    file.value = null
    author.value = ''
    load(1)
    // 自动打开详情弹窗展示完整结果
    openDetail({ id: data.id, title: data.title })
  } finally {
    uploading.value = false
  }
}

async function load(p) {
  page.value = p || 1
  loading.value = true
  try {
    const data = await http.get('/api/admin/novels', {
      params: { page: page.value, size, keyword: keyword.value || undefined }
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  detail.value = await http.get(`/api/admin/novels/${row.id}`)
  detailVisible.value = true
}

async function remove(row) {
  await ElMessageBox.confirm(
    `确定删除《${row.title}》吗？\n将同时删除数据库数据（${fmtNum(row.paragraphCount)} 段）与落盘目录，不可恢复。`,
    '删除小说', { type: 'warning', confirmButtonText: '删除' }
  )
  await http.delete(`/api/admin/novels/${row.id}`)
  ElMessage.success('已删除')
  load(page.value)
}

onMounted(() => load(1))
</script>
