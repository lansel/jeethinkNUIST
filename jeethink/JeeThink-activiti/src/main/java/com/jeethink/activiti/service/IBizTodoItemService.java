package com.jeethink.activiti.service;

import java.util.List;

import com.jeethink.activiti.domain.BizTodoItem;
import sun.rmi.log.LogInputStream;

/**
 * 待办事项Service接口
 *
 * @author Xianlu Tech
 * @date 2019-11-08
 */
public interface IBizTodoItemService {
    /**
     * 查询待办事项
     *
     * @param id 待办事项ID
     * @return 待办事项
     */
    public BizTodoItem selectBizTodoItemById(Long id);

    /**
     * 查询待办事项列表
     *
     * @param bizTodoItem 待办事项
     * @return 待办事项集合
     */
    public List<BizTodoItem> selectBizTodoItemList(BizTodoItem bizTodoItem);

    /**
     * 新增待办事项
     *
     * @param bizTodoItem 待办事项
     * @return 结果
     */
    public int insertBizTodoItem(BizTodoItem bizTodoItem);

    /**
     * 修改待办事项
     *
     * @param bizTodoItem 待办事项
     * @return 结果
     */
    public int updateBizTodoItem(BizTodoItem bizTodoItem);

    /**
     * 批量删除待办事项
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteBizTodoItemByIds(String ids);

    /**
     * 删除待办事项信息
     *
     * @param id 待办事项ID
     * @return 结果
     */
    public int deleteBizTodoItemById(Long id);

    int insertTodoItem(String instanceId, String itemName, String itemContent, String module);

    int insertTodoItem1(String instanceId, String itemName, String itemContent, String module, String bussinessKey);

    BizTodoItem selectBizTodoItemByCondition(String taskId, String todoUserId);

    List<BizTodoItem> selectTodoItemByInstanceId(String instanceId);
}
