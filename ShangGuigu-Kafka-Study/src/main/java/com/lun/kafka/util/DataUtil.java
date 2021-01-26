package com.lun.kafka.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lun.kafka.vo.DataVo;
import com.lun.kafka.vo.People;
import com.lun.kafka.vo.PeopleInfo;

import java.io.IOException;
import java.util.Map;

public class DataUtil {
    public static ObjectMapper objectMapper = new ObjectMapper();
    
    public static People getPeople() {
        int getRandom = RandomUtil.getRandom();
        PeopleInfo peopleInfo = new PeopleInfo("sfzh-" + getRandom, "address-" + getRandom, "company-" + getRandom, "tel-" + getRandom, "qq-" + getRandom, "weixin-" + getRandom);
        People people = new People("id-" + getRandom, "name-" + getRandom, "age-" + getRandom, "sex-" + getRandom, "title-" + getRandom, "sfzh-" + getRandom, peopleInfo);
        return people;
    }
    
    
    public static DataVo getDataVo(Object data) {
        String uuid = RandomUtil.getUUID();
        
        DataVo dataVo = new DataVo();
        dataVo.setDatabase("test_db");
        dataVo.setTableName("test_people");
        
        dataVo.setDataStamp(System.currentTimeMillis() / 1000 + "");
        dataVo.setUuId(uuid);
        dataVo.setMsgId(uuid);
        dataVo.setDataVer("0.1");
        dataVo.setData(data);
        return dataVo;
    }
    
    
    public static String getJsonData(DataVo dataVo) throws JsonProcessingException {
        String dataJson = objectMapper.writeValueAsString(dataVo);
        return dataJson;
    }
    
    
    public static void main(String[] args) throws IOException {
        People people = getPeople();
        System.out.println(people);
        System.out.println("----------------------------------");
        try {
            DataVo dataVo = getDataVo(people);
            System.out.println("----------------------------------");
            
            System.out.println("json字符串转为Map对象:");
            String dataJson = getJsonData(dataVo);
            System.out.println(dataJson);
            
            Map<String, Object> jsonData = objectMapper.readValue(dataJson, new TypeReference<Map<String, Object>>() {
            });
            System.out.println(jsonData);
            System.out.println("-----------------获取data-----------------");
            Map<String, Object> data = (Map<String, Object>) jsonData.get("data");
            System.out.println("-----------------获取data-----------------" + data);
            
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } finally {
        }
    }
}
