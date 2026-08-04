import request from './request'
import type { PageResult } from './ai'

export interface ListPage<T> {
  list: T[]
  total: number
  page: number
  size: number
}

// ---------- 用户 ----------
export interface SysUser {
  id: number
  username: string
  nickname: string
  avatar: string | null
  email: string | null
  phone: string | null
  sex: number
  status: number
  createTime: string
}
export function listUsers(params: { page: number; size: number; keyword?: string }) {
  return request.get<ListPage<SysUser>, ListPage<SysUser>>('/system/user/list', { params })
}
export function addUser(data: any) {
  return request.post('/system/user', data)
}
export function updateUser(data: any) {
  return request.put('/system/user', data)
}
export function deleteUser(id: number) {
  return request.delete(`/system/user/${id}`)
}
export function resetPwd(id: number) {
  return request.put(`/system/user/${id}/resetPwd`)
}
export function setUserStatus(id: number, status: number) {
  return request.put(`/system/user/${id}/status`, { status })
}

// ---------- 角色 ----------
export interface SysRole {
  id: number
  roleName: string
  roleKey: string
  sort: number
}
export function listRoles() {
  return request.get<SysRole[], SysRole[]>('/system/role/list')
}
export function addRole(data: any) {
  return request.post('/system/role', data)
}
export function updateRole(data: any) {
  return request.put('/system/role', data)
}
export function deleteRole(id: number) {
  return request.delete(`/system/role/${id}`)
}
export function getRolePerms(id: number) {
  return request.get<number[], number[]>(`/system/role/${id}/perms`)
}
export function setRolePerms(id: number, permIds: number[]) {
  return request.put(`/system/role/${id}/perms`, { permIds })
}

// ---------- 权限 ----------
export interface SysPerm {
  id: number
  parentId: number
  permName: string
  permKey: string | null
  permType: number
  path: string | null
  component: string | null
  icon: string | null
  sort: number
}
export function permTree() {
  return request.get<SysPerm[], SysPerm[]>('/system/perm/tree')
}

// ---------- 字典 ----------
export function listDictTypes() {
  return request.get<any[], any[]>('/system/dict/type/list')
}
export function listDictData(dictType: string) {
  return request.get<any[], any[]>(`/system/dict/data/${dictType}`)
}

// ---------- 参数 ----------
export function listConfigs(params: { page: number; size: number }) {
  return request.get<ListPage<any>, ListPage<any>>('/system/config/list', { params })
}

// ---------- 公告 ----------
export interface SysNotice {
  id: number
  noticeTitle: string
  noticeType: number
  noticeContent: string
  status: number
  createTime: string
}
export interface NoticePage {
  list: SysNotice[]
  total: number
  page: number
  size: number
}
export function listNotices(params: { page: number; size: number }) {
  return request.get<NoticePage, NoticePage>('/system/notice/list', { params })
}
export function deleteNotice(id: number) {
  return request.delete(`/system/notice/${id}`)
}

// ---------- 日志 / 在线 ----------
export interface OperLog {
  id: number
  title: string
  method: string
  requestMethod: string
  operName: string
  operUrl: string
  operIp: string
  operParam: string
  status: number
  errorMsg: string | null
  costTime: number
  createTime: string
}
export function listOperLogs(params: { page: number; size: number }) {
  return request.get<ListPage<OperLog>, ListPage<OperLog>>('/system/operlog/list', { params })
}
export function cleanOperLogs() {
  return request.delete('/system/operlog/clean')
}

export interface LoginLog {
  id: number
  username: string
  ipaddr: string
  status: number
  msg: string
  createTime: string
}
export function listLoginLogs(params: { page: number; size: number }) {
  return request.get<ListPage<LoginLog>, ListPage<LoginLog>>('/system/loginlog/list', { params })
}

export interface OnlineUser {
  userId: number
  username: string
  nickname: string
  loginTime: string
}
export function listOnline() {
  return request.get<OnlineUser[], OnlineUser[]>('/system/online/list')
}
export function forceLogout(userId: number) {
  return request.delete(`/system/online/${userId}`)
}

// 仪表盘
export interface DashboardData {
  userTotal?: number
  operLogToday?: number
  loginLogToday?: number
  [k: string]: any
}
export function getDashboard() {
  return request.get<DashboardData, DashboardData>('/system/dashboard')
}
