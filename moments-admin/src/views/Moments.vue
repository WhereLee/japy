<template>
  <div class="page-card">
    <div class="toolbar">
      <el-radio-group v-model="status" @change="load(1)">
        <el-radio-button :value="null">全部</el-radio-button>
        <el-radio-button :value="0">正常</el-radio-button>
        <el-radio-button :value="1">已隐藏</el-radio-button>
        <el-radio-button :value="2">已删除</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="nickname" label="昵称" width="120" />
      <el-table-column prop="content" label="内容" min-width="320" show-overflow-tooltip />
      <el-table-column prop="likeCount" label="点赞" width="70" align="center"><template #default="{ row }">{{ fmtNum(row.likeCount) }}</template></el-table-column>
      <el-table-column prop="commentCount" label="评论" width="70" align="center"><template #default="{ row }">{{ fmtNum(row.commentCount) }}</template></el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 0 ? 'success' : row.status === 1 ? 'warning' : 'info'">
            {{ ['正常', '已隐藏', '已删除'][row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="置顶" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.pinned === 1" type="danger" size="small">置顶中</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="发布时间" width="165">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="330" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.pinned !== 1 && row.status === 0" size="small" @click="pin(row, true)">置顶</el-button>
          <el-button v-if="row.pinned === 1" size="small" @click="pin(row, false)">取消置顶</el-button>
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
const status = ref(null)
const loading = ref(false)

async function load(p) {
  page.value = p || 1
  loading.value = true
  try {
    const data = await http.get('/api/admin/moments', {
      params: { page: page.value, size, status: status.value === null ? undefined : status.value }
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function pin(row, on) {
  await http.put(`/api/admin/moments/${row.id}/${on ? 'pin' : 'unpin'}`)
  ElMessage.success(on ? '已置顶' : '已取消置顶')
  load(page.value)
}
async function hide(row) {
  await http.put(`/api/admin/moments/${row.id}/hide`)
  ElMessage.success('已隐藏（通知作者）')
  load(page.value)
}
async function restore(row) {
  await http.put(`/api/admin/moments/${row.id}/restore`)
  ElMessage.success('已恢复')
  load(page.value)
}
async function remove(row) {
  await ElMessageBox.confirm(`确定删除该动态吗？其下评论将一并删除。`, '删除动态', { type: 'warning' })
  await http.delete(`/api/admin/moments/${row.id}`)
  ElMessage.success('已删除')
  load(page.value)
}

onMounted(() => load(1))
</script>
