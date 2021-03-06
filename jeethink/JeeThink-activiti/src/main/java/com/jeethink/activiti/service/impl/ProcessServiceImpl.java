package com.jeethink.activiti.service.impl;

import com.jeethink.activiti.domain.ActivitiBaseEntity;
import com.jeethink.activiti.domain.BizTodoItem;
import com.jeethink.activiti.domain.HistoricActivity;
import com.jeethink.activiti.service.IBizTodoItemService;
import com.jeethink.activiti.service.IProcessService;
import com.jeethink.common.core.domain.entity.SysUser;
import com.jeethink.common.utils.DateUtils;
import com.jeethink.common.utils.SecurityUtils;
import com.jeethink.common.utils.StringUtils;
import com.jeethink.system.mapper.SysUserMapper;

import org.activiti.engine.HistoryService;
import org.activiti.engine.IdentityService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricActivityInstanceQuery;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Comment;
import org.activiti.engine.task.Task;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@Transactional
public class ProcessServiceImpl implements IProcessService {
    protected final Logger logger = LoggerFactory.getLogger(ProcessServiceImpl.class);

    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private IdentityService identityService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private SysUserMapper userMapper;
    @Autowired
    private IBizTodoItemService bizTodoItemService;

    @Override
    public ProcessInstance submitApply(String applyUserId, String businessKey, String itemName, String itemConent, String module, Map<String, Object> variables) {
        // ?????????????????????????????????ID???????????????????????????ID?????????activiti:initiator???
        identityService.setAuthenticatedUserId(applyUserId);
        // ??????????????????????????? key
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(module, businessKey, variables);
        // ?????????????????????????????????
        bizTodoItemService.insertTodoItem(instance.getProcessInstanceId(), itemName, itemConent, module);
        return instance;
    }

    /**
     * ????????????
     *
     * @param applyUserId ?????????
     * @param businessKey ????????? id
     * @param itemName
     * @param itemConent
     * @param key         ???????????? key
     * @param variables   ????????????
     * @return
     */
    @Override
    public ProcessInstance submitApply1(String applyUserId, String businessKey, String itemName, String itemConent, String module, Map<String, Object> variables) {
        // ?????????????????????????????????ID???????????????????????????ID?????????activiti:initiator???
        identityService.setAuthenticatedUserId(applyUserId);
        // ??????????????????????????? key
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(module, businessKey, variables);
        // ?????????????????????????????????
        bizTodoItemService.insertTodoItem1(instance.getProcessInstanceId(), itemName, itemConent, module, businessKey);
        return instance;
    }

    @Override
    public List<Task> findTodoTasks(String userId, String key) {
        List<Task> tasks = new ArrayList<Task>();
        // ??????????????????ID??????
        List<Task> todoList = taskService
                .createTaskQuery()
                .processDefinitionKey(key)
                .taskAssignee(userId)
                .list();
        // ?????????????????????????????????
        List<Task> unsignedTasks = taskService
                .createTaskQuery()
                .processDefinitionKey(key)
                .taskCandidateUser(userId)
                .list();
        // ??????
        tasks.addAll(todoList);
        tasks.addAll(unsignedTasks);
        return tasks;
    }

    @Override
    public List<Task> findTodoTasks1(String userId, String key) {
        List<Task> tasks = new ArrayList<Task>();
        List<Task> rtnTasks = new ArrayList<Task>();
        // ??????????????????ID??????
        List<Task> todoList = taskService
                .createTaskQuery()
                .processDefinitionKey(key)
                .taskAssignee(userId)
                .list();
        // ?????????????????????????????????
        List<Task> unsignedTasks = taskService
                .createTaskQuery()
                .processDefinitionKey(key)
                .taskCandidateUser(userId)
                .list();
        // ??????
        tasks.addAll(todoList);
        tasks.addAll(unsignedTasks);
        if (tasks != null && !tasks.isEmpty()) {
            for (Task task : tasks) {
                String processInstanceId = task.getProcessInstanceId();
                List<BizTodoItem> bizTodoItems = bizTodoItemService.selectTodoItemByInstanceId(processInstanceId);
                if (bizTodoItems != null && !bizTodoItems.isEmpty()) {
                    for (BizTodoItem item : bizTodoItems) {
                        if (userId.equals(item.getTodoUserId())) {
                            rtnTasks.add(task);
                        }
                    }
                }
            }
        }


        return rtnTasks;
    }

    @Override
    public List<HistoricTaskInstance> findDoneTasks(String userId, String key) {
        List<HistoricTaskInstance> list = historyService
                .createHistoricTaskInstanceQuery()
                .processDefinitionKey(key)
                .taskAssignee(userId)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .list();
        return list;
    }

