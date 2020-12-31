/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.log.Logger;
import io.prestosql.eventlistener.EventListenerManager;
import io.prestosql.execution.TaskId;
import io.prestosql.operator.DriverStats;
import io.prestosql.operator.OperatorStats;
import io.prestosql.operator.SplitOperatorInfo;
import io.prestosql.spi.eventlistener.SplitCompletedEvent;
import io.prestosql.spi.eventlistener.SplitFailureInfo;
import io.prestosql.spi.eventlistener.SplitStatistics;

import javax.annotation.Nullable;
import javax.inject.Inject;

import java.time.Duration;
import java.util.Optional;

import static java.time.Duration.ofMillis;
import static java.util.Objects.requireNonNull;

public class SplitMonitor
{
    private static final Logger log = Logger.get(SplitMonitor.class);

    private final ObjectMapper objectMapper;
    private final EventListenerManager eventListenerManager;

    @Inject
    public SplitMonitor(EventListenerManager eventListenerManager, ObjectMapper objectMapper)
    {
        this.eventListenerManager = requireNonNull(eventListenerManager, "eventListenerManager is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    public void splitCompletedEvent(TaskId taskId, DriverStats driverStats)
    {
        splitCompletedEvent(taskId, driverStats, null, null);
    }

    public void splitFailedEvent(TaskId taskId, DriverStats driverStats, Throwable cause)
    {
        splitCompletedEvent(taskId, driverStats, cause.getClass().getName(), cause.getMessage());
    }

    private void splitCompletedEvent(TaskId taskId, DriverStats driverStats, @Nullable String failureType, @Nullable String failureMessage)
    {
        Duration queuedTime = ofMillis(driverStats.getQueuedTime().toMillis());
        Optional<Duration> queuedTimeIfSplitRan = Optional.empty();
        if (driverStats.getStartTime() != null) {
            queuedTimeIfSplitRan = Optional.of(queuedTime);
        }

        Duration elapsedTime = ofMillis(driverStats.getElapsedTime().toMillis());
        Optional<Duration> elapsedTimeIfSplitRan = Optional.empty();
        if (driverStats.getEndTime() != null) {
            elapsedTimeIfSplitRan = Optional.of(elapsedTime);
        }

        Optional<SplitFailureInfo> splitFailureMetadata = Optional.empty();
        if (failureType != null) {
            splitFailureMetadata = Optional.of(new SplitFailureInfo(failureType, failureMessage != null ? failureMessage : ""));
        }

        Optional<String> splitCatalog = driverStats.getOperatorStats().stream()
                .map(OperatorStats::getInfo)
                .filter(SplitOperatorInfo.class::isInstance)
                .map(SplitOperatorInfo.class::cast)
                .map(info -> info.getCatalogName().getCatalogName())
                .findFirst();

        try {
            eventListenerManager.splitCompleted(
                    new SplitCompletedEvent(
                            taskId.getQueryId().toString(),
                            taskId.getStageId().toString(),
                            Integer.toString(taskId.getId()),
                            splitCatalog,
                            driverStats.getCreateTime().toDate().toInstant(),
                            Optional.ofNullable(driverStats.getStartTime()).map(startTime -> startTime.toDate().toInstant()),
                            Optional.ofNullable(driverStats.getEndTime()).map(endTime -> endTime.toDate().toInstant()),
                            new SplitStatistics(
                                    ofMillis(driverStats.getTotalCpuTime().toMillis()),
                                    elapsedTime,
                                    queuedTime,
                                    ofMillis(driverStats.getRawInputReadTime().toMillis()),
                                    driverStats.getRawInputPositions(),
                                    driverStats.getRawInputDataSize().toBytes(),
                                    queuedTimeIfSplitRan,
                                    elapsedTimeIfSplitRan),
                            splitFailureMetadata,
                            objectMapper.writeValueAsString(driverStats)));
        }
        catch (JsonProcessingException e) {
            log.error(e, "Error processing split completion event for task %s", taskId);
        }
    }
}
