package com.jeethink.leave.controller;

import java.util.HashMap;
import java.util.List;

import com.alibaba.fastjson.JSONObject;
import com.jeethink.activiti.domain.BizTodoItem;
import com.jeethink.activiti.service.IBizTodoItemService;
import com.jeethink.common.utils.DateUtils;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import com.jeethink.common.annotation.Log;
import com.jeethink.common.core.controller.BaseController;
import com.jeethink.common.core.domain.AjaxResult;
import com.jeethink.common.core.domain.entity.SysUser;
import com.jeethink.common.core.page.TableDataInfo;
import com.jeethink.common.enums.BusinessType;
import com.jeethink.common.utils.SecurityUtils;
import com.jeethink.common.utils.poi.ExcelUtil;
import com.jeethink.leave.domain.BizLeave;
import com.jeethink.leave.service.IBizLeaveService;

/**
 * 请假流程Controller
 *
 * @author jeethink
 * @date 2020-09-17
 */
@RestController
@RequestMapping("/workflow/leave")
public class BizLeaveController extends BaseController {
    @Autowired
    private IBizLeaveService bizLeaveService;

    @Autowired
    private IBizTodoItemService bizTodoItemService;

    /**
     * 查询请假流程列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:leave')")
    @GetMapping("/list")
    public TableDataInfo list(BizLeave bizLeave) {
        if (!SysUser.isAdmin(SecurityUtils.getUserId())) {
            bizLeave.setCreateBy(SecurityUtils.getUsername());
        }
        bizLeave.setType("leave");
        startPage();
        List<BizLeave> list = bizLeaveService.selectBizLeaveList(bizLeave);
        return getDataTable(list);
    }



    /**
     * 导出请假流程列表
     */
    @PreAuthorize("@ss.hasPermi('workflow:leave')")
    @Log(title = "请假流程", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    public AjaxResult export(BizLeave bizLeave) {
        bizLeave.setType("leave");
        List<BizLeave> list = bizLeaveService.selectBizLeaveList(bizLeave);
        ExcelUtil<BizLeave> util = new ExcelUtil<BizLeave>(BizLeave.class);
        return util.exportExcel(list, "leave");
    }

    /**
     * 获取请假流程详细信息
     */
    @PreAuthorize("@ss.hasPermi('workflow:leave')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return AjaxResult.success(bizLeaveService.selectBizLeaveById(id));
    }

    /**
     * 新增请假流程
     */
    @PreAuthorize("@ss.hasPermi('workflow:leave')")
    @Log(title = "请假流程", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BizLeave bizLeave) {
        Long userId = SecurityUtils.getUserId();
        if (SysUser.isAdmin(userId)) {
            return AjaxResult.error("提交申请失败：不允许管理员提交申请！");
        }
        bizLeave.setType("leave");
        return toAjax(bizLeaveService.insertBizLeave(bizLeave));
    }

    /**
     * 修改请假流程
     */
    @PreAuthorize("@ss.hasPermi('workflow:leave')")
    @Log(title = "请假流程", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BizLeave bizLeave) {
        return toAjax(bizLeaveService.updateBizLeave(bizLeave));
    }

    /**
     * 删除请假流程
     */
    @PreAuthorize("@ss.hasPermi('workflow:leave')")
    @Log(title = "请假流程", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(bizLeaveService.deleteBizLeaveByIds(ids));
    }

    /**
     * 提交申请
     */
    @Log(title = "请假业务", businessType = BusinessType.UPDATE)
    @PostMapping( "/submitApply/{id}")
    @ResponseBody
    public AjaxResult submitApply(@PathVariable Long id) {
        BizLeave leave = bizLeaveService.selectBizLeaveById(id);
        String applyUserId = SecurityUtils.getUsername();
        bizLeaveService.submitApply(leave, applyUserId, "leave", new HashMap<>());
        return AjaxResult.success();
    }

    /**
     * 我的待办列表
     * @return
     */
    @GetMapping("/taskList")
    @ResponseBody
    public TableDataInfo taskList(BizLeave bizLeave) {
        bizLeave.setType("leave");
        List<BizLeave> list = bizLeaveService.findTodoTasks(bizLeave, SecurityUtils.getUsername());
        return getDataTable(list);
    }

    /**
     * 我的已办列表
     * @param bizLeave
     * @return
     */
    @GetMapping("/taskDoneList")
    @ResponseBody
    public TableDataInfo taskDoneList(BizLeave bizLeave) {
        bizLeave.setType("leave");
        List<BizLeave> list = bizLeaveService.findDoneTasks(bizLeave, SecurityUtils.getUsername());
        return getDataTable(list);
    }

    /**
     * 首页信息
     * @param
     * @return
     */
    @GetMapping("/datainfo")
    @ResponseBody
    public JSONObject getDataInfo() {
        JSONObject rtnObj = new JSONObject();
        Integer number1 = 0;
        Integer number2 = 0;
        Integer number3 = 0;
        Integer number4 = 0;
        List<String> list = bizLeaveService.getAllList();

        number1 = list.size();
        number4 = bizLeaveService.countProcessing(DateUtils.getTime());

        if (list != null && !list.isEmpty()) {
            for (int index = 0; index < list.size(); index++) {
                String s = list.get(index);
                List<BizTodoItem> bizTodoItems = bizTodoItemService.selectTodoItemByInstanceId(list.get(index));
                if (bizTodoItems != null && !bizTodoItems.isEmpty()) {
                    Boolean ingflag = false;
                    Boolean edflag = false;
                    for (BizTodoItem item : bizTodoItems) {
                        if ((item.getNodeName().contains("主管") && "0".equals(item.getIsHandle()))
                                || item.getNodeName().contains("总经理") && "0".equals(item.getIsHandle())
                                || item.getNodeName().contains("总经理") && "1".equals(item.getIsHandle()) && item
                                .getNodeName().contains("调整")) {
                            ingflag = true;
                        }
                        if (item.getNodeName().contains("启动")) {
                            edflag = true;
                        }
                    }
                    if (ingflag) {
                        number2++;
                    }
                    if (edflag) {
                        number3++;
                    }
                }
            }
        }

        rtnObj.put("number1", number1);
        rtnObj.put("number2", number2);
        rtnObj.put("number3", number3);
        rtnObj.put("number4", number4);


        return rtnObj;
    }



}
