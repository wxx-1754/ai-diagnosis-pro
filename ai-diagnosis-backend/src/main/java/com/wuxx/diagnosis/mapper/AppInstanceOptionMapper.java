package com.wuxx.diagnosis.mapper;

import java.util.List;

import com.wuxx.diagnosis.domain.AppInstanceOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppInstanceOptionMapper {

    @Select("""
            SELECT DISTINCT
                app_id AS appId,
                app_name AS appName,
                env
            FROM app_instance
            WHERE status = 'ONLINE'
            ORDER BY app_name ASC, app_id ASC, env ASC
            """)
    List<AppInstanceOption> findOnlineOptions();
}
