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
 *
 */

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.segment;

import java.util.*;
import org.apache.skywalking.apm.collector.analysis.metric.define.graph.MetricGraphIdDefine;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.decorator.SpanDecorator;
import org.apache.skywalking.apm.collector.analysis.segment.parser.define.listener.*;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.core.graph.*;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.*;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentDuration;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class SegmentDurationSpanListener implements EntrySpanListener, ExitSpanListener, LocalSpanListener, FirstSpanListener {

    private static final Logger logger = LoggerFactory.getLogger(SegmentDurationSpanListener.class);

    private final List<SegmentDuration> segmentDurations;
    private final ServiceNameCacheService serviceNameCacheService;
    private boolean isError = false;
    private long timeBucket;

    private SegmentDurationSpanListener(ModuleManager moduleManager) {
        this.segmentDurations = new LinkedList<>();
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
    }

    @Override public boolean containsPoint(Point point) {
        return Point.Entry.equals(point) || Point.Exit.equals(point) || Point.Local.equals(point) || Point.First.equals(point);
    }

    @Override
    public void parseFirst(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {

        timeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(spanDecorator.getStartTime());

        SegmentDuration segmentDuration = new SegmentDuration();
        segmentDuration.setId(segmentId);
        segmentDuration.setSegmentId(segmentId);
        segmentDuration.setApplicationId(applicationId);
        segmentDuration.setDuration(spanDecorator.getEndTime() - spanDecorator.getStartTime());
        segmentDuration.setStartTime(spanDecorator.getStartTime());
        segmentDuration.setEndTime(spanDecorator.getEndTime());
        if (spanDecorator.getOperationNameId() == 0) {
            segmentDuration.setServiceName(spanDecorator.getOperationName());
        } else {
            segmentDuration.setServiceName(serviceNameCacheService.get(spanDecorator.getOperationNameId()).getServiceName());
        }

        segmentDurations.add(segmentDuration);
        isError = isError || spanDecorator.getIsError();
    }

    @Override
    public void parseEntry(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        isError = isError || spanDecorator.getIsError();
    }

    @Override
    public void parseExit(SpanDecorator spanDecorator, int applicationId, int instanceId, String segmentId) {
        isError = isError || spanDecorator.getIsError();
    }

    @Override
    public void parseLocal(SpanDecorator spanDecorator, int applicationId, int instanceId,
        String segmentId) {
        isError = isError || spanDecorator.getIsError();
    }

    @Override public void build() {
        Graph<SegmentDuration> graph = GraphManager.INSTANCE.findGraph(MetricGraphIdDefine.SEGMENT_DURATION_GRAPH_ID, SegmentDuration.class);
        logger.debug("segment cost listener build");
        segmentDurations.forEach(segmentDuration -> {
            segmentDuration.setIsError(BooleanUtils.booleanToValue(isError));
            segmentDuration.setTimeBucket(timeBucket);
            graph.start(segmentDuration);
        });
    }

    public static class Factory implements SpanListenerFactory {

        @GraphComputingMetric(name = "/segment/parse/createSpanListeners/segmentDurationSpanListener")
        @Override public SpanListener create(ModuleManager moduleManager) {
            return new SegmentDurationSpanListener(moduleManager);
        }
    }
}
