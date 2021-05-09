package com.jeethink.leave.mapper;

import java.util.List;

import com.jeethink.activiti.domain.BizTodoItem;
import com.jeethink.leave.domain.BizLeave;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 请假流程Mapper接口
 * 
 * @author jeethink
 * @date 2020-09-17
 */
public interface BizLeaveMapper 
{
    /**
     * 查询请假流程
     * 
     * @param id 请假流程ID
     * @return 请假流程
     */
    public BizLeave selectBizLeaveById(Long id);

    /**
     * 查询请假流程列表
     * 
     * @param bizLeave 请假流程
     * @return 请假流程集合
     */
    public List<BizLeave> selectBizLeaveList(BizLeave bizLeave);

    /**
     * 新增请假流程
     * 
     * @param bizLeave 请假流程
     * @return 结果
     */
    public int insertBizLeave(BizLeave bizLeave);

    /**
     * 修改请假流程
     * 
     * @param bizLeave 请假流程
     * @return 结果
     */
    public int updateBizLeave(BizLeave bizLeave);

    /**
     * 删除请假流程
     * 
     * @param id 请假流程ID
     * @return 结果
     */
    public int deleteBizLeaveById(Long id);

    /**
     * 批量删除请假流程
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteBizLeaveByIds(Long[] ids);

    @Select("SELECT count(1) FROM BIZ_LEAVE WHERE #{time} BETWEEN LEAVE_START_TIME AND LEAVE_END_TIME")
    Integer countProcessing(@Param(value = "time") String time);

    @Select("SELECT INSTANCE_ID FROM BIZ_LEAVE")
    List<String> getAllList();

}
