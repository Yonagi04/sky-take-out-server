package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import springfox.documentation.spring.web.json.Json;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WebSocketServer webSocketServer;

    private Orders orders = new Orders();

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        ShoppingCart shoppingCart = new ShoppingCart();
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() +
                addressBook.getDetail());

        orderMapper.insert(orders);

        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        shoppingCartMapper.deleteByUserId(userId);

        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        JSONObject jsonObject = new JSONObject();

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        // WebSocket向客户端推送消息
        Map map = new HashMap();
        map.put("type", "1");
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号：" + outTradeNo);

        String jsonString = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(jsonString);
    }

    /**
     * 查看历史订单
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult pageQuery(Integer pageNum, Integer pageSize, Integer status) {
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();
        if (page != null && page.getTotal() > 0) {
            for (Orders orders1 : page) {
                Long orders1Id = orders1.getId();

                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orders1Id);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders1, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }

    /**
     * 查看具体订单
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        Orders orders1 = orderMapper.getById(id);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders1.getId());

        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders1, orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 取消订单
     * @param id
     * @throws Exception
     */
    @Override
    public void cancelById(Long id) throws Exception {
        Orders ordersDB = orderMapper.getById(id);

        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        if (ordersDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders1 = new Orders();
        orders1.setId(ordersDB.getId());

        if (ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            orders1.setPayStatus(Orders.REFUND);
        }
        orders1.setStatus(Orders.CANCELLED);
        orders1.setCancelReason("用户取消");
        orders1.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders1);
    }

    /**
     * 再来一单
     * @param id
     */
    @Override
    public void repetition(Long id) {
        Long userId = BaseContext.getCurrentId();

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * (商家）搜索订单
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> query = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = getOrderVOList(query);
        return new PageResult(query.getTotal(), list);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> query) {
        List<OrderVO> list = new ArrayList<>();

        List<Orders> ordersList = query.getResult();

        if (!CollectionUtils.isEmpty(ordersList)) {
            for (Orders orders1 : ordersList) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders1, orderVO);
                String orderDishes = getOrderDishesStr(orders1);
                orderVO.setOrderDishes(orderDishes);
                list.add(orderVO);
            }
        }
        return list;
    }

    private String getOrderDishesStr(Orders orders) {
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber();
            return orderDish;
        }).collect(Collectors.toList());

        return String.join("", orderDishList);
    }

    /**
     * 不同状态订单统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistic() {
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer Confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(Confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);

        return orderStatisticsVO;
    }

    /**
     * 接单（修改订单状态为已接受）
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orderDB = orderMapper.getById(ordersConfirmDTO.getId());
        if (orderDB == null || !orderDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders1 = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders1);
    }

    /**
     * 拒单（修改订单状态为已拒绝）
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        Orders ordersDB = orderMapper.getById(ordersRejectionDTO.getId());

        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus == Orders.PAID) {
            log.info("需要退款{}元", ordersDB.getAmount());
        }

        Orders orders1 = new Orders();
        orders1.setCancelTime(LocalDateTime.now());
        orders1.setId(ordersDB.getId());
        orders1.setStatus(Orders.CANCELLED);
        orders1.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orderMapper.update(orders1);
    }

    /**
     * 取消订单（修改订单状态为取消）
     * @param ordersCancelDTO
     * @throws Exception
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        Orders orderDB = orderMapper.getById(ordersCancelDTO.getId());

        Integer payStatus = orderDB.getPayStatus();
        if (payStatus == Orders.PAID) {
            log.info("需要退款{}元", orderDB.getAmount());
        }

        Orders orders1 = new Orders();
        orders1.setId(ordersCancelDTO.getId());
        orders1.setStatus(Orders.CANCELLED);
        orders1.setCancelTime(LocalDateTime.now());
        orders1.setCancelReason(ordersCancelDTO.getCancelReason());

        orderMapper.update(orders1);
    }

    /**
     * 订单派送（修改订单状态为派送中）
     * @param id
     */
    @Override
    public void delivery(Long id) {
        Orders orderDB = orderMapper.getById(id);
        if (orderDB == null || !orderDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders1 = Orders.builder()
                .status(Orders.DELIVERY_IN_PROGRESS)
                .id(orderDB.getId())
                .build();
        orderMapper.update(orders1);
    }

    /**
     * 完成订单（修改订单状态为已完成）
     * @param id
     */
    @Override
    public void complete(Long id) {
       Orders orderDB = orderMapper.getById(id);
       if (orderDB == null || !orderDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
           throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
       }

       Orders orders1 = Orders.builder()
               .status(Orders.COMPLETED)
               .id(orderDB.getId())
               .deliveryTime(LocalDateTime.now())
               .build();
       orderMapper.update(orders1);
    }

    /**
     * 客户催单
     * @param id
     */
    @Override
    public void remind(Long id) {
        Orders orderDB = orderMapper.getById(id);
        if (orderDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Map map = new HashMap();
        map.put("type", "2");
        map.put("content", "订单号：" +orderDB.getNumber());
        String jsonString = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(jsonString);
    }
}
