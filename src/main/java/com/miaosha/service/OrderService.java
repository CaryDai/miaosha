package com.miaosha.service;

import com.miaosha.error.BusinessException;
import com.miaosha.service.model.OrderModel;

/**
 * @Author dqj
 * @Date 2020/4/18
 * @Version 1.0
 * @Description
 */
public interface OrderService {

    // 使用1.通过前端url传过来秒杀商品id，然后下单接口内校验对应id是否属于对应商品活动，且活动已开始
    // 2.直接在下单接口内判断对应商品是否存在秒杀活动，若存在进行中的则以秒杀价格下单
    OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) throws BusinessException;
}
