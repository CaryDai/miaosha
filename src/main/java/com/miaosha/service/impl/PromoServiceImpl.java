package com.miaosha.service.impl;

import com.miaosha.dao.PromoDOMapper;
import com.miaosha.dataobject.PromoDO;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EnumBusinessError;
import com.miaosha.service.ItemService;
import com.miaosha.service.PromoService;
import com.miaosha.service.UserService;
import com.miaosha.service.model.ItemModel;
import com.miaosha.service.model.PromoModel;
import com.miaosha.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Author dqj
 * @Date 2020/4/19
 * @Version 1.0
 * @Description
 */
@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        // 获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);
        PromoModel promoModel = convertFromDOToModel(promoDO);
        if (promoModel == null) {
            return null;
        }

        // 判断当前时间是否秒杀活动即将开始或正在进行
        if (promoModel.getStartTime().isAfterNow()) {   // 开始时间在当前时间之后
            promoModel.setStatus(1);
        } else if (promoModel.getEndTime().isBeforeNow()) { // 结束时间在当前时间之前
            promoModel.setStatus(3);
        } else {
            promoModel.setStatus(2);
        }

        return promoModel;
    }

    @Override
    public void publishPromo(Integer promoId) {
        // 通过活动id获取活动
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        if (promoDO.getItemId() == null || promoDO.getItemId().intValue() == 0) {
            return;
        }
        ItemModel itemModel = itemService.getItemById(promoDO.getItemId());

        // 将库存同步到redis内
        redisTemplate.opsForValue().set("promo_item_stock_"+promoDO.getItemId(), itemModel.getStock());

        // 将大闸的限制数字设到redis内（这里设置成库存量的5倍，即100个库存可以发放500个令牌）
        redisTemplate.opsForValue().set("promo_door_count_"+promoId,itemModel.getStock().intValue() * 5);
    }

    @Override
    public String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId) {

        // 判断是否库存已售罄，若对应的售罄key存在，则没法获得秒杀令牌
        if(redisTemplate.hasKey("promo_item_stock_invalid_"+itemId)) {
            return null;
        }

        // 获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        PromoModel promoModel = convertFromDOToModel(promoDO);
        if (promoModel == null) {
            return null;
        }

        // 判断当前时间是否秒杀活动即将开始或正在进行
        if (promoModel.getStartTime().isAfterNow()) {   // 开始时间在当前时间之后
            promoModel.setStatus(1);
        } else if (promoModel.getEndTime().isBeforeNow()) { // 结束时间在当前时间之前
            promoModel.setStatus(3);
        } else {
            promoModel.setStatus(2);
        }

        // 判断活动是否正在进行
        if (promoModel.getStatus().intValue() != 2) {
            return null;
        }
        // 判断商品是否存在
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null) {
            return null;
        }
        // 判断用户是否存在
        UserModel userModel = userService.getUserByIdInCache(userId);
        if (userModel == null) {
            return null;
        }

        // 获取秒杀大闸的count数量
        long result = redisTemplate.opsForValue().increment("promo_door_count_"+promoId,-1);
        if (result < 0) {
            return null;
        }

        // 如果秒杀活动正在进行中，则生成秒杀令牌
        String token = UUID.randomUUID().toString().replace("-","");
        // 将令牌存入redis，设置5分钟过期时间，即5分钟内该用户不去使用该token，则过期
        redisTemplate.opsForValue().set("promo_token_"+promoId+"_userId_"+userId+"_itemId_"+itemId,token);
        redisTemplate.expire("promo_token_"+promoId+"_userId_"+userId+"_itemId_"+itemId,5, TimeUnit.MINUTES);
        return token;
    }

    private PromoModel convertFromDOToModel(PromoDO promoDO) {
        if (promoDO == null) {
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO, promoModel);
        promoModel.setPromoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        promoModel.setStartTime(new DateTime(promoDO.getStartDate()));
        promoModel.setEndTime(new DateTime(promoDO.getEndDate()));
        return promoModel;
    }
}
