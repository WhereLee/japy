<template>
  <div class="page-card">
    <div class="toolbar">
      <span style="color:#8a919f;font-size:13px;">隐藏/恢复会同步更新动态的评论计数</span>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="nickname" label="昵称" width="110" />
      <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip />
      <el-table-column prop="momentId" label="所属动态" width="90" align="center"><template #default="{ row }">{{ fmtNum(row.momentId) }}</template></el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.parentId" size="small" type="info">回复</el-tag>
          <el-tag v-else size="small">顶层</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 0 ? 'success' : row.status === 1 ? 'warning' : 'info'">
            {{ ['正常', '已隐藏', '已删除'][row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="评论时间" width="165">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" type="warning" size="small" @click="hide(row)">隐藏</el-button>
          <el-button v-if="row.status === 1" type="success" size="small" @click="restore(row)">恢复</el-button>
          <el-button v-if="row.status !== 2" type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'
import { fmtNum, fmtTime } from '../utils/format'

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const loading = ref(false)

async function load(p) {
  page.value = p || 1
  loading.value = true
  try {
    const data = await http.get('/api/admin/comments', { params: { page: page.value, size } })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function hide(row) {
  await http.put(`/api/admin/comments/${row.id}/hide`)
  ElMessage.success('已隐藏')
  load(page.value)
}
async function restore(row) {
  await http.put(`/api/admin/comments/${row.id}/restore`)
  ElMessage.success('已恢复')
  load(page.value)
}
async function remove(row) {
  await ElMessageBox.confirm('确定删除该评论吗？', '删除评论', { type: 'warning' })
  await http.delete(`/api/admin/comments/${row.id}`)
  ElMessage.success('已删除')
  load(page.value)
}

onMounted(() => load(1))
</script>
