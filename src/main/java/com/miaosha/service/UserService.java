package com.miaosha.service;

import com.miaosha.error.BusinessException;
import com.miaosha.service.model.UserModel;

/**
 * @Author dqj
 * @Date 2020/4/17
 * @Version 1.0
 * @Description
 */
public interface UserService {
    // 通过用户ID获取用户对象的方法
    UserModel getUserById(int id);

    // 通过缓存获取用户对象
    UserModel getUserByIdInCache(Integer id);

    // 用户注册
    void register(UserModel userModel) throws BusinessException;

    // 用户登陆服务，用来校验用户登陆是否合法
    UserModel validateLogin(String telphone, String encrptPassword) throws BusinessException;
}
