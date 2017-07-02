package net.moopa.guard.config;

import net.moopa.guard.common.PropertiesFileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by LeeAutumn on 11/25/16.
 * blog: leeautumn.net
 */
public class GuardConfigs {
    private static HashMap<String,String> configs = new HashMap<String,String>();
    private static Logger logger = LoggerFactory.getLogger(GuardConfigs.class);

    public static void init(){
        Properties properties = PropertiesFileUtil.getProperties("guard.config");
        //开始验证 配置 是否正确
        //需要得到用户自己的用户服务实现

        for(Map.Entry<Object,Object> e :properties.entrySet()){
            put(properties,e.getKey().toString());
        }
    }

    public static String get(String key){
        return configs.get(key);
    }

    private static void put(Properties properties,String key){
        Object o1 = properties.get(key);
        if(o1 == null){

        }
        configs.put(key,o1.toString().trim());
    }
}
