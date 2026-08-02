<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input
        v-model="keyword" placeholder="搜索用户名 / 昵称" clearable style="width:260px"
        @keyup.enter="load(1)" @clear="load(1)"
      >
        <template #append><el-button :icon="Search" @click="load(1)" /></template>
      </el-input>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="130" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column label="角色" width="90">
        <template #default="{ row }">
          <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small">
            {{ row.role === 'admin' ? '管理员' : '用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'danger' : 'success'" size="small">
            {{ row.status === 1 ? '已封禁' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="注册时间" width="175">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="330" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0 && row.id !== auth.userId" type="danger" size="small" @click="openBan(row)">封禁</el-button>
          <el-button v-if="row.id !== auth.userId && row.status === 1" type="success" size="small" @click="ban(row, false)">解封</el-button>
          <el-button v-if="row.id !== auth.userId" size="small" @click="resetPwd(row)">重置密码</el-button>
          <el-button v-if="row.id !== auth.userId" size="small" @click="openNickname(row)">改昵称</el-button>
          <span v-if="row.id === auth.userId" style="color:#c0c4cc;font-size:13px;">当前账号</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="load"
      />
    </div>

    <!-- 封禁弹窗 -->
    <el-dialog v-model="banVisible" title="封禁用户" width="420px">
      <el-form label-width="70px">
        <el-form-item label="用户"><b>{{ banRow?.username }}</b></el-form-item>
        <el-form-item label="封禁原因">
          <el-input v-model="banReason" type="textarea" :rows="3" placeholder="将通知给用户" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banVisible = false">取消</el-button>
        <el-button type="danger" @click="ban(banRow, true)">确认封禁</el-button>
      </template>
    </el-dialog>

    <!-- 改昵称弹窗 -->
    <el-dialog v-model="nickVisible" title="强制改名" width="420px">
      <el-form label-width="70px">
        <el-form-item label="当前昵称"><b>{{ nickRow?.nickname }}</b></el-form-item>
        <el-form-item label="新昵称">
          <el-input v-model="newNickname" maxlength="50" placeholder="输入新昵称（立即生效）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nickVisible = false">取消</el-button>
        <el-button type="primary" @click="forceNickname">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../api'
import { useAuthStore } from '../stores/auth'
import { fmtTime } from '../utils/format'

const auth = useAuthStore()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const keyword = ref('')
const loading = ref(false)

const banVisible = ref(false)
const banRow = ref(null)
const banReason = ref('')
const nickVisible = ref(false)
const nickRow = ref(null)
const newNickname = ref('')

async function load(p) {
  page.value = p || 1
  loading.value = true
  try {
    const data = await http.get('/api/admin/users', {
      params: { page: page.value, size, keyword: keyword.value || undefined }
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function openBan(row) { banRow.value = row; banReason.value = ''; banVisible.value = true }
async function ban(row, confirm) {
  if (confirm) {
    await http.put(`/api/admin/users/${row.id}/ban`, { reason: banReason.value })
    ElMessage.success('已封禁')
    banVisible.value = false
  } else {
    await http.put(`/api/admin/users/${row.id}/unban`)
    ElMessage.success('已解封')
  }
  load(page.value)
}

async function resetPwd(row) {
  await ElMessageBox.confirm(`将用户「${row.username}」的密码重置为 123456？`, '重置密码', { type: 'warning' })
  await http.put(`/api/admin/users/${row.id}/reset-password`)
  ElMessage.success('已重置为 123456')
}

function openNickname(row) { nickRow.value = row; newNickname.value = ''; nickVisible.value = true }
async function forceNickname() {
  if (!newNickname.value.trim()) { ElMessage.warning('请输入新昵称'); return }
  await http.put(`/api/admin/users/${nickRow.value.id}/nickname`, { nickname: newNickname.value.trim() })
  ElMessage.success('昵称已修改（立即生效）')
  nickVisible.value = false
  load(page.value)
}

onMounted(() => load(1))
</script>
