package com.jeethink.activiti.controller;

import com.jeethink.activiti.config.ICustomProcessDiagramGenerator;
import com.jeethink.activiti.config.WorkflowConstants;
import com.jeethink.activiti.domain.ActivitiBaseEntity;
import com.jeethink.activiti.domain.HistoricActivity;
import com.jeethink.activiti.service.IProcessService;
import com.jeethink.common.core.controller.BaseController;
import com.jeethink.common.core.domain.AjaxResult;
import com.jeethink.common.core.page.TableDataInfo;
import com.jeethink.common.utils.SecurityUtils;
import com.jeethink.common.utils.StringUtils;

import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.FlowNode;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntityImpl;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/process")
public class ProcessController extends BaseController {



    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private IProcessService processService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;


    /**
     * ??????????????????
     * @param instanceId
     * @return
     */
//    @RequiresPermissions("process:leave:list")
    @GetMapping("/listHistory/{instanceId}")
    @ResponseBody
    public TableDataInfo listHistory(@PathVariable String instanceId, HistoricActivity historicActivity) {
        startPage();
        List<HistoricActivity> list = processService.selectHistoryList(instanceId, historicActivity);
        return getDataTable(list);
    }

    @RequestMapping(value = "/read-resource")
    public void readResource(String pProcessInstanceId, HttpServletResponse response)
            throws Exception {

        String processDefinitionId = "";
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(pProcessInstanceId).singleResult();
        if(processInstance == null) {
            HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().processInstanceId(pProcessInstanceId).singleResult();
            processDefinitionId = historicProcessInstance.getProcessDefinitionId();
        } else {
            processDefinitionId = processInstance.getProcessDefinitionId();
        }
        ProcessDefinitionQuery pdq = repositoryService.createProcessDefinitionQuery();
        ProcessDefinition pd = pdq.processDefinitionId(processDefinitionId).singleResult();

        String resourceName = pd.getDiagramResourceName();

        if(resourceName.endsWith(".png") && StringUtils.isEmpty(pProcessInstanceId) == false)
        {
            getActivitiProccessImage(pProcessInstanceId,response);
            //ProcessDiagramGenerator.generateDiagram(pde, "png", getRuntimeService().getActiveActivityIds(processInstanceId));
        }
        else
        {
            // ??????????????????
            InputStream resourceAsStream = repositoryService.getResourceAsStream(pd.getDeploymentId(), resourceName);

            // ?????????????????????????????????
            byte[] b = new byte[1024];
            int len = -1;
            while ((len = resourceAsStream.read(b, 0, 1024)) != -1) {
                response.getOutputStream().write(b, 0, len);
            }
        }
    }

