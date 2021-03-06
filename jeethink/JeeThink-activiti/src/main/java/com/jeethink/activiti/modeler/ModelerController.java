package com.jeethink.activiti.modeler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.pagehelper.Page;
import com.jeethink.activiti.domain.ModelerVo;
import com.jeethink.common.annotation.Log;
import com.jeethink.common.config.JeeThinkConfig;
import com.jeethink.common.constant.HttpStatus;
import com.jeethink.common.core.controller.BaseController;
import com.jeethink.common.core.domain.AjaxResult;
import com.jeethink.common.core.page.PageDomain;
import com.jeethink.common.core.page.TableDataInfo;
import com.jeethink.common.core.page.TableSupport;
import com.jeethink.common.enums.BusinessType;

import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.impl.persistence.entity.ModelEntityImpl;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ModelQuery;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.List;

import static org.activiti.editor.constants.ModelDataJsonConstants.MODEL_DESCRIPTION;
import static org.activiti.editor.constants.ModelDataJsonConstants.MODEL_NAME;

@Controller
public class ModelerController extends BaseController {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ModelEditorJsonRestResource.class);

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private ObjectMapper objectMapper;


    @GetMapping("/modeler/list")
    @ResponseBody
    public TableDataInfo list(ModelEntityImpl modelEntity) {
        ModelQuery modelQuery = repositoryService.createModelQuery();
        modelQuery.orderByLastUpdateTime().desc();

        // ????????????
        if (com.jeethink.common.utils.StringUtils.isNotBlank(modelEntity.getKey())) {
            modelQuery.modelKey(modelEntity.getKey());
        }
        if (com.jeethink.common.utils.StringUtils.isNotBlank(modelEntity.getName())) {
            modelQuery.modelNameLike("%" + modelEntity.getName() + "%");
        }

        PageDomain pageDomain = TableSupport.buildPageRequest();
        Integer pageNum = pageDomain.getPageNum();
        Integer pageSize = pageDomain.getPageSize();

        List<Model> resultList = modelQuery.listPage((pageNum - 1) * pageSize, pageSize);

        Page<Model> list = new Page<>();
        list.addAll(resultList);

        list.setTotal(modelQuery.count());
        list.setPageNum(pageNum);
        list.setPageSize(pageSize);

        return getDataTable(list);
    }


    /**
     * ????????????
     */
    @RequestMapping(value = "/modeler/create")
    @ResponseBody
    public AjaxResult create( @RequestBody ModelerVo modelerVo) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode editorNode = objectMapper.createObjectNode();
            editorNode.put("id", "canvas");
            editorNode.put("resourceId", "canvas");
            ObjectNode stencilSetNode = objectMapper.createObjectNode();
            stencilSetNode.put("namespace", "http://b3mn.org/stencilset/bpmn2.0#");
            editorNode.put("stencilset", stencilSetNode);

            ObjectNode modelObjectNode = objectMapper.createObjectNode();
            modelObjectNode.put(MODEL_NAME, modelerVo.getName());
            modelObjectNode.put(ModelDataJsonConstants.MODEL_REVISION, 1);
            String description = StringUtils.defaultString(modelerVo.getDescription());
            modelObjectNode.put(MODEL_DESCRIPTION, description);

            Model newModel = repositoryService.newModel();
            newModel.setMetaInfo(modelObjectNode.toString());
            newModel.setName(modelerVo.getName());
            newModel.setKey(StringUtils.defaultString(modelerVo.getKey()));

            repositoryService.saveModel(newModel);
            repositoryService.addModelEditorSource(newModel.getId(), editorNode.toString().getBytes("utf-8"));

            return new AjaxResult(HttpStatus.SUCCESS, "??????????????????", newModel.getId());
        } catch (Exception e) {
            logger.error("?????????????????????", e);
        }
        return AjaxResult.error();
    }

    /**
     * ??????Model????????????
     */
    @RequestMapping(value = "/modeler/deploy/{modelId}")
    @ResponseBody
    public AjaxResult deploy(@PathVariable("modelId") String modelId, RedirectAttributes redirectAttributes) {
        try {
            Model modelData = repositoryService.getModel(modelId);
            ObjectNode modelNode = (ObjectNode) new ObjectMapper().readTree(repositoryService.getModelEditorSource(modelData.getId()));
            byte[] bpmnBytes = null;

            BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
            bpmnBytes = new BpmnXMLConverter().convertToXML(model);

            String processName = modelData.getName() + ".bpmn20.xml";
            Deployment deployment = repositoryService.createDeployment().name(modelData.getName()).addString(processName, new String(bpmnBytes, "UTF-8")).deploy();
            LOGGER.info("?????????????????????ID=" + deployment.getId());
            return AjaxResult.success("????????????");
        } catch (Exception e) {
            LOGGER.error("?????????????????????????????????modelId={}", modelId, e);

        }
        return AjaxResult.error("????????????");
    }

    /**
     * ??????model???xml??????
     */
    @RequestMapping(value = "/modeler/export/{modelId}")
    @ResponseBody
    public AjaxResult export(@PathVariable("modelId") String modelId) {
        OutputStream out = null;
        try {
            Model modelData = repositoryService.getModel(modelId);
            BpmnJsonConverter jsonConverter = new BpmnJsonConverter();
            JsonNode editorNode = new ObjectMapper().readTree(repositoryService.getModelEditorSource(modelData.getId()));
            BpmnModel bpmnModel = jsonConverter.convertToBpmnModel(editorNode);

            // ??????????????????
            if (!CollectionUtils.isEmpty(bpmnModel.getProcesses())) {
                BpmnXMLConverter xmlConverter = new BpmnXMLConverter();
                byte[] bpmnBytes = xmlConverter.convertToXML(bpmnModel);

                ByteArrayInputStream in = new ByteArrayInputStream(bpmnBytes);
                String filename = bpmnModel.getMainProcess().getId() + ".bpmn";

                File file  = new File(getAbsoluteFile(filename));
                if(file.exists()){
                    file.delete();
                }
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(bpmnBytes,0,bpmnBytes.length);
                fos.flush();
                fos.close();
                return AjaxResult.success(filename);
            } else {
                return AjaxResult.error();
            }

        } catch (Exception e) {
            LOGGER.error("??????model???xml???????????????modelId={}", modelId, e);
            return AjaxResult.error("??????model???xml???????????????modelId={}", modelId);

        }
    }

    /**
     * ??????????????????
     *
     * @param filename ????????????
     */
    public String getAbsoluteFile(String filename)
    {
        String downloadPath = JeeThinkConfig.getDownloadPath() + filename;
        File desc = new File(downloadPath);
        if (!desc.getParentFile().exists())
        {
            desc.getParentFile().mkdirs();
        }
        return downloadPath;
    }


    @Log(title = "????????????", businessType = BusinessType.DELETE)
    @DeleteMapping("/modeler/remove/{ids}")
    @ResponseBody
    public AjaxResult remove(@PathVariable String ids) {
        try {
            repositoryService.deleteModel(ids);
            return AjaxResult.success();
        }
        catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

}
