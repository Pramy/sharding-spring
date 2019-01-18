/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.shardingsphere.shardingjdbc.spring.boot;

import com.google.common.base.Preconditions;
import io.shardingsphere.shardingjdbc.spring.boot.common.SpringBootConfigMapConfigurationProperties;
import io.shardingsphere.shardingjdbc.spring.boot.common.SpringBootPropertiesConfigurationProperties;
import io.shardingsphere.shardingjdbc.spring.boot.masterslave.SpringBootMasterSlaveRuleConfigurationProperties;
import io.shardingsphere.shardingjdbc.spring.boot.sharding.SpringBootShardingRuleConfigurationProperties;
import io.shardingsphere.shardingjdbc.spring.boot.util.PropertyUtil;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.core.exception.ShardingException;
import org.apache.shardingsphere.core.util.InlineExpressionParser;
import org.apache.shardingsphere.shardingjdbc.api.MasterSlaveDataSourceFactory;
import org.apache.shardingsphere.shardingjdbc.api.ShardingDataSourceFactory;
import org.apache.shardingsphere.shardingjdbc.util.DataSourceUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Spring boot sharding and master-slave configuration.
 *
 * @author caohao
 */
@Configuration
@EnableConfigurationProperties({
        SpringBootShardingRuleConfigurationProperties.class, SpringBootMasterSlaveRuleConfigurationProperties.class, 
        SpringBootConfigMapConfigurationProperties.class, SpringBootPropertiesConfigurationProperties.class
})
@RequiredArgsConstructor
public class SpringBootConfiguration implements EnvironmentAware {
    
    private final SpringBootShardingRuleConfigurationProperties shardingProperties;
    
    private final SpringBootMasterSlaveRuleConfigurationProperties masterSlaveProperties;
    
    private final SpringBootConfigMapConfigurationProperties configMapProperties;
    
    private final SpringBootPropertiesConfigurationProperties propMapProperties;
    
    private final Map<String, DataSource> dataSourceMap = new LinkedHashMap<>();
    
    /**
     * Get data source bean.
     *
     * @return data source bean
     * @throws SQLException SQL exception
     */
    @Bean
    public DataSource dataSource() throws SQLException {
        return null == masterSlaveProperties.getMasterDataSourceName()
                ? ShardingDataSourceFactory
            .createDataSource(dataSourceMap, shardingProperties.getShardingRuleConfiguration(), configMapProperties.getConfigMap(), propMapProperties.getProps())
                : MasterSlaveDataSourceFactory.createDataSource(
                        dataSourceMap, masterSlaveProperties.getMasterSlaveRuleConfiguration(), configMapProperties.getConfigMap(), propMapProperties.getProps());
    }
    
    @Override
    public final void setEnvironment(final Environment environment) {
        setDataSourceMap(environment);
    }

    @SuppressWarnings("unchecked")
    private void setDataSourceMap(final Environment environment) {
        String prefix = "sharding.jdbc.datasource.";
        String dataSources = environment.getProperty(prefix + "names");
        for (String each : getDataSourceNames(dataSources)) {
            try {
                Map<String, Object> dataSourceProps = PropertyUtil.handle(environment, prefix + each.trim(), Map.class);
                Preconditions.checkState(!dataSourceProps.isEmpty(), "Wrong datasource properties!");
                DataSource dataSource = DataSourceUtil
                    .getDataSource(dataSourceProps.get("type").toString(), dataSourceProps);
                dataSourceMap.put(each, dataSource);
            } catch (final ReflectiveOperationException ex) {
                throw new ShardingException("Can't find datasource type!", ex);
            }
        }
    }

    private List<String> getDataSourceNames(String dataSources) {
        List<String> names = new LinkedList<>();
        for (String each : dataSources.split(",")) {
            names.addAll(new InlineExpressionParser(each).splitAndEvaluate());
        }
        return names;
    }
}
