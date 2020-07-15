package com.miaosha.error;

/**
 * @Author dqj
 * @Date 2020/4/17
 * @Description
 */
public interface CommonError {
    public int getErrCode();
    public String getErrMsg();
    // 这个方法是为了在处理通用错误类型时，根据具体场景的不同，设置不同的errMsg错误信息
    public CommonError setErrMsg(String errMsg);
}
