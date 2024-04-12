package com.sky.service;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrderService {
    /**
     * 提交订单
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    /**
     * 查看历史订单
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    PageResult pageQuery(Integer page, Integer pageSize, Integer status);

    /**
     * 查看具体订单
     * @param id
     * @return
     */
    OrderVO details(Long id);

    /**
     * 取消订单
     * @param id
     * @throws Exception
     */
    void cancelById(Long id) throws Exception;

    /**
     * 再来一单
     * @param id
     */
    void repetition(Long id);

    /**
     * (商家）搜索订单
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 不同状态订单统计
     * @return
     */
    OrderStatisticsVO statistic();

    /**
     * 接单（修改订单状态为已接受）
     * @param ordersConfirmDTO
     */
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 拒单（修改订单状态为已拒绝）
     * @param ordersRejectionDTO
     */
    void rejection(OrdersRejectionDTO ordersRejectionDTO);

    /**
     * 取消订单（修改订单状态为取消）
     * @param ordersCancelDTO
     * @throws Exception
     */
    void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception;

    /**
     * 订单派送（修改订单状态为派送中）
     * @param id
     */
    void delivery(Long id);

    /**
     * 完成订单（修改订单状态为已完成）
     * @param id
     */
    void complete(Long id);

    /**
     * 客户催单
     * @param id
     */
    void remind(Long id);
}
