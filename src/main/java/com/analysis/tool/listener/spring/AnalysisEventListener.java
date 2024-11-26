package com.analysis.tool.listener.spring;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.analysis.tool.common.event.AnalysisEvent;
import com.analysis.tool.entity.dto.StepDTO;
import com.analysis.tool.plugin.AbstractPlugin;
import com.analysis.tool.util.ThreadIdGeneratorUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.lang.NonNullApi;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author DingYulong
 * @Date 2024/11/14 15:24
 */
@Component
public class AnalysisEventListener implements ApplicationListener<AnalysisEvent> {


    private static final Logger log = LoggerFactory.getLogger(AnalysisEventListener.class);
    @Value("${analysis.tool.param}")
    private String param;
    @Value("${analysis.tool.step}")
    private String step;
    @Value("${analysis.tool.script.path}")
    private String scriptPath;

    @Resource
    private ApplicationContext context;


    @Override
    public void onApplicationEvent(@NonNull AnalysisEvent event) {
        //读取执行规则
        JSONArray jsonArray = JSONArray.parseArray(step);
        Map<String, Object> runtimeParam = new HashMap<>();
        log.info("taskId is:{}", ThreadIdGeneratorUtil.getThreadId());

        for (String s : param.split(",")) {
            runtimeParam.put(s, event.getParam().getOrDefault(s,""));
        }

        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            StepDTO stepDTO = JSONObject.parseObject(jsonObject.toJSONString(), StepDTO.class);
            String type = jsonObject.getString("type");

            Map<String, Object> nodeParam = stepDTO.getNodeParam();
            List<Map<String, Object>> taskParam = stepDTO.getTaskParam();


            if ("bean".equals(type)){
                //执行插件
                String pluginName = String.valueOf(nodeParam.get("name"));
                AbstractPlugin abstractPlugin = (AbstractPlugin)context.getBean(pluginName);
                Map<String, Object> taskParamMap = new HashMap<>();
                taskParam.forEach(node->{
                    String name = String.valueOf(node.get("name"));
                    String value = String.valueOf(node.get("value"));
                    taskParamMap.put(name, runtimeParam.get(value));
                });
                Map<String, Object> execute = abstractPlugin.execute(taskParamMap);
                runtimeParam.putAll(execute);
            } else if ("script".equals(type)) {
                //将 taskParam 的value先转为list再将所有元素用 分割后得到字符串

                String param = taskParam.stream().map(node -> String.valueOf(runtimeParam.get(String.valueOf(node.get("value"))))).collect(Collectors.joining(" "));

                //执行脚本
                String url = String.valueOf(nodeParam.get("url"));
                String path = String.valueOf(nodeParam.get("path"));
                String token = String.valueOf(nodeParam.get("token"));
                url=String.format(url,token);
                String downloadCommand = "git clone "+url +" "+ scriptPath;
                executeShellCommand(downloadCommand);
                String executeCommand = "sh "+scriptPath+path + " " + param;
                List<String> results = executeShellCommand(executeCommand);
                log.info(results.toString());

                Map<String, Object> outputParam = stepDTO.getOutputParam();
                outputParam.forEach((k, v)-> runtimeParam.put( k,results.get(Integer.parseInt( String.valueOf(v)))));
                String deleteCommand = "rm -rf "+scriptPath;
                executeShellCommand(deleteCommand);
            }else {
                throw new RuntimeException("不支持的类型");
            }

        }


       log.info(String.valueOf(runtimeParam));
    }


    /**
     * 执行 Shell 命令并返回控制台输出的每一行。
     *
     * @param command 命令字符串
     * @param params 命令参数列表
     * @return 控制台输出的每一行作为一个列表元素
     */
    public static List<String> executeShellCommand(String command, String... params) {
        List<String> outputLines = new ArrayList<>();
        try {
            // 使用 Runtime.exec() 执行命令
            Process process = Runtime.getRuntime().exec(command);
            // 获取命令的输出流
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            // 读取命令的输出
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line);
            }
            // 等待命令执行完成
            process.waitFor();
            // 关闭输入流
            reader.close();
            errorReader.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            log.error("Error executing shell command: {}", e.getMessage());
        }

        return outputLines;
    }

}
