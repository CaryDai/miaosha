package com.miaosha.error;

/**
 * @Author dqj
 * @Date 2020/4/17
 * @Version 1.0
 * @Description
 */
//包装器业务异常类实现
public class BusinessException extends Exception implements CommonError {

    private CommonError commonError;

    // 直接接收EnumBusinessError的传参用于构造业务异常
    public BusinessException(CommonError commonError) {
        super();    // Exception自身会有一些初始化机制，所以需要调用super()方法
        this.commonError = commonError;
    }

    // 接收自定义errMsg的方式构造业务异常
    public BusinessException(CommonError commonError, String errMsg) {
        super();
        this.commonError = commonError;
        this.commonError.setErrMsg(errMsg);
    }

    @Override
    public int getErrCode() {
        return this.commonError.getErrCode();
    }

    @Override
    public String getErrMsg() {
        return this.commonError.getErrMsg();
    }

    @Override
    public CommonError setErrMsg(String errMsg) {
        this.commonError.setErrMsg(errMsg);
        return this;
    }
}
