package com.miaosha.dao;

import com.miaosha.dataobject.UserPasswordDo;

public interface UserPasswordDoMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_password
     *
     * @mbg.generated Fri Apr 17 10:16:06 CST 2020
     */
    int deleteByPrimaryKey(Integer id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_password
     *
     * @mbg.generated Fri Apr 17 10:16:06 CST 2020
     */
    int insert(UserPasswordDo record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_password
     *
     * @mbg.generated Fri Apr 17 10:16:06 CST 2020
     */
    int insertSelective(UserPasswordDo record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_password
     *
     * @mbg.generated Fri Apr 17 10:16:06 CST 2020
     */
    UserPasswordDo selectByPrimaryKey(Integer id);

    UserPasswordDo selectByUserId(Integer userId);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_password
     *
     * @mbg.generated Fri Apr 17 10:16:06 CST 2020
     */
    int updateByPrimaryKeySelective(UserPasswordDo record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table user_password
     *
     * @mbg.generated Fri Apr 17 10:16:06 CST 2020
     */
    int updateByPrimaryKey(UserPasswordDo record);
}