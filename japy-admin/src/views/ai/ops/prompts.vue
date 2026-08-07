<template>
  <div>
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="card-head">
          <span class="title">LLM 提示词管理</span>
          <span class="sub">集中管理每个 LLM 场景的固定设定提示词（不含检索临时塞入的文档）。保存即升版本并立即生效，可回滚历史版本。</span>
        </div>
      </template>

      <el-table :data="prompts" v-loading="loading" style="width: 100%">
        <el-table-column prop="code" label="场景标识" width="190">
          <template #default="{ row }">
            <code class="code-badge">{{ row.code }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="场景名称" width="150" />
        <el-table-column label="当前版本" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="success" effect="light">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="systemPrompt" label="提示词预览" min-width="320" show-overflow-tooltip />
        <el-table-column label="最近更新" width="170">
          <template #default="{ row }">{{ row.updatedAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain v-perm="'ai:prompt:edit'" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button size="small" v-perm="'ai:prompt:list'" @click="openVersions(row)">
              版本
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑对话框：保存即升版本 -->
    <el-dialog v-model="editVisible" :title="`编辑提示词 · ${editRow?.code}（当前 v${editRow?.version} → 保存后 v${editRow ? editRow.version + 1 : ''}）`"
      width="720px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 12px"
        title="保存将生成新版本并立即生效（无需重启服务）；旧版本保留可回滚。" />
      <el-input v-model="editText" type="textarea" :rows="16" placeholder="在此编辑 system prompt…" />
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存（升版本）</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史对话框：回滚 -->
    <el-dialog v-model="verVisible" :title="`版本历史 · ${verRow?.code}`" width="680px">
      <el-table :data="versions" v-loading="verLoading" style="width: 100%">
        <el-table-column label="版本" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="light">
              v{{ row.version }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="systemPrompt" label="提示词" min-width="300" show-overflow-tooltip />
        <el-table-column label="更新人/时间" width="150">
          <template #default="{ row }">{{ row.updatedAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 1" size="small" type="warning" plain
              v-perm="'ai:prompt:rollback'" :loading="rollbacking === row.version" @click="onRollback(row)">
              回滚
            </el-button>
            <el-tag v-else size="small" type="success">当前生效</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPrompts, listPromptVersions, updatePrompt, rollbackPrompt, type AiPrompt } from '@/api/ai'

const prompts = ref<AiPrompt[]>([])
const loading = ref(false)

const editVisible = ref(false)
const editRow = ref<AiPrompt | null>(null)
const editText = ref('')
const saving = ref(false)

const verVisible = ref(false)
const verRow = ref<AiPrompt | null>(null)
const versions = ref<AiPrompt[]>([])
const verLoading = ref(false)
const rollbacking = ref(0)

async function load() {
  loading.value = true
  try {
    prompts.value = await listPrompts()
  } finally {
    loading.value = false
  }
}

function openEdit(row: AiPrompt) {
  editRow.value = row
  editText.value = row.systemPrompt
  editVisible.value = true
}

async function onSave() {
  if (!editRow.value) return
  if (!editText.value.trim()) {
    ElMessage.warning('提示词不能为空')
    return
  }
  saving.value = true
  try {
    const saved = await updatePrompt(editRow.value.code, editText.value)
    ElMessage.success(`已保存为 v${saved.version}，立即生效`)
    editVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function openVersions(row: AiPrompt) {
  verRow.value = row
  verVisible.value = true
  verLoading.value = true
  try {
    versions.value = await listPromptVersions(row.code)
  } finally {
    verLoading.value = false
  }
}

async function onRollback(row: AiPrompt) {
  if (!verRow.value) return
  try {
    await ElMessageBox.confirm(
      `确认回滚「${verRow.value.code}」到 v${row.version}？将立即生效，当前版本转为历史。`,
      '回滚确认',
      { type: 'warning', confirmButtonText: '回滚', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  rollbacking.value = row.version
  try {
    const target = await rollbackPrompt(verRow.value.code, row.version)
    ElMessage.success(`已回滚到 v${target.version}，立即生效`)
    await openVersions(verRow.value)
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '回滚失败')
  } finally {
    rollbacking.value = 0
  }
}

onMounted(load)
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: baseline;
  gap: 14px;
}
.card-head .sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.code-badge {
  background: var(--el-fill-color-light);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--el-color-primary);
}
</style>
