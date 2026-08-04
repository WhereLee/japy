<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="head">
        <span class="title">用户管理</span>
        <el-button type="primary" size="small" v-perm="'system:user:add'" @click="openAdd">新增用户</el-button>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="用户" min-width="160">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="26" :src="row.avatar || undefined">{{ row.nickname?.charAt(0) }}</el-avatar>
            <span>{{ row.nickname }}</span>
            <span class="uname">@{{ row.username }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch :model-value="row.status === 0" size="small" v-perm="'system:user:status'"
            @change="(v: boolean) => toggleStatus(row, v)" />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" v-perm="'system:user:edit'" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" size="small" v-perm="'system:user:resetPwd'" @click="onReset(row)">重置密码</el-button>
          <el-button link type="danger" size="small" v-perm="'system:user:delete'" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="(p: number) => { page = p; load() }" />
    </div>

    <el-dialog v-model="dialog" :title="editing ? '编辑用户' : '新增用户'" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名" v-if="!editing">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码" v-if="!editing">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, addUser, updateUser, deleteUser, resetPwd, setUserStatus } from '@/api/system'

const rows = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
const loading = ref(false)
const dialog = ref(false)
const editing = ref(false)
const form = reactive<any>({})

async function load() {
  loading.value = true
  try {
    const p = await listUsers({ page: page.value, size: size.value })
    rows.value = p.list
    total.value = p.total
  } finally {
    loading.value = false
  }
}

function openAdd() {
  editing.value = false
  Object.assign(form, { username: '', password: '', nickname: '', email: '', phone: '' })
  dialog.value = true
}
function openEdit(row: any) {
  editing.value = true
  Object.assign(form, { id: row.id, nickname: row.nickname, email: row.email, phone: row.phone })
  dialog.value = true
}
async function onSave() {
  if (editing.value) await updateUser(form)
  else await addUser(form)
  ElMessage.success('已保存')
  dialog.value = false
  load()
}
async function onDelete(row: any) {
  await ElMessageBox.confirm(`删除用户 ${row.username}？`, '警告', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  load()
}
async function onReset(row: any) {
  await ElMessageBox.confirm(`重置 ${row.username} 密码为 123456？`, '提示', { type: 'info' })
  await resetPwd(row.id)
  ElMessage.success('已重置')
}
async function toggleStatus(row: any, v: boolean) {
  await setUserStatus(row.id, v ? 0 : 1)
  ElMessage.success('已更新')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-card { border-radius: 12px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 600; }
.pager { margin-top: 14px; display: flex; justify-content: flex-end; }
.user-cell { display: flex; align-items: center; gap: 8px; }
.uname { font-size: 12px; color: #9ca3af; }
</style>
