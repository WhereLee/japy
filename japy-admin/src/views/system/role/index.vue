<template>
  <el-card shadow="never" class="page-card">
    <template #header>
      <div class="head">
        <span class="title">角色管理</span>
        <el-button type="primary" size="small" v-perm="'system:role:add'" @click="openAdd">新增角色</el-button>
      </div>
    </template>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="roleName" label="角色名称" width="140" />
      <el-table-column prop="roleKey" label="角色标识" width="140" />
      <el-table-column prop="remark" label="备注" min-width="200" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" v-perm="'system:role:assignPerm'" @click="openPerm(row)">分配权限</el-button>
          <el-button link type="primary" size="small" v-perm="'system:role:edit'" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" v-perm="'system:role:delete'" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="permDialog" :title="`分配权限 - ${current?.roleName}`" width="420px">
      <el-tree ref="treeRef" :data="permTreeData" show-checkbox node-key="id"
        :props="{ label: 'permName', children: 'children' }" :default-checked-keys="checkedPerms" />
      <template #footer>
        <el-button @click="permDialog = false">取消</el-button>
        <el-button type="primary" @click="onSavePerm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialog" :title="editing ? '编辑角色' : '新增角色'" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="角色名称"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="角色标识"><el-input v-model="form.roleKey" placeholder="如 tech_admin" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
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
import { listRoles, addRole, updateRole, deleteRole, permTree, getRolePerms, setRolePerms } from '@/api/system'

const rows = ref<any[]>([])
const loading = ref(false)
const permDialog = ref(false)
const current = ref<any>(null)
const permTreeData = ref<any[]>([])
const checkedPerms = ref<number[]>([])
const treeRef = ref<any>()
const dialog = ref(false)
const editing = ref(false)
const form = reactive<any>({})

async function load() {
  loading.value = true
  try {
    rows.value = await listRoles()
  } finally {
    loading.value = false
  }
}

function openAdd() {
  editing.value = false
  Object.assign(form, { roleName: '', roleKey: '', sort: 0, remark: '' })
  dialog.value = true
}
function openEdit(row: any) {
  editing.value = true
  Object.assign(form, { id: row.id, roleName: row.roleName, roleKey: row.roleKey, sort: row.sort, remark: row.remark })
  dialog.value = true
}
async function onSave() {
  if (editing.value) await updateRole(form)
  else await addRole(form)
  ElMessage.success('已保存')
  dialog.value = false
  load()
}

async function openPerm(row: any) {
  current.value = row
  const tree = await permTree()
  // 后端返回扁平列表 → 转树
  permTreeData.value = buildTree(tree)
  const ids = await getRolePerms(row.id)
  checkedPerms.value = ids
  permDialog.value = true
}

function buildTree(list: any[]): any[] {
  const map = new Map(list.map((n) => [n.id, { ...n, children: [] }]))
  const roots: any[] = []
  map.forEach((n) => {
    if (n.parentId === 0) roots.push(n)
    else map.get(n.parentId)?.children.push(n)
  })
  return roots
}

async function onSavePerm() {
  const checked = treeRef.value?.getCheckedKeys() as number[]
  const half = treeRef.value?.getHalfCheckedKeys() as number[]
  await setRolePerms(current.value.id, [...checked, ...half])
  ElMessage.success('已保存')
  permDialog.value = false
}

async function onDelete(row: any) {
  await ElMessageBox.confirm(`删除角色 ${row.roleName}？`, '警告', { type: 'warning' })
  await deleteRole(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.page-card { border-radius: 12px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 600; }
</style>
