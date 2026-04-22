package com.icon.agent00.mapper;

import com.icon.agent00.entity.SchoolDAO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface SchoolMapper {

    /**
     * 获取所有学校数据
     */
    List<SchoolDAO> getAllSchools();
}