    /**
     * ????????????????????????????????????????????????????????????
     */
    public void getActivitiProccessImage(String pProcessInstanceId, HttpServletResponse response) {
        //logger.info("[??????]-?????????????????????");
        try {
            //  ????????????????????????
            HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(pProcessInstanceId).singleResult();

            if (historicProcessInstance == null) {
                //throw new BusinessException("??????????????????ID[" + pProcessInstanceId + "]????????????????????????????????????");
            }
            else {
                // ??????????????????
                ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService)
                        .getDeployedProcessDefinition(historicProcessInstance.getProcessDefinitionId());

                // ??????????????????????????????????????????????????????????????????????????????????????????
                List<HistoricActivityInstance> historicActivityInstanceList = historyService.createHistoricActivityInstanceQuery()
                        .processInstanceId(pProcessInstanceId).orderByHistoricActivityInstanceId().asc().list();

                // ??????????????????ID??????
                List<String> executedActivityIdList = new ArrayList<String>();
                int index = 1;
                //logger.info("???????????????????????????ID");
                for (HistoricActivityInstance activityInstance : historicActivityInstanceList) {
                    executedActivityIdList.add(activityInstance.getActivityId());

                    //logger.info("???[" + index + "]??????????????????=" + activityInstance.getActivityId() + " : " +activityInstance.getActivityName());
                    index++;
                }

                BpmnModel bpmnModel = repositoryService.getBpmnModel(historicProcessInstance.getProcessDefinitionId());

                // ?????????????????????
                List<String> flowIds = new ArrayList<String>();
                // ???????????????????????? (getHighLightedFlows??????????????????)
                flowIds = getHighLightedFlows(bpmnModel,processDefinition, historicActivityInstanceList);

//                // ??????????????????????????????
//                ProcessDiagramGenerator pec = processEngine.getProcessEngineConfiguration().getProcessDiagramGenerator();
//                //????????????
//                InputStream imageStream = pec.generateDiagram(bpmnModel, "png", executedActivityIdList, flowIds,"??????","????????????","??????",null,2.0);

                Set<String> currIds = runtimeService.createExecutionQuery().processInstanceId(pProcessInstanceId).list()
                        .stream().map(e->e.getActivityId()).collect(Collectors.toSet());

                ICustomProcessDiagramGenerator diagramGenerator = (ICustomProcessDiagramGenerator) processEngine.getProcessEngineConfiguration().getProcessDiagramGenerator();
                InputStream imageStream = diagramGenerator.generateDiagram(bpmnModel, "png", executedActivityIdList,
                        flowIds, "??????", "??????", "??????", null, 1.0, new Color[] { WorkflowConstants.COLOR_NORMAL, WorkflowConstants.COLOR_CURRENT }, currIds);

                response.setContentType("image/png");
                OutputStream os = response.getOutputStream();
                int bytesRead = 0;
                byte[] buffer = new byte[8192];
                while ((bytesRead = imageStream.read(buffer, 0, 8192)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.close();
                imageStream.close();
            }
            //logger.info("[??????]-?????????????????????");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            //logger.error("????????????-????????????????????????" + e.getMessage());
            //throw new BusinessException("????????????????????????" + e.getMessage());
        }
    }

    private List<String> getHighLightedFlows(BpmnModel bpmnModel,ProcessDefinitionEntity processDefinitionEntity,List<HistoricActivityInstance> historicActivityInstances) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //24?????????
        List<String> highFlows = new ArrayList<String>();// ????????????????????????flowId

        for (int i = 0; i < historicActivityInstances.size() - 1; i++) {
            // ?????????????????????????????????
            // ?????????????????????????????????
            FlowNode activityImpl = (FlowNode)bpmnModel.getMainProcess().getFlowElement(historicActivityInstances.get(i).getActivityId());


            List<FlowNode> sameStartTimeNodes = new ArrayList<FlowNode>();// ?????????????????????????????????????????????
            FlowNode sameActivityImpl1 = null;

            HistoricActivityInstance activityImpl_ = historicActivityInstances.get(i);// ???????????????
            HistoricActivityInstance activityImp2_ ;

            for(int k = i + 1 ; k <= historicActivityInstances.size() - 1; k++) {
                activityImp2_ = historicActivityInstances.get(k);// ?????????1?????????

                if ( activityImpl_.getActivityType().equals("userTask") && activityImp2_.getActivityType().equals("userTask") &&
                        df.format(activityImpl_.getStartTime()).equals(df.format(activityImp2_.getStartTime()))   ) //??????usertask???????????????????????????????????????????????????????????????????????????????????????
                {

                }
                else {
                    sameActivityImpl1 = (FlowNode)bpmnModel.getMainProcess().getFlowElement(historicActivityInstances.get(k).getActivityId());//????????????????????????????????????
                    break;
                }

            }
            sameStartTimeNodes.add(sameActivityImpl1); // ????????????????????????????????????????????????????????????
            for (int j = i + 1; j < historicActivityInstances.size() - 1; j++) {
                HistoricActivityInstance activityImpl1 = historicActivityInstances.get(j);// ?????????????????????
                HistoricActivityInstance activityImpl2 = historicActivityInstances.get(j + 1);// ?????????????????????

                if (df.format(activityImpl1.getStartTime()).equals(df.format(activityImpl2.getStartTime()))  )
                {// ???????????????????????????????????????????????????????????????
                    FlowNode sameActivityImpl2 = (FlowNode)bpmnModel.getMainProcess().getFlowElement(activityImpl2.getActivityId());
                    sameStartTimeNodes.add(sameActivityImpl2);
                }
                else
                {// ????????????????????????
                    break;
                }
            }
            List<SequenceFlow> pvmTransitions = activityImpl.getOutgoingFlows() ; // ?????????????????????????????????

            for (SequenceFlow pvmTransition : pvmTransitions)
            {// ???????????????????????????
                FlowNode pvmActivityImpl = (FlowNode)bpmnModel.getMainProcess().getFlowElement( pvmTransition.getTargetRef());// ?????????????????????????????????????????????????????????????????????????????????id?????????????????????
                if (sameStartTimeNodes.contains(pvmActivityImpl)) {
                    highFlows.add(pvmTransition.getId());
                }
            }

        }
        return highFlows;

    }


    @PostMapping("/delegate")
    @ResponseBody
    public AjaxResult delegate(String taskId, String delegateToUser) {
        processService.delegate(taskId, SecurityUtils.getUsername(), delegateToUser);
        return AjaxResult.success();
    }

    @PostMapping( "/cancelApply/{instanceId}")
    @ResponseBody
    public AjaxResult cancelApply(@PathVariable String instanceId) {
        processService.cancelApply(instanceId, "????????????");
        return AjaxResult.success();
    }

        @PostMapping( "/suspendOrActiveApply")
    @ResponseBody
    public AjaxResult suspendOrActiveApply(@RequestBody ActivitiBaseEntity activitiBaseEntity) {
        processService.suspendOrActiveApply(activitiBaseEntity.getInstanceId(), activitiBaseEntity.getSuspendState());
        return AjaxResult.success();
    }

    @GetMapping("/showVerifyDialog/{taskId}")
    public AjaxResult showVerifyDialog(@PathVariable("taskId") String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        String verifyName = task.getTaskDefinitionKey().substring(0, 1).toUpperCase() + task.getTaskDefinitionKey().substring(1);
        return AjaxResult.success(verifyName);
    }
    /**
     * ????????????
     *
     * @return
     */
    @PostMapping(value = "/complete")
    @ResponseBody
    public AjaxResult complete( @RequestBody ActivitiBaseEntity activitiBaseEntity) {
        List<Task> taskList = taskService.createTaskQuery()
                .processInstanceId(activitiBaseEntity.getInstanceId())
//                        .singleResult();
                .list();

        if (!CollectionUtils.isEmpty(taskList)) {
            TaskEntityImpl task = (TaskEntityImpl) taskList.get(0);
            activitiBaseEntity.setTaskId(task.getId());

        }
        processService.complete(activitiBaseEntity,"leave");
        return AjaxResult.success("???????????????");
    }

}
