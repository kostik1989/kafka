/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.common.requests;

import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.kafka.common.protocol.types.Struct;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DescribeConfigsRequest extends AbstractRequest {

    private static final String RESOURCES_KEY_NAME = "resources";
    private static final String RESOURCE_TYPE_KEY_NAME = "resource_type";
    private static final String RESOURCE_NAME_KEY_NAME = "resource_name";
    private static final String CONFIG_NAMES_KEY_NAME = "config_names";

    public static class Builder extends AbstractRequest.Builder {
        private final Map<Resource, Collection<String>> resourceToConfigNames;

        public Builder(Map<Resource, Collection<String>> resourceToConfigNames) {
            super(ApiKeys.DESCRIBE_CONFIGS);
            this.resourceToConfigNames = resourceToConfigNames;
        }

        public Builder(Collection<Resource> resources) {
            this(toResourceToConfigNames(resources));
        }

        private static Map<Resource, Collection<String>> toResourceToConfigNames(Collection<Resource> resources) {
            Map<Resource, Collection<String>> result = new HashMap<>(resources.size());
            for (Resource resource : resources)
                result.put(resource, null);
            return result;
        }

        @Override
        public DescribeConfigsRequest build(short version) {
            return new DescribeConfigsRequest(version, resourceToConfigNames);
        }
    }

    private final Map<Resource, Collection<String>> resourceToConfigNames;

    public DescribeConfigsRequest(short version, Map<Resource, Collection<String>> resourceToConfigNames) {
        super(version);
        this.resourceToConfigNames = resourceToConfigNames;

    }

    public DescribeConfigsRequest(Struct struct, short version) {
        super(version);
        Object[] resourcesArray = struct.getArray(RESOURCES_KEY_NAME);
        resourceToConfigNames = new HashMap<>(resourcesArray.length);
        for (Object resourceObj : resourcesArray) {
            Struct resourceStruct = (Struct) resourceObj;
            ResourceType resourceType = ResourceType.forId(resourceStruct.getByte(RESOURCE_TYPE_KEY_NAME));
            String resourceName = resourceStruct.getString(RESOURCE_NAME_KEY_NAME);

            Object[] configNamesArray = resourceStruct.getArray(CONFIG_NAMES_KEY_NAME);
            List<String> configNames = null;
            if (configNamesArray != null) {
                configNames = new ArrayList<>(configNamesArray.length);
                for (Object configNameObj : configNamesArray)
                    configNames.add((String) configNameObj);
            }

            resourceToConfigNames.put(new Resource(resourceType, resourceName), configNames);
        }
    }

    public Collection<Resource> resources() {
        return resourceToConfigNames.keySet();
    }

    /**
     * Return null if all config names should be returned.
     */
    public Collection<String> configNames(Resource resource) {
        return resourceToConfigNames.get(resource);
    }

    @Override
    protected Struct toStruct() {
        Struct struct = new Struct(ApiKeys.DESCRIBE_CONFIGS.requestSchema(version()));
        List<Struct> resourceStructs = new ArrayList<>(resources().size());
        for (Map.Entry<Resource, Collection<String>> entry : resourceToConfigNames.entrySet()) {
            Resource resource = entry.getKey();
            Struct resourceStruct = struct.instance(RESOURCES_KEY_NAME);
            resourceStruct.set(RESOURCE_TYPE_KEY_NAME, resource.type().id());
            resourceStruct.set(RESOURCE_NAME_KEY_NAME, resource.name());

            String[] configNames = entry.getValue() == null ? null : entry.getValue().toArray(new String[0]);
            resourceStruct.set(CONFIG_NAMES_KEY_NAME, configNames);

            resourceStructs.add(resourceStruct);
        }
        struct.set(RESOURCES_KEY_NAME, resourceStructs.toArray(new Struct[0]));
        return struct;
    }

    @Override
    public DescribeConfigsResponse getErrorResponse(int throttleTimeMs, Throwable e) {
        short version = version();
        switch (version) {
            case 0:
                ApiError error = ApiError.fromThrowable(e);
                Map<Resource, DescribeConfigsResponse.Config> errors = new HashMap<>(resources().size());
                DescribeConfigsResponse.Config config = new DescribeConfigsResponse.Config(error,
                        Collections.<DescribeConfigsResponse.ConfigEntry>emptyList());
                for (Resource resource : resources())
                    errors.put(resource, config);
                return new DescribeConfigsResponse(throttleTimeMs, errors);
            default:
                throw new IllegalArgumentException(String.format("Version %d is not valid. Valid versions for %s are 0 to %d",
                        version, this.getClass().getSimpleName(), ApiKeys.DESCRIBE_CONFIGS.latestVersion()));
        }
    }

    public static DescribeConfigsRequest parse(ByteBuffer buffer, short version) {
        return new DescribeConfigsRequest(ApiKeys.DESCRIBE_CONFIGS.parseRequest(version, buffer), version);
    }
}
