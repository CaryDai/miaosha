package com.miaosha.controller;

import com.alibaba.druid.util.StringUtils;
import com.google.common.util.concurrent.RateLimiter;
import com.miaosha.error.BusinessException;
import com.miaosha.error.EnumBusinessError;
import com.miaosha.mq.MqProducer;
import com.miaosha.response.CommonReturnType;
import com.miaosha.service.ItemService;
import com.miaosha.service.OrderService;
import com.miaosha.service.PromoService;
import com.miaosha.service.model.OrderModel;
import com.miaosha.service.model.UserModel;
import com.miaosha.util.CodeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @Author dqj
 * @Date 2020/4/18
 * @Version 1.0
 * @Description
 */
@Controller("order")
@RequestMapping("/order")
@CrossOrigin(allowCredentials = "true", allowedHeaders = "*")    // 处理跨域请求
public class OrderController extends BaseController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProducer mqProducer;

    @Autowired
    private ItemService itemService;

    @Autowired
    private PromoService promoService;

    private ExecutorService executorService;

    // 使用令牌桶算法来限流
    private RateLimiter orderCreateRateLimiter;

    @PostConstruct
    public void init() {
        // 队列泄洪
        executorService = Executors.newFixedThreadPool(1000);

        // RateLimiter会按照一定的频率往桶里扔令牌，线程拿到令牌才能执行，比如你希望自己的应用程序QPS不要超过1000，
        // 那么RateLimiter设置1000的速率后，就会每秒往桶里扔1000个令牌。
        orderCreateRateLimiter = RateLimiter.create(100);
    }

    // 生成验证码
    @RequestMapping(value = "/generateverifycode", method = {RequestMethod.GET,RequestMethod.POST})
    @ResponseBody
    public void generateverifycode(HttpServletResponse response) throws BusinessException, IOException {
        // 根据token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(EnumBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能生成验证码");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        // 获取用户的登陆信息
        if (userModel == null) {
            throw new BusinessException(EnumBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能生成验证码");
        }

        Map<String,Object> map = CodeUtil.generateCodeAndPic();

        redisTemplate.opsForValue().set("verify_code_"+userModel.getId(),map.get("code"));
        redisTemplate.expire("verify_code_"+userModel.getId(),10,TimeUnit.MINUTES);

        ImageIO.write((RenderedImage) map.get("codePic"), "jpeg", response.getOutputStream());
    }

    // 生成秒杀令牌
    @RequestMapping(value = "/generatetoken", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generatetoken(@RequestParam(name = "itemId") Integer itemId,
                                          @RequestParam(name = "promoId") Integer promoId,
                                          @RequestParam(name = "verifyCode") String verifyCode) throws BusinessException {
        String promoToken = null;
        // 同步调用线程池的submit方法
        // 拥塞窗口为1000的等待队列，用来队列化泄洪
        Future<String> future = executorService.submit(new Callable<String>() {

            @Override
            public String call() throws Exception {
                // 根据token获取用户信息
                String token = httpServletRequest.getParameterMap().get("token")[0];
                if (StringUtils.isEmpty(token)) {
                    throw new BusinessException(EnumBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能下单");
                }
                UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
                // 获取用户的登陆信息
                if (userModel == null) {
                    throw new BusinessException(EnumBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能下单");
                }

                // 通过verifyCode验证验证码的有效性
                String redisVerifyCode = (String) redisTemplate.opsForValue().get("verify_code_"+userModel.getId());
                if (StringUtils.isEmpty(redisVerifyCode)) {
                    throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"请求非法");
                }
                if (!redisVerifyCode.equalsIgnoreCase(verifyCode)) {
                    throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"请求非法，验证码错误");
                }

                // 获取秒杀访问令牌
                String promoToken = promoService.generateSecondKillToken(promoId, itemId, userModel.getId());
                if (promoToken == null) {
                    throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"生成令牌失败");
                }
                return promoToken;
            }
        });

        try {
            promoToken = future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return CommonReturnType.create(promoToken);
    }

    // 封装下单请求
    @RequestMapping(value = "/createorder", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId") Integer itemId,
                                        @RequestParam(name = "amount") Integer amount,
                                        @RequestParam(name = "promoId", required = false) Integer promoId,
                                        @RequestParam(name = "promoToken", required = false) String promoToken) throws BusinessException {

        // 试着去令牌桶中拿令牌
        if (!orderCreateRateLimiter.tryAcquire()) {
            throw new BusinessException(EnumBusinessError.RATELIMIT);
        }

        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(EnumBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能下单");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        // 获取用户的登陆信息
        if (userModel == null) {
            throw new BusinessException(EnumBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能下单");
        }
        // 校验秒杀令牌是否正确
        if (promoId != null) {
            String inRedisPromoToken = (String) redisTemplate.opsForValue().get("promo_token_"+promoId+"_userId_"+userModel.getId()+"_itemId_"+itemId);
            if (inRedisPromoToken == null) {
                throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"秒杀令牌校验失败");
            }
            if (!org.apache.commons.lang3.StringUtils.equals(promoToken,inRedisPromoToken)) {
                throw new BusinessException(EnumBusinessError.PARAMETER_VALIDATION_ERROR,"秒杀令牌校验失败");
            }
        }

        //UserModel userModel = (UserModel) httpServletRequest.getSession().getAttribute("LOGIN_USER");
//        OrderModel orderModel = orderService.createOrder(userModel.getId(), itemId, promoId, amount);

        // 加入库存流水init状态
        String stockLogId = itemService.initStockLog(itemId,amount);

        // 再去异步发送事务型消息
        if (!mqProducer.transactionAsyncReduceStock(userModel.getId(),promoId,itemId,amount,stockLogId)) {
            throw new BusinessException(EnumBusinessError.UNKNOWN_ERROR,"下单失败");
        }

        return CommonReturnType.create(null);
    }
}
