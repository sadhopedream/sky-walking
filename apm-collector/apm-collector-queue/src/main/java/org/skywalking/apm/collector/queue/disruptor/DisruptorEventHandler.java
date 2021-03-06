package org.skywalking.apm.collector.queue.disruptor;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import org.skywalking.apm.collector.core.queue.EndOfBatchCommand;
import org.skywalking.apm.collector.core.queue.MessageHolder;
import org.skywalking.apm.collector.core.queue.QueueEventHandler;
import org.skywalking.apm.collector.core.worker.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.core.worker.WorkerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class DisruptorEventHandler implements EventHandler<MessageHolder>, QueueEventHandler {

    private final Logger logger = LoggerFactory.getLogger(DisruptorEventHandler.class);

    private RingBuffer<MessageHolder> ringBuffer;
    private AbstractLocalAsyncWorker asyncWorker;

    DisruptorEventHandler(RingBuffer<MessageHolder> ringBuffer, AbstractLocalAsyncWorker asyncWorker) {
        this.ringBuffer = ringBuffer;
        this.asyncWorker = asyncWorker;
    }

    /**
     * Receive the message from disruptor, when message in disruptor is empty, then send the cached data
     * to the next workers.
     *
     * @param event published to the {@link RingBuffer}
     * @param sequence of the event being processed
     * @param endOfBatch flag to indicate if this is the last event in a batch from the {@link RingBuffer}
     */
    public void onEvent(MessageHolder event, long sequence, boolean endOfBatch) {
        try {
            Object message = event.getMessage();
            event.reset();

            asyncWorker.allocateJob(message);
            if (endOfBatch) {
                asyncWorker.allocateJob(new EndOfBatchCommand());
            }
        } catch (WorkerException e) {
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Push the message into disruptor ring buffer.
     *
     * @param message of the data to process.
     */
    public void tell(Object message) {
        long sequence = ringBuffer.next();
        try {
            ringBuffer.get(sequence).setMessage(message);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
