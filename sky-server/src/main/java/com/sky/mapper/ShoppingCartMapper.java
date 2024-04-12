package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {
    /**
     * 动态调节查询
     * @param shoppingCart
     * @return
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 根据id更新购物车单品数量
     * @param shoppingCart
     */
    @Update("update shopping_cart set number = #{number} where id = #{id}")
    void updateNumberById(ShoppingCart shoppingCart);

    /**
     * 为购物车插入新商品
     * @param shoppingCart
     */
    @Insert("insert into shopping_cart(name, image, user_id, dish_id, setmeal_id, dish_flavor, number, amount, create_time) " +
            "VALUES (#{name}, #{image}, #{userId}, #{dishId}, #{setmealId}, #{dishFlavor}, #{number}, #{amount}, #{createTime})")
    void insert(ShoppingCart shoppingCart);

    /**
     * 根据用户ID删购物车数据
     * @param userId
     */
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void deleteByUserId(Long userId);

    /**
     * 根据菜品ID和用户ID删除购物车菜品数据
     * @param shoppingCart
     */
    @Delete("delete from shopping_cart where dish_id = #{dishId} and user_id = #{userId}")
    void deleteByUserIdAndDishId(ShoppingCart shoppingCart);

    /**
     * 根据套餐ID和用户ID删除购物车菜品数据
     * @param shoppingCart
     */
    @Delete("delete from shopping_cart where setmeal_id = #{setmealId} and user_id = #{userId}")
    void deleteByUserIdAndSetmealId(ShoppingCart shoppingCart);

    /**
     * 批量为购物车插入新菜品
     * @param shoppingCartList
     */
    void insertBatch(List<ShoppingCart> shoppingCartList);
}
