package com.miaosha.dao;

import com.miaosha.dataobject.ItemDo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ItemDoMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table item
     *
     * @mbg.generated Sat Apr 18 15:10:09 CST 2020
     */
    int deleteByPrimaryKey(Integer id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table item
     *
     * @mbg.generated Sat Apr 18 15:10:09 CST 2020
     */
    int insert(ItemDo record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table item
     *
     * @mbg.generated Sat Apr 18 15:10:09 CST 2020
     */
    int insertSelective(ItemDo record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table item
     *
     * @mbg.generated Sat Apr 18 15:10:09 CST 2020
     */
    ItemDo selectByPrimaryKey(Integer id);

    List<ItemDo> listItem();

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table item
     *
     * @mbg.generated Sat Apr 18 15:10:09 CST 2020
     */
    int updateByPrimaryKeySelective(ItemDo record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table item
     *
     * @mbg.generated Sat Apr 18 15:10:09 CST 2020
     */
    int updateByPrimaryKey(ItemDo record);

    int increaseSales(@Param("id") Integer id, @Param("amount") Integer amount);
}