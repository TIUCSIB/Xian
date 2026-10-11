package dev.avsong.domain

/** 需要登录的接口在未登录时的返回(-101)。UI 据此显示「去登录」而不是「加载失败」。 */
class NotLoggedInException(message: String = "未登录") : Exception(message)