    @Override
    public void complete(ActivitiBaseEntity activitiBaseEntity,String module) {
        Map<String, Object> variables =new HashMap<String, Object>();
        String comment = null;          // ??????
        boolean agree = true;
        try {
        for(Map.Entry<String, Object> entry : activitiBaseEntity.getProcessParams().entrySet()){
            String parameterName = entry.getKey();
                    // ???????????????B_name???B????????????name???????????????
                    String[] parameter = parameterName.split("_");
                    if (parameter.length == 2) {
                        String paramValue = (String) entry.getValue();
                        Object value = paramValue;
                        if (parameter[0].equals("B")) {
                            value = BooleanUtils.toBoolean(paramValue);
                            agree = (boolean) value;
                        } else if (parameter[0].equals("DT")) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            value = sdf.parse(paramValue);
                        } else if (parameter[0].equals("COM")) {
                            comment = paramValue;
                        }
                        variables.put(parameter[1], value);
                    }
                }




            if (StringUtils.isNotEmpty(comment)) {
                identityService.setAuthenticatedUserId(SecurityUtils.getUsername());
                comment = agree ? "????????????" + comment : "????????????" + comment;
                taskService.addComment(activitiBaseEntity.getTaskId(), activitiBaseEntity.getInstanceId(), comment);
            }
            // ??????????????????????????????
            // p.s. ??????????????????????????? resolved ????????????????????????
            // ????????? complete ??????????????? resolved
            // resolveTask() ?????? claim() ??????????????? act_hi_taskinst ?????? assignee ???????????? null
            taskService.resolveTask(activitiBaseEntity.getTaskId(), variables);
            // ?????????????????????act_hi_taskinst ?????? assignee ??????????????? null
            taskService.claim(activitiBaseEntity.getTaskId(), SecurityUtils.getUsername());
            taskService.complete(activitiBaseEntity.getTaskId(), variables);

            // ????????????????????????
            BizTodoItem query = new BizTodoItem();
            query.setTaskId(activitiBaseEntity.getTaskId());
            // ??????????????????????????????????????? todoitem ???????????? task
            List<BizTodoItem> updateList = CollectionUtils.isEmpty(bizTodoItemService.selectBizTodoItemList(query)) ? null : bizTodoItemService.selectBizTodoItemList(query);
            for (BizTodoItem update: updateList) {
                // ??????????????????????????? todoitem???????????????
                if (update.getTodoUserId().equals(SecurityUtils.getUsername())) {
                    update.setIsView("1");
                    update.setIsHandle("1");
                    update.setHandleUserId(SecurityUtils.getUsername());
                    update.setHandleUserName(SecurityUtils.getNickName());
                    update.setHandleTime(DateUtils.getNowDate());
                    bizTodoItemService.updateBizTodoItem(update);
                } else {
                    bizTodoItemService.deleteBizTodoItemById(update.getId()); // ??????????????????????????? todoitem
                }
            }

            // ?????????????????????????????????
            bizTodoItemService.insertTodoItem(activitiBaseEntity.getInstanceId(),activitiBaseEntity.getTitle(),activitiBaseEntity.getReason(), module);
        } catch (Exception e) {
            logger.error("error on complete task {}, variables={}", new Object[]{activitiBaseEntity.getTaskId(), variables, e});
        }
    }

    @Override
    public List<HistoricActivity> selectHistoryList(String processInstanceId, HistoricActivity historicActivity) {
//        PageDomain pageDomain = TableSupport.buildPageRequest();
//        Integer pageNum = pageDomain.getPageNum();
//        Integer pageSize = pageDomain.getPageSize();
        List<HistoricActivity> activityList = new ArrayList<>();
        HistoricActivityInstanceQuery query = historyService.createHistoricActivityInstanceQuery();
        if (StringUtils.isNotBlank(historicActivity.getAssignee())) {
            query.taskAssignee(historicActivity.getAssignee());
        }
        if (StringUtils.isNotBlank(historicActivity.getActivityName())) {
            query.activityName(historicActivity.getActivityName());
        }
        List<HistoricActivityInstance> list = query.processInstanceId(processInstanceId)
                .activityType("userTask")
                .finished()
                .orderByHistoricActivityInstanceStartTime()
                .desc()
                .list();
//                .listPage((pageNum - 1) * pageSize, pageNum * pageSize);
        list.forEach(instance -> {
            HistoricActivity activity = new HistoricActivity();
            BeanUtils.copyProperties(instance, activity);
            String taskId = instance.getTaskId();
            List<Comment> comment = taskService.getTaskComments(taskId, "comment");
            if (!CollectionUtils.isEmpty(comment)) {
                activity.setComment(comment.get(0).getFullMessage());
            }
            SysUser sysUser = userMapper.selectUserByUserName(instance.getAssignee());
            if (sysUser != null) {
                activity.setAssigneeName(sysUser.getUserName());
            }
            activityList.add(activity);
        });
        return activityList;
    }

    @Override
    public void delegate(String taskId, String fromUser, String delegateToUser) {
        taskService.delegateTask(taskId, delegateToUser);
        // ??????????????????
//        BizTodoItem updateItem = bizTodoItemService.selectBizTodoItemByCondition(taskId, fromUser);
//        if (updateItem != null) {
//            SysUser todoUser = userMapper.selectUserByLoginName(delegateToUser);
//            updateItem.setTodoUserId(delegateToUser);
//            updateItem.setTodoUserName(todoUser.getUserName());
//            bizTodoItemService.updateBizTodoItem(updateItem);
//        }
    }

    @Override
    public void cancelApply(String instanceId, String deleteReason) {
        // ???????????????????????????????????? act_ru_task ??????????????????????????? act_hi_taskinst ????????????????????????????????????????????????finished??????
        runtimeService.deleteProcessInstance(instanceId, deleteReason);
    }

    @Override
    public void suspendOrActiveApply(String instanceId, String suspendState) {
        if ("1".equals(suspendState)) {
            // ????????????????????????????????????????????????????????????????????????id??????????????????????????????
            // ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            // ??????????????????????????????????????????????????????????????????????????????
            // ???????????????act_ru_task ??? SUSPENSION_STATE_ ??? 2
            runtimeService.suspendProcessInstanceById(instanceId);
        } else if ("2".equals(suspendState)) {
            runtimeService.activateProcessInstanceById(instanceId);
        }
    }

    @Override
    public String findBusinessKeyByInstanceId(String instanceId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (processInstance == null) {
            HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(instanceId)
                    .singleResult();
            return historicProcessInstance.getBusinessKey();
        } else {
            return processInstance.getBusinessKey();
        }
    }

}
