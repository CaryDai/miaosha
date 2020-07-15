package com.miaosha.service.impl;

import com.miaosha.dao.UserDoMapper;
import com.miaosha.dao.UserPasswordDoMapper;
import com.miaosha.dataobject.UserDo;
import com.miaosha.dataobject.UserPasswordDo;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EnumBusinessError;
import com.miaosha.service.UserService;
import com.miaosha.service.model.UserModel;
import com.miaosha.validator.ValidationResult;
import com.miaosha.validator.ValidatorImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * @Author dqj
 * @Date 2020/4/17
 * @Version 1.0
 * @Description
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDoMapper userDoMapper;

    @Autowired
    private UserPasswordDoMapper userPasswordDoMapper;

    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public UserModel getUserById(int id) {
        // 调用userDoMapper获取对应的用户dataobject
        UserDo userDo = userDoMapper.selectByPrimaryKey(id);

        if (userDo == null) {
            return null;
        }
        // 通过用户id获取对应的用户加密密码信息
        UserPasswordDo userPasswordDo = userPasswordDoMapper.selectByUserId(userDo.getId());

        return convertFromDataObject(userDo, userPasswordDo);
    }

    @Override
    public UserModel getUserByIdInCache(Integer id) {
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get("user_validate_"+id);
        if (userModel == null) {
            userModel = this.getUserById(id);
            redisTemplate.opsForValue().set("user_validate_"+id, userModel);
            redisTemplate.expire("user_validate_"+id, 10, TimeUnit.MINUTES);
        }
        return userModel;
    }

    @Override
    @Transactional  // 保证注册在一个事务里完成
    public void  register(UserModel userModel) throws BusinessException {
        if (userModel == null) {
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR);
        }
//        if (StringUtils.isEmpty(userModel.getName())
//                || userModel.getGender() == null
//                || userModel.getAge() == null
//                || StringUtils.isEmpty(userModel.getTelphone())) {
//            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR);
//        }
        ValidationResult result = validator.validate(userModel);
        if (result.isHasErrors()) {
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR, result.getErrMsg());
        }

        // 实现model->dataobject方法
        UserDo userDo = convertFromModel(userModel);
        try {
            userDoMapper.insertSelective(userDo);   // insertSelective会判断插入的字段是否为空，为null的话会使用数据库默认值
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR, "手机号已注册");
        }

        // userDo的id会自增，所以将它先复制给userModel，之后再传给userPasswordDo，这样password表的user_id就和info表一致
        userModel.setId(userDo.getId());

        UserPasswordDo userPasswordDo = convertPasswordFromModel(userModel);
        userPasswordDoMapper.insertSelective(userPasswordDo);
    }

    @Override
    public UserModel validateLogin(String telphone, String encrptPassword) throws BusinessException {
        // 通过用户的手机号获取用户登陆信息
        UserDo userDo = userDoMapper.selectByTelphone(telphone);
        if (userDo == null) {
            throw new BusinessException(EnumBusinessError.USER_LOGIN_FAIL);
        }
        UserPasswordDo userPasswordDo = userPasswordDoMapper.selectByUserId(userDo.getId());
        UserModel userModel = convertFromDataObject(userDo, userPasswordDo);

        // 比对用户信息内加密的密码是否和传输进来的密码相匹配
        if (!com.alibaba.druid.util.StringUtils.equals(encrptPassword, userModel.getEncrptPassword())) {
            throw new BusinessException(EnumBusinessError.USER_NOT_EXIST);
        }
        return userModel;
    }

    private UserPasswordDo convertPasswordFromModel(UserModel userModel) {
        if (userModel == null) {
            return null;
        }
        UserPasswordDo userPasswordDo = new UserPasswordDo();
        userPasswordDo.setEncrptPassword(userModel.getEncrptPassword());
        userPasswordDo.setUserId(userModel.getId());
        return userPasswordDo;
    }

    private UserDo convertFromModel(UserModel userModel) {
        if (userModel == null) {
            return null;
        }
        UserDo userDo = new UserDo();
        BeanUtils.copyProperties(userModel, userDo);
        return userDo;
    }

    private UserModel convertFromDataObject(UserDo userDo, UserPasswordDo userPasswordDo) {
        if (userDo == null) {
            return null;
        }
        UserModel userModel = new UserModel();
        // 把对应userDo的属性copy到userModel(要求对应的字段名和类型一致)
        BeanUtils.copyProperties(userDo, userModel);

        if (userPasswordDo != null) {
            // 这里不能copy，因为id字段会重复
            userModel.setEncrptPassword(userPasswordDo.getEncrptPassword());
        }

        return userModel;
    }
}
