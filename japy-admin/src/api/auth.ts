import request from './request'

export interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userId: number
  nickname: string
  avatar: string | null
  roles: string[]
}

export function login(data: { username: string; password: string }) {
  return request.post<LoginResult, LoginResult>('/auth/login', data)
}

export function logout() {
  return request.post('/auth/logout')
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar: string | null
  email: string | null
  phone: string | null
  sex: number
  createTime: string
}

export interface InfoResult {
  user: UserInfo
  roles: string[]
  permissions: string[]
}

export function getInfo() {
  return request.get<InfoResult, InfoResult>('/auth/info')
}

export interface RouterItem {
  name: string
  path: string
  component: string
  redirect?: string
  meta: { title: string; icon?: string; hidden?: boolean }
  children?: RouterItem[]
}

export function getRouters() {
  return request.get<RouterItem[], RouterItem[]>('/auth/routers')
}
