package com.miaosha.service;

import com.miaosha.error.BusinessException;
import com.miaosha.service.model.ItemModel;

import java.util.List;

/**
 * @Author dqj
 * @Date 2020/4/18
 * @Version 1.0
 * @Description
 */
public interface ItemService {

    // 创建商品
    ItemModel createItem(ItemModel itemModel) throws BusinessException;

    // 商品列表浏览
    List<ItemModel> listItem();

    // 商品详情浏览
    ItemModel getItemById(Integer id);

    // item及promo model缓存模型
    ItemModel getItemByIdInCache(Integer id);

    // 库存扣减
    boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException;

    // 库存回补
    boolean increaseStock(Integer itemId, Integer amount) throws BusinessException;

    // 异步更新库存
    boolean asyncDecreaseStock(Integer itemId, Integer amount);

    // 商品销量增加
    void increaseSales(Integer itemId, Integer amount) throws BusinessException;

    // 初始化库存流水（用于将状态机设置成准备开始冻结的状态，并且提交对应的事务，使得数据库内有对应的stock_log生成）
    // stock_log生成之后，再去createOrder。若MqProducer中的某条消息有对应的stock_log记录时，只需要将该条消息带上stock_log
    // 这样在checkLocalTransaction中就有依靠stock_log记录对应的item_id数据，这样就能追踪到具体订单的状态。
    String initStockLog(Integer itemId, Integer amount);
}
