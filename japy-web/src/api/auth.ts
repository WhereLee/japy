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
