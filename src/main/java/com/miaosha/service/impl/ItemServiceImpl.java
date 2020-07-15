package com.miaosha.service.impl;

import com.miaosha.dao.ItemDoMapper;
import com.miaosha.dao.ItemStockDoMapper;
import com.miaosha.dao.StockLogDOMapper;
import com.miaosha.dataobject.ItemDo;
import com.miaosha.dataobject.ItemStockDo;
import com.miaosha.dataobject.StockLogDO;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EnumBusinessError;
import com.miaosha.mq.MqProducer;
import com.miaosha.service.ItemService;
import com.miaosha.service.PromoService;
import com.miaosha.service.model.ItemModel;
import com.miaosha.service.model.PromoModel;
import com.miaosha.validator.ValidationResult;
import com.miaosha.validator.ValidatorImpl;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author dqj
 * @Date 2020/4/18
 * @Version 1.0
 * @Description
 */
@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private ItemDoMapper itemDoMapper;

    @Autowired
    private ItemStockDoMapper itemStockDoMapper;

    @Autowired
    private PromoService promoService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProducer mqProducer;

    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @Autowired
    private RedissonClient redissonClient;

    private ItemDo convertItemDoFromItemModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemDo itemDo = new ItemDo();
        BeanUtils.copyProperties(itemModel,itemDo);
        // copyProperties不会拷贝类型不一样的对象，price在ItemModel中为BigDecimal，在数据库中为Double
        itemDo.setPrice(itemModel.getPrice().doubleValue());
        return itemDo;
    }

    private ItemStockDo convertItemStockDoFromItemModel(ItemModel itemModel) {
        if (itemModel == null) {
            return null;
        }
        ItemStockDo itemStockDo = new ItemStockDo();
        itemStockDo.setItemId(itemModel.getId());
        itemStockDo.setStock(itemModel.getStock());
        return itemStockDo;
    }

    @Override
    public ItemModel getItemByIdInCache(Integer id) {
        ItemModel itemModel = (ItemModel) redisTemplate.opsForValue().get("item_validate_"+id);
        if (itemModel == null) {
            itemModel = this.getItemById(id);
            redisTemplate.opsForValue().set("item_validate_"+id, itemModel);
            redisTemplate.expire("item_validate_"+id, 10, TimeUnit.MINUTES);
        }
        return itemModel;
    }

    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        // 校验入参
        ValidationResult result = validator.validate(itemModel);
        if (result.isHasErrors()) {
            throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR, result.getErrMsg());
        }

        // 转化itemmodel->dataobject
        ItemDo itemDo = convertItemDoFromItemModel(itemModel);

        // 写入数据库
        itemDoMapper.insertSelective(itemDo);
        itemModel.setId(itemDo.getId());

        ItemStockDo itemStockDo = convertItemStockDoFromItemModel(itemModel);
        itemStockDoMapper.insertSelective(itemStockDo);

        // 返回ItemModel对象
        return this.getItemById(itemModel.getId());
    }

    @Override
    public List<ItemModel> listItem() {
        List<ItemDo> itemDoList = itemDoMapper.listItem();
        // 使用Stream的map()将每个itemDo都映射到对应的itemModel
        List<ItemModel> itemModelList = itemDoList.stream().map(itemDo -> {
            ItemStockDo itemStockDo = itemStockDoMapper.selectByItemId(itemDo.getId());
            ItemModel itemModel = this.convertModelFromDataObject(itemDo, itemStockDo);
            return itemModel;
        }).collect(Collectors.toList());
        return itemModelList;
    }

    @Override
    public ItemModel getItemById(Integer id) {
        // 先根据商品id查询对应的信息
        ItemDo itemDo = itemDoMapper.selectByPrimaryKey(id);
        if (itemDo == null) {
            return null;
        }
        // 再根据item_id查询库存数量
        ItemStockDo itemStockDo = itemStockDoMapper.selectByItemId(itemDo.getId());

        // 将dataobject->model
        ItemModel itemModel = convertModelFromDataObject(itemDo, itemStockDo);

        // 获取秒杀商品信息
        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());
        if (promoModel != null && promoModel.getStatus().intValue() != 3) {
            itemModel.setPromoModel(promoModel);
        }

        return itemModel;
    }

    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException {
        // 更新成功返回1，更新失败返回0
//        int affectedRow = itemStockDoMapper.decreaseStock(itemId,amount);
//        if (affectedRow > 0) {
//            return true;
//        } else {
//            return false;
//        }

        // 改为从redis中减库存
        // 对该商品减库存时需要先获得它对应的分布式锁
        final String key = itemId + "-redisLock";
        RLock lock = redissonClient.getLock(key);
        try {
            // 最大的等待获取锁的时间为30s. 上锁之后，10s内操作完毕将自动释放锁
            boolean getLock = lock.tryLock(30,10, TimeUnit.SECONDS);
            if (!getLock) {
                throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"请稍等...");
            }
            long result = redisTemplate.opsForValue().increment("promo_item_stock_"+itemId, amount.intValue()*-1);
            if (result > 0) {
                return true;
            } else if (result == 0) {
                // 打上库存已售罄标识
                redisTemplate.opsForValue().set("promo_item_stock_invalid_"+itemId,true);
                return true;
            } else {
                // 更新库存失败
                increaseStock(itemId,amount);
                return false;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        // try语句块执行完后，会进入finally解锁然后返回，所以这一句不会被执行，只是为了有返回值才写
        return true;
    }

    @Override
    public boolean increaseStock(Integer itemId, Integer amount) throws BusinessException {
        redisTemplate.opsForValue().increment("promo_item_stock_"+itemId, amount.intValue());
        return true;
    }

    // 初始化对应的库存流水
    @Override
    @Transactional
    public String initStockLog(Integer itemId, Integer amount) {
        StockLogDO stockLogDO = new StockLogDO();
        stockLogDO.setItemId(itemId);
        stockLogDO.setAmount(amount);
        stockLogDO.setStockLogId(UUID.randomUUID().toString().replace("-",""));
        // 1表示初识状态，2表示下单扣减库存成功，3表示下单回滚
        stockLogDO.setStatus(1);

        stockLogDOMapper.insertSelective(stockLogDO);
        return stockLogDO.getStockLogId();
    }

    @Override
    public boolean asyncDecreaseStock(Integer itemId, Integer amount) {
        boolean mqResult = mqProducer.asyncReduceStock(itemId,amount);
        return mqResult;
    }

    @Override
    @Transactional
    public void increaseSales(Integer itemId, Integer amount) throws BusinessException {
        itemDoMapper.increaseSales(itemId,amount);
    }

    private ItemModel convertModelFromDataObject(ItemDo itemDo, ItemStockDo itemStockDo) {
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDo,itemModel);
        itemModel.setPrice(new BigDecimal(itemDo.getPrice()));
        itemModel.setStock(itemStockDo.getStock());

        return itemModel;
    }
}
