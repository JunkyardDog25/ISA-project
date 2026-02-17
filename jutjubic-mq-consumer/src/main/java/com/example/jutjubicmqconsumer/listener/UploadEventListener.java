package com.example.jutjubicmqconsumer.listener;

import com.example.jutjubic.proto.UploadEvent;
import com.example.jutjubicmqconsumer.config.RabbitConfig;
import com.example.jutjubicmqconsumer.messaging.UploadEventJson;
import com.example.jutjubicmqconsumer.stats.MqStats;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UploadEventListener {

    private final ObjectMapper om = new ObjectMapper();
    private final MqStats stats;

    public UploadEventListener(MqStats stats) {
        this.stats = stats;
    }

    @RabbitListener(queues = RabbitConfig.UPLOAD_QUEUE_JSON)
    public void onUploadJson(byte[] body) throws Exception {
        long t1 = System.nanoTime();
        UploadEventJson ev = om.readValue(body, UploadEventJson.class);
        long t2 = System.nanoTime();

        stats.jsonCount.incrementAndGet();
        stats.jsonDeserNanos.addAndGet(t2 - t1);
        stats.jsonBytes.addAndGet(body.length);




         System.out.println("JSON event: " + ev);
    }

    @RabbitListener(queues = RabbitConfig.UPLOAD_QUEUE_PB)
    public void onUploadPb(byte[] body) throws Exception {
        long t1 = System.nanoTime();
        UploadEvent ev = UploadEvent.parseFrom(body);
        long t2 = System.nanoTime();

        stats.pbCount.incrementAndGet();
        stats.pbDeserNanos.addAndGet(t2 - t1);
        stats.pbBytes.addAndGet(body.length);


         System.out.println("PB event: " + ev);
    }
}